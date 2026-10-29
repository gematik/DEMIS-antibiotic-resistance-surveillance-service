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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Observation;
import org.springframework.stereotype.Service;

/** Detects sentinel types by matching Observation codes and values against ValueSets. */
@Service
@Slf4j
@RequiredArgsConstructor
class SentinelTypeDetection {

  private final SentinelConfigurationProperties properties;
  private final LoadingCache<String, ValueSet> valueSetCache;

  /** Detects all sentinel types found in the given bundle. */
  public Set<SentinelType> detect(final Bundle bundle) {
    final List<Observation> observations = getObservations(bundle);
    final EnumSet<SentinelType> detectedTypes = EnumSet.noneOf(SentinelType.class);

    for (final SentinelType sentinelType : SentinelType.values()) {
      final var names = properties.types().get(sentinelType);
      final ValueSet codeValueSet = getValueSet(names.codeValueSet());
      final ValueSet valueValueSet = getValueSet(names.valueValueSet());

      final boolean matches =
          observations.stream()
              .anyMatch(
                  observation ->
                      isContainedInValueSet(observation.getCode(), codeValueSet)
                          || isContainedInValueSet(
                              observation.getValueCodeableConcept(), valueValueSet));
      if (matches) {
        detectedTypes.add(sentinelType);
      }
    }

    return detectedTypes;
  }

  private List<Observation> getObservations(final Bundle bundle) {
    return bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(Observation.class::isInstance)
        .map(Observation.class::cast)
        .toList();
  }

  private boolean isContainedInValueSet(
      final CodeableConcept codeableConcept, final ValueSet valueSet) {
    return codeableConcept != null
        && codeableConcept.getCoding().stream().anyMatch(valueSet::contains);
  }

  /**
   * Returns the value set from the cache.
   *
   * <p>An exception can only occur if the value set has never been loaded successfully (e.g. FUTS
   * not reachable at first access). Once loaded, the cache keeps serving the last known value even
   * if a refresh fails. In the error case an empty value set is used, so no sentinel type is
   * detected and the conservative cleanup (remove everything) is applied. The processing is not
   * aborted.
   */
  private ValueSet getValueSet(final String system) {
    try {
      return valueSetCache.get(system);
    } catch (final RuntimeException ex) {
      log.error(
          "FUTS value set '{}' could not be loaded ({}). Continue with empty value set (not cached)",
          system,
          ex.toString());
      return ValueSet.EMPTY;
    }
  }
}
