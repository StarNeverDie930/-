package org.example.utils;

import org.example.entity.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户服务工具类
 */
public class UserService {
    
    /**
     * 注册用户
     * @param username 用户名
     * @param password 密码
     * @return 注册成功返回true，否则返回false
     */
    public static boolean register(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return false;
        }
        
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        
        try {
            conn = DBUtil.getConnection();
            
            // 检查用户名是否已存在
            String checkSql = "SELECT * FROM users WHERE username = ?";
            pstmt = conn.prepareStatement(checkSql);
            pstmt.setString(1, username);
            rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return false; // 用户名已存在
            }
            
            // 关闭之前的PreparedStatement和ResultSet
            rs.close();
            pstmt.close();
            
            // 加密密码
            String encryptedPassword = MD5Util.encrypt(password);
            
            // 注册新用户（默认为普通用户，root=0）
            String insertSql = "INSERT INTO users (username, password, root) VALUES (?, ?, 0)";
            pstmt = conn.prepareStatement(insertSql);
            pstmt.setString(1, username);
            pstmt.setString(2, encryptedPassword);
            int result = pstmt.executeUpdate();
            
            return result > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            DBUtil.closeResources(conn, pstmt, rs);
        }
    }
    
    /**
     * 用户登录验证
     * @param username 用户名
     * @param password 密码
     * @return 登录成功返回用户对象，否则返回null
     */
    public static User login(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return null;
        }
        
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        
        try {
            conn = DBUtil.getConnection();
            
            // 查询用户
            String sql = "SELECT * FROM users WHERE username = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, username);
            rs = pstmt.executeQuery();
            
            if (rs.next()) {
                String storedPassword = rs.getString("password");
                int root = rs.getInt("root");
                
                // 验证密码（对输入的密码进行MD5加密后与数据库中的密码比较）
                String encryptedPassword = MD5Util.encrypt(password);
                if (encryptedPassword.equals(storedPassword)) {
                    User user = new User();
                    user.setUsername(username);
                    user.setPassword(storedPassword); // 存储加密后的密码
                    user.setRoot(root);
                    return user;
                }
            }
            
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        } finally {
            DBUtil.closeResources(conn, pstmt, rs);
        }
    }
    
    /**
     * 获取所有用户信息（管理员功能）
     * @return 用户列表
     */
    public static List<User> getAllUsers() {
        List<User> userList = new ArrayList<>();
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        
        try {
            conn = DBUtil.getConnection();
            
            // 查询所有用户
            String sql = "SELECT * FROM users";
            pstmt = conn.prepareStatement(sql);
            rs = pstmt.executeQuery();
            
            while (rs.next()) {
                User user = new User();
                user.setUsername(rs.getString("username"));
                user.setPassword(rs.getString("password")); // 已加密的密码
                user.setRoot(rs.getInt("root"));
                userList.add(user);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DBUtil.closeResources(conn, pstmt, rs);
        }
        
        return userList;
    }
} 