## Execução

1. Iniciar o servidor executando a classe ServerChat.java ou Main.java.
2. Em seguida, executar uma ou mais instâncias da classe ClientChat.java para iniciar os clientes e permitir a comunicação entre eles.

## Alunas

Giulia Rodrigues de Araújo – Bacharelado em Ciência da Computação (UFSM)
Vitória Luiza Camara – Bacharelado em Sistemas de Informação (UFSM)

## Trabalho 1: Chat Distribuído com gRPC e Protocol Buffers

Aplicação de chat distribuído com arquitetura cliente-servidor, desenvolvida em Java utilizando gRPC e Protocol Buffers.

## Tecnologias Utilizadas

Java  
gRPC  
Protocol Buffers (proto3)  

## Funcionalidades

Registro de usuários com nome único  
Sala única com múltiplos usuários  
Envio de mensagens com remetente, conteúdo e timestamp  
Recebimento de mensagens via streaming  
Notificação de entrada e saída de usuários  

## Arquitetura

A aplicação segue o contrato definido em contrato-chat.proto, utilizando:
-Comunicação Unary para registro e envio de mensagens  
-Comunicação Streaming para recebimento de mensagens em tempo real  