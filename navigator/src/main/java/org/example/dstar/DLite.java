package org.example.dstar;

import org.example.utils.ConnectionUtil;
import redis.clients.jedis.Jedis;
import java.util.*;

public class DLite {
    private static class Node implements Comparable<Node> {
        int x, y;
        double g;
        double rhs;
        double h;
        Node parent;

        public Node(int x, int y) {
            this.x = x;
            this.y = y;
            this.g = Double.POSITIVE_INFINITY;
            this.rhs = Double.POSITIVE_INFINITY;
        }

        @Override
        public int compareTo(Node other) {
            double thisKey = Math.min(g, rhs) + h;
            double otherKey = Math.min(other.g, other.rhs) + other.h;
            if (thisKey < otherKey)
                return -1;
            if (thisKey > otherKey)
                return 1;
            return 0;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null || getClass() != obj.getClass())
                return false;
            Node node = (Node) obj;
            return x == node.x && y == node.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    private PriorityQueue<Node> openList = new PriorityQueue<>();
    private Map<String, Node> allNodes = new HashMap<>();
    private Node start;
    private Node goal;
    private int[][] map;
    private int mapSize;

    public DLite(int startX, int startY, int goalX, int goalY, int mapSize) {
        this.mapSize = mapSize;
        this.start = getNode(startX, startY);
        this.goal = getNode(goalX, goalY);
        this.goal.rhs = 0;
        this.goal.h = heuristic(start, goal);
        openList.add(goal);
    }

    private Node getNode(int x, int y) {
        String key = x + "," + y;
        if (!allNodes.containsKey(key)) {
            allNodes.put(key, new Node(x, y));
        }
        return allNodes.get(key);
    }

    private double heuristic(Node a, Node b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    private List<Node> getNeighbors(Node node) {
        List<Node> neighbors = new ArrayList<>();
        int[][] directions = { { 0, 1 }, { 1, 0 }, { 0, -1 }, { -1, 0 } };

        for (int[] dir : directions) {
            int nx = node.x + dir[0];
            int ny = node.y + dir[1];

            if (nx >= 0 && nx < mapSize && ny >= 0 && ny < mapSize && map[nx][ny] != 1) {
                neighbors.add(getNode(nx, ny));
            }
        }
        return neighbors;
    }

    private double cost(Node a, Node b) {
        if (map[b.x][b.y] == 1)
            return Double.POSITIVE_INFINITY;
        return 1;
    }

    private void updateVertex(Node u) {
        if (!u.equals(goal)) {
            double minRhs = Double.POSITIVE_INFINITY;
            for (Node s : getNeighbors(u)) {
                minRhs = Math.min(minRhs, s.g + cost(u, s));
            }
            u.rhs = minRhs;
        }

        openList.remove(u);
        if (u.g != u.rhs) {
            openList.add(u);
        }
    }

    private void computeShortestPath() {
        while (!openList.isEmpty() &&
                (openList.peek().compareTo(start) < 0 || start.rhs != start.g)) {
            Node u = openList.poll();

            if (u.g > u.rhs) {
                u.g = u.rhs;
                for (Node s : getNeighbors(u)) {
                    updateVertex(s);
                }
            } else {
                u.g = Double.POSITIVE_INFINITY;
                updateVertex(u);
                for (Node s : getNeighbors(u)) {
                    updateVertex(s);
                }
            }
        }
    }

    public String getPath(int carX, int carY, int endX, int endY) {
        Jedis jedis = ConnectionUtil.getJedis();

        // 初始化地图
        map = new int[mapSize][mapSize];
        for (int offset = 0; offset < mapSize * mapSize; offset++) {
            if (jedis.getbit("blockView", offset)) {
                map[offset / mapSize][offset % mapSize] = 1;
            }
        }

        // 设置新的起点
        this.start = getNode(carX, carY);
        this.start.h = heuristic(start, goal);

        computeShortestPath();

        // 构建路径
        StringBuilder path = new StringBuilder();
        Node current = start;

        while (current != null && !current.equals(goal)) {
            Node next = null;
            double minCost = Double.POSITIVE_INFINITY;

            for (Node neighbor : getNeighbors(current)) {
                double c = cost(current, neighbor) + neighbor.g;
                if (c < minCost) {
                    minCost = c;
                    next = neighbor;
                }
            }

            if (next == null)
                break;

            // 确定移动方向
            if (next.x > current.x)
                path.append("D");
            else if (next.x < current.x)
                path.append("U");
            else if (next.y > current.y)
                path.append("R");
            else if (next.y < current.y)
                path.append("L");

            current = next;
        }

        return path.toString();
    }
}