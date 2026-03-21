package com.vocawik.service.audio;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Configuration properties for song audio import and storage. */
@Component
@Getter
public class AudioImportProperties {

    private final String bucket;
    private final long commandTimeoutSeconds;
    private final String ytDlpCommand;
    private final String ffmpegCommand;

    public AudioImportProperties(
            @Value("${audio.bucket:}") String bucket,
            @Value("${audio.command-timeout-seconds:1800}") long commandTimeoutSeconds,
            @Value("${audio.yt-dlp-command:yt-dlp}") String ytDlpCommand,
            @Value("${audio.ffmpeg-command:ffmpeg}") String ffmpegCommand) {
        this.bucket = bucket == null ? "" : bucket.trim();
        this.commandTimeoutSeconds = commandTimeoutSeconds;
        this.ytDlpCommand = ytDlpCommand == null ? "yt-dlp" : ytDlpCommand.trim();
        this.ffmpegCommand = ffmpegCommand == null ? "ffmpeg" : ffmpegCommand.trim();
    }
}
