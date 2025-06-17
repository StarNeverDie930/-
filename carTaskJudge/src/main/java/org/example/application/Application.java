package org.example.application;

import org.example.factory.CarTaskJudgeFactory;

import java.util.Scanner;

public class Application {
    public static void main(String[] args) {

        CarTaskJudgeFactory carTaskJudgeFactory = new CarTaskJudgeFactory(true);



        carTaskJudgeFactory.start();


        Scanner scanner = new Scanner(System.in);
        if (scanner.hasNext()){

            carTaskJudgeFactory.setWork(false);

        }
    }
}
