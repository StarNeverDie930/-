package org.example.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MD5加密工具类
 */
public class MD5Util {
    
    /**
     * MD5加密
     * @param input 需要加密的字符串
     * @return 加密后的字符串
     */
    public static String encrypt(String input) {
        try {
            // 获取MD5实例
            MessageDigest md = MessageDigest.getInstance("MD5");
            // 将输入转换为字节数组并更新消息摘要
            md.update(input.getBytes());
            // 获取加密后的字节数组
            byte[] digest = md.digest();
            // 将字节数组转换为十六进制字符串
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }
} 