package sample.domain;

public class FileOffer {
    public final String sender;
    public final String recipient;
    public final String fileName;
    public final Long fileSize;
    public FileOffer(String sender, String recipient, String fileName, Long fileSize) {
        this.sender = sender;
        this.recipient = recipient;
        this.fileName = fileName;
        this.fileSize = fileSize;
    }
}
