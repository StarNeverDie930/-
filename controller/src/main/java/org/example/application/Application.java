package org.example.application;

import org.example.controller.Controller;
import org.example.factory.ControllerFactory;
import org.example.utils.ConnectionUtil;
import java.util.Scanner;
import redis.clients.jedis.Jedis;

public class Application {
    public static void main(String[] args) {
        ControllerFactory controllerFactory = new ControllerFactory(true);
      
        controllerFactory.start();

        Scanner scanner = new Scanner(System.in);
      
        if (scanner.hasNext()){
            controllerFactory.setWork(false);
        }
    }
}
