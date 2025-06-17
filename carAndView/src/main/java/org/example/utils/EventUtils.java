package org.example.utils;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.example.entity.AllData;
import org.example.entity.Block;
import org.example.entity.Car;
import org.example.factory.CarFactory;
import org.example.factory.BlockFactory;
import org.example.view.*;
import redis.clients.jedis.Jedis;

import static org.example.utils.ConnectionUtil.jedisPool;
import static org.example.view.MainJPanel.replayRecorder;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.logging.FileHandler;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class EventUtils {
    public static String Algorithm;
    private static Jedis jedis;
    private static MainJPanel mainJPanel;
    private static LinkedList<Car> carList;
    // 游戏状态
    private static boolean gameStarted = false;

    // 定义模式枚举
    public enum Mode {
        NORMAL("普通模式"),
        CAR("小车模式"),
        BLOCK("障碍物模式");

        private final String displayName;

        Mode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    // 当前模式
    private static Mode currentMode = Mode.NORMAL;

    // 获取当前模式
    public static Mode getCurrentMode() {
        return currentMode;
    }

    // 设置模式
    public static void setMode(Mode mode) {
        currentMode = mode;
        // 更新状态显示
        updateStatusDsplay();
    }

    // 更新状态显示
    private static void updateStatusDsplay() {
        if (mainJPanel != null) {
            mainJPanel.updateStatusLabel();
        }
    }

    // 模式切换按钮监听器
    public static class ModeToggleButtonListener implements ActionListener {
        private final Mode mode;

        public ModeToggleButtonListener(Mode mode) {
            this.mode = mode;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            setMode(mode);
            if (mainJPanel != null) {
                mainJPanel.setCurrentMode(currentMode);
            }
        }
    }

    // 设置游戏状态
    public static void setGameStarted(boolean Started) {
        gameStarted = Started;
    }

    /**
     * 初始化事件处理器
     * 
     * @param panel 地图视图面板
     * @param jedis Redis连接
     * @param cars  汽车列表
     */
    public static void initEventHandlers(MainJPanel panel, Jedis jedis, LinkedList<Car> cars) {
        mainJPanel = panel;
        EventUtils.jedis = jedis;
        carList = cars;

        // 设置初始模式
        panel.setCurrentMode(currentMode);
        gameStarted = panel.isGameStarted();
    }

    /**
     * 清空日志区
     */
    public static String clearLogsTextAreaActionPerformed(ActionEvent evt) {
        return "";
    }

    /**
     * 增加小车
     */
    public static Car createCarAtPosition(int x, int y) {
        // 检查游戏是否已经开始
        if (gameStarted) {
            System.out.println("游戏已经开始，无法添加小车");
            return null;
        }

        // 让小车工厂生产一个小车
        Car car = CarFactory.getCar(x, y);
        if (car != null) {
            mainJPanel.carList.add(car);
            mainJPanel.repaint();
        }
        return car;
    }

    /**
     * 增加障碍物
     */
    public static Block createBlockAtPosition(int x, int y) {
        // 检查游戏是否已经开始
        if (gameStarted) {
            System.out.println("游戏已经开始，无法添加障碍物");
            return null;
        }

        // 让障碍物工厂生产一个障碍物
        Block block = BlockFactory.getBlock(x, y);
        if (block != null) {
            mainJPanel.blockList.add(block);
            mainJPanel.repaint();
            if(replayRecorder != null){
                replayRecorder.recordBlockEvent(block, "BLOCK_ADD");
            }
        }
        return block;
    }

    /**
     * 删除功能
     * 
     * @param x 横坐标
     * @param y 纵坐标
     */
    public static void deleteAtPosition(int x, int y) {
        try (Jedis jedis = jedisPool.getResource()) {
            // 1.获取地图大小
            int mapSize = Integer.parseInt(jedis.get("map_size"));
            // 2.检查位置是否超出地图范围
            if (x < 0 || x > mapSize || y < 0 || y > mapSize) {
                return;
            }
            // 3.检查是否有小车
            Set<String> carIDs = jedis.smembers("car_ids");
            for (String id : carIDs) {
                Map<String, String> carData = jedis.hgetAll(id);
                int carX = Integer.parseInt(carData.get("x"));
                int carY = Integer.parseInt(carData.get("y"));

                if (carX == x && carY == y) {
                    synchronized (AllData.class) {
                        // 删除小车
                        List<String> list = new ArrayList<>(Arrays.asList(AllData.carIDs));
                        list.remove(id);
                        AllData.carIDs = list.toArray(new String[0]);
                    }
                    break;
                }
            }
            // 4.检查是否有障碍物
            if (jedis.getbit("blockView", y * mapSize + x) && jedis.getbit("mapview", y * mapSize + x)) { // 修正坐标计算顺序
                // 仅更新障碍物视图
                jedis.setbit("blockView", y * mapSize + x, false);
                jedis.setbit("mapview", y * mapSize + x, false);
                mainJPanel.blockList.removeIf(block -> block.getX() == x && block.getY() == y);
                mainJPanel.blockNum--;
                AllData.mapView[y * mapSize + x] = 0;
                Component[] components = mainJPanel.getComponents();
                for(Component comp:components){
                    if(comp instanceof JPanel){
                        comp.repaint();;
                    }
                }

                return;
            }

            // 5.删除小车（使用迭代器避免并发异常）
            Iterator<Car> iterator = carList.iterator();
            while (iterator.hasNext()) {
                Car car = iterator.next();
                if (car.getX() == x && car.getY() == y) {
                    iterator.remove();
                    // 更新Redis小车数据
                    // 假设car.getId()返回的是long类型，将其转换为String类型以适配srem方法
                    jedis.srem("car_ids", String.valueOf(car.getId()));
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //算法切换
    public static void setAlgorithm(String algorithm) {
        if(algorithm == null){
            return;
        }
        Algorithm = algorithm;
        try {
            Connection connection = ConnectionUtil.getConnection();
            Channel channel = connection.createChannel();
            channel.basicPublish("workQueue",algorithm,null, algorithm.getBytes());
            System.out.println("算法切换为："+algorithm);
            channel.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
