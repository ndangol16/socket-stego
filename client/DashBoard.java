package client;

import steganography.StegoPanel;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

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
    private JPanel imagePanelArea, imagePanel;
    private JTextArea dragDropArea;
    private JLabel imagePreview;
    private JButton btn_sendImage;
    private JDialog requestDialog;
    private int userId;
    private StegoPanel spnl;
    private byte[] droppedImageBytes; // Store the dropped image bytes

    public DashBoard(int userId, String username) {
        this.userId = userId;

        setTitle("Dashboard");
        setSize(800, 700);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        lbl_name = new JLabel("Welcome " + username, SwingConstants.CENTER);
        add(lbl_name, BorderLayout.NORTH);

        spnl = new StegoPanel();

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

        imagePanelArea = new JPanel(new GridLayout(2, 1));

        imagePanel = new JPanel(new BorderLayout());
        imagePanel.setBorder(BorderFactory.createTitledBorder("Send Image"));
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
        spnl.setBorder(BorderFactory.createTitledBorder("Steganography"));
        dragDropArea.setPreferredSize(new Dimension(300, 150));
        dragDropArea.setEditable(false);
        imagePanel.add(dragDropArea, BorderLayout.CENTER);

        imagePreview = new JLabel("No image selected", SwingConstants.CENTER);
        imagePreview.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        imagePanel.add(imagePreview, BorderLayout.SOUTH);

        btn_sendImage = new JButton("Send Image");
        btn_sendImage.setEnabled(false); // Initially disabled
        btn_sendImage.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendImage();
            }
        });
        imagePanel.add(btn_sendImage, BorderLayout.NORTH);

        add(imagePanelArea, BorderLayout.CENTER);
        imagePanelArea.add(imagePanel);
        imagePanelArea.add(spnl);

        try {
            String envFilePath = "client/.env";
            Map<String, String> envVars = EnvLoader.loadEnv(envFilePath);
            socket = new Socket(envVars.get("URL"), Integer.parseInt(envVars.get("PORT")));
            out = new ObjectOutputStream(socket.getOutputStream());
//            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            out.writeObject("UserId: " + userId);
            out.flush();
            loadFriends();
            new ImageReceiver().start(); // Start the image receiver thread
        } catch (IOException e) {
            e.printStackTrace();
        }

        setVisible(true);
    }
    private void resetAfterSend() {
        imagePreview.setIcon(null);
        imagePreview.setText("No image selected");
        btn_sendImage.setEnabled(false);
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
                System.out.println(response);
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
            out.flush();
            Object response = in.readObject();
            System.out.println(response.getClass());
            if (response instanceof List<?>) {
                List<String> requests = (List<String>) response;
                StringBuilder sb = new StringBuilder("Pending Friend Requests:\n");
                for (String request : requests) {
                    String[] parts = request.split(" - ");
                    String senderUsername = parts[1];
                    sb.append(senderUsername).append("\n");
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
                } else {
                    loadFriends();
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleImageDrop(File file) {
        try {
            // Load the image and display it
            droppedImageBytes = Files.readAllBytes(file.toPath());
            ImageIcon imageIcon = new ImageIcon(droppedImageBytes);
            imagePreview.setIcon(imageIcon);
            imagePreview.setText(null);
            btn_sendImage.setEnabled(true); // Enable the send button
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading image.");
        }
    }

    private void sendImage() {
        try {
            if (droppedImageBytes != null) {
                // Send the image data
                out.writeObject("SEND_IMAGE");

                out.writeObject(friendList.getSelectedValuesList());

                out.writeObject(droppedImageBytes);
                out.flush(); // Ensure all data is sent

                // Wait for the response
                Object response = in.readObject();
                System.out.println("Received response: " + response.getClass().getName());

                if ("IMAGE_SENT".equals(response)) {
                    JOptionPane.showMessageDialog(this, "Image sent successfully.");
                    resetAfterSend();
                } else {
                    JOptionPane.showMessageDialog(this, "Error sending image.");
                }
                droppedImageBytes = null; // Reset after sending
                imagePreview.setIcon(null);
                imagePreview.setText("No image selected");
                btn_sendImage.setEnabled(false);
            } else {
                JOptionPane.showMessageDialog(this, "No image to send.");
            }
        } catch (IOException | ClassNotFoundException e) {

            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error sending image: " + e.getMessage());
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

class ImageReceiver extends Thread {
    @Override
    public void run() {
        try {
            String envFilePath = "client/.env";
            Map<String, String> envVars = EnvLoader.loadEnv(envFilePath);
            Socket socket = new Socket(envVars.get("URL"), Integer.parseInt(envVars.get("PORT")));
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            while (true) {
                Object response = in.readObject();
                if ("RECEIVE_IMAGE".equals(response)) {
                    byte[] imageBytes = (byte[]) in.readObject();
                    // Create a temporary file to store the received image
                    File tempFile = File.createTempFile("received_image", ".jpg");
                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        fos.write(imageBytes);
                    }

                    // Display the received image in a new JFrame
                    JFrame imageFrame = new JFrame("Received Image");
                    imageFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                    imageFrame.setSize(600, 600);
                    ImageIcon imageIcon = new ImageIcon(imageBytes);
                    JLabel imageLabel = new JLabel(imageIcon);
                    imageFrame.add(new JScrollPane(imageLabel));
                    imageFrame.setVisible(true);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
