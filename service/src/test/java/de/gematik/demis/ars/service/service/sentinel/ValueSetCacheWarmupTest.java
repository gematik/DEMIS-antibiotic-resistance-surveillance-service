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

import static de.gematik.demis.ars.service.service.sentinel.SentinelType.CHLAMYDIA;
import static de.gematik.demis.ars.service.service.sentinel.SentinelType.MEASLES;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.LoadingCache;
import de.gematik.demis.ars.service.service.sentinel.SentinelConfigurationProperties.ValueSetNames;
import de.gematik.demis.ars.service.service.sentinel.terminology.ValueSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class ValueSetCacheWarmupTest {

  private static final ApplicationArguments NO_ARGS = new DefaultApplicationArguments();

  @Mock private LoadingCache<String, ValueSet> sentinelValueSetCache;
  @Mock private SentinelConfigurationProperties properties;

  @InjectMocks private ValueSetCacheWarmup underTest;

  @BeforeEach
  void setUp() {
    when(properties.types())
        .thenReturn(
            Map.of(
                MEASLES, new ValueSetNames("masern-code", "masern-value"),
                CHLAMYDIA, new ValueSetNames("chlamydien-code", "chlamydien-value")));
  }

  @Test
  void loadsAllConfiguredValueSets() {
    underTest.run(NO_ARGS);

    verify(sentinelValueSetCache)
        .getAll(Set.of("masern-code", "masern-value", "chlamydien-code", "chlamydien-value"));
  }

  @Test
  void doesNotFailOnLoadingError() {
    when(sentinelValueSetCache.getAll(anyIterable()))
        .thenThrow(new IllegalStateException("FUTS unavailable"));

    assertThatCode(() -> underTest.run(NO_ARGS)).doesNotThrowAnyException();
  }
}
