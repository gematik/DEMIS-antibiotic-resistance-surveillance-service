package de.gematik.demis.ars.service.batchprocessing;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import de.gematik.demis.ars.service.exception.ArsServiceException;
import de.gematik.demis.ars.service.exception.ArsValidationException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.ErrorHandler;

@Slf4j
@ConditionalOnProperty("ars.batch-processing.enabled")
public class AsyncArsExceptionHandler implements ErrorHandler {

  @Override
  public void handleError(@NonNull Throwable throwable) {
    if (isPermanentError(throwable)) {
      throw new AmqpRejectAndDontRequeueException(
          "Permanent error detected, message will not be re-queued: " + throwable.getMessage(),
          throwable);
    }

    // allows requeue
    throw new RuntimeException(
        "Transient error detected, message will be re-queued: " + throwable.getMessage(),
        throwable);
  }

  private boolean isPermanentError(Throwable throwable) {
    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;

    // FSS or CES is not available (5xx errors) - transient error
    // TODO refine this logic, this should return false, but we don't have a strategy for retries
    // yet
    if (cause instanceof ArsServiceException arsServiceException) {
      if (arsServiceException.getResponseStatus().is5xxServerError()) {
        return true;
      }
    }

    if (cause instanceof JsonProcessingException) {
      return true;
    }

    return cause instanceof ArsValidationException || (cause instanceof IllegalArgumentException);
  }
}
