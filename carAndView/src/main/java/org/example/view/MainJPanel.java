package org.example.view;

import com.rabbitmq.client.Channel;
import org.example.entity.AllData;
import org.example.entity.Block;
import org.example.entity.Car;
import org.example.factory.CarFactory;
import org.example.replay.ReplayRecorder;
import org.example.utils.ConnectionUtil;
import org.example.utils.EventUtils;
import redis.clients.jedis.Jedis;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;

import static org.example.utils.ConnectionUtil.jedisPool;
import java.util.Queue;
import java.awt.Point;

/**
 * 主面板类
 * 负责显示游戏界面和处理用户交互
 * 继承JPanel并实现ActionListener接口
 */
public class MainJPanel extends JPanel implements ActionListener {

    private Timer timer; // 定时器，40毫秒触发一次
    private final JTextArea logsTextArea; // 日志显示区域
    private String logs = ""; // 日志内容
    public LinkedList<Car> carList; // 汽车对象列表
    public LinkedList<Block> blockList; // 障碍物对象列表
    private HashMap<String, LinkedList<Character>> tasksMapList; // 任务映射表，key为汽车ID，value为任务序列

    // 当前模式
    private final JLabel statusLabel;
    private EventUtils.Mode currentMode = EventUtils.Mode.NORMAL; // 当前模式

    private Jedis jedis; // Redis连接(黑板)
    private boolean isFinish; // 游戏是否结束标志
    public int blockNum; // 当前障碍物数量
    private int carNum; // 当前汽车数量
    protected int mapSize; // 地图大小
    private int maxBlockNum; // 最大障碍物数量
    private int maxCarNum; // 最大汽车数量
    public static final String MAP_SIZE_KEY = "map_size"; // Redis中存储地图大小的key
    private boolean gameStarted = false;
    public static ReplayRecorder replayRecorder; // 回放记录器

    // 获取游戏状态
    public boolean isGameStarted() {
        return gameStarted;
    }

    //
    public void setCurrentMode(EventUtils.Mode mode) {
        this.currentMode = mode;
        repaint();
    }

