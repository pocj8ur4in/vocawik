package com.vocawik.service.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocawik.common.i18n.Language;
import com.vocawik.domain.artist.Artist;
import com.vocawik.domain.artist.ArtistGroup;
import com.vocawik.domain.artist.ArtistLink;
import com.vocawik.domain.playlist.Playlist;
import com.vocawik.domain.playlist.PlaylistSong;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceName;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.domain.song.Song;
import com.vocawik.domain.song.SongArtist;
import com.vocawik.domain.song.SongLink;
import com.vocawik.domain.song.SongLyric;
import com.vocawik.domain.song.SongPv;
import com.vocawik.domain.song.SongPvView;
import com.vocawik.domain.song.SongRelation;
import com.vocawik.domain.song.SongVocal;
import com.vocawik.domain.vocal.Vocal;
import com.vocawik.domain.vocal.VocalLink;
import com.vocawik.dto.resource.ArtistResourceDetailResponse;
import com.vocawik.dto.resource.PlaylistResourceDetailResponse;
import com.vocawik.dto.resource.ResourceAclDetailResponse;
import com.vocawik.dto.resource.ResourceElementResponse;
import com.vocawik.dto.resource.ResourceInfoResponse;
import com.vocawik.dto.resource.ResourceListResponse;
import com.vocawik.dto.resource.ResourceNameDetailResponse;
import com.vocawik.dto.resource.ResourceSuggestionElementResponse;
import com.vocawik.dto.resource.ResourceSuggestionListResponse;
import com.vocawik.dto.resource.SongResourceDetailResponse;
import com.vocawik.dto.resource.VocalResourceDetailResponse;
import com.vocawik.repository.acl.AclRepository;
import com.vocawik.repository.artist.ArtistGroupRepository;
import com.vocawik.repository.artist.ArtistLinkRepository;
import com.vocawik.repository.artist.ArtistRepository;
import com.vocawik.repository.playlist.PlaylistRepository;
import com.vocawik.repository.playlist.PlaylistSongRepository;
import com.vocawik.repository.resource.ResourceCriteria;
import com.vocawik.repository.resource.ResourceNameRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.repository.song.SongArtistRepository;
import com.vocawik.repository.song.SongLinkRepository;
import com.vocawik.repository.song.SongLyricRepository;
import com.vocawik.repository.song.SongPvRepository;
import com.vocawik.repository.song.SongPvViewRepository;
import com.vocawik.repository.song.SongRelationRepository;
import com.vocawik.repository.song.SongRepository;
import com.vocawik.repository.song.SongVocalRepository;
import com.vocawik.repository.vocal.VocalLinkRepository;
import com.vocawik.repository.vocal.VocalRepository;
import com.vocawik.service.history.ResourceHistoryService;
import com.vocawik.web.error.ErrorCode;
import com.vocawik.web.exception.BusinessException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for searching resources and loading typed resource details. */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Repositories and ObjectMapper are Spring-managed dependencies.")
public class ResourceService {

    private static final int RESOURCE_SUGGESTION_LIMIT = 10;
    private static final int RELATED_SONG_SECTION_LIMIT = 10;

