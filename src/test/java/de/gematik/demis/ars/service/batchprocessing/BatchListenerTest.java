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

import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageHeaderConstants.HEADER_BATCH_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.demis.ars.service.batchprocessing.entity.Batch;
import de.gematik.demis.ars.service.batchprocessing.messages.BatchMessage;
import de.gematik.demis.ars.service.repository.BatchRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BatchListenerTest {

  private static final UUID BATCH_UUID = UUID.randomUUID();
  private static final String BATCH_ID = BATCH_UUID.toString();
  private static final int NUMBER_OF_NOTIFICATIONS = 5;

  @Mock private BatchRepository repository;
  @Mock private ObjectMapper objectMapper;

  @InjectMocks private BatchListener batchListener;

  private Map<String, Object> headers;
  private BatchMessage batchMessage;

  @BeforeEach
  void setUp() {
    headers = new HashMap<>();
    headers.put(HEADER_BATCH_ID, BATCH_ID);

    batchMessage = new BatchMessage(NUMBER_OF_NOTIFICATIONS);
  }

  @Test
  @SneakyThrows
  void shouldSaveBatch() {
    String message = "{\"numberOfMessages\":5}";
    when(objectMapper.readValue(message, BatchMessage.class)).thenReturn(batchMessage);

    batchListener.processMessage(message, headers);

    ArgumentCaptor<Batch> captor = forClass(Batch.class);
    verify(repository).save(captor.capture());
    Batch saved = captor.getValue();
    assertThat(saved.getBatchId()).isEqualTo(BATCH_UUID);
    assertThat(saved.getNumberOfNotifications()).isEqualTo(NUMBER_OF_NOTIFICATIONS);
  }

  @Test
  void onMissingBatchIdHeaderThrowsIllegalArgumentException() {
    headers.remove(HEADER_BATCH_ID);

    assertThatThrownBy(() -> batchListener.processMessage("{}", headers))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(HEADER_BATCH_ID);

    verify(repository, never()).save(any());
  }

  @Test
  void onNullBatchIdHeaderThrowsIllegalArgumentException() {
    headers.put(HEADER_BATCH_ID, null);

    assertThatThrownBy(() -> batchListener.processMessage("{}", headers))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(HEADER_BATCH_ID);

    verify(repository, never()).save(any());
  }

  @Test
  void onInvalidUuidInHeaderThrowsIllegalArgumentException() {
    headers.put(HEADER_BATCH_ID, "not-a-valid-uuid");

    assertThatThrownBy(() -> batchListener.processMessage("{}", headers))
        .isInstanceOf(IllegalArgumentException.class);

    verify(repository, never()).save(any());
  }

  @Test
  @SneakyThrows
  void onMalformedJsonThrowsJsonProcessingException() {
    String malformedMessage = "{ invalid json }";
    when(objectMapper.readValue(malformedMessage, BatchMessage.class))
        .thenThrow(new JsonParseException(null, "Unexpected character"));

    assertThatThrownBy(() -> batchListener.processMessage(malformedMessage, headers))
        .isInstanceOf(JsonProcessingException.class);

    verify(repository, never()).save(any());
  }
}
