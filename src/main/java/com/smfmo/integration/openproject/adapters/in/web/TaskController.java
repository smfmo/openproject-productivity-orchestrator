package com.smfmo.integration.openproject.adapters.in.web;

import com.smfmo.integration.openproject.domain.model.Task;
import com.smfmo.integration.openproject.ports.in.RunRulesPort;
import com.smfmo.integration.openproject.ports.in.SyncTaskPort;
import com.smfmo.integration.openproject.ports.out.TaskRepositoryPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TaskController {

    private final TaskRepositoryPort taskRepositoryPort;
    private final SyncTaskPort syncTaskPort;
    private final RunRulesPort runRulesPort;

    public TaskController(
            TaskRepositoryPort taskRepositoryPort, SyncTaskPort syncTasksPort, RunRulesPort runRulesPort) {
        this.taskRepositoryPort = taskRepositoryPort;
        this.syncTaskPort = syncTasksPort;
        this.runRulesPort = runRulesPort;
    }

    /**
     * Retorna todas as tasks sincronizadas localmente.
     */
    @GetMapping("/tasks")
    public List<Task> listTasks() {
        return taskRepositoryPort.findAll();
    }

    /**
     * Retorna o detalhe de uma task pelo ID do OpenProject.
     */
    @GetMapping("/tasks/{id}")
    public ResponseEntity<Task> getTask(@PathVariable Long id) {
        return taskRepositoryPort.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Dispara a sincronização manual de tasks com o OpenProject.
     */
    @PostMapping("/sync")
    public ResponseEntity<Void> sync() {
        syncTaskPort.execute();
        return ResponseEntity.ok().build();
    }

    /**
     * Dispara a execução manual das regras de produtividade.
     */
    @PostMapping("/rules/run")
    public ResponseEntity<Void> runRules() {
        runRulesPort.execute();
        return ResponseEntity.ok().build();
    }

}
