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

import static de.gematik.demis.ars.service.batchprocessing.resilience.RetryableConfig.KEY_DELAY;
import static de.gematik.demis.ars.service.batchprocessing.resilience.RetryableConfig.KEY_MAX_DELAY;
import static de.gematik.demis.ars.service.batchprocessing.resilience.RetryableConfig.KEY_MAX_RETRIES;
import static de.gematik.demis.ars.service.batchprocessing.resilience.RetryableConfig.KEY_MULTIPLIER;

import de.gematik.demis.ars.service.service.NotificationContext;
import de.gematik.demis.ars.service.service.NotificationProcessingResult;
import de.gematik.demis.ars.service.service.NotificationService;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.service.base.error.ServiceCallException;
import feign.RetryableException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.resilience.retry.MethodRetryPredicate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty("ars.batch-processing.enabled")
@RequiredArgsConstructor
@Slf4j
public class RetryableNotificationProcessor {
  private final NotificationService notificationService;

  @Retryable(
      includes = {ServiceCallException.class, RetryableException.class},
      predicate = RetryableNotificationProcessor.ExcludeServiceCallClientErrors.class,
      maxRetriesString = "${" + KEY_MAX_RETRIES + "}",
      delayString = "${" + KEY_DELAY + "}",
      maxDelayString = "${" + KEY_MAX_DELAY + "}",
      multiplierString = "${" + KEY_MULTIPLIER + "}",
      timeUnit = TimeUnit.MILLISECONDS)
  public NotificationProcessingResult process(
      final String content,
      final MessageType messageType,
      final String authorization,
      final NotificationContext context) {
    return notificationService.process(content, messageType, authorization, context);
  }

  static class ExcludeServiceCallClientErrors implements MethodRetryPredicate {

    @Override
    public boolean shouldRetry(final @NonNull Method method, final @NonNull Throwable throwable) {
      final boolean retry;
      if (throwable instanceof ServiceCallException serviceCallException) {
        retry = serviceCallException.getHttpStatus() >= 500;
      } else {
        retry = true;
      }
      if (retry) {
        log.info("Processing failed. retry... Cause = {}", throwable.getMessage());
      }
      return retry;
    }
  }
}
