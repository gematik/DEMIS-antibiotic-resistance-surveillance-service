package de.gematik.demis.ars.service.batchprocessing.resilience;

/*-
 * #%L
 * Antibiotic-Resistance-Surveillance-Service
 * %%
 * Copyright (C) 2025 - 2026 gematik GmbH
 * %%
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the
 * European Commission – subsequent versions of the EUPL (the "Licence").
 * You may not use this work except in compliance with the Licence.
 *
 * You find a copy of the Licence in the "Licence" file or at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
 * In case of changes by gematik find details in the "Readme" file.
 *
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
 * #L%
 */

import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
@Slf4j
public class RabbitListenerCircuitBreaker {

  private final RabbitListenerEndpointRegistry rabbitRegistry;
  private final DbHealthCheck dbHealthCheck;

  private volatile boolean listenerActive = true;
  private volatile boolean stoppingInProgress = false;

  public void triggerDatabaseCheck() {
    stopListenersIfDatabaseIsNotAvailable();
  }

  private void stopListenersIfDatabaseIsNotAvailable() {
    if (stoppingInProgress || !listenerActive) {
      return;
    }
    synchronized (this) {
      if (stoppingInProgress || !listenerActive) {
        return;
      }
      if (!dbHealthCheck.isValid()) {
        triggerStopListeners();
      }
    }
  }

  @Scheduled(
      fixedDelayString =
          "${ars.batch-processing.circuit-breaker.scheduler-reconnect-check-fixed-delay-ms}")
  public void checkDatabaseReconnection() {
    if (listenerActive) {
      return;
    }

    if (dbHealthCheck.isValid()) {
      synchronized (this) {
        if (!listenerActive) {
          startListeners();
        }
      }
    }
  }

  private void triggerStopListeners() {
    log.error("DB is down! Stopping rabbitmq listener...");
    stoppingInProgress = true;

    final var futures =
        rabbitRegistry.getListenerContainers().stream()
            .map(
                container ->
                    CompletableFuture.runAsync(
                        () -> stopRabbitListener(container), Thread::startVirtualThread))
            .toArray(CompletableFuture[]::new);

    CompletableFuture.allOf(futures)
        .whenComplete(
            (_, _) -> {
              listenerActive = false;
              stoppingInProgress = false;
              log.info("All RabbitListeners stopped.");
            });
  }

  private void startListeners() {
    log.info("Db is up. Start RabbitListeners...");
    rabbitRegistry.getListenerContainers().forEach(this::startRabbitListener);
    listenerActive = true;
  }

  private void stopRabbitListener(final MessageListenerContainer container) {
    if (container.isRunning()) {
      container.stop();
      log.info("RabbitListener was stopped.");
    }
  }

  private void startRabbitListener(final MessageListenerContainer container) {
    if (!container.isRunning()) {
      container.start();
      log.info("RabbitListener was started.");
    }
  }
}
