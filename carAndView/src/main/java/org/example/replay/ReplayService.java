package org.example.replay;

import org.example.entity.Car;
import org.example.entity.Block;
import org.example.utils.DBUtil;
import redis.clients.jedis.Jedis;

import java.sql.*;
import java.util.*;

public class ReplayService {
    public List<ReplaySession> getAllSessions() {
        List<ReplaySession> sessions = new ArrayList<>();

        try (Connection conn = DBUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM replay_sessions")) {

            while (rs.next()) {
                ReplaySession session = new ReplaySession(
                        rs.getInt("session_id"),
                        rs.getTimestamp("start_time"),
                        rs.getTimestamp("end_time"),
                        rs.getInt("map_size"),
                        rs.getInt("car_count"),
                        rs.getInt("block_count"),
                        rs.getLong("duration_ms")
                );
                sessions.add(session);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return sessions;
    }

    public ReplayData getReplayData(int sessionId, long startTime, long endTime) {
        ReplayData replayData = new ReplayData();
        replayData.setEvents(new ArrayList<>());

        // 获取初始状态
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT map_view, block_view FROM replay_initial_state WHERE session_id = ?")) {
            pstmt.setInt(1, sessionId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                byte[] mapViewBytes = rs.getBytes("map_view");
                byte[] blockViewBytes = rs.getBytes("block_view");

                // 获取地图大小
                int mapSize;
                try (PreparedStatement sizeStmt = conn.prepareStatement(
                        "SELECT map_size FROM replay_sessions WHERE session_id = ?")) {
                    sizeStmt.setInt(1, sessionId);
                    ResultSet sizeRs = sizeStmt.executeQuery();
                    mapSize = sizeRs.next() ? sizeRs.getInt("map_size") : 10;
                }

                // 初始化数组
                int[] mapView = new int[mapSize * mapSize];
                boolean[] blockView = new boolean[mapSize * mapSize];

                // 解析 mapView
                if (mapViewBytes != null && mapViewBytes.length >= mapSize * mapSize) {
                    for (int i = 0; i < mapView.length; i++) {
                        mapView[i] = mapViewBytes[i] & 0xFF; // 转换为无符号整数
                    }
                } else {
                    System.err.println("map_view 数据长度不足，session_id: " + sessionId + ", 期望: " + (mapSize * mapSize) + ", 实际: " + (mapViewBytes != null ? mapViewBytes.length : 0));
                    Arrays.fill(mapView, 0); // 默认全黑
                }

                // 解析 blockView
                if (blockViewBytes != null && blockViewBytes.length * 8 >= mapSize * mapSize) {
                    for (int i = 0; i < blockView.length; i++) {
                        blockView[i] = (blockViewBytes[i / 8] & (1 << (7 - (i % 8)))) != 0;
                    }
                } else {
                    System.err.println("block_view 数据长度不足，session_id: " + sessionId + ", 期望: " + (mapSize * mapSize) + ", 实际: " + (blockViewBytes != null ? blockViewBytes.length * 8 : 0));
                    Arrays.fill(blockView, false); // 默认无障碍
                }

                replayData.setInitialState(mapView, blockView);
            } else {
                System.err.println("未找到初始状态，session_id: " + sessionId);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("加载初始状态失败: " + e.getMessage());
        }

        // 获取事件数据（保持不变）
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT * FROM replay_data WHERE session_id = ? AND timestamp_ms BETWEEN ? AND ? ORDER BY timestamp_ms")) {
            pstmt.setInt(1, sessionId);
            pstmt.setLong(2, startTime);
            pstmt.setLong(3, endTime);
            ResultSet rs = pstmt.executeQuery();

            List<ReplayEvent> events = new ArrayList<>();
            while (rs.next()) {
                ReplayEvent event = new ReplayEvent(
                        rs.getLong("timestamp_ms"),
                        rs.getString("event_type"),
                        rs.getString("car_id"),
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getString("direction"),
                        rs.getInt("block_id")
                );
                events.add(event);
            }
            replayData.setEvents(events);
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("加载事件数据失败: " + e.getMessage());
        }

        return replayData;
    }

    public void deleteSession(int sessionId) throws SQLException {
        Connection conn = null;
        PreparedStatement pstmt1 = null;
        PreparedStatement pstmt2 = null;

        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            // 删除 replay_data 表中的记录
            String sql1 = "DELETE FROM replay_data WHERE session_id = ?";
            pstmt1 = conn.prepareStatement(sql1);
            pstmt1.setInt(1, sessionId);
            pstmt1.executeUpdate();

            // 删除 replay_initial_state 表中的记录
            String sql2 = "DELETE FROM replay_initial_state WHERE session_id = ?";
            pstmt2 = conn.prepareStatement(sql2);
            pstmt2.setInt(1, sessionId);
            pstmt2.executeUpdate();

            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            throw e;
        } finally {
            DBUtil.closeResources(conn, pstmt1, null);
            if (pstmt2 != null) {
                try {
                    pstmt2.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static class ReplaySession {
        private int sessionId;
        private Timestamp startTime;
        private Timestamp endTime;
        private int mapSize;
        private int carCount;
        private int blockCount;
        private long durationMs;

        // 构造函数、getters和setters
        public ReplaySession(int sessionId, Timestamp startTime, Timestamp endTime, int mapSize, int carCount, int blockCount, long durationMs) {
            this.sessionId = sessionId;
            this.startTime = startTime;
            this.endTime = endTime;
            this.mapSize = mapSize;
            this.carCount = carCount;
            this.blockCount = blockCount;
            this.durationMs = durationMs;
        }

        public int getSessionId() {
            return sessionId;
        }

        public void setSessionId(int sessionId) {
            this.sessionId = sessionId;
        }

        public Timestamp getStartTime() {
            return startTime;
        }

        public void setStartTime(Timestamp startTime) {
            this.startTime = startTime;
        }
        public Timestamp getEndTime() {
            return endTime;
        }
        public void setEndTime(Timestamp endTime) {
            this.endTime = endTime;
        }
        public int getMapSize() {
            return mapSize;
        }
        public void setMapSize(int mapSize) {
            this.mapSize = mapSize;
        }
        public int getCarCount() {
            return carCount;
        }
        public void setCarCount(int carCount) {
            this.carCount = carCount;
        }
        public int getBlockCount() {
            return blockCount;
        }
        public void setBlockCount(int blockCount) {
            this.blockCount = blockCount;
        }
        public long getDurationMs() {
            return durationMs;
        }
        public void setDurationMs(long durationMs) {
            this.durationMs = durationMs;
        }
    }

    public static class ReplayData {
        private int[] initialMapView;
        private boolean[] initialBlockView;
        private List<ReplayEvent> events = new ArrayList<>(); // 默认初始化

        public void setInitialState(int[] mapView, boolean[] blockView) {
            this.initialMapView = mapView;
            this.initialBlockView = blockView;
        }

        public int[] getInitialMapView() {
            return initialMapView;
        }

        public boolean[] getInitialBlockView() {
            return initialBlockView;
        }

        public void setEvents(List<ReplayEvent> events) {
            this.events = events != null ? events : new ArrayList<>();
        }

        public List<ReplayEvent> getEvents() {
            return events;
        }
    }



    public static class ReplayEvent {
        private long timestampMs;
        private String eventType;
        private String carId;
        private int x;
        private int y;
        private String direction;
        private int blockId;

        // 构造函数、getters和setters
        public ReplayEvent( long timestampMs, String eventType, String carId, int x, int y, String direction, int blockId) {
            this.timestampMs = timestampMs;
            this.eventType = eventType;
            this.carId = carId;
            this.x = x;
            this.y = y;
            this.direction = direction;
            this.blockId = blockId;
        }
        public long getTimestampMs() {
            return timestampMs;
        }
        public void setTimestampMs(long timestampMs) {
            this.timestampMs = timestampMs;
        }
        public String getEventType() {
            return eventType;
        }
        public void setEventType(String eventType) {
            this.eventType = eventType;
        }
        public String getCarId() {
            return carId;
        }
        public void setCarId(String carId) {
            this.carId = carId;
        }
        public int getX() {
            return x;
        }
        public void setX(int x) {
            this.x = x;
        }
        public int getY() {
            return y;
        }
        public void setY(int y) {
            this.y = y;
        }
        public String getDirection() {
            return direction;
        }
        public void setDirection(String direction) {
            this.direction = direction;
        }
        public int getBlockId() {
            return blockId;
        }
        public void setBlockId(int blockId) {
            this.blockId = blockId;
        }
    }
}