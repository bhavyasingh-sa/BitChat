package Server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Looks after ONE connected client. The server creates one ClientHandler
 * (and one thread to run it) for every client that connects.
 *
 * Implementing Runnable means the class has a run() method that a Thread can
 * execute, so each handler waits for its own client's messages without
 * blocking the other clients.
 *
 * Message format on the wire (all sent with writeUTF / read with readUTF):
 *   client -> server : the first message is the username, after that plain text
 *   server -> client : MSG|sender|text   a chat message
 *                      SYS|text          "x joined / left the chat"
 *                      USERS|a,b,c       the names of everybody online
 */
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

            // 1. The first thing a client sends is its username.
            String requestedName = din.readUTF();
            Server.addClient(this, requestedName);
            joined = true;

            Server.broadcast("SYS|" + username + " joined the chat", this);
            Server.broadcastUserList();

            // 2. After that, pass every message on to all the OTHER clients.
            while (true) {
                String text = din.readUTF();            // waits for this client's next message
                if (text.trim().isEmpty()) {
                    continue;
                }
                Server.broadcast("MSG|" + username + "|" + text, this);
            }
        } catch (IOException e) {
            // The client closed its window or the connection dropped.
            // This is normal, so the server just carries on.
        } finally {
            if (joined && Server.removeClient(this)) {
                Server.broadcast("SYS|" + username + " left the chat", this);
                Server.broadcastUserList();
            }
            try {
                socket.close();
            } catch (IOException e) {
                // nothing more to do
            }
        }
    }

    /**
     * Sends one message to this client. 'synchronized' because several other
     * handlers may call it at the same moment.
     */
    synchronized void send(String message) {
        try {
            dout.writeUTF(message);
            dout.flush();
        } catch (IOException e) {
            // This client has gone; its own run() loop will notice and remove it.
        }
    }
}
