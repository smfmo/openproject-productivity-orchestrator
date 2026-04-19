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

    /**
     * Persiste a lista de tasks via upsert — atualiza se o ID já existir,
     * insere caso contrário.
     */
    @Override
    public void saveAll(List<Task> tasks) {
        repository.saveAll(tasks.stream().map(this::toEntity).toList());
    }

    /** Retorna todas as tasks persistidas localmente mapeadas para o domínio. */
    @Override
    public List<Task> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    /** Busca uma task pelo ID do OpenProject. */
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

    private Task toDomain(TaskEntity e) {
        return new Task(e.getId(), e.getTitle(), e.getStatus(), e.getAssigneeId(),
                e.getDeadline(), e.getUpdatedAt(), e.getPercentDone());
    }

}
