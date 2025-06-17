package org.example.factory;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Connection;
import org.example.entity.AllData;
import org.example.entity.Car;
import org.example.utils.ConnectionUtil;
import com.rabbitmq.client.Channel;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeoutException;

public class CarFactory {

    private static Jedis jedis;
    private static Channel channel;
    // 存储所有创建的小车实例，便于后续查找
    private static final Map<String, Car> carMap = new HashMap<>();

    /**
     * 生产一个小车
     */
    public static Car getCar(int x,int y) {
        Car car = null;
        try (Jedis jedis = ConnectionUtil.getJedis())
        {
            //1.检查位置是否可用
            int mapSize = Integer.parseInt(jedis.get("map_size"));
            if(x<0||x>=mapSize||y<0||y>=mapSize) {
                System.out.println("位置超出地图范围");
                return null;
            }

            //检查障碍物
            if(jedis.getbit("blockview",y*mapSize+x)) {
                System.out.println("位置有障碍物");
                return null;
            }

            //2.检查是否已有小车
            Set<String> carIDs = jedis.smembers("car_ids");
            for (String id : carIDs) {
                Map<String, String> carData = jedis.hgetAll(id);
                int carX = Integer.parseInt(carData.get("x"));
                int carY = Integer.parseInt(carData.get("y"));

                if (carX == x && carY == y) {
                    return null; // 位置已有小车
                }
            }

            //3.创建小车
            String carID = UUID.randomUUID().toString();
            int carColor = new Random().nextInt(4) + 1; // 随机颜色，范围1-4，避免黑色

            Map<String, String> carMap = new HashMap<>();
            carMap.put("x", String.valueOf(x));
            carMap.put("y", String.valueOf(y));
            carMap.put("status", String.valueOf(Car.UP)); // 默认方向
            carMap.put("color", String.valueOf(carColor));
            jedis.hmset(carID, carMap);
            jedis.sadd("car_ids", carID);


            // 4.消息队列增加一个队列，队列名字就是carID，然后给carId交换机发生成的汽车id

            car = new Car(carID, x, y, Car.UP,carColor);
            // 将车辆添加到CarFactory的carMap用于后续查找
            CarFactory.carMap.put(carID, car);

            //创建后不立即启动
            //car.start();
            //将新小车ID添加到AllData.carIds中
            synchronized (AllData.class){
                if (AllData.carIDs == null) {
                    AllData.carIDs = new String[1];
                    AllData.carIDs[0] = carID;
                } else {
                    // 复制数组并添加新元素
                    String[] newCarIDs = new String[AllData.carIDs.length + 1];
                    // 复制数组
                    System.arraycopy(AllData.carIDs, 0, newCarIDs, 0, AllData.carIDs.length);
                    // 添加新元素
                    newCarIDs[AllData.carIDs.length] = carID;
                    // 更新数组引用
                    AllData.carIDs = newCarIDs;
                }
            }

            return car;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 启动小车
    public static void startCar(String carID) {
        try {
            Connection connection = ConnectionUtil.getConnection();
            Channel channel = connection.createChannel();

            System.out.println("正在启动小车: " + carID);

            try {
                // 声明队列 - 每个小车一个队列，用于接收指令
                channel.queueDeclare(carID, true, false, false, null);
                System.out.println("- 声明小车队列成功: " + carID);

                // 声明Direct交换机用于直接路由
                channel.exchangeDeclare("carDirectExchange", BuiltinExchangeType.DIRECT, true);
                System.out.println("- 声明carDirectExchange交换机成功");

                // 声明Fanout交换机用于广播
                channel.exchangeDeclare("carId", BuiltinExchangeType.FANOUT, true, false, null);
                System.out.println("- 声明carId交换机成功");

                // 将小车队列绑定到Direct交换机，使用carID作为路由键
                channel.queueBind(carID, "carDirectExchange", carID);
                System.out.println("- 绑定小车队列到carDirectExchange交换机成功");

                channel.exchangeBind("carId","carDirectExchange",carID);

                // 通过Fanout交换机广播小车ID，通知控制器
                channel.basicPublish("carId", "", null, carID.getBytes(StandardCharsets.UTF_8));
                System.out.println("- 通过carId交换机广播小车ID成功");

                // 查找并启动对应的小车线程
                Car car = carMap.get(carID);
                if (car != null) {
                    car.startCar();
                    if (!car.isAlive()) {
                        car.start();  // 启动线程
                        System.out.println("- 小车线程启动成功: " + carID);
                    } else {
                        System.out.println("- 小车线程已经在运行中: " + carID);
                    }
                } else {
                    System.err.println("- 未找到对应ID的小车对象: " + carID);
                    // 从Redis尝试找到这个小车
                    try (Jedis jedis = ConnectionUtil.getJedis()) {
                        Map<String, String> carData = jedis.hgetAll(carID);
                        if (carData != null && !carData.isEmpty()) {
                            int x = Integer.parseInt(carData.get("x"));
                            int y = Integer.parseInt(carData.get("y"));
                            char status = carData.get("status").charAt(0);
                            int color = Integer.parseInt(carData.get("color"));

                            car = new Car(carID, x, y, status, color);
                            carMap.put(carID, car);
                            car.startCar();
                            car.start();
                            System.out.println("- 从Redis重建小车并启动成功: " + carID);
                        }
                    }
                }

                System.out.println("小车启动成功: " + carID);
            } catch (Exception e) {
                System.err.println("小车启动失败: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // 关闭通道
                if (channel != null && channel.isOpen()) {
                    channel.close();
                }
            }
        } catch (IOException e) {
            System.err.println("创建连接失败: " + e.getMessage());
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            System.err.println("连接超时: " + e.getMessage());
            throw new RuntimeException(e);
        } catch (Exception e) {
            System.err.println("启动小车时发生未知错误: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
