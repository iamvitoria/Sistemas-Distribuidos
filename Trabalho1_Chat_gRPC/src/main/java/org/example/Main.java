package org.example;

public class Main {
    public static void main(String[] args) {
        System.out.println("Iniciando o sistema de Chat gRPC...");
        try {
            // chama a Main do servidor
            ServerChat.main(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}