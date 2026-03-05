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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.demis.ars.service.exception.ArsServiceException;
import de.gematik.demis.ars.service.exception.ArsValidationException;
import de.gematik.demis.ars.service.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AsyncArsExceptionHandlerTest {

  private final AsyncArsExceptionHandler underTest = new AsyncArsExceptionHandler();

  @Test
  void shouldRejectAndNotRequeueWhenArsValidationExceptionOccurs() {
    ArsValidationException exception =
        new ArsValidationException(ErrorCode.FHIR_VALIDATION_ERROR, "Validation failed", null);

    assertThatThrownBy(() -> underTest.handleError(exception))
        .isInstanceOf(AmqpRejectAndDontRequeueException.class)
        .hasCause(exception)
        .hasMessageContaining("Permanent error detected, message will not be re-queued");
  }

  @Test
  void shouldRejectAndNotRequeueWhenIllegalArgumentExceptionOccurs() {
    IllegalArgumentException exception = new IllegalArgumentException("Bad argument");

    assertThatThrownBy(() -> underTest.handleError(exception))
        .isInstanceOf(AmqpRejectAndDontRequeueException.class)
        .hasCause(exception)
        .hasMessageContaining("Permanent error detected, message will not be re-queued");
  }

  @Test
  void shouldRejectAndNotRequeueWhenArsServiceExceptionIs5xx() {
    ArsServiceException exception = mock(ArsServiceException.class);
    when(exception.getResponseStatus()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);

    assertThatThrownBy(() -> underTest.handleError(exception))
        .isInstanceOf(AmqpRejectAndDontRequeueException.class)
        .hasCause(exception)
        .hasMessageContaining("Permanent error detected, message will not be re-queued");
  }

  @Test
  void shouldRequeueWhenGenericRuntimeExceptionOccurs() {
    RuntimeException exception = new RuntimeException("Something unexpected happened");

    assertThatThrownBy(() -> underTest.handleError(exception))
        .isInstanceOf(RuntimeException.class)
        .isNotInstanceOf(AmqpRejectAndDontRequeueException.class)
        .hasMessageContaining("Transient error detected, message will be re-queued")
        .hasCause(exception);
  }
}
