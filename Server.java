import java.io.*;
import java.net.*;
import java.nio.file.*;

public class Server {
    private static final String PASSWORD = "secure_password";
    private static final int PORT = 12345;
    private static final String SERVER_DIRECTORY = "server_files";

    public static void main(String[] args) {
        try {
            Files.createDirectories(Paths.get(SERVER_DIRECTORY));

            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("Server is running on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (DataInputStream in = new DataInputStream(socket.getInputStream());
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

                String clientPassword = in.readUTF();
                if (!PASSWORD.equals(clientPassword)) {
                    out.writeUTF("Authentication Failed");
                    return;
                }
                out.writeUTF("Authenticated");
                System.out.println("Client authenticated.");

                while (true) {
                    String command = in.readUTF();
                    if ("exit".equalsIgnoreCase(command)) {
                        System.out.println("Client disconnected.");
                        break;
                    }

                    if (command.startsWith("get ")) {
                        String filename = command.split(" ", 2)[1];
                        handleGet(filename, out);
                    } else if (command.startsWith("put ")) {
                        String filename = command.split(" ", 2)[1];
                        handlePut(filename, in, out);
                    } else {
                        out.writeUTF("Invalid command");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void handleGet(String filename, DataOutputStream out) throws IOException {
            File file = new File(SERVER_DIRECTORY, filename);
            if (!file.exists()) {
                out.writeUTF("File not found");
                return;
            }

            out.writeUTF("File found");

            byte[] fileData = Files.readAllBytes(file.toPath());
            int crc = computeCRC(fileData);
            out.writeInt(crc);
            out.writeInt(fileData.length);
            out.write(fileData);
            System.out.println("Sent file: " + filename);
        }

        private void handlePut(String filename, DataInputStream in, DataOutputStream out) throws IOException {
            int receivedCrc = in.readInt();
            int fileLength = in.readInt();
            byte[] fileData = new byte[fileLength];
            in.readFully(fileData);

            int calculatedCrc = computeCRC(fileData);
            if (receivedCrc != calculatedCrc) {
                out.writeUTF("CRC error in received file.");
                return;
            }
            else {
            	System.out.println("CRC correctly calculated.");
            }

            File file = new File(SERVER_DIRECTORY, filename);
            Files.write(file.toPath(), fileData);
            System.out.println("File uploaded successfully.");
            System.out.println("Received file: " + filename);
        }

        private int computeCRC(byte[] data) {
            int crc = 0xFFFF;
            for (byte b : data) {
                crc ^= b << 8;
                for (int i = 0; i < 8; i++) {
                    if ((crc & 0x8000) != 0) {
                        crc = (crc << 1) ^ 0x1021;
                    } else {
                        crc <<= 1;
                    }
                }
            }
            return crc & 0xFFFF;
        }
    }
}
