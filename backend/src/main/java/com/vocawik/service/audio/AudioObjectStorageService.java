package com.vocawik.service.audio;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Stores imported song audio files in GCS. */
@Service
public class AudioObjectStorageService {

    private final AudioImportProperties properties;
    private final Storage storage;

    public AudioObjectStorageService(AudioImportProperties properties) {
        this(properties, StorageOptions.getDefaultInstance().getService());
    }

    AudioObjectStorageService(AudioImportProperties properties, Storage storage) {
        this.properties = properties;
        this.storage = storage;
    }

    /** Uploads the processed song audio file and returns its object key. */
    public String uploadSongAudio(UUID songUuid, Path audioFile) {
        validateConfiguredBucket();

        String objectKey = objectKey(songUuid);
        BlobInfo blobInfo =
                BlobInfo.newBuilder(properties.getBucket(), objectKey)
                        .setContentType("audio/mpeg")
                        .build();
        try {
            storage.create(blobInfo, Files.readAllBytes(audioFile));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to upload song audio to GCS", e);
        }
        return objectKey;
    }

    /** Opens a stored audio object for streaming. */
    public AudioStream open(String objectKey) {
        validateConfiguredBucket();

        Blob blob = storage.get(BlobId.of(properties.getBucket(), objectKey));
        if (blob == null || !blob.exists()) {
            return null;
        }

        String contentType =
                blob.getContentType() == null || blob.getContentType().isBlank()
                        ? "audio/mpeg"
                        : blob.getContentType();
        return new AudioStream(Channels.newInputStream(blob.reader()), blob.getSize(), contentType);
    }

    /** Opens the stored song audio object for the provided song UUID. */
    public AudioStream openSongAudio(UUID songUuid) {
        return open(objectKey(songUuid));
    }

    /** Returns whether the stored song audio object exists. */
    public boolean existsSongAudio(UUID songUuid) {
        return exists(objectKey(songUuid));
    }

    /** Returns whether the provided object key exists in the configured bucket. */
    public boolean exists(String objectKey) {
        validateConfiguredBucket();

        try {
            Blob blob = storage.get(BlobId.of(properties.getBucket(), objectKey));
            return blob != null && blob.exists();
        } catch (Exception ignored) {
            return false;
        }
    }

    public String objectKey(UUID songUuid) {
        return "audio/" + songUuid;
    }

    private void validateConfiguredBucket() {
        if (properties.getBucket().isBlank()) {
            throw new IllegalStateException("audio.bucket must be configured");
        }
    }

    /** Stream payload returned from GCS. */
    public record AudioStream(InputStream inputStream, long contentLength, String contentType) {}
}
