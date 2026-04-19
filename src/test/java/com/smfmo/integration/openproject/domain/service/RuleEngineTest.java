package com.smfmo.integration.openproject.domain.service;


import com.smfmo.integration.openproject.domain.model.RuleType;
import com.smfmo.integration.openproject.domain.model.Task;
import com.smfmo.integration.openproject.domain.model.TimeEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-04-19T22:00:00Z"),
            ZoneOffset.UTC
    );

    private final RuleEngine ruleEngine = new RuleEngine(FIXED_CLOCK);

    private Task task(LocalDate deadline, LocalDateTime updatedAt, int parseDone) {
        return new Task(1L, "Test task", "open", "user1", deadline, updatedAt, parseDone);
    }

    @Test
    @DisplayName("Deve gerar DEADLINE_APPROACHING quando deadline está dentro de 3 dias")
    void deadlineApproaching_whenDeadlineWithin3Days() {
        Task task = task(LocalDate.now(FIXED_CLOCK).plusDays(2), LocalDateTime.now(FIXED_CLOCK), 50);
        assertThat(ruleEngine.evaluateTask(task))
                .anyMatch(v -> v.type() == RuleType.DEADLINE_APPROACHING);
    }

    @Test
    @DisplayName("Não deve gerar DEADLINE_APPROACHING quando deadline está além de 3 dias")
    void noDeadlineApproaching_whenDeadlineBeyond3Days() {
        Task task = task(LocalDate.now(FIXED_CLOCK).plusDays(5), LocalDateTime.now(FIXED_CLOCK), 50);
        assertThat(ruleEngine.evaluateTask(task))
                .noneMatch(v -> v.type() == RuleType.DEADLINE_APPROACHING);
    }

    @Test
    @DisplayName("não deve gerar DEADLINE_APPROACHING quando task está 100% concluída")
    void noDeadlineApproaching_whenTaskComplete() {
        Task task = task(LocalDate.now(FIXED_CLOCK).plusDays(1), LocalDateTime.now(FIXED_CLOCK), 100);
        assertThat(ruleEngine.evaluateTask(task))
                .noneMatch(v -> v.type() == RuleType.DEADLINE_APPROACHING);
    }

    @Test
    @DisplayName("não deve gerar DEADLINE_APPROACHING quando task não tem deadline")
    void noDeadlineApproaching_whenDeadlineIsNull() {
        Task task = task(null, LocalDateTime.now(FIXED_CLOCK), 50);
        assertThat(ruleEngine.evaluateTask(task))
                .noneMatch(v -> v.type() == RuleType.DEADLINE_APPROACHING);
    }


    @Test
    @DisplayName("deve gerar STALE_TASK quando task não foi atualizada há mais de 7 dias")
    void staleTask_whenNotUpdatedFor7Days() {
        Task task = task(null, LocalDateTime.now(FIXED_CLOCK).minusDays(8), 50);
        assertThat(ruleEngine.evaluateTask(task))
                .anyMatch(v -> v.type() == RuleType.STALE_TASK);
    }

    @Test
    @DisplayName("não deve gerar STALE_TASK quando task foi atualizada recentemente")
    void noStaleTask_whenRecentlyUpdated() {
        Task task = task(null, LocalDateTime.now(FIXED_CLOCK).minusDays(3), 50);
        assertThat(ruleEngine.evaluateTask(task))
                .noneMatch(v -> v.type() == RuleType.STALE_TASK);
    }

    @Test
    @DisplayName("deve gerar MISSING_HOURS quando não há horas registradas após 18h")
    void missingHours_whenNoEntriesAfter18h() {
        assertThat(ruleEngine.evaluateGlobal(List.of()))
                .anyMatch(v -> v.type() == RuleType.MISSING_HOURS);
    }

    @Test
    @DisplayName("não deve gerar MISSING_HOURS quando há horas registradas no dia")
    void noMissingHours_whenEntriesExist() {
        TimeEntry entry = new TimeEntry(1L, 10L, 2.0, LocalDate.now(FIXED_CLOCK));
        assertThat(ruleEngine.evaluateGlobal(List.of(entry)))
                .noneMatch(v -> v.type() == RuleType.MISSING_HOURS);
    }

    @Test
    @DisplayName("não deve gerar MISSING_HOURS antes das 18h mesmo sem horas registradas")
    void noMissingHours_whenBefore18h() {
        Clock morning = Clock.fixed(Instant.parse("2026-04-19T10:00:00Z"), ZoneOffset.UTC);
        RuleEngine earlyEngine = new RuleEngine(morning);
        assertThat(earlyEngine.evaluateGlobal(List.of()))
                .noneMatch(v -> v.type() == RuleType.MISSING_HOURS);
    }
}
