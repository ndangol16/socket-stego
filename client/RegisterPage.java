package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;
import java.util.Map;

public class RegisterPage extends JFrame implements ActionListener {
    private JLabel lbl_title, lbl_username, lbl_password;
    private JTextField txt_username;
    private JPasswordField txt_password;
    private JButton btn_register, btn_back;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public RegisterPage() {
        setTitle("Register Page");
        setSize(400, 300);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(null);

        lbl_title = new JLabel("Register Form");
        lbl_title.setFont(new Font("Verdana", Font.BOLD, 18));
        lbl_title.setHorizontalAlignment(SwingConstants.CENTER);
        lbl_title.setBounds(120, 20, 160, 30);
        add(lbl_title);

        JPanel inputPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        inputPanel.setBounds(50, 80, 300, 80);

        lbl_username = new JLabel("Username:");
        inputPanel.add(lbl_username);
        txt_username = new JTextField(20);
        inputPanel.add(txt_username);

        lbl_password = new JLabel("Password:");
        inputPanel.add(lbl_password);
        txt_password = new JPasswordField(20);
        inputPanel.add(txt_password);

        add(inputPanel);

        btn_register = new JButton("Register");
        btn_register.setBounds(150, 180, 100, 30);
        btn_back = new JButton("Back");
        btn_back.setBounds(150, 220, 100, 30);
        btn_back.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
                new LoginPage();
            }
        });
        btn_register.addActionListener(this);
        add(btn_register);
        add(btn_back);

        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == btn_register) {
            String username = txt_username.getText();
            String password = String.valueOf(txt_password.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter all fields!", "Unsuccessful", JOptionPane.ERROR_MESSAGE);
            } else {
                try {
                    String envFilePath = "client/.env";
                    Map<String, String> envVars = EnvLoader.loadEnv(envFilePath);
                    socket = new Socket(envVars.get("URL"), Integer.parseInt(envVars.get("PORT")));
                    out = new ObjectOutputStream(socket.getOutputStream());
                    in = new ObjectInputStream(socket.getInputStream());

                    out.writeObject("REGISTER");
                    out.writeObject(username);
                    out.writeObject(password);

                    String response = (String) in.readObject();
                    if ("SUCCESS".equals(response)) {
                        JOptionPane.showMessageDialog(this, "Registration Successful", "Successful", JOptionPane.INFORMATION_MESSAGE);
                        new LoginPage();
                        dispose();
                    } else if ("USERNAME_NOT_UNIQUE".equals(response)) {
                        JOptionPane.showMessageDialog(this, "Username is already taken", "Unsuccessful", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, "User already exists", "Unsuccessful", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (IOException | ClassNotFoundException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Error connecting to server", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }
}
