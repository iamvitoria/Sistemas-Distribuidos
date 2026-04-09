# Sistemas Distribuídos (ELC1018) - UFSM

Este repositório contém os projetos práticos desenvolvidos para a disciplina de **Sistemas Distribuídos (ELC1018)** da Universidade Federal de Santa Maria (UFSM), ministrada pelo Prof. Dr. Raul Ceretta Nunes.

O foco da disciplina é o estudo e implementação de sistemas funcionais, escaláveis e resilientes, explorando diferentes paradigmas de comunicação e middleware.

## 👥 Autoras

* **Giulia Rodrigues de Araújo** – Bacharelado em Ciência da Computação (UFSM)
* **Vitória Luiza Camara** – Bacharelado em Sistemas de Informação (UFSM)

---

## 🚀 Trabalho 1: Chat Distribuído com gRPC e Protocol Buffers

O primeiro projeto consistiu na implementação de uma aplicação de chat multiusuário, utilizando uma arquitetura cliente-servidor baseada em chamadas de procedimento remoto (RPC).

### 🛠️ Tecnologias Utilizadas
* **Java**: Linguagem base para o desenvolvimento do servidor e do cliente.
* **gRPC**: Framework de comunicação de alta performance.
* **Protocol Buffers (proto3)**: Utilizado para a definição do "contrato" de comunicação e serialização de dados.

### 📋 Requisitos e Funcionalidades
* **Registro de Usuários:** Validação de *username* único via chamada unária.
* **Sala Única:** O servidor gerencia uma única sala de chat para múltiplos usuários simultâneos.
* **Envio de Mensagens:** Mensagens contendo remetente, conteúdo e *timestamp*.
* **Recebimento via Streaming:** Utilização de *Server-side streaming* para manter a conexão ativa e receber mensagens em tempo real.
* **Notificações:** O sistema emite alertas quando usuários entram ou saem da sala.

### 🏗️ Arquitetura
O projeto segue rigorosamente o contrato definido em `contrato-chat.proto`, explorando os modelos de comunicação **Unary** (para registro e envio) e **Streaming** (para recebimento).

---

## 🚀 Trabalho 2: [Ainda não disponível]


---
