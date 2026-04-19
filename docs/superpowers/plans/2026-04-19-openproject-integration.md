# OpenProject Integration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir um backend hexagonal que sincroniza tasks do OpenProject, avalia regras de produtividade e envia notificações por e-mail.

**Architecture:** Módulo Maven único com separação por pacotes hexagonais. Domínio é Java puro (records, sem anotações de framework). Adapters implementam ports e são injetados pelo Spring via construtor. `RuleEngine` recebe um `Clock` para testabilidade.

**Tech Stack:** Java 21, Spring Boot 4.0.5, Lombok, Spring RestClient, Spring Data JPA, Flyway, PostgreSQL, JavaMailSender (SMTP), Spring `@Scheduled`

---

## File Map

| Arquivo | Responsabilidade |
|---|---|
| `pom.xml` | Adicionar `spring-boot-starter-mail` |
| `domain/model/Task.java` | Record de domínio — task do OpenProject |
| `domain/model/TimeEntry.java` | Record de domínio — registro de horas |
| `domain/model/RuleType.java` | Enum dos tipos de violação |
| `domain/model/RuleViolation.java` | Value object — resultado de uma regra |
| `domain/service/RuleEngine.java` | Avalia regras, retorna violações |
| `ports/in/SyncTasksPort.java` | Interface: sincronizar tasks |
| `ports/in/RunRulesPort.java` | Interface: executar regras |
| `ports/out/OpenProjectTaskPort.java` | Interface: acesso à API OpenProject |
| `ports/out/TaskRepositoryPort.java` | Interface: persistência de tasks |
| `ports/out/NotificationPort.java` | Interface: envio de notificações |
| `application/usecase/SyncTasksUseCase.java` | Orquestra sincronização |
| `application/usecase/RunRulesUseCase.java` | Orquestra execução de regras |
| `adapters/config/DomainConfig.java` | @Bean para RuleEngine (com Clock) |
| `adapters/out/persistence/TaskEntity.java` | Entidade JPA — tabela `tasks` |
| `adapters/out/persistence/TaskJpaRepository.java` | Spring Data repository |
| `adapters/out/persistence/TaskJpaAdapter.java` | Implementa TaskRepositoryPort |
| `adapters/out/openproject/config/OpenProjectProperties.java` | @ConfigurationProperties — URL e API Key |
| `adapters/out/openproject/config/OpenProjectClientConfig.java` | Bean RestClient com Basic Auth |
| `adapters/out/openproject/dto/WorkPackageListResponse.java` | DTO — listagem de work packages |
| `adapters/out/openproject/dto/WorkPackageResponse.java` | DTO — um work package |
| `adapters/out/openproject/dto/TimeEntryListResponse.java` | DTO — listagem de time entries |
| `adapters/out/openproject/dto/TimeEntryResponse.java` | DTO — um time entry |
| `adapters/out/openproject/dto/CurrentUserResponse.java` | DTO — usuário atual |
| `adapters/out/openproject/OpenProjectRestAdapter.java` | Implementa OpenProjectTaskPort |
| `adapters/out/notification/NotificationProperties.java` | @ConfigurationProperties — destinatário |
| `adapters/out/notification/EmailNotificationAdapter.java` | Implementa NotificationPort |
| `adapters/in/job/SyncJob.java` | @Scheduled — dispara SyncTasksPort |
| `adapters/in/job/RulesJob.java` | @Scheduled — dispara RunRulesPort |
| `adapters/in/web/TaskController.java` | REST controller |
| `resources/application.yaml` | Configuração completa |
| `resources/db/migration/V1__create_tasks.sql` | Tabela tasks |
| `resources/db/migration/V2__create_time_entries.sql` | Tabela time_entries |

Prefixo base de todos os arquivos Java: `src/main/java/com/smfmo/integration/openproject/`  
Prefixo base de todos os testes: `src/test/java/com/smfmo/integration/openproject/`

---

## Task 1: Adicionar dependência spring-boot-starter-mail ao pom.xml

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Adicionar a dependência no bloco `<dependencies>`**

Em `pom.xml`, após a dependência do `postgresql`, adicione:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

- [ ] **Step 2: Verificar que o projeto compila**

```bash
./mvnw compile -q
```

Esperado: BUILD SUCCESS (sem erros de compilação)

- [ ] **Step 3: Commit**

```bash
git init
git add pom.xml
git commit -m "chore: add spring-boot-starter-mail dependency"
```

---

## Task 2: Domínio — Task, TimeEntry, RuleType, RuleViolation

**Files:**
- Create: `src/main/java/com/smfmo/integration/openproject/domain/model/Task.java`
- Create: `src/main/java/com/smfmo/integration/openproject/domain/model/TimeEntry.java`
- Create: `src/main/java/com/smfmo/integration/openproject/domain/model/RuleType.java`
- Create: `src/main/java/com/smfmo/integration/openproject/domain/model/RuleViolation.java`

- [ ] **Step 1: Criar Task.java**

```java
package com.smfmo.integration.openproject.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record Task(
    Long id,
    String title,
    String status,
    String assigneeId,
    LocalDate deadline,
    LocalDateTime updatedAt,
    int percentDone
) {}
```

- [ ] **Step 2: Criar TimeEntry.java**

```java
package com.smfmo.integration.openproject.domain.model;

import java.time.LocalDate;

public record TimeEntry(
    Long id,
    Long taskId,
    double hours,
    LocalDate date
) {}
```

- [ ] **Step 3: Criar RuleType.java**

```java
package com.smfmo.integration.openproject.domain.model;

public enum RuleType {
    DEADLINE_APPROACHING,
    STALE_TASK,
    MISSING_HOURS
}
```

- [ ] **Step 4: Criar RuleViolation.java**

```java
package com.smfmo.integration.openproject.domain.model;

public record RuleViolation(
    RuleType type,
    String message,
    Long taskId  // null para violações globais (ex: MISSING_HOURS)
) {}
```

- [ ] **Step 5: Compilar**

```bash
./mvnw compile -q
```

