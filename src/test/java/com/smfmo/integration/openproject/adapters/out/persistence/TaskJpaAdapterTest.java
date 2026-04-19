package com.smfmo.integration.openproject.adapters.out.persistence;

import com.smfmo.integration.openproject.domain.model.Task;
import org.junit.jupiter.api.DisplayName;
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


@DisplayName("TaskJpaAdapter")
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
    @DisplayName("deve delegar saveAll ao repositório JPA")
    void saveAll_delegatesToRepository() {
        taskJpaAdapter.saveAll(List.of(task(1L), task(2L)));
        verify(taskJpaRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("deve retornar tasks mapeadas para domínio ao buscar todas")
    void findAll_returnsMappedDomainTasks() {
        TaskEntity entity = TaskEntity.builder()
                .id(1L).title("Task 1").status("open").assigneeId("user1")
                .deadline(LocalDate.now()).updatedAt(LocalDateTime.now()).percentDone(50)
                .build();
        when(taskJpaRepository.findAll()).thenReturn(List.of(entity));

        List<Task> tasks = taskJpaAdapter.findAll();

        assertThat(tasks).hasSize(1);
        assertThat(tasks.getFirst().id()).isEqualTo(1L);
        assertThat(tasks.getFirst().title()).isEqualTo("Task 1");
    }

    @Test
    @DisplayName("deve retornar task mapeada ao buscar por ID existente")
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

    @Test
    @DisplayName("deve retornar Optional vazio ao buscar por ID inexistente")
    void findById_returnsEmpty_whenNotFound() {
        when(taskJpaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(taskJpaAdapter.findById(99L)).isEmpty();
    }

}
