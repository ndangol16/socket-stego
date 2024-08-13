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
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.UUID;

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

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(embedButton);
        buttonPanel.add(extractButton);
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
                    // Save the stego image with the unique image ID
                    ImageIO.write(image, "png", new File(uniqueImageId + "_stego_image.png"));

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
                        } else {
                            JOptionPane.showMessageDialog(null,
                                    "No data found in the image for the provided Image ID.",
                                    "No Data Found",
                                    JOptionPane.WARNING_MESSAGE);
                        }
                    } else {
                        JOptionPane.showMessageDialog(null,
                                "No pseudo-random sequence found for the provided Image ID.",
                                "Error",
                                JOptionPane.ERROR_MESSAGE);
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

