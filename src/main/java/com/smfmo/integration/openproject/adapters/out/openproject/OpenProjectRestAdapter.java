package com.smfmo.integration.openproject.adapters.out.openproject;

import com.smfmo.integration.openproject.adapters.out.openproject.dto.*;
import com.smfmo.integration.openproject.domain.model.Task;
import com.smfmo.integration.openproject.domain.model.TimeEntry;
import com.smfmo.integration.openproject.ports.out.OpenProjectTaskPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class OpenProjectRestAdapter implements OpenProjectTaskPort {

    private static final Logger log = LoggerFactory.getLogger(OpenProjectRestAdapter.class);

    private final RestClient restClient;

    public OpenProjectRestAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Retorna o ID do usuário autenticado via API Key.
     */
    @Override
    public String fetchCurrentUserId() {
        CurrentUserResponse user = restClient.get()
                .uri("/api/v3/users/me")
                .retrieve()
                .body(CurrentUserResponse.class);
        return String.valueOf(Objects.requireNonNull(user).getId());
    }

    /**
     * Retorna todas as tasks atribuídas ao usuário informado.
     */
    @Override
    public List<Task> fetchAllTasks(String userId) {
        log.info("Buscando tasks para userId: {}", userId);
        String filters = "[{\"responsible\":{\"operator\":\"=\",\"values\":[\"%s\"]}}]"
                .formatted(userId);

        List<Task> allTasks = new ArrayList<>();
        int pageSize = 50;
        int offset = 1;

        while (true) {
            WorkPackageListResponse response = restClient.get()
                    .uri("/api/v3/work_packages?filters={filters}&pageSize={pageSize}&offset={offset}&sortBy=[[\"id\",\"asc\"]]",
                            filters, pageSize, offset)
                    .retrieve()
                    .body(WorkPackageListResponse.class);

            if (response == null || response.getEmbedded() == null
                    || response.getEmbedded().getElements().isEmpty()) {
                break;
            }

            log.info("Página {}: {} tasks retornadas, total={}",
                    offset, response.getEmbedded().getElements().size(), response.getTotal());

            List<Task> page = response.getEmbedded().getElements().stream()
                    .map(this::toDomainTask)
                    .toList();

            allTasks.addAll(page);

            if (page.size() < pageSize) {
                break;
            }

            offset += pageSize;
        }

        log.info("Total de tasks sincronizadas: {}", allTasks.size());
        return allTasks;
    }


    /**
     * Retorna os registros de horas do usuário para o dia atual.
     */
    @Override
    public List<TimeEntry> fetchTodayEntries(String userId) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String filters = "[{\"user\":{\"operator\":\"=\",\"values\":[\"%s\"]}},{\"spent_on\":{\"operator\":\"=\",\"values\":[\"%s\"]}}]"
                .formatted(userId, today);

        TimeEntryListResponse response = restClient.get()
                .uri("/api/v3/time_entries?filters={filters}", filters)
                .retrieve()
                .body(TimeEntryListResponse.class);

        if (response == null || response.getEmbedded() == null) {
            return List.of();
        }

        return response.getEmbedded().getElements().stream()
                .map(this::toDomainEntry)
                .toList();
    }

    private Task toDomainTask(WorkPackageResponse wp) {
        LocalDate deadline = wp.getDueDate() != null
                ? LocalDate.parse(wp.getDueDate()) : null;

        LocalDateTime updatedAt = wp.getUpdatedAt() != null
                ? LocalDateTime.parse(wp.getUpdatedAt(), DateTimeFormatter.ISO_DATE_TIME)
                : LocalDateTime.now();

        String status = (wp.getLinks() != null && wp.getLinks().getStatus() != null)
                ? wp.getLinks().getStatus().getTitle() : "unknown";

        String assigneeId = null;
        if (wp.getLinks() != null && wp.getLinks().getAssignee() != null
                && wp.getLinks().getAssignee().getHref() != null) {
            String href = wp.getLinks().getAssignee().getHref();
            assigneeId = href.substring(href.lastIndexOf('/') + 1);
        }

        int percentDone = wp.getPercentageDone() != null ? wp.getPercentageDone() : 0;

        return new Task(wp.getId(), wp.getSubject(), status, assigneeId,
                deadline, updatedAt, percentDone);
    }


    private TimeEntry toDomainEntry(TimeEntryResponse te) {
        double hours = te.getHours() != null ? Duration.parse(te.getHours()).toMinutes() / 60.0 : 0.0;

        LocalDate date = te.getSpentOn() != null ? LocalDate.parse(te.getSpentOn()) : LocalDate.now();

        return new TimeEntry(te.getId(), null, hours, date);
    }

}
