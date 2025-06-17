package org.example.factory;

import org.example.carTaskJudge.CarTaskJudge;
import org.example.utils.ConnectionUtil;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;

public class CarTaskJudgeFactory extends Thread {

    private Channel channel;
    private boolean isWork;

    public CarTaskJudgeFactory(boolean isWork) {
        this.isWork = isWork;
    }

    public void setWork(boolean work) {
        isWork = work;
    }

    // 收到carId,生产一个汽车任务评判器为之服务
    DeliverCallback deliverCallback = (consumerTag, message) -> {
        try {
            String carId = new String(message.getBody(), "UTF-8");
            if (carId != null && !carId.isEmpty()) {
                System.out.println("汽车任务评判器工厂: 接收到小车ID: " + carId);
                System.out.println("汽车任务评判器工厂: 正在生产一个汽车任务评判器...");
                CarTaskJudge c = new CarTaskJudge(true, carId);
                c.start();
                System.out.println("汽车任务评判器工厂: 小车" + carId + "的任务评判器启动成功");
                // 消息确认
                channel.basicAck(message.getEnvelope().getDeliveryTag(), false);
            } else {
                System.err.println("汽车任务评判器工厂: 接收到无效的小车ID，忽略此消息");
                // 拒绝消息但不重新入队
                channel.basicReject(message.getEnvelope().getDeliveryTag(), false);
            }
        } catch (Exception e) {
            System.err.println("汽车任务评判器工厂: 处理消息时发生异常: " + e.getMessage());
            e.printStackTrace();
            try {
                // 拒绝消息并重新入队
                channel.basicReject(message.getEnvelope().getDeliveryTag(), true);
            } catch (Exception ex) {
                System.err.println("汽车任务评判器工厂: 拒绝消息时发生异常: " + ex.getMessage());
            }
        }
    };

    // 取消消息时的回调
    CancelCallback cancelCallback = consumerTag -> {
        System.out.println("消息消费中断");
    };

    @Override
    public void run(){
        try{
            // 1.获取连接
            channel = ConnectionUtil.getConnection().createChannel();
            
            // 设置消费者预取计数
            channel.basicQos(1);
            
            // 声明交换机
            channel.exchangeDeclare("carId", "fanout", true);
            
            // 声明队列（持久化队列）
            channel.queueDeclare("carTaskJudgeQueue", true, false, false, null);

            // 绑定队列到交换机
            channel.queueBind("carTaskJudgeQueue", "carId", "");
            
            System.out.println("CarTaskJudgeFactory: 队列和交换机初始化完成，等待小车注册...");

            // 消费消息，手动确认模式
            channel.basicConsume("carTaskJudgeQueue", false, deliverCallback, cancelCallback);

            while (isWork){
                try {
                    if (channel == null || !channel.isOpen()){
                        System.out.println("CarTaskJudgeFactory: channel断开重连...");
                        channel = ConnectionUtil.getConnection().createChannel();
                        channel.basicQos(1);
                        
                        // 重新声明队列和绑定
                        channel.exchangeDeclare("carId", "fanout", true);
                        channel.queueDeclare("carTaskJudgeQueue", true, false, false, null);
                        channel.queueBind("carTaskJudgeQueue", "carId", "");
                        
                        // 重新开始消费
                        channel.basicConsume("carTaskJudgeQueue", false, deliverCallback, cancelCallback);
                        System.out.println("CarTaskJudgeFactory: 重新连接成功");
                    }
                    
                    Thread.sleep(5000);  // 降低检查频率，减少资源消耗
                } catch (Exception e) {
                    System.err.println("CarTaskJudgeFactory: 连接检查时出错: " + e.getMessage());
                    e.printStackTrace();
                    try {
                        Thread.sleep(2000);  // 出错后等待一段时间再重试
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } catch (Exception e){
            System.err.println("CarTaskJudgeFactory: 初始化或运行时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
