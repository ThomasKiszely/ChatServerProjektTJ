package sample.service;

import sample.domain.Message;

public interface AuditLogger {
    void logEvent(Message message);
}
