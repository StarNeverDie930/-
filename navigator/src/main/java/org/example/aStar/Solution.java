package org.example.aStar;

import org.example.utils.ConnectionUtil;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.PriorityQueue;

/**
 * A*算法路径查找解决方案类
 * 使用A*算法在二维网格地图中查找从起点到终点的最短路径
 * 
 * 主要功能：
 * 1. 实现A*寻路算法核心逻辑
 * 2. 管理开放列表(open)和关闭列表(close)
 * 3. 处理地图障碍物信息
 * 4. 生成从起点到终点的移动路径
 * 
 * 算法特点：
 * - 使用启发式函数评估节点优先级
 * - 结合Dijkstra算法和贪心算法的优点
 * - 保证找到最短路径(如果存在)
 */
public class Solution {

    /**
     * 开放列表(Open List)
     * 存储待探索的节点，按总代价(F值)排序
     * 使用优先队列实现，确保每次取出F值最小的节点
     */
    private PriorityQueue<Node> open = new PriorityQueue<Node>();

    /**
     * 关闭列表(Close List)
     * 存储已探索的节点
     * 使用ArrayList实现，便于快速查找和遍历
     */
    private ArrayList<Node> close = new ArrayList<Node>();

    /**
     * 已存在节点列表
     * 用于快速判断节点是否已被探索
     * 避免重复处理相同节点
     */
    private ArrayList<Node> exist = new ArrayList<Node>();

    /**
     * 地图二维数组
     * - 1表示障碍物(不可通行)
     * - 0表示可通行区域
     * 地图尺寸固定为22x22，包含边界墙
     */
    private int[][] map;

    public PriorityQueue<Node> getOpen() {
        return open;
    }

    public void setOpen(PriorityQueue<Node> open) {
        this.open = open;
    }

    public ArrayList<Node> getClose() {
        return close;
    }

    public void setClose(ArrayList<Node> close) {
        this.close = close;
    }

    public ArrayList<Node> getExist() {
        return exist;
    }

    public void setExist(ArrayList<Node> exist) {
        this.exist = exist;
    }

    public int[][] getMap() {
        return map;
    }

    public void setMap(int[][] map) {
        this.map = map;
    }

