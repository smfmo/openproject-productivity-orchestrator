# Design Spec — Integração OpenProject + Arquitetura Hexagonal

**Data:** 2026-04-19  
**Autor:** Samuel Monteiro Ferreira  
**Status:** Aprovado

---

## 1. Objetivo

Backend que atua como **orquestrador de produtividade**: consome dados do OpenProject, persiste localmente, aplica regras de negócio e dispara notificações automáticas via e-mail.

---

## 2. Stack Técnica

| Componente | Tecnologia |
|---|---|
| Framework | Spring Boot 4.0.5 / Java 21 |
| Banco de dados | PostgreSQL |
| Migrations | Flyway |
| HTTP Client | Spring `RestClient` |
| Persistência | Spring Data JPA |
| Agendamento | Spring `@Scheduled` |
| Notificação | `JavaMailSender` (SMTP) |
| Arquitetura | Hexagonal (módulo único, separação por pacotes) |

---

## 3. Estrutura de Pacotes

```
com.smfmo.integration.openproject
├── domain
│   ├── model
│   │   ├── Task.java
│   │   └── TimeEntry.java
│   └── service
│       └── RuleEngine.java
├── application
│   └── usecase
│       ├── SyncTasksUseCase.java
│       └── RunRulesUseCase.java
├── ports
│   ├── in
│   │   ├── SyncTasksPort.java
│   │   └── RunRulesPort.java
│   └── out
│       ├── OpenProjectTaskPort.java
│       ├── TaskRepositoryPort.java
│       └── NotificationPort.java
└── adapters
    ├── in
    │   ├── web
    │   └── job
    └── out
        ├── openproject
        ├── persistence
        └── notification
```

**Regra de dependência:**
- `domain` não importa nada de fora de si mesmo
- `application` importa apenas `domain` e `ports`
- `adapters` importa `ports` e `application` — nunca `domain` diretamente

---

## 4. Domínio

### 4.1 Task
Entidade pura, sem anotações de framework.

```
Task {
    Long id
    String title
    String status
    String assigneeId
    LocalDate deadline
    LocalDateTime updatedAt
    int percentDone
}
```

### 4.2 TimeEntry
```
TimeEntry {
    Long id
    Long taskId
    double hours
    LocalDate date
}
```

### 4.3 RuleEngine
Recebe uma `Task` e a lista de `TimeEntry` do dia. Retorna `List<RuleViolation>`.

**Regras do MVP:**

| Regra | Condição |
|---|---|
| `DEADLINE_APPROACHING` | `deadline ≤ hoje + 3 dias` e `percentDone < 100` |
| `STALE_TASK` | `updatedAt` há mais de 7 dias e `percentDone < 100` |
| `MISSING_HOURS` | nenhuma hora registrada na data atual (regra avaliada apenas após 18h) |

**RuleViolation** é um value object: `{ RuleType type, String message, Long taskId }`.

O `RuleEngine` apenas retorna violações — não dispara nenhuma ação. Quem decide o que fazer é o `RunRulesUseCase`.

---

## 5. Portas (Interfaces)

### 5.1 Input Ports
```java
interface SyncTasksPort { void execute(); }
interface RunRulesPort  { void execute(); }
```

### 5.2 Output Ports
```java
interface OpenProjectTaskPort {
    List<Task> fetchAllTasks(String userId);
    Task fetchById(Long id);
    void updateStatus(Long id, String newStatus);
    List<TimeEntry> fetchTodayEntries(String userId);
    String fetchCurrentUserId();
}

interface TaskRepositoryPort {
    void saveAll(List<Task> tasks); // upsert: atualiza se existir, insere se não existir
    List<Task> findAll();
    Optional<Task> findById(Long id);
}

interface NotificationPort {
    void send(String subject, String body);
}
```

---

## 6. Casos de Uso

### SyncTasksUseCase
1. Busca o ID do usuário autenticado via `OpenProjectTaskPort`
2. Busca todas as tasks via `OpenProjectTaskPort`
3. Persiste localmente via `TaskRepositoryPort`

