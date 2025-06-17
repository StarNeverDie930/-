package org.example.entity;

import org.example.utils.ConnectionUtil;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import redis.clients.jedis.Jedis;

import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import javax.swing.*;

import static org.example.view.MainJPanel.replayRecorder;

/**
 * 汽车实体类
 * 该类表示一个可以在网格中移动的汽车对象
 * 使用RabbitMQ接收移动指令，使用Redis存储位置信息
 * 每个汽车对象运行在独立的线程中
 */
public class Car extends Thread {
    // 常量定义
    public static final char UP = 'U';
    public static final char DOWN = 'D';
    public static final char LEFT = 'L';
    public static final char RIGHT = 'R';
    private static final int HEARTBEAT_INTERVAL = 5000; // 心跳间隔，毫秒

    // 状态标志
    private static boolean isStarted = false;
    private boolean isWork;

    // 汽车属性
    private String carID;
    private long x;
    private long y;
    private char status;
    private ImageIcon carIcon;
    private int carColor;

    // 通信组件
    private Channel channel;
    private int mapSize;
    private long lastTaskTime;

    /**
     * 汽车构造函数
     *
     * @param carID    汽车唯一标识符
     * @param x        初始x坐标
     * @param y        初始y坐标
     * @param status   初始方向(UP/DOWN/LEFT/RIGHT)
     * @param carColor 汽车颜色标识
     */
    public Car(String carID, long x, long y, char status, int carColor) {
        try {
            this.carID = carID;
            this.x = x;
            this.y = y;
            this.status = status;
            this.carColor = carColor;
            this.isWork = true;
            this.lastTaskTime = System.currentTimeMillis();

            // 初始化图标
            setCarIcon();

            // 初始化RabbitMQ连接
            this.channel = ConnectionUtil.getConnection().createChannel();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 消息接收回调函数
     * 处理从RabbitMQ接收到的移动指令
     */
    DeliverCallback deliverCallback = (consumerTag, message) -> {
        String direction = new String(message.getBody(), StandardCharsets.UTF_8);
        processMovement(direction.charAt(0));
        channel.basicAck(message.getEnvelope().getDeliveryTag(), false);
    };

    /**
     * 处理移动指令
     * @param direction 移动方向 (U/D/L/R)
     */
    private void processMovement(char direction) {
        try (Jedis jedis = ConnectionUtil.getJedis()) {
            // 获取地图大小
            mapSize = Integer.parseInt(jedis.get("map_size"));

            // 获取当前坐标
            int currentX = Integer.parseInt(jedis.hget(carID, "x"));
            int currentY = Integer.parseInt(jedis.hget(carID, "y"));

            // 根据方向更新坐标
            boolean moved = false;
            int newX = currentX;
            int newY = currentY;

            switch (direction) {
                case UP:
                    if (currentY > 0) {
                        newY = currentY - 1;
                        status = UP;
                    }
                    break;
                case DOWN:
                    if (currentY < mapSize - 1) {
                        newY = currentY + 1;
                        status = DOWN;
                    }
                    break;
                case LEFT:
                    if (currentX > 0) {
                        newX = currentX - 1;
                        status = LEFT;
                    }
                    break;
                case RIGHT:
                    if (currentX < mapSize - 1) {
                        newX = currentX + 1;
                        status = RIGHT;
                    }
                    break;
            }

            // 检查目标位置是否有效
            if (newX != currentX || newY != currentY) {
                // 检查目标位置是否有障碍物
                if (!jedis.getbit("blockView", newY * mapSize + newX)) {
                    // 检查目标位置是否被其他小车占用
                    boolean isOccupied = false;
                    for (String otherCarID : AllData.carIDs) {
                        if (otherCarID != null && !otherCarID.equals(carID)) {
                            int otherX = Integer.parseInt(jedis.hget(otherCarID, "x"));
                            int otherY = Integer.parseInt(jedis.hget(otherCarID, "y"));
                            if (otherX == newX && otherY == newY) {
                                isOccupied = true;
                                break;
                            }
                        }
                    }

                    if (!isOccupied) {
                        x = newX;
                        y = newY;
                        moved = true;
                    }
                }
            }

            // 如果成功移动，更新Redis和UI
            if (replayRecorder != null && moved) {
                // 更新Redis中的坐标
                jedis.hset(carID, "x", String.valueOf(x));
                jedis.hset(carID, "y", String.valueOf(y));

                replayRecorder.recordCarEvent(this,"CAR_MOVE");

                // 更新图标
                setCarIcon();

                // 更新地图视图
                updateMapView(jedis);

                System.out.printf("%s: 移动到 (%d, %d), 方向: %c\n", carID, x, y, status);
            } else {
                System.out.printf("%s: 无法移动到目标位置 (%d, %d)\n", carID, newX, newY);
            }
        } catch (Exception e) {
            System.err.println("处理移动指令时出错: " + e.getMessage());
        }
    }

    /**
     * 更新地图视图
     * 点亮当前位置和周围的格子
     */
    private void updateMapView(Jedis jedis) {
        // 点亮当前格子
        if (!jedis.getbit("blockView", y * mapSize + x)) {
            jedis.setbit("mapView", y * mapSize + x, true);
            AllData.mapView[(int) (y * mapSize + x)] = carColor;
        }

        // 点亮上方格子
        if (y > 0 && !jedis.getbit("blockView", (y - 1) * mapSize + x)) {
            jedis.setbit("mapView", (y - 1) * mapSize + x, true);
            AllData.mapView[(int) ((y - 1) * mapSize + x)] = carColor;
        }

        // 点亮下方格子
        if (y < mapSize - 1 && !jedis.getbit("blockView", (y + 1) * mapSize + x)) {
            jedis.setbit("mapView", (y + 1) * mapSize + x, true);
            AllData.mapView[(int) ((y + 1) * mapSize + x)] = carColor;
        }

        // 点亮左侧格子
        if (x > 0 && !jedis.getbit("blockView", y * mapSize + x - 1)) {
            jedis.setbit("mapView", y * mapSize + x - 1, true);
            AllData.mapView[(int) (y * mapSize + x - 1)] = carColor;
        }

        // 点亮右侧格子
        if (x < mapSize - 1 && !jedis.getbit("blockView", y * mapSize + x + 1)) {
            jedis.setbit("mapView", y * mapSize + x + 1, true);
            AllData.mapView[(int) (y * mapSize + x + 1)] = carColor;
        }
    }

    /**
     * 消息取消回调
     */
    CancelCallback cancelCallback = consumerTag -> {
        System.out.println(carID + ": 消息消费中断");
    };

    /**
     * 线程运行方法
     * 初始化消息队列并持续监听移动指令
     */
    @Override
    public void run() {
        try {
            System.out.println(carID + " 小车启动...");

            // 声明交换机和队列
            channel.exchangeDeclare("carDirectExchange", "direct", true);
            channel.queueDeclare(carID, true, false, false, null);
            channel.queueBind(carID, "carDirectExchange", carID);

            // 设置消费者
            channel.basicConsume(carID, false, deliverCallback, cancelCallback);

            // 主循环
            while (isWork) {
                // 健康检查
                checkHealth();

                // 心跳检测
                sendHeartbeat();

                Thread.sleep(100);
            }

            // 关闭连接
            channel.close();
        } catch (Exception e) {
            System.err.println(carID + " 运行异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 健康检查
     * 检查连接状态并在必要时重新连接
     */
    private void checkHealth() {
        try {
            if (channel == null || !channel.isOpen()) {
                System.out.println(carID + ": 通道断开，尝试重新连接...");
                channel = ConnectionUtil.getConnection().createChannel();
                channel.exchangeDeclare("carDirectExchange", "direct", true);
                channel.queueDeclare(carID, true, false, false, null);
                channel.queueBind(carID, "carDirectExchange", carID);
                channel.basicConsume(carID, false, deliverCallback, cancelCallback);
            }
        } catch (Exception e) {
            System.err.println(carID + " 健康检查异常: " + e.getMessage());
        }
    }

    /**
     * 发送心跳
     * 定期向Redis更新状态
     */
    private void sendHeartbeat() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTaskTime > HEARTBEAT_INTERVAL) {
            try (Jedis jedis = ConnectionUtil.getJedis()) {
                jedis.hset(carID, "lastHeartbeat", String.valueOf(currentTime));
                lastTaskTime = currentTime;
            } catch (Exception e) {
                System.err.println(carID + " 心跳更新异常: " + e.getMessage());
            }
        }
    }

    /**
     * 启动小车
     */
    public void startCar() {
        isStarted = true;
        this.start();

        //记录小车启动
        if(replayRecorder!= null) {
            replayRecorder.recordCarEvent(this,"CAR_START");
        }
    }

    // Getter和Setter方法
    public long getX() {
        return x;
    }

    public void setX(long x) {
        this.x = x;
    }

    public long getY() {
        return y;
    }

    public void setY(long y) {
        this.y = y;
    }

    public char getStatus() {
        return status;
    }

    public void setStatus(char status) {
        this.status = status;
    }

    public ImageIcon getCarIcon() {
        return carIcon;
    }

    /**
     * 根据当前方向设置汽车图标
     */
    private void setCarIcon() {
        String basePath = "/images/";
        try {
            switch (status) {
                case UP:
                    carIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource(basePath + "carUp.png")));
                    break;
                case DOWN:
                    carIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource(basePath + "carDown.png")));
                    break;
                case LEFT:
                    carIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource(basePath + "carLeft.png")));
                    break;
                case RIGHT:
                    carIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource(basePath + "carRight.png")));
                    break;
                default:
                    carIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource(basePath + "carUp.png")));
            }
            if (carIcon.getImageLoadStatus() != MediaTracker.COMPLETE) {
                System.err.println("Car icon loading failed for " + status + " at " + basePath + status + ".png");
                carIcon = null; // 回退到默认显示
            }
        } catch (Exception e) {
            System.err.println("Car icon loading failed: " + e.getMessage());
            carIcon = null; // 回退到默认显示
        }
    }

    public String getCarID() {
        return carID;
    }

    public void setCarID(String carID) {
        this.carID = carID;
    }

    public boolean isWork() {
        return isWork;
    }

    public void setWork(boolean isWork) {
        this.isWork = isWork;
    }

    public int getCarColor() {
        return carColor;
    }

    public void setCarColor(int carColor) {
        this.carColor = carColor;
    }
}
