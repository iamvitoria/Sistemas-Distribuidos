package org.example;

import com.google.protobuf.Timestamp;
import elc1018.grpc.chat.protos.*;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServiceImpl extends ChatServiceGrpc.ChatServiceImplBase {

    private static final ConcurrentHashMap<String, StreamObserver<ChatMessage>> clients = new ConcurrentHashMap<>();

    @Override
    public void register(User request, StreamObserver<RegisterResponse> responseObserver) {
        String nome = request.getUsername();

        if (clients.containsKey(nome)) {
            // LOG NO SERVIDOR
            System.out.println("Servidor: Tentativa de login negada. Nome já em uso -> [" + nome + "]");

            responseObserver.onNext(RegisterResponse.newBuilder().setSuccess(false).build());
            responseObserver.onCompleted();
        } else {
            // LOG NO SERVIDOR
            System.out.println("Servidor: Novo usuário registrado com sucesso -> [" + nome + "]");

            responseObserver.onNext(RegisterResponse.newBuilder()
                    .setSuccess(true)
                    .setUsername(nome)
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void receiveMessages(User request, StreamObserver<ChatMessage> responseObserver) {
        String nome = request.getUsername();

        clients.put(nome, responseObserver);

        String msgEntrada = "O usuário [" + nome + "] entrou na sala.";
        // LOG NO SERVIDOR
        System.out.println("Servidor (Broadcast): " + msgEntrada);
        broadcastMessage("Sistema", msgEntrada);

        Context.current().addListener(context -> {
            if (context.isCancelled()) {
                clients.remove(nome);
                String msgSaida = "O usuário [" + nome + "] saiu da sala.";
                // LOG NO SERVIDOR
                System.out.println("Servidor (Broadcast): " + msgSaida);
                broadcastMessage("Sistema", msgSaida);
            }
        }, Runnable::run);
    }

    @Override
    public void sendMessage(ChatMessage request, StreamObserver<Ack> responseObserver) {
        // LOG NO SERVIDOR
        System.out.println("Servidor (Chat): [" + request.getFrom() + "] disse: " + request.getContent());

        broadcastMessage(request.getFrom(), request.getContent());

        Ack resposta = Ack.newBuilder()
                .setSuccess(true)
                .build();

        responseObserver.onNext(resposta);
        responseObserver.onCompleted();
    }

    private void broadcastMessage(String remetente, String conteudo) {
        Instant now = Instant.now();
        Timestamp ts = Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();

        ChatMessage msg = ChatMessage.newBuilder()
                .setFrom(remetente)
                .setContent(conteudo)
                .setTimestamp(ts)
                .build();

        for (StreamObserver<ChatMessage> client : clients.values()) {
            try {
                client.onNext(msg);
            } catch (Exception e) {
                // ignora exceções se tentar enviar para um cliente que caiu
            }
        }
    }
}