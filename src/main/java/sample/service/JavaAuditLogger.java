package sample.service;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

import sample.domain.ChatType;
import sample.domain.Message;
import sample.domain.User;
import sample.proto.EmojiParser;

public class JavaAuditLogger implements AuditLogger {
    private static final Logger eventLogger = Logger.getLogger("EventLogger");
    private static final DateTimeFormatter DTF = DateTimeFormatter.ISO_INSTANT;

    static {
        try {
            FileHandler fileHandler = new FileHandler("Event.log", 1_000_000, 5, true);
            fileHandler.setFormatter(new SimpleFormatter());
            eventLogger.addHandler(fileHandler);
            eventLogger.setUseParentHandlers(false);
        } catch (IOException e) {
            System.out.println("Fejl ved opstart af log: " + e.getMessage());
        }
    }


    @Override
    public void logEvent(Message message, User user) {
        String logText;
        if (message.chatType() == ChatType.EMOJI){
            String emoji = EmojiParser.parseEmoji(message.payload());
            logText = String.format(message.clientId() + " | " + message.formattedTimestamp() + " | " + user.getChatRoom() + " | " + message.chatType() + " | " +  emoji + " | " + message.recipient());
            eventLogger.info(logText);
            return;
        }
        logText = String.format(message.clientId() + " | " + message.formattedTimestamp() + " | " + user.getChatRoom() + " | " + message.chatType() + " | " +  message.payload() + " | " + message.recipient());
        eventLogger.info(logText);
    }
}
