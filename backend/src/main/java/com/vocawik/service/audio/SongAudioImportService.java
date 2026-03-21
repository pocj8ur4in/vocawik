package com.vocawik.service.audio;

import com.vocawik.domain.song.Song;
import com.vocawik.domain.song.SongPv;
import com.vocawik.repository.song.SongPvRepository;
import com.vocawik.repository.song.SongRepository;
import com.vocawik.web.error.ErrorCode;
import com.vocawik.web.exception.BusinessException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orchestrates song-level audio imports after song mutations commit. */
@Slf4j
@Service
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Repositories and helper services are Spring-managed dependencies.")
public class SongAudioImportService {

    private final SongRepository songRepository;
    private final SongPvRepository songPvRepository;
    private final AudioImportProcessService audioImportProcessService;
    private final AudioObjectStorageService audioObjectStorageService;

    public SongAudioImportService(
            SongRepository songRepository,
            SongPvRepository songPvRepository,
            AudioImportProcessService audioImportProcessService,
            AudioObjectStorageService audioObjectStorageService) {
        this.songRepository = songRepository;
        this.songPvRepository = songPvRepository;
        this.audioImportProcessService = audioImportProcessService;
        this.audioObjectStorageService = audioObjectStorageService;
    }

    /** Imports song audio if it does not already exist. */
    @Async
    public void importIfMissing(UUID songResourceUuid, List<AudioSourceCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        Song song =
                songRepository
                        .findByResourceUuid(songResourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (audioObjectStorageService.existsSongAudio(songResourceUuid)) {
            logger.info(
                    "Skip song audio import because audio already exists: {}", songResourceUuid);
            return;
        }

        for (AudioSourceCandidate candidate :
                candidates.stream()
                        .sorted(
                                Comparator.comparingInt(AudioSourceCandidate::sortOrder)
                                        .thenComparingInt(AudioSourceCandidate::index))
                        .toList()) {
            if (candidate.url() == null || candidate.url().isBlank()) {
                continue;
            }
            try {
                String objectKey = importSingleCandidate(song, candidate);
                logger.info(
                        "Imported song audio for song {} from {} into {}",
                        songResourceUuid,
                        candidate.url(),
                        objectKey);
                return;
            } catch (Exception ex) {
                logger.warn(
                        "Song audio import failed for song {} with source {}: {}",
                        songResourceUuid,
                        candidate.url(),
                        ex.getMessage());
            }
        }

        logger.warn("Song audio import failed for all PV candidates: {}", songResourceUuid);
    }

    /** Opens the imported song audio through a song PV UUID lookup. */
    @Transactional(readOnly = true)
    public AudioObjectStorageService.AudioStream openAudioBySongPvUuid(UUID songPvUuid) {
        SongPv songPv =
                songPvRepository
                        .findByUuid(songPvUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        AudioObjectStorageService.AudioStream audioStream =
                audioObjectStorageService.openSongAudio(songPv.getSong().getResource().getUuid());
        if (audioStream == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return audioStream;
    }

    private String importSingleCandidate(Song song, AudioSourceCandidate candidate) {
        Path workingDirectory = null;
        try {
            workingDirectory = Files.createTempDirectory("song-audio-import-");
            Path audioFile =
                    audioImportProcessService.downloadAudio(
                            candidate.url(), workingDirectory, song.getResource().getUuid());
            audioImportProcessService.addThumbnail(audioFile, candidate.thumbnailUrl());
            audioImportProcessService.addMetadata(
                    audioFile,
                    new AudioMetadata(
                            candidate.title() == null || candidate.title().isBlank()
                                    ? song.getResource().getCanonicalName()
                                    : candidate.title(),
                            null,
                            "vocawik",
                            song.getPublishedAt(),
                            song.getSongType().name(),
                            null,
                            candidate.url(),
                            candidate.url()));
            return audioObjectStorageService.uploadSongAudio(
                    song.getResource().getUuid(), audioFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare temporary directory", e);
        } finally {
            if (workingDirectory != null) {
                cleanupRecursively(workingDirectory);
            }
        }
    }

    private void cleanupRecursively(Path root) {
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(
                            path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException ignored) {
                                }
                            });
        } catch (IOException ignored) {
        }
    }

    /** Candidate PV source considered for song audio import. */
    public record AudioSourceCandidate(
            String url, String title, String thumbnailUrl, int sortOrder, int index) {}

    /** Metadata embedded into the imported MP3 file. */
    public record AudioMetadata(
            String title,
            String artist,
            String album,
            LocalDateTime publishedAt,
            String comment,
            String track,
            String originalUrl,
            String encoder) {

        public String genre() {
            return "vocawik";
        }

        public String year() {
            return publishedAt == null ? null : Integer.toString(publishedAt.getYear());
        }

        public String duration() {
            return null;
        }

        public String language() {
            return null;
        }
    }
}
