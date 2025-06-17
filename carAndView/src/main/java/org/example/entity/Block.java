package org.example.entity;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class Block {
    private int x; // x坐标
    private int y; // y坐标
    private ImageIcon blockIcon; // 图片

    public Block(int x, int y) {
        super();
        this.x = x + 1;
        this.y = y + 1;
        try {
            this.blockIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/images/block.png")));
            if (blockIcon.getImageLoadStatus() != MediaTracker.COMPLETE) {
                System.err.println("Block icon loading failed: incomplete image at /images/block.png");
                blockIcon = null; // 回退到默认显示
            }
        } catch (Exception e) {
            System.err.println("Block icon loading failed: " + e.getMessage());
            blockIcon = null; // 回退到默认显示
        }
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public ImageIcon getBlockIcon() {
        return blockIcon;
    }
}