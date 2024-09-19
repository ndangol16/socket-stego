package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Server {
    private static final int PORT = 12345;
    private static DBConnection db;
    private static Map<Integer, ObjectOutputStream> activeClients = new HashMap<>(); // Map to hold connected clients' output streams
//    private static int userId;

    public static void main(String[] args) {
        db = new DBConnection();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);
            while (true) {
                new ClientHandler(serverSocket.accept()).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            int userId = -1;

            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());

                Object initialActionObject = in.readObject();
                if (initialActionObject instanceof String) {
                    String initialAction = (String) initialActionObject;
                    if ("LOGIN".equals(initialAction)) {
                        handleLogin();
                        return;
                    } else if ("REGISTER".equals(initialAction)) {
                        handleRegister();
                        return;
                    }
//                    else {
//                        out.writeObject("INVALID_ACTION");
//                        return;
//                    }
                }


                if (initialActionObject instanceof String) {

                    String value = (String) initialActionObject;

                    String[] data = value.split(" ");

                    if (data.length == 2){
                        if (data[0].equals("UserId:")) {
                            userId = Integer.parseInt(data[1]);
                        }
                    }
                }

                if (userId == -1) {
                    throw new RuntimeException("Invalid user");
                }

                // Store the output stream for the logged-in user
                activeClients.put(userId, out);

                while (true) {
                    Object actionObject = in.readObject();
                    if (actionObject instanceof String) {
                        String action = (String) actionObject;
                        System.out.println("Received action: " + action);
                        switch (action) {
                            case "SEND_FRIEND_REQUEST":
                                handleSendFriendRequest(userId);
                                break;
                            case "GET_PENDING_REQUESTS":
                                handleGetPendingRequests(userId);
                                break;
                            case "UPDATE_FRIEND_REQUEST":
                                handleUpdateFriendRequest(userId);
                                break;
                            case "GET_FRIENDS":
                                handleGetFriends(userId);
                                break;
                            case "SEND_IMAGE":
                                handleSendImage();
                                break;
                            default:
                                System.out.println("Unknown action: " + action);
                                out.writeObject("UNKNOWN_ACTION");
                        }
                    } else {
                        out.writeObject("INVALID_ACTION_TYPE");
                    }
                }
            } catch (EOFException e) {
                System.err.println("EOFException: Client disconnected prematurely.");
            } catch (IOException e) {
                System.err.println("IOException: Error in communication.");
            } catch (ClassNotFoundException e) {
                System.err.println("ClassNotFoundException: Error in reading object.");
            } catch (SQLException e) {
                System.err.println("SQLException: Database error.");
            } finally {
                try {
                    activeClients.remove(userId);  // Remove the client from active clients list on disconnect
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void handleLogin() throws IOException, SQLException, ClassNotFoundException {
            System.out.println("Handling login request");
            String username = (String) in.readObject();
            String password = (String) in.readObject();
            System.out.println("Received login credentials: username = " + username);
            int userId = db.authenticateUser(username, password);

            if (userId != -1) {
                System.out.println("User authenticated: userId = " + userId);
                out.writeObject(userId);
            } else {
                System.out.println("Authentication failed for user: " + username);
                out.writeObject("USER_NOT_FOUND");
            }
        }

        private void handleRegister() throws IOException, SQLException, ClassNotFoundException {
            System.out.println("Handling register request");
            String username = (String) in.readObject();
            String password = (String) in.readObject();
            System.out.println("Received registration credentials: username = " + username);

            if (handleCheckUsername(username)) {
                boolean success = db.registerUser(username, password);
                if (success) {
                    System.out.println("User registered successfully: username = " + username);
                    out.writeObject("SUCCESS");
                } else {
                    System.out.println("Registration failed, user already exists: username = " + username);
                    out.writeObject("USER_EXISTS");
                }
            } else {
                out.writeObject("USERNAME_NOT_UNIQUE");
            }
        }

        private void handleSendFriendRequest(int userId) throws IOException, SQLException {
            try {
                System.out.println("Starting handleSendFriendRequest method.");
                String friendUsername = (String) in.readObject();
                System.out.println("Received friend request for username: " + friendUsername);
                int receiverId = db.getUserIdByUsername(friendUsername);
                System.out.println("Retrieved receiverId: " + receiverId);
                System.out.println("Sender ID: " + userId);

                if (receiverId != -1) {
                    db.sendFriendRequest(userId, receiverId);
                    out.writeObject("SUCCESS");
                    System.out.println("Friend request sent successfully from userId: " + userId + " to receiverId: " + receiverId);
                } else {
                    out.writeObject("USER_NOT_FOUND");
                    System.out.println("User not found for username: " + friendUsername);
                }
            } catch (ClassNotFoundException e) {
                System.err.println("ClassNotFoundException: " + e.getMessage());
                e.printStackTrace();
            } catch (SQLException e) {
                System.err.println("SQLException: " + e.getMessage());
                e.printStackTrace();
            } catch (IOException e) {
                System.err.println("IOException: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
        }

        private void handleGetPendingRequests(int userId) throws IOException, SQLException {
            System.out.println("handleGetPendingRequests userid: " + userId);
            try {
                ResultSet rs = db.getPendingRequests(userId);
                List<String> requests = new ArrayList<>();
                while (rs.next()) {
                    int requestId = rs.getInt("sender_id");
                    String senderUsername = rs.getString("username");
                    requests.add(requestId + " - " + senderUsername);
                }
                System.out.println(requests.getClass() );
                System.out.println(requests);
                out.writeObject(requests);
                out.flush();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private boolean handleCheckUsername(String username) throws SQLException {
            boolean isUnique = !db.checkUsernameExists(username);
            return isUnique;
        }

        private void handleUpdateFriendRequest(int userId) throws IOException, SQLException {
            try {
                Object requestIdObject = in.readObject();
                Object statusObject = in.readObject();

                if (requestIdObject instanceof Integer && statusObject instanceof String) {
                    int requestId = (Integer) requestIdObject;
                    String status = (String) statusObject;
                    db.updateFriendRequest(requestId, status);
                    if ("ACCEPTED".equals(status)) {
                        db.addFriend(userId, requestId);
                    }
                    out.writeObject("SUCCESS");
                } else {
                    out.writeObject("INVALID_REQUEST_DATA");
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private void handleGetFriends(int userId) throws IOException, SQLException {
            List<String> friends = db.getFriends(userId);
            System.out.println("Sending friends list: " + friends); // Log the friends list
            out.writeObject(friends);
            out.flush();
        }

        private void handleSendImage() throws IOException {
            try {
                // Read the list of recipients (usernames) and the image bytes
                Object recipientsObject = in.readObject();
                Object imageBytesObject = in.readObject();

                if (recipientsObject instanceof List<?> && imageBytesObject instanceof byte[]) {
                    @SuppressWarnings("unchecked")
                    List<String> recipients = (List<String>) recipientsObject;
                    byte[] imageBytes = (byte[]) imageBytesObject;

                    for (String recipient : recipients) {
                        int recipientId = db.getUserIdByUsername(recipient);
                        if (recipientId != -1 && activeClients.containsKey(recipientId)) {
                            // Send the image to the recipient
                            ObjectOutputStream recipientOut = activeClients.get(recipientId);
                            recipientOut.writeObject("RECEIVE_IMAGE");
                            recipientOut.writeObject(imageBytes);
                            System.out.println("Image sent to " + recipient);
                        } else {
//                            ObjectOutputStream recipientOut = activeClients.get(recipientId);
//                            recipientOut.writeObject("RECEIVE_IMAGE_ERROR");
                            System.out.println("Recipient " + recipient + " is offline or not found.");
                        }
                    }
                    out.writeObject("IMAGE_SENT");
                } else {
                    out.writeObject("INVALID_IMAGE_DATA");
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}

class DBConnection {
    private Connection con;

    public DBConnection() {
        try {
            String envFilePath = "server/.env";
            Map<String, String> envVars = EnvLoad.loadEnv(envFilePath);
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection(envVars.get("DB_URL"), envVars.get("DB_USER"), envVars.get("DB_PASSWORD"));
            if (con != null) {
                System.out.println("DB CONNECTED!");
            }
        } catch (ClassNotFoundException | SQLException | IOException e) {
            e.printStackTrace();
        }
    }
    public int authenticateUser(String username, String password) {
        int userId = -1;  // Default value if authentication fails
        String sql = "SELECT id FROM users WHERE username = ? AND password = ?";

        try (
             PreparedStatement stmt = con.prepareStatement(sql)) {

            // Set parameters for the SQL query
            stmt.setString(1, username);
            stmt.setString(2, password);

            // Execute the query
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    userId = rs.getInt("id");  // Get the user ID if the user exists
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();  // Handle SQL exception
        }

        return userId;  // Return the user ID if found, otherwise -1 for failure
    }

    public int getUserIdByUsername(String username) throws SQLException {
        String query = "SELECT id FROM users WHERE username = ?";
        try (PreparedStatement pst = con.prepareStatement(query)) {
            pst.setString(1, username);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        }
        return -1;
    }

    public void sendFriendRequest(int senderId, int receiverId) throws SQLException {
        String query = "INSERT INTO friend_requests (sender_id, receiver_id) VALUES (?, ?)";
        try (PreparedStatement pst = con.prepareStatement(query)) {
            pst.setInt(1, senderId);
            pst.setInt(2, receiverId);
            pst.executeUpdate();
        }
    }

    public ResultSet getPendingRequests(int userId) throws SQLException {
        String query = "SELECT fr.sender_id, u.username " +
                "FROM friend_requests fr " +
                "JOIN users u ON fr.sender_id = u.id " +
                "WHERE fr.receiver_id = ? AND fr.status is NULL";
        PreparedStatement pst = con.prepareStatement(query);
        pst.setInt(1, userId);
        return pst.executeQuery();
    }

    public boolean checkUsernameExists(String username) throws SQLException {
        String query = "SELECT * FROM users WHERE username = ?";
        try (PreparedStatement pst = con.prepareStatement(query)) {
            pst.setString(1, username);
            ResultSet rs = pst.executeQuery();
            return rs.next();
        }
    }

    public boolean registerUser(String username, String password) throws SQLException {
        if (!checkUsernameExists(username)) {
            String query = "INSERT INTO users (username, password) VALUES (?, ?)";
            try (PreparedStatement pst = con.prepareStatement(query)) {
                pst.setString(1, username);
                pst.setString(2, password);
                pst.executeUpdate();
                return true;
            }
        }
        return false;
    }

    public void updateFriendRequest(int requestId, String status) throws SQLException {
        String query = "UPDATE friend_requests SET status = ? WHERE sender_id = ?";
        try (PreparedStatement pst = con.prepareStatement(query)) {
            pst.setString(1, status);
            pst.setInt(2, requestId);
            pst.executeUpdate();
        }
    }

    public void addFriend(int userId1, int userId2) throws SQLException {
        String query = "INSERT INTO friends (user1_id, user2_id) VALUES (?, ?)";
        try (PreparedStatement pst = con.prepareStatement(query)) {
            pst.setInt(1, userId1);
            pst.setInt(2, userId2);
            pst.executeUpdate();
        }
    }

    public List<String> getFriends(int userId) throws SQLException {
        List<String> friends = new ArrayList<>();
        String query = "SELECT u.username " +
                "FROM friends f " +
                "JOIN users u ON (f.user1_id = ? AND f.user2_id = u.id) OR (f.user2_id = ? AND f.user1_id = u.id)";
        try (PreparedStatement pst = con.prepareStatement(query)) {
            pst.setInt(1, userId);
            pst.setInt(2, userId);
            ResultSet rs = pst.executeQuery();
            while (rs.next()) {
                friends.add(rs.getString("username"));
            }
        }
        return friends;
    }
}


class EnvLoad {
    public static Map<String, String> loadEnv(String filePath) throws IOException {
        Map<String, String> envVars = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue; // Skip empty lines and comments
                }
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    envVars.put(key, value);
                }
            }
        }

        return envVars;
    }
}

