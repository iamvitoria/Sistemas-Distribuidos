package org.example;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;

public class ServerChat {
    public static void main(String[] args) throws IOException, InterruptedException {
        // Constrói o servidor na porta 50051 e adiciona o serviço de chat
        Server server = ServerBuilder.forPort(50051)
                .addService(new ChatServiceImpl())
                .build();

        System.out.println("=== SERVIDOR DE CHAT gRPC INICIADO ===");
        System.out.println("Aguardando conexões na porta 50051...");

        server.start();

        // Faz o servidor ficar rodando infinitamente
        server.awaitTermination();
    }
}