    private final ResourceRepository resourceRepository;
    private final ResourceNameRepository resourceNameRepository;
    private final AclRepository aclRepository;
    private final SongRepository songRepository;
    private final SongLinkRepository songLinkRepository;
    private final SongLyricRepository songLyricRepository;
    private final SongPvRepository songPvRepository;
    private final SongPvViewRepository songPvViewRepository;
    private final SongArtistRepository songArtistRepository;
    private final SongVocalRepository songVocalRepository;
    private final SongRelationRepository songRelationRepository;
    private final PlaylistSongRepository playlistSongRepository;
    private final PlaylistRepository playlistRepository;
    private final ArtistRepository artistRepository;
    private final ArtistGroupRepository artistGroupRepository;
    private final ArtistLinkRepository artistLinkRepository;
    private final VocalRepository vocalRepository;
    private final VocalLinkRepository vocalLinkRepository;
    private final ResourceHistoryService resourceHistoryService;
    private final ResourcePopularityService resourcePopularityService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ResourceListResponse search(ResourceStatus status, String query, Pageable pageable) {
        String normalizedQuery = normalizeQuery(query);
        Page<Resource> result =
                resourceRepository.search(new ResourceCriteria(status, normalizedQuery), pageable);

        Map<Long, String> localizedNamesByResourceId =
                loadLocalizedNamesByResourceId(result.getContent());
        List<ResourceElementResponse> items =
                result.getContent().stream()
                        .map(resource -> toSummary(resource, localizedNamesByResourceId))
                        .toList();

        return new ResourceListResponse(
                items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ResourceSuggestionListResponse suggest(String query) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery == null) {
            return new ResourceSuggestionListResponse(List.of());
        }

        LinkedHashMap<String, LinkedHashMap<Long, UUID>> resourceRefsByName = new LinkedHashMap<>();
        resourceNameRepository
                .findSuggestionCandidates(
                        ResourceStatus.ACTIVE,
                        normalizedQuery,
                        org.springframework.data.domain.PageRequest.of(
                                0, RESOURCE_SUGGESTION_LIMIT * 3))
                .forEach(
                        resourceName -> {
                            Resource resource = resourceName.getResource();
                            resourceRefsByName
                                    .computeIfAbsent(
                                            resourceName.getName(),
                                            ignored -> new LinkedHashMap<>())
                                    .putIfAbsent(resource.getId(), resource.getUuid());
                        });

        Map<Long, String> localizedNamesByResourceId =
                loadLocalizedNamesByResourceIds(
                        resourceRefsByName.values().stream()
                                .flatMap(resourceRefs -> resourceRefs.keySet().stream())
                                .distinct()
                                .toList());

        return new ResourceSuggestionListResponse(
                resourceRefsByName.entrySet().stream()
                        .limit(RESOURCE_SUGGESTION_LIMIT)
                        .map(
                                entry -> {
                                    LinkedHashMap<Long, UUID> resourceRefs = entry.getValue();
                                    boolean hasMultipleResources = resourceRefs.size() > 1;
                                    UUID resourceUuid =
                                            hasMultipleResources
                                                    ? null
                                                    : resourceRefs.values().iterator().next();
                                    String localizedName =
                                            hasMultipleResources
                                                    ? null
                                                    : localizedNamesByResourceId.get(
                                                            resourceRefs
                                                                    .keySet()
                                                                    .iterator()
                                                                    .next());
                                    return new ResourceSuggestionElementResponse(
                                            resourceUuid,
                                            entry.getKey(),
                                            localizedName,
                                            hasMultipleResources);
                                })
                        .toList());
    }

