package sample.proto;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import sample.domain.ChatType;
import sample.domain.Message;


import java.time.Instant;

public class JsonMessageParser implements MessageParser {

    private final Gson gson = new Gson();

    private static class MessageDTO {
        String clientId;
        String chatType;
        String payload;
    }
    @Override
    public Message parseMessage(String message) throws ParseException {
        try {
            System.out.println("Parsing JSON message: " + message);
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
            return new Message(dto.clientId, Instant.now(), chatType, dto.payload);
        } catch (JsonSyntaxException e) {
            throw new ParseException("Mangler felter i JSON: " + message, e);
        }

    }
}
