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

import jakarta.annotation.PostConstruct;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiFunction;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Orchestrates sentinel detection and cleanup. */
@Service
@RequiredArgsConstructor
@Slf4j
public class SentinelService {

  private final SentinelTypeDetection typeDetection;
  private final SentinelCleanupService cleanupService;

  @Value("${ars.sentinel.enabled}")
  @Setter(AccessLevel.PACKAGE)
  private boolean featureFlagSentinelEnabled;

  @PostConstruct
  void logFF() {
    log.info("FEATURE_FLAG_ARS_SENTINEL_ENABLED = {}", featureFlagSentinelEnabled);
  }

  /**
   * Applies sentinel cleanup rules based on the detected types.
   *
   * <ul>
   *   <li>CHLAMYDIA: nothing is removed (takes precedence over MASERN).
   *   <li>MAESLES: patient address and coverage are removed, reason codes are kept.
   *   <li>Otherwise: patient address, coverage and reason codes are removed.
   * </ul>
   *
   * Conservative by design: if the FUTS value sets could not be loaded (only possible if they were
   * never loaded successfully before), no type is detected and everything is removed. The
   * processing is not aborted in this case.
   */
  public void removeSentinelData(final Bundle bundle) {
    if (!featureFlagSentinelEnabled) {
      legacyLogic(bundle);
      return;
    }

    final Set<SentinelType> detectedTypes = typeDetection.detect(bundle);
    final Set<CleanupStep> cleanupSteps = determineCleanupSteps(detectedTypes);

    final Set<String> removedResources = new LinkedHashSet<>();
    cleanupSteps.forEach(
        cleanupStep -> {
          if (cleanupStep.getCleanupAction().apply(cleanupService, bundle)) {
            removedResources.add(cleanupStep.getResourceName());
          }
        });

    if (!removedResources.isEmpty()) {
      log.info("Sentinel information removed: {}", removedResources);
    }
  }

  private Set<CleanupStep> determineCleanupSteps(final Set<SentinelType> detectedTypes) {
    final EnumSet<CleanupStep> cleanupSteps = EnumSet.allOf(CleanupStep.class);

    if (detectedTypes.contains(SentinelType.CHLAMYDIA)) {
      cleanupSteps.clear();
    }

    if (detectedTypes.contains(SentinelType.MEASLES)) {
      cleanupSteps.remove(CleanupStep.REASON_CODE);
    }

    return cleanupSteps;
  }

  private void legacyLogic(final Bundle bundle) {
    cleanupService.removePatientAddress(bundle);
    cleanupService.removeCoverage(bundle);
    cleanupService.removeReasonCodes(bundle);
  }

  @RequiredArgsConstructor
  @Getter
  private enum CleanupStep {
    PATIENT_ADDRESS("Patient.Address", SentinelCleanupService::removePatientAddress),
    COVERAGE("Coverage", SentinelCleanupService::removeCoverage),
    REASON_CODE("ReasonCode", SentinelCleanupService::removeReasonCodes);

    private final String resourceName;
    private final BiFunction<SentinelCleanupService, Bundle, Boolean> cleanupAction;
  }
}
