package com.vocawik.service.vocadb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocawik.common.i18n.Language;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.dto.artist.ArtistCreateRequest;
import com.vocawik.dto.vocal.VocalCreateRequest;
import com.vocawik.dto.vocadb.VocadbPrefillResponse;
import com.vocawik.service.artist.ArtistService;
import com.vocawik.service.vocal.VocalService;
import com.vocawik.service.pv.detector.PvUrlDetector;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.transaction.Transactional;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Resolves create-form prefill payloads from VocaDB dump tables. */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Injected infrastructure beans are Spring-managed dependencies.")
public class VocadbPrefillService {

    private static final Set<String> VOCALIST_TYPES =
            Set.of(
                    "SynthesizerV",
                    "NEUTRINO",
                    "NewType",
                    "Vocaloid",
                    "UTAU",
                    "ACEVirtualSinger",
                    "AIVOICE",
                    "VOICEVOX",
                    "Unknown",
                    "Voiceroid",
                    "CeVIO",
                    "VoiSona",
                    "OtherVoiceSynthesizer");
    private static final Set<String> ALLOWED_SONG_ARTIST_ROLES =
            Set.of(
                    "PRODUCER",
                    "ARRANGER",
                    "COMPOSER",
                    "LYRICIST",
                    "INSTRUMENTALIST",
                    "VOCALIST",
                    "MASTERING",
                    "MIXER",
                    "VOICE_MANIPULATOR");
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PvUrlDetector pvUrlDetector;
    private final ArtistService artistService;
    private final VocalService vocalService;

    @Transactional(Transactional.TxType.SUPPORTS)
    public VocadbPrefillResponse resolve(String rawUrl) {
        VocadbLink link = parseVocadbLink(rawUrl);
        if (link == null) {
            return new VocadbPrefillResponse(false, null, null, null, null, null);
        }

        return switch (link.type()) {
            case SONG -> resolveSong(link.id());
            case ARTIST -> resolveArtist(link.id());
        };
    }

    private VocadbPrefillResponse resolveSong(long vocadbId) {
        List<java.util.Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        """
                        SELECT
                            id,
                            song_default_name,
                            song_default_name_language,
                            song_main_picture_url_thumb,
                            song_main_picture_url_original,
                            song_publish_date,
                            song_type,
                            artists,
                            pvs,
                            original_version,
                            web_links,
                            deleted
                        FROM dump_vocadb_song
                        WHERE id = ?
                        LIMIT 1
                        """,
                        vocadbId);
        if (rows.isEmpty()) {
            return new VocadbPrefillResponse(false, "SONG", vocadbId, null, null, null);
        }

        java.util.Map<String, Object> row = rows.getFirst();
        if (Boolean.TRUE.equals(row.get("deleted"))) {
            return new VocadbPrefillResponse(false, "SONG", vocadbId, null, null, null);
        }

        String canonicalName =
                firstNonBlank(asString(row.get("song_default_name")), "VocaDB Song " + vocadbId);
        Language language =
                resolveLanguage(asString(row.get("song_default_name_language")), canonicalName);
        String thumbnailUrl =
                firstNonBlank(
                        asString(row.get("song_main_picture_url_thumb")),
                        asString(row.get("song_main_picture_url_original")));
        LocalDateTime publishedAt = parseLocalDateTime(asString(row.get("song_publish_date")));
        String songType = mapSongType(asString(row.get("song_type")));

        List<VocadbPrefillResponse.LinkPrefill> links = new ArrayList<>();
        appendVocadbLink(links, "https://vocadb.net/S/" + vocadbId);
        appendWebLinks(links, row.get("web_links"), LinkNormalization.SONG);

        List<DumpSongArtistRef> dumpArtists = parseSongArtists(row.get("artists"));
        ArrayList<VocadbPrefillResponse.SongArtistPrefill> artists = new ArrayList<>();
        ArrayList<VocadbPrefillResponse.SongVocalPrefill> vocals = new ArrayList<>();
        java.util.Map<Long, UUID> createdArtists = new java.util.HashMap<>();
        java.util.Map<Long, UUID> createdVocals = new java.util.HashMap<>();
        boolean hasMainArtist = false;
        boolean hasMainVocal = false;
        for (DumpSongArtistRef item : dumpArtists) {
            List<String> roles = mapArtistRoles(item.roles(), item.artistType());
            if (!roles.isEmpty()) {
                UUID artistResourceUuid =
                        ensureArtistResourceUuid(
                                item.artistId(), item.name(), roles, createdArtists);
                artists.add(
                        new VocadbPrefillResponse.SongArtistPrefill(
                                item.artistId(),
                                artistResourceUuid,
                                firstNonBlank(item.name(), "VocaDB Artist " + item.artistId()),
                                roles,
                                !hasMainArtist,
                                item.sortOrder()));
                hasMainArtist = true;
            }
            if (isVocalistType(item.artistType())) {
                UUID vocalResourceUuid =
                        ensureVocalResourceUuid(item.artistId(), item.name(), createdVocals);
                vocals.add(
                        new VocadbPrefillResponse.SongVocalPrefill(
                                item.artistId(),
                                vocalResourceUuid,
                                firstNonBlank(item.name(), "VocaDB Vocal " + item.artistId()),
                                !hasMainVocal,
                                item.sortOrder()));
                hasMainVocal = true;
            }
        }

        ArrayList<VocadbPrefillResponse.SongPvPrefill> pvs = new ArrayList<>();
        int pvIndex = 0;
        for (JsonNode item : iterableArray(row.get("pvs"))) {
            String url = asText(item, "url");
            if (url == null || url.isBlank()) {
                continue;
            }
            var detected = pvUrlDetector.detect(url).orElse(null);
            String service =
                    detected != null
                            ? detected.provider().name()
                            : mapPvService(asText(item, "service"));
            String videoKey = detected != null ? detected.videoKey() : null;
            pvs.add(
                    new VocadbPrefillResponse.SongPvPrefill(
                            service,
                            videoKey,
                            detected != null ? detected.normalizedUrl() : url.trim(),
                            firstNonBlank(asText(item, "name"), asText(item, "title")),
                            firstNonBlank(asText(item, "thumbUrl"), asText(item, "thumbnailUrl")),
                            asInteger(item.get("length")),
                            parseLocalDateTime(asText(item, "publishDate")),
                            true,
                            pvIndex++));
        }

        VocadbPrefillResponse.SongRelationPrefill relation = null;
        JsonNode originalVersion = toJsonNode(row.get("original_version"));
        if (originalVersion != null && originalVersion.isObject()) {
            long targetVocadbId = originalVersion.path("id").asLong(0);
            if (targetVocadbId > 0) {
                relation =
                        new VocadbPrefillResponse.SongRelationPrefill(
                                targetVocadbId,
                                findSongResourceUuidByVocadbId(targetVocadbId),
                                firstNonBlank(
                                        asText(originalVersion, "name"),
                                        findDumpSongCanonicalName(targetVocadbId)));
            }
        }

