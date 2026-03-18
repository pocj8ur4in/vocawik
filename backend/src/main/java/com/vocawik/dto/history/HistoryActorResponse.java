package com.vocawik.dto.history;

/** Actor summary for history timeline/detail payloads. */
public record HistoryActorResponse(String type, String userNickname, String guestIp) {}
