package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.ResultSet;
import java.sql.SQLException;
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
                    } else {
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
            boolean success = db.registerUser(username, password);
            if (success) {
                System.out.println("User registered successfully: username = " + username);
                out.writeObject("SUCCESS");
            } else {
                System.out.println("Registration failed, user already exists: username = " + username);
                out.writeObject("USER_EXISTS");
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

        private void handleUpdateFriendRequest() throws IOException, SQLException {
            try {
                Object requestIdObject = in.readObject();
                Object statusObject = in.readObject();

                if (requestIdObject instanceof Integer && statusObject instanceof String) {
                    int requestId = (Integer) requestIdObject;
                    String status = (String) statusObject;
                    db.updateFriendRequest(requestId, status);
                    if ("ACCEPTED".equals(status)) {
                        int senderId = db.getSenderIdByRequestId(requestId);
                        db.addFriend(userId, senderId);
                    }
                    out.writeObject("SUCCESS");
                } else {
                    out.writeObject("INVALID_REQUEST_DATA");
                }
            } catch (ClassNotFoundException e) {
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
