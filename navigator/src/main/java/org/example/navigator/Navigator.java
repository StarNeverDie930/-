package org.example.navigator;

import org.example.aStar.Solution;
import org.example.utils.ConnectionUtil;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import redis.clients.jedis.Jedis;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 导航器类
 * 负责为小车规划路径，使用A*算法计算最优路径
 * 监听RabbitMQ中的任务请求，为小车选择合适的目标点
 */
public class Navigator extends Thread {
    // 常量定义
    private static final String WORK_QUEUE = "workQueue";
    private static final int RECONNECT_DELAY = 1000; // 重连延迟，毫秒
    
    // 状态标志
    private final AtomicBoolean isWork;
    
    // 通信组件
    private Jedis jedis;
    private Channel channel;
    private final Random random;

    /**
     * 导航器构造函数
     * 
     * @param isWork 是否工作标志
     */
    public Navigator(boolean isWork) {
        this.isWork = new AtomicBoolean(isWork);
        this.random = new Random();
        
        try {
            // 初始化Redis连接
            this.jedis = ConnectionUtil.getJedis();
            
            // 初始化RabbitMQ连接
            this.channel = ConnectionUtil.getConnection().createChannel();
            
            // 声明消息队列
            this.channel.queueDeclare(WORK_QUEUE, true, false, false, null);
        } catch (Exception e) {
            System.err.println("导航器初始化异常: " + e.getMessage());
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
     * 消息接收回调函数
     * 处理从RabbitMQ接收到的任务请求
     */
    DeliverCallback deliverCallback = (consumerTag, message) -> {
        try {
            String carId = new String(message.getBody(), StandardCharsets.UTF_8);
            System.out.println("导航器: 收到小车 " + carId + " 的任务请求");
            
            // 为小车规划路径
            planPath(carId);
        } catch (Exception e) {
            System.err.println("处理任务请求异常: " + e.getMessage());
            e.printStackTrace();
        }
    };

    /**
     * 为小车规划路径
     * 
     * @param carId 小车ID
     */
    private void planPath(String carId) {
        try {
            // 1. 读取小车当前位置
//            int startX = Integer.parseInt(jedis.hget(carId, "x"));
//            int startY = Integer.parseInt(jedis.hget(carId, "y"));
//            System.out.printf("导航器: 小车 %s 当前位置 (%d, %d)\n", carId, startX, startY);
            String xStr = jedis.hget(carId,"x");
            String yStr = jedis.hget(carId,"y");
            if(xStr == null || yStr == null) {
                System.out.printf("导航器：小车 %s 位置数据缺失 (x:%s,y:%s)\n",carId,xStr,yStr);
                return;

            }

            int startX = Integer.parseInt(xStr);
            int startY = Integer.parseInt(yStr);
            
            // 2. 获取地图大小
            int mapSize = Integer.parseInt(jedis.get("map_size"));
            
            // 3. 选择目标点
            int[] target = selectTargetPoint(mapSize);
            int endX = target[0];
            int endY = target[1];
            
            // 4. 使用A*算法计算路径
            Solution solution = new Solution();
            String path = "";
            
            // 确保目标点不是障碍物
            if (!jedis.getbit("blockView", endY * mapSize + endX)) {
                path = solution.getPath(startX, startY, endX, endY);
            }
            
            // 5. 将路径写入Redis
            jedis.hset(carId, "task", path);
            System.out.printf("导航器: 为小车 %s 设置路径: %s\n", carId, path);
        } catch (Exception e) {
            System.err.println("规划路径异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 选择目标点
     * 优先选择未探索的区域
     * 
     * @param mapSize 地图大小
     * @return 目标点坐标 [x, y]
     */
    private int[] selectTargetPoint(int mapSize) {
        int endX, endY;
        
        // 首先尝试随机选择一个点
        endX = random.nextInt(mapSize);
        endY = random.nextInt(mapSize);
        System.out.printf("导航器: 随机生成目标点 (%d, %d)\n", endY, endX);
        
        // 检查该点是否已被探索或是障碍物
        if (jedis.getbit("mapView", endY * mapSize + endX) || jedis.getbit("blockView", endY * mapSize + endX)) {
            System.out.printf("导航器: 点 (%d, %d) 已被探索或是障碍物，重新选择...\n", endY, endX);
            
            // 随机决定从头还是从尾开始搜索未探索点
            if (random.nextInt(2) == 0) {
                System.out.println("导航器: 从头开始搜索未探索点");
                int offset = 0;
                while (offset < mapSize * mapSize) {
                    if (!jedis.getbit("mapView", offset) && !jedis.getbit("blockView", offset)) {
                        endX = offset % mapSize;
                        endY = offset / mapSize;
                        System.out.printf("导航器: 选择未探索点 (%d, %d)\n", endY, endX);
                        break;
                    }
                    offset++;
                }
            } else {
                System.out.println("导航器: 从尾开始搜索未探索点");
                int offset = mapSize * mapSize - 1;
                while (offset >= 0) {
                    if (!jedis.getbit("mapView", offset) && !jedis.getbit("blockView", offset)) {
                        endX = offset % mapSize;
                        endY = offset / mapSize;
                        System.out.printf("导航器: 选择未探索点 (%d, %d)\n", endY, endX);
                        break;
                    }
                    offset--;
                }
            }
        }
        
        return new int[] {endX, endY};
    }

    /**
     * 消息取消回调
     */
    CancelCallback cancelCallback = consumerTag -> {
        System.out.println("导航器: 消息消费中断");
    };

    /**
     * 线程运行方法
     * 初始化消息队列并持续监听任务请求
     */
    @Override
    public void run() {
        System.out.println("导航器启动...");
        
        try {
            // 设置消费者
            channel.basicConsume(WORK_QUEUE, true, deliverCallback, cancelCallback);
            
            // 主循环
            while (isWork.get()) {
                // 健康检查
                checkHealth();
                
                Thread.sleep(RECONNECT_DELAY);
            }
            
            // 关闭连接
            closeConnections();
        } catch (Exception e) {
            System.err.println("导航器运行异常: " + e.getMessage());
            e.printStackTrace();
            isWork.set(false);
        }
    }
    
    /**
     * 健康检查
     * 检查连接状态并在必要时重新连接
     */
    private void checkHealth() {
        try {
            // 检查Redis连接
            if (jedis == null || !jedis.isConnected()) {
                System.out.println("导航器: Redis连接断开，尝试重新连接...");
                jedis = ConnectionUtil.getJedis();
                if(!testRedisConnection(jedis)){
                    System.out.println("导航器: Redis连接失败，尝试重新连接...");
                    jedis = ConnectionUtil.getJedis();
                }
            }
            
            // 检查RabbitMQ连接
            if (channel == null || !channel.isOpen()) {
                System.out.println("导航器: RabbitMQ连接断开，尝试重新连接...");
                channel = ConnectionUtil.getConnection().createChannel();
                channel.queueDeclare(WORK_QUEUE, true, false, false, null);
                channel.basicConsume(WORK_QUEUE, true, deliverCallback, cancelCallback);
            }
        } catch (Exception e) {
            System.err.println("健康检查异常: " + e.getMessage());
        }
    }

    // 测试Redis连接
    private boolean testRedisConnection(Jedis jedis) {
        try {
            return "PONG".equals(jedis.ping()); // 发送一个简单的PING命令，返回"PONG"
        } catch (Exception e) {
            return false;
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
                System.out.println("导航器: Redis连接已关闭");
            }
            
            if (channel != null && channel.isOpen()) {
                channel.close();
                System.out.println("导航器: RabbitMQ连接已关闭");
            }
        } catch (Exception e) {
            System.err.println("关闭连接异常: " + e.getMessage());
        }
    }
}
