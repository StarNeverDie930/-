package org.example.entity;

/**
 * 用户实体类
 */
public class User {
    private String username;
    private String password;
    private int root; // 0:普通用户 1:管理员

    public User() {
    }

    public User(String username, String password, int root) {
        this.username = username;
        this.password = password;
        this.root = root;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getRoot() {
        return root;
    }

    public void setRoot(int root) {
        this.root = root;
    }
} 