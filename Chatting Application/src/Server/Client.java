package Server;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * BitChat client: the Swing chat window.
 *
 * When started it asks for a username and the server's IP address, connects
 * to the server, and then shows the chat. Typed messages are sent from the
 * Send button / Enter key (ActionListener); incoming messages are read on a
 * separate background thread so the window never freezes.
 */
public class Client implements ActionListener {

    private static final int PORT = 6001;

    // Profile picture in the header. Put your own picture in src/icons/ with this
    // name (or change the name here). If the file is missing, no picture is shown.
    private static final String PROFILE_PICTURE = "profile.png";

    private JFrame f = new JFrame();
    private JTextField text;
    private JButton send;
    private JPanel a1;              // the chat area that holds all the bubbles
    private JScrollPane scroll;
    private JLabel status;

    private Socket socket;
    private DataInputStream din;
    private DataOutputStream dout;
    private volatile boolean connected = true;

    // ---------------------------------------------------------------------
    // Constructor: builds the whole chat window and starts listening
    // ---------------------------------------------------------------------
    Client(String username, Socket socket) throws IOException {
        this.socket = socket;
        din = new DataInputStream(socket.getInputStream());
        dout = new DataOutputStream(socket.getOutputStream());

        // The first thing the server expects is our username.
        dout.writeUTF(username);
        dout.flush();

        //null means not using any preset layout
        f.setLayout(null);

        //create a new panel for name (the green header)
        JPanel p1 = new JPanel();
        p1.setBackground(new Color(7, 94, 84));
        p1.setBounds(0, 0, 450, 70);
        p1.setLayout(null);
        f.add(p1);

        // The window has no title bar, so the header can be dragged to move it.
        MouseAdapter dragger = new MouseAdapter() {
            private Point start;

            @Override
            public void mousePressed(MouseEvent e) {
                start = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                Point p = f.getLocation();
                f.setLocation(p.x + e.getX() - start.x, p.y + e.getY() - start.y);
            }
        };
        p1.addMouseListener(dragger);
        p1.addMouseMotionListener(dragger);

        //Back button: leaves the chat and closes the window
        JLabel back = new JLabel(loadIcon("previous.png", 25, 25));
        back.setBounds(5, 20, 25, 25);
        p1.add(back);
        back.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent ae) {
                disconnect();
                System.exit(0);
            }
        });

        //Profile Image
        JLabel profile = new JLabel(loadIcon(PROFILE_PICTURE, 50, 50));
        profile.setBounds(40, 10, 50, 50);
        p1.add(profile);

        //VideoCall Icon
        JLabel videoCall = new JLabel(loadIcon("video.png", 35, 30));
        videoCall.setBounds(300, 20, 35, 30);
        p1.add(videoCall);

        //Phone Icon
        JLabel phone = new JLabel(loadIcon("call.png", 25, 25));
        phone.setBounds(360, 20, 35, 35);
        p1.add(phone);

        //More Options
        JLabel more = new JLabel(loadIcon("menu.png", 10, 25));
        more.setBounds(420, 20, 10, 25);
        p1.add(more);

        //Name of the chat
        JLabel name = new JLabel("BitChat");
        name.setBounds(110, 15, 200, 18);
        name.setForeground(Color.WHITE);
        name.setFont(new Font("SANS_SERIF", Font.BOLD, 18));
        p1.add(name);

        //Status: how many people are online (updated when the server sends the list)
        status = new JLabel("Connected");
        status.setBounds(110, 35, 180, 18);
        status.setForeground(Color.WHITE);
        status.setFont(new Font("SANS_SERIF", Font.PLAIN, 13));
        p1.add(status);

        //Chat Area: bubbles are stacked from top to bottom
        a1 = new JPanel();
        a1.setLayout(new BoxLayout(a1, BoxLayout.Y_AXIS));
        a1.setBackground(Color.WHITE);

        // The wrapper keeps the bubbles at the top instead of stretching them
        // to fill the whole window when there are only a few messages.
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Color.WHITE);
        wrapper.add(a1, BorderLayout.PAGE_START);

        scroll = new JScrollPane(wrapper);
        scroll.setBounds(5, 75, 440, 570);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        f.add(scroll);

        //Text field area
        text = new JTextField();
        text.setBounds(5, 655, 310, 40);
        text.setFont(new Font("SANS_SERIF", Font.PLAIN, 16));
        f.add(text);

        //Send Button
        send = new JButton("Send");
        send.setBounds(320, 655, 123, 40);
        send.setBackground(new Color(7, 94, 84));
        send.setForeground(Color.WHITE);
        send.setOpaque(true);                   // without these two lines macOS ignores the
        send.setBorderPainted(false);           // green background and the white text disappears
        send.setFont(new Font("SANS_SERIF", Font.PLAIN, 16));
        send.addActionListener(this);
        f.add(send);

        //Enable Enter key send
        text.addActionListener(this);

        f.setSize(450, 700);
        f.setLocationRelativeTo(null);          // centre of the screen ...
        f.setLocation(f.getX(), 50);            // ... but near the top
        f.getContentPane().setBackground(Color.WHITE);
        f.setUndecorated(true);
        f.setVisible(true);
        text.requestFocusInWindow();

        addSystemMessage("Connected as " + username);

        // Read incoming messages on a separate thread, so the window stays usable.
        Thread listener = new Thread(new Runnable() {
            @Override
            public void run() {
                receiveMessages();
            }
        });
        listener.setDaemon(true);
        listener.start();
    }

    // ---------------------------------------------------------------------
    // Sending
    // ---------------------------------------------------------------------

    //overriding the ActionListener Interface
    @Override
    public void actionPerformed(ActionEvent ae) {
        String out = text.getText().trim();
        if (out.isEmpty() || !connected) {
            return;
        }
        if (out.length() > 1000) {
            out = out.substring(0, 1000);       // keep messages a sensible size
        }

        try {
            dout.writeUTF(out);
            dout.flush();

            // show our own message as a green bubble on the right
            addBubble(formatLabel(null, out), true);
            text.setText("");
        } catch (IOException e) {
            showDisconnected();
        }
    }

    // ---------------------------------------------------------------------
    // Receiving (runs on the background thread)
    // ---------------------------------------------------------------------
    private void receiveMessages() {
        try {
            while (true) {
                final String msg = din.readUTF();       // waits for the next message from the server

                // Swing components must only be changed on the Swing thread,
                // so we hand the work over with invokeLater.
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        handleIncoming(msg);
                    }
                });
            }
        } catch (IOException e) {
            if (connected) {                            // not just us closing the window
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        showDisconnected();
                    }
                });
            }
        }
    }

    /** Works out what kind of message the server sent and shows it. */
    private void handleIncoming(String msg) {
        String[] parts = msg.split("\\|", 3);       // e.g. "MSG|alice|hello" -> MSG, alice, hello

        if (parts[0].equals("MSG") && parts.length == 3) {
            addBubble(formatLabel(parts[1], parts[2]), false);
        } else if (parts[0].equals("SYS") && parts.length >= 2) {
            addSystemMessage(parts[1]);
        } else if (parts[0].equals("USERS")) {
            String list = parts.length >= 2 ? parts[1] : "";
            String[] names = list.isEmpty() ? new String[0] : list.split(",");
            status.setText(names.length + " online");
            status.setToolTipText("Online: " + list.replace(",", ", "));
        }
    }

    // ---------------------------------------------------------------------
    // Drawing the chat
    // ---------------------------------------------------------------------

    /**
     * Builds one chat bubble with the time shown below it.
     * sender == null means "my own message" (green); otherwise it is a
     * received message (gray) with the sender's name in bold on top.
     */
    public static JPanel formatLabel(String sender, String out) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // Text is escaped so that something like <b> typed by a user shows up as text.
        String body = escapeHtml(out);
        if (sender != null) {
            body = "<b>" + escapeHtml(sender) + "</b><br>" + body;
        }

        JLabel output = new JLabel("<html><p style=\"width: 150px\">" + body + "</p></html>");
        output.setFont(new Font("Tahoma", Font.PLAIN, 16));
        if (sender == null) {
            output.setBackground(new Color(37, 211, 102));      // green: sent by me
        } else {
            output.setBackground(new Color(240, 240, 240));     // gray: received
        }
        output.setOpaque(true);
        output.setBorder(new EmptyBorder(15, 15, 15, 50));
        panel.add(output);

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        JLabel time = new JLabel(sdf.format(new Date()));
        panel.add(time);

        panel.setBackground(Color.WHITE);
        return panel;
    }

    /** Puts a bubble in the chat: on the right if it is mine, on the left otherwise. */
    private void addBubble(JPanel bubble, boolean mine) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(Color.WHITE);
        row.add(bubble, mine ? BorderLayout.LINE_END : BorderLayout.LINE_START);
        addRow(row);
    }

    /** A small centred line such as "bob joined the chat". */
    private void addSystemMessage(String message) {
        JLabel label = new JLabel("<html>" + escapeHtml(message) + "</html>", SwingConstants.CENTER);
        label.setFont(new Font("SANS_SERIF", Font.ITALIC, 12));
        label.setForeground(Color.GRAY);

        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(Color.WHITE);
        row.add(label, BorderLayout.CENTER);
        addRow(row);
    }

    private void addRow(JPanel row) {
        a1.add(row);
        a1.add(Box.createVerticalStrut(8));         // gap between messages
        a1.revalidate();
        a1.repaint();

        // scroll down so the newest message is visible
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JScrollBar bar = scroll.getVerticalScrollBar();
                bar.setValue(bar.getMaximum());
            }
        });
    }

    private void showDisconnected() {
        if (!connected) {
            return;
        }
        connected = false;
        addSystemMessage("Disconnected from the server");
        status.setText("Offline");
        text.setEnabled(false);
        send.setEnabled(false);
    }

    private void disconnect() {
        connected = false;
        try {
            socket.close();
        } catch (IOException e) {
            // already closed
        }
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Loads an icon from the icons folder and scales it to the given size. */
    private static ImageIcon loadIcon(String file, int w, int h) {
        URL url = Client.class.getResource("/icons/" + file);
        ImageIcon raw;
        if (url != null) {
            raw = new ImageIcon(url);
        } else if (new File("src/icons/" + file).exists()) {
            raw = new ImageIcon("src/icons/" + file);       // fallback if icons were not copied next to the classes
        } else {
            return new ImageIcon();                         // picture not found: show nothing instead of crashing
        }
        Image scaled = raw.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    // ---------------------------------------------------------------------
    // Start-up: ask for username + server IP, connect, open the chat window
    // ---------------------------------------------------------------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                start();
            }
        });
    }

    private static void start() {
        String lastName = "";
        String lastIp = "127.0.0.1";

        while (true) {
            // Login box with two fields
            JTextField nameField = new JTextField(lastName, 15);
            JTextField ipField = new JTextField(lastIp, 15);
            JPanel form = new JPanel(new GridLayout(0, 1, 5, 5));
            form.add(new JLabel("Username:"));
            form.add(nameField);
            form.add(new JLabel("Server IP address:"));
            form.add(ipField);

            int choice = JOptionPane.showConfirmDialog(null, form, "BitChat - Join the chat",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                System.exit(0);                     // user pressed Cancel
            }

            lastName = nameField.getText().trim();
            lastIp = ipField.getText().trim();

            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(lastIp, PORT), 5000);   // give up after 5 seconds
                new Client(lastName, s);
                return;                             // connected: the chat window is now open
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null,
                        "Could not connect to " + lastIp + ":" + PORT + "\n(" + e.getMessage() + ")\n\n"
                                + "Check that the server is running and that both\n"
                                + "computers are on the same network.",
                        "BitChat", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
