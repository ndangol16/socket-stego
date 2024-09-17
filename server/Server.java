package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Server {
    private static final int PORT = 12345;
    private static DBConnection db;
    private static int userId;


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
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());

                Object initialActionObject = in.readObject();
                if (initialActionObject instanceof String) {
                    String initialAction = (String) initialActionObject;
                    if ("LOGIN".equals(initialAction)) {
                        handleLogin();
                    } else if ("REGISTER".equals(initialAction)) {
                        handleRegister();
                    }
                    else {
                        out.writeObject("INVALID_ACTION");
                        return;
                    }
                }

                while (true) {
                    Object actionObject = in.readObject();
                    if (actionObject instanceof String) {
                        String action = (String) actionObject;
                        System.out.println("Received action: " + action);
                        switch (action) {
                            case "SEND_FRIEND_REQUEST":
                                handleSendFriendRequest();
                                break;
                            case "GET_PENDING_REQUESTS":
                                handleGetPendingRequests();
                                break;
                            case "UPDATE_FRIEND_REQUEST":
                                handleUpdateFriendRequest();
                                break;
                            case "GET_FRIENDS":
                                handleGetFriends();
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
            userId = db.authenticateUser(username, password);

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

            if (handleCheckUsername(username)){
                boolean success = db.registerUser(username, password);
                if (success) {
                    System.out.println("User registered successfully: username = " + username);
                    out.writeObject("SUCCESS");
                } else {
                    System.out.println("Registration failed, user already exists: username = " + username);
                    out.writeObject("USER_EXISTS");
                }
            }else{
                out.writeObject("USERNAME_NOT_UNIQUE");
            }

        }

        private void handleSendFriendRequest() throws IOException, SQLException {
            try {
                System.out.println("Starting handleSendFriendRequest method.");

                String friendUsername = (String) in.readObject();
                System.out.println("Received friend request for username: " + friendUsername);

                int receiverId = db.getUserIdByUsername(friendUsername);
                System.out.println("Retrieved receiverId: " + receiverId);
                System.out.println("Sender ID: "+userId);

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
                e.printStackTrace(); // Print stack trace for debugging
            } catch (SQLException e) {
                System.err.println("SQLException: " + e.getMessage());
                e.printStackTrace(); // Print stack trace for debugging
            } catch (IOException e) {
                System.err.println("IOException: " + e.getMessage());
                e.printStackTrace(); // Print stack trace for debugging
                throw e;  // Rethrow the IOException to ensure the caller handles it.
            }
        }

        private void handleGetPendingRequests() throws IOException, SQLException {
            System.out.println("handleGetPendingRequests userid: "+ userId);
            try{
                ResultSet rs = db.getPendingRequests(userId);
                List<String> requests = new ArrayList<>();
                while (rs.next()) {
                    int requestId = rs.getInt("sender_id");
                    String senderUsername = rs.getString("username");
                    requests.add(requestId + " - " + senderUsername);
                }
                out.writeObject(requests);
            }catch(SQLException e){
                e.printStackTrace();
            }

        }
        private boolean handleCheckUsername(String username) throws SQLException {
            boolean isUnique = !db.checkUsernameExists(username);
            if (isUnique) {
                return true;
            } else {
                return false;
            }
        }

        private void handleUpdateFriendRequest() throws IOException, SQLException {
            try {
                Object requestIdObject = in.readObject();
                Object statusObject = in.readObject();

                if (requestIdObject instanceof Integer && statusObject instanceof String) {
                    int requestId = (Integer) requestIdObject;
                    String status = (String) statusObject;
                    db.updateFriendRequest(requestId, status);
                    if ("ACCEPTED".equals(status)) {
//                        int senderId = db.getSenderIdByRequestId(requestId);
                        db.addFriend(userId, requestId);
                    }

                    out.writeObject("SUCCESS");
                } else {
                    out.writeObject("INVALID_REQUEST_DATA");
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            catch (SQLException e){
                e.printStackTrace();
            }
        }

        private void handleGetFriends() throws IOException, SQLException {
            List<String> friends = db.getFriends(userId);
            System.out.println("Sending friends list: " + friends); // Log the friends list
            out.writeObject(friends);
        }


        private void handleSendImage() throws IOException {
            try {
                Object recipientsObject = in.readObject();
                Object imageBytesObject = in.readObject();

                if (recipientsObject instanceof List<?> && imageBytesObject instanceof byte[]) {
                    @SuppressWarnings("unchecked")
                    List<String> recipients = (List<String>) recipientsObject;
                    byte[] imageBytes = (byte[]) imageBytesObject;
                    for (String recipient : recipients) {
                        System.out.println("Sending image to " + recipient);
                    }
                    out.writeObject("IMAGE_SENT");
                } else {
                    out.writeObject("INVALID_IMAGE_DATA");
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
}

class DBConnection {
    private Connection con;
    private String url = "jdbc:mysql://localhost:3306/project_two";
    private String username = "root";
    private String pass = "";

    public DBConnection() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection(url, username, pass);
            if (con != null) {
                System.out.println("DB CONNECTED!");
            }
        } catch (ClassNotFoundException | SQLException e) {
            System.err.println("DB connection failure!");
            e.printStackTrace();
        }
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
        System.out.println("SQL sender:"+ senderId );
        System.out.println("SQL reciever:"+ receiverId );
        String query = "INSERT INTO friend_requests (sender_id, receiver_id) VALUES (?, ?)";
        try (PreparedStatement pst = con.prepareStatement(query)) {
            pst.setInt(1, senderId);
            pst.setInt(2, receiverId);
            pst.executeUpdate();
        }
    }

    public ResultSet getPendingRequests(int userId) {
        String query = "SELECT fr.sender_id, u.username " +
                "FROM friend_requests fr " +
                "JOIN users u ON fr.sender_id = u.id " +
                "WHERE fr.receiver_id = ? AND fr.status is NULL";

        PreparedStatement pst = null;
        ResultSet rs = null;

        try {
            // Print the query and parameters for debugging
            System.out.println("Preparing to execute query: " + query);
            System.out.println("With parameter userId = " + userId);

            pst = con.prepareStatement(query);
            pst.setInt(1, userId);

            // Execute the query
            rs = pst.executeQuery();

            // Log success
            System.out.println("Query executed successfully.");

        } catch (SQLException e) {
            // Print stack trace and message for any SQL exceptions
            System.err.println("SQL Exception occurred while executing query:");
            e.printStackTrace();
            throw e; // Re-throw the exception after logging
        } finally {
            // Print logs for cleanup actions if needed
            System.out.println("Returning result set for userId = " + userId);
            return rs;
        }


    }

    public void updateFriendRequest(int requestId, String status) throws SQLException{
        String query = "UPDATE friend_requests SET status = ? WHERE sender_id = ?";
        System.out.println("updateFriendRequest id:" + requestId);
        System.out.println("updateFriendRequest status:" + status);

        try (PreparedStatement pst = con.prepareStatement(query)) {
            pst.setString(1, status);
            pst.setInt(2, requestId);
            pst.executeUpdate();
        }catch (SQLException e){
            e.printStackTrace();
        }
    }

    public int getSenderIdByRequestId(int requestId) throws SQLException {
        String query = "SELECT receiver_id FROM friend_requests WHERE sender_id = ?";
        System.out.println("getSenderIdByRequestId requestId: "+ requestId);
        try (PreparedStatement pst = con.prepareStatement(query)) {
            pst.setInt(1, requestId);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                return rs.getInt("sender_id");
            }
        }catch (SQLException e){
            e.printStackTrace();

        }
        return -1;
    }

    public void addFriend(int user1Id, int user2Id) throws SQLException {
//        String query = "INSERT INTO friends (user1_id, user2_id) VALUES (?, ?), (?, ?)";
        System.out.println("addFriend user1Id: "+user1Id);
        System.out.println("addFriend user2Id: "+user2Id);
        String query = "INSERT INTO friends (user1_id, user2_id) VALUES (?, ?)";
        try (PreparedStatement pst = con.prepareStatement(query)) {
            pst.setInt(1, user1Id);
            pst.setInt(2, user2Id);
//            pst.setInt(3, user2Id);
//            pst.setInt(4, user1Id);
            pst.executeUpdate();
        }
    }
    // Check if the username exists in the database
    public boolean checkUsernameExists(String username) throws SQLException {
        String query = "SELECT COUNT(*) FROM users WHERE username = ?";
        try (PreparedStatement pst = con.prepareStatement(query)) {
            pst.setString(1, username);
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
                return false;
            }
        }
    }

    public List<String> getFriends(int userId) throws SQLException {
        String query = "SELECT u.username FROM friends f JOIN users u ON (f.user1_id = u.id OR f.user2_id = u.id) WHERE (f.user1_id = ? OR f.user2_id = ?) AND u.id != ?";
        try (PreparedStatement pst = con.prepareStatement(query)) {
            pst.setInt(1, userId);
            pst.setInt(2, userId);
            pst.setInt(3, userId);
            ResultSet rs = pst.executeQuery();
            List<String> friends = new ArrayList<>();
            while (rs.next()) {
                friends.add(rs.getString("username"));
            }
            return friends;
        }
    }
    public int authenticateUser(String username, String password) throws SQLException {
        String query = "SELECT id FROM users WHERE username = ? AND password = ?";
        try (PreparedStatement pst = con.prepareStatement(query)) {
            pst.setString(1, username);
            pst.setString(2, password);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        }
        return -1;
    }

    public boolean registerUser(String username, String password) throws SQLException {

        String checkQuery = "SELECT id FROM users WHERE username = ?";
        try (PreparedStatement checkPst = con.prepareStatement(checkQuery)) {
            checkPst.setString(1, username);
            ResultSet rs = checkPst.executeQuery();
            if (rs.next()) {
                return false; // Username already exists
            }
        }

        String query = "INSERT INTO users (username, password) VALUES (?, ?)";
        try (PreparedStatement pst = con.prepareStatement(query)) {
            pst.setString(1, username);
            pst.setString(2, password);
            pst.executeUpdate();
            return true;
        }
    }

}



