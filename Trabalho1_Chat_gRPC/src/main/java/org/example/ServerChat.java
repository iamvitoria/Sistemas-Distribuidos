package org.example;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;

public class ServerChat {
    public static void main(String[] args) throws IOException, InterruptedException {
        // sobe o server na porta 51018 e adiciona o serviço de chat
        Server server = ServerBuilder.forPort(51018)
                .addService(new ChatServiceImpl())
                .build();

        System.out.println("=== SERVIDOR DE CHAT gRPC INICIADO ===");
        System.out.println("Aguardando conexões na porta 51018...");

        server.start();

        // faz o servidor ficar rodando infinitamente
        server.awaitTermination();
    }
}