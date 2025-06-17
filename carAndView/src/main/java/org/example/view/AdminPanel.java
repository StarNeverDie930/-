package org.example.view;

import org.example.entity.User;
import org.example.utils.UserService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * 管理员界面
 */
public class AdminPanel extends JFrame {

    private JPanel contentPane;
    private JTable userTable;
    private DefaultTableModel tableModel;

    /**
     * 创建管理员界面
     */
    public AdminPanel(User adminUser) {
        setTitle("管理员界面 - " + adminUser.getUsername());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 600, 400);
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        setContentPane(contentPane);
        contentPane.setLayout(new BorderLayout(0, 0));
        
        // 顶部面板
        JPanel topPanel = new JPanel();
        contentPane.add(topPanel, BorderLayout.NORTH);
        
        JLabel titleLabel = new JLabel("用户管理系统");
        titleLabel.setFont(new Font("宋体", Font.BOLD, 20));
        topPanel.add(titleLabel);
        
        // 中间面板（用户列表）
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BorderLayout(0, 0));
        contentPane.add(centerPanel, BorderLayout.CENTER);
        
        // 表格模型
        String[] columnNames = {"用户名", "密码(MD5加密)", "身份"};
        tableModel = new DefaultTableModel(null, columnNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // 不允许编辑单元格
            }
        };
        userTable = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(userTable);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        
        // 底部面板（按钮）
        JPanel bottomPanel = new JPanel();
        contentPane.add(bottomPanel, BorderLayout.SOUTH);
        
        JButton refreshButton = new JButton("刷新用户列表");
        bottomPanel.add(refreshButton);
        
        JButton logoutButton = new JButton("退出登录");
        bottomPanel.add(logoutButton);
        
        // 刷新按钮事件
        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadUserData();
            }
        });
        
        // 退出登录按钮事件
        logoutButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
                LoginFrame loginFrame = new LoginFrame();
                loginFrame.setVisible(true);
            }
        });
        
        // 加载用户数据
        loadUserData();
        
        // 居中显示
        setLocationRelativeTo(null);
    }
    
    /**
     * 加载用户数据
     */
    private void loadUserData() {
        // 清空表格数据
        tableModel.setRowCount(0);
        
        // 获取所有用户
        List<User> userList = UserService.getAllUsers();
        
        // 添加用户数据到表格
        for (User user : userList) {
            String userType = user.getRoot() == 1 ? "管理员" : "普通用户";
            Object[] rowData = {user.getUsername(), user.getPassword(), userType};
            tableModel.addRow(rowData);
        }
    }
} 