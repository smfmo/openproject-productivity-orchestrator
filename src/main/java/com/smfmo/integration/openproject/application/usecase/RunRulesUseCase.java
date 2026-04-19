package com.smfmo.integration.openproject.application.usecase;

import com.smfmo.integration.openproject.domain.model.RuleViolation;
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

    public RunRulesUseCase(
            OpenProjectTaskPort openProjectTaskPort, TaskRepositoryPort taskRepositoryPort,
            RuleEngine ruleEngine, NotificationPort notificationPort) {
        this.openProjectTaskPort = openProjectTaskPort;
        this.taskRepositoryPort = taskRepositoryPort;
        this.ruleEngine = ruleEngine;
        this.notificationPort = notificationPort;
    }

    /**
     * Carrega tasks locais, busca horas do dia, avalia regras por task e globais,
     * e envia uma notificação por e-mail para cada violação encontrada.
     */
    @Override
    public void execute() {
        String userId = openProjectTaskPort.fetchCurrentUserId();
        List<TimeEntry> todayEntries = openProjectTaskPort.fetchTodayEntries(userId);

        taskRepositoryPort.findAll()
                .forEach(task -> ruleEngine.evaluateTask(task)
                        .forEach(v -> notificationPort.send(subjectFor(v), v.message())));

        ruleEngine.evaluateGlobal(todayEntries)
                .forEach(v -> notificationPort.send(subjectFor(v), v.message()));
    }

    private String subjectFor(RuleViolation violation) {
        return "[OpenProject] " + violation.type().name();
    }

}
