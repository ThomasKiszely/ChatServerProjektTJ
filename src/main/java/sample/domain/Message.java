package sample.domain;

import java.time.Instant;

public record Message(String clientId, Instant timeStamp, ChatType chatType, String payload) {
    public Message withClientId(String clientId) {
        return new Message(clientId, timeStamp, chatType, payload);
    }
}