Esperado: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/smfmo/integration/openproject/domain/
git commit -m "feat: add domain model records (Task, TimeEntry, RuleType, RuleViolation)"
```

---

## Task 3: Ports — todas as interfaces

**Files:**
- Create: `src/main/java/com/smfmo/integration/openproject/ports/in/SyncTasksPort.java`
- Create: `src/main/java/com/smfmo/integration/openproject/ports/in/RunRulesPort.java`
- Create: `src/main/java/com/smfmo/integration/openproject/ports/out/OpenProjectTaskPort.java`
- Create: `src/main/java/com/smfmo/integration/openproject/ports/out/TaskRepositoryPort.java`
- Create: `src/main/java/com/smfmo/integration/openproject/ports/out/NotificationPort.java`

- [ ] **Step 1: Criar SyncTasksPort.java**

```java
package com.smfmo.integration.openproject.ports.in;

public interface SyncTasksPort {
    void execute();
}
```

- [ ] **Step 2: Criar RunRulesPort.java**

```java
package com.smfmo.integration.openproject.ports.in;

public interface RunRulesPort {
    void execute();
}
```

- [ ] **Step 3: Criar OpenProjectTaskPort.java**

```java
package com.smfmo.integration.openproject.ports.out;

import com.smfmo.integration.openproject.domain.model.Task;
import com.smfmo.integration.openproject.domain.model.TimeEntry;

import java.util.List;

public interface OpenProjectTaskPort {
    String fetchCurrentUserId();
    List<Task> fetchAllTasks(String userId);
    List<TimeEntry> fetchTodayEntries(String userId);
}
```

- [ ] **Step 4: Criar TaskRepositoryPort.java**

```java
package com.smfmo.integration.openproject.ports.out;

import com.smfmo.integration.openproject.domain.model.Task;

import java.util.List;
import java.util.Optional;

public interface TaskRepositoryPort {
    void saveAll(List<Task> tasks); // upsert: atualiza se existir, insere se não
    List<Task> findAll();
    Optional<Task> findById(Long id);
}
```

- [ ] **Step 5: Criar NotificationPort.java**

```java
package com.smfmo.integration.openproject.ports.out;

public interface NotificationPort {
    void send(String subject, String body);
}
```

- [ ] **Step 6: Compilar**

```bash
./mvnw compile -q
```

Esperado: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/smfmo/integration/openproject/ports/
git commit -m "feat: add input and output ports"
```

---

## Task 4: RuleEngine — TDD

**Files:**
- Create: `src/main/java/com/smfmo/integration/openproject/domain/service/RuleEngine.java`
- Test: `src/test/java/com/smfmo/integration/openproject/domain/service/RuleEngineTest.java`

O `RuleEngine` tem dois métodos:
- `evaluateTask(Task)` → regras por task (DEADLINE_APPROACHING, STALE_TASK)
- `evaluateGlobal(List<TimeEntry>)` → regras globais (MISSING_HOURS, avaliada após 18h)

Recebe um `Clock` no construtor para permitir testes sem dependência do relógio real.

- [ ] **Step 1: Criar o arquivo de teste**

```java
package com.smfmo.integration.openproject.domain.service;

import com.smfmo.integration.openproject.domain.model.*;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineTest {

    // Clock fixo: 2026-04-19 às 19:00 (após 18h, num domingo)
    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-04-19T19:00:00Z"),
        ZoneId.systemDefault()
    );

    private final RuleEngine ruleEngine = new RuleEngine(FIXED_CLOCK);

    private Task taskWith(LocalDate deadline, LocalDateTime updatedAt, int percentDone) {
        return new Task(1L, "Test Task", "open", "user1", deadline, updatedAt, percentDone);
    }

    @Test
    void deadlineApproaching_whenDeadlineWithin3Days() {
        Task task = taskWith(LocalDate.now(FIXED_CLOCK).plusDays(2), LocalDateTime.now(FIXED_CLOCK), 50);

        List<RuleViolation> violations = ruleEngine.evaluateTask(task);

        assertThat(violations).anyMatch(v -> v.type() == RuleType.DEADLINE_APPROACHING);
    }

    @Test
    void noDeadlineApproaching_whenDeadlineBeyond3Days() {
        Task task = taskWith(LocalDate.now(FIXED_CLOCK).plusDays(5), LocalDateTime.now(FIXED_CLOCK), 50);

        List<RuleViolation> violations = ruleEngine.evaluateTask(task);

        assertThat(violations).noneMatch(v -> v.type() == RuleType.DEADLINE_APPROACHING);
    }

    @Test
    void noDeadlineApproaching_whenTaskComplete() {
        Task task = taskWith(LocalDate.now(FIXED_CLOCK).plusDays(1), LocalDateTime.now(FIXED_CLOCK), 100);

        List<RuleViolation> violations = ruleEngine.evaluateTask(task);

        assertThat(violations).noneMatch(v -> v.type() == RuleType.DEADLINE_APPROACHING);
    }

    @Test
    void noDeadlineApproaching_whenDeadlineIsNull() {
        Task task = taskWith(null, LocalDateTime.now(FIXED_CLOCK), 50);

        List<RuleViolation> violations = ruleEngine.evaluateTask(task);

        assertThat(violations).noneMatch(v -> v.type() == RuleType.DEADLINE_APPROACHING);
    }

    @Test
    void staleTask_whenNotUpdatedFor7Days() {
        Task task = taskWith(null, LocalDateTime.now(FIXED_CLOCK).minusDays(8), 50);

        List<RuleViolation> violations = ruleEngine.evaluateTask(task);

        assertThat(violations).anyMatch(v -> v.type() == RuleType.STALE_TASK);
    }

    @Test
    void noStaleTask_whenRecentlyUpdated() {
        Task task = taskWith(null, LocalDateTime.now(FIXED_CLOCK).minusDays(3), 50);

        List<RuleViolation> violations = ruleEngine.evaluateTask(task);

        assertThat(violations).noneMatch(v -> v.type() == RuleType.STALE_TASK);
    }

    @Test
    void missingHours_whenNoEntriesAfter18h() {
        // FIXED_CLOCK está em 19h — deve detectar MISSING_HOURS
        List<RuleViolation> violations = ruleEngine.evaluateGlobal(List.of());

        assertThat(violations).anyMatch(v -> v.type() == RuleType.MISSING_HOURS);
    }

    @Test
    void noMissingHours_whenEntriesExist() {
        TimeEntry entry = new TimeEntry(1L, 10L, 2.0, LocalDate.now(FIXED_CLOCK));

        List<RuleViolation> violations = ruleEngine.evaluateGlobal(List.of(entry));

        assertThat(violations).noneMatch(v -> v.type() == RuleType.MISSING_HOURS);
    }

    @Test
    void noMissingHours_whenBefore18h() {
        Clock earlyMorning = Clock.fixed(
            Instant.parse("2026-04-19T10:00:00Z"),
            ZoneId.systemDefault()
        );
        RuleEngine earlyEngine = new RuleEngine(earlyMorning);

        List<RuleViolation> violations = earlyEngine.evaluateGlobal(List.of());

        assertThat(violations).noneMatch(v -> v.type() == RuleType.MISSING_HOURS);
    }
}
```

