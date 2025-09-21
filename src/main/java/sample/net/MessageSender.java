package sample.net;

public interface MessageSender {
    void unicast(String message, String recipient);
}
