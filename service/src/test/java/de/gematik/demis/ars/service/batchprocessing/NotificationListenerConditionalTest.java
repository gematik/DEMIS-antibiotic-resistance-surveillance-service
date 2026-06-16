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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.gematik.demis.ars.service.batchprocessing.repository.BatchResultDAO;
import de.gematik.demis.ars.service.batchprocessing.resilience.RetryableNotificationProcessor;
import de.gematik.demis.service.base.security.crypto.AESEncryptionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

class NotificationListenerConditionalTest {

  private static final String FEATURE_FLAG = "ars.batch-processing.enabled";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(TestConfiguration.class);

  @Test
  void shouldCreateListenerBeanWhenFeatureFlagIsTrue() {
    contextRunner
        .withPropertyValues(FEATURE_FLAG + "=" + true)
        .run(context -> assertThat(context).hasSingleBean(NotificationListener.class));
  }

  @Test
  void shouldNotCreateListenerBeanWhenFeatureFlagIsFalse() {
    contextRunner
        .withPropertyValues(FEATURE_FLAG + "=" + false)
        .run(context -> assertThat(context).doesNotHaveBean(NotificationListener.class));
  }

  @Configuration
  @Import(NotificationListener.class)
  static class TestConfiguration {
    @Bean
    public AESEncryptionService encryptionService() {
      return mock(AESEncryptionService.class);
    }

    @Bean
    public RetryableNotificationProcessor notificationService() {
      return mock(RetryableNotificationProcessor.class);
    }

    @Bean
    public BatchResultDAO batchResultDAO() {
      return mock(BatchResultDAO.class);
    }

    @Bean
    public JsonMapper jsonMapper() {
      return mock(JsonMapper.class);
    }
  }
}
