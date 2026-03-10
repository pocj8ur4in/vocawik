package com.vocawik.service.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocawik.domain.artist.Artist;
import com.vocawik.domain.artist.ArtistGroup;
import com.vocawik.domain.playlist.Playlist;
import com.vocawik.domain.playlist.PlaylistSong;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.domain.song.Song;
import com.vocawik.domain.song.SongArtist;
import com.vocawik.domain.song.SongLyric;
import com.vocawik.domain.song.SongPv;
import com.vocawik.domain.song.SongPvView;
import com.vocawik.domain.song.SongRelation;
import com.vocawik.domain.song.SongVocal;
import com.vocawik.domain.vocal.Vocal;
import com.vocawik.dto.resource.ArtistResourceDetailResponse;
import com.vocawik.dto.resource.PlaylistResourceDetailResponse;
import com.vocawik.dto.resource.ResourceAclDetailResponse;
import com.vocawik.dto.resource.ResourceElementResponse;
import com.vocawik.dto.resource.ResourceListResponse;
import com.vocawik.dto.resource.ResourceNameDetailResponse;
import com.vocawik.dto.resource.SongResourceDetailResponse;
import com.vocawik.dto.resource.VocalResourceDetailResponse;
import com.vocawik.repository.acl.AclRepository;
import com.vocawik.repository.artist.ArtistGroupRepository;
import com.vocawik.repository.artist.ArtistRepository;
import com.vocawik.repository.playlist.PlaylistRepository;
import com.vocawik.repository.playlist.PlaylistSongRepository;
import com.vocawik.repository.resource.ResourceCriteria;
import com.vocawik.repository.resource.ResourceNameRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.repository.song.SongArtistRepository;
import com.vocawik.repository.song.SongLyricRepository;
import com.vocawik.repository.song.SongPvRepository;
import com.vocawik.repository.song.SongPvViewRepository;
import com.vocawik.repository.song.SongRelationRepository;
import com.vocawik.repository.song.SongRepository;
import com.vocawik.repository.song.SongVocalRepository;
import com.vocawik.repository.vocal.VocalRepository;
import com.vocawik.web.error.ErrorCode;
import com.vocawik.web.exception.BusinessException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for searching resources and loading typed resource details. */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Repositories and ObjectMapper are Spring-managed dependencies.")
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final ResourceNameRepository resourceNameRepository;
    private final AclRepository aclRepository;
    private final SongRepository songRepository;
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
    private final VocalRepository vocalRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ResourceListResponse search(ResourceStatus status, String query, Pageable pageable) {
        String normalizedQuery = normalizeQuery(query);
        Slice<Resource> resultSlice =
                resourceRepository.search(new ResourceCriteria(status, normalizedQuery), pageable);

        List<ResourceElementResponse> items =
                resultSlice.getContent().stream().map(this::toSummary).toList();

        return new ResourceListResponse(
                items, resultSlice.getNumber(), resultSlice.getSize(), resultSlice.hasNext());
    }

    @Transactional(readOnly = true)
    public SongResourceDetailResponse getSongByResourceUuid(UUID resourceUuid) {
        Song song =
                songRepository
                        .findByResourceUuid(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Resource resource = song.getResource();

        List<ResourceNameDetailResponse> names = loadResourceNames(resource.getId());
        List<ResourceAclDetailResponse> acls = loadResourceAcls(resource.getId());
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
                resource.getStatus().name(),
                song.getSongType().name(),
                resource.getViewCount(),
                resource.getThumbnailUrl(),
                song.getContent(),
                toJsonValue(song.getLinks()),
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

    @Transactional(readOnly = true)
    public ArtistResourceDetailResponse getArtistByResourceUuid(UUID resourceUuid) {
        Artist artist =
                artistRepository
                        .findByResourceUuid(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Resource resource = artist.getResource();

        return new ArtistResourceDetailResponse(
                resource.getUuid(),
                resource.isDeleted(),
                resource.getCanonicalName(),
                resource.getStatus().name(),
                resource.getViewCount(),
                resource.getThumbnailUrl(),
                artist.getContent(),
                toJsonValue(artist.getLinks()),
                resource.getCreatedAt(),
                resource.getUpdatedAt(),
                loadResourceNames(resource.getId()),
                loadResourceAcls(resource.getId()),
                songArtistRepository
                        .findAllByArtistIdOrderBySortOrderAscIdAsc(artist.getId())
                        .stream()
                        .map(this::toArtistSong)
                        .toList(),
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

    @Transactional(readOnly = true)
    public VocalResourceDetailResponse getVocalByResourceUuid(UUID resourceUuid) {
        Vocal vocal =
                vocalRepository
                        .findByResourceUuid(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Resource resource = vocal.getResource();

        return new VocalResourceDetailResponse(
                resource.getUuid(),
                resource.isDeleted(),
                resource.getCanonicalName(),
                resource.getStatus().name(),
                resource.getViewCount(),
                resource.getThumbnailUrl(),
                vocal.getContent(),
                toJsonValue(vocal.getLinks()),
                resource.getCreatedAt(),
                resource.getUpdatedAt(),
                loadResourceNames(resource.getId()),
                loadResourceAcls(resource.getId()),
                songVocalRepository.findAllByVocalIdOrderBySortOrderAscIdAsc(vocal.getId()).stream()
                        .map(this::toVocalSong)
                        .toList());
    }

    @Transactional(readOnly = true)
    public PlaylistResourceDetailResponse getPlaylistByResourceUuid(UUID resourceUuid) {
        Playlist playlist =
                playlistRepository
                        .findByResourceUuid(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Resource resource = playlist.getResource();

        return new PlaylistResourceDetailResponse(
                resource.getUuid(),
                resource.isDeleted(),
                resource.getCanonicalName(),
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

    private String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String trimmed = query.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ResourceElementResponse toSummary(Resource resource) {
        return new ResourceElementResponse(
                resource.getUuid(),
                resource.getCanonicalName(),
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
