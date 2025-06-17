package org.example.carTaskJudge;

import org.example.utils.ConnectionUtil;
import redis.clients.jedis.Jedis;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 任务评估类
 * 评估小车任务完成情况，监控系统整体状态
 * 检查小车任务终点是否已被探索，避免重复探索
 */
public class CarTaskJudge extends Thread {
    // 常量定义
    private static final long CHECK_INTERVAL = 400; // 检查间隔，毫秒
    
    // 方向常量
    private static final char UP = 'U';
    private static final char DOWN = 'D';
    private static final char LEFT = 'L';
    private static final char RIGHT = 'R';
    
    // 状态标志
    private final AtomicBoolean isWork;
    private final String carId;
    
    // 通信组件
    private Jedis jedis;

    /**
     * 任务评估器构造函数
     * 
     * @param isWork 是否工作标志
     * @param carId 小车ID
     */
    public CarTaskJudge(boolean isWork, String carId) {
        this.isWork = new AtomicBoolean(isWork);
        this.carId = carId;
        
        try {
            // 初始化Redis连接
            this.jedis = ConnectionUtil.getJedis();
        } catch (Exception e) {
            System.err.println("任务评估器初始化异常: " + e.getMessage());
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
     * 检查任务终点状态
     * 如果终点已被探索或是障碍物，则清空任务
     */
    private void checkTaskEndpoint() {
        try {
            String task = jedis.hget(carId, "task");
            
            // 如果任务不为空，计算终点位置
            if (task != null && !task.isEmpty()) {
                // 获取当前位置
                int x = Integer.parseInt(jedis.hget(carId, "x"));
                int y = Integer.parseInt(jedis.hget(carId, "y"));
                int mapSize = Integer.parseInt(jedis.get("map_size"));
                
                // 模拟执行任务，计算终点位置
                int[] endpoint = calculateEndpoint(x, y, task, mapSize);
                int endX = endpoint[0];
                int endY = endpoint[1];
                
                // 检查终点是否已被探索或是障碍物
                if (jedis.getbit("mapView", endY * mapSize + endX) || 
                    jedis.getbit("blockView", endY * mapSize + endX)) {
                    // 终点已被探索或是障碍物，清空任务
                    System.out.printf("任务评估器: 小车 %s 的终点 (%d, %d) 已被探索或是障碍物，清空任务\n", 
                                      carId, endY, endX);
                    jedis.hset(carId, "task", "");
                }
            }
        } catch (Exception e) {
            System.err.println("检查任务终点异常: " + e.getMessage());
        }
    }
    
    /**
     * 计算终点位置
     * 根据当前位置和任务路径计算终点坐标
     * 
     * @param startX 起点X坐标
     * @param startY 起点Y坐标
     * @param task 任务路径
     * @param mapSize 地图大小
     * @return 终点坐标 [x, y]
     */
    private int[] calculateEndpoint(int startX, int startY, String task, int mapSize) {
        int x = startX;
        int y = startY;
        
        // 遍历任务路径，计算终点位置
        for (int i = 0; i < task.length(); i++) {
            char direction = task.charAt(i);
            
            switch (direction) {
                case UP:
                    if (y > 0) {
                        y--;
                    }
                    break;
                case DOWN:
                    if (y < mapSize - 1) {
                        y++;
                    }
                    break;
                case LEFT:
                    if (x > 0) {
                        x--;
                    }
                    break;
                case RIGHT:
                    if (x < mapSize - 1) {
                        x++;
                    }
                    break;
            }
        }
        
        return new int[] {x, y};
    }
    
    /**
     * 健康检查
     * 检查连接状态并在必要时重新连接
     */
    private void checkHealth() {
        try {
            if (jedis == null || !jedis.isConnected()) {
                System.out.printf("任务评估器: Redis连接断开，尝试重新连接...\n");
                jedis = ConnectionUtil.getJedis();
            }
        } catch (Exception e) {
            System.err.println("健康检查异常: " + e.getMessage());
        }
    }

    /**
     * 线程运行方法
     * 定期检查任务终点状态
     */
    @Override
    public void run() {
        System.out.printf("任务评估器: 启动，为小车 %s 服务\n", carId);
        
        try {
            while (isWork.get()) {
                // 健康检查
                checkHealth();
                
                // 检查任务终点状态
                checkTaskEndpoint();
                
                // 休眠一段时间
                TimeUnit.MILLISECONDS.sleep(CHECK_INTERVAL);
            }
            
            // 关闭连接
            closeConnection();
        } catch (Exception e) {
            System.err.println("任务评估器运行异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 关闭连接
     * 清理资源
     */
    private void closeConnection() {
        try {
            if (jedis != null && jedis.isConnected()) {
                jedis.close();
                System.out.printf("任务评估器: Redis连接已关闭\n");
            }
        } catch (Exception e) {
            System.err.println("关闭连接异常: " + e.getMessage());
        }
    }
}
