package Server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private DataInputStream din;
    private DataOutputStream dout;
    private String username = "?";

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    void setUsername(String username) {
        this.username = username;
    }

    String getUsername() {
        return username;
    }

    @Override
    public void run() {
        boolean joined = false;
        try {
            din = new DataInputStream(socket.getInputStream());
            dout = new DataOutputStream(socket.getOutputStream());

            String requestedName = din.readUTF();
            Server.addClient(this, requestedName);
            joined = true;

            Server.broadcast("SYS|" + username + " joined the chat", this);
            Server.broadcastUserList();

            while (true) {
                String text = din.readUTF();
                if (text.trim().isEmpty()) {
                    continue;
                }
                Server.broadcast("MSG|" + username + "|" + text, this);
            }
        } catch (IOException e) {
        } finally {
            if (joined && Server.removeClient(this)) {
                Server.broadcast("SYS|" + username + " left the chat", this);
                Server.broadcastUserList();
            }
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }

    synchronized void send(String message) {
        try {
            dout.writeUTF(message);
            dout.flush();
        } catch (IOException e) {
        }
    }
}
