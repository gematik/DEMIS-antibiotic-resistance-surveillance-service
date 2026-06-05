package de.gematik.demis.ars.service.batchprocessing.config;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.resilience.annotation.EnableResilientMethods;

@Configuration
@ConditionalOnProperty("ars.batch-processing.enabled")
@ConditionalOnProperty("ars.batch-processing.retry.enabled")
// this activates the Retryable annotation which is used in RetryableNotificationProcessor
@EnableResilientMethods
@RequiredArgsConstructor
@Slf4j
public class RetryableConfig {

  public static final String KEY_MAX_RETRIES = "ars.batch-processing.retry.max-retries";
  public static final String KEY_DELAY = "ars.batch-processing.retry.delay-ms";
  public static final String KEY_MAX_DELAY = "ars.batch-processing.retry.max-delay-ms";
  public static final String KEY_MULTIPLIER = "ars.batch-processing.retry.multiplier";

  // just for logging
  private final Environment env;

  @PostConstruct
  void log() {
    log.info(
        "Retrying in batch processing ist enabled. Max-Retries={}, Delay={}ms, Max-Delays={}ms, Multiplier={}",
        env.getProperty(KEY_MAX_RETRIES),
        env.getProperty(KEY_DELAY),
        env.getProperty(KEY_MAX_DELAY),
        env.getProperty(KEY_MULTIPLIER));
  }
}
