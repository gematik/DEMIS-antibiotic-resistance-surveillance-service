package de.gematik.demis.ars.service.batchprocessing.test;

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

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("batch-test")
public class RabbitConfig {

  public static final String TEST_DLQ_CONTROL = "test.dlq.control";
  public static final String TEST_DLQ_SECURE = "test.dlq.secure";

  @Bean
  public Queue bulkControlQueue(
      @Value("${ars.batch-processing.control-queue}") final String controlQueueName) {
    return QueueBuilder.durable(controlQueueName)
        .withArgument("x-dead-letter-exchange", "")
        .withArgument("x-dead-letter-routing-key", TEST_DLQ_CONTROL)
        .build();
  }

  @Bean
  public Queue bulkSecureQueue(
      @Value("${ars.batch-processing.secure-queue}") final String secureQueueName) {
    return QueueBuilder.durable(secureQueueName)
        .withArgument("x-dead-letter-exchange", "")
        .withArgument("x-dead-letter-routing-key", TEST_DLQ_SECURE)
        .build();
  }

  // only for testing. dead letter queues do not exist in production
  @Bean
  public Queue deadLetterQueueControl() {
    return QueueBuilder.durable(TEST_DLQ_CONTROL).build();
  }

  @Bean
  public Queue deadLetterQueueSecure() {
    return QueueBuilder.durable(TEST_DLQ_SECURE).build();
  }
}
