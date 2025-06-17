package org.example.entity;

public class AllData {
    public static int[] mapView;// 存放地图数据
    public static String[] carIDs;// 存放小车ID

    public static void init(int mapSize, int carNum) {
        mapView = new int[mapSize * mapSize];
        carIDs = new String[carNum];
    }

    public static void setCarIDsLength(int carNum) {
        carIDs = new String[carNum];
    }
}
