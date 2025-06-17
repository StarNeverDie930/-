package org.example.controller;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import org.example.utils.ConnectionUtil;
import redis.clients.jedis.Jedis;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 控制器类
 * 协调小车和导航器，控制小车按照规划路径移动
 * 监控小车任务状态，当小车无任务时请求导航器规划路径
 */
public class Controller extends Thread {
    // 常量定义
    public static final String CONTROLLER_REGISTRY = "controller_registry";
    private static final String WORK_QUEUE = "workQueue";
    private static final String CAR_DIRECT_EXCHANGE = "carDirectExchange";
    private static final long STATUS_CHECK_INTERVAL = 5000; // 状态检查间隔，毫秒
    private static final long COMMAND_INTERVAL = 500; // 指令发送间隔，毫秒
    private static final long PUBLISH_TIMEOUT = 5000; // 发布确认超时，毫秒
    
    // 状态标志
    private final AtomicBoolean isWork;
    private final String carId;
    
    // 通信组件
    private Jedis jedis;
    private Channel channel;
    private long lastCheckTime;

    /**
     * 控制器构造函数
     * 
     * @param isWork 是否工作标志
     * @param carId 小车ID
     */
    public Controller(boolean isWork, String carId) {
        this.isWork = new AtomicBoolean(isWork);
        this.carId = carId;
        this.lastCheckTime = System.currentTimeMillis();
        
        try {
            // 初始化Redis连接
            this.jedis = ConnectionUtil.getJedis();
            
            // 初始化RabbitMQ连接
            this.channel = ConnectionUtil.getConnection().createChannel();
            
            // 启用发布确认机制
            this.channel.confirmSelect();
        } catch (Exception e) {
            System.err.println("控制器初始化异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 设置工作状态
     * 
     * @param work 工作状态
     */
    public void setWork(boolean work) {
        isWork.set(work);
    }
    
    /**
     * 检查组件状态
     * 检查Redis连接和小车任务状态
     */
    private void checkComponentsStatus() {
        try {
            // 检查Redis连接状态
            if (jedis == null || !jedis.isConnected()) {
                System.out.printf("控制器 %s: Redis连接断开，尝试重新连接...\n", carId);
                jedis = ConnectionUtil.getJedis();
            }
            
            // 检查RabbitMQ连接状态
            if (channel == null || !channel.isOpen()) {
                System.out.printf("控制器 %s: RabbitMQ连接断开，尝试重新连接...\n", carId);
                channel = ConnectionUtil.getConnection().createChannel();
                channel.confirmSelect();
            }

            // 检查小车任务状态
            String task = jedis.hget(carId, "task");
            
            if (task == null || task.isEmpty()) {
                System.out.printf("控制器 %s: 检测到小车无任务，请求任务\n", carId);
                requestTask();
            } else if ("task_request".equals(task)) {
                System.out.printf("控制器 %s: 收到小车的任务请求\n", carId);
                requestTask();
            }
        } catch (Exception e) {
            System.err.println("状态检查异常: " + e.getMessage());
        }
    }
    
    /**
     * 请求任务
     * 向导航器发送任务请求
     */
    private void requestTask() throws Exception {
        // 发送持久化消息到workQueue
        channel.basicPublish("", WORK_QUEUE,
                new AMQP.BasicProperties.Builder()
                        .deliveryMode(2) // 持久化消息
                        .build(),
                carId.getBytes(StandardCharsets.UTF_8));
        
        // 等待发布确认
        channel.waitForConfirmsOrDie(PUBLISH_TIMEOUT);
        System.out.printf("控制器 %s: 已向导航器发送任务请求\n", carId);
    }
    
    /**
     * 发送移动指令
     * 向小车发送单步移动命令
     * 
     * @param direction 移动方向 (U/D/L/R)
     */
    private void sendMoveCommand(char direction) throws Exception {
        // 发送持久化消息到小车队列
        channel.basicPublish(CAR_DIRECT_EXCHANGE, carId,
                new AMQP.BasicProperties.Builder()
                        .deliveryMode(2) // 持久化消息
                        .build(),
                String.valueOf(direction).getBytes(StandardCharsets.UTF_8));
        
        // 等待发布确认
        channel.waitForConfirmsOrDie(PUBLISH_TIMEOUT);
        System.out.printf("控制器 %s: 已向小车发送移动指令 %c\n", carId, direction);
    }
    
    /**
     * 检查地图是否已完全探索
     * 
     * @return 是否还有未探索区域
     */
    private boolean hasUnexploredArea() {
        try {
            int mapSize = Integer.parseInt(jedis.get("map_size"));
            long exploredCount = jedis.bitcount("mapView", 0, mapSize * mapSize - 1);
            return exploredCount < (long)mapSize * mapSize;
        } catch (Exception e) {
            System.err.println("检查地图探索状态异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 线程运行方法
     * 控制小车按照规划路径移动
     */
    @Override
    public void run() {
        System.out.printf("控制器 %s: 启动，为小车 %s 服务\n", carId, carId);
        
        try {
            while (isWork.get()) {
                // 定期检查组件状态
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastCheckTime > STATUS_CHECK_INTERVAL) {
                    checkComponentsStatus();
                    lastCheckTime = currentTime;
                }
                
                // 检查是否还有未探索区域
                if (hasUnexploredArea()) {
                    // 处理小车任务
                    processCarTask();
                } else {
                    System.out.printf("控制器 %s: 地图已完全探索，任务完成\n", carId);
                    isWork.set(false);
                }
                
                TimeUnit.MILLISECONDS.sleep(COMMAND_INTERVAL);
            }
            
            // 关闭连接
            closeConnections();
        } catch (Exception e) {
            System.err.println("控制器运行异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 处理小车任务
     * 读取任务并发送移动指令
     */
    private void processCarTask() {
        try {
            String task = jedis.hget(carId, "task");
            
            if (task != null && !task.isEmpty()) {
                // 获取第一个移动指令
                char direction = task.charAt(0);
                
                // 发送移动指令
                sendMoveCommand(direction);
                
                // 更新任务
                String remainingTask = task.substring(1);
                jedis.hset(carId, "task", remainingTask);
                System.out.printf("控制器 %s: 更新小车任务为 %s\n", carId, remainingTask);
            }
        } catch (Exception e) {
            System.err.println("处理小车任务异常: " + e.getMessage());
        }
    }
    
    /**
     * 关闭连接
     * 清理资源
     */
    private void closeConnections() {
        try {
            if (jedis != null && jedis.isConnected()) {
                jedis.close();
                System.out.printf("控制器 %s: Redis连接已关闭\n", carId);
            }
            
            if (channel != null && channel.isOpen()) {
                channel.close();
                System.out.printf("控制器 %s: RabbitMQ连接已关闭\n", carId);
            }
        } catch (Exception e) {
            System.err.println("关闭连接异常: " + e.getMessage());
        }
    }
}
