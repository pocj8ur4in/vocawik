package com.vocawik.service.audio;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/** Executes the external audio download and ffmpeg processing steps. */
@Service
@SuppressFBWarnings(
        value = "COMMAND_INJECTION",
        justification =
                "The process is executed without a shell and uses fixed application commands plus"
                        + " explicit argument tokens.")
public class AudioImportProcessService {

    private static final DateTimeFormatter AUDIO_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AudioImportProperties properties;
    private final HttpClient httpClient;

    public AudioImportProcessService(AudioImportProperties properties) {
        this.properties = properties;
        this.httpClient =
                HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(30))
                        .build();
    }

    /** Downloads a PV URL into a temporary MP3 file. */
    public Path downloadAudio(String url, Path workingDirectory, UUID songUuid) {
        try {
            Files.createDirectories(workingDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create audio temp directory", e);
        }

        String baseName = songUuid.toString();
        Path outputBase = workingDirectory.resolve(baseName);
        runCommand(
                List.of(
                        properties.getYtDlpCommand(),
                        "--extract-audio",
                        "--audio-format",
                        "mp3",
                        "--format",
                        "bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio/best",
                        "--audio-quality",
                        "0",
                        "--output",
                        outputBase + ".%(ext)s",
                        "--no-playlist",
                        "--socket-timeout",
                        "60",
                        "--retries",
                        "5",
                        "--ignore-errors",
                        "--no-check-certificate",
                        "--no-continue",
                        "--prefer-insecure",
                        "--no-part",
                        "--force-overwrites",
                        "--no-warnings",
                        url),
                workingDirectory,
                "Audio download failed");

        Path outputFile = outputBase.resolveSibling(baseName + ".mp3");
        if (!Files.exists(outputFile)) {
            throw new IllegalStateException("Downloaded audio file does not exist");
        }
        return outputFile;
    }

    /** Downloads and embeds an external thumbnail into the MP3 file. */
    public void addThumbnail(Path audioFile, String thumbnailUrl) {
        if (thumbnailUrl == null || thumbnailUrl.isBlank()) {
            return;
        }

        Path thumbnailFile = audioFile.resolveSibling(audioFile.getFileName() + ".thumb.jpg");
        try {
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(thumbnailUrl.trim())).GET().build();
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Thumbnail download failed with status " + response.statusCode());
            }
            try (InputStream body = response.body()) {
                Files.copy(body, thumbnailFile, StandardCopyOption.REPLACE_EXISTING);
            }

            Path tempOutput = audioFile.resolveSibling(audioFile.getFileName() + ".thumb.mp3");
            runCommand(
                    List.of(
                            properties.getFfmpegCommand(),
                            "-i",
                            audioFile.toString(),
                            "-i",
                            thumbnailFile.toString(),
                            "-map",
                            "0:0",
                            "-map",
                            "1:0",
                            "-c",
                            "copy",
                            "-metadata:s:v",
                            "title=Album cover",
                            "-metadata:s:v",
                            "comment=Cover (front)",
                            "-id3v2_version",
                            "3",
                            "-y",
                            tempOutput.toString()),
                    requireWorkingDirectory(audioFile),
                    "Thumbnail embedding failed");
            Files.move(tempOutput, audioFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thumbnail embedding failed", e);
        } finally {
            try {
                Files.deleteIfExists(thumbnailFile);
            } catch (IOException ignored) {
            }
        }
    }

    /** Adds ID3 metadata to the MP3 file. */
    public void addMetadata(Path audioFile, SongAudioImportService.AudioMetadata metadata) {
        Path tempOutput = audioFile.resolveSibling(audioFile.getFileName() + ".meta.mp3");
        List<String> args = new ArrayList<>();
        args.add(properties.getFfmpegCommand());
        args.add("-i");
        args.add(audioFile.toString());
        args.add("-c");
        args.add("copy");
        args.add("-map_metadata");
        args.add("0");
        args.add("-id3v2_version");
        args.add("3");

        addMetadataArg(args, "title", metadata.title());
        addMetadataArg(args, "artist", metadata.artist());
        addMetadataArg(args, "album", metadata.album());
        addMetadataArg(args, "date", toAudioDate(metadata.publishedAt()));
        addMetadataArg(args, "genre", metadata.genre());
        addMetadataArg(args, "track", metadata.track());
        addMetadataArg(args, "comment", metadata.comment());
        addMetadataArg(args, "copyright", metadata.originalUrl());
        addMetadataArg(args, "year", metadata.year());
        addMetadataArg(args, "duration", metadata.duration());
        addMetadataArg(args, "language", metadata.language());
        addMetadataArg(args, "encoder", metadata.encoder());

        args.add("-y");
        args.add(tempOutput.toString());
        runCommand(args, requireWorkingDirectory(audioFile), "Audio metadata update failed");

        try {
            Files.move(tempOutput, audioFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Audio metadata update failed", e);
        }
    }

    private void addMetadataArg(List<String> args, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        args.add("-metadata");
        args.add(key + "=" + value);
    }

    private String toAudioDate(LocalDateTime publishedAt) {
        if (publishedAt == null) {
            return null;
        }
        return publishedAt.atOffset(ZoneOffset.UTC).format(AUDIO_DATE_FORMATTER);
    }

    private Path requireWorkingDirectory(Path audioFile) {
        Path parent = audioFile.getParent();
        if (parent == null) {
            throw new IllegalStateException("Audio import working directory must not be null");
        }
        return parent;
    }

    private void runCommand(List<String> command, Path workingDirectory, String failureMessage) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDirectory.toFile());
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            String output;
            try (InputStream inputStream = process.getInputStream()) {
                output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            boolean finished =
                    process.waitFor(properties.getCommandTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException(
                        failureMessage
                                + ": timed out after "
                                + properties.getCommandTimeoutSeconds()
                                + " seconds");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException(failureMessage + ": " + output);
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failureMessage, e);
        }
    }
}
