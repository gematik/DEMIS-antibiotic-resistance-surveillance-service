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

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@ConditionalOnProperty("ars.batch-processing.enabled")
@ConditionalOnProperty("ars.batch-processing.circuit-breaker.enabled")
@EnableScheduling
@Slf4j
class CircuitBreakerConfig {

  @PostConstruct
  void logInfo() {
    log.info("RabbitMQ CircuitBreaker is enabled");
  }

  @Bean
  DbHealthCheck dbHealthCheck(final DataSource dataSource) {
    return new DbHealthCheck(dataSource);
  }

  @Bean
  RabbitListenerCircuitBreaker rabbitListenerCircuitBreaker(
      final RabbitListenerEndpointRegistry rabbitListenerEndpointRegistry,
      final DbHealthCheck dbHealthCheck) {
    return new RabbitListenerCircuitBreaker(rabbitListenerEndpointRegistry, dbHealthCheck);
  }
}
