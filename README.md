# Sistemas Distribuídos (ELC1018) - UFSM

Este repositório contém os projetos práticos desenvolvidos para a disciplina de **Sistemas Distribuídos (ELC1018)** da Universidade Federal de Santa Maria (UFSM), ministrada pelo Prof. Dr. Raul Ceretta Nunes.

O foco da disciplina é o estudo e implementação de sistemas funcionais, escaláveis e resilientes, explorando diferentes paradigmas de comunicação e middleware.

---

## 🚀 Trabalho 1: Chat Distribuído com gRPC e Protocol Buffers

O primeiro projeto consistiu na implementação de uma aplicação de chat multiusuário, utilizando uma arquitetura cliente-servidor baseada em chamadas de procedimento remoto (RPC).

## 👥 Autoras

* **Giulia Rodrigues de Araújo** – Bacharelado em Ciência da Computação (UFSM)
* **Vitória Luiza Camara** – Bacharelado em Sistemas de Informação (UFSM)

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

## 🚀 Trabalho 2: Middleware para Multicast Causal com Estabilização de Mensagens

O segundo projeto consiste na implementação de um middleware para comunicação multicast com ordenamento causal de mensagens e mecanismo de estabilização para descarte de mensagens do buffer utilizando vetores de relógios lógicos.

### 👥 Autoras

* **Bianca Sabrina Bublitz** – Bacharelado em Ciência da Computação (UFSM)
* **Vitória Luiza Camara** – Bacharelado em Sistemas de Informação (UFSM)

### 🛠️ Tecnologias Utilizadas

* **Java**
* **Sockets UDP**
* **IP Multicast**
* **Vetores de Relógios Lógicos (Vector Clocks)**

### 📋 Requisitos e Funcionalidades

* Comunicação multicast implementada sobre mensagens unicast UDP.
* Serviço de descoberta de participantes utilizando IP Multicast.
* Ordenamento causal de mensagens por meio de relógios vetoriais.
* Estabilização de mensagens para descarte seguro do buffer.
* Atualização dinâmica dos membros do grupo.
* Exibição contínua do conteúdo dos buffers e relógios lógicos.

### 🏗️ Arquitetura

O middleware é disponibilizado através do pacote `CausalMulticast`, oferecendo uma API para envio e recebimento de mensagens multicast com ordenamento causal. A solução implementa mecanismos de descoberta de participantes, controle causal de entrega e estabilização de mensagens conforme os algoritmos estudados na disciplina de Sistemas Distribuídos.

---
