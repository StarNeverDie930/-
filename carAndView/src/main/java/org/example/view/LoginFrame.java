package org.example.view;

import org.example.entity.User;
import org.example.utils.UserService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * 登录界面
 */
public class LoginFrame extends JFrame {

    private JPanel contentPane;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton registerButton;

    /**
     * 创建登录界面
     */
    public LoginFrame() {
        setTitle("小车大冒险 - 登录");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 400, 300);
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        setContentPane(contentPane);
        contentPane.setLayout(null);
        
        JLabel titleLabel = new JLabel("小车大冒险");
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
        
        loginButton = new JButton("登录");
        loginButton.setBounds(80, 180, 100, 30);
        contentPane.add(loginButton);
        
        registerButton = new JButton("注册");
        registerButton.setBounds(220, 180, 100, 30);
        contentPane.add(registerButton);
        
        // 登录按钮事件
        loginButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String username = usernameField.getText();
                String password = new String(passwordField.getPassword());
                
                User user = UserService.login(username, password);
                if (user != null) {
                    JOptionPane.showMessageDialog(LoginFrame.this, "登录成功！", "提示", JOptionPane.INFORMATION_MESSAGE);
                    // 关闭登录窗口
                    dispose();
                    
                    // 根据用户类型打开不同的界面
                    if (user.getRoot() == 1) {
                        // 管理员界面
                        AdminPanel adminPanel = new AdminPanel(user);
                        adminPanel.setVisible(true);
                    } else {
                        // 普通用户界面（游戏主界面）
                        CarAdventureMainJFrame mainFrame = new CarAdventureMainJFrame();
                        mainFrame.setVisible(true);
                    }
                } else {
                    JOptionPane.showMessageDialog(LoginFrame.this, "用户名或密码错误！", "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        
        // 注册按钮事件
        registerButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // 关闭登录窗口，打开注册窗口
                dispose();
                RegisterFrame registerFrame = new RegisterFrame();
                registerFrame.setVisible(true);
            }
        });
        
        // 居中显示
        setLocationRelativeTo(null);
    }
} 