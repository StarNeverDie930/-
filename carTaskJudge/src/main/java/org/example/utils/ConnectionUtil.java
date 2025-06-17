package org.example.utils;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import redis.clients.jedis.Jedis;

public class ConnectionUtil {

    public static final String CHANNEL_USERNAME = "guest";
    public static final String CHANNEL_PASSWORD = "guest";
    public static final String HOST = "localhost";
    public static final int JEDIS_PORT = 6379;
    public static final int CHANNEL_PORT = 5672;
    public static final String VIRTUALHOST = "/";

    public static Connection getConnection() throws Exception{
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(HOST);
        connectionFactory.setPort(CHANNEL_PORT);
        connectionFactory.setUsername(CHANNEL_USERNAME);
        connectionFactory.setPassword(CHANNEL_PASSWORD);
        connectionFactory.setVirtualHost(VIRTUALHOST);
        Connection connection = connectionFactory.newConnection();
        return connection;
    }

    public static Jedis getJedis(){
        Jedis jedis = new Jedis(HOST, JEDIS_PORT);
        return jedis;
    }
}
