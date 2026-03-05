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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.demis.ars.service.batchprocessing.entity.Batch;
import de.gematik.demis.ars.service.batchprocessing.messages.BatchMessage;
import de.gematik.demis.ars.service.repository.BatchRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ listener responsible for processing incoming batch control messages.
 *
 * <p>Listens on the queue configured by {@code ars.batch-processing.control-queue} and persists a
 * {@link Batch} record for each received message. The listener is only activated when the property
 * {@code ars.batch-processing.enabled} is set to {@code true}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty("ars.batch-processing.enabled")
public class BatchListener {
  private final BatchRepository repository;
  private final ObjectMapper objectMapper;

  /**
   * Processes a batch control message received from RabbitMQ.
   *
   * <p>Extracts the batch UUID from the AMQP header {@code batchId}, parses the number of
   * notifications from the JSON message body, creates a new {@link Batch} entity and persists it
   * via {@link BatchRepository}.
   *
   * @param message JSON-encoded {@link BatchMessage} payload containing {@code numberOfMessages}
   * @param amqpHeaders AMQP message headers; must contain the {@code batchId} header
   * @throws JsonProcessingException if the message body cannot be deserialized
   * @throws IllegalArgumentException if the {@code batchId} header is missing or not a valid UUID
   */
  @RabbitListener(queues = "${ars.batch-processing.control-queue}")
  public void processMessage(String message, @Headers Map<String, Object> amqpHeaders)
      throws JsonProcessingException {
    String batchId = extractBatchId(amqpHeaders);
    UUID batchUuid = parseBatchUuid(batchId);
    int numberOfNotifications = extractNumberOfNotifications(message);

    Batch batch = new Batch();
    batch.setBatchId(batchUuid);
    batch.setNumberOfNotifications(numberOfNotifications);
    repository.save(batch);

    log.info("Closed batchId: {} with {} notifications", batchId, numberOfNotifications);
  }

  /**
   * Extracts the batch ID string from the given AMQP headers.
   *
   * @param amqpHeaders the AMQP message headers map
   * @return the batch ID as a non-null string
   * @throws IllegalArgumentException if the {@code batchId} header is absent or {@code null}
   */
  private String extractBatchId(Map<String, Object> amqpHeaders) {
    return Optional.ofNullable(amqpHeaders.get(HEADER_BATCH_ID))
        .map(Object::toString)
        .orElseThrow(
            () -> new IllegalArgumentException("Missing required AMQP header: " + HEADER_BATCH_ID));
  }

  /**
   * Parses a {@link UUID} from the given batch ID string.
   *
   * @param batchId the string representation of the batch UUID
   * @return the parsed {@link UUID}
   * @throws IllegalArgumentException if {@code batchId} is not a valid UUID string
   */
  private UUID parseBatchUuid(String batchId) {
    try {
      return UUID.fromString(batchId);
    } catch (IllegalArgumentException e) {
      log.error("Invalid UUID format for batchId: {}", batchId);
      throw e;
    }
  }

  /**
   * Deserializes the JSON message body and returns the number of notifications.
   *
   * @param message JSON-encoded {@link BatchMessage}
   * @return the number of notifications declared in the message
   * @throws JsonProcessingException if the message cannot be parsed as a {@link BatchMessage}
   */
  private int extractNumberOfNotifications(String message) throws JsonProcessingException {
    BatchMessage batchMessage = objectMapper.readValue(message, BatchMessage.class);
    return batchMessage.numberOfMessages();
  }
}
