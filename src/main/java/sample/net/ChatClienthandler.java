package sample.net;

import com.google.gson.Gson;
import sample.domain.*;
import sample.proto.JsonMessageParser;
import sample.proto.MessageDTO;
import sample.proto.ParseException;
import sample.service.JavaAuditLogger;
import sample.service.UserService;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatClienthandler implements Runnable{
    private final Socket socket;
    private final UserService userService = new UserService();
    private final JsonMessageParser jsonMessageParser = new JsonMessageParser();
    private final JavaAuditLogger auditLogger = new JavaAuditLogger();
    private PrintWriter out;
    private User user;
    private final static Map<String, FileOffer> pendingFiles = Collections.synchronizedMap(new HashMap<>());

    public ChatClienthandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        ) {
         out = new PrintWriter(socket.getOutputStream(), true);
            String userName = in.readLine();
            String password = in.readLine();
            user = userService.login(userName, password);
            if (user == null) {
                out.println("Brugernavn eller kodeord forkert. Prøv igen");
            } else {
                ChatServer.userMap.put(user, out);
                out.println("Velkommen " + userName + ". Hvilket rum vil du joine? Vælg 1 for gamechat, 2 for casualchat eller 3 for musikchat");
                String choice = jsonMessageParser.parseMessage(in.readLine()).payload();
                System.out.println(choice);
                switch (choice) {
                    case "1":
                        user.setChatRoom(ChatRoom.GAME);
                        ChatServer.gameClients.add(out);
                        break;
                    case "2":
                        user.setChatRoom(ChatRoom.CHATTING);
                        ChatServer.chattingClients.add(out);
                        break;
                    case "3":
                        user.setChatRoom(ChatRoom.MUSIC);
                        ChatServer.musicClients.add(out);
                        break;
                }
                broadcast(userName + " er tilsluttet chatrummet " + user.getChatRoom(), getClientsByRoom(user.getChatRoom()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    try {
                        Message msg = jsonMessageParser.parseMessage(inputLine);
                        auditLogger.logEvent(msg, user);
                        handleMessage(msg);
                    } catch (Exception e) {
                        out.println("Fejl i besked: " + e.getMessage());
                    }
                }

                }
            } catch(IOException e){
                System.out.println("Fejl i forbindelsen: " + e.getMessage());
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }
    public void broadcast(String message, List<PrintWriter> clients) {
        synchronized(clients){
            for (PrintWriter client : clients) {
                client.println(message);
            }
        }
    }
    public void unicast(String message,String recipientUserName){
        for (User user : ChatServer.userMap.keySet()){
            if (user.getUsername().equals(recipientUserName)){
                PrintWriter recipientOut = ChatServer.userMap.get(user);
                recipientOut.println(message);
                return;
            }
        }
        out.println("Brugeren '" + recipientUserName + "' kan ikke findes");
    }

    private List<PrintWriter> getClientsByRoom(ChatRoom room) {
        return switch (room) {
            case GAME -> ChatServer.gameClients;
            case CHATTING -> ChatServer.chattingClients;
            case MUSIC -> ChatServer.musicClients;
        };
    }
    private void handleMessage (Message message){
        switch (message.chatType()) {
            case TEXT -> broadcast(user.getUsername() + " | " + message.formattedTimestamp() + " | " + message.chatType() + " | " + message.payload(), getClientsByRoom(user.getChatRoom()));
            case EMOJI ->
                    broadcast(user.getUsername() + " sender emoji: " + message.payload(), getClientsByRoom(user.getChatRoom()));
            case FILE_OFFER -> {
                String[] parts = message.payload().split("\\|");
                if (parts.length != 5) {
                    out.println("Ugyldig metadata for filoverførsel");
                    return;
                }
                String sender = parts[0];
                String timestamp = parts[1];
                String filename = parts[3];
                long fileSize = Long.parseLong(parts[4]);
                String recipient = message.recipient();

                pendingFiles.put(recipient, new FileOffer(sender, recipient, filename, fileSize));
                unicast(sender + " vil sende dig filen '" + filename + "' (Størrelse: " + fileSize + " bytes. Svar /FILE_ACCEPT eller /FILE_REJECT.", recipient);
            }
            case FILE_ACCEPT -> {
                FileOffer offer = pendingFiles.remove(user.getUsername());
                if (offer == null) {
                    out.println("Ingen ventende filer");
                    return;
                }
                // 1) Bekræft accept til afsender
                unicast(
                        "Bruger " + user.getUsername() + " har accepteret din fil: " + offer.fileName,
                        offer.sender
                );

                // 2) Find en ledig port
                int filePort;
                try {
                    filePort = findFreePort();
                } catch (IOException e) {
                    out.println("Kunne ikke allokere port til filtransfer");
                    return;
                }

                // 3) Send portnummer som JSON via MessageDTO
                MessageDTO portMsg = new MessageDTO(
                        "server",
                        ChatType.FILE_PORT,
                        String.valueOf(filePort),
                        null,
                        offer.sender
                );
                out.println(new Gson().toJson(portMsg));

                // 4) Start file-server i en ny tråd
                new Thread(() -> startFileServer(filePort, offer)).start();
            }

            case FILE_REJECT -> {
                FileOffer offer = pendingFiles.remove(user.getUsername());
                if (offer != null) {
                    unicast("Bruger " + user.getUsername() + " har afvist din fil: " + offer.fileName, offer.sender);
                } else {
                    out.println("Ingen ventende filer");
                }
            }
            case PRIVATE -> {
                if (message.recipient() != null && !message.recipient().isBlank()) {
                    unicast("Privat besked fra " + user.getUsername() + ": " + message.payload(), message.recipient());
                } else {
                    out.println("Der er ingen brugere med navnet: " + message.recipient());
                }
            }
            case JOIN_ROOM -> {
                removeClientFromRoom();
                try {
                    ChatRoom chatRoom = ChatRoom.valueOf(message.payload().trim().toUpperCase());
                    user.setChatRoom(chatRoom);
                    getClientsByRoom(chatRoom).add(out);
                    broadcast("Bruger " + user.getUsername() + " har skiftet til chatrummet " + chatRoom, getClientsByRoom(chatRoom));
                    out.println("Du er nu i rummet: " + chatRoom);
                } catch (IllegalArgumentException e) {
                    out.println("Ugyldigt chatrum: " + message.payload());
                    getClientsByRoom(user.getChatRoom()).add(out);
                }
            }
        }
    }

    private void handleFiletransfer(FileOffer offer) {
        File outputFile = new File("received_files/" +  offer.fileName);
        outputFile.getParentFile().mkdirs();
        out.println("Filoverførsel starter");
        try (
                BufferedInputStream inputStream = new BufferedInputStream(socket.getInputStream());
                FileOutputStream outputStream = new FileOutputStream(outputFile)
        ){
            byte[] buffer = new byte[1024];
            long bytesReadTotal = 0;
            while (bytesReadTotal < offer.fileSize) {
                int bytesToRead = (int) Math.min(buffer.length, offer.fileSize - bytesReadTotal);
                int bytesRead = inputStream.read(buffer, 0, bytesToRead);
                if (bytesRead == -1) {
                    break;
                }
                outputStream.write(buffer, 0, bytesRead);
                bytesReadTotal += bytesRead;
            }
            out.println("Fil '" + offer.fileName + "' modtaget fra " + offer.sender);
        }
        catch (IOException e) {
            out.println("Fejl under overførsel af filen '" + offer.fileName + "': " + e.getMessage());
        }
    }
    private void removeClientFromRoom(){
        if (user == null || out == null) {
            return;
        }
        List <PrintWriter> clients = getClientsByRoom(user.getChatRoom());
        synchronized (clients) {
            clients.remove(out);
        }
        broadcast("User " + user.getUsername() + " har forladt rummet", clients);
    }
    /** Åbner en midlertidig ServerSocket på port 0, returnerer den tildelte port */
    private int findFreePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    /** Accepterer én forbindelse og skriver filen til disk */
    private void startFileServer(int port, FileOffer offer) {
        try (
                ServerSocket server = new ServerSocket(port);
                Socket fsocket = server.accept();
                BufferedInputStream bis = new BufferedInputStream(fsocket.getInputStream());
                FileOutputStream fos = new FileOutputStream("received_files/" + offer.fileName)
        ) {
            byte[] buf = new byte[4096];
            long total = 0;
            int r;
            while ((r = bis.read(buf)) != -1 && total < offer.fileSize) {
                fos.write(buf, 0, r);
                total += r;
            }
            fos.flush();
            unicast("Fil modtaget: " + offer.fileName, offer.sender);
        } catch (IOException e) {
            unicast("Fejl under modtagelse af fil '" + offer.fileName + "': " + e.getMessage(), offer.sender);
        }
    }

}
