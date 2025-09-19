package sample.proto;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import sample.domain.ChatType;
import sample.domain.Message;


import java.time.Instant;
import java.time.LocalDateTime;

public class JsonMessageParser implements MessageParser {

    private final Gson gson = new Gson();

    private static class MessageDTO {
        String timestamp;
        String clientId;
        String chatType;
        String payload;
        String recipient;
    }
    @Override
    public Message parseMessage(String message) throws ParseException {
        try {
            MessageDTO dto = gson.fromJson(message, MessageDTO.class);

            if (dto.clientId == null || dto.chatType == null || dto.payload == null) {
                throw new ParseException("Mangler felter i JSON: " + message, null);
            }

            ChatType chatType;
            try {
                chatType = ChatType.valueOf(dto.chatType.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ParseException("Chat type ukendt: " + dto.chatType, e);
            }
//            Instant timestamp;
//            if (dto.timestamp != null && !dto.timestamp.isBlank()) {
//                timestamp = Instant.parse(dto.timestamp);
//            } else {
//                timestamp = Instant.now();
//            }

            return new Message(dto.clientId, Instant.now(), chatType, dto.payload, dto.recipient);
        } catch (JsonSyntaxException e) {
            throw new ParseException("Mangler felter i JSON: " + message, e);
        }

    }
}
