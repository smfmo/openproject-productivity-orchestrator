package com.smfmo.integration.openproject.application.usecase;

import com.smfmo.integration.openproject.domain.model.RuleType;
import com.smfmo.integration.openproject.domain.model.RuleViolation;
import com.smfmo.integration.openproject.domain.model.Task;
import com.smfmo.integration.openproject.domain.service.RuleEngine;
import com.smfmo.integration.openproject.ports.out.NotificationPort;
import com.smfmo.integration.openproject.ports.out.OpenProjectTaskPort;
import com.smfmo.integration.openproject.ports.out.TaskRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@DisplayName("RunRulesUseCase")
@ExtendWith(MockitoExtension.class)
class RunRulesUseCaseTest {

    @Mock
    private OpenProjectTaskPort openProjectTaskPort;

    @Mock
    private TaskRepositoryPort taskRepositoryPort;

    @Mock
    private RuleEngine ruleEngine;

    @Mock
    private NotificationPort notificationPort;

    @InjectMocks
    private RunRulesUseCase runRulesUseCase;

    private Task task(Long id) {
        return new Task(id, "Task " + id, "open", "user1",
                LocalDate.now().plusDays(1), LocalDateTime.now(), 50);
    }

    @Test
    @DisplayName("deve enviar notificação para cada violação de task")
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
    @DisplayName("deve enviar notificação para violação global (MISSING_HOURS)")
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
    @DisplayName("não deve notificar quando não há violações")
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
