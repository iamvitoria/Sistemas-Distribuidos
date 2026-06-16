# CausalMulticast

## Arquitetura do Middleware

O middleware CausalMulticast atua como uma camada intermediária entre a aplicação do usuário e a rede. A arquitetura foi desenvolvida utilizando comunicação assíncrona orientada a eventos.

O sistema utiliza sockets UDP não confiáveis (`DatagramSocket`) para simular multicast através de múltiplos envios unicast.

### Componentes principais

#### Serviço de Descoberta

Utiliza um `MulticastSocket` no grupo `230.0.0.1:4446`.

Duas threads são executadas:

* **Announcer:** envia periodicamente mensagens `DISCOVER`.
* **Listener:** recebe mensagens `DISCOVER` e adiciona novos participantes.

#### Comunicação

O envio multicast é realizado por múltiplos envios UDP individuais para todos os participantes conhecidos.

#### Aplicação Cliente

A aplicação implementa a interface `ICausalMulticast`, recebendo mensagens através do callback:

```java
public void deliver(String msg)
```

---

## Algoritmo de Ordenação Causal

A ordenação causal é baseada em Relógios Vetoriais.

### Envio

Ao executar:

```java
mcsend(String msg)
```

o processo:

1. Incrementa seu relógio vetorial local.
2. Copia o vetor.
3. Anexa o vetor à mensagem (piggyback).
4. Envia a mensagem aos participantes.

### Recepção

Ao receber uma mensagem, são verificadas duas condições:

### Condição 1

A mensagem deve ser a próxima esperada do remetente.

[
VC_m[sender] = VC_{local}[sender] + 1
]

### Condição 2

Nenhuma dependência causal pode estar ausente.

[
VC_m[k] \le VC_{local}[k]
]

para todo (k \neq sender).

### Buffer de Mensagens Pendentes

Caso alguma condição não seja satisfeita:

* a mensagem é armazenada em `messageBuffer`;
* ela não é entregue à aplicação;
* o buffer é reprocessado após cada nova entrega.

### Entrega

Quando as dependências são satisfeitas:

* a mensagem é entregue através de `deliver()`;
* o relógio vetorial local é atualizado;
* o buffer é reavaliado.

---

## Algoritmo de Estabilização

O descarte de mensagens utiliza Matrix Clocks.

### Estrutura

A matriz de relógios mantém o conhecimento que cada processo possui sobre os demais.

Cada linha representa um processo:

```
M[i]
```

Cada coluna representa o conhecimento sobre outro processo.

### Funcionamento

Após a entrega causal:

1. A mensagem é armazenada em `historyBuffer`.
2. A matriz local é atualizada.
3. O sistema procura o menor valor da coluna referente ao remetente.

### Critério de Estabilização

Uma mensagem é considerada estabilizada quando:

[
timestamp \le min(coluna)
]

Isso significa que todos os participantes já possuem conhecimento daquela mensagem.

### Descarte

Quando estabilizada:

* a mensagem é removida do `historyBuffer`;
* o evento é exibido no terminal.

Exemplo:

```text
[ESTABILIZAÇÃO] 1 mensagens foram estabilizadas e descartadas
 -> Descartada: A
```

---

## Funcionalidades Implementadas

### Middleware

* Descoberta automática de participantes
* Comunicação multicast via UDP unicast
* Entrada dinâmica de participantes
* Detecção de participantes inativos por timeout
* Entrega para si mesmo
* Atraso manual de mensagens
* Reenvio manual de mensagens atrasadas

### Ordenação Causal

* Relógio vetorial
* Piggyback do relógio
* Buffer de mensagens pendentes
* Verificação das dependências causais
* Entrega causal

### Estabilização

* Matrix Clock
* Histórico de mensagens
* Detecção de mensagens estabilizadas
* Descarte automático

---

## Instruções de Execução

### Compilação

```bash
javac */*.java
```

### Execução

Abrir três terminais:

```bash
java exemplo.ClienteTeste
```

Portas sugeridas:

| Processo | Porta |
| -------- | ----- |
| P1       | 5001  |
| P2       | 5002  |
| P3       | 5003  |

Aguardar aproximadamente 5 segundos para descoberta dos participantes.

### Teste da Ordenação Causal

1. P1 envia mensagem "A".
2. Atrasar envio para P3.
3. P2 recebe "A".
4. P2 envia mensagem "B".
5. Enviar "B" para P3.
6. P3 receberá "B" antes de "A".
7. A mensagem ficará armazenada no buffer.
8. Utilizar a opção de enviar mensagens atrasadas.
9. Enviar "A" para P3.
10. O middleware entregará primeiro "A" e depois liberará "B".

### Teste da Estabilização

1. Enviar mensagens normalmente entre os participantes.
2. Observar a atualização da matriz de relógios.
3. Verificar o surgimento da mensagem:

```text
[ESTABILIZAÇÃO] X mensagens foram estabilizadas e descartadas
```

4. Confirmar que as mensagens foram removidas do histórico.
