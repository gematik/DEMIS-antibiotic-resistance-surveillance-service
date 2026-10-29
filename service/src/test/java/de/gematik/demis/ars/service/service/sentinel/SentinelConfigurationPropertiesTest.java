package de.gematik.demis.ars.service.service.sentinel;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SentinelConfigurationPropertiesTest {

  @Test
  void shouldAcceptValidConfiguration() {
    final var properties =
        new SentinelConfigurationProperties(
            Duration.ofHours(1),
            Map.of(
                SentinelType.MEASLES,
                new SentinelConfigurationProperties.ValueSetNames("m1", "m2"),
                SentinelType.CHLAMYDIA,
                new SentinelConfigurationProperties.ValueSetNames("c1", "c2")));

    assertThat(validate(properties)).isEmpty();
  }

  @Test
  void shouldRejectNonPositiveCacheRefreshDuration() {
    final var properties =
        new SentinelConfigurationProperties(
            Duration.ZERO,
            Map.of(
                SentinelType.MEASLES,
                new SentinelConfigurationProperties.ValueSetNames("m1", "m2"),
                SentinelType.CHLAMYDIA,
                new SentinelConfigurationProperties.ValueSetNames("c1", "c2")));

    assertThat(validate(properties))
        .anyMatch(
            violation -> violation.getPropertyPath().toString().equals("cacheRefreshAfterWrite"));
  }

  @Test
  void shouldRejectBlankValueSetSystems() {
    final var properties =
        new SentinelConfigurationProperties(
            Duration.ofHours(1),
            Map.of(
                SentinelType.MEASLES,
                new SentinelConfigurationProperties.ValueSetNames("", "m2"),
                SentinelType.CHLAMYDIA,
                new SentinelConfigurationProperties.ValueSetNames("c1", "c2")));

    assertThat(validate(properties))
        .anySatisfy(
            violation ->
                assertThat(violation.getPropertyPath()).hasToString("types[MEASLES].codeValueSet"));
  }

  @Test
  void shouldFailWhenATypeIsMissing() {
    assertThatThrownBy(SentinelConfigurationPropertiesTest::createMissingTypeConfiguration)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CHLAMYDIA");
  }

  private static Set<ConstraintViolation<SentinelConfigurationProperties>> validate(
      final SentinelConfigurationProperties properties) {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      return factory.getValidator().validate(properties);
    }
  }

  private static void createMissingTypeConfiguration() {
    new SentinelConfigurationProperties(
        Duration.ofHours(1),
        Map.of(
            SentinelType.MEASLES, new SentinelConfigurationProperties.ValueSetNames("m1", "m2")));
  }
}