    /**
     * 判断节点是否已存在于exist列表中
     * 
     * @param node 要检查的节点
     * @return 如果节点已存在返回true，否则返回false
     * 
     *         实现细节：
     *         1. 遍历exist列表中的所有节点
     *         2. 比较节点的x和y坐标
     *         3. 如果找到坐标相同的节点则返回true
     * 
     *         时间复杂度：O(n)，n为exist列表大小
     */
    private boolean isExist(Node node) {
        for (Node node1 : exist) {
            if (node.getXCoordinate() == node1.getXCoordinate() && node.getYCoordinate() == node1.getYCoordinate()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断节点是否合法（可通行且未被占用）
     * 
     * @param x 节点的x坐标
     * @param y 节点的y坐标
     * @return 如果节点合法返回true，否则返回false
     * 
     *         合法性检查条件：
     *         1. 地图该位置不是障碍物(map[x][y] != 1)
     *         2. 该位置没有被其他节点占用(不在exist列表中)
     * 
     *         注意：
     *         - 该方法会调用isExist方法进行二次验证
     *         - 边界检查由地图初始化保证(边界默认为1)
     */
    private boolean isValid(int x, int y) {
        if (map[x][y] == 1) {
            return false;
        }
        for (Node node : exist) {
            if (isExist(new Node(x, y))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 扩展当前节点的所有合法邻居节点
     * 
     * @param currentNode 当前节点
     * @return 包含所有合法邻居节点的列表
     * 
     *         实现细节：
     *         1. 检查当前节点的上下左右四个方向
     *         2. 对每个方向调用isValid方法验证合法性
     *         3. 将合法节点加入邻居列表
     * 
     *         移动规则：
     *         - 仅支持四方向移动(上、下、左、右)
     *         - 不支持斜向移动
     * 
     *         返回值：
     *         - 可能返回空列表(如果没有合法邻居)
     *         - 最多返回4个节点的列表
     */
    private ArrayList<Node> extendCurrentNode(Node currentNode) {
        int x = currentNode.getXCoordinate();
        int y = currentNode.getYCoordinate();
        ArrayList<Node> neighbourNode = new ArrayList<Node>();

        if (isValid(x + 1, y)) {
            Node node = new Node(x + 1, y);
            neighbourNode.add(node);
        }
        if (isValid(x - 1, y)) {
            Node node = new Node(x - 1, y);
            neighbourNode.add(node);
        }
        if (isValid(x, y + 1)) {
            Node node = new Node(x, y + 1);
            neighbourNode.add(node);
        }
        if (isValid(x, y - 1)) {
            Node node = new Node(x, y - 1);
            neighbourNode.add(node);
        }

        return neighbourNode;
    }

    /**
     * A*算法核心搜索方法
     * 
     * @param start 起点节点
     * @param end   终点节点
     * @return 如果找到路径返回终点节点(包含完整路径)，否则返回null
     * 
     *         算法流程：
     *         1. 将起点加入开放列表(open)
     *         2. 循环处理直到开放列表为空：
     *         a. 取出开放列表中F值最小的节点作为当前节点
     *         b. 将当前节点移到关闭列表(close)
     *         c. 扩展当前节点的所有合法邻居节点
     *         d. 对每个邻居节点：
     *         - 如果是终点，返回该节点
     *         - 如果不在exist列表中，计算其F/G/H值并加入开放列表
     *         3. 如果开放列表为空且未找到终点，返回null
     * 
     *         性能考虑：
     *         - 使用优先队列优化节点选取
     *         - 使用exist列表避免重复处理
     */
    private Node aStarSearch(Node start, Node end) {
        this.open.add(start);
        this.exist.add(start);

        while (open.size() > 0) {
            // 拿到顶部元素,并从open表中删除
            Node currentNode = open.poll();

            // 将这个结点加入到close表
            this.close.add(currentNode);

            // 对当前结点进行扩展,返回邻居结点表
            ArrayList<Node> neighbourNode = extendCurrentNode(currentNode);

            // 遍历这个数组,看是否有目标结点出现
            for (Node node : neighbourNode) {
                if (node.getXCoordinate() == end.getXCoordinate() && node.getYCoordinate() == end.getYCoordinate()) {
                    node.initNode(currentNode, end);
                    return node;
                }
                if (!isExist(node)) {
                    // 对于未出现的结点加入到open表并且设置父节点,计算F,G,H
                    node.initNode(currentNode, end);
                    open.add(node);
                    exist.add(node);
                }
            }
        }

        return null;
    }

    /**
     * 获取从起点到终点的单步移动方向
     * 
     * @param start 起点节点
     * @param end   终点节点
     * @return 移动方向字符串（U:上, D:下, L:左, R:右）
     * 
     *         方向判断规则：
     *         1. 起点和终点必须相邻(坐标差为1)
     *         2. 比较x坐标差判断上下移动
     *         3. 比较y坐标差判断左右移动
     * 
     *         返回值说明：
     *         - "U": 向上移动(start.x + 1 == end.x)
     *         - "D": 向下移动(start.x - 1 == end.x)
     *         - "L": 向左移动(start.y + 1 == end.y)
     *         - "R": 向右移动(start.y - 1 == end.y)
     *         - "": 无效移动(节点不相邻或参数为null)
     */
    private String getOneStep(Node start, Node end) {
        String step = "";
        if (null == end || null == start) {
            return step;
        } else {
            if (start.getXCoordinate() + 1 == end.getXCoordinate()) {
                step = "U";
            } else if (start.getXCoordinate() - 1 == end.getXCoordinate()) {
                step = "D";
            } else if (start.getYCoordinate() + 1 == end.getYCoordinate()) {
                step = "L";
            } else if (start.getYCoordinate() - 1 == end.getYCoordinate()) {
                step = "R";
            }
        }
        return step;
    }

    /**
     * 获取从起点到终点的完整路径(主接口方法)
     * 
     * @param carX 起点的x坐标(0到map_size-1)
     * @param carY 起点的y坐标(0到map_size-1)
     * @param endX 终点的x坐标(0到map_size-1)
     * @param endY 终点的y坐标(0到map_size-1)
     * @return 包含完整移动路径的字符串(如"URLD")
     * 
     *         方法流程：
     *         1. 初始化22x22的地图(包含边界墙)
     *         2. 从Redis读取障碍物信息并更新地图
     *         3. 创建起点和终点节点(坐标+1跳过边界)
     *         4. 调用aStarSearch进行路径查找
     *         5. 从终点回溯生成完整路径字符串
     * 
     *         坐标转换：
     *         - 外部坐标(0到map_size-1)对应地图内部(1到map_size)
     *         - 边界墙占用0和21行列
     * 
     *         Redis交互：
     *         - 使用"blockView"键存储障碍物位图
     *         - 每个位代表一个网格(共map_size*map_size位)
     *         - offset计算: offset = (x-1)*map_size + (y-1)
     * 
     *         输出：
     *         - 打印路径中每个节点的坐标
     *         - 打印最终路径字符串
     *         - 打印带路径标记的地图(88表示路径)
     */
    public String getPath(int carX, int carY, int endX, int endY) {
        Jedis jedis = ConnectionUtil.getJedis();

        // 获取地图大小
        int mapSize = Integer.parseInt(jedis.get("map_size"));

        // 初始化地图
        int[][] map = new int[mapSize + 2][mapSize + 2];
        for (int i = 0; i < mapSize + 2; i++) {
            map[0][i] = 1;
            map[i][0] = 1;
            map[mapSize + 1][i] = 1;
            map[i][mapSize + 1] = 1;
        }

        // 读取障碍物
        for (int offset = 0; offset < mapSize * mapSize; offset++) {
            if (jedis.getbit("blockView", offset)) {
                map[offset / mapSize + 1][offset % mapSize + 1] = 1;
            }
        }

        Node start = new Node(carY + 1, carX + 1);
        start.setParentNode(null);
        Node end = new Node(endY + 1, endX + 1);
        setMap(map);

        Node resNode = aStarSearch(start, end);

        int count = 0;
        String path = "";
        while (resNode != null) {
            map[resNode.getXCoordinate()][resNode.getYCoordinate()] = 88;
            Node currentNode = resNode;
            // System.out.printf("%d: x:%d
            // y:%d\n",count,currentNode.getX(),currentNode.getY());
            resNode = resNode.getParentNode();
            if (resNode != null) {
                System.out.printf("%d: x:%d y:%d\n", count, resNode.getXCoordinate(), resNode.getYCoordinate());
            }
            count++;
            path = getOneStep(currentNode, resNode) + path;
        }
        System.out.println(path);
        // 画出来
        // 由于mapSize变量已在前面声明过，直接使用之前的变量并加上边界墙的偏移量
        mapSize += 2;
        for (int i = 0; i < mapSize; i++) {
            for (int j = 0; j < mapSize; j++) {
                System.out.printf("%3d", map[i][j]);
            }
            System.out.println();
        }
        return path;
    }
}