- [ ] **Step 2: Executar os testes para confirmar que falham**

```bash
./mvnw test -Dtest=RuleEngineTest
```

Esperado: FAIL — `RuleEngine` não existe ainda

- [ ] **Step 3: Criar RuleEngine.java**

```java
package com.smfmo.integration.openproject.domain.service;

import com.smfmo.integration.openproject.domain.model.*;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

public class RuleEngine {

    private final Clock clock;

    public RuleEngine(Clock clock) {
        this.clock = clock;
    }

    public List<RuleViolation> evaluateTask(Task task) {
        List<RuleViolation> violations = new ArrayList<>();
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);

        if (task.deadline() != null
                && !task.deadline().isAfter(today.plusDays(3))
                && task.percentDone() < 100) {
            violations.add(new RuleViolation(
                RuleType.DEADLINE_APPROACHING,
                "Task '%s' vence em %s".formatted(task.title(), task.deadline()),
                task.id()
            ));
        }

        if (task.updatedAt() != null
                && task.updatedAt().isBefore(now.minusDays(7))
                && task.percentDone() < 100) {
            violations.add(new RuleViolation(
                RuleType.STALE_TASK,
                "Task '%s' sem atualização há mais de 7 dias".formatted(task.title()),
                task.id()
            ));
        }

        return violations;
    }

    public List<RuleViolation> evaluateGlobal(List<TimeEntry> todayEntries) {
        List<RuleViolation> violations = new ArrayList<>();
        LocalTime now = LocalTime.now(clock);

        if (now.isAfter(LocalTime.of(18, 0)) && todayEntries.isEmpty()) {
            violations.add(new RuleViolation(
                RuleType.MISSING_HOURS,
                "Nenhuma hora registrada hoje",
                null
            ));
        }

        return violations;
    }
}
```

- [ ] **Step 4: Executar os testes para confirmar que passam**

```bash
./mvnw test -Dtest=RuleEngineTest
```

Esperado: BUILD SUCCESS — todos os testes passando

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/smfmo/integration/openproject/domain/service/RuleEngine.java \
        src/test/java/com/smfmo/integration/openproject/domain/service/RuleEngineTest.java
git commit -m "feat: add RuleEngine with DEADLINE_APPROACHING, STALE_TASK, MISSING_HOURS rules"
```

---

## Task 5: SyncTasksUseCase — TDD

**Files:**
- Create: `src/main/java/com/smfmo/integration/openproject/application/usecase/SyncTasksUseCase.java`
- Test: `src/test/java/com/smfmo/integration/openproject/application/usecase/SyncTasksUseCaseTest.java`

- [ ] **Step 1: Criar o arquivo de teste**

```java
package com.smfmo.integration.openproject.application.usecase;

import com.smfmo.integration.openproject.domain.model.Task;
import com.smfmo.integration.openproject.ports.out.OpenProjectTaskPort;
import com.smfmo.integration.openproject.ports.out.TaskRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncTasksUseCaseTest {

    @Mock
    private OpenProjectTaskPort openProjectTaskPort;

    @Mock
    private TaskRepositoryPort taskRepositoryPort;

    @InjectMocks
    private SyncTasksUseCase syncTasksUseCase;

    @Test
    void execute_fetchesTasksAndPersistsThem() {
        String userId = "42";
        List<Task> tasks = List.of(
            new Task(1L, "Task A", "open", userId, LocalDate.now(), LocalDateTime.now(), 0)
        );
        when(openProjectTaskPort.fetchCurrentUserId()).thenReturn(userId);
        when(openProjectTaskPort.fetchAllTasks(userId)).thenReturn(tasks);

        syncTasksUseCase.execute();

        verify(openProjectTaskPort).fetchCurrentUserId();
        verify(openProjectTaskPort).fetchAllTasks(userId);
        verify(taskRepositoryPort).saveAll(tasks);
    }
}
```

- [ ] **Step 2: Executar para confirmar falha**

```bash
./mvnw test -Dtest=SyncTasksUseCaseTest
```

Esperado: FAIL — `SyncTasksUseCase` não existe

- [ ] **Step 3: Criar SyncTasksUseCase.java**

```java
package com.smfmo.integration.openproject.application.usecase;

