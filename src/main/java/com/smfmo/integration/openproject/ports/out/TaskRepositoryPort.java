package com.smfmo.integration.openproject.ports.out;

import com.smfmo.integration.openproject.domain.model.Task;

import java.util.List;
import java.util.Optional;

public interface TaskRepositoryPort {
    void saveAll(List<Task> tasks);
    List<Task> findAll();
    Optional<Task> findById(Long id);
}
