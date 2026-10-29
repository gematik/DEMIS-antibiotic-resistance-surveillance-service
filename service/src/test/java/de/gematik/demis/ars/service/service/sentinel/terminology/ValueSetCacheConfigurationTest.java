package de.gematik.demis.ars.service.service.sentinel.terminology;

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
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import de.gematik.demis.ars.service.service.sentinel.SentinelConfigurationProperties;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ValueSetCacheConfigurationTest {

  private static final String SYSTEM = "urn:test:my-value-set";

  @Mock private SentinelConfigurationProperties props;
  @Mock private FutsClient futsClient;

  @Test
  void shouldCacheValueSets() {
    final Duration refreshAfterWrite = Duration.ofMillis(200);
    when(props.cacheRefreshAfterWrite()).thenReturn(refreshAfterWrite);

    final List<CodeKey> codes = List.of(new CodeKey("http://loinc.org", "LA6576-8"));
    final ValueSet valueSet = ValueSet.of(codes);
    when(futsClient.getValueSet(SYSTEM)).thenReturn(codes);

    final var underTest = new ValueSetCacheConfiguration(futsClient, props).sentinelValueSetCache();

    assertThat(underTest.get(SYSTEM)).isEqualTo(valueSet);
    verify(futsClient).getValueSet(SYSTEM);
    assertThat(underTest.get(SYSTEM)).isEqualTo(valueSet);
    verifyNoMoreInteractions(futsClient);
  }

  @Test
  void shouldRefresh() {
    final List<CodeKey> initialCodes = List.of(new CodeKey("http://loinc.org", "LA6576-8"));
    final List<CodeKey> refreshedCodes = List.of(new CodeKey("http://loinc.org", "LA9663-1"));
    final ValueSet initialValue = ValueSet.of(initialCodes);
    final ValueSet refreshedValue = ValueSet.of(refreshedCodes);
    when(futsClient.getValueSet(SYSTEM)).thenReturn(initialCodes).thenReturn(refreshedCodes);

    final Duration refreshAfterWrite = Duration.ofMillis(20);
    when(props.cacheRefreshAfterWrite()).thenReturn(refreshAfterWrite);

    final var underTest = new ValueSetCacheConfiguration(futsClient, props).sentinelValueSetCache();

    assertThat(underTest.get(SYSTEM)).isEqualTo(initialValue);
    await()
        .pollInterval(5, TimeUnit.MILLISECONDS)
        .atMost(1, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(underTest.get(SYSTEM)).isEqualTo(refreshedValue));
    // further refreshes may be triggered by polling, so only a minimum is guaranteed
    verify(futsClient, atLeast(2)).getValueSet(SYSTEM);
  }

  @Test
  void shouldKeepOldValueWhenRefreshFails() {
    final List<CodeKey> initialCodes = List.of(new CodeKey("http://loinc.org", "LA6576-8"));
    final ValueSet initialValue = ValueSet.of(initialCodes);
    when(futsClient.getValueSet(SYSTEM))
        .thenReturn(initialCodes)
        .thenThrow(new RuntimeException("refresh failed"));

    final Duration refreshAfterWrite = Duration.ofMillis(10);
    when(props.cacheRefreshAfterWrite()).thenReturn(refreshAfterWrite);

    final var underTest = new ValueSetCacheConfiguration(futsClient, props).sentinelValueSetCache();

    assertThat(underTest.get(SYSTEM)).isEqualTo(initialValue);
    await()
        .pollInterval(5, TimeUnit.MILLISECONDS)
        .atMost(50, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> {
              // call get cache to trigger refresh
              underTest.get(SYSTEM);
              verify(futsClient, atLeast(2)).getValueSet(SYSTEM);
            });
    // cache returns still old value (is not expired)
    assertThat(underTest.get(SYSTEM)).isEqualTo(initialValue);
  }
}
