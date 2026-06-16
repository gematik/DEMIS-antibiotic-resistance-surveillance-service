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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.util.ErrorHandler;

@RequiredArgsConstructor
@Slf4j
public class RabbitListenerErrorHandler implements ErrorHandler {

  @Nullable private final RabbitListenerCircuitBreaker rabbitListenerCircuitBreaker;

  @Override
  public void handleError(final @NonNull Throwable t) {
    log.error("Execution of Rabbit message listener failed.", t);

    if (causeChainContains(t, SaveResultFailedException.class)) {
      if (rabbitListenerCircuitBreaker != null) {
        rabbitListenerCircuitBreaker.triggerDatabaseCheck();
      }

      throw new ImmediateRequeueAmqpException(
          "requeue message because saving processing result failed.", t);
    }
  }

  private boolean causeChainContains(
      final Throwable t, final Class<? extends Throwable> searchedExceptionClass) {
    Throwable cause = t;
    while (cause != null) {
      if (searchedExceptionClass.isInstance(cause)) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }
}
