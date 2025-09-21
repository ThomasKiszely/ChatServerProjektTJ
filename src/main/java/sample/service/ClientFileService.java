package sample.service;

import sample.proto.MessageDTO;

import java.io.*;
import java.net.Socket;

public class ClientFileService {
    private Socket socket;

    public void fileTransfer(MessageDTO msg, String pendingFileName, long pendingFileSize, String host) {
        String[] parts = msg.payload().split("\\|");
        int    filePort = Integer.parseInt(parts[0]);
        String role     = parts[1];

        if (role.equals("UPLOAD")) {
            // Afsender skal uploade til serveren
            new Thread(() ->
                    sendFileOnSeparateSocket(
                            "files/" + pendingFileName,
                            host,
                            filePort
                    )
            ).start();

        } else if (role.equals("DOWNLOAD")) {
            // Modtager skal downloade fra serveren
            String fileName = parts[2];
            long   fileSize = Long.parseLong(parts[3]);

            new Thread(() ->
                    receiveFileOnSeparateSocket(
                            "received_files/" + fileName,
                            host,
                            filePort,
                            fileSize
                    )
            ).start();
        }
    }

    public void receiveFile(String savePath, long fileSize){
        File file = new File(savePath);
        file.getParentFile().mkdirs();
        try (BufferedInputStream inputStream = new BufferedInputStream(socket.getInputStream());
             FileOutputStream fileOutputStream = new FileOutputStream(file)){
            byte[] buffer = new byte[4096];
            long totalRead = 0;
            while (totalRead < fileSize) {
                int bytesToRead = (int) Math.min(fileSize - totalRead, buffer.length);
                int bytesRead = inputStream.read(buffer, 0, bytesToRead);
                if (bytesRead == -1) {
                    break;
                }
                fileOutputStream.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }
            System.out.println("Fil gemt som: " +  savePath);
        } catch (IOException e) {
            System.out.println("Fejl ved modtagelse af fil: " + e.getMessage());
        }
    }
    private void sendFileOnSeparateSocket(String filePath, String host, int port) {
        File file = new File(filePath);
        System.out.println("Sender fil via separat socket " + port + ": " + file.getName());
        try (
                Socket fs = new Socket(host, port);
                BufferedOutputStream bos = new BufferedOutputStream(fs.getOutputStream());
                BufferedInputStream  bis = new BufferedInputStream(new FileInputStream(file))
        ) {
            byte[] buffer = new byte[4096];
            int    r;
            while ((r = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, r);
            }
            bos.flush();
            System.out.println("Fil sendt: " + file.getName());
        } catch (IOException e) {
            System.out.println("Fejl ved filtransfer på port " + port + ": " + e.getMessage());
        }
    }
    private void receiveFileOnSeparateSocket(String savePath,
                                             String host,
                                             int port,
                                             long fileSize) {
        try {
            Thread.sleep(1000); // 1 sekund forsinkelse
        } catch (InterruptedException ignored) {}

        try (
                Socket sock = new Socket(host, port);
                BufferedInputStream bis = new BufferedInputStream(sock.getInputStream());
                FileOutputStream     fos = new FileOutputStream(savePath)
        ) {
            System.out.println("Downloader fil fra server…");
            byte[] buffer = new byte[4096];
            long   total  = 0;

            while (total < fileSize) {
                int toRead = (int) Math.min(buffer.length, fileSize - total);
                int read   = bis.read(buffer, 0, toRead);
                if (read < 0) break;
                fos.write(buffer, 0, read);
                total += read;
            }
            System.out.println("Fil gemt som: " + savePath);

        } catch (IOException e) {
            System.err.println("Fejl ved download: " + e.getMessage());
        }
    }
}
