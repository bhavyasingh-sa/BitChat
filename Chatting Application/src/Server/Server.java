package Server;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Enumeration;

public class Server {
    static final int PORT = 6001;

    private static final ArrayList<ClientHandler> clients = new ArrayList<ClientHandler>();

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("BitChat server started on port " + PORT);
            printAddresses();
            System.out.println("Waiting for clients...\n");

            while (true) {
                Socket socket = serverSocket.accept();

                ClientHandler handler = new ClientHandler(socket);
                Thread thread = new Thread(handler);
                thread.start();
            }
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }

    static synchronized String addClient(ClientHandler handler, String requestedName) {
        String name = cleanName(requestedName);
        String finalName = name;
        int n = 2;
        while (isNameTaken(finalName)) {
            finalName = name + n;
            n++;
        }
        handler.setUsername(finalName);
        clients.add(handler);
        System.out.println(finalName + " joined  (" + clients.size() + " online)");
        return finalName;
    }

    static synchronized boolean removeClient(ClientHandler handler) {
        boolean removed = clients.remove(handler);
        if (removed) {
            System.out.println(handler.getUsername() + " left    (" + clients.size() + " online)");
        }
        return removed;
    }

    static synchronized void broadcast(String message, ClientHandler except) {
        for (ClientHandler c : clients) {
            if (c != except) {
                c.send(message);
            }
        }
    }

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