        return new VocadbPrefillResponse(
                true,
                "SONG",
                vocadbId,
                new VocadbPrefillResponse.SongPrefill(
                        new VocadbPrefillResponse.CanonicalNamePrefill(language, canonicalName),
                        thumbnailUrl,
                        publishedAt,
                        songType,
                        List.copyOf(links),
                        List.copyOf(pvs),
                        List.copyOf(artists),
                        List.copyOf(vocals),
                        relation),
                null,
                null);
    }

    private VocadbPrefillResponse resolveArtist(long vocadbId) {
        List<java.util.Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        """
                        SELECT
                            id,
                            name,
                            groups,
                            pictures,
                            web_links,
                            deleted
                        FROM dump_vocadb_artist
                        WHERE id = ?
                        LIMIT 1
                        """,
                        vocadbId);
        if (rows.isEmpty()) {
            return new VocadbPrefillResponse(false, "ARTIST", vocadbId, null, null, null);
        }

        java.util.Map<String, Object> row = rows.getFirst();
        if (Boolean.TRUE.equals(row.get("deleted"))) {
            return new VocadbPrefillResponse(false, "ARTIST", vocadbId, null, null, null);
        }

        String canonicalName =
                firstNonBlank(asString(row.get("name")), "VocaDB Artist " + vocadbId);
        Language language = guessLanguage(canonicalName);
        String thumbnailUrl = extractThumbnail(row.get("pictures"));

        List<VocadbPrefillResponse.LinkPrefill> links = new ArrayList<>();
        appendVocadbLink(links, "https://vocadb.net/Ar/" + vocadbId);
        appendWebLinks(links, row.get("web_links"), LinkNormalization.ARTIST);

        ArrayList<VocadbPrefillResponse.ArtistMemberPrefill> members = new ArrayList<>();
        int memberSortOrder = 0;
        for (JsonNode group : iterableArray(row.get("groups"))) {
            long groupVocadbId = group.path("id").asLong(0);
            if (groupVocadbId <= 0) {
                continue;
            }
            members.add(
                    new VocadbPrefillResponse.ArtistMemberPrefill(
                            groupVocadbId,
                            findArtistResourceUuidByVocadbId(groupVocadbId),
                            firstNonBlank(asText(group, "name"), asText(group, "defaultName")),
                            memberSortOrder++));
        }

        VocadbPrefillResponse.ArtistPrefill artist =
                new VocadbPrefillResponse.ArtistPrefill(
                        new VocadbPrefillResponse.CanonicalNamePrefill(language, canonicalName),
                        thumbnailUrl,
                        List.copyOf(links),
                        List.copyOf(members));

        VocadbPrefillResponse.VocalPrefill vocal = null;
        String detectedArtistType = findVocadbArtistType(vocadbId);
        if (isVocalistType(detectedArtistType)) {
            vocal =
                    new VocadbPrefillResponse.VocalPrefill(
                            new VocadbPrefillResponse.CanonicalNamePrefill(language, canonicalName),
                            thumbnailUrl,
                            List.copyOf(links));
        }

        return new VocadbPrefillResponse(true, "ARTIST", vocadbId, null, artist, vocal);
    }

    private UUID findSongResourceUuidByVocadbId(long vocadbId) {
        return findResourceUuidByVocadbId(
                """
                SELECT r.uuid
                FROM resources r
                JOIN songs s ON s.id = r.id
                JOIN song_links sl ON sl.song_id = s.id
                WHERE sl.song_link_type = 'VOCADB'
                  AND substring(sl.url from '([0-9]+)$')::bigint = ?
                ORDER BY sl.id ASC
                LIMIT 1
                """,
                vocadbId);
    }

    private UUID findArtistResourceUuidByVocadbId(long vocadbId) {
        return findResourceUuidByVocadbId(
                """
                SELECT r.uuid
                FROM resources r
                JOIN artists a ON a.id = r.id
                JOIN artist_links al ON al.artist_id = a.id
                WHERE al.artist_link_type = 'VOCADB'
                  AND substring(al.url from '([0-9]+)$')::bigint = ?
                ORDER BY al.id ASC
                LIMIT 1
                """,
                vocadbId);
    }

    private UUID findVocalResourceUuidByVocadbId(long vocadbId) {
        return findResourceUuidByVocadbId(
                """
                SELECT r.uuid
                FROM resources r
                JOIN vocals v ON v.id = r.id
                JOIN vocal_links vl ON vl.vocal_id = v.id
                WHERE vl.vocal_link_type = 'VOCADB'
                  AND substring(vl.url from '([0-9]+)$')::bigint = ?
                ORDER BY vl.id ASC
                LIMIT 1
                """,
                vocadbId);
    }

    private UUID findResourceUuidByVocadbId(String sql, long vocadbId) {
        List<UUID> uuids =
                jdbcTemplate.query(
                        sql,
                        (rs, rowNum) -> {
                            Object value = rs.getObject(1);
                            if (value instanceof UUID uuid) {
                                return uuid;
                            }
                            return UUID.fromString(String.valueOf(value));
                        },
                        vocadbId);
        return uuids.isEmpty() ? null : uuids.getFirst();
    }

    private UUID ensureArtistResourceUuid(
            long vocadbArtistId,
            String fallbackName,
            List<String> roles,
            java.util.Map<Long, UUID> createdArtists) {
        if (vocadbArtistId <= 0) {
            return null;
        }
        UUID cached = createdArtists.get(vocadbArtistId);
        if (cached != null) {
            return cached;
        }
        UUID existing = findArtistResourceUuidByVocadbId(vocadbArtistId);
        if (existing != null) {
            createdArtists.put(vocadbArtistId, existing);
            return existing;
        }
        if (!hasAllowedArtistRole(roles)) {
            return null;
        }
        DumpArtistRow row = loadDumpArtistRow(vocadbArtistId);
        if (row == null || row.deleted()) {
            return null;
        }
        UUID created = createArtistFromDump(vocadbArtistId, row, fallbackName);
        if (created != null) {
            createdArtists.put(vocadbArtistId, created);
        }
        return created;
    }

    private UUID ensureVocalResourceUuid(
            long vocadbArtistId,
            String fallbackName,
            java.util.Map<Long, UUID> createdVocals) {
        if (vocadbArtistId <= 0) {
            return null;
        }
        UUID cached = createdVocals.get(vocadbArtistId);
        if (cached != null) {
            return cached;
        }
        UUID existing = findVocalResourceUuidByVocadbId(vocadbArtistId);
        if (existing != null) {
            createdVocals.put(vocadbArtistId, existing);
            return existing;
        }
        DumpArtistRow row = loadDumpArtistRow(vocadbArtistId);
        if (row == null || row.deleted()) {
            return null;
        }
        UUID created = createVocalFromDump(vocadbArtistId, row, fallbackName);
        if (created != null) {
            createdVocals.put(vocadbArtistId, created);
        }
        return created;
    }

    private boolean hasAllowedArtistRole(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        boolean hasAllowed = false;
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            String normalized = role.trim().toUpperCase(Locale.ROOT);
            if (ALLOWED_SONG_ARTIST_ROLES.contains(normalized)) {
                hasAllowed = true;
            }
        }
        return hasAllowed;
    }

    private record DumpArtistRow(String name, Object pictures, Object webLinks, boolean deleted) {}

    private DumpArtistRow loadDumpArtistRow(long vocadbArtistId) {
        List<java.util.Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        """
                        SELECT
                            name,
                            pictures,
                            web_links,
                            deleted
                        FROM dump_vocadb_artist
                        WHERE id = ?
                        LIMIT 1
                        """,
                        vocadbArtistId);
        if (rows.isEmpty()) {
            return null;
        }
        java.util.Map<String, Object> row = rows.getFirst();
        boolean deleted = Boolean.TRUE.equals(row.get("deleted"));
        return new DumpArtistRow(
                asString(row.get("name")),
                row.get("pictures"),
                row.get("web_links"),
                deleted);
    }

    private UUID createArtistFromDump(long vocadbArtistId, DumpArtistRow row, String fallbackName) {
        String canonicalName =
                firstNonBlank(row.name(), fallbackName, "VocaDB Artist " + vocadbArtistId);
        if (canonicalName == null) {
            return null;
        }
        Language language = guessLanguage(canonicalName);
        String thumbnailUrl = extractThumbnail(row.pictures());
        List<ArtistCreateRequest.ArtistLinkCreateRequest> links =
                buildArtistLinkRequests(vocadbArtistId, row.webLinks());
        ArtistCreateRequest request =
                new ArtistCreateRequest(
                        new ArtistCreateRequest.CanonicalNameCreateRequest(language, canonicalName),
                        thumbnailUrl,
                        null,
                        links,
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null);
        return artistService.create(request);
    }

    private UUID createVocalFromDump(long vocadbArtistId, DumpArtistRow row, String fallbackName) {
        String canonicalName =
                firstNonBlank(row.name(), fallbackName, "VocaDB Vocal " + vocadbArtistId);
        if (canonicalName == null) {
            return null;
        }
        Language language = guessLanguage(canonicalName);
        String thumbnailUrl = extractThumbnail(row.pictures());
        List<VocalCreateRequest.VocalLinkCreateRequest> links =
                buildVocalLinkRequests(vocadbArtistId, row.webLinks());
        VocalCreateRequest request =
                new VocalCreateRequest(
                        new VocalCreateRequest.CanonicalNameCreateRequest(language, canonicalName),
                        thumbnailUrl,
                        null,
                        links,
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null);
        return vocalService.create(request);
    }

    private List<ArtistCreateRequest.ArtistLinkCreateRequest> buildArtistLinkRequests(
            long vocadbArtistId, Object webLinks) {
        List<VocadbPrefillResponse.LinkPrefill> links = new ArrayList<>();
        appendVocadbLink(links, "https://vocadb.net/Ar/" + vocadbArtistId);
        appendWebLinks(links, webLinks, LinkNormalization.ARTIST);
        return links.stream()
                .map(
                        link ->
                                new ArtistCreateRequest.ArtistLinkCreateRequest(
                                        link.type(), link.url(), link.content(), link.isDeleted()))
                .toList();
    }

    private List<VocalCreateRequest.VocalLinkCreateRequest> buildVocalLinkRequests(
            long vocadbArtistId, Object webLinks) {
        List<VocadbPrefillResponse.LinkPrefill> links = new ArrayList<>();
        appendVocadbLink(links, "https://vocadb.net/Ar/" + vocadbArtistId);
        appendWebLinks(links, webLinks, LinkNormalization.ARTIST);
        return links.stream()
                .map(
                        link ->
                                new VocalCreateRequest.VocalLinkCreateRequest(
                                        link.type(), link.url(), link.content(), link.isDeleted()))
                .toList();
    }

    private String findDumpSongCanonicalName(long vocadbId) {
        List<String> values =
                jdbcTemplate.query(
                        """
                        SELECT song_default_name
                        FROM dump_vocadb_song
                        WHERE id = ?
                        LIMIT 1
                        """,
                        (rs, rowNum) -> rs.getString(1),
                        vocadbId);
        return values.isEmpty() ? null : firstNonBlank(values.getFirst());
    }

    private String findVocadbArtistType(long vocadbArtistId) {
        List<String> values =
                jdbcTemplate.query(
                        """
                        SELECT item->'artist'->>'artistType'
                        FROM dump_vocadb_song song
                        CROSS JOIN LATERAL jsonb_array_elements(song.artists) item
                        WHERE (item->'artist'->>'id')::bigint = ?
                          AND item->'artist'->>'artistType' IS NOT NULL
                        ORDER BY song.id ASC
                        LIMIT 1
                        """,
                        (rs, rowNum) -> rs.getString(1),
                        vocadbArtistId);
        return values.isEmpty() ? null : values.getFirst();
    }

    private List<DumpSongArtistRef> parseSongArtists(Object rawValue) {
        ArrayList<DumpSongArtistRef> items = new ArrayList<>();
        int sortOrder = 0;
        for (JsonNode item : iterableArray(rawValue)) {
            JsonNode artist = item.path("artist");
            long artistId = artist.path("id").asLong(0);
            String name =
                    firstNonBlank(
                            artist.path("name").asText(null),
                            item.path("name").asText(null),
                            item.path("artistString").asText(null));
            String artistType = artist.path("artistType").asText(null);
            String rolesRaw =
                    firstNonBlank(
                            item.path("roles").asText(null),
                            item.path("effectiveRoles").asText(null));
            ArrayList<String> roles = new ArrayList<>();
            if (rolesRaw != null) {
                for (String role : rolesRaw.split(",")) {
                    String trimmed = role.trim();
                    if (!trimmed.isEmpty()) {
                        roles.add(trimmed);
                    }
                }
            }
            if (artistId <= 0 && (name == null || name.isBlank())) {
                continue;
            }
            items.add(
                    new DumpSongArtistRef(
                            artistId, name, artistType, List.copyOf(roles), sortOrder++));
        }
        return items;
    }

    private void appendVocadbLink(List<VocadbPrefillResponse.LinkPrefill> links, String url) {
        links.add(new VocadbPrefillResponse.LinkPrefill("VOCADB", url, null, false));
    }

    private enum LinkNormalization {
        SONG,
        ARTIST
    }

    private void appendWebLinks(
            List<VocadbPrefillResponse.LinkPrefill> links,
            Object rawValue,
            LinkNormalization normalization) {
        LinkedHashSet<String> seenUrls = new LinkedHashSet<>();
        for (VocadbPrefillResponse.LinkPrefill link : links) {
            seenUrls.add(normalizeUrlKey(link.url()));
        }
        for (JsonNode item : iterableArray(rawValue)) {
            String url = asText(item, "url");
            if (url == null || url.isBlank()) {
                continue;
            }
            if (item.path("disabled").asBoolean(false)) {
                continue;
            }
            if (!seenUrls.add(normalizeUrlKey(url))) {
                continue;
            }
            String description = firstNonBlank(asText(item, "description"), asText(item, "name"));
            VocadbPrefillResponse.LinkPrefill normalized =
                    normalization == LinkNormalization.SONG
                            ? normalizeSongWebLink(url.trim(), description)
                            : normalizeArtistWebLink(url.trim(), description);
            if (normalized != null) {
                links.add(normalized);
            }
        }
    }

    private VocadbPrefillResponse.LinkPrefill normalizeSongWebLink(
            String url, String description) {
        String desc = description == null ? "" : description.trim();
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception ignored) {
            return null;
        }
        String host = uri.getHost();
        if (host == null) {
            return null;
        }
        host = host.toLowerCase();
        String path = uri.getPath() == null ? "" : uri.getPath();

        return switch (host) {
            case "piapro.jp" -> {
                if (isPiaproInstrumental(desc)) {
                    yield new VocadbPrefillResponse.LinkPrefill("PIAPRO", url, "Piapro (Instrumental)", false);
                }
                if (isPiaproLyrics(desc)) {
                    yield new VocadbPrefillResponse.LinkPrefill("PIAPRO", url, "Piapro (Lyrics)", false);
                }
                if (isPiaproIllustration(desc)) {
                    yield new VocadbPrefillResponse.LinkPrefill("PIAPRO", url, "Piapro (Illustration)", false);
                }
                yield null;
            }
            case "soundcloud.com" -> isSoundCloudInstrumental(desc)
                    ? new VocadbPrefillResponse.LinkPrefill("SOUNDCLOUD", url, "SoundCloud (Instrumental)", false)
                    : null;
            case "www5.atwiki.jp" -> new VocadbPrefillResponse.LinkPrefill("ATWIKI", url, "初音ミク Wiki", false);
            case "w.atwiki.jp" -> path.contains("/hmiku/")
                    ? new VocadbPrefillResponse.LinkPrefill("ATWIKI", url, "初音ミク Wiki", false)
                    : null;
            case "www.pixiv.net" -> new VocadbPrefillResponse.LinkPrefill("PIXIV", url, "Pixiv", false);
            case "music.163.com", "y.music.163.com" -> {
                if (isNetEaseInstrumental(desc)) {
                    yield new VocadbPrefillResponse.LinkPrefill("NETEASE_MUSIC", url, "NCM Instrumental", false);
                }
                if (isNetEaseAlbum(desc)) {
                    yield new VocadbPrefillResponse.LinkPrefill("NETEASE_MUSIC", url, "NCM Album", false);
                }
                if (isNetEaseRelease(desc)) {
                    yield new VocadbPrefillResponse.LinkPrefill("NETEASE_MUSIC", url, "NCM Release", false);
                }
                yield null;
            }
            case "x.com" -> {
                if (isXIllustration(desc)) {
                    yield new VocadbPrefillResponse.LinkPrefill("X", url, "X (Illustration)", false);
                }
                if (isXDefault(desc)) {
                    yield new VocadbPrefillResponse.LinkPrefill("X", url, "X", false);
                }
                yield null;
            }
            case "open.spotify.com" -> {
                String label = classifySpotifyPath(path);
                yield label == null ? null : new VocadbPrefillResponse.LinkPrefill("SPOTIFY", url, label, false);
            }
            case "music.apple.com" -> {
                String label = classifyAppleMusicPath(path);
                yield label == null ? null : new VocadbPrefillResponse.LinkPrefill("APPLE_MUSIC", url, label, false);
            }
            case "utaitedb.net" -> isUtaiteDBOriginal(desc)
                    ? new VocadbPrefillResponse.LinkPrefill("UTAITEDB", url, "UtaiteDB (Original)", false)
                    : null;
            case "www.youtube.com", "youtu.be" -> {
                if (isYouTubeInstrumental(desc)) {
                    yield new VocadbPrefillResponse.LinkPrefill("YOUTUBE", url, "YouTube (Instrumental)", false);
                }
                if (isYouTubeOriginal(desc)) {
                    yield new VocadbPrefillResponse.LinkPrefill("YOUTUBE", url, "YouTube (Original)", false);
                }
                yield null;
            }
            case "www.nicovideo.jp" -> {
                if (isNicoNicoInstrumental(desc)) {
                    yield new VocadbPrefillResponse.LinkPrefill("NICONICO", url, "NicoNico (Instrumental)", false);
                }
                if (isNicoNicoOriginal(desc)) {
                    yield new VocadbPrefillResponse.LinkPrefill("NICONICO", url, "NicoNico (Original)", false);
                }
                yield null;
            }
            case "dic.nicovideo.jp" -> "NicoNicoPedia".equals(desc)
                    ? new VocadbPrefillResponse.LinkPrefill("NICONICO_PEDIA", url, "NicoNicoPedia", false)
                    : null;
            case "commons.nicovideo.jp" -> {
                if (isNicommonsInstrumental(desc)) {
                    yield new VocadbPrefillResponse.LinkPrefill("NICOMMONS", url, "Nicommons (Instrumental)", false);
                }
                if (isNicommonsIllustration(desc)) {
                    yield new VocadbPrefillResponse.LinkPrefill("NICOMMONS", url, "Nicommons (Illustration)", false);
                }
                yield null;
            }
            default -> null;
        };
    }

    private VocadbPrefillResponse.LinkPrefill normalizeArtistWebLink(
            String url, String description) {
        String desc = description == null ? "" : description.trim();
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception ignored) {
            return null;
        }
        String hostRaw = uri.getHost();
        if (hostRaw == null) {
            return null;
        }
        hostRaw = hostRaw.toLowerCase();
        String host = normalizeHost(hostRaw);
        String path = uri.getPath() == null ? "" : uri.getPath();
        List<String> parts = splitPath(path);

        if (hostRaw.endsWith(".fanbox.cc") || hostRaw.equals("fanbox.cc")) {
            return new VocadbPrefillResponse.LinkPrefill("PIXIV", url, "Pixiv (Fanbox)", false);
        }
        if (hostRaw.endsWith(".tumblr.com") && !hostRaw.equals("tumblr.com")) {
            return new VocadbPrefillResponse.LinkPrefill("TUMBLR", url, "Tumblr (Blog)", false);
        }

        return switch (host) {
            case "piapro.jp" -> new VocadbPrefillResponse.LinkPrefill("PIAPRO", url, "Piapro (User)", false);
            case "pixiv.net" -> isPixivUserPath(parts)
                    ? new VocadbPrefillResponse.LinkPrefill("PIXIV", url, "Pixiv (User)", false)
                    : null;
            case "soundcloud.com" -> {
                String label = classifySoundCloudArtistPath(parts);
                yield label == null ? null : new VocadbPrefillResponse.LinkPrefill("SOUNDCLOUD", url, label, false);
            }
            case "instagram.com" -> isInstagramUserPath(parts)
                    ? new VocadbPrefillResponse.LinkPrefill("INSTAGRAM", url, "Instagram (User)", false)
                    : null;
            case "twitch.tv" -> isTwitchUserPath(parts)
                    ? new VocadbPrefillResponse.LinkPrefill("TWITCH", url, "Twitch (User)", false)
                    : null;
            case "space.bilibili.com" -> parts.isEmpty()
                    ? null
                    : new VocadbPrefillResponse.LinkPrefill("BILIBILI", url, "Bilibili (Space)", false);
            case "utaitedb.net" -> isUtaiteDBArtistPath(parts)
                    ? new VocadbPrefillResponse.LinkPrefill("UTAITEDB", url, "UtaiteDB (Artist)", false)
                    : null;
            case "facebook.com" -> {
                String label = classifyFacebookArtistPath(parts);
                yield label == null ? null : new VocadbPrefillResponse.LinkPrefill("FACEBOOK", url, label, false);
            }
            case "skeb.jp" -> isSkebUserPath(parts)
                    ? new VocadbPrefillResponse.LinkPrefill("SKEB", url, "Skeb (User)", false)
                    : null;
            case "open.spotify.com", "play.spotify.com" -> {
                String label = classifySpotifyArtistPath(parts);
                yield label == null ? null : new VocadbPrefillResponse.LinkPrefill("SPOTIFY", url, label, false);
            }
            case "tiktok.com" -> isTikTokUserPath(parts)
                    ? new VocadbPrefillResponse.LinkPrefill("TIKTOK", url, "TikTok (User)", false)
                    : null;
            case "tumblr.com" -> isTumblrBlogPath(parts)
                    ? new VocadbPrefillResponse.LinkPrefill("TUMBLR", url, "Tumblr (Blog)", false)
                    : null;
            case "bowlroll.net" -> {
                String label = classifyBowlRollArtistPath(parts);
                yield label == null ? null : new VocadbPrefillResponse.LinkPrefill("BOWLROLL", url, label, false);
            }
            case "utau.fandom.com" -> new VocadbPrefillResponse.LinkPrefill("FANDOM", url, "UTAU Wiki", false);
            case "utau.wikia.com" -> new VocadbPrefillResponse.LinkPrefill("FANDOM", url, "UTAU Wiki (Wikia)", false);
            case "vocaloid.wikia.com" -> new VocadbPrefillResponse.LinkPrefill("FANDOM", url, "Vocaloid Wiki (Wikia)", false);
            case "weibo.com" -> isWeiboProfilePath(parts)
                    ? new VocadbPrefillResponse.LinkPrefill("WEIBO", url, "Weibo (Profile)", false)
                    : null;
            case "twpf.jp" -> new VocadbPrefillResponse.LinkPrefill("TWPF", url, "Twpf", false);
            case "www5.atwiki.jp" -> path.contains("/hmiku/")
                    ? new VocadbPrefillResponse.LinkPrefill("ATWIKI", url, "初音ミク Wiki", false)
                    : null;
            case "ameblo.jp" -> isAmebloUserPath(parts)
                    ? new VocadbPrefillResponse.LinkPrefill("AMEBLO", url, "Ameblo", false)
                    : null;
            case "bsky.app" -> parts.size() >= 2 && "profile".equals(parts.get(0))
                    ? new VocadbPrefillResponse.LinkPrefill("BLUESKY", url, "Bluesky", false)
                    : null;
            case "discogs.com" -> {
                String label = classifyDiscogsPath(parts);
                yield label == null ? null : new VocadbPrefillResponse.LinkPrefill("DISCOGS", url, label, false);
            }
            case "music.163.com", "y.music.163.com" -> {
                String label = classifyNetEaseArtistPath(parts, uri.getQuery(), uri.getFragment());
                yield label == null
                        ? null
                        : new VocadbPrefillResponse.LinkPrefill("NETEASE_MUSIC", url, label, false);
            }
            case "tunecore.co.jp" -> isTuneCoreArtistPath(parts, uri.getQuery())
                    ? new VocadbPrefillResponse.LinkPrefill("TUNECORE", url, "TuneCore (Artist)", false)
                    : null;
            case "vgmdb.net" -> {
                String label = classifyVgmdbPath(parts);
                yield label == null ? null : new VocadbPrefillResponse.LinkPrefill("VGMDB", url, label, false);
            }
            case "ko-fi.com" -> isSimpleUserPath(parts)
                    ? new VocadbPrefillResponse.LinkPrefill("KOFI", url, "Ko-fi", false)
                    : null;
            case "ja.wikipedia.org" -> isWikipediaPath(parts)
                    ? new VocadbPrefillResponse.LinkPrefill("WIKIPEDIA", url, "Wikipedia (JA)", false)
                    : null;
            case "en.wikipedia.org" -> isWikipediaPath(parts)
                    ? new VocadbPrefillResponse.LinkPrefill("WIKIPEDIA", url, "Wikipedia (EN)", false)
                    : null;
            case "sites.google.com" -> isGoogleSitesPath(parts)
                    ? new VocadbPrefillResponse.LinkPrefill("GOOGLE_SITES", url, "Google Sites", false)
                    : null;
            case "utaudatabase.wiki.fc2.com" -> new VocadbPrefillResponse.LinkPrefill(
                    "FC2_WIKI", url, "UTAU Database Wiki", false);
            case "utau.wikidot.com" -> new VocadbPrefillResponse.LinkPrefill("WIKIDOT", url, "UTAU Wikidot", false);
            case "utau.wiki" -> new VocadbPrefillResponse.LinkPrefill("UTAU_WIKI", url, "UTAU Wiki", false);
            case "vocaloidlyrics.miraheze.org" -> new VocadbPrefillResponse.LinkPrefill(
                    "MIRAHEZE", url, "Vocaloid Lyrics Wiki", false);
            case "linktr.ee" -> isSimpleUserPath(parts)
                    ? new VocadbPrefillResponse.LinkPrefill("LINKTREE", url, "Linktree", false)
                    : null;
            case "note.com" -> isSimpleUserPath(parts)
                    ? new VocadbPrefillResponse.LinkPrefill("NOTE", url, "note", false)
                    : null;
            case "karent.jp" -> parts.size() >= 2 && "artist".equals(parts.get(0))
                    ? new VocadbPrefillResponse.LinkPrefill("KARENT", url, "KARENT (Artist)", false)
                    : null;
            case "musicbrainz.org" -> {
                String label = classifyMusicBrainzPath(parts);
                yield label == null
                        ? null
                        : new VocadbPrefillResponse.LinkPrefill("MUSICBRAINZ", url, label, false);
            }
            case "music.apple.com" -> {
                String label = classifyAppleMusicArtistPath(parts);
                yield label == null
                        ? null
                        : new VocadbPrefillResponse.LinkPrefill("APPLE_MUSIC", url, label, false);
            }
            case "touhoudb.com" -> parts.size() >= 2 && "ar".equalsIgnoreCase(parts.get(0))
                    ? new VocadbPrefillResponse.LinkPrefill("TOUHOUDB", url, "TouhouDB (Artist)", false)
                    : null;
            case "deviantart.com" -> isDeviantArtUserPath(parts)
                    ? new VocadbPrefillResponse.LinkPrefill("DEVIANTART", url, "DeviantArt (User)", false)
                    : null;
            case "lit.link" -> isSimpleUserPath(parts)
                    ? new VocadbPrefillResponse.LinkPrefill("LITLINK", url, "lit.link", false)
                    : null;
            case "x.com", "twitter.com" -> isXDefault(desc) && isXHandlePath(parts)
                    ? new VocadbPrefillResponse.LinkPrefill("X", url, "X (Twitter)", false)
                    : null;
            case "dic.nicovideo.jp" -> isNicoPediaPath(parts)
                    ? new VocadbPrefillResponse.LinkPrefill("NICONICO", url, "NicoNicoPedia", false)
                    : null;
            case "seiga.nicovideo.jp" -> isNicoSeigaIllustrationPath(parts)
                    ? new VocadbPrefillResponse.LinkPrefill(
                            "NICONICO", url, "NicoNico Seiga (Illustration)", false)
                    : null;
            case "nicovideo.jp", "com.nicovideo.jp" -> {
                String label = classifyNicoNicoArtistPath(parts);
                yield label == null ? null : new VocadbPrefillResponse.LinkPrefill("NICONICO", url, label, false);
            }
            default -> {
                if (host.endsWith("youtube.com")) {
                    String label = classifyYouTubeArtistPath(parts);
                    yield label == null
                            ? null
                            : new VocadbPrefillResponse.LinkPrefill("YOUTUBE", url, label, false);
                }
                yield null;
            }
        };
    }

    private boolean isPiaproInstrumental(String desc) {
        String l = desc.toLowerCase();
        return l.contains("karaoke")
                || l.contains("karoake")
                || l.contains("off vocal")
                || l.contains("offvo")
                || l.contains("instrumental")
                || l.contains("no vocal")
                || l.contains("without vocal")
                || l.contains("without main vocal")
                || l.contains("without bass")
                || l.contains("backing track")
                || l.contains("chorus only")
                || l.contains("voiceless")
                || l.contains("drumless")
                || l.contains("inst.")
                || desc.contains("カラオケ")
                || desc.contains("オケ")
                || desc.contains("オフボーカル")
                || desc.contains("ハモリなし")
                || desc.contains("抜きVer")
                || desc.contains("コーラス付")
                || desc.contains("男性キー")
                || desc.contains("音声無し")
                || desc.contains("メインボーカル無し")
                || desc.contains("ドラムレス")
                || desc.contains("ガイド用クリック");
    }

    private boolean isPiaproLyrics(String desc) {
        String l = desc.toLowerCase();
        return l.contains("lyric")
                || l.contains("lyrics")
                || l.contains("translation")
                || desc.contains("歌詞");
    }

    private boolean isPiaproIllustration(String desc) {
        String l = desc.toLowerCase();
        return l.contains("illustr")
                || l.contains("illust")
                || l.contains("illus.")
                || l.contains("ilustration")
                || l.contains("image")
                || l.contains("artwork")
                || l.contains("logo")
                || l.contains("pixel art")
                || l.contains("cover art")
                || l.contains("background")
                || l.contains("avatar")
                || l.contains("photograph")
                || desc.contains("各パートmp3");
    }

    private boolean isSoundCloudInstrumental(String desc) {
        String l = desc.toLowerCase();
        return l.contains("instrumental")
                || l.contains("inst")
                || l.contains("off vocal")
                || l.contains("off-vocal")
                || l.contains("offvocal")
                || l.contains("karaoke")
                || l.contains("no vocal")
                || l.contains("without vocal")
                || l.contains("without main vocal")
                || l.contains("offvo");
    }

    private boolean isNetEaseInstrumental(String desc) {
        return hasAny(desc,
                "instrumental",
                "inst",
                "off vocal",
                "off-vocal",
                "karaoke",
                "カラオケ",
                "オフボーカル",
                "offvo");
    }

    private boolean isNetEaseAlbum(String desc) {
        return desc.toLowerCase().contains("album");
    }

    private boolean isNetEaseRelease(String desc) {
        String l = desc.toLowerCase();
        if (l.contains("release")
                || l.contains("song release")
                || l.contains("single release")
                || l.contains("digital release")
                || l.contains("mp3")) {
            return true;
        }
        return desc.contains("网易云音乐")
                || desc.equals("NCM Song Release")
                || desc.equals("NCM Song Release (Album ver.)")
                || desc.equals("NCM Song Release (blocked both inside and outside China?)")
                || desc.equals("NCM Song Release - Instrumental")
                || desc.equals("NCM Song Release (off vocal)")
                || desc.equals("NCM Song Release (instrumental)")
                || desc.equals("NCM Song Release (Instrumental - female version)")
                || desc.equals("NCM Song Release (Instrumental - male version)")
                || desc.equals("NCM Song Release (Instrumental)")
                || desc.equals("NCM Album Release")
                || desc.equals("NCM Song Release (inst)");
    }

    private boolean isXIllustration(String desc) {
        return hasAny(desc,
                "illustration",
                "illustrations",
                "illust",
                "artwork",
                "cover",
                "image",
                "art",
                "イラスト",
                "絵",
                "画像",
                "ジャケット");
    }

    private boolean isXDefault(String desc) {
        return desc.isBlank() || desc.equals("X") || desc.equals("Twitter") || desc.equals("X (Twitter)");
    }

    private String normalizeHost(String host) {
        String normalized = host == null ? "" : host.trim().toLowerCase();
        if (normalized.startsWith("www.")) {
            normalized = normalized.substring(4);
        }
        if (normalized.startsWith("m.")) {
            normalized = normalized.substring(2);
        }
        if (normalized.startsWith("mobile.")) {
            normalized = normalized.substring("mobile.".length());
        }
        return normalized;
    }

    private boolean isPixivUserPath(List<String> parts) {
        return parts.size() >= 2 && "users".equals(parts.get(0));
    }

    private String classifySoundCloudArtistPath(List<String> parts) {
        if (parts.isEmpty()) {
            return null;
        }
        if (parts.size() > 1 && "sets".equals(parts.get(1))) {
            return "SoundCloud (Playlist)";
        }
        switch (parts.getFirst()) {
            case "discover", "charts", "search", "stream", "you" -> {
                return null;
            }
            default -> {
                return "SoundCloud (User)";
            }
        }
    }

    private boolean isInstagramUserPath(List<String> parts) {
        if (parts.isEmpty()) {
            return false;
        }
        return switch (parts.getFirst()) {
            case "p", "reel", "tv", "stories", "explore", "accounts" -> false;
            default -> true;
        };
    }

    private boolean isTwitchUserPath(List<String> parts) {
        if (parts.isEmpty()) {
            return false;
        }
        return switch (parts.getFirst()) {
            case "directory", "videos", "p", "downloads", "store", "settings" -> false;
            default -> true;
        };
    }

    private boolean isUtaiteDBArtistPath(List<String> parts) {
        if (parts.isEmpty()) {
            return false;
        }
        String first = parts.getFirst();
        return "artist".equalsIgnoreCase(first) || "artists".equalsIgnoreCase(first) || "ar".equalsIgnoreCase(first);
    }

    private String classifyFacebookArtistPath(List<String> parts) {
        if (parts.isEmpty()) {
            return null;
        }
        if ("pages".equals(parts.getFirst())) {
            return "Facebook (Page)";
        }
        if ("profile.php".equals(parts.getFirst())) {
            return "Facebook (Profile)";
        }
        return switch (parts.getFirst()) {
            case "home", "groups", "pages", "events", "watch", "marketplace" -> null;
            default -> "Facebook (Profile)";
        };
    }

    private boolean isSkebUserPath(List<String> parts) {
        if (parts.isEmpty()) {
            return false;
        }
        return parts.getFirst().startsWith("@") || !parts.getFirst().isBlank();
    }

    private String classifySpotifyArtistPath(List<String> parts) {
        if (parts.isEmpty()) {
            return null;
        }
        String kind = parts.getFirst();
        if (kind.startsWith("intl-") && parts.size() > 1) {
            kind = parts.get(1);
        }
        return switch (kind) {
            case "artist" -> "Spotify (Artist)";
            case "album" -> "Spotify (Album)";
            case "playlist" -> "Spotify (Playlist)";
            default -> null;
        };
    }

    private boolean isTikTokUserPath(List<String> parts) {
        return !parts.isEmpty() && parts.getFirst().startsWith("@");
    }

    private boolean isTumblrBlogPath(List<String> parts) {
        return !parts.isEmpty();
    }

    private String classifyBowlRollArtistPath(List<String> parts) {
        if (parts.isEmpty()) {
            return null;
        }
        return switch (parts.getFirst()) {
            case "file" -> "BowlRoll (File)";
            case "user" -> "BowlRoll (User)";
            default -> null;
        };
    }

    private boolean isWeiboProfilePath(List<String> parts) {
        if (parts.isEmpty()) {
            return false;
        }
        return "u".equals(parts.getFirst()) ? parts.size() > 1 : true;
    }

    private boolean isAmebloUserPath(List<String> parts) {
        return !parts.isEmpty();
    }

    private String classifyDiscogsPath(List<String> parts) {
        if (parts.size() < 2) {
            return null;
        }
        return switch (parts.getFirst()) {
            case "artist" -> "Discogs (Artist)";
            case "label" -> "Discogs (Label)";
            default -> null;
        };
    }

    private String classifyNetEaseArtistPath(List<String> parts, String query, String fragment) {
        if (!parts.isEmpty()) {
            return switch (parts.getFirst()) {
                case "artist" -> "NCM Artist";
                case "album" -> "NCM Album";
                case "playlist" -> "NCM Playlist";
                default -> null;
            };
        }
        if (fragment != null) {
            String lower = fragment.toLowerCase();
            if (lower.contains("artist")) {
                return "NCM Artist";
            }
            if (lower.contains("album")) {
                return "NCM Album";
            }
            if (lower.contains("playlist")) {
                return "NCM Playlist";
            }
        }
        return null;
    }

    private boolean isTuneCoreArtistPath(List<String> parts, String query) {
        if (parts.isEmpty()) {
            return false;
        }
        String first = parts.getFirst();
        return "artist".equals(first) || "artists".equals(first);
    }

    private String classifyVgmdbPath(List<String> parts) {
        if (parts.size() < 2) {
            return null;
        }
        return switch (parts.getFirst()) {
            case "artist" -> "VGMdb (Artist)";
            case "album" -> "VGMdb (Album)";
            default -> null;
        };
    }

    private boolean isSimpleUserPath(List<String> parts) {
        return !parts.isEmpty();
    }

    private boolean isWikipediaPath(List<String> parts) {
        return !parts.isEmpty();
    }

    private boolean isGoogleSitesPath(List<String> parts) {
        if (parts.isEmpty()) {
            return false;
        }
        String first = parts.getFirst();
        return "view".equals(first) || "site".equals(first) || "a".equals(first);
    }

    private String classifyMusicBrainzPath(List<String> parts) {
        if (parts.size() < 2) {
            return null;
        }
        return switch (parts.getFirst()) {
            case "artist" -> "MusicBrainz (Artist)";
            case "label" -> "MusicBrainz (Label)";
            default -> null;
        };
    }

    private String classifyAppleMusicArtistPath(List<String> parts) {
        if (parts.size() < 2) {
            return null;
        }
        String kind = parts.get(1);
        return switch (kind) {
            case "artist" -> "Apple Music (Artist)";
            case "album" -> "Apple Music (Album)";
            case "playlist" -> "Apple Music (Playlist)";
            case "music-video" -> "Apple Music (Music Video)";
            default -> null;
        };
    }

    private boolean isDeviantArtUserPath(List<String> parts) {
        return !parts.isEmpty();
    }

    private boolean isXHandlePath(List<String> parts) {
        if (parts.isEmpty()) {
            return false;
        }
        return switch (parts.getFirst()) {
            case "home", "intent", "i", "share", "search", "hashtag", "explore" -> false;
            default -> true;
        };
    }

    private boolean isNicoPediaPath(List<String> parts) {
        return !parts.isEmpty() && ("a".equals(parts.getFirst()) || "id".equals(parts.getFirst()));
    }

    private boolean isNicoSeigaIllustrationPath(List<String> parts) {
        return parts.size() >= 2 && "user".equals(parts.get(0)) && "illust".equals(parts.get(1));
    }

    private String classifyNicoNicoArtistPath(List<String> parts) {
        if (parts.size() < 2) {
            return null;
        }
        return switch (parts.getFirst()) {
            case "user" -> "NicoNico (User)";
            case "mylist" -> "NicoNico (Mylist)";
            case "series" -> "NicoNico (Series)";
            case "community" -> "NicoNico (Community)";
            case "channel" -> "NicoNico (Channel)";
            default -> null;
        };
    }

    private String classifyYouTubeArtistPath(List<String> parts) {
        if (parts.isEmpty()) {
            return null;
        }
        String first = parts.getFirst();
        if ("channel".equals(first) && parts.size() > 1) {
            return "YouTube (Channel)";
        }
        if (first.startsWith("@")) {
            return "YouTube (Handle)";
        }
        if ("user".equals(first) && parts.size() > 1) {
            return "YouTube (User)";
        }
        if ("c".equals(first) && parts.size() > 1) {
            return "YouTube (Custom)";
        }
        if ("watch".equals(first) || "playlist".equals(first) || "shorts".equals(first)) {
            return null;
        }
        return "YouTube (Custom)";
    }

    private String classifySpotifyPath(String path) {
        List<String> parts = splitPath(path);
        if (parts.isEmpty()) {
            return null;
        }
        String kind = parts.getFirst();
        if (kind.startsWith("intl-") && parts.size() > 1) {
            kind = parts.get(1);
        }
        return switch (kind) {
            case "track" -> "Spotify";
            case "album" -> "Spotify (Album)";
            case "playlist" -> "Spotify (Playlist)";
            default -> null;
        };
    }

    private String classifyAppleMusicPath(String path) {
        List<String> parts = splitPath(path);
        if (parts.size() < 2) {
            return null;
        }
        String country = parts.getFirst();
        String kind = parts.get(1);
        return switch (kind) {
            case "album" -> String.format("Apple Music (%s) (Album)", country);
            case "playlist" -> String.format("Apple Music (%s) (Playlist)", country);
            case "music-video" -> String.format("Apple Music (%s) (Music Video)", country);
            default -> null;
        };
    }

    private boolean isUtaiteDBOriginal(String desc) {
        String l = desc.toLowerCase();
        return l.contains("original")
                || l.contains("original song")
                || l.contains("original ver")
                || l.contains("original version");
    }

    private boolean isYouTubeInstrumental(String desc) {
        return hasAny(desc,
                "instrumental",
                "inst",
                "off vocal",
                "off-vocal",
                "karaoke",
                "カラオケ",
                "オフボーカル",
                "offvo");
    }

    private boolean isYouTubeOriginal(String desc) {
        String l = desc.toLowerCase();
        return l.contains("original")
                || l.contains("original song")
                || l.contains("original ver")
                || l.contains("original version");
    }

    private boolean isNicoNicoInstrumental(String desc) {
        return hasAny(desc,
                "instrumental",
                "inst",
                "off vocal",
                "off-vocal",
                "karaoke",
                "カラオケ",
                "オフボーカル",
                "offvo");
    }

    private boolean isNicoNicoOriginal(String desc) {
        String l = desc.toLowerCase();
        return l.contains("original")
                || l.contains("original song")
                || l.contains("original ver")
                || l.contains("original version");
    }

    private boolean isNicommonsInstrumental(String desc) {
        return hasAny(desc,
                "instrumental",
                "inst",
                "off vocal",
                "off-vocal",
                "karaoke",
                "カラオケ",
                "オフボーカル",
                "offvo",
                "chorus");
    }

    private boolean isNicommonsIllustration(String desc) {
        return hasAny(desc,
                "illustration",
                "illust",
                "image",
                "artwork",
                "background",
                "イラスト",
                "絵",
                "画像",
                "背景");
    }

    private boolean hasAny(String desc, String... needles) {
        String l = desc.toLowerCase();
        for (String needle : needles) {
            if (l.contains(needle.toLowerCase()) || desc.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private List<String> splitPath(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(path.split("/"))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .toList();
    }

    private String extractThumbnail(Object rawValue) {
        for (JsonNode item : iterableArray(rawValue)) {
            JsonNode mainPicture = item.path("mainPicture");
            String url =
                    firstNonBlank(
                            asText(mainPicture, "urlThumb"),
                            asText(mainPicture, "urlOriginal"),
                            asText(item, "urlThumb"),
                            asText(item, "urlOriginal"));
            if (url != null && !url.isBlank()) {
                return url;
            }
        }
        return null;
    }

    private JsonNode toJsonNode(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        try {
            return objectMapper.readTree(String.valueOf(rawValue));
        } catch (Exception ignored) {
            return objectMapper.valueToTree(rawValue);
        }
    }

    private List<JsonNode> iterableArray(Object rawValue) {
        JsonNode jsonNode = toJsonNode(rawValue);
        if (jsonNode == null || !jsonNode.isArray()) {
            return List.of();
        }
        ArrayList<JsonNode> items = new ArrayList<>();
        jsonNode.forEach(items::add);
        return items;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    private String asText(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode field = node.path(fieldName);
        if (field.isMissingNode() || field.isNull()) {
            return null;
        }
        String value = field.asText(null);
        return value == null ? null : value.trim();
    }

    private Integer asInteger(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.canConvertToInt() ? node.intValue() : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isVocalistType(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return VOCALIST_TYPES.stream().anyMatch(item -> item.equalsIgnoreCase(value.trim()));
    }

    private List<String> mapArtistRoles(List<String> roles, String artistType) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        boolean onlyDefaultish = true;
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            String trimmed = role.trim();
            String normalized = trimmed.toLowerCase(Locale.ROOT);
            if (!normalized.equals("default")) {
                onlyDefaultish = false;
            }
            switch (normalized) {
                case "producer" -> result.add("PRODUCER");
                case "arranger" -> result.add("ARRANGER");
                case "composer" -> result.add("COMPOSER");
                case "lyricist", "lyrics" -> result.add("LYRICIST");
                case "instrumentalist" -> result.add("INSTRUMENTALIST");
                case "vocalist" -> result.add("VOCALIST");
                case "mastering" -> result.add("MASTERING");
                case "mixer" -> result.add("MIXER");
                case "voice manipulator", "voice_manipulator" -> result.add("VOICE_MANIPULATOR");
                case "default" -> {
                    // handled by fallback below
                }
                default -> {
                    // ignore unknown roles to avoid creating OTHER
                }
            }
        }

        if (result.isEmpty() || onlyDefaultish) {
            String normalizedType = artistType == null ? "" : artistType.trim().toLowerCase(Locale.ROOT);
            if (normalizedType.equals("producer") || normalizedType.equals("coverartist")) {
                result.add("PRODUCER");
            }
        }

        return List.copyOf(result);
    }

    private String mapSongType(String value) {
        if (value == null || value.isBlank()) {
            return "OTHER";
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "original" -> "ORIGINAL";
            case "cover" -> "COVER";
            case "remix" -> "REMIX";
            case "remaster" -> "REMASTER";
            case "mashup" -> "MASHUP";
            default -> "OTHER";
        };
    }

    private String mapPvService(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "youtube", "youtubevideo", "youtubepv" -> "YOUTUBE";
            case "niconico", "niconicodouga" -> "NICONICO";
            case "bilibili" -> "BILIBILI";
            case "piapro" -> "PIAPRO";
            case "soundcloud" -> "SOUNDCLOUD";
            default -> value.trim().toUpperCase(Locale.ROOT);
        };
    }

    private Language resolveLanguage(String explicit, String fallbackText) {
        if (explicit != null && !explicit.isBlank()) {
            return switch (explicit.trim().toLowerCase(Locale.ROOT)) {
                case "japanese" -> Language.JA;
                case "english" -> Language.EN;
                case "korean" -> Language.KO;
                case "chinese" -> Language.ZH;
                case "romaji" -> Language.LA;
                default -> guessLanguage(fallbackText);
            };
        }
        return guessLanguage(fallbackText);
    }

    private Language guessLanguage(String value) {
        if (value == null || value.isBlank()) {
            return Language.UND;
        }
        if (value.codePoints()
                .anyMatch(
                        codePoint ->
                                Character.UnicodeBlock.of(codePoint)
                                                == Character.UnicodeBlock.HANGUL_SYLLABLES
                                        || Character.UnicodeBlock.of(codePoint)
                                                == Character.UnicodeBlock.HANGUL_JAMO
                                        || Character.UnicodeBlock.of(codePoint)
                                                == Character.UnicodeBlock
                                                        .HANGUL_COMPATIBILITY_JAMO)) {
            return Language.KO;
        }
        if (value.codePoints()
                .anyMatch(
                        codePoint -> {
                            Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
                            return block == Character.UnicodeBlock.HIRAGANA
                                    || block == Character.UnicodeBlock.KATAKANA
                                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS;
                        })) {
            return Language.JA;
        }
        if (value.codePoints().allMatch(codePoint -> codePoint < 128)) {
            return Language.EN;
        }
        return Language.UND;
    }

    private LocalDateTime parseLocalDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        List<DateTimeFormatter> localFormats =
                List.of(
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                        DateTimeFormatter.ISO_LOCAL_DATE,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        try {
            return OffsetDateTime.parse(trimmed)
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (RuntimeException ignored) {
            // Fall through.
        }
        for (DateTimeFormatter formatter : localFormats) {
            try {
                if (formatter == DateTimeFormatter.ISO_LOCAL_DATE) {
                    return LocalDateTime.of(
                            java.time.LocalDate.parse(trimmed, formatter), java.time.LocalTime.MIN);
                }
                return LocalDateTime.parse(trimmed, formatter);
            } catch (RuntimeException ignored) {
                // Try next format.
            }
        }
        return null;
    }

    private String normalizeUrlKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private VocadbLink parseVocadbLink(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(rawUrl.trim());
            String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
            if (!host.equals("vocadb.net") && !host.equals("www.vocadb.net")) {
                return null;
            }
            String[] parts =
                    Optional.ofNullable(uri.getPath()).orElse("").replaceAll("^/+", "").split("/");
            if (parts.length < 2) {
                return null;
            }
            String prefix = parts[0].toLowerCase(Locale.ROOT);
            long id = Long.parseLong(parts[1]);
            if (id <= 0) {
                return null;
            }
            return switch (prefix) {
                case "s", "song", "songs" -> new VocadbLink(VocadbLinkType.SONG, id);
                case "ar", "artist", "artists" -> new VocadbLink(VocadbLinkType.ARTIST, id);
                default -> null;
            };
        } catch (URISyntaxException | NumberFormatException ignored) {
            return null;
        }
    }

    private record VocadbLink(VocadbLinkType type, long id) {}

    private enum VocadbLinkType {
        SONG,
        ARTIST
    }

    private record DumpSongArtistRef(
            long artistId, String name, String artistType, List<String> roles, int sortOrder) {}
}
