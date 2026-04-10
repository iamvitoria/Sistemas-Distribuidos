package org.example;

import elc1018.grpc.chat.protos.*;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServiceImpl extends ChatServiceGrpc.ChatServiceImplBase {

    // Guarda os usuários ativos e seus canais de comunicação (RFA01 e RFA05)
    private static final ConcurrentHashMap<String, StreamObserver<ChatMessage>> observers = new ConcurrentHashMap<>();

    @Override
    public void register(User request, StreamObserver<RegisterResponse> responseObserver) {
        String username = request.getUsername();
        boolean success = false;

        // RFA01: Verificar se o nome é único
        if (!username.isEmpty() && !observers.containsKey(username)) {
            success = true;
            System.out.println("Usuário registrado: " + username);
        }

        RegisterResponse response = RegisterResponse.newBuilder()
                .setSuccess(success)
                .setUsername(username)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void receiveMessages(User request, StreamObserver<ChatMessage> responseObserver) {
        // RFA05: Armazena o observer para enviar mensagens depois
        observers.put(request.getUsername(), responseObserver);

        // Notifica que alguém entrou (RFA07)
        broadcast(request.getUsername(), "entrou no chat!");
    }

    @Override
    public void sendMessage(ChatMessage request, StreamObserver<Ack> responseObserver) {
        // RFA03/RFA04: Repassa a mensagem para todos
        broadcast(request.getFrom(), request.getContent());

        responseObserver.onNext(Ack.newBuilder().setSuccess(true).build());
        responseObserver.onCompleted();
    }

    private void broadcast(String from, String content) {
        ChatMessage message = ChatMessage.newBuilder()
                .setFrom(from)
                .setContent(content)
                .build();

        observers.forEach((user, observer) -> {
            try {
                observer.onNext(message);
            } catch (Exception e) {
                observers.remove(user); // Remove se o usuário caiu
            }
        });
    }
}