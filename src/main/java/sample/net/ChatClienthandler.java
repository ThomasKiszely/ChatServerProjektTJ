package sample.net;

import sample.domain.*;
import sample.proto.EmojiParser;
import sample.proto.JsonMessageParser;
import sample.proto.ParseException;
import sample.service.ServerFileService;
import sample.service.JavaAuditLogger;
import sample.service.UserService;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatClienthandler implements Runnable, MessageSender {
    private final Socket socket;
    private final UserService userService = new UserService();
    private final JsonMessageParser jsonMessageParser = new JsonMessageParser();
    private final JavaAuditLogger auditLogger = new JavaAuditLogger();
    private PrintWriter out;
    private User user;
    private String userName;
    private final Map<String, FileOffer> pendingFiles;
    private final Map<User ,PrintWriter> userMap;
    private final List<PrintWriter> gameClients;
    private final List<PrintWriter> chattingClients;
    private final List<PrintWriter> musicClients;

    private ServerFileService serverFileService;


    public ChatClienthandler(Socket socket, Map<String, FileOffer> pendingFiles, Map<User ,PrintWriter> userMap, List<PrintWriter> gameClients, List<PrintWriter> chattingClients,List<PrintWriter> musicClients) {
        this.socket = socket;
        this.pendingFiles = pendingFiles;
        this.serverFileService = new ServerFileService(this,  pendingFiles);
        this.userMap = userMap;
        this.gameClients = gameClients;
        this.chattingClients = chattingClients;
        this.musicClients = musicClients;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        ) {
            out = new PrintWriter(socket.getOutputStream(), true);
            while (user == null) {
                String userName = in.readLine();
                String password = in.readLine();
                user = userService.login(userName, password);
                if (user == null) {
                    out.println("Brugernavn eller kodeord forkert. Prøv igen");
                } else {
                    out.println("loggedIn");
                }
            }


                userMap.put(user, out);
                userName = user.getUsername();
                out.println("Velkommen " + userName + ". Hvilket rum vil du joine? Vælg 1 for gamechat, 2 for casualchat eller 3 for musikchat");
                String choice = jsonMessageParser.parseMessage(in.readLine()).payload();
                System.out.println(choice);
                switch (choice) {
                    case "1":
                        user.setChatRoom(ChatRoom.GAME);
                        gameClients.add(out);
                        break;
                    case "2":
                        user.setChatRoom(ChatRoom.CHATTING);
                        chattingClients.add(out);
                        break;
                    case "3":
                        user.setChatRoom(ChatRoom.MUSIC);
                        musicClients.add(out);
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
    @Override
    public void unicast(String message,String recipientUserName){
        for (User user : userMap.keySet()){
            if (user.getUsername().equals(recipientUserName)){
                PrintWriter recipientOut = userMap.get(user);
                recipientOut.println(message);
                return;
            }
        }
        out.println("Brugeren '" + recipientUserName + "' kan ikke findes");
    }

    private List<PrintWriter> getClientsByRoom(ChatRoom room) {
        return switch (room) {
            case GAME -> gameClients;
            case CHATTING -> chattingClients;
            case MUSIC -> musicClients;
        };
    }
    private void handleMessage (Message message){
        switch (message.chatType()) {
            case TEXT ->
                    broadcast(user.getUsername() + " | " + message.formattedTimestamp() + " | " + message.chatType() + " | " + message.payload(), getClientsByRoom(user.getChatRoom()));
            case EMOJI -> {
                String emoji = EmojiParser.parseEmoji(message.payload());
                broadcast(user.getUsername() + " | " + message.formattedTimestamp() + " | " + message.chatType() + " | " + emoji, getClientsByRoom(user.getChatRoom()));
            }

            case FILE_OFFER -> {
                String[] parts = message.payload().split("\\|");
                if (parts.length != 5) {
                    out.println("Ugyldig metadata for filoverførsel");
                    return;
                }
                String[] fileOffer  = serverFileService.offerFile(message, parts);
                unicast(fileOffer[0], fileOffer[1]);
            }
            case FILE_ACCEPT -> {
                serverFileService.acceptFile(user, out);
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
                joinRoom(message);
            }
        }
    }

    private void joinRoom(Message message) {
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
}
