package sample.service;

import java.time.format.DateTimeFormatter;
import java.util.logging.*;
import sample.domain.Message;

public class JavaAuditLogger implements AuditLogger {
    private static final Logger eventLogger = Logger.getLogger("EventLogger");
    private static final DateTimeFormatter DTF = DateTimeFormatter.ISO_INSTANT;

    @Override
    public void logEvent(Message message){

    }
}
