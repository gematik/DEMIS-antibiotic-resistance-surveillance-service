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
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import de.gematik.demis.ars.service.batchprocessing.entity.BatchEntity;
import de.gematik.demis.ars.service.batchprocessing.repository.BatchRepository;
import de.gematik.demis.ars.service.batchprocessing.test.RabbitAndPostgresTestContainer;
import de.gematik.demis.ars.service.batchprocessing.test.RabbitConfig;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("batch-test")
class BatchControlListenerSystemTest extends RabbitAndPostgresTestContainer {

  private static final Duration MAX_WAIT_TIMEOUT = Duration.of(2, SECONDS);
  private static final int NUMBER_OF_MESSAGES = 10;
  private static final String MESSAGE_PAYLOAD =
      String.format("{\"numberOfMessages\": %d}", NUMBER_OF_MESSAGES);

  @Value("${ars.batch-processing.control-queue}")
  private String controlQueue;

  @Autowired private RabbitTemplate rabbitTemplate;

  @MockitoSpyBean private BatchControlListener batchControlListener;
  @MockitoSpyBean private BatchRepository batchRepository;

  @BeforeEach
  void setUp() {
    batchRepository.deleteAll();
    // clear queues
    rabbitTemplate.receive(controlQueue);
    getDlqMessageNonBlocking();
  }

  @Test
  void saveBatchWhenValidCloseMessageReceived() {
    final UUID batchId = UUID.randomUUID();

    rabbitTemplate.send(
        controlQueue,
        MessageBuilder.withBody(MESSAGE_PAYLOAD.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .setHeader(HEADER_BATCH_ID, batchId.toString())
            .build());

    await()
        .atMost(MAX_WAIT_TIMEOUT)
        .untilAsserted(
            () -> {
              Optional<BatchEntity> saved = batchRepository.findById(batchId);
              assertThat(saved).isPresent();
              assertThat(saved.get().getNumberOfNotifications()).isEqualTo(NUMBER_OF_MESSAGES);
            });
  }

  @Test
  void doesNotSaveBatchWhenBatchIdHeaderIsMissing() {
    rabbitTemplate.send(
        controlQueue,
        MessageBuilder.withBody(MESSAGE_PAYLOAD.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build());

    await().atMost(MAX_WAIT_TIMEOUT).untilAsserted(() -> assertDlqContainsMessage(null));

    assertThat(batchRepository.findAll()).isEmpty();
  }

  @Test
  void doesNotSaveBatchWhenBatchIdIsInvalidUuid() {
    final String invalidBatchId = "not-a-valid-uuid";
    rabbitTemplate.send(
        controlQueue,
        MessageBuilder.withBody(MESSAGE_PAYLOAD.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .setHeader(HEADER_BATCH_ID, invalidBatchId)
            .build());

    await().atMost(MAX_WAIT_TIMEOUT).untilAsserted(() -> assertDlqContainsMessage(invalidBatchId));

    assertThat(batchRepository.findAll()).isEmpty();
  }

  @Test
  void doesNotSaveBatchWhenPayloadIsMalformedJson() {
    final UUID batchId = UUID.randomUUID();
    final String malformedPayload = "not-json";

    rabbitTemplate.send(
        controlQueue,
        MessageBuilder.withBody(malformedPayload.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .setHeader(HEADER_BATCH_ID, batchId.toString())
            .build());

    await()
        .atMost(MAX_WAIT_TIMEOUT)
        .untilAsserted(() -> assertThat(batchRepository.findAll()).isEmpty());
  }

  @Test
  void dbExceptionShouldMessageRequeued() {
    final UUID batchId = UUID.randomUUID();

    final var batchRespositorySaveRealAnswer =
        Mockito.mockingDetails(batchRepository).getMockCreationSettings().getDefaultAnswer();

    Mockito.doThrow(new RuntimeException("just for test"))
        .doAnswer(batchRespositorySaveRealAnswer)
        .when(batchRepository)
        .save(any());

    rabbitTemplate.send(
        controlQueue,
        MessageBuilder.withBody(MESSAGE_PAYLOAD.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .setHeader(HEADER_BATCH_ID, batchId.toString())
            .build());

    await()
        .atMost(MAX_WAIT_TIMEOUT)
        .untilAsserted(
            () -> {
              Optional<BatchEntity> saved = batchRepository.findById(batchId);
              assertThat(saved).isPresent();
              assertThat(saved.get().getNumberOfNotifications()).isEqualTo(NUMBER_OF_MESSAGES);
            });

    verify(batchControlListener, times(2)).processMessage(any(), any());

    assertThat(getDlqMessageNonBlocking()).isNull();
  }

  @Test
  void otherExceptionShouldRejectMessage() {
    final String batchId = UUID.randomUUID().toString();

    doThrow(new RuntimeException("don't requeue me"))
        .when(batchControlListener)
        .processMessage(any(), any());

    rabbitTemplate.send(
        controlQueue,
        MessageBuilder.withBody(MESSAGE_PAYLOAD.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .setHeader(HEADER_BATCH_ID, batchId)
            .build());

    await().atMost(MAX_WAIT_TIMEOUT).untilAsserted(() -> assertDlqContainsMessage(batchId));

    verify(batchControlListener, times(1)).processMessage(any(), any());
  }

  private void assertDlqContainsMessage(final String batchId) {
    final Message dlqMessage = getDlqMessageNonBlocking();
    assertThat(dlqMessage).isNotNull();
    assertThat((String) dlqMessage.getMessageProperties().getHeader(HEADER_BATCH_ID))
        .isEqualTo(batchId);
  }

  @Nullable
  private Message getDlqMessageNonBlocking() {
    return rabbitTemplate.receive(RabbitConfig.TEST_DLQ_CONTROL);
  }
}