    public MainJPanel() {
        mapSize = 10;
        maxBlockNum = (int) (mapSize * mapSize * 0.37);
        maxCarNum = (int) (mapSize * 0.2);
        // 添加程序退出时的连接池关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (jedisPool != null && !jedisPool.isClosed()) {
                jedisPool.close();
            }
            if (timer != null && timer.isRunning()) {
                timer.stop();
            }
            System.out.println("程序退出,已关闭Redis连接池");
        }));

        // 确保连接池初始化
        if (jedisPool == null) {
            System.out.println("Redis连接池未初始化");
            new ConnectionUtil();
        }

        try (Jedis jedis = jedisPool.getResource()) {
            if (!jedis.exists("map_size")) {
                jedis.set("map_size", String.valueOf(mapSize));
            }
        }

        // 初始化组件
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        // 创建地图面板
        JPanel mapPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                int panelWidth = getWidth();
                int panelHeight = getHeight();
                int cellSize = Math.min(panelWidth / (mapSize + 2), panelHeight / (mapSize + 2));

                int displaySize = mapSize + 2;
                for (int i = 0; i < displaySize * displaySize; i++) {
                    int x = i % displaySize;
                    int y = i / displaySize;

                    if (x == 0 || x == displaySize - 1 || y == 0 || y == displaySize - 1) {
                        g.setColor(Color.DARK_GRAY);
                        g.fillRect(x * cellSize, y * cellSize, cellSize - 1, cellSize - 1);
                    } else {
                        int mapIndex = (y - 1) * mapSize + (x - 1);
                        if (mapIndex >= 0 && mapIndex < AllData.mapView.length) {
                            switch (AllData.mapView[mapIndex]) {
                                case -1:
                                    g.setColor(Color.WHITE);
                                    break;
                                case 0:
                                    g.setColor(Color.BLACK);
                                    break;
                                case 1:
                                    g.setColor(Color.YELLOW);
                                    break;
                                case 2:
                                    g.setColor(Color.GREEN);
                                    break;
                                case 3:
                                    g.setColor(Color.BLUE);
                                    break;
                                case 4:
                                    g.setColor(Color.MAGENTA);
                                    break;
                            }
                            g.fillRect(x * cellSize, y * cellSize, cellSize - 1, cellSize - 1);
                        }
                    }
                }

                // 画障碍物 - 修正贴图位置
                for (Block block : blockList) {
                    ImageIcon icon = block.getBlockIcon();
                    Image img = icon.getImage();
                    g.drawImage(img, block.getX() * cellSize, block.getY() * cellSize, this);
                }

                // 画车 - 修正贴图位置和缩放
                for (Car car : carList) {
                    ImageIcon icon = car.getCarIcon();
                    Image img = icon.getImage();
                    g.drawImage(img, ((int) car.getX() + 1) * cellSize, ((int) car.getY() + 1) * cellSize, this);
                }

                // 绘制模式指示器
                drawModeIndicator(g);
            }

            // 绘制模式指示器
            private void drawModeIndicator(Graphics g) {
                int panelWidth = getWidth();
                int indicatorSize = 20;
                int x = panelWidth - indicatorSize - 10;
                int y = 10;

                switch (currentMode) {
                    case CAR:
                        g.setColor(Color.BLUE);
                        g.fillOval(x, y, indicatorSize, indicatorSize);
                        g.setColor(Color.WHITE);
                        g.drawString("C", x + 7, y + 15);
                        break;
                    case BLOCK:
                        g.setColor(Color.RED);
                        g.fillRect(x, y, indicatorSize, indicatorSize);
                        g.setColor(Color.WHITE);
                        g.drawString("B", x + 7, y + 15);
                        break;
                    case NORMAL:
                        g.setColor(Color.GREEN);
                        g.fillRect(x, y, indicatorSize, indicatorSize);
                        g.setColor(Color.WHITE);
                        g.drawString("N", x + 7, y + 15);
                        break;
                }
            }
        };
        mapPanel.setPreferredSize(new Dimension(600, 600));
        mapPanel.setMinimumSize(new Dimension(100, 100));
        mapPanel.revalidate();
        mapPanel.repaint();
        add(mapPanel, BorderLayout.CENTER);
        mapPanel.addMouseListener(new MapMouseListener());

        // 创建控制面板
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setPreferredSize(new Dimension(200, 600));
        add(controlPanel, BorderLayout.EAST);

        // 创建模式切换面板
        JPanel modePanel = new JPanel(new GridLayout(3, 1, 5, 5));
        modePanel.setBorder(BorderFactory.createTitledBorder("模式选择"));

        // 普通模式按钮
        JButton normalModeButton = new JButton("普通模式");
        normalModeButton.addActionListener(new EventUtils.ModeToggleButtonListener(EventUtils.Mode.NORMAL));
        // 小车模式按钮
        JButton carModeButton = new JButton("小车模式");
        carModeButton.addActionListener(new EventUtils.ModeToggleButtonListener(EventUtils.Mode.CAR));
        // 障碍物模式按钮
        JButton blockModeButton = new JButton("障碍物模式");
        blockModeButton.addActionListener(new EventUtils.ModeToggleButtonListener(EventUtils.Mode.BLOCK));

        modePanel.add(normalModeButton);
        modePanel.add(carModeButton);
        modePanel.add(blockModeButton);

        controlPanel.add(modePanel);

        // 创建状态面板
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("当前模式: " + EventUtils.getCurrentMode());
        statusPanel.add(statusLabel);
        add(statusPanel, BorderLayout.SOUTH);

        // 地图大小选择下拉框
        String[] mapSizes = { "10x10", "20x20", "50x50" };
        JComboBox<String> mapSizeComboBox = new JComboBox<>(mapSizes);
        mapSizeComboBox.setSelectedIndex(0); // 默认选中10*10

        // 设置默认地图大小
        mapSize = 10;
        mapSizeComboBox.setMaximumSize(new Dimension(150, 25));
        mapSizeComboBox.addActionListener(e -> {
            String selected = (String) mapSizeComboBox.getSelectedItem();
            if (selected != null) {
                switch (selected) {
                    case "10x10":
                        mapSize = 10;
                        break;
                    case "20x20":
                        mapSize = 20;
                        break;
                    case "50x50":
                        mapSize = 50;
                        break;
                }
                maxBlockNum = (int) (mapSize * mapSize * 0.37);
                maxCarNum = (int) (mapSize * 0.2);
            }
            logs = "\n> 地图大小设置为: " + selected + ", 最大障碍物数量: " + maxBlockNum + ", 最大小车数量: " + maxCarNum;

            changeLogsTextArea(logs);
            // 重新初始化地图数据并强制重绘
            AllData.mapView = new int[mapSize * mapSize];
            AllData.setCarIDsLength(maxCarNum);
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.flushDB();
                jedis.set("map_size", String.valueOf(mapSize)); // 更新地图大小
                jedis.set("max_block_num", String.valueOf(maxBlockNum)); // 更新最大障碍物数量
                jedis.set("max_car_num", String.valueOf(maxCarNum));
            }
            repaint();
        });
        controlPanel.add(mapSizeComboBox);
        init(); // 在设置完默认值后初始化界面

        // 结束按钮
        JButton endButton = new JButton("结    束");
        endButton.addActionListener(e -> {
            gameOver();
        });
        controlPanel.add(endButton);

        //算法切换按钮
        String[] algorithm = { "A*","D Lite" };
        JComboBox<String> algorithmBox = new JComboBox<>(algorithm);
        algorithmBox.setSelectedIndex(0);

        algorithmBox.setMaximumSize(new Dimension(150, 25));
        algorithmBox.addActionListener(e -> {
            String selected = (String) algorithmBox.getSelectedItem();
            if (selected != null) {
                switch (selected) {
                    case "A*":
                        EventUtils.setAlgorithm("A*");
                        break;
                    case "D_Lite":
                        EventUtils.setAlgorithm("D_Lite");
                        break;
                }
            }
        });
        controlPanel.add(algorithmBox);

        // 调整地图面板大小，留出按钮空间
        setBounds(0, 0, 600, 600);

        // 日志文字区
        logsTextArea = new JTextArea();
        logsTextArea.setLineWrap(true);
        logsTextArea.setFont(new Font("楷体", Font.PLAIN, 13));
        logsTextArea.setText("> 初始化...");
        logsTextArea.setEditable(false);
        logsTextArea.setPreferredSize(new Dimension(180, 200));
        controlPanel.add(new JScrollPane(logsTextArea));

        // 清空按钮
        JButton clearTextAreaButton = new JButton("清    空");
        clearTextAreaButton.addActionListener(e -> {
            logs = EventUtils.clearLogsTextAreaActionPerformed(e);
            changeLogsTextArea(logs);
        });
        controlPanel.add(clearTextAreaButton);

        // 开始游戏按钮
        JButton startButton = new JButton("开始游戏");
        startButton.addActionListener(e -> {
            if (!carList.isEmpty()) {
                for (Car car : carList) {
                    car.setWork(true);
                }

                replayRecorder = new ReplayRecorder();

                // 确保 carIDs 数组已正确初始化
                if (AllData.carIDs == null || AllData.carIDs.length == 0) {
                    logs += "\n> 警告: carIDs数组未正确初始化，重新初始化...";
                    changeLogsTextArea(logs);
                    AllData.setCarIDsLength(carList.size());
                }

                // 从 carList 获取所有小车ID并存入 carIDs 数组
                int i = 0;
                for (Car car : carList) {
                    if (i < AllData.carIDs.length) {
                        AllData.carIDs[i++] = car.getCarID();
                    }
                }

                // 启动所有小车
                for (Car car : carList) {
                    try {
                        CarFactory.startCar(car.getCarID());
                        logs += "\n> 启动小车: " + car.getCarID();
                        changeLogsTextArea(logs);
                    } catch (Exception ex) {
                        logs += "\n> 启动小车失败: " + ex.getMessage();
                        changeLogsTextArea(logs);
                    }
                }

                gameStarted = true;
                logs += "\n> 游戏已开始，小车开始运行";
                logs += "\n> 游戏开始后禁止添加小车或障碍物";
                changeLogsTextArea(logs);
            } else {
                logs += "\n> 请先添加小车";
                changeLogsTextArea(logs);
            }
        });
        controlPanel.add(startButton);

        // 一键生成障碍物按钮
        JButton generateBlocksButton = new JButton("生成障碍物");
        generateBlocksButton.addActionListener(e -> {
            Random random = new Random();

            do {
                int x = random.nextInt(mapSize);
                int y = random.nextInt(mapSize);

                Block block = EventUtils.createBlockAtPosition(x, y);
                blockNum++;
                repaint();
                // 在生成循环中添加连通性检查
                if (!checkConnectivity()) {
                    // 回退最后生成的障碍物
                    blockList.remove(block);
                    blockNum--;
                    AllData.mapView[y * mapSize + x] = 1;
                    try (Jedis jedis = jedisPool.getResource()) {
                        jedis.setbit("blockView", y * mapSize + x, false);
                        jedis.setbit("mapView", y * mapSize + x, false);
                    }
                }

            } while (blockNum < maxBlockNum * 0.67);

            // 连通性检查（后续实现）
            logs += "\n> 已生成 " + blockNum + " 个障碍物";

            changeLogsTextArea(logs);
            repaint();
        });
        controlPanel.add(generateBlocksButton);

        // 添加组件间的间距
        controlPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // 调用初始化方法
        init();

        EventUtils.initEventHandlers(this, jedis, carList);
    }

    // 更新状态标签
    public void updateStatusLabel() {
        statusLabel.setText("当前模式: " + EventUtils.getCurrentMode());
    }

    // 地图鼠标监听器
    public class MapMouseListener extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            // 如果游戏已开始，禁止添加小车或障碍物
            if (gameStarted) {
                logs += "\n> 游戏已开始，禁止添加小车或障碍物";
                changeLogsTextArea(logs);
                return;
            }

            // 计算单元格大小
            int panelWidth = e.getComponent().getWidth();
            int panelHeight = e.getComponent().getHeight();
            int cellSize = Math.min(panelWidth / (mapSize + 2), panelHeight / (mapSize + 2));

            // 获取点击的坐标（修正版）
            int cellx = e.getX() / cellSize - 1;
            int celly = e.getY() / cellSize - 1;

            // 调整坐标计算（确保在边框内点击有效）
            if (cellx < 0)
                cellx = 0;
            if (celly < 0)
                celly = 0;
            if (cellx >= mapSize)
                cellx = mapSize - 1;
            if (celly >= mapSize)
                celly = mapSize - 1;
            if (SwingUtilities.isRightMouseButton(e)) {
                // 右键删除
                EventUtils.deleteAtPosition(cellx, celly);
            }
            if (SwingUtilities.isLeftMouseButton(e)) {
                // 左键添加
                EventUtils.Mode currentMode = EventUtils.getCurrentMode();
                if (currentMode == EventUtils.Mode.CAR) {
                    if (carNum >= maxCarNum) {
                        logs += "\n> 小车数量已达到上限";
                        changeLogsTextArea(logs);
                        return;
                    }
                    // 确保carIDs数组已初始化且有足够空间
                    if (AllData.carIDs == null || AllData.carIDs.length <= carNum) {
                        logs += "\n> 重新调整carIDs数组大小...";
                        changeLogsTextArea(logs);
                        AllData.setCarIDsLength(Math.max(carNum + 1, maxCarNum));
                    }
                    // 创建小车并添加ID到数组
                    Car newCar = EventUtils.createCarAtPosition(cellx, celly);
                    if (newCar != null) {
                        AllData.carIDs[carNum] = newCar.getCarID();
                        carNum++;
                        logs += "\n> 添加小车成功，当前小车数量: " + carNum;
                        changeLogsTextArea(logs);
                    }
                } else if (currentMode == EventUtils.Mode.BLOCK) {
                    if (blockNum >= maxBlockNum) {
                        logs += "\n> 障碍物数量已达到上限";
                        changeLogsTextArea(logs);
                        return;
                    }
                    // 处理障碍物模式
                    EventUtils.createBlockAtPosition(cellx, celly);
                    blockNum++;
                }
            }
        }
    }

    /**
     * 更新日志显示区域内容
     *
     * @param logs 要显示的日志内容
     */
    private void changeLogsTextArea(String logs) {
        logsTextArea.setText(logs);
    }

    /**
     * 初始化方法
     * 1. 初始化数据结构
     * 2. 初始化Redis连接池
     * 3. 初始化游戏地图
     * 4. 启动定时器
     */
    private void init() {
        try (Jedis jedis = ConnectionUtil.getResourceSafely()) {
            jedis.flushDB();
            // 1. 初始化数据结构
            carList = new LinkedList<Car>();
            blockList = new LinkedList<Block>();
            tasksMapList = new HashMap<String, LinkedList<Character>>();
            blockNum = 0;
            carNum = 0;
            AllData.mapView = new int[mapSize * mapSize];

            // 2. 使用已初始化的Redis连接池
            logs += "\n> 使用黑板连接池...";
            if (logsTextArea != null) {
                logsTextArea.setText(logs);
            }

            // 3. 将地图大小存入Redis
            if (mapSize <= 0) {
                mapSize = 10;
            }
            maxBlockNum = (int) (mapSize * mapSize * 0.37);
            maxCarNum = (int) (mapSize * 0.2);
            jedis.set("max_car_num", String.valueOf(maxCarNum));
            jedis.set("max_block_num", String.valueOf(maxBlockNum));
            jedis.set("map_size", String.valueOf(mapSize));
            logs += "\n> 地图大小已设置为: " + mapSize + "x" + mapSize;
            // 初始化AllData
            AllData.init(mapSize, maxCarNum);

            // 启动计时器
            logs += "\n> 计时器启动...";
            if (logsTextArea != null) {
                logsTextArea.setText(logs);
            }
            timer = new Timer(40, this);
            timer.start();

            // 小车大冒险开始
            logs += "\n> 小车大冒险开始...";
            if (logsTextArea != null) {
                logsTextArea.setText(logs);
            }
            isFinish = false;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 绘制游戏界面
     * 1. 绘制地图网格
     * 2. 绘制障碍物
     * 3. 绘制汽车
     * 4. 绘制任务路线(待实现)
     *
     * @param g 图形上下文对象
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // 计算每个格子的实际大小，根据窗口大小自适应
        int panelWidth = getWidth();
        int panelHeight = getHeight();
        int cellSize = Math.min(panelWidth / (mapSize + 2), panelHeight / (mapSize + 2)); // 考虑边框后的尺寸

        // 绘制地图网格(包括边框)
        int displaySize = mapSize + 2; // 增加边框
        for (int i = 0; i < displaySize * displaySize; i++) {
            int x = i % displaySize;
            int y = i / displaySize;

            // 判断是否为边框区域
            if (x == 0 || x == displaySize - 1 || y == 0 || y == displaySize - 1) {
                // 绘制边框障碍物
                g.setColor(Color.DARK_GRAY);
                g.fillRect(x * cellSize, y * cellSize, cellSize - 1, cellSize - 1);
            } else {
                // 绘制内部地图
                int mapIndex = (y - 1) * mapSize + (x - 1);
                if (mapIndex >= 0 && mapIndex < AllData.mapView.length) {
                    // 根据地图状态设置颜色
                    switch (AllData.mapView[mapIndex]) {
                        case -1:
                            g.setColor(Color.WHITE);
                            break;
                        case 0:
                            g.setColor(Color.BLACK);
                            break;
                        case 1:
                            g.setColor(Color.YELLOW);
                            break;
                        case 2:
                            g.setColor(Color.GREEN);
                            break;
                        case 3:
                            g.setColor(Color.BLUE);
                            break;
                        case 4:
                            g.setColor(Color.MAGENTA);
                            break;
                    }
                    g.fillRect(x * cellSize, y * cellSize, cellSize - 1, cellSize - 1);
                }
            }
        }

        // 画障碍物 - 修正贴图位置
        for (Block block : blockList) {
            ImageIcon icon = block.getBlockIcon();
            Image img = icon.getImage().getScaledInstance(cellSize, cellSize, Image.SCALE_SMOOTH);
            g.drawImage(img, block.getX() * cellSize, block.getY() * cellSize, this);
        }

        // 画车 - 修正贴图位置和缩放
        for (Car car : carList) {
            ImageIcon icon = car.getCarIcon();
            Image img = icon.getImage().getScaledInstance(cellSize, cellSize, Image.SCALE_SMOOTH);
            g.drawImage(img, ((int) car.getX() + 1) * cellSize, ((int) car.getY() + 1) * cellSize, this);
        }
    }

    /**
     * 游戏结束处理方法
     * 1. 停止定时器
     * 2. 停止所有汽车线程
     * 3. 关闭Redis连接池
     * 4. 更新日志显示
     */
    void gameOver() {
        // 1.计时器停止
        if (timer != null && timer.isRunning()) {
            timer.stop();
        }

        // 2.汽车全部死亡
        for (Car car : carList) {
            car.setWork(false);
        }

        // 3.清理所有小车队列和Redis内容
        logs += "\n> 清理小车队列和Redis内容";
        changeLogsTextArea(logs);
        try (Jedis jedis = ConnectionUtil.getJedis()) {
            // 清理Redis中的小车注册信息
            jedis.del("car_registry");

            // 清理每个小车的消息队列
            for (Car car : carList) {
                try {
                    Channel channel = ConnectionUtil.getConnection().createChannel();
                    channel.queueDelete(car.getCarID());
                    channel.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 4.打印日志
        logs += "\n> 小车大冒险结束";
        changeLogsTextArea(logs);

        gameStarted = false;
        EventUtils.setGameStarted(false);

        logs += "\n> 游戏已结束";
        changeLogsTextArea(logs);
        ConnectionUtil.markPoolForReset();

        if (replayRecorder != null) {
            replayRecorder.endSession();
            replayRecorder = null;
        }
    }

    /**
     * 定时器事件处理方法
     * 每40毫秒触发一次，用于更新游戏状态
     *
     * @param e 动作事件对象
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        // 小车大冒险还没有结束
        if (!isFinish) {
            if (jedisPool != null && !jedisPool.isClosed()) {
                return;
            }
            try (Jedis jedis = jedisPool.getResource()) {
                // 判断地图是否全部探索完
                boolean flag = jedis.bitcount("mapView", 0L, (long) mapSize * mapSize - 1) == (long) mapSize * mapSize;
                jedis.close();
                if (flag) {
                    isFinish = true;
                    gameOver();
                    JOptionPane.showMessageDialog(null, "小车全部探索完毕");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            repaint();
        }
    }

    // 新增位置可用性检查方法
    private boolean isPositionAvailable(int x, int y) {
        // 检查障碍物列表
        for (Block block : blockList) {
            if (block.getX() == x && block.getY() == y) {
                return false;
            }
        }
        // 检查小车位置（需要访问carList）
        for (Car car : carList) {
            if ((int) car.getX() == x && (int) car.getY() == y) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查地图的连通性，确保地图中大部分可通行区域是连通的。
     * 该方法使用广度优先搜索（BFS）算法来遍历地图，计算可达区域的格子数量。
     * 最后将可达区域的格子数量与理论最大可达区域进行比较，允许5%的误差。
     *
     * @return 如果可达区域的格子数量达到理论最大可达区域的95%及以上，返回true；否则返回false。
     */
    private boolean checkConnectivity() {
        // 用于记录地图中每个格子是否被访问过的二维数组
        boolean[][] visited = new boolean[mapSize][mapSize];
        // 记录地图中连通区域的数量
        int connectedComponents = 0;

        // 遍历地图内部区域（排除边框）
        for (int y = 1; y < mapSize - 1; y++) {
            for (int x = 1; x < mapSize - 1; x++) {
                // 如果当前格子为可通行区域且未被访问过
                if (AllData.mapView[y * mapSize + x] == 1 && !visited[y][x]) {
                    // 从该格子开始进行广度优先搜索
                    bfs(x, y, visited);
                    // 连通区域数量加1
                    connectedComponents++;
                    // 发现超过一个连通区域立即返回失败
                    if (connectedComponents > 1)
                        return false;
                }
            }
        }
        // 若只有一个连通区域，返回true
        return true;
    }

    /**
     * 广度优先搜索方法，从指定的起始点开始遍历地图，标记所有可达的格子。
     *
     * @param startX  起始点的x坐标
     * @param startY  起始点的y坐标
     * @param visited 记录格子是否被访问过的二维数组
     */
    private void bfs(int startX, int startY, boolean[][] visited) {
        // 用于存储待访问格子的队列
        Queue<Point> queue = new LinkedList<>();
        // 将起始点加入队列
        queue.add(new Point(startX, startY));
        // 标记起始点已访问
        visited[startY][startX] = true;

        // 定义四个方向：右、左、下、上
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        // 当队列不为空时，继续遍历
        while (!queue.isEmpty()) {
            // 取出队列头部的格子
            Point p = queue.poll();
            // 遍历四个方向
            for (int[] dir : dirs) {
                // 计算下一个格子的x坐标
                int nx = p.x + dir[0];
                // 计算下一个格子的y坐标
                int ny = p.y + dir[1];

                // 检查下一个格子是否在地图内部、未被访问过、为可通行区域且位置可用
                if (nx >= 1 && nx < mapSize - 1 && ny >= 1 && ny < mapSize - 1
                        && !visited[ny][nx]
                        && AllData.mapView[ny * mapSize + nx] == 1
                        && isPositionAvailable(nx, ny)) {
                    // 标记下一个格子已访问
                    visited[ny][nx] = true;
                    // 将下一个格子加入队列
                    queue.add(new Point(nx, ny));
                }
            }
        }
    }
}