import com.smfmo.integration.openproject.domain.model.Task;
import com.smfmo.integration.openproject.ports.in.SyncTasksPort;
import com.smfmo.integration.openproject.ports.out.OpenProjectTaskPort;
import com.smfmo.integration.openproject.ports.out.TaskRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SyncTasksUseCase implements SyncTasksPort {

    private final OpenProjectTaskPort openProjectTaskPort;
    private final TaskRepositoryPort taskRepositoryPort;

    public SyncTasksUseCase(OpenProjectTaskPort openProjectTaskPort,
                            TaskRepositoryPort taskRepositoryPort) {
        this.openProjectTaskPort = openProjectTaskPort;
        this.taskRepositoryPort = taskRepositoryPort;
    }

    @Override
    public void execute() {
        String userId = openProjectTaskPort.fetchCurrentUserId();
        List<Task> tasks = openProjectTaskPort.fetchAllTasks(userId);
        taskRepositoryPort.saveAll(tasks);
    }
}
```

- [ ] **Step 4: Executar para confirmar que passa**

```bash
./mvnw test -Dtest=SyncTasksUseCaseTest
```

Esperado: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/smfmo/integration/openproject/application/usecase/SyncTasksUseCase.java \
        src/test/java/com/smfmo/integration/openproject/application/usecase/SyncTasksUseCaseTest.java
git commit -m "feat: add SyncTasksUseCase"
```

---

## Task 6: RunRulesUseCase — TDD

**Files:**
- Create: `src/main/java/com/smfmo/integration/openproject/application/usecase/RunRulesUseCase.java`
- Test: `src/test/java/com/smfmo/integration/openproject/application/usecase/RunRulesUseCaseTest.java`

- [ ] **Step 1: Criar o arquivo de teste**

```java
package com.smfmo.integration.openproject.application.usecase;

import com.smfmo.integration.openproject.domain.model.*;
import com.smfmo.integration.openproject.domain.service.RuleEngine;
import com.smfmo.integration.openproject.ports.out.NotificationPort;
import com.smfmo.integration.openproject.ports.out.OpenProjectTaskPort;
import com.smfmo.integration.openproject.ports.out.TaskRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RunRulesUseCaseTest {

    @Mock private OpenProjectTaskPort openProjectTaskPort;
    @Mock private TaskRepositoryPort taskRepositoryPort;
    @Mock private RuleEngine ruleEngine;
    @Mock private NotificationPort notificationPort;

    @InjectMocks
    private RunRulesUseCase runRulesUseCase;

    private Task task(Long id) {
        return new Task(id, "Task " + id, "open", "user1",
            LocalDate.now().plusDays(1), LocalDateTime.now(), 50);
    }

    @Test
    void execute_sendsNotificationForEachTaskViolation() {
        String userId = "42";
        Task task = task(1L);
        RuleViolation violation = new RuleViolation(RuleType.DEADLINE_APPROACHING, "deadline", 1L);

        when(openProjectTaskPort.fetchCurrentUserId()).thenReturn(userId);
        when(taskRepositoryPort.findAll()).thenReturn(List.of(task));
        when(openProjectTaskPort.fetchTodayEntries(userId)).thenReturn(List.of());
        when(ruleEngine.evaluateTask(task)).thenReturn(List.of(violation));
        when(ruleEngine.evaluateGlobal(anyList())).thenReturn(List.of());

        runRulesUseCase.execute();

        verify(notificationPort).send(anyString(), anyString());
    }

    @Test
    void execute_sendsNotificationForGlobalViolation() {
        String userId = "42";
        RuleViolation globalViolation = new RuleViolation(RuleType.MISSING_HOURS, "sem horas", null);

        when(openProjectTaskPort.fetchCurrentUserId()).thenReturn(userId);
        when(taskRepositoryPort.findAll()).thenReturn(List.of());
        when(openProjectTaskPort.fetchTodayEntries(userId)).thenReturn(List.of());
        when(ruleEngine.evaluateGlobal(anyList())).thenReturn(List.of(globalViolation));

        runRulesUseCase.execute();

        verify(notificationPort).send(anyString(), anyString());
    }

    @Test
    void execute_doesNotNotify_whenNoViolations() {
        String userId = "42";
        Task task = task(1L);

        when(openProjectTaskPort.fetchCurrentUserId()).thenReturn(userId);
        when(taskRepositoryPort.findAll()).thenReturn(List.of(task));
        when(openProjectTaskPort.fetchTodayEntries(userId)).thenReturn(List.of());
        when(ruleEngine.evaluateTask(task)).thenReturn(List.of());
        when(ruleEngine.evaluateGlobal(anyList())).thenReturn(List.of());

        runRulesUseCase.execute();

        verify(notificationPort, never()).send(anyString(), anyString());
    }
}
```

- [ ] **Step 2: Executar para confirmar falha**

```bash
./mvnw test -Dtest=RunRulesUseCaseTest
```

Esperado: FAIL

- [ ] **Step 3: Criar RunRulesUseCase.java**

```java
package com.smfmo.integration.openproject.application.usecase;

import com.smfmo.integration.openproject.domain.model.RuleViolation;
import com.smfmo.integration.openproject.domain.model.Task;
import com.smfmo.integration.openproject.domain.model.TimeEntry;
import com.smfmo.integration.openproject.domain.service.RuleEngine;
import com.smfmo.integration.openproject.ports.in.RunRulesPort;
import com.smfmo.integration.openproject.ports.out.NotificationPort;
import com.smfmo.integration.openproject.ports.out.OpenProjectTaskPort;
import com.smfmo.integration.openproject.ports.out.TaskRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RunRulesUseCase implements RunRulesPort {

    private final OpenProjectTaskPort openProjectTaskPort;
    private final TaskRepositoryPort taskRepositoryPort;
    private final RuleEngine ruleEngine;
    private final NotificationPort notificationPort;

    public RunRulesUseCase(OpenProjectTaskPort openProjectTaskPort,
                           TaskRepositoryPort taskRepositoryPort,
                           RuleEngine ruleEngine,
                           NotificationPort notificationPort) {
        this.openProjectTaskPort = openProjectTaskPort;
        this.taskRepositoryPort = taskRepositoryPort;
        this.ruleEngine = ruleEngine;
        this.notificationPort = notificationPort;
    }

    @Override
    public void execute() {
        String userId = openProjectTaskPort.fetchCurrentUserId();
        List<Task> tasks = taskRepositoryPort.findAll();
        List<TimeEntry> todayEntries = openProjectTaskPort.fetchTodayEntries(userId);

        for (Task task : tasks) {
            ruleEngine.evaluateTask(task)
                .forEach(v -> notificationPort.send(subjectFor(v), bodyFor(v)));
        }

        ruleEngine.evaluateGlobal(todayEntries)
            .forEach(v -> notificationPort.send(subjectFor(v), bodyFor(v)));
    }

    private String subjectFor(RuleViolation v) {
        return "[OpenProject] " + v.type().name();
    }

    private String bodyFor(RuleViolation v) {
        return v.message();
    }
}
```

