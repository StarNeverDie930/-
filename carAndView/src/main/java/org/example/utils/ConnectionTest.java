package org.example.utils;

import com.rabbitmq.client.Connection;
import redis.clients.jedis.Jedis;

public class ConnectionTest {
    public static void main(String[] args) {
        // 测试Redis连接
        try (Jedis jedis = ConnectionUtil.getJedis()) {
            jedis.set("test_key", "hello_redis");
            System.out.println("Redis连接成功，测试值：" + jedis.get("test_key"));
        } catch (Exception e) {
            System.out.println("Redis连接失败：" + e.getMessage());
        }

        // 测试RabbitMQ连接
        try (Connection connection = ConnectionUtil.getConnection()) {
            System.out.println("RabbitMQ连接成功，协议版本：" + connection.getServerProperties());
        } catch (Exception e) {
        System.out.println("RabbitMQ连接失败：" + e.getMessage());
        }

    }
}
