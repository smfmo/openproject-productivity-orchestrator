package com.smfmo.integration.openproject.ports.out;

import com.smfmo.integration.openproject.domain.model.Task;
import com.smfmo.integration.openproject.domain.model.TimeEntry;

import java.util.List;

public interface OpenProjectTaskPort {
    String fetchCurrentUserId();
    List<Task> fetchAllTasks(String userId);
    List<TimeEntry> fetchTodayEntries(String userId);
}
