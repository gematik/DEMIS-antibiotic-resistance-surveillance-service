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

import static java.util.Objects.requireNonNullElse;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import de.gematik.demis.ars.service.service.sentinel.SentinelConfigurationProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the value set cache.
 *
 * <p>Intentionally there is no expiry: only a few entries exist, so there is no memory risk, and
 * stale values are preferred over no values (value sets only grow additively). Failed loads are not
 * cached; a failed refresh keeps the old value.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ValueSetCacheConfiguration {

  private final FutsClient futsClient;
  private final SentinelConfigurationProperties properties;

  @Bean
  public LoadingCache<String, ValueSet> sentinelValueSetCache() {
    return Caffeine.newBuilder()
        .refreshAfterWrite(properties.cacheRefreshAfterWrite())
        .build(this::loadValueSet);
  }

  private ValueSet loadValueSet(final String system) {
    final List<CodeKey> codeKeys = requireNonNullElse(futsClient.getValueSet(system), List.of());
    log.info("FUTS value set '{}' loaded with {} codes", system, codeKeys.size());
    return ValueSet.of(codeKeys);
  }
}
