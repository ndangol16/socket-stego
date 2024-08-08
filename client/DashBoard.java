package client;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.List;

public class DashBoard extends JFrame {
    private JLabel lbl_name;
    private JButton btn_sendRequest;
    private JButton btn_receiveRequests;
    private JTextField txt_friendUsername;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private JList<String> friendList;
    private DefaultListModel<String> friendListModel;
    private JPanel imagePanel;
    private JTextArea dragDropArea;
    private JDialog requestDialog;
    private int userId;

    public DashBoard(int userId, String username) {
        this.userId = userId;

        setTitle("Dashboard");
        setSize(600, 500);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        lbl_name = new JLabel("Welcome " + username, SwingConstants.CENTER);
        add(lbl_name, BorderLayout.NORTH);

        // Menu bar with logout option
        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("Menu");
        JMenuItem logoutItem = new JMenuItem("Logout");
        logoutItem.addActionListener(e -> logout());
        menu.add(logoutItem);
        menuBar.add(menu);
        setJMenuBar(menuBar);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Friends"));

        friendListModel = new DefaultListModel<>();
        friendList = new JList<>(friendListModel);
        leftPanel.add(new JScrollPane(friendList), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new GridLayout(3, 1, 10, 10));
        btn_sendRequest = new JButton("Send Friend Request");
        btn_sendRequest.addActionListener(e -> sendFriendRequest());

        btn_receiveRequests = new JButton("Pending Friend Requests");
        btn_receiveRequests.addActionListener(e -> showPendingRequests());

        buttonPanel.add(btn_sendRequest);
        buttonPanel.add(btn_receiveRequests);

        leftPanel.add(buttonPanel, BorderLayout.SOUTH);
        add(leftPanel, BorderLayout.WEST);

        imagePanel = new JPanel(new BorderLayout());
        imagePanel.setBorder(BorderFactory.createTitledBorder("Drag and Drop Image"));
        dragDropArea = new JTextArea("Drag and drop image here");
        dragDropArea.setDropTarget(new DropTarget() {
            @Override
            public synchronized void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!droppedFiles.isEmpty()) {
                        File file = droppedFiles.get(0);
                        if (Files.probeContentType(file.toPath()).startsWith("image")) {
                            handleImageDrop(file);
                        } else {
                            JOptionPane.showMessageDialog(DashBoard.this, "Please drop an image file.");
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        dragDropArea.setPreferredSize(new Dimension(300, 300));
        dragDropArea.setEditable(false);
        imagePanel.add(dragDropArea, BorderLayout.CENTER);
        add(imagePanel, BorderLayout.CENTER);

        try {
            socket = new Socket("localhost", 12345);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            out.writeObject(userId);
            loadFriends();
        } catch (IOException e) {
            e.printStackTrace();
        }

        setVisible(true);
    }

    private void loadFriends() {
        try {
            out.writeObject("GET_FRIENDS");
            out.flush();
            Object response = in.readObject();
            if (response instanceof List<?>) {
                List<?> responseList = (List<?>) response;
                if (responseList.isEmpty() || responseList.get(0) instanceof String) {
                    List<String> friends = (List<String>) response;
                    System.out.println("Received friends list: " + friends); // Log the friends list
                    friendListModel.clear();
                    for (String friend : friends) {
                        friendListModel.addElement(friend);
                    }
                } else {
                    JOptionPane.showMessageDialog(this, "Error loading friends: List contents are not of type String");
                }
            } else if (response instanceof String) {
                System.out.println(response);

            } else {
                JOptionPane.showMessageDialog(this, "Error loading friends: Invalid response type");
                System.out.println("Invalid response type: " + response.getClass()); // Log the actual type
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading friends: " + e.getMessage());
        }
    }


    private void sendFriendRequest() {
        String friendUsername = JOptionPane.showInputDialog(this, "Enter friend's username:");
        if (friendUsername != null && !friendUsername.trim().isEmpty()) {
            try {
                out.writeObject("SEND_FRIEND_REQUEST");
                out.writeObject(friendUsername);
                Object response = in.readObject();
                if ("SUCCESS".equals(response)) {
                    JOptionPane.showMessageDialog(this, "Friend request sent.");
                } else if ("USER_NOT_FOUND".equals(response)) {
                    JOptionPane.showMessageDialog(this, "User not found.");
                } else {
                    JOptionPane.showMessageDialog(this, "Error sending friend request.");
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void showPendingRequests() {
        try {
            out.writeObject("GET_PENDING_REQUESTS");
            Object response = in.readObject();
            System.out.println(response.getClass());
            if (response instanceof List<?>) {
                List<String> requests = (List<String>) response;
                StringBuilder sb = new StringBuilder("Pending Friend Requests:\n");
                for (String request : requests) {
                    sb.append(request).append("\n");
                }
                JOptionPane.showMessageDialog(this, sb.toString());
                if (!requests.isEmpty()) {
                    handleFriendRequests(requests);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Error retrieving pending requests: Invalid response type");
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error retrieving pending requests: " + e.getMessage());
        }
    }

    private void handleFriendRequests(List<String> requests) {
        for (String request : requests) {
            String[] parts = request.split(" - ");
            int requestId = Integer.parseInt(parts[0]);
            String senderUsername = parts[1];
            int option = JOptionPane.showConfirmDialog(this, senderUsername + " sent you a friend request. Do you accept?");
            try {
                if (option == JOptionPane.YES_OPTION) {
                    out.writeObject("UPDATE_FRIEND_REQUEST");
                    out.writeObject(requestId);
                    out.writeObject("ACCEPTED");
                } else if (option == JOptionPane.NO_OPTION) {
                    out.writeObject("UPDATE_FRIEND_REQUEST");
                    out.writeObject(requestId);
                    out.writeObject("DECLINED");
                }
                Object response = in.readObject();
                if (!"SUCCESS".equals(response)) {
                    JOptionPane.showMessageDialog(this, "Error updating friend request.");
                }else{
                    loadFriends();
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleImageDrop(File file) {
        try {
            byte[] imageBytes = Files.readAllBytes(file.toPath());
            out.writeObject("SEND_IMAGE");
            out.writeObject(friendList.getSelectedValuesList());
            out.writeObject(imageBytes);
            Object response = in.readObject();
            if ("IMAGE_SENT".equals(response)) {
                JOptionPane.showMessageDialog(this, "Image sent successfully.");
            } else {
                JOptionPane.showMessageDialog(this, "Error sending image.");
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void logout() {
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        dispose();
        new LoginPage();
    }
}
