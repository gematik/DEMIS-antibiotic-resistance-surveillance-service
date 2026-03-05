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
import static org.awaitility.Awaitility.await;

import de.gematik.demis.ars.service.batchprocessing.config.RabbitListenerIntegrationTest;
import de.gematik.demis.ars.service.batchprocessing.entity.Batch;
import de.gematik.demis.ars.service.repository.BatchRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "ars.batch-processing.enabled=true")
class BatchListenerIT extends RabbitListenerIntegrationTest {

  @Value("${ars.batch-processing.control-queue}")
  private String controlQueue;

  @Autowired private RabbitTemplate rabbitTemplate;
  @Autowired private BatchRepository batchRepository;

  private static final int NUMBER_OF_MESSAGES = 10;
  private static final String MESSAGE_PAYLOAD =
      String.format("{\"numberOfMessages\": %d}", NUMBER_OF_MESSAGES);

  @BeforeEach
  void setUp() {
    batchRepository.deleteAll();
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
        .atMost(3, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              Optional<Batch> saved = batchRepository.findById(batchId);
              assertThat(saved).isPresent();
              assertThat(saved.get().getNumberOfNotifications()).isEqualTo(NUMBER_OF_MESSAGES);
            });
  }

  @Test
  void saveBatchWithZeroNotificationsWhenNumberOfMessagesFieldIsMissing() {
    final UUID batchId = UUID.randomUUID();
    final String payloadWithoutNumberOfMessages = "{}";

    rabbitTemplate.send(
        controlQueue,
        MessageBuilder.withBody(payloadWithoutNumberOfMessages.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .setHeader(HEADER_BATCH_ID, batchId.toString())
            .build());

    await()
        .atMost(3, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              Optional<Batch> saved = batchRepository.findById(batchId);
              assertThat(saved).isPresent();
              assertThat(saved.get().getNumberOfNotifications()).isZero();
            });
  }

  @Test
  void doesNotSaveBatchWhenBatchIdHeaderIsMissing() {
    rabbitTemplate.send(
        controlQueue,
        MessageBuilder.withBody(MESSAGE_PAYLOAD.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build());

    await()
        .atMost(3, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(batchRepository.findAll()).isEmpty());
  }

  @Test
  void doesNotSaveBatchWhenBatchIdIsInvalidUuid() {
    rabbitTemplate.send(
        controlQueue,
        MessageBuilder.withBody(MESSAGE_PAYLOAD.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .setHeader(HEADER_BATCH_ID, "not-a-valid-uuid")
            .build());

    await()
        .atMost(3, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(batchRepository.findAll()).isEmpty());
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
        .atMost(3, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(batchRepository.findAll()).isEmpty());
  }
}