    @Transactional(readOnly = true)
    public SongResourceDetailResponse getSongByResourceUuid(UUID resourceUuid) {
        Song song =
                songRepository
                        .findByResourceUuid(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Resource resource = song.getResource();
        String localizedName = loadLocalizedName(resource.getId());

        List<ResourceNameDetailResponse> names = loadResourceNames(resource.getId());
        List<ResourceAclDetailResponse> acls = loadResourceAcls(resource.getId());
        List<SongLink> links = songLinkRepository.findAllBySongIdOrderByIdAsc(song.getId());
        List<SongLyric> lyrics =
                songLyricRepository.findAllBySongIdOrderBySortOrderAscIdAsc(song.getId());
        List<SongPv> pvs = songPvRepository.findAllBySongIdOrderBySortOrderAscIdAsc(song.getId());
        Map<Long, List<SongPvView>> pvViewsBySongPvId = loadSongPvViews(pvs);
        List<SongArtist> artists =
                songArtistRepository.findAllBySongIdOrderBySortOrderAscIdAsc(song.getId());
        List<SongVocal> vocals =
                songVocalRepository.findAllBySongIdOrderBySortOrderAscIdAsc(song.getId());
        List<SongRelation> outgoingRelations =
                songRelationRepository.findAllBySourceSongIdOrderByIdAsc(song.getId());
        List<SongRelation> incomingRelations =
                songRelationRepository.findAllByTargetSongIdOrderByIdAsc(song.getId());
        List<PlaylistSong> playlists =
                playlistSongRepository.findAllBySongIdOrderBySortOrderAscIdAsc(song.getId());

        return new SongResourceDetailResponse(
                resource.getUuid(),
                resource.isDeleted(),
                resource.getCanonicalName(),
                localizedName,
                resource.getStatus().name(),
                song.getSongType().name(),
                resource.getViewCount(),
                resource.getThumbnailUrl(),
                song.getContent(),
                links.stream().map(this::toSongLink).toList(),
                song.getPublishedAt(),
                resource.getCreatedAt(),
                resource.getUpdatedAt(),
                names,
                acls,
                lyrics.stream().map(this::toSongLyric).toList(),
                pvs.stream().map(pv -> toSongPv(pv, pvViewsBySongPvId.get(pv.getId()))).toList(),
                artists.stream().map(this::toSongArtist).toList(),
                vocals.stream().map(this::toSongVocal).toList(),
                outgoingRelations.stream().map(this::toSongRelation).toList(),
                incomingRelations.stream().map(this::toSongIncomingRelation).toList(),
                playlists.stream().map(this::toSongPlaylist).toList());
    }

    @Transactional
    public SongResourceDetailResponse getSongByResourceUuidWithTracking(UUID resourceUuid) {
        resourcePopularityService.trackView(resourceUuid);
        return getSongByResourceUuid(resourceUuid);
    }

    @Transactional(readOnly = true)
    public ArtistResourceDetailResponse getArtistByResourceUuid(UUID resourceUuid) {
        Artist artist =
                artistRepository
                        .findByResourceUuid(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Resource resource = artist.getResource();
        String localizedName = loadLocalizedName(resource.getId());
        List<ArtistLink> links = artistLinkRepository.findAllByArtistIdOrderByIdAsc(artist.getId());
        long songCount = songArtistRepository.countByArtistId(artist.getId());
        List<ArtistResourceDetailResponse.ArtistSong> recentSongs =
                songArtistRepository
                        .findRecentByArtistId(
                                artist.getId(), PageRequest.of(0, RELATED_SONG_SECTION_LIMIT))
                        .stream()
                        .map(this::toArtistSong)
                        .toList();
        List<ArtistResourceDetailResponse.ArtistSong> popularSongs =
                songArtistRepository
                        .findPopularByArtistId(
                                artist.getId(), PageRequest.of(0, RELATED_SONG_SECTION_LIMIT))
                        .stream()
                        .map(this::toArtistSong)
                        .toList();

        return new ArtistResourceDetailResponse(
                resource.getUuid(),
                resource.isDeleted(),
                resource.getCanonicalName(),
                localizedName,
                resource.getStatus().name(),
                resource.getViewCount(),
                resource.getThumbnailUrl(),
                artist.getContent(),
                links.stream().map(this::toArtistLink).toList(),
                resource.getCreatedAt(),
                resource.getUpdatedAt(),
                loadResourceNames(resource.getId()),
                loadResourceAcls(resource.getId()),
                new ArtistResourceDetailResponse.ArtistSongs(songCount, recentSongs, popularSongs),
                artistGroupRepository
                        .findAllByGroupArtistIdOrderBySortOrderAscIdAsc(artist.getId())
                        .stream()
                        .map(this::toArtistGroup)
                        .toList(),
                artistGroupRepository
                        .findAllByMemberArtistIdOrderBySortOrderAscIdAsc(artist.getId())
                        .stream()
                        .map(this::toArtistMember)
                        .toList());
    }

    @Transactional
    public ArtistResourceDetailResponse getArtistByResourceUuidWithTracking(UUID resourceUuid) {
        resourcePopularityService.trackView(resourceUuid);
        return getArtistByResourceUuid(resourceUuid);
    }

    @Transactional(readOnly = true)
    public VocalResourceDetailResponse getVocalByResourceUuid(UUID resourceUuid) {
        Vocal vocal =
                vocalRepository
                        .findByResourceUuid(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Resource resource = vocal.getResource();
        String localizedName = loadLocalizedName(resource.getId());
        List<VocalLink> links = vocalLinkRepository.findAllByVocalIdOrderByIdAsc(vocal.getId());

        long songCount = songVocalRepository.countByVocalId(vocal.getId());
        List<VocalResourceDetailResponse.VocalSong> recentSongs =
                songVocalRepository
                        .findRecentByVocalId(
                                vocal.getId(), PageRequest.of(0, RELATED_SONG_SECTION_LIMIT))
                        .stream()
                        .map(this::toVocalSong)
                        .toList();
        List<VocalResourceDetailResponse.VocalSong> popularSongs =
                songVocalRepository
                        .findPopularByVocalId(
                                vocal.getId(), PageRequest.of(0, RELATED_SONG_SECTION_LIMIT))
                        .stream()
                        .map(this::toVocalSong)
                        .toList();

        return new VocalResourceDetailResponse(
                resource.getUuid(),
                resource.isDeleted(),
                resource.getCanonicalName(),
                localizedName,
                resource.getStatus().name(),
                resource.getViewCount(),
                resource.getThumbnailUrl(),
                vocal.getContent(),
                links.stream().map(this::toVocalLink).toList(),
                resource.getCreatedAt(),
                resource.getUpdatedAt(),
                loadResourceNames(resource.getId()),
                loadResourceAcls(resource.getId()),
                new VocalResourceDetailResponse.VocalSongs(songCount, recentSongs, popularSongs));
    }

    @Transactional
    public VocalResourceDetailResponse getVocalByResourceUuidWithTracking(UUID resourceUuid) {
        resourcePopularityService.trackView(resourceUuid);
        return getVocalByResourceUuid(resourceUuid);
    }

    @Transactional(readOnly = true)
    public PlaylistResourceDetailResponse getPlaylistByResourceUuid(UUID resourceUuid) {
        Playlist playlist =
                playlistRepository
                        .findByResourceUuid(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Resource resource = playlist.getResource();
        String localizedName = loadLocalizedName(resource.getId());

        return new PlaylistResourceDetailResponse(
                resource.getUuid(),
                resource.isDeleted(),
                resource.getCanonicalName(),
                localizedName,
                resource.getStatus().name(),
                resource.getViewCount(),
                resource.getThumbnailUrl(),
                playlist.getContent(),
                playlist.isPublic(),
                resource.getCreatedAt(),
                resource.getUpdatedAt(),
                loadResourceNames(resource.getId()),
                loadResourceAcls(resource.getId()),
                playlistSongRepository
                        .findAllByPlaylistIdOrderBySortOrderAscIdAsc(playlist.getId())
                        .stream()
                        .map(this::toPlaylistDetailSong)
                        .toList());
    }

    @Transactional
    public PlaylistResourceDetailResponse getPlaylistByResourceUuidWithTracking(UUID resourceUuid) {
        resourcePopularityService.trackView(resourceUuid);
        return getPlaylistByResourceUuid(resourceUuid);
    }

    @Transactional(readOnly = true)
    public ResourceInfoResponse getResourceInfoByResourceUuid(UUID resourceUuid) {
        Resource resource =
                resourceRepository
                        .findByUuid(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return new ResourceInfoResponse(
                resource.getUuid(),
                loadResourceAcls(resource.getId()),
                resourceHistoryService.listByResourceId(resource.getId()));
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String trimmed = query.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Map<Long, String> loadLocalizedNamesByResourceId(List<Resource> resources) {
        Language language = resolveCurrentLanguage();
        if (language == null || resources.isEmpty()) {
            return Map.of();
        }

        return loadLocalizedNamesByResourceIds(
                resources.stream().map(Resource::getId).distinct().toList());
    }

    private Map<Long, String> loadLocalizedNamesByResourceIds(List<Long> resourceIds) {
        Language language = resolveCurrentLanguage();
        if (language == null || resourceIds.isEmpty()) {
            return Map.of();
        }

        List<ResourceName> localizedNames =
                resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        resourceIds);
        if (localizedNames == null) {
            return Map.of();
        }

        Map<Long, String> localizedNamesByResourceId = new HashMap<>();
        for (ResourceName resourceName : localizedNames) {
            if (resourceName.getLangCode() != language) {
                continue;
            }
            localizedNamesByResourceId.putIfAbsent(
                    resourceName.getResource().getId(), resourceName.getName());
        }
        return localizedNamesByResourceId;
    }

    private String loadLocalizedName(Long resourceId) {
        return loadLocalizedNamesByResourceIds(List.of(resourceId)).get(resourceId);
    }

    private Language resolveCurrentLanguage() {
        return switch (LocaleContextHolder.getLocale().getLanguage()) {
            case "ko" -> Language.KO;
            case "en" -> Language.EN;
            case "ja" -> Language.JA;
            case "zh" -> Language.ZH;
            default -> null;
        };
    }

    private ResourceElementResponse toSummary(
            Resource resource, Map<Long, String> localizedNamesByResourceId) {
        return new ResourceElementResponse(
                resource.getUuid(),
                resource.getCanonicalName(),
                localizedNamesByResourceId.get(resource.getId()),
                resource.getResourceType().name(),
                resource.getStatus().name(),
                resource.getViewCount(),
                resource.getThumbnailUrl(),
                resource.getCreatedAt(),
                resource.getUpdatedAt());
    }

    private List<ResourceNameDetailResponse> loadResourceNames(Long resourceId) {
        return resourceNameRepository
                .findAllByResourceIdOrderBySortOrderAscIdAsc(resourceId)
                .stream()
                .map(
                        name ->
                                new ResourceNameDetailResponse(
                                        name.getUuid(),
                                        name.getLangCode().name(),
                                        name.getName(),
                                        name.isPrimary(),
                                        name.getSortOrder(),
                                        name.getCreatedAt(),
                                        name.getUpdatedAt()))
                .toList();
    }

    private List<ResourceAclDetailResponse> loadResourceAcls(Long resourceId) {
        return aclRepository.findAllByResourceIdOrderByPriorityAscIdAsc(resourceId).stream()
                .map(
                        acl ->
                                new ResourceAclDetailResponse(
                                        acl.getUuid(),
                                        acl.getAction().name(),
                                        acl.getSubjectType().name(),
                                        acl.getSubjectValue(),
                                        acl.getEffect().name(),
                                        acl.getPriority(),
                                        acl.getExpiresAt(),
                                        acl.getCreatedAt(),
                                        acl.getUpdatedAt()))
                .toList();
    }

    private Map<Long, List<SongPvView>> loadSongPvViews(List<SongPv> pvs) {
        if (pvs.isEmpty()) {
            return Map.of();
        }
        List<Long> songPvIds = pvs.stream().map(SongPv::getId).toList();
        return songPvViewRepository.findAllBySongPvIdIn(songPvIds).stream()
                .collect(Collectors.groupingBy(view -> view.getSongPv().getId()));
    }

    private SongResourceDetailResponse.SongLyric toSongLyric(SongLyric lyric) {
        return new SongResourceDetailResponse.SongLyric(
                lyric.getUuid(),
                lyric.getLangCodes().stream().map(Enum::name).sorted().toList(),
                toJsonValue(lyric.getLyrics()),
                lyric.isPrimary(),
                lyric.getSortOrder(),
                lyric.getCreatedAt(),
                lyric.getUpdatedAt());
    }

    private SongResourceDetailResponse.SongLink toSongLink(SongLink songLink) {
        return new SongResourceDetailResponse.SongLink(
                songLink.getSongLinkType().name(),
                songLink.getUrl(),
                songLink.getContent(),
                songLink.isDeleted());
    }

    private Object toJsonValue(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull()) {
            return null;
        }
        return objectMapper.convertValue(jsonNode, Object.class);
    }

    private SongResourceDetailResponse.SongPv toSongPv(SongPv pv, List<SongPvView> views) {
        return new SongResourceDetailResponse.SongPv(
                pv.getUuid(),
                pv.getService().name(),
                pv.getVideoKey(),
                pv.getTitle(),
                pv.getThumbnailUrl(),
                pv.getUploaderKey(),
                pv.getDurationSeconds(),
                pv.isOfficial(),
                pv.getPublishedAt(),
                toSongPvExtra(pv),
                pv.getSortOrder(),
                (views == null ? List.<SongPvView>of() : views)
                        .stream()
                                .sorted(
                                        Comparator.comparing(SongPvView::getCreatedAt)
                                                .thenComparing(SongPvView::getId))
                                .map(this::toSongPvView)
                                .toList(),
                pv.getCreatedAt(),
                pv.getUpdatedAt());
    }

    private SongResourceDetailResponse.SongPvExtra toSongPvExtra(SongPv pv) {
        String audioUrl = pv.getPiaproAudioUrl();
        Long cid = pv.getBilibiliCid();
        String externalUrl = pv.getBandcampExternalUrl();
        if (audioUrl == null && cid == null && externalUrl == null) {
            return null;
        }
        return new SongResourceDetailResponse.SongPvExtra(audioUrl, cid, externalUrl);
    }

    private SongResourceDetailResponse.SongPvView toSongPvView(SongPvView view) {
        return new SongResourceDetailResponse.SongPvView(
                view.getUuid(), view.getViewCount(), view.getCreatedAt(), view.getUpdatedAt());
    }

    private SongResourceDetailResponse.SongArtist toSongArtist(SongArtist songArtist) {
        return new SongResourceDetailResponse.SongArtist(
                songArtist.getArtist().getResource().getUuid(),
                songArtist.getArtist().getResource().getCanonicalName(),
                songArtist.getArtist().getResource().getThumbnailUrl(),
                songArtist.isMain(),
                songArtist.getSortOrder(),
                songArtist.getRoles().stream().map(Enum::name).sorted().toList());
    }

    private SongResourceDetailResponse.SongVocal toSongVocal(SongVocal songVocal) {
        return new SongResourceDetailResponse.SongVocal(
                songVocal.getVocal().getResource().getUuid(),
                songVocal.getVocal().getResource().getCanonicalName(),
                songVocal.isMain(),
                songVocal.getSortOrder());
    }

    private SongResourceDetailResponse.SongRelation toSongRelation(SongRelation relation) {
        return new SongResourceDetailResponse.SongRelation(
                relation.getTargetSong().getResource().getUuid(),
                relation.getTargetSong().getResource().getCanonicalName(),
                relation.getTargetSong().getSongType().name());
    }

    private SongResourceDetailResponse.SongIncomingRelation toSongIncomingRelation(
            SongRelation relation) {
        return new SongResourceDetailResponse.SongIncomingRelation(
                relation.getSourceSong().getResource().getUuid(),
                relation.getSourceSong().getResource().getCanonicalName(),
                relation.getSourceSong().getSongType().name());
    }

    private SongResourceDetailResponse.SongPlaylist toSongPlaylist(PlaylistSong playlistSong) {
        return new SongResourceDetailResponse.SongPlaylist(
                playlistSong.getPlaylist().getResource().getUuid(),
                playlistSong.getPlaylist().getResource().getCanonicalName(),
                playlistSong.getSortOrder());
    }

    private ArtistResourceDetailResponse.ArtistSong toArtistSong(SongArtist songArtist) {
        return new ArtistResourceDetailResponse.ArtistSong(
                songArtist.getSong().getResource().getUuid(),
                songArtist.getSong().getResource().getCanonicalName(),
                songArtist.getSong().getResource().getThumbnailUrl(),
                songArtist.getSong().getSongType().name(),
                songArtist.getSong().getPublishedAt(),
                songArtist.isMain(),
                songArtist.getSortOrder(),
                songArtist.getRoles().stream().map(Enum::name).sorted().toList());
    }

    private ArtistResourceDetailResponse.ArtistGroup toArtistGroup(ArtistGroup artistGroup) {
        return new ArtistResourceDetailResponse.ArtistGroup(
                artistGroup.getMemberArtist().getResource().getUuid(),
                artistGroup.getMemberArtist().getResource().getCanonicalName(),
                artistGroup.getMemberArtist().getResource().getThumbnailUrl(),
                artistGroup.getSortOrder());
    }

    private ArtistResourceDetailResponse.ArtistMember toArtistMember(ArtistGroup artistGroup) {
        return new ArtistResourceDetailResponse.ArtistMember(
                artistGroup.getGroupArtist().getResource().getUuid(),
                artistGroup.getGroupArtist().getResource().getCanonicalName(),
                artistGroup.getGroupArtist().getResource().getThumbnailUrl(),
                artistGroup.getSortOrder());
    }

    private ArtistResourceDetailResponse.ArtistLink toArtistLink(ArtistLink artistLink) {
        return new ArtistResourceDetailResponse.ArtistLink(
                artistLink.getArtistLinkType().name(),
                artistLink.getUrl(),
                artistLink.getContent(),
                artistLink.isDeleted());
    }

    private VocalResourceDetailResponse.VocalSong toVocalSong(SongVocal songVocal) {
        Song song = songVocal.getSong();
        return new VocalResourceDetailResponse.VocalSong(
                song.getResource().getUuid(),
                song.getResource().getCanonicalName(),
                song.getResource().getThumbnailUrl(),
                song.getSongType().name(),
                song.getPublishedAt(),
                songVocal.isMain(),
                songVocal.getSortOrder());
    }

    private VocalResourceDetailResponse.VocalLink toVocalLink(VocalLink vocalLink) {
        return new VocalResourceDetailResponse.VocalLink(
                vocalLink.getVocalLinkType().name(),
                vocalLink.getUrl(),
                vocalLink.getContent(),
                vocalLink.isDeleted());
    }

    private PlaylistResourceDetailResponse.PlaylistSong toPlaylistDetailSong(
            PlaylistSong playlistSong) {
        Song song = playlistSong.getSong();
        return new PlaylistResourceDetailResponse.PlaylistSong(
                song.getResource().getUuid(),
                song.getResource().getCanonicalName(),
                song.getResource().getThumbnailUrl(),
                song.getSongType().name(),
                playlistSong.getSortOrder());
    }
}
