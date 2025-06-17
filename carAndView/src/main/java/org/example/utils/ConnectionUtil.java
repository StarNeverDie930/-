package org.example.utils;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class ConnectionUtil {

    public static JedisPool jedisPool = null;
    public static final String CHANNEL_USERNAME = "guest";
    public static final String CHANNEL_PASSWORD = "guest";
    public static final String HOST = "localhost";
    public static final int JEDIS_PORT = 6379;
    public static final int CHANNEL_PORT = 5672;
    public static final String VIRTUALHOST = "/";
    public static boolean needsReset = false;

    static {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxIdle(10);
        config.setMaxTotal(50);
        try {
            jedisPool = new JedisPool(config, HOST, JEDIS_PORT);
            System.out.println("Redis连接成功");
        }catch (Exception e){
            System.out.println("Redis连接失败");
            e.printStackTrace();
        }
    }

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


    public static boolean isPoolInitialized() {
        return jedisPool != null && !jedisPool.isClosed();
    }

    public static Jedis safeGetResource(){
        if(!isPoolInitialized()){
            System.err.println("连接池未初始化，尝试重新创建");
            jedisPool = new JedisPool(new JedisPoolConfig(), HOST, JEDIS_PORT);
        }

        try {
            return jedisPool.getResource();
        }catch (Exception e){
            System.err.println("获取链接失败，使用直接连接："+e.getMessage());
            return new Jedis(HOST, CHANNEL_PORT);
        }
    }

    public static void close() {
        if(jedisPool!=null &&!jedisPool.isClosed()){
            jedisPool.close();
        }
    }

    //连接池状态检查
    public static boolean isPoolOpen(){
        return jedisPool!=null && !jedisPool.isClosed();
    }

    //安全的资源获取方法
    public static Jedis getResourceSafely(){
        if(!isPoolOpen()){
            closePool();
            initPool();
            needsReset = false;
        }
        return jedisPool.getResource();
    }

    private static void initPool(){
        if(isPoolOpen()) return;

        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxIdle(10);
        config.setMaxTotal(50);
        jedisPool = new JedisPool(config, HOST, JEDIS_PORT);
    }

    public static void markPoolForReset(){
        needsReset = true;
    }

    public static synchronized void closePool(){
        if(jedisPool!=null &&!jedisPool.isClosed()){
            jedisPool.close();
        }
    };
}
