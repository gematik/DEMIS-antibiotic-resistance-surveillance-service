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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Sentinel-specific configuration.
 *
 * @param cacheRefreshAfterWrite interval after which a cached FUTS value set is refreshed on its
 *     next access
 * @param types value set names per sentinel type; every {@link SentinelType} must be configured
 */
@Validated
@ConfigurationProperties(prefix = "ars.sentinel")
public record SentinelConfigurationProperties(
    @NotNull @DurationMin(seconds = 1) Duration cacheRefreshAfterWrite,
    @NotNull Map<SentinelType, @Valid ValueSetNames> types) {

  public SentinelConfigurationProperties {
    if (types == null) {
      throw new IllegalStateException("Sentinel type configuration is missing.");
    }

    final Set<SentinelType> missingTypes = EnumSet.allOf(SentinelType.class);
    missingTypes.removeAll(types.keySet());
    if (!missingTypes.isEmpty()) {
      throw new IllegalStateException("Missing sentinel type configuration for: " + missingTypes);
    }
  }

  /**
   * Names (url) of the ValueSets for one sentinel type.
   *
   * @param codeValueSet value set matched against {@code Observation.code}
   * @param valueValueSet value set matched against {@code Observation.valueCodeableConcept}
   */
  record ValueSetNames(@NotBlank String codeValueSet, @NotBlank String valueValueSet) {}
}
