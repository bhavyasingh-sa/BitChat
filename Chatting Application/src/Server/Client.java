package Server;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class Client implements ActionListener {
    private static final int PORT = 6001;

    private JFrame f = new JFrame();
    private JTextField text;
    private JButton send;
    private JPanel a1;
    private JScrollPane scroll;
    private JLabel status;

    private Socket socket;
    private DataInputStream din;
    private DataOutputStream dout;
    private volatile boolean connected = true;

    Client(String username, Socket socket) throws IOException {
        this.socket = socket;
        din = new DataInputStream(socket.getInputStream());
        dout = new DataOutputStream(socket.getOutputStream());

        dout.writeUTF(username);
        dout.flush();

        f.setLayout(null);

        JPanel p1 = new JPanel();
        p1.setBackground(new Color(7, 94, 84));
        p1.setBounds(0, 0, 450, 70);
        p1.setLayout(null);
        f.add(p1);

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

        JLabel videoCall = new JLabel(loadIcon("video.png", 35, 30));
        videoCall.setBounds(300, 20, 35, 30);
        p1.add(videoCall);

        JLabel phone = new JLabel(loadIcon("call.png", 25, 25));
        phone.setBounds(360, 20, 35, 35);
        p1.add(phone);

        JLabel more = new JLabel(loadIcon("menu.png", 10, 25));
        more.setBounds(420, 20, 10, 25);
        p1.add(more);

        JLabel name = new JLabel("BitChat");
        name.setBounds(45, 15, 200, 18);
        name.setForeground(Color.WHITE);
        name.setFont(new Font("SANS_SERIF", Font.BOLD, 18));
        p1.add(name);

        status = new JLabel("Connected");
        status.setBounds(45, 35, 180, 18);
        status.setForeground(Color.WHITE);
        status.setFont(new Font("SANS_SERIF", Font.PLAIN, 13));
        p1.add(status);

        a1 = new JPanel();
        a1.setLayout(new BoxLayout(a1, BoxLayout.Y_AXIS));
        a1.setBackground(Color.WHITE);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Color.WHITE);
        wrapper.add(a1, BorderLayout.PAGE_START);

        scroll = new JScrollPane(wrapper);
        scroll.setBounds(5, 75, 440, 570);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        f.add(scroll);

        text = new JTextField();
        text.setBounds(5, 655, 310, 40);
        text.setFont(new Font("SANS_SERIF", Font.PLAIN, 16));
        f.add(text);

        send = new JButton("Send");
        send.setBounds(320, 655, 123, 40);
        send.setBackground(new Color(7, 94, 84));
        send.setForeground(Color.WHITE);
        send.setOpaque(true);
        send.setBorderPainted(false);
        send.setFont(new Font("SANS_SERIF", Font.PLAIN, 16));
        send.addActionListener(this);
        f.add(send);

        text.addActionListener(this);

        f.setSize(450, 700);
        f.setLocationRelativeTo(null);
        f.setLocation(f.getX(), 50);
        f.getContentPane().setBackground(Color.WHITE);
        f.setUndecorated(true);
        f.setVisible(true);
        text.requestFocusInWindow();

        addSystemMessage("Connected as " + username);

        Thread listener = new Thread(new Runnable() {
            @Override
            public void run() {
                receiveMessages();
            }
        });
        listener.setDaemon(true);
        listener.start();
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        String out = text.getText().trim();
        if (out.isEmpty() || !connected) {
            return;
        }
        if (out.length() > 1000) {
            out = out.substring(0, 1000);
        }

        try {
            dout.writeUTF(out);
            dout.flush();

            addBubble(formatLabel(null, out), true);
            text.setText("");
        } catch (IOException e) {
            showDisconnected();
        }
    }

    private void receiveMessages() {
        try {
            while (true) {
                final String msg = din.readUTF();

                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        handleIncoming(msg);
                    }
                });
            }
        } catch (IOException e) {
            if (connected) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        showDisconnected();
                    }
                });
            }
        }
    }

    private void handleIncoming(String msg) {
        String[] parts = msg.split("\\|", 3);

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

    public static JPanel formatLabel(String sender, String out) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        String body = escapeHtml(out);
        if (sender != null) {
            body = "<b>" + escapeHtml(sender) + "</b><br>" + body;
        }

        JLabel output = new JLabel("<html><p style=\"width: 150px\">" + body + "</p></html>");
        output.setFont(new Font("Tahoma", Font.PLAIN, 16));
        if (sender == null) {
            output.setBackground(new Color(37, 211, 102));
        } else {
            output.setBackground(new Color(240, 240, 240));
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

    private void addBubble(JPanel bubble, boolean mine) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(Color.WHITE);
        row.add(bubble, mine ? BorderLayout.LINE_END : BorderLayout.LINE_START);
        addRow(row);
    }

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
        a1.add(Box.createVerticalStrut(8));
        a1.revalidate();
        a1.repaint();

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
        }
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static ImageIcon loadIcon(String file, int w, int h) {
        URL url = Client.class.getResource("/icons/" + file);
        ImageIcon raw;
        if (url != null) {
            raw = new ImageIcon(url);
        } else if (new File("src/icons/" + file).exists()) {
            raw = new ImageIcon("src/icons/" + file);
        } else {
            return new ImageIcon();
        }
        Image scaled = raw.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

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
                System.exit(0);
            }

            lastName = nameField.getText().trim();
            lastIp = ipField.getText().trim();

            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(lastIp, PORT), 5000);
                new Client(lastName, s);
                return;
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
