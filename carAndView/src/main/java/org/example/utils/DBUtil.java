package org.example.utils;

import java.sql.*;

/**
 * 数据库连接工具类
 */
public class DBUtil {
    private static final String DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    private static final String URL = "jdbc:sqlserver://localhost:1433;databaseName=carDB;encrypt=false";
    private static final String USERNAME = "sa";
    private static final String PASSWORD = "425615234689Tsh";

    // 初始化数据库驱动
    static {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取数据库连接
     * @return 数据库连接对象
     * @throws SQLException SQL异常
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USERNAME, PASSWORD);
    }

    /**
     * 关闭数据库连接资源
     * @param conn 数据库连接
     * @param stmt 语句对象
     * @param rs 结果集
     */
    public static void closeResources(Connection conn, Statement stmt, ResultSet rs) {
        try {
            if (rs != null) {
                rs.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 初始化数据库，创建用户表和默认管理员账户
     */
    public static void initDatabase() {
        Connection conn = null;
        Statement stmt = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = getConnection();
            stmt = conn.createStatement();

            // 检查用户表是否存在，不存在则创建
            DatabaseMetaData meta = conn.getMetaData();
            rs = meta.getTables(null, null, "users", new String[]{"TABLE"});
            if (!rs.next()) {
                // 创建用户表
                String createTableSql = "CREATE TABLE users (" +
                        "username VARCHAR(50) PRIMARY KEY," +
                        "password VARCHAR(50) NOT NULL," +
                        "root INT NOT NULL DEFAULT 0" + // 0:普通用户 1:管理员
                        ")";
                stmt.executeUpdate(createTableSql);
                System.out.println("用户表创建成功");

                // 添加默认管理员账户
                String adminPassword = MD5Util.encrypt("admin");
                String insertAdminSql = "INSERT INTO users (username, password, root) VALUES (?, ?, ?)";
                pstmt = conn.prepareStatement(insertAdminSql);
                pstmt.setString(1, "admin");
                pstmt.setString(2, adminPassword);
                pstmt.setInt(3, 1); // 1表示管理员
                pstmt.executeUpdate();
                System.out.println("默认管理员账户创建成功");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeResources(conn, stmt, rs);
            if (pstmt != null) {
                try {
                    pstmt.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
} 