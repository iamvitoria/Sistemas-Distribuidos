package org.example;

import elc1018.grpc.chat.protos.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.Scanner;

public class ClientChat {
    public static void main(String[] args) {
        // 1. Conecta ao servidor
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 50051)
                .usePlaintext()
                .build();

        // 2. Cria os Stubs (objetos de chamada)
        ChatServiceGrpc.ChatServiceBlockingStub blockingStub = ChatServiceGrpc.newBlockingStub(channel);
        ChatServiceGrpc.ChatServiceStub asyncStub = ChatServiceGrpc.newStub(channel);

        Scanner scanner = new Scanner(System.in);
        System.out.print("Escolha seu apelido para o chat: ");
        String nome = scanner.nextLine();

        // 3. Tenta Registrar (RFA01)
        User usuario = User.newBuilder().setUsername(nome).build();
        RegisterResponse res = blockingStub.register(usuario);

        if (res.getSuccess()) {
            System.out.println("Conectado com sucesso como: " + res.getUsername());

            // 4. Abre o canal de recebimento de mensagens (RFA05)
            asyncStub.receiveMessages(usuario, new StreamObserver<ChatMessage>() {
                @Override
                public void onNext(ChatMessage msg) {
                    // Só exibe se a mensagem não for minha
                    if (!msg.getFrom().equals(nome)) {
                        System.out.println("\n[" + msg.getFrom() + "]: " + msg.getContent());
                    }
                }
                @Override public void onError(Throwable t) { System.err.println("Erro na conexão."); }
                @Override public void onCompleted() { System.out.println("Chat encerrado."); }
            });

            // 5. Loop para enviar mensagens (RFA03)
            System.out.println("Pode começar a digitar (Pressione Enter para enviar):");
            while (true) {
                String texto = scanner.nextLine();
                if (!texto.isEmpty()) {
                    blockingStub.sendMessage(ChatMessage.newBuilder()
                            .setFrom(nome)
                            .setContent(texto)
                            .build());
                }
            }
        } else {
            System.out.println("ERRO: Este nome já está sendo usado ou é inválido.");
            channel.shutdown();
        }
    }
}