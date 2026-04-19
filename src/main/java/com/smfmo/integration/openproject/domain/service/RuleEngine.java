package com.smfmo.integration.openproject.domain.service;

import com.smfmo.integration.openproject.domain.model.RuleType;
import com.smfmo.integration.openproject.domain.model.RuleViolation;
import com.smfmo.integration.openproject.domain.model.Task;
import com.smfmo.integration.openproject.domain.model.TimeEntry;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class RuleEngine {

    private final Clock clock;

    public RuleEngine(Clock clock) {
        this.clock = clock;
    }

    /**
     * Avalia regras aplicáveis a uma task individual.
     * <p>
     *  Regras avaliadas:
     * <ul>
     *     <li>{@link RuleType#DEADLINE_APPROACHING} — deadline dentro de 3 dias e task incompleta</li>
     *     <li>{@link RuleType#STALE_TASK} — Sem atualização há mais de 7 dias e task incompleta</li>
     * </ul>
     *
     * @param task tarefa a ser avaliada
     * @return lista de violações encontradas; vazia se nenhuma regra for violada
     */
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

    /**
     * Avalia regras globais que não dependem de uma task específica.
     * <p>
     *  Regras avaliadas:
     *  <ul>
     *      <li>{@link RuleType#MISSING_HOURS} — Nenhuma hora registrada hoje, avaliada apenas após 18h</li>
     *  </ul>
     * @param todayEntries registros de horas do dia atual.
     * @return lista de violações globais; vazia se nenhuma regra for violada
     */
    public List<RuleViolation> evaluateGlobal(List<TimeEntry> todayEntries) {
        List<RuleViolation> violations = new ArrayList<>();

        if (LocalTime.now(clock).isAfter(LocalTime.of(18, 0)) && todayEntries.isEmpty()) {
            violations.add(new RuleViolation(
                    RuleType.MISSING_HOURS,
                    "Nenhuma hora registrada hoje",
                    null
            ));
        }

        return violations;
    }
}
