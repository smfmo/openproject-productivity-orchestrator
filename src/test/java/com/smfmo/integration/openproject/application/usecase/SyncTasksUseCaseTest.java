package com.smfmo.integration.openproject.application.usecase;

import com.smfmo.integration.openproject.domain.model.Task;
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

import static org.mockito.Mockito.*;

@DisplayName("SyncTasksUseCase")
@ExtendWith(MockitoExtension.class)
class SyncTasksUseCaseTest {

    @Mock
    private OpenProjectTaskPort openProjectTaskPort;

    @Mock
    private TaskRepositoryPort taskRepositoryPort;

    @InjectMocks
    private SyncTasksUseCase syncTasksUseCase;


    @Test
    @DisplayName("deve buscar task do OpenProject e persistir localmente")
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
