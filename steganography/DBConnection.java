package steganography;

import java.io.IOException;
import java.sql.*;
import java.util.Arrays;
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

    public void saveSequence(String imageId, int[] sequence) {
        String query = "INSERT INTO PseudoRandomSequences (image_id, sequence) VALUES (?, ?)";
        try (PreparedStatement stmt = con.prepareStatement(query)) {
            stmt.setString(1, imageId);
            stmt.setString(2, sequenceToString(sequence));
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int[] getSequence(String imageId) {
        String query = "SELECT sequence FROM PseudoRandomSequences WHERE image_id = ?";
        try (PreparedStatement stmt = con.prepareStatement(query)) {
            stmt.setString(1, imageId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return stringToSequence(rs.getString("sequence"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new int[0]; // Return empty array if not found
    }

    private String sequenceToString(int[] sequence) {
        StringBuilder sb = new StringBuilder();
        for (int num : sequence) {
            sb.append(num).append(",");
        }
        return sb.toString();
    }

    private int[] stringToSequence(String sequenceStr) {
        String[] parts = sequenceStr.split(",");
        return Arrays.stream(parts)
                .filter(part -> !part.isEmpty())
                .mapToInt(Integer::parseInt)
                .toArray();
    }
}
