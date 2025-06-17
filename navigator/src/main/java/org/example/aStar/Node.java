package org.example.aStar;

public class Node implements Comparable<Node> {
    private int xCoordinate; // 节点的x坐标
    private int yCoordinate; // 节点的y坐标
    private int totalCost;   // 总成本(F = G + H)
    private int actualCost;  // 实际成本(从起点到当前节点的成本)
    private int heuristicCost; // 启发式成本(从当前节点到终点的估计成本)
    private Node parentNode; // 父节点，用于回溯路径

    public Node(int x, int y) {
        this.xCoordinate = x;
        this.yCoordinate = y;
    }

    public int getXCoordinate() {
        return xCoordinate;
    }

    public void setXCoordinate(int xCoordinate) {
        this.xCoordinate = xCoordinate;
    }

    public int getYCoordinate() {
        return yCoordinate;
    }

    public void setYCoordinate(int yCoordinate) {
        this.yCoordinate = yCoordinate;
    }

    public int getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(int totalCost) {
        this.totalCost = totalCost;
    }

    public int getActualCost() {
        return actualCost;
    }

    public void setActualCost(int actualCost) {
        this.actualCost = actualCost;
    }

    public int getHeuristicCost() {
        return heuristicCost;
    }

    public void setHeuristicCost(int heuristicCost) {
        this.heuristicCost = heuristicCost;
    }

    public Node getParentNode() {
        return parentNode;
    }

    public void setParentNode(Node parentNode) {
        this.parentNode = parentNode;
    }

    /**
     * 通过结点的目标和目标结点,计算出F,G,H三个属性
     */
    public void initNode(Node parentNode,Node dest){
        this.parentNode = parentNode;
        if (this.parentNode != null){// 走过的步数G是父节点加1
            this.actualCost = parentNode.getActualCost() + 1;
        }else {// 如果父节点是空,说明当前节点是第一个结点
            this.actualCost = 0;
        }

        this.heuristicCost = Math.abs(this.xCoordinate - dest.getXCoordinate()) + Math.abs(this.yCoordinate - dest.getYCoordinate());
        this.totalCost = this.actualCost + this.heuristicCost;
    }

    @Override
    public int compareTo(Node o) {
        return Integer.compare(this.totalCost,o.getTotalCost());
    }
}
