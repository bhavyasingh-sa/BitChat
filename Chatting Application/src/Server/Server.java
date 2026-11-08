package Server;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Enumeration;

/**
 * BitChat server.
 *
 * It listens on port 6001, accepts every client that connects and starts one
 * ClientHandler thread for each of them. It also keeps the list of connected
 * clients and passes every message on to the other clients.
 *
 * The server is a console program (it has no window): run it first, then run
 * Client on each laptop.
 */
public class Server {

    static final int PORT = 6001;

    // Every connected user has one ClientHandler object in this list.
    private static final ArrayList<ClientHandler> clients = new ArrayList<ClientHandler>();

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("BitChat server started on port " + PORT);
            printAddresses();
            System.out.println("Waiting for clients...\n");

            // Keep accepting clients for as long as the server runs.
            while (true) {
                Socket socket = serverSocket.accept();      // waits until a client connects

                ClientHandler handler = new ClientHandler(socket);
                Thread thread = new Thread(handler);        // one thread for every client
                thread.start();
            }
        } catch (IOException e) {
            // Most common reason: the port is already in use (server started twice).
            System.out.println("Server error: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // The methods below are 'synchronized' because many ClientHandler threads
    // use the clients list at the same time. Only one thread may be inside
    // any of them at a time, so the list is never changed while it is being
    // read.
    // ---------------------------------------------------------------------

    /** Adds a new client to the list and returns the (unique) name it was given. */
    static synchronized String addClient(ClientHandler handler, String requestedName) {
        String name = cleanName(requestedName);
        String finalName = name;
        int n = 2;
        while (isNameTaken(finalName)) {        // two "alice" -> "alice" and "alice2"
            finalName = name + n;
            n++;
        }
        handler.setUsername(finalName);
        clients.add(handler);
        System.out.println(finalName + " joined  (" + clients.size() + " online)");
        return finalName;
    }

    /** Removes a client. Returns true if it was really in the list. */
    static synchronized boolean removeClient(ClientHandler handler) {
        boolean removed = clients.remove(handler);
        if (removed) {
            System.out.println(handler.getUsername() + " left    (" + clients.size() + " online)");
        }
        return removed;
    }

    /** Sends a message to every client except 'except' (pass null to send to all). */
    static synchronized void broadcast(String message, ClientHandler except) {
        for (ClientHandler c : clients) {
            if (c != except) {
                c.send(message);
            }
        }
    }

    /** Tells everybody who is online right now, e.g. "USERS|alice,bob,carol". */
    static synchronized void broadcastUserList() {
        StringBuilder names = new StringBuilder();
        for (ClientHandler c : clients) {
            if (names.length() > 0) {
                names.append(",");
            }
            names.append(c.getUsername());
        }
        broadcast("USERS|" + names, null);
    }

    private static boolean isNameTaken(String name) {
        for (ClientHandler c : clients) {
            if (name.equalsIgnoreCase(c.getUsername())) {
                return true;
            }
        }
        return false;
    }

    /** '|' and ',' are used by the message format, so they are not allowed in names. */
    private static String cleanName(String name) {
        String cleaned = name.trim().replace("|", "_").replace(",", "_");
        if (cleaned.length() > 20) {
            cleaned = cleaned.substring(0, 20);
        }
        if (cleaned.isEmpty()) {
            cleaned = "Guest";
        }
        return cleaned;
    }

    /** Prints this computer's addresses so you know what to type in the client. */
    private static void printAddresses() {
        System.out.println("Clients on other laptops should connect to one of these addresses:");
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            while (nets.hasMoreElements()) {
                NetworkInterface net = nets.nextElement();
                if (!net.isUp() || net.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = net.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address) {
                        System.out.println("    " + address.getHostAddress() + "   (" + net.getDisplayName() + ")");
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("    (could not read the network addresses)");
        }
        System.out.println("On this same computer you can use 127.0.0.1");
    }
}
