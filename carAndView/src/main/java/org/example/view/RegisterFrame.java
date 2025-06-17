package org.example.view;

import org.example.utils.UserService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * 注册界面
 */
public class RegisterFrame extends JFrame {

    private JPanel contentPane;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JPasswordField confirmPasswordField;
    private JButton registerButton;
    private JButton backButton;

    /**
     * 创建注册界面
     */
    public RegisterFrame() {
        setTitle("小车大冒险 - 注册");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 400, 350);
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        setContentPane(contentPane);
        contentPane.setLayout(null);
        
        JLabel titleLabel = new JLabel("用户注册");
        titleLabel.setFont(new Font("宋体", Font.BOLD, 24));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        titleLabel.setBounds(10, 10, 364, 40);
        contentPane.add(titleLabel);
        
        JLabel usernameLabel = new JLabel("用户名：");
        usernameLabel.setBounds(50, 80, 80, 25);
        contentPane.add(usernameLabel);
        
        usernameField = new JTextField();
        usernameField.setBounds(140, 80, 200, 25);
        contentPane.add(usernameField);
        usernameField.setColumns(10);
        
        JLabel passwordLabel = new JLabel("密码：");
        passwordLabel.setBounds(50, 120, 80, 25);
        contentPane.add(passwordLabel);
        
        passwordField = new JPasswordField();
        passwordField.setBounds(140, 120, 200, 25);
        contentPane.add(passwordField);
        
        JLabel confirmPasswordLabel = new JLabel("确认密码：");
        confirmPasswordLabel.setBounds(50, 160, 80, 25);
        contentPane.add(confirmPasswordLabel);
        
        confirmPasswordField = new JPasswordField();
        confirmPasswordField.setBounds(140, 160, 200, 25);
        contentPane.add(confirmPasswordField);
        
        registerButton = new JButton("注册");
        registerButton.setBounds(80, 220, 100, 30);
        contentPane.add(registerButton);
        
        backButton = new JButton("返回登录");
        backButton.setBounds(220, 220, 100, 30);
        contentPane.add(backButton);
        
        // 注册按钮事件
        registerButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String username = usernameField.getText();
                String password = new String(passwordField.getPassword());
                String confirmPassword = new String(confirmPasswordField.getPassword());
                
                // 验证输入
                if (username.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(RegisterFrame.this, "用户名不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                if (password.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(RegisterFrame.this, "密码不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                if (!password.equals(confirmPassword)) {
                    JOptionPane.showMessageDialog(RegisterFrame.this, "两次输入的密码不一致！", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                // 注册用户
                boolean result = UserService.register(username, password);
                if (result) {
                    JOptionPane.showMessageDialog(RegisterFrame.this, "注册成功，请登录！", "提示", JOptionPane.INFORMATION_MESSAGE);
                    // 关闭注册窗口，打开登录窗口
                    dispose();
                    LoginFrame loginFrame = new LoginFrame();
                    loginFrame.setVisible(true);
                } else {
                    JOptionPane.showMessageDialog(RegisterFrame.this, "注册失败，用户名可能已存在！", "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        
        // 返回登录按钮事件
        backButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // 关闭注册窗口，打开登录窗口
                dispose();
                LoginFrame loginFrame = new LoginFrame();
                loginFrame.setVisible(true);
            }
        });
        
        // 居中显示
        setLocationRelativeTo(null);
    }
} 