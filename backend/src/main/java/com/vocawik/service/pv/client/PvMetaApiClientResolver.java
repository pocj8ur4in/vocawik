package com.vocawik.service.pv.client;

import com.vocawik.domain.song.SongPvProvider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Resolves a PV metadata API client by provider. */
@Component
public class PvMetaApiClientResolver {

    private final Map<SongPvProvider, PvMetaApiClient> clientsByProvider;

    public PvMetaApiClientResolver(List<PvMetaApiClient> clients) {
        Map<SongPvProvider, PvMetaApiClient> mapped = new EnumMap<>(SongPvProvider.class);
        for (PvMetaApiClient client : List.copyOf(clients)) {
            mapped.putIfAbsent(client.provider(), client);
        }
        this.clientsByProvider = Map.copyOf(mapped);
    }

    /** Returns a provider-specific metadata client when registered. */
    public Optional<PvMetaApiClient> resolve(SongPvProvider provider) {
        return Optional.ofNullable(clientsByProvider.get(provider));
    }
}
