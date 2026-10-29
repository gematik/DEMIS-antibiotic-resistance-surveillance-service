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

import com.github.benmanes.caffeine.cache.LoadingCache;
import de.gematik.demis.ars.service.service.sentinel.terminology.ValueSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Pre-loads all configured sentinel value sets into the cache on startup. Runs as {@link
 * ApplicationRunner}, so the application reports readiness only after the warmup has finished.
 * Failures are logged and do not prevent startup; value sets are then loaded lazily.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ars.sentinel.enabled", havingValue = "true", matchIfMissing = true)
class ValueSetCacheWarmup implements ApplicationRunner {

  private final LoadingCache<String, ValueSet> sentinelValueSetCache;
  private final SentinelConfigurationProperties properties;

  @Override
  public void run(final ApplicationArguments args) {
    final Set<String> valueSets =
        properties.types().values().stream()
            .flatMap(v -> Stream.of(v.codeValueSet(), v.valueValueSet()))
            .collect(Collectors.toSet());
    try {
      sentinelValueSetCache.getAll(valueSets);
      log.info("Sentinel value set cache warmed up: {}", valueSets);
    } catch (final RuntimeException ex) {
      log.warn("Sentinel value set cache warmup failed. Value sets will be loaded lazily.", ex);
    }
  }
}
