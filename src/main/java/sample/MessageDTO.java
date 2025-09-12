package sample;

import sample.domain.ChatType;

public record MessageDTO (String clientId, ChatType chatType, String payload) {}
