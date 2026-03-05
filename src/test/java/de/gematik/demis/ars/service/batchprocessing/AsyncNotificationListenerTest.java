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

import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageHeaderConstants.HEADER_AUTHORIZATION;
import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageHeaderConstants.HEADER_BATCH_ID;
import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageHeaderConstants.HEADER_DOCUMENT_ID;
import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageHeaderConstants.HEADER_TYPE;
import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageType.ERROR;
import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageType.NOTIFICATION;
import static de.gematik.demis.ars.service.service.validation.ValidationServiceClient.HEADER_FHIR_API_VERSION;
import static de.gematik.demis.ars.service.service.validation.ValidationServiceClient.HEADER_FHIR_PROFILE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.demis.ars.service.service.NotificationContext;
import de.gematik.demis.ars.service.service.NotificationService;
import de.gematik.demis.fhirparserlibrary.MessageType;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AsyncNotificationListenerTest {

  private static final String ENCRYPTED_MESSAGE = "some-encrypted-payload";
  private static final String DECRYPTED_MESSAGE = "some-decrypted-payload";
  private static final String ENCRYPTED_AUTHORIZATION = "some-encrypted-authorization";
  private static final String DECRYPTED_AUTHORIZATION = "some-decrypted-authorization";
  @Mock private NotificationService notificationService;
  @Mock private DecryptionService decryptionService;
  @InjectMocks private AsyncNotificationListener underTest;
  @Captor private ArgumentCaptor<NotificationContext> notificationContextCaptor;

  @Test
  void shouldDelegateWithHeaders() {
    Map<String, Object> headers = new HashMap<>();
    headers.put(HEADER_AUTHORIZATION, ENCRYPTED_AUTHORIZATION);
    headers.put(HEADER_TYPE, NOTIFICATION.name());
    headers.put(HEADER_BATCH_ID, "BATCH_ID");
    headers.put(HEADER_DOCUMENT_ID, "DOCUMENT_ID");
    headers.put(HEADER_FHIR_API_VERSION, "FHIR_API_VERSION");
    headers.put(HEADER_FHIR_PROFILE, "FHIR_PROFILE");

    when(decryptionService.decryptData(ENCRYPTED_MESSAGE)).thenReturn(DECRYPTED_MESSAGE);
    when(decryptionService.decryptData(ENCRYPTED_AUTHORIZATION))
        .thenReturn(DECRYPTED_AUTHORIZATION);

    underTest.processMessage(ENCRYPTED_MESSAGE, headers);

    verify(notificationService, times(1))
        .process(
            eq(DECRYPTED_MESSAGE),
            eq(MessageType.JSON),
            eq(DECRYPTED_AUTHORIZATION),
            notificationContextCaptor.capture());
    NotificationContext capturedContext = notificationContextCaptor.getValue();
    assertThat(capturedContext.getHeaders()).isEqualTo(headers);
  }

  @Test
  @SneakyThrows
  void shouldNotProcessIfTypeIsError() {
    Map<String, Object> headers = new HashMap<>();
    headers.put(HEADER_AUTHORIZATION, ENCRYPTED_AUTHORIZATION);
    headers.put(HEADER_TYPE, ERROR);
    underTest.processMessage(ENCRYPTED_MESSAGE, headers);

    Mockito.verifyNoInteractions(decryptionService);
    Mockito.verifyNoInteractions(notificationService);
  }
}
