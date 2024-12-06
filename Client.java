import java.io.*;
import java.net.*;
import java.nio.file.*;

public class Client {
    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final int PORT = 12345;
    private static final String CLIENT_DIRECTORY = "client_files";

    public static void main(String[] args) {
        try {
            Socket socket = new Socket(SERVER_ADDRESS, PORT);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            out.writeUTF("secure_password");
            String authResponse = in.readUTF();
            if (!"Authenticated".equals(authResponse)) {
                System.out.println("Authentication failed.");
                socket.close();
                return;
            }
            System.out.println("Authentication successful.");

            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                System.out.print("Enter command (get <filename>, put <filename>, exit): ");
                String command = console.readLine();
                out.writeUTF(command);

                if ("exit".equalsIgnoreCase(command)) {
                    break;
                }

                if (command.startsWith("get ")) {
                    String filename = command.split(" ", 2)[1];
                    handleGet(filename, in);
                } else if (command.startsWith("put ")) {
                    String filename = command.split(" ", 2)[1];
                    handlePut(filename, out);
                } else {
                    System.out.println(in.readUTF());
                }
            }

            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleGet(String filename, DataInputStream in) throws IOException {
        String response = in.readUTF();
        if ("File not found".equals(response)) {
            System.out.println("File not found on server.");
            return;
        } else if (!"File found".equals(response)) {
            System.out.println("Unexpected server response: " + response);
            return;
        }

        int crc = in.readInt();
        int fileLength = in.readInt();
        byte[] fileData = new byte[fileLength];
        in.readFully(fileData);

        int calculatedCrc = computeCRC(fileData);
        if (crc != calculatedCrc) {
            System.out.println("CRC error in received file.");
            return;
        }

        Files.write(Paths.get(CLIENT_DIRECTORY, filename), fileData);
        System.out.println("File downloaded successfully.");
    }

    private static void handlePut(String filename, DataOutputStream out) throws IOException {
        File file = new File(CLIENT_DIRECTORY, filename);
        if (!file.exists()) {
            System.out.println("File not found on client.");
            return;
        }

        byte[] fileData = Files.readAllBytes(file.toPath());
        int crc = computeCRC(fileData);
        out.writeInt(crc);
        out.writeInt(fileData.length);
        out.write(fileData);

        System.out.println("File uploaded successfully.");
    }

    private static int computeCRC(byte[] data) {
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
