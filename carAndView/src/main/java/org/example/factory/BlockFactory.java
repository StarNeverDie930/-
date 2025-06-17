package org.example.factory;

import org.example.entity.AllData;
import org.example.entity.Block;
import redis.clients.jedis.Jedis;
import org.example.utils.ConnectionUtil;


public class BlockFactory {

    public static Block getBlock(int x,int y) {
        // 1.获取黑板连接
        try(Jedis jedis = ConnectionUtil.getJedis()) {
            int mapSize = Integer.parseInt(jedis.get("map_size"));
            //检查位置是否可用
            if(x<0||x>=mapSize||y<0||y>=mapSize) {
                System.out.println("位置超出地图范围");
                return null;
            }

            //检查障碍物
            if(jedis.getbit("blockView",y*mapSize+x)) {
                System.out.println("位置有障碍物");
                return null;
            }

            //创建障碍物
            Block block = new Block(x, y);
            jedis.setbit("blockView", y * mapSize + x, true);
            jedis.setbit("mapView", y * mapSize + x, true);
            AllData.mapView[y * mapSize + x] = -1;
            return block;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}