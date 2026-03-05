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

import de.gematik.demis.ars.service.service.NotificationService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class AsyncNotificationListenerConditionalTest {

  private static final String FEATURE_FLAG = "feature.flag.ars_bulk_enabled";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(TestConfiguration.class);

  @ParameterizedTest
  @ValueSource(strings = {"true"})
  void shouldCreateListenerBeanWhenFeatureFlagIsTrue(String value) {
    contextRunner
        .withPropertyValues(FEATURE_FLAG + "=" + value)
        .run(context -> assertThat(context).hasSingleBean(AsyncNotificationListener.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"false"})
  void shouldNotCreateListenerBeanWhenFeatureFlagIsFalse(String value) {
    contextRunner
        .withPropertyValues(FEATURE_FLAG + "=" + value)
        .run(context -> assertThat(context).doesNotHaveBean(AsyncNotificationListener.class));
  }

  @Configuration
  static class TestConfiguration {
    @Bean
    public DecryptionService decryptionService() {
      return mock(DecryptionService.class);
    }

    @Bean
    public NotificationService notificationService() {
      return mock(NotificationService.class);
    }

    @Bean
    @ConditionalOnProperty(name = FEATURE_FLAG, havingValue = "true")
    public AsyncNotificationListener asyncNotificationListener(
        DecryptionService decryptionService, NotificationService notificationService) {
      return new AsyncNotificationListener(decryptionService, notificationService);
    }
  }
}