### RunRulesUseCase
1. Carrega tasks do repositório local
2. Busca time entries de hoje via `OpenProjectTaskPort`
3. Passa cada task + entries ao `RuleEngine`
4. Para cada `RuleViolation`, envia notificação via `NotificationPort`

---

## 7. Adaptadores

### 7.1 Saída (Outbound)

| Adapter | Tecnologia | Implementa |
|---|---|---|
| `OpenProjectRestAdapter` | `RestClient` + Basic Auth (API Key) | `OpenProjectTaskPort` |
| `TaskJpaAdapter` | Spring Data JPA | `TaskRepositoryPort` |
| `EmailNotificationAdapter` | `JavaMailSender` (SMTP) | `NotificationPort` |

**Autenticação OpenProject:** Basic Auth onde o usuário é a API Key e a senha é `apikey` (valor dummy conforme documentação OpenProject).

### 7.2 Entrada (Inbound)

| Adapter | Tecnologia | Responsabilidade |
|---|---|---|
| `SyncJob` | `@Scheduled` | Chama `SyncTasksPort` periodicamente |
| `RulesJob` | `@Scheduled` | Chama `RunRulesPort` periodicamente |
| `TaskController` | Spring MVC REST | Trigger manual e consulta de tasks |

---

## 8. Fluxo de Dados

```
[SyncJob @Scheduled]
    → SyncTasksUseCase
        → OpenProjectRestAdapter.fetchCurrentUserId()
        → OpenProjectRestAdapter.fetchAllTasks(userId)
        → TaskJpaAdapter.saveAll(tasks)

[RulesJob @Scheduled]
    → RunRulesUseCase
        → TaskJpaAdapter.findAll()
        → OpenProjectRestAdapter.fetchTodayEntries(userId)
        → RuleEngine.evaluate(task, entries) → List<RuleViolation>
        → EmailNotificationAdapter.send(subject, body)  [por violação]
```

---

## 9. Configuração (`application.yaml`)

```yaml
openproject:
  base-url: https://your-instance.openproject.com
  api-key: ${OPENPROJECT_API_KEY}

spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true

notification:
  recipient: smf.ferreira1901@gmail.com

scheduler:
  sync-cron: "0 0 * * * *"     # a cada hora
  rules-cron: "0 30 * * * *"   # meia hora após sync
```

Credenciais sensíveis são injetadas via variáveis de ambiente — nunca commitadas.

---

## 10. Persistência (Flyway)

### Migration V1 — `tasks`
Colunas: `id`, `title`, `status`, `assignee_id`, `deadline`, `updated_at`, `percent_done`

### Migration V2 — `time_entries`
Colunas: `id`, `task_id`, `hours`, `date`

---

## 11. Endpoints REST (MVP)

| Método | Path | Descrição |
|---|---|---|
| `GET` | `/tasks` | Lista tasks sincronizadas localmente |
| `GET` | `/tasks/{id}` | Detalha uma task |
| `POST` | `/sync` | Dispara sincronização manual |
| `POST` | `/rules/run` | Dispara execução manual das regras |

---

## 12. O que está fora do MVP

- Webhooks em tempo real
- Dashboard frontend
- Integração com Telegram / WhatsApp
- Motor de regras configurável via UI
- Projetos, comentários, anexos, custom fields

---

## 13. Fases de Desenvolvimento

| Fase | Entregável |
|---|---|
| 1 | Estrutura hexagonal + entidades de domínio |
| 2 | Cliente REST OpenProject + autenticação |
| 3 | Persistência JPA + Flyway migrations |
| 4 | Use case SyncTasks |
| 5 | RuleEngine + RunRules use case |
| 6 | Jobs `@Scheduled` |
| 7 | Notificação por e-mail |

---

## 14. Critérios de Conclusão do MVP

- [ ] Tasks sincronizadas automaticamente via job
- [ ] Pelo menos 1 regra detectada (`DEADLINE_APPROACHING`)
- [ ] Notificação por e-mail disparada para violação
- [ ] Job automatizado rodando sem intervenção manual
