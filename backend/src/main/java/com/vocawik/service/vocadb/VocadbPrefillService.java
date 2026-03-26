package com.vocawik.service.vocadb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocawik.common.i18n.Language;
import com.vocawik.dto.vocadb.VocadbPrefillResponse;
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

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PvUrlDetector pvUrlDetector;

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
        appendWebLinks(links, row.get("web_links"));

        List<DumpSongArtistRef> dumpArtists = parseSongArtists(row.get("artists"));
        ArrayList<VocadbPrefillResponse.SongArtistPrefill> artists = new ArrayList<>();
        ArrayList<VocadbPrefillResponse.SongVocalPrefill> vocals = new ArrayList<>();
        boolean hasMainArtist = false;
        boolean hasMainVocal = false;
        for (DumpSongArtistRef item : dumpArtists) {
            List<String> roles = mapArtistRoles(item.roles());
            if (!roles.isEmpty()) {
                artists.add(
                        new VocadbPrefillResponse.SongArtistPrefill(
                                item.artistId(),
                                findArtistResourceUuidByVocadbId(item.artistId()),
                                firstNonBlank(item.name(), "VocaDB Artist " + item.artistId()),
                                roles,
                                !hasMainArtist,
                                item.sortOrder()));
                hasMainArtist = true;
            }
            if (isVocalistType(item.artistType())) {
                vocals.add(
                        new VocadbPrefillResponse.SongVocalPrefill(
                                item.artistId(),
                                findVocalResourceUuidByVocadbId(item.artistId()),
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
        appendWebLinks(links, row.get("web_links"));

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
                JOIN songs s ON s.resource_id = r.id
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
                JOIN artists a ON a.resource_id = r.id
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
                JOIN vocals v ON v.resource_id = r.id
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

    private void appendWebLinks(List<VocadbPrefillResponse.LinkPrefill> links, Object rawValue) {
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
            links.add(
                    new VocadbPrefillResponse.LinkPrefill(
                            "OTHER",
                            url.trim(),
                            firstNonBlank(asText(item, "description"), asText(item, "name")),
                            false));
        }
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

    private List<String> mapArtistRoles(List<String> roles) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            switch (role.trim().toLowerCase(Locale.ROOT)) {
                case "producer" -> result.add("PRODUCER");
                case "arranger" -> result.add("ARRANGER");
                case "composer" -> result.add("COMPOSER");
                case "lyricist", "lyrics" -> result.add("LYRICIST");
                case "default" -> result.add("OTHER");
                default -> result.add("OTHER");
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