- [ ] **Step 4: Executar para confirmar que passa**

```bash
./mvnw test -Dtest=RunRulesUseCaseTest
```

Esperado: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/smfmo/integration/openproject/application/usecase/RunRulesUseCase.java \
        src/test/java/com/smfmo/integration/openproject/application/usecase/RunRulesUseCaseTest.java
git commit -m "feat: add RunRulesUseCase"
```

---

## Task 7: Flyway migrations

**Files:**
- Create: `src/main/resources/db/migration/V1__create_tasks.sql`
- Create: `src/main/resources/db/migration/V2__create_time_entries.sql`

- [ ] **Step 1: Criar V1__create_tasks.sql**

```sql
CREATE TABLE tasks (
    id           BIGINT       NOT NULL,
    title        VARCHAR(500) NOT NULL,
    status       VARCHAR(100) NOT NULL,
    assignee_id  VARCHAR(100),
    deadline     DATE,
    updated_at   TIMESTAMP    NOT NULL,
    percent_done INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_tasks PRIMARY KEY (id)
);
```

- [ ] **Step 2: Criar V2__create_time_entries.sql**

```sql
CREATE TABLE time_entries (
    id      BIGINT         NOT NULL,
    task_id BIGINT,
    hours   DECIMAL(10, 2) NOT NULL,
    date    DATE           NOT NULL,
    CONSTRAINT pk_time_entries PRIMARY KEY (id)
);
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/
git commit -m "feat: add Flyway migrations for tasks and time_entries"
```

---

## Task 8: TaskEntity, TaskJpaRepository e TaskJpaAdapter

**Files:**
- Create: `src/main/java/com/smfmo/integration/openproject/adapters/out/persistence/TaskEntity.java`
- Create: `src/main/java/com/smfmo/integration/openproject/adapters/out/persistence/TaskJpaRepository.java`
- Create: `src/main/java/com/smfmo/integration/openproject/adapters/out/persistence/TaskJpaAdapter.java`
- Test: `src/test/java/com/smfmo/integration/openproject/adapters/out/persistence/TaskJpaAdapterTest.java`

- [ ] **Step 1: Criar TaskEntity.java**

```java
package com.smfmo.integration.openproject.adapters.out.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskEntity {

    @Id
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 100)
    private String status;

    @Column(name = "assignee_id", length = 100)
    private String assigneeId;

    private LocalDate deadline;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "percent_done", nullable = false)
    private int percentDone;
}
```

- [ ] **Step 2: Criar TaskJpaRepository.java**

```java
package com.smfmo.integration.openproject.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskJpaRepository extends JpaRepository<TaskEntity, Long> {
}
```

- [ ] **Step 3: Criar o arquivo de teste**

```java
package com.smfmo.integration.openproject.adapters.out.persistence;

import com.smfmo.integration.openproject.domain.model.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskJpaAdapterTest {

    @Mock
    private TaskJpaRepository taskJpaRepository;

    @InjectMocks
    private TaskJpaAdapter taskJpaAdapter;

    private Task task(Long id) {
        return new Task(id, "Task " + id, "open", "user1",
            LocalDate.now(), LocalDateTime.now(), 50);
    }

    @Test
    void saveAll_delegatesToRepository() {
        taskJpaAdapter.saveAll(List.of(task(1L), task(2L)));

        verify(taskJpaRepository).saveAll(anyList());
    }

    @Test
    void findAll_returnsMappedDomainTasks() {
        TaskEntity entity = TaskEntity.builder()
            .id(1L).title("Task 1").status("open").assigneeId("user1")
            .deadline(LocalDate.now()).updatedAt(LocalDateTime.now()).percentDone(50)
            .build();
        when(taskJpaRepository.findAll()).thenReturn(List.of(entity));

        List<Task> tasks = taskJpaAdapter.findAll();

        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).id()).isEqualTo(1L);
        assertThat(tasks.get(0).title()).isEqualTo("Task 1");
    }

    @Test
    void findById_returnsMappedTask_whenFound() {
        TaskEntity entity = TaskEntity.builder()
            .id(10L).title("Task 10").status("closed").assigneeId(null)
            .deadline(null).updatedAt(LocalDateTime.now()).percentDone(100)
            .build();
        when(taskJpaRepository.findById(10L)).thenReturn(Optional.of(entity));

        Optional<Task> result = taskJpaAdapter.findById(10L);

        assertThat(result).isPresent();
        assertThat(result.get().percentDone()).isEqualTo(100);
    }
}
```

- [ ] **Step 4: Executar para confirmar falha**

```bash
./mvnw test -Dtest=TaskJpaAdapterTest
```

Esperado: FAIL — `TaskJpaAdapter` não existe

- [ ] **Step 5: Criar TaskJpaAdapter.java**

```java
package com.smfmo.integration.openproject.adapters.out.persistence;

