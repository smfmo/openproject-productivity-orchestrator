# OpenProject Productivity Orchestrator

Backend que sincroniza tasks do OpenProject, avalia regras de produtividade e envia notificações automáticas por e-mail, utilizando arquitetura hexagonal.

---

## Visão Geral

Este sistema atua como um **orquestrador de produtividade** — não como um gerenciador de tarefas. Ele consome dados do OpenProject, monitora tasks e registros de horas, aplica regras de produtividade e dispara notificações automáticas por e-mail quando violações são detectadas.

---

## Funcionalidades

- Sincronização automática de tasks do OpenProject via API REST
- Avaliação de regras de produtividade:
  - **Deadline Próximo** — task com prazo em até 3 dias e não concluída
  - **Task Parada** — task sem atualização há mais de 7 dias e não concluída
  - **Horas Não Registradas** — nenhum registro de horas no dia, avaliado após 18h
- Notificações automáticas por e-mail para cada violação detectada
- Jobs agendados sem necessidade de intervenção manual
- Endpoints REST para trigger manual e consulta de tasks

---

## Arquitetura

O projeto segue **Arquitetura Hexagonal** (Ports and Adapters) com módulo Maven único e separação por pacotes.

```
com.smfmo.integration.openproject
├── domain          # Regras de negócio puras — sem dependência de framework
│   ├── model       # Task, TimeEntry, RuleType, RuleViolation
│   └── service     # RuleEngine
├── ports           # Interfaces que definem o que o sistema faz e precisa
│   ├── in          # SyncTasksPort, RunRulesPort
│   └── out         # OpenProjectTaskPort, TaskRepositoryPort, NotificationPort
├── application     # Casos de uso que orquestram domínio e ports
│   └── usecase     # SyncTasksUseCase, RunRulesUseCase
└── adapters        # Implementações dos ports
    ├── in
    │   ├── web     # Controllers REST
    │   └── job     # Jobs agendados
    └── out
        ├── openproject   # RestClient → API OpenProject
        ├── persistence   # Spring Data JPA → PostgreSQL
        └── notification  # JavaMailSender → SMTP
```

**Regra de dependência:** `domain` não tem dependências externas. `application` depende apenas de `domain` e `ports`. `adapters` dependem de `ports` — nunca de `domain` diretamente.

---

## Stack Técnica

| Componente | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 4.0.5 |
| HTTP Client | Spring RestClient |
| Persistência | Spring Data JPA + Flyway |
| Banco de dados | PostgreSQL |
| Agendamento | Spring `@Scheduled` |
| Notificações | JavaMailSender (SMTP) |
| Build | Maven |

---

## Pré-requisitos

- Java 21+
- Maven 3.9+
- PostgreSQL 14+
- Instância OpenProject com API Key
- Conta Gmail com Senha de App (ou qualquer servidor SMTP)

---

## Configuração

### 1. Clone o repositório

```bash
git clone https://github.com/smfmo/openproject-productivity-orchestrator.git
cd openproject-productivity-orchestrator
```

### 2. Crie o banco de dados

```bash
psql -U postgres -c "CREATE DATABASE openproject;"
```

### 3. Configure as variáveis de ambiente

Crie um arquivo `.env` na raiz do projeto (nunca commite este arquivo):

```env
# OpenProject
OPENPROJECT_BASE_URL=https://sua-instancia.openproject.com
OPENPROJECT_API_KEY=sua-api-key-aqui

# Banco de dados
DB_USERNAME=postgres
DB_PASSWORD=sua-senha-postgres

# SMTP (exemplo Gmail)
MAIL_USERNAME=seu-email@gmail.com
MAIL_PASSWORD=sua-senha-de-app

# Destinatário das notificações
NOTIFICATION_RECIPIENT=seu-email@gmail.com

# Agendamento (opcional — padrões abaixo)
SCHEDULER_SYNC_CRON=0 0 * * * *
SCHEDULER_RULES_CRON=0 30 * * * *
```

> **Senha de App Gmail:** Acesse Conta Google → Segurança → Verificação em duas etapas → Senhas de app. Gere uma senha para "E-mail".

> **API Key OpenProject:** Acesse sua instância → Avatar → Minha conta → Tokens de acesso → Token de acesso à API → Gerar.

### 4. Carregue o `.env` no IntelliJ

**Run → Edit Configurations → Spring Boot → Application → Environment variables** → selecione o arquivo `.env`.

### 5. Inicie a aplicação

```bash
./mvnw spring-boot:run
```

Na inicialização, o Flyway criará automaticamente as tabelas:
- `tasks`
- `time_entries`

---

## Endpoints REST

### Sincronizar tasks manualmente
```
POST /sync
```
Busca todas as tasks atribuídas ao usuário autenticado no OpenProject e persiste localmente.

### Listar tasks sincronizadas
```
GET /tasks
```
Retorna todas as tasks armazenadas no banco local.

### Buscar task por ID
```
GET /tasks/{id}
```
Retorna uma task pelo ID do OpenProject.

### Executar regras manualmente
```
POST /rules/run
```
Avalia todas as regras de produtividade e envia notificações por e-mail para cada violação encontrada.

---

## Jobs Agendados

| Job | Agendamento Padrão | Descrição |
|---|---|---|
| `SyncJob` | Todo hora aos :00 | Sincroniza tasks do OpenProject |
| `RulesJob` | Todo hora aos :30 | Avalia regras e envia notificações |

Os agendamentos são configuráveis via `SCHEDULER_SYNC_CRON` e `SCHEDULER_RULES_CRON` usando expressões cron padrão (6 campos: segundo minuto hora dia mês dia-da-semana).

---

## Regras de Produtividade

| Regra | Condição | Escopo |
|---|---|---|
| `DEADLINE_APPROACHING` | Prazo ≤ hoje + 3 dias e progresso < 100% | Por task |
| `STALE_TASK` | Sem atualização há mais de 7 dias e progresso < 100% | Por task |
| `MISSING_HOURS` | Nenhuma hora registrada hoje, avaliado após 18h | Global |

---

## Executando os Testes

```bash
./mvnw test
```

Cobertura de testes:
- `RuleEngineTest` — todas as regras de produtividade
- `SyncTasksUseCaseTest` — fluxo de sincronização de tasks
- `RunRulesUseCaseTest` — fluxo de avaliação de regras e notificação
- `TaskJpaAdapterTest` — mapeamento de persistência

---

## Estrutura do Projeto

```
openproject/
├── src/
│   ├── main/
│   │   ├── java/com/smfmo/integration/openproject/
│   │   └── resources/
│   │       ├── application.yaml
│   │       └── db/migration/
│   │           ├── V1__create_tasks.sql
│   │           └── V2__create_time_entries.sql
│   └── test/
│       └── java/com/smfmo/integration/openproject/
├── docs/
│   └── superpowers/
│       ├── specs/   # Especificação de design
│       └── plans/   # Plano de implementação
├── .env             # Variáveis de ambiente locais (não commitado)
├── .gitignore
└── pom.xml
```

---

## Evoluções Futuras

- Webhooks para atualizações em tempo real
- Notificações via Telegram / WhatsApp
- Motor de regras configurável via interface
- Dashboard frontend
- Análise de produtividade

---

## Autor

**Samuel Monteiro Ferreira** — [SEA Tecnologia](https://github.com/smfmo)
