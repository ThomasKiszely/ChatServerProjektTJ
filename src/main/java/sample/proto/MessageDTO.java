package sample.proto;

import sample.domain.ChatType;

public record MessageDTO (String clientId, ChatType chatType, String payload, String timestamp, String recipient) {}
