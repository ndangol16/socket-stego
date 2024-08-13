package server;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DBConnection {
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