import com.smfmo.integration.openproject.domain.model.Task;
import com.smfmo.integration.openproject.ports.out.TaskRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class TaskJpaAdapter implements TaskRepositoryPort {

    private final TaskJpaRepository repository;

    public TaskJpaAdapter(TaskJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void saveAll(List<Task> tasks) {
        List<TaskEntity> entities = tasks.stream().map(this::toEntity).toList();
        repository.saveAll(entities);
    }

    @Override
    public List<Task> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Task> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    private TaskEntity toEntity(Task task) {
        return TaskEntity.builder()
            .id(task.id())
            .title(task.title())
            .status(task.status())
            .assigneeId(task.assigneeId())
            .deadline(task.deadline())
            .updatedAt(task.updatedAt())
            .percentDone(task.percentDone())
            .build();
    }

    private Task toDomain(TaskEntity entity) {
        return new Task(
            entity.getId(),
            entity.getTitle(),
            entity.getStatus(),
            entity.getAssigneeId(),
            entity.getDeadline(),
            entity.getUpdatedAt(),
            entity.getPercentDone()
        );
    }
}
```

- [ ] **Step 6: Executar para confirmar que passa**

```bash
./mvnw test -Dtest=TaskJpaAdapterTest
```

Esperado: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/smfmo/integration/openproject/adapters/out/persistence/ \
        src/test/java/com/smfmo/integration/openproject/adapters/out/persistence/
git commit -m "feat: add JPA persistence adapter (TaskEntity, TaskJpaAdapter)"
```

---

## Task 9: OpenProject — configuração, DTOs e RestAdapter

**Files:**
- Create: `src/main/java/com/smfmo/integration/openproject/adapters/out/openproject/config/OpenProjectProperties.java`
- Create: `src/main/java/com/smfmo/integration/openproject/adapters/out/openproject/config/OpenProjectClientConfig.java`
- Create: `src/main/java/com/smfmo/integration/openproject/adapters/out/openproject/dto/WorkPackageListResponse.java`
- Create: `src/main/java/com/smfmo/integration/openproject/adapters/out/openproject/dto/WorkPackageResponse.java`
- Create: `src/main/java/com/smfmo/integration/openproject/adapters/out/openproject/dto/TimeEntryListResponse.java`
- Create: `src/main/java/com/smfmo/integration/openproject/adapters/out/openproject/dto/TimeEntryResponse.java`
- Create: `src/main/java/com/smfmo/integration/openproject/adapters/out/openproject/dto/CurrentUserResponse.java`

- [ ] **Step 1: Criar OpenProjectProperties.java**

```java
package com.smfmo.integration.openproject.adapters.out.openproject.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openproject")
public record OpenProjectProperties(String baseUrl, String apiKey) {}
```

- [ ] **Step 2: Criar OpenProjectClientConfig.java**

```java
package com.smfmo.integration.openproject.adapters.out.openproject.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.Base64;

@Configuration
@EnableConfigurationProperties(OpenProjectProperties.class)
public class OpenProjectClientConfig {

    @Bean
    public RestClient openProjectRestClient(OpenProjectProperties properties) {
        String credentials = Base64.getEncoder()
            .encodeToString((properties.apiKey() + ":apikey").getBytes());

        return RestClient.builder()
            .baseUrl(properties.baseUrl())
            .defaultHeader("Authorization", "Basic " + credentials)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
}
```

- [ ] **Step 3: Criar WorkPackageListResponse.java**

A API OpenProject retorna listas no formato HAL com `_embedded.elements`.

```java
package com.smfmo.integration.openproject.adapters.out.openproject.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class WorkPackageListResponse {

    @JsonProperty("_embedded")
    private Embedded embedded;

    @Data
    public static class Embedded {
        private List<WorkPackageResponse> elements;
    }
}
```

- [ ] **Step 4: Criar WorkPackageResponse.java**

```java
package com.smfmo.integration.openproject.adapters.out.openproject.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WorkPackageResponse {

    private Long id;
    private String subject;
    private Integer percentageDone;
    private String dueDate;
    private String updatedAt;

    @JsonProperty("_embedded")
    private Embedded embedded;

    @Data
    public static class Embedded {
        private Status status;
        private Assignee assignee;
    }

    @Data
    public static class Status {
        private String name;
    }

    @Data
    public static class Assignee {
        private Long id;
    }
}
```

- [ ] **Step 5: Criar TimeEntryListResponse.java**

```java
package com.smfmo.integration.openproject.adapters.out.openproject.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class TimeEntryListResponse {

    @JsonProperty("_embedded")
    private Embedded embedded;

    @Data
    public static class Embedded {
        private List<TimeEntryResponse> elements;
    }
}
```

- [ ] **Step 6: Criar TimeEntryResponse.java**

O campo `hours` na API OpenProject é uma duração ISO 8601 (ex: "PT2H30M").

```java
package com.smfmo.integration.openproject.adapters.out.openproject.dto;

import lombok.Data;

@Data
public class TimeEntryResponse {

    private Long id;
    private String hours;   // ISO 8601 duration, ex: "PT2H30M"
    private String spentOn; // "yyyy-MM-dd"
}
```

- [ ] **Step 7: Criar CurrentUserResponse.java**

```java
package com.smfmo.integration.openproject.adapters.out.openproject.dto;

import lombok.Data;

@Data
public class CurrentUserResponse {
    private Long id;
    private String login;
    private String name;
}
```

- [ ] **Step 8: Compilar**

```bash
./mvnw compile -q
```

Esperado: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/smfmo/integration/openproject/adapters/out/openproject/
git commit -m "feat: add OpenProject config, DTOs and RestClient bean"
```

---

## Task 10: OpenProjectRestAdapter

**Files:**
- Create: `src/main/java/com/smfmo/integration/openproject/adapters/out/openproject/OpenProjectRestAdapter.java`

- [ ] **Step 1: Criar OpenProjectRestAdapter.java**

```java
package com.smfmo.integration.openproject.adapters.out.openproject;

import com.smfmo.integration.openproject.adapters.out.openproject.dto.*;
import com.smfmo.integration.openproject.domain.model.Task;
import com.smfmo.integration.openproject.domain.model.TimeEntry;
import com.smfmo.integration.openproject.ports.out.OpenProjectTaskPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Component
public class OpenProjectRestAdapter implements OpenProjectTaskPort {

    private final RestClient restClient;

    public OpenProjectRestAdapter(@Qualifier("openProjectRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public String fetchCurrentUserId() {
        CurrentUserResponse user = restClient.get()
            .uri("/api/v3/users/me")
            .retrieve()
            .body(CurrentUserResponse.class);
        return String.valueOf(Objects.requireNonNull(user).getId());
    }

    @Override
    public List<Task> fetchAllTasks(String userId) {
        String filters = "[{\"assignee\":{\"operator\":\"=\",\"values\":[\"%s\"]}}]".formatted(userId);

        WorkPackageListResponse response = restClient.get()
            .uri("/api/v3/work_packages?filters={filters}", filters)
            .retrieve()
            .body(WorkPackageListResponse.class);

        if (response == null || response.getEmbedded() == null) {
            return List.of();
        }

        return response.getEmbedded().getElements().stream()
            .map(this::toDomainTask)
            .toList();
    }

    @Override
    public List<TimeEntry> fetchTodayEntries(String userId) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String filters = "[{\"user\":{\"operator\":\"=\",\"values\":[\"%s\"]}},{\"spent_on\":{\"operator\":\"=\",\"values\":[\"%s\"]}}]"
            .formatted(userId, today);

        TimeEntryListResponse response = restClient.get()
            .uri("/api/v3/time_entries?filters={filters}", filters)
            .retrieve()
            .body(TimeEntryListResponse.class);

        if (response == null || response.getEmbedded() == null) {
            return List.of();
        }

        return response.getEmbedded().getElements().stream()
            .map(this::toDomainEntry)
            .toList();
    }

    private Task toDomainTask(WorkPackageResponse wp) {
        LocalDate deadline = wp.getDueDate() != null
            ? LocalDate.parse(wp.getDueDate()) : null;

        LocalDateTime updatedAt = wp.getUpdatedAt() != null
            ? LocalDateTime.parse(wp.getUpdatedAt(), DateTimeFormatter.ISO_DATE_TIME) : LocalDateTime.now();

        String status = (wp.getEmbedded() != null && wp.getEmbedded().getStatus() != null)
            ? wp.getEmbedded().getStatus().getName() : "unknown";

        String assigneeId = (wp.getEmbedded() != null && wp.getEmbedded().getAssignee() != null)
            ? String.valueOf(wp.getEmbedded().getAssignee().getId()) : null;

        int percentDone = wp.getPercentageDone() != null ? wp.getPercentageDone() : 0;

        return new Task(wp.getId(), wp.getSubject(), status, assigneeId, deadline, updatedAt, percentDone);
    }

    private TimeEntry toDomainEntry(TimeEntryResponse te) {
        double hours = te.getHours() != null
            ? Duration.parse(te.getHours()).toMinutes() / 60.0 : 0.0;

        LocalDate date = te.getSpentOn() != null
            ? LocalDate.parse(te.getSpentOn()) : LocalDate.now();

        return new TimeEntry(te.getId(), null, hours, date);
    }
}
```

- [ ] **Step 2: Compilar**

```bash
./mvnw compile -q
```

Esperado: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/smfmo/integration/openproject/adapters/out/openproject/OpenProjectRestAdapter.java
git commit -m "feat: add OpenProjectRestAdapter using RestClient"
```

---

## Task 11: EmailNotificationAdapter

**Files:**
- Create: `src/main/java/com/smfmo/integration/openproject/adapters/out/notification/NotificationProperties.java`
- Create: `src/main/java/com/smfmo/integration/openproject/adapters/out/notification/EmailNotificationAdapter.java`

- [ ] **Step 1: Criar NotificationProperties.java**

```java
package com.smfmo.integration.openproject.adapters.out.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(String recipient) {}
```

- [ ] **Step 2: Criar EmailNotificationAdapter.java**

```java
package com.smfmo.integration.openproject.adapters.out.notification;

import com.smfmo.integration.openproject.ports.out.NotificationPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(NotificationProperties.class)
public class EmailNotificationAdapter implements NotificationPort {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    public EmailNotificationAdapter(JavaMailSender mailSender,
                                    NotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(properties.recipient());
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
```

- [ ] **Step 3: Compilar**

```bash
./mvnw compile -q
```

Esperado: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/smfmo/integration/openproject/adapters/out/notification/
git commit -m "feat: add EmailNotificationAdapter"
```

---

## Task 12: DomainConfig e configuração do application.yaml

**Files:**
- Create: `src/main/java/com/smfmo/integration/openproject/adapters/config/DomainConfig.java`
- Modify: `src/main/resources/application.yaml`

- [ ] **Step 1: Criar DomainConfig.java**

O `RuleEngine` é uma classe de domínio pura (sem `@Component`). Este `@Configuration` cria o bean com o `Clock` do sistema.

```java
package com.smfmo.integration.openproject.adapters.config;

import com.smfmo.integration.openproject.domain.service.RuleEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class DomainConfig {

    @Bean
    public RuleEngine ruleEngine() {
        return new RuleEngine(Clock.systemDefaultZone());
    }
}
```

- [ ] **Step 2: Substituir o conteúdo de application.yaml**

```yaml
spring:
  application:
    name: openproject

  datasource:
    url: jdbc:postgresql://localhost:5432/openproject
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true

openproject:
  base-url: ${OPENPROJECT_BASE_URL:https://your-instance.openproject.com}
  api-key: ${OPENPROJECT_API_KEY}

notification:
  recipient: ${NOTIFICATION_RECIPIENT:smf.ferreira1901@gmail.com}

scheduler:
  sync-cron: ${SCHEDULER_SYNC_CRON:0 0 * * * *}
  rules-cron: ${SCHEDULER_RULES_CRON:0 30 * * * *}
```

- [ ] **Step 3: Compilar**

```bash
./mvnw compile -q
```

Esperado: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/smfmo/integration/openproject/adapters/config/DomainConfig.java \
        src/main/resources/application.yaml
git commit -m "feat: add DomainConfig bean and complete application.yaml"
```

---

## Task 13: @EnableScheduling, SyncJob e RulesJob

**Files:**
- Modify: `src/main/java/com/smfmo/integration/openproject/Application.java`
- Create: `src/main/java/com/smfmo/integration/openproject/adapters/in/job/SyncJob.java`
- Create: `src/main/java/com/smfmo/integration/openproject/adapters/in/job/RulesJob.java`

- [ ] **Step 1: Adicionar @EnableScheduling ao Application.java**

```java
package com.smfmo.integration.openproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

- [ ] **Step 2: Criar SyncJob.java**

```java
package com.smfmo.integration.openproject.adapters.in.job;

import com.smfmo.integration.openproject.ports.in.SyncTasksPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SyncJob {

    private static final Logger log = LoggerFactory.getLogger(SyncJob.class);

    private final SyncTasksPort syncTasksPort;

    public SyncJob(SyncTasksPort syncTasksPort) {
        this.syncTasksPort = syncTasksPort;
    }

    @Scheduled(cron = "${scheduler.sync-cron}")
    public void run() {
        log.info("Iniciando sincronização de tasks");
        syncTasksPort.execute();
        log.info("Sincronização concluída");
    }
}
```

- [ ] **Step 3: Criar RulesJob.java**

```java
package com.smfmo.integration.openproject.adapters.in.job;

import com.smfmo.integration.openproject.ports.in.RunRulesPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RulesJob {

    private static final Logger log = LoggerFactory.getLogger(RulesJob.class);

    private final RunRulesPort runRulesPort;

    public RulesJob(RunRulesPort runRulesPort) {
        this.runRulesPort = runRulesPort;
    }

    @Scheduled(cron = "${scheduler.rules-cron}")
    public void run() {
        log.info("Iniciando execução de regras");
        runRulesPort.execute();
        log.info("Execução de regras concluída");
    }
}
```

- [ ] **Step 4: Compilar**

```bash
./mvnw compile -q
```

Esperado: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/smfmo/integration/openproject/Application.java \
        src/main/java/com/smfmo/integration/openproject/adapters/in/job/
git commit -m "feat: add scheduled jobs (SyncJob, RulesJob)"
```

---

## Task 14: TaskController — REST endpoints

**Files:**
- Create: `src/main/java/com/smfmo/integration/openproject/adapters/in/web/TaskController.java`

- [ ] **Step 1: Criar TaskController.java**

```java
package com.smfmo.integration.openproject.adapters.in.web;

import com.smfmo.integration.openproject.domain.model.Task;
import com.smfmo.integration.openproject.ports.in.RunRulesPort;
import com.smfmo.integration.openproject.ports.in.SyncTasksPort;
import com.smfmo.integration.openproject.ports.out.TaskRepositoryPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class TaskController {

    private final TaskRepositoryPort taskRepositoryPort;
    private final SyncTasksPort syncTasksPort;
    private final RunRulesPort runRulesPort;

    public TaskController(TaskRepositoryPort taskRepositoryPort,
                          SyncTasksPort syncTasksPort,
                          RunRulesPort runRulesPort) {
        this.taskRepositoryPort = taskRepositoryPort;
        this.syncTasksPort = syncTasksPort;
        this.runRulesPort = runRulesPort;
    }

    @GetMapping("/tasks")
    public List<Task> listTasks() {
        return taskRepositoryPort.findAll();
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<Task> getTask(@PathVariable Long id) {
        return taskRepositoryPort.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/sync")
    public ResponseEntity<Void> sync() {
        syncTasksPort.execute();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/rules/run")
    public ResponseEntity<Void> runRules() {
        runRulesPort.execute();
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 2: Compilar**

```bash
./mvnw compile -q
```

Esperado: BUILD SUCCESS

- [ ] **Step 3: Rodar todos os testes**

```bash
./mvnw test
```

Esperado: BUILD SUCCESS — todos os testes passando (RuleEngineTest, SyncTasksUseCaseTest, RunRulesUseCaseTest, TaskJpaAdapterTest)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/smfmo/integration/openproject/adapters/in/web/TaskController.java
git commit -m "feat: add TaskController with GET /tasks, POST /sync, POST /rules/run"
```

---

## Task 15: Smoke test manual

Esta task não tem código — é um checklist para validar o sistema end-to-end contra uma instância real do OpenProject.

**Pré-requisitos:**
- PostgreSQL rodando localmente com banco `openproject` criado
- Instância OpenProject acessível com API Key válida

- [ ] **Step 1: Configurar variáveis de ambiente**

```bash
export OPENPROJECT_BASE_URL=https://seu-dominio.openproject.com
export OPENPROJECT_API_KEY=sua-api-key-aqui
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export MAIL_USERNAME=seu-email@gmail.com
export MAIL_PASSWORD=sua-senha-de-app
export NOTIFICATION_RECIPIENT=smf.ferreira1901@gmail.com
```

- [ ] **Step 2: Criar o banco de dados**

```bash
psql -U postgres -c "CREATE DATABASE openproject;"
```

- [ ] **Step 3: Iniciar a aplicação**

```bash
./mvnw spring-boot:run
```

Esperado: aplicação sobe sem erros, Flyway executa as migrations V1 e V2

- [ ] **Step 4: Testar sincronização manual**

```bash
curl -X POST http://localhost:8080/sync
```

Esperado: HTTP 200 — logs mostram `Sincronização concluída`

- [ ] **Step 5: Verificar tasks sincronizadas**

```bash
curl http://localhost:8080/tasks
```

Esperado: JSON array com tasks do OpenProject

- [ ] **Step 6: Testar execução de regras**

```bash
curl -X POST http://localhost:8080/rules/run
```

Esperado: HTTP 200 — se houver tasks próximas do prazo, e-mail enviado ao destinatário configurado

---

## Critérios de Conclusão do MVP

- [ ] `./mvnw test` passa com BUILD SUCCESS
- [ ] Aplicação inicia sem erros com banco PostgreSQL disponível
- [ ] `POST /sync` sincroniza tasks do OpenProject para o banco local
- [ ] `GET /tasks` retorna as tasks sincronizadas
- [ ] `POST /rules/run` avalia regras e envia e-mail quando há violações
- [ ] Jobs `@Scheduled` executam automaticamente sem intervenção manual
