package org.example.replay;

import org.example.entity.AllData;
import org.example.entity.Car;
import org.example.entity.Block;
import org.example.utils.ConnectionUtil;
import org.example.utils.DBUtil;
import redis.clients.jedis.Jedis;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

public class ReplayRecorder {
    private int sessionId;
    private long startTime;
    private Jedis jedis;
    private Connection dbConnection;

    public ReplayRecorder() {
        this.startTime = System.currentTimeMillis();
        this.jedis = ConnectionUtil.getJedis();
        try {
            this.dbConnection = DBUtil.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // 创建新的回放会话
        createNewSession();
    }

    private void createNewSession() {
        try {
            // 获取地图大小
            int mapSize = Integer.parseInt(jedis.get("map_size"));
            int carCount = jedis.scard("car_ids").intValue();
            int blockCount = jedis.scard("block_ids").intValue();

            // 插入新会话
            String sql = "INSERT INTO replay_sessions (start_time, end_time, map_size, car_count, block_count, duration_ms) " + "VALUES (?, NULL, ?, ?, ?, 0)";
            PreparedStatement pstmt = dbConnection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
            pstmt.setTimestamp(1, new Timestamp(startTime));
            pstmt.setInt(2, mapSize);
            pstmt.setInt(3, carCount);
            pstmt.setInt(4, blockCount);
            pstmt.executeUpdate();

            // 获取生成的sessionId
            java.sql.ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                sessionId = rs.getInt(1);
            }

            // 保存初始状态
            saveInitialState(mapSize);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void saveInitialState(int mapSize) throws SQLException {
        try (Jedis jedis = ConnectionUtil.getJedis()) {
            // 获取 mapView
            int[] mapView = AllData.mapView;
            if (mapView == null || mapView.length != mapSize * mapSize) {
                System.err.println("mapView 数据无效，初始化为空地图，mapSize: " + mapSize);
                mapView = new int[mapSize * mapSize]; // 默认全黑
            }
            byte[] mapViewBytes = new byte[mapView.length];
            for (int i = 0; i < mapView.length; i++) {
                mapViewBytes[i] = (byte) mapView[i];
            }

            // 获取 blockView
            byte[] blockViewBytes = new byte[(mapSize * mapSize + 7) / 8];
            for (int i = 0; i < mapSize * mapSize; i++) {
                if (jedis.getbit("blockView", i)) {
                    blockViewBytes[i / 8] |= (1 << (7 - (i % 8)));
                }
            }

            String sql = "INSERT INTO replay_initial_state (session_id, map_view, block_view) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
                pstmt.setInt(1, sessionId);
                pstmt.setBytes(2, mapViewBytes);
                pstmt.setBytes(3, blockViewBytes);
                pstmt.executeUpdate();
            }
        }
    }

    public void recordCarEvent(Car car, String eventType) {
        long timestamp = System.currentTimeMillis() - startTime;

        try {
            String sql = "INSERT INTO replay_data (session_id, timestamp_ms, event_type, car_id, x, y, direction) " + "VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement pstmt = dbConnection.prepareStatement(sql);
            pstmt.setInt(1, sessionId);
            pstmt.setLong(2, timestamp);
            pstmt.setString(3, eventType);
            pstmt.setString(4, car.getCarID());
            pstmt.setInt(5, (int) car.getX());
            pstmt.setInt(6, (int) car.getY());
            pstmt.setString(7, String.valueOf(car.getStatus()));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void recordBlockEvent(Block block, String eventType) {
        long timestamp = System.currentTimeMillis() - startTime;

        try {
            String sql = "INSERT INTO replay_data (session_id, timestamp_ms, event_type, block_id, x, y) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            PreparedStatement pstmt = dbConnection.prepareStatement(sql);
            pstmt.setInt(1, sessionId);
            pstmt.setLong(2, timestamp);
            pstmt.setString(3, eventType);
            pstmt.setInt(4, block.hashCode()); // 简单标识符
            pstmt.setInt(5, block.getX());
            pstmt.setInt(6, block.getY());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void endSession() {
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        try {
            String sql = "UPDATE replay_sessions SET end_time = ?, duration_ms = ? WHERE session_id = ?";
            PreparedStatement pstmt = dbConnection.prepareStatement(sql);
            pstmt.setTimestamp(1, new Timestamp(endTime));
            pstmt.setLong(2, duration);
            pstmt.setInt(3, sessionId);
            pstmt.executeUpdate();

            dbConnection.close();
            jedis.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}