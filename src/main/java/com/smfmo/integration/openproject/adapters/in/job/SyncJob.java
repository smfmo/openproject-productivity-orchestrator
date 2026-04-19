package com.smfmo.integration.openproject.adapters.in.job;

import com.smfmo.integration.openproject.ports.in.SyncTaskPort;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;

@Component
public class SyncJob {

    private static final Logger log = LoggerFactory.getLogger(SyncJob.class);

    private final SyncTaskPort syncTaskPort;

    public SyncJob(SyncTaskPort syncTaskPort) {
        this.syncTaskPort = syncTaskPort;
    }

    /**
     * Executa a sincronização de tasks conforme o cron configurado em
     * {@code scheduler.sync-cron} no application.yaml.
     */
    @Scheduled(cron = "${scheduler.sync-cron}")
    public void run() {
        log.info("Iniciando sincronização de tasks");
        syncTaskPort.execute();
        log.info("Sincronização concluída");
    }
}
