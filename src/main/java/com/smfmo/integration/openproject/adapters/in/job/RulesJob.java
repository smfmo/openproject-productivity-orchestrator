package com.smfmo.integration.openproject.adapters.in.job;

import com.smfmo.integration.openproject.ports.in.RunRulesPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RulesJob {

    private static final Logger log = LoggerFactory.getLogger(RulesJob.class);

    private final RunRulesPort runRulesPort;

    public RulesJob(RunRulesPort runRulesPort) {
        this.runRulesPort = runRulesPort;
    }

    /**
     * Executa a avaliação de regras conforme o cron configurado em
     * {@code scheduler.rules-cron} no application.yaml.
     */
    @Scheduled(cron = "${scheduler.rules-cron}")
    public void run() {
        log.info("Iniciando sincronização de tasks");
        runRulesPort.execute();
        log.info("Sincronização concluída");
    }
}
