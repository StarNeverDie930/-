package org.example.view;

import org.example.utils.ConnectionUtil;
import org.example.utils.DBUtil;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class CarAdventureMainJFrame extends JFrame {

    private JPanel contentPane;

    /**
     * Launch the application.
     */
    public static void main(String[] args) {
        ConnectionUtil.jedisPool = new JedisPool(new JedisPoolConfig(), "localhost",6379);
        try{
            Runtime.getRuntime().addShutdownHook(new Thread(()-> {
                ConnectionUtil.close();
                System.out.println("程序退出，已关闭Redis连接池");
            }));

            //初始化数据库
            DBUtil.initDatabase();

            // 启动登录窗口
            SwingUtilities.invokeLater(() -> {
                LoginFrame loginFrame = new LoginFrame();
                loginFrame.setVisible(true);
            });
        }catch (Exception e) {
            e.printStackTrace();
            ConnectionUtil.close();
        }
    }

    /**
     * Create the frame.
     */
    public CarAdventureMainJFrame() {
        ImageIcon icon = new ImageIcon("E:\\idea_production\\car_find_way\\images\\carUp.png");
        setIconImage(icon.getImage());
        ConnectionUtil.jedisPool = new JedisPool(new JedisPoolConfig(), "localhost", 6379);
        setTitle("小车大冒险");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(20, 20, 820, 690);
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        contentPane.setLayout(new BorderLayout(0, 0));
        setContentPane(contentPane);
        MainJPanel mainJPanel = new MainJPanel();
        getContentPane().add(mainJPanel);

        //添加回放菜单
        JMenuBar menuBar = new JMenuBar();
        JMenu replayMenu = new JMenu("回放");
        JMenuItem openReplayItem = new JMenuItem("打开回放");
        openReplayItem.addActionListener(e -> {
            ReplayFrame replayFrame = new ReplayFrame();
            replayFrame.setVisible(true);
        });
        replayMenu.add(openReplayItem);
        menuBar.add(replayMenu);
        setJMenuBar(menuBar);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                Component[] components = getContentPane().getComponents();
                for (Component comp : components) {
                    if (comp instanceof MainJPanel) {
                        ((MainJPanel) comp).gameOver();
                    }
                }
                dispose();
            }
        });
    }
}
