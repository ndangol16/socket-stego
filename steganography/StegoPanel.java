package steganography;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.List;

public class StegoPanel extends JPanel {
    private BufferedImage image;
    private JTextArea textArea;
    private JLabel imageLabel;
    private DBConnection dbConnection; // DBConnection instance

    public StegoPanel() {
        dbConnection = new DBConnection(); // Initialize DBConnection

        setLayout(new BorderLayout());

        textArea = new JTextArea("Drop text here...");
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setDropTarget(new TextDropTarget());

        imageLabel = new JLabel("Drop image here...", SwingConstants.CENTER);
        imageLabel.setDropTarget(new ImageDropTarget());

        JButton embedButton = new JButton("Embed");
        embedButton.addActionListener(new EmbedButtonListener());

        JButton extractButton = new JButton("Extract");
        extractButton.addActionListener(new ExtractButtonListener());

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(textArea), BorderLayout.CENTER);

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetStegoPanel();
            }
        });
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(embedButton);
        buttonPanel.add(extractButton);
        buttonPanel.add(cancel);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        add(panel, BorderLayout.EAST);
        add(imageLabel, BorderLayout.CENTER);
    }

    private class TextDropTarget extends DropTarget {
        public synchronized void drop(DropTargetDropEvent evt) {
            try {
                evt.acceptDrop(DnDConstants.ACTION_COPY);
                Transferable t = evt.getTransferable();
                if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    String text = (String) t.getTransferData(DataFlavor.stringFlavor);
                    textArea.setText(text);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private class ImageDropTarget extends DropTarget {
        public synchronized void drop(DropTargetDropEvent evt) {
            try {
                evt.acceptDrop(DnDConstants.ACTION_COPY);
                Transferable t = evt.getTransferable();
                if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    List<File> fileList = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                    File file = fileList.get(0);
                    image = ImageIO.read(file);
                    imageLabel.setIcon(new ImageIcon(image));
                    imageLabel.setText("");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private class EmbedButtonListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            if (image != null && !textArea.getText().isEmpty()) {
                String uniqueImageId = generateUniqueImageId(); // Generate unique image ID
                int[] prSequence = generatePseudoRandomSequence(image.getWidth() * image.getHeight() * 3);
                embedData(image, textArea.getText(), prSequence);
                dbConnection.saveSequence(uniqueImageId, prSequence); // Store sequence with unique ID

                try {
                    // Create a JFileChooser for saving the image
                    JFileChooser fileChooser = new JFileChooser();
                    fileChooser.setDialogTitle("Save Stego Image");
                    fileChooser.setSelectedFile(new File(uniqueImageId + "_stego_image.png")); // Default filename

                    int userSelection = fileChooser.showSaveDialog(null);
                    if (userSelection == JFileChooser.APPROVE_OPTION) {
                        File fileToSave = fileChooser.getSelectedFile();
                        // Save the stego image to the user-selected file
                        ImageIO.write(image, "png", fileToSave);

                        // Create a JTextArea with the Image ID that is selectable
                        JTextArea textArea = new JTextArea("Data embedded successfully!\nImage ID: " + uniqueImageId);
                        textArea.setEditable(false); // Not editable
                        textArea.setLineWrap(true);  // Enable line wrap
                        textArea.setWrapStyleWord(true);
                        textArea.setOpaque(false); // Makes it look like a JLabel
                        textArea.setFont(UIManager.getFont("Label.font")); // Use the default label font
                        textArea.setPreferredSize(new Dimension(300, 100)); // Increase width and height

                        // Add the "Copy to Clipboard" button
                        JButton copyButton = new JButton("Copy Image ID");
                        copyButton.addActionListener(new ActionListener() {
                            public void actionPerformed(ActionEvent evt) {
                                StringSelection selection = new StringSelection(uniqueImageId);
                                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                                clipboard.setContents(selection, selection);
                            }
                        });

                        // Create a panel to hold both the text area and the button
                        JPanel panel = new JPanel(new BorderLayout());
                        panel.add(new JScrollPane(textArea), BorderLayout.CENTER);
                        panel.add(copyButton, BorderLayout.SOUTH);

                        // Show the panel in a JOptionPane
                        JOptionPane.showMessageDialog(null,
                                panel,
                                "Embedding Complete",
                                JOptionPane.INFORMATION_MESSAGE);

                        // Reset the StegoPanel after successful embedding
                        resetStegoPanel(); // Custom method to reset the panel

                    }

                } catch (IOException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(null,
                            "Error saving the image.",
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(null,
                        "Please drop an image and text first.",
                        "Input Required",
                        JOptionPane.WARNING_MESSAGE);
            }
        }
    }







    private class ExtractButtonListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            if (image != null) {
                String imageId = JOptionPane.showInputDialog(null,
                        "Please enter the Image ID:",
                        "Enter Image ID",
                        JOptionPane.QUESTION_MESSAGE);

                if (imageId != null && !imageId.trim().isEmpty()) {
                    int[] prSequence = dbConnection.getSequence(imageId);

                    if (prSequence != null) {
                        String extractedText = extractData(image, prSequence);

                        if (extractedText != null && !extractedText.isEmpty()) {
                            JTextArea textArea = new JTextArea("Extracted Text:\n" + extractedText);
                            textArea.setEditable(false);
                            textArea.setLineWrap(true);
                            textArea.setWrapStyleWord(true);
                            textArea.setFont(UIManager.getFont("Label.font"));

                            // Set preferred size for the text area
                            textArea.setPreferredSize(new Dimension(300, 100)); // Increase width and height

                            JOptionPane.showMessageDialog(null,
                                    new JScrollPane(textArea),
                                    "Data Extraction Complete",
                                    JOptionPane.INFORMATION_MESSAGE);
                            resetStegoPanel();
                        } else {
                            JOptionPane.showMessageDialog(null,
                                    "No data found in the image for the provided Image ID.",
                                    "No Data Found",
                                    JOptionPane.WARNING_MESSAGE);
                            resetStegoPanel();
                        }
                    } else {
                        JOptionPane.showMessageDialog(null,
                                "No pseudo-random sequence found for the provided Image ID.",
                                "Error",
                                JOptionPane.ERROR_MESSAGE);
                        resetStegoPanel();
                    }
                } else {
                    JOptionPane.showMessageDialog(null,
                            "Image ID is required for data extraction.",
                            "Input Required",
                            JOptionPane.WARNING_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(null,
                        "Please drop an image first.",
                        "Input Required",
                        JOptionPane.WARNING_MESSAGE);
            }
        }
    }
    private void resetStegoPanel() {
        // Reset the image to null (or a default placeholder)
        image = null;
        imageLabel.setIcon(null); // Assuming imageLabel is the JLabel showing the image preview
        imageLabel.setText("Drop image here...");
        // Clear the text area
        textArea.setText("Drop text here...");

        // Optionally, reset other components if necessary
        // For example, you could disable certain buttons or fields
    }



    private void embedData(BufferedImage image, String data, int[] prSequence) {
        String dataBin = toBinary(data);
        int dataIdx = 0;
        int redundancy = 3;

        for (char bit : dataBin.toCharArray()) {
            for (int r = 0; r < redundancy; r++) {
                if (dataIdx >= prSequence.length) break;

                int idx = prSequence[dataIdx];
                int x = (idx / 3) % image.getWidth();
                int y = (idx / 3) / image.getWidth();
                int channel = idx % 3;

                int pixel = image.getRGB(x, y);
                int color = (pixel >> (8 * (2 - channel))) & 0xFF;
                color = (color & 0xFE) | (bit - '0');
                pixel = (pixel & ~(0xFF << (8 * (2 - channel)))) | (color << (8 * (2 - channel)));
                image.setRGB(x, y, pixel);

                dataIdx++;
            }
        }
    }

    private String extractData(BufferedImage image, int[] prSequence) {
        int dataLen = 100; // Assuming length of the hidden data
        int redundancy = 3;
        int[] bitCounts = new int[dataLen * 8];

        int dataIdx = 0;
        for (int i = 0; i < dataLen * 8 * redundancy; i++) {
            if (dataIdx >= prSequence.length) break;

            int idx = prSequence[dataIdx];
            int x = (idx / 3) % image.getWidth();
            int y = (idx / 3) / image.getWidth();
            int channel = idx % 3;

            int pixel = image.getRGB(x, y);
            int color = (pixel >> (8 * (2 - channel))) & 0xFF;
            int bit = color & 1;
            bitCounts[i / redundancy] += bit;

            dataIdx++;
        }

        StringBuilder dataBin = new StringBuilder();
        for (int count : bitCounts) {
            dataBin.append(count > redundancy / 2 ? '1' : '0');
        }

        return fromBinary(dataBin.toString());
    }

    private String toBinary(String text) {
        StringBuilder bin = new StringBuilder();
        for (char c : text.toCharArray()) {
            bin.append(String.format("%8s", Integer.toBinaryString(c)).replace(' ', '0'));
        }
        return bin.toString();
    }

    private String fromBinary(String bin) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < bin.length(); i += 8) {
            String byteString = bin.substring(i, i + 8);
            text.append((char) Integer.parseInt(byteString, 2));
        }
        return text.toString();
    }

    private int[] generatePseudoRandomSequence(int length) {
        Random random = new Random();
        int[] sequence = new int[length];
        for (int i = 0; i < length; i++) {
            sequence[i] = i;
        }
        for (int i = 0; i < length; i++) {
            int j = random.nextInt(length);
            int temp = sequence[i];
            sequence[i] = sequence[j];
            sequence[j] = temp;
        }
        return sequence;
    }

    private String generateUniqueImageId() {
        return UUID.randomUUID().toString();
    }
}
class DBConnection {
    private Connection con;

    public DBConnection() {
        try {String envFilePath = "server/.env";
            Map<String, String> envVars = EnvLoad.loadEnv(envFilePath);
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection(envVars.get("DB_URL"), envVars.get("DB_USER"), envVars.get("DB_PASSWORD"));
            if (con != null) {
                System.out.println("DB CONNECTED!");
            }
        } catch (ClassNotFoundException | SQLException e) {
            System.err.println("DB connection failure!");
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
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