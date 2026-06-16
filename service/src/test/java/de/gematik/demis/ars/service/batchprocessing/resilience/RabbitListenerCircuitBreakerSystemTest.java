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

import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageHeaderConstants.HEADER_BATCH_ID;
import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageHeaderConstants.HEADER_DOCUMENT_ID;
import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageHeaderConstants.HEADER_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import de.gematik.demis.ars.service.batchprocessing.BatchControlListener;
import de.gematik.demis.ars.service.batchprocessing.NotificationListener;
import de.gematik.demis.ars.service.batchprocessing.test.RabbitAndPostgresTestContainer;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "ars.batch-processing.circuit-breaker.scheduler-reconnect-check-fixed-delay-ms=100",
      "spring.rabbitmq.listener.simple.concurrency=2"
    })
@ActiveProfiles("batch-test")
@Slf4j
class RabbitListenerCircuitBreakerSystemTest extends RabbitAndPostgresTestContainer {

  private static final String ERROR_MESSAGE = "{\"error\":\"WAF\"}";
  private static final String CLOSE_MESSAGE = "{\"numberOfMessages\": 10}";

  @Value("${ars.batch-processing.secure-queue}")
  private String notificationQueue;

  @Value("${ars.batch-processing.control-queue}")
  private String controlQueue;

  @Autowired private RabbitTemplate rabbitTemplate;
  @Autowired private RabbitListenerEndpointRegistry rabbitListenerEndpointRegistry;
  @MockitoBean private RetryableNotificationProcessor processorMock;
  @MockitoSpyBean private BatchControlListener batchControlListener;
  @MockitoSpyBean private NotificationListener notificationListener;
  @MockitoSpyBean private DataSource dataSource;

  private enum QueueEnum {
    NOTIFICATION,
    CONTROL
  }

  @ParameterizedTest
  @EnumSource(QueueEnum.class)
  void all(final QueueEnum queue) throws Exception {
    log.info("simulate database is down...");
    doThrow(new SQLException("just for test")).when(dataSource).getConnection();

    // fill queue with 3 messages -> 2 will be processed from 2 threads concurrently. The third must
    // wait.
    final int messageCount = 3;
    for (int i = 1; i <= messageCount; ++i) {
      sendMessage(queue, "QUEUED-" + i);
    }

    // wait until listener is stopped
    await().atMost(6, TimeUnit.SECONDS).untilAsserted(() -> assertListenerRunning(false));
    assertListenerCalls(queue, 2);
    Mockito.reset(notificationListener, batchControlListener);

    // test that listener really not works -> NotificationListener is not called.
    await().during(500, TimeUnit.MILLISECONDS).untilAsserted(() -> assertListenerCalls(queue, 0));

    log.info("database works normally now...");
    // with reset getConnection does not throw any exception anymore
    Mockito.reset(dataSource);

    // wait until rabbit listener is running again
    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertListenerRunning(true));

    // now all messages must be processed again.
    await()
        .atMost(2, TimeUnit.SECONDS)
        .untilAsserted(() -> assertListenerCalls(queue, messageCount));
  }

  private void assertListenerCalls(final QueueEnum queue, int wanted) {
    switch (queue) {
      case CONTROL -> verify(batchControlListener, times(wanted)).processMessage(any(), any());
      case NOTIFICATION -> verify(notificationListener, times(wanted)).processMessage(any(), any());
    }
  }

  private void assertListenerRunning(final boolean shouldRunning) {
    assertThat(rabbitListenerEndpointRegistry.isRunning()).isEqualTo(shouldRunning);
  }

  private void sendMessage(final QueueEnum queue, final String documentId) {
    final String queueName = queue == QueueEnum.CONTROL ? controlQueue : notificationQueue;
    final String message = queue == QueueEnum.CONTROL ? CLOSE_MESSAGE : ERROR_MESSAGE;
    log.info("sending message '{}' to queue {}", documentId, queueName);
    rabbitTemplate.send(
        queueName,
        MessageBuilder.withBody(message.getBytes())
            .setHeader(HEADER_BATCH_ID, UUID.randomUUID())
            .setHeader(HEADER_DOCUMENT_ID, documentId)
            .setHeader(HEADER_TYPE, "ERROR")
            .build());
  }
}
