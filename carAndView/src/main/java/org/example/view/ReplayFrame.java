package org.example.view;

import org.example.replay.ReplayService;
import org.example.replay.ReplayService.ReplayData;
import org.example.replay.ReplayService.ReplayEvent;
import org.example.replay.ReplayService.ReplaySession;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class ReplayFrame extends JFrame {
    private ReplayService replayService = new ReplayService();
    private Timer replayTimer;
    private long currentReplayTime = 0;
    private long replayDuration = 0;
    private ReplayData currentReplayData;
    private int currentSessionId;
    private ReplaySession currentSession;
    private List<String> carIds = new ArrayList<>(); // 小车ID列表，按添加顺序
    private List<Color> carColors = new ArrayList<>(); // 小车颜色列表，按添加顺序
    private static final Color[] COLOR_PALETTE = {
            new Color(0, 255, 0),   // 绿色
            new Color(0, 0, 255),   // 蓝色
            new Color(255, 255, 0), // 黄色
            new Color(255, 0, 255), // 紫色
            new Color(0, 255, 255), // 青色
            new Color(255, 165, 0)  // 橙色
    };

    private JPanel replayPanel;
    private JButton playButton;
    private JButton pauseButton;
    private JButton stopButton;
    private JButton prevStepButton;
    private JButton nextStepButton;
    private JSlider progressSlider;
    private JComboBox<ReplaySession> sessionComboBox;
    private JComboBox<String> carFilterComboBox;
    private JSpinner startTimeSpinner;
    private JSpinner endTimeSpinner;
    private JLabel timestampLabel;

    public ReplayFrame() {
        setTitle("探索过程回放");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        initUI();
        loadSessions();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        // 会话选择和过滤面板
        JPanel sessionPanel = new JPanel();
        sessionComboBox = new JComboBox<>();
        carFilterComboBox = new JComboBox<>();
        JButton loadButton = new JButton("加载");
        JButton deleteButton = new JButton("删除");
        loadButton.addActionListener(e -> loadReplaySession());
        deleteButton.addActionListener(e -> deleteReplaySession());
        carFilterComboBox.addActionListener(e -> replayPanel.repaint());
        sessionPanel.add(new JLabel("选择会话:"));
        sessionPanel.add(sessionComboBox);
        sessionPanel.add(loadButton);
        sessionPanel.add(deleteButton);
        sessionPanel.add(new JLabel("小车过滤:"));
        sessionPanel.add(carFilterComboBox);

        // 时间范围选择和时间戳显示
        JPanel timePanel = new JPanel();
        startTimeSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100000, 1000));
        endTimeSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100000, 1000));
        timestampLabel = new JLabel("当前时间: 0 ms");
        timePanel.add(new JLabel("开始时间(ms):"));
        timePanel.add(startTimeSpinner);
        timePanel.add(new JLabel("结束时间(ms):"));
        timePanel.add(endTimeSpinner);
        timePanel.add(timestampLabel);

        // 控制面板
        JPanel controlPanel = new JPanel();
        playButton = new JButton("播放");
        pauseButton = new JButton("暂停");
        stopButton = new JButton("停止");
        prevStepButton = new JButton("上一步");
        nextStepButton = new JButton("下一步");
        progressSlider = new JSlider();

        playButton.addActionListener(e -> startReplay());
        pauseButton.addActionListener(e -> pauseReplay());
        stopButton.addActionListener(e -> stopReplay());
        prevStepButton.addActionListener(e -> prevStep());
        nextStepButton.addActionListener(e -> nextStep());

        controlPanel.add(playButton);
        controlPanel.add(pauseButton);
        controlPanel.add(stopButton);
        controlPanel.add(prevStepButton);
        controlPanel.add(nextStepButton);
        controlPanel.add(progressSlider);

        // 进度条拖动功能
        progressSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (progressSlider.getValueIsAdjusting()) {
                    pauseReplay();
                    currentReplayTime = progressSlider.getValue();
                    timestampLabel.setText("当前时间: " + currentReplayTime + " ms");
                    replayPanel.repaint();
                }
            }
        });

        // 回放面板
        replayPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                renderReplayFrame(g);
            }
        };
        replayPanel.setPreferredSize(new Dimension(600, 400));

        mainPanel.add(sessionPanel, BorderLayout.NORTH);
        mainPanel.add(timePanel, BorderLayout.SOUTH);
        mainPanel.add(controlPanel, BorderLayout.CENTER);
        mainPanel.add(replayPanel, BorderLayout.WEST);

        add(mainPanel);
    }

    private void loadSessions() {
        List<ReplaySession> sessions = replayService.getAllSessions();
        sessionComboBox.removeAllItems();
        for (ReplaySession session : sessions) {
            sessionComboBox.addItem(session);
        }
    }

    private void loadReplaySession() {
        ReplaySession session = (ReplaySession) sessionComboBox.getSelectedItem();
        if (session == null) {
            JOptionPane.showMessageDialog(this, "未选择会话", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        currentSession = session;
        currentSessionId = session.getSessionId();
        replayDuration = session.getDurationMs();

        startTimeSpinner.setValue(0L);
        endTimeSpinner.setValue(replayDuration);

        currentReplayData = replayService.getReplayData(currentSessionId, 0, replayDuration);
        if (currentReplayData == null || currentReplayData.getInitialMapView() == null || currentReplayData.getInitialBlockView() == null) {
            JOptionPane.showMessageDialog(this, "回放数据加载失败", "错误", JOptionPane.ERROR_MESSAGE);
            currentReplayData = null;
            return;
        }

        // 初始化小车ID和颜色
        carIds.clear();
        carColors.clear();
        List<ReplayEvent> events = currentReplayData.getEvents();
        int colorIndex = 0;
        if (events != null) {
            for (ReplayEvent event : events) {
                if ("CAR_MOVE".equals(event.getEventType())) {
                    String carId = event.getCarId();
                    if (!carIds.contains(carId)) {
                        carIds.add(carId);
                        carColors.add(COLOR_PALETTE[colorIndex % COLOR_PALETTE.length]);
                        colorIndex++;
                    }
                }
            }
        }

        // 更新小车过滤下拉框
        carFilterComboBox.removeAllItems();
        carFilterComboBox.addItem("显示全部");
        for (String carId : carIds) {
            carFilterComboBox.addItem(carId);
        }
        carFilterComboBox.setSelectedIndex(0);

        progressSlider.setMinimum(0);
        progressSlider.setMaximum((int) replayDuration);
        progressSlider.setValue(0);
        currentReplayTime = 0;
        timestampLabel.setText("当前时间: 0 ms");

        replayPanel.repaint();
    }

    private void deleteReplaySession() {
        ReplaySession session = (ReplaySession) sessionComboBox.getSelectedItem();
        if (session == null) {
            JOptionPane.showMessageDialog(this, "未选择会话", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this, "确定删除会话 " + session.getSessionId() + " 吗？", "确认删除", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                replayService.deleteSession(session.getSessionId());
                loadSessions();
                currentReplayData = null;
                currentSession = null;
                replayPanel.repaint();
                JOptionPane.showMessageDialog(this, "会话已删除", "提示", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "删除失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void startReplay() {
        if (currentReplayData == null) {
            JOptionPane.showMessageDialog(this, "请先加载回放会话", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (replayTimer == null) {
            long startTime = (Long) startTimeSpinner.getValue();
            long endTime = (Long) endTimeSpinner.getValue();
            currentReplayTime = Math.max(startTime, currentReplayTime);

            replayTimer = new Timer(40, e -> {
                currentReplayTime += 40;
                if (currentReplayTime > endTime) {
                    stopReplay();
                    return;
                }

                progressSlider.setValue((int) currentReplayTime);
                timestampLabel.setText("当前时间: " + currentReplayTime + " ms");
                replayPanel.repaint();
            });
        }
        replayTimer.start();
    }

    private void pauseReplay() {
        if (replayTimer != null) {
            replayTimer.stop();
        }
    }

    private void stopReplay() {
        if (replayTimer != null) {
            replayTimer.stop();
            replayTimer = null;
        }
        currentReplayTime = 0;
        progressSlider.setValue(0);
        timestampLabel.setText("当前时间: 0 ms");
        replayPanel.repaint();
    }

    private void prevStep() {
        if (currentReplayData == null) {
            return;
        }
        pauseReplay();
        currentReplayTime = Math.max(0, currentReplayTime - 1000);
        progressSlider.setValue((int) currentReplayTime);
        timestampLabel.setText("当前时间: " + currentReplayTime + " ms");
        replayPanel.repaint();
    }

    private void nextStep() {
        if (currentReplayData == null) {
            return;
        }
        pauseReplay();
        long endTime = (Long) endTimeSpinner.getValue();
        currentReplayTime = Math.min(endTime, currentReplayTime + 1000);
        progressSlider.setValue((int) currentReplayTime);
        timestampLabel.setText("当前时间: " + currentReplayTime + " ms");
        replayPanel.repaint();
    }

    private void renderReplayFrame(Graphics g) {
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, replayPanel.getWidth(), replayPanel.getHeight());

        if (currentReplayData == null || currentSession == null) {
            return;
        }

        drawInitialMap(g);

        List<ReplayEvent> events = currentReplayData.getEvents();
        String selectedCarId = (String) carFilterComboBox.getSelectedItem();
        boolean showAllCars = "显示全部".equals(selectedCarId);

        // 绘制路径和障碍物
        if (events != null) {
            for (ReplayEvent event : events) {
                if (event.getTimestampMs() <= currentReplayTime) {
                    if ("CAR_MOVE".equals(event.getEventType()) && (showAllCars || event.getCarId().equals(selectedCarId))) {
                        drawCarPath(event, g);
                    } else if ("BLOCK_ADD".equals(event.getEventType())) {
                        renderBlock(event, g);
                    }
                }
            }
        }

        // 点亮邻域区域
        if (events != null) {
            for (ReplayEvent event : events) {
                if ("CAR_MOVE".equals(event.getEventType()) && event.getTimestampMs() <= currentReplayTime
                        && (showAllCars || event.getCarId().equals(selectedCarId))) {
                    markExploredArea(event, g);
                }
            }
        }

        // 绘制当前小车位置
        if (events != null) {
            for (ReplayEvent event : events) {
                if ("CAR_MOVE".equals(event.getEventType()) && event.getTimestampMs() <= currentReplayTime
                        && (showAllCars || event.getCarId().equals(selectedCarId))) {
                    drawCarMovement(event, g);
                }
            }
        }
    }

    private void drawInitialMap(Graphics g) {
        if (currentReplayData == null || currentSession == null) {
            return;
        }

        int mapSize = currentSession.getMapSize();
        int totalSize = mapSize + 2;
        int panelWidth = replayPanel.getWidth();
        int panelHeight = replayPanel.getHeight();
        int cellSize = Math.min(panelWidth / totalSize, panelHeight / totalSize);
        if (cellSize <= 0) cellSize = 1;
        int offsetX = (panelWidth - totalSize * cellSize) / 2;
        int offsetY = (panelHeight - totalSize * cellSize) / 2;

        // 绘制边框
        g.setColor(Color.DARK_GRAY);
        for (int i = 0; i < totalSize; i++) {
            for (int j = 0; j < totalSize; j++) {
                if (i == 0 || i == totalSize - 1 || j == 0 || j == totalSize - 1) {
                    g.fillRect(offsetX + i * cellSize, offsetY + j * cellSize, cellSize, cellSize);
                }
            }
        }

        // 绘制地图
        int[] mapView = currentReplayData.getInitialMapView();
        boolean[] blockView = currentReplayData.getInitialBlockView();
        if (mapView == null || blockView == null) {
            System.err.println("地图数据无效");
            return;
        }

        for (int y = 0; y < mapSize; y++) {
            for (int x = 0; x < mapSize; x++) {
                int index = y * mapSize + x;
                if (index >= mapView.length || index >= blockView.length) {
                    continue;
                }
                if (blockView[index]) {
                    g.setColor(Color.RED);
                } else {
                    g.setColor(Color.BLACK);
                }
                g.fillRect(offsetX + (x + 1) * cellSize, offsetY + (y + 1) * cellSize, cellSize - 1, cellSize - 1);
            }
        }
    }

    private void drawCarPath(ReplayEvent event, Graphics g) {
        if (currentSession == null) return;

        int mapSize = currentSession.getMapSize();
        int totalSize = mapSize + 2;
        int panelWidth = replayPanel.getWidth();
        int panelHeight = replayPanel.getHeight();
        int cellSize = Math.min(panelWidth / totalSize, panelHeight / totalSize);
        if (cellSize <= 0) cellSize = 1;
        int offsetX = (panelWidth - totalSize * cellSize) / 2;
        int offsetY = (panelHeight - totalSize * cellSize) / 2;

        int x = Math.max(0, Math.min(event.getX(), mapSize - 1)) + 1;
        int y = Math.max(0, Math.min(event.getY(), mapSize - 1)) + 1;

        int carIndex = carIds.indexOf(event.getCarId());
        if (carIndex >= 0 && carIndex < carColors.size()) {
            g.setColor(carColors.get(carIndex));
            g.fillRect(offsetX + x * cellSize, offsetY + y * cellSize, cellSize - 1, cellSize - 1);
        }
    }

    private void drawCarMovement(ReplayEvent event, Graphics g) {
        if (currentSession == null) return;

        int mapSize = currentSession.getMapSize();
        int totalSize = mapSize + 2;
        int panelWidth = replayPanel.getWidth();
        int panelHeight = replayPanel.getHeight();
        int cellSize = Math.min(panelWidth / totalSize, panelHeight / totalSize);
        if (cellSize <= 0) cellSize = 1;
        int offsetX = (panelWidth - totalSize * cellSize) / 2;
        int offsetY = (panelHeight - totalSize * cellSize) / 2;

        int x = Math.max(0, Math.min(event.getX(), mapSize - 1)) + 1;
        int y = Math.max(0, Math.min(event.getY(), mapSize - 1)) + 1;

        int carIndex = carIds.indexOf(event.getCarId());
        if (carIndex >= 0 && carIndex < carColors.size()) {
            g.setColor(carColors.get(carIndex));
            g.fillRect(offsetX + x * cellSize, offsetY + y * cellSize, cellSize - 1, cellSize - 1);
        }
    }

    private void renderBlock(ReplayEvent event, Graphics g) {
        if (currentSession == null) return;

        int mapSize = currentSession.getMapSize();
        int totalSize = mapSize + 2;
        int panelWidth = replayPanel.getWidth();
        int panelHeight = replayPanel.getHeight();
        int cellSize = Math.min(panelWidth / totalSize, panelHeight / totalSize);
        if (cellSize <= 0) cellSize = 1;
        int offsetX = (panelWidth - totalSize * cellSize) / 2;
        int offsetY = (panelHeight - totalSize * cellSize) / 2;

        int x = event.getX() + 1;
        int y = event.getY() + 1;
        g.setColor(Color.RED);
        g.fillRect(offsetX + x * cellSize, offsetY + y * cellSize, cellSize - 1, cellSize - 1);
    }

    private void markExploredArea(ReplayEvent event, Graphics g) {
        if (currentSession == null) return;

        int mapSize = currentSession.getMapSize();
        int totalSize = mapSize + 2;
        int panelWidth = replayPanel.getWidth();
        int panelHeight = replayPanel.getHeight();
        int cellSize = Math.min(panelWidth / totalSize, panelHeight / totalSize);
        if (cellSize <= 0) cellSize = 1;
        int offsetX = (panelWidth - totalSize * cellSize) / 2;
        int offsetY = (panelHeight - totalSize * cellSize) / 2;

        boolean[] blockView = currentReplayData.getInitialBlockView();
        int[][] directions = { {0, -1}, {0, 1}, {-1, 0}, {1, 0} };
        for (int[] dir : directions) {
            int nx = event.getX() + dir[0];
            int ny = event.getY() + dir[1];
            if (nx >= 0 && nx < mapSize && ny >= 0 && ny < mapSize) {
                int index = ny * mapSize + nx;
                if (index >= 0 && index < blockView.length && !blockView[index]) {
                    g.setColor(Color.WHITE);
                    g.fillRect(offsetX + (nx + 1) * cellSize, offsetY + (ny + 1) * cellSize, cellSize - 1, cellSize - 1);
                }
            }
        }
    }
}