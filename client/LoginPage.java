package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;

public class LoginPage extends JFrame implements ActionListener {
    private JLabel lbl_title, lbl_username, lbl_password;
    private JTextField txt_username;
    private JPasswordField txt_password;
    private JButton btn_login, btn_register;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public LoginPage() {
        setTitle("Login Page");
        setSize(400, 300);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(null);

        lbl_title = new JLabel("Login Form");
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

        btn_login = new JButton("Login");
        btn_login.setBounds(150, 180, 100, 30);
        btn_login.addActionListener(this);
        add(btn_login);

        btn_register = new JButton("Register");
        btn_register.setBounds(150, 210, 100, 30);
        btn_register.addActionListener(this);
        add(btn_register);

        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == btn_login) {
            String username = txt_username.getText();
            String password = String.valueOf(txt_password.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter all fields!", "Unsuccessful", JOptionPane.ERROR_MESSAGE);
            } else {
                try {
                    System.out.println("Connecting to server...");
                    socket = new Socket("localhost", 12345);
                    out = new ObjectOutputStream(socket.getOutputStream());
                    in = new ObjectInputStream(socket.getInputStream());

                    System.out.println("Sending login request");
                    out.writeObject("LOGIN");
                    out.writeObject(username);
                    out.writeObject(password);
                    System.out.println("Login request sent");

                    Object response = in.readObject();

                    if (response instanceof String) {
                        String responseStr = (String) response;
                        if (responseStr.startsWith("SUCCESS")) {
                            int userId = Integer.parseInt(responseStr.substring("SUCCESS".length()).trim());
                            JOptionPane.showMessageDialog(this, "Login Successful", "Successful", JOptionPane.INFORMATION_MESSAGE);
                            new DashBoard(userId, username);
                            dispose();
                        } else {
                            JOptionPane.showMessageDialog(this, "Invalid Username or Password", "Unsuccessful", JOptionPane.ERROR_MESSAGE);
                        }
                    } else if (response instanceof Integer) {
                        int userId = (Integer) response;
                        JOptionPane.showMessageDialog(this, "Login Successful", "Successful", JOptionPane.INFORMATION_MESSAGE);
                        new DashBoard(userId, username);
                        dispose();
                    } else {
                        JOptionPane.showMessageDialog(this, "Unexpected response from server", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (IOException | ClassNotFoundException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Error connecting to server", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        } else if (e.getSource() == btn_register) {
            new RegisterPage();
            dispose();
        }
    }
}
