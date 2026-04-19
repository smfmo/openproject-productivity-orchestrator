package com.smfmo.integration.openproject.application.usecase;

import com.smfmo.integration.openproject.ports.in.SyncTaskPort;
import com.smfmo.integration.openproject.ports.out.OpenProjectTaskPort;
import com.smfmo.integration.openproject.ports.out.TaskRepositoryPort;
import org.springframework.stereotype.Service;

@Service
public class SyncTasksUseCase implements SyncTaskPort {

    private final OpenProjectTaskPort openProjectTaskPort;
    private final TaskRepositoryPort taskRepositoryPort;

    public SyncTasksUseCase(OpenProjectTaskPort openProjectTaskPort, TaskRepositoryPort taskRepositoryPort) {
        this.openProjectTaskPort = openProjectTaskPort;
        this.taskRepositoryPort = taskRepositoryPort;
    }

    /**
     * Busca o usuário autenticado, sincroniza todas as suas tasks do OpenProject
     * e persiste localmente via upsert.
     */
    @Override
    public void execute() {
        String userId = openProjectTaskPort.fetchCurrentUserId();
        taskRepositoryPort.saveAll(openProjectTaskPort.fetchAllTasks(userId));
    }
}
