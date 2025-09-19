package sample.domain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record Message(String clientId, Instant timeStamp, ChatType chatType, String payload, String recipient) {
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    public String formattedTimestamp() {
        return DTF.format(timeStamp);
    }

}
