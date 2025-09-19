package sample.service;

import sample.domain.Message;
import sample.domain.User;

public interface AuditLogger {
    void logEvent(Message message, User user);
}
