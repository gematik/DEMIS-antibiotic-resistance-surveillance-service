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

import de.gematik.demis.ars.service.batchprocessing.entity.BatchEntity;
import de.gematik.demis.ars.service.batchprocessing.repository.BatchRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class BatchControlListenerTest {

  private static final UUID BATCH_UUID = UUID.randomUUID();
  private static final String BATCH_ID = BATCH_UUID.toString();
  private static final int NUMBER_OF_NOTIFICATIONS = 5;
  private static final String MESSAGE = "{\"numberOfMessages\":" + NUMBER_OF_NOTIFICATIONS + "}";

  @Mock private BatchRepository repository;

  private BatchControlListener underTest;

  private Map<String, Object> headers;

  @BeforeEach
  void setUp() {
    underTest = new BatchControlListener(repository, new JsonMapper());

    headers = new HashMap<>();
    headers.put(HEADER_BATCH_ID, BATCH_ID);
  }

  @Test
  @SneakyThrows
  void shouldSaveBatch() {

    underTest.processMessage(MESSAGE, headers);

    ArgumentCaptor<BatchEntity> captor = forClass(BatchEntity.class);
    verify(repository).save(captor.capture());
    BatchEntity saved = captor.getValue();
    assertThat(saved.getBatchId()).isEqualTo(BATCH_UUID);
    assertThat(saved.getNumberOfNotifications()).isEqualTo(NUMBER_OF_NOTIFICATIONS);
  }

  @Test
  void onMissingBatchIdHeaderThrowsIllegalArgumentException() {
    headers.remove(HEADER_BATCH_ID);

    assertThatThrownBy(() -> underTest.processMessage(MESSAGE, headers))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(HEADER_BATCH_ID);

    verify(repository, never()).save(any());
  }

  @Test
  void onNullBatchIdHeaderThrowsIllegalArgumentException() {
    headers.put(HEADER_BATCH_ID, null);

    assertThatThrownBy(() -> underTest.processMessage(MESSAGE, headers))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(HEADER_BATCH_ID);

    verify(repository, never()).save(any());
  }

  @Test
  void onInvalidUuidInHeaderThrowsIllegalArgumentException() {
    headers.put(HEADER_BATCH_ID, "not-a-valid-uuid");

    assertThatThrownBy(() -> underTest.processMessage(MESSAGE, headers))
        .isInstanceOf(IllegalArgumentException.class);

    verify(repository, never()).save(any());
  }

  @Test
  @SneakyThrows
  void onMalformedJsonThrowsException() {
    String malformedMessage = "{ invalid json }";

    assertThatThrownBy(() -> underTest.processMessage(malformedMessage, headers))
        .isInstanceOf(JacksonException.class)
        .hasMessageContaining("Unexpected character");

    verify(repository, never()).save(any());
  }

  @Test
  @SneakyThrows
  void onEmptyMessageThrowsException() {
    String emptyMessage = "{}";

    assertThatThrownBy(() -> underTest.processMessage(emptyMessage, headers))
        .isInstanceOf(JacksonException.class)
        .hasMessageContaining("Cannot map `null` into type `int`");

    verify(repository, never()).save(any());
  }
}
