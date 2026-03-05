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
import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageHeaderConstants.HEADER_TYPE;
import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageType.ERROR;

import de.gematik.demis.ars.service.service.NotificationContext;
import de.gematik.demis.ars.service.service.NotificationProcessingResult;
import de.gematik.demis.ars.service.service.NotificationService;
import de.gematik.demis.fhirparserlibrary.MessageType;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty("ars.batch-processing.enabled")
public class AsyncNotificationListener {

  private final DecryptionService decryptionService;
  private final NotificationService notificationService;

  /**
   * Processes an incoming encrypted notification message from the RabbitMQ queue.
   *
   * <p>This method is invoked automatically by Spring AMQP when a message arrives in the {@code
   * bulk.secure} queue.
   *
   * @param message the encrypted message body from the queue
   * @param amqpHeaders the AMQP message headers containing
   * @see NotificationContext#fromAmqpHeaders(Map)
   */
  @RabbitListener(queues = "${ars.batch-processing.secure-queue}")
  public void processMessage(String message, @Headers Map<String, Object> amqpHeaders) {
    if (amqpHeaders.get(HEADER_TYPE).equals(ERROR)) {
      // TODO: DB interaction for WAF error
      return;
    }
    String decryptedMessage = decryptionService.decryptData(message);
    String decryptedAuthorization =
        decryptionService.decryptData(amqpHeaders.get(HEADER_AUTHORIZATION).toString());
    NotificationContext context = NotificationContext.fromAmqpHeaders(amqpHeaders);

    NotificationProcessingResult processingResult =
        notificationService.process(
            decryptedMessage, MessageType.JSON, decryptedAuthorization, context);
    log.info("Processed message successfully: {}", processingResult);
  }
}
