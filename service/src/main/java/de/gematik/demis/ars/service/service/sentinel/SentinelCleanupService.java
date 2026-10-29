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

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.springframework.stereotype.Service;

/**
 * Performs the bundle cleanup steps used by sentinel processing. Each step returns {@code true} if
 * it removed something from the bundle (used for logging).
 */
@Service
class SentinelCleanupService {

  /** Removes patient addresses from the bundle. */
  public boolean removePatientAddress(final Bundle bundle) {
    boolean removed = false;
    for (final Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      final Resource resource = entry.getResource();
      if (resource instanceof Patient patient && patient.hasAddress()) {
        patient.getAddress().clear();
        removed = true;
      }
    }
    return removed;
  }

  /** Removes coverage resources and insurance references from the bundle. */
  public boolean removeCoverage(final Bundle bundle) {
    final boolean removed =
        bundle.getEntry().removeIf(entry -> entry.getResource() instanceof Coverage);

    for (final Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      final Resource resource = entry.getResource();
      if (resource instanceof ServiceRequest serviceRequest) {
        serviceRequest.getInsurance().clear();
      }
    }

    return removed;
  }

  /** Removes reason codes from service requests in the bundle. */
  public boolean removeReasonCodes(final Bundle bundle) {
    boolean removed = false;
    for (final Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      final Resource resource = entry.getResource();
      if (resource instanceof ServiceRequest serviceRequest && serviceRequest.hasReasonCode()) {
        serviceRequest.getReasonCode().clear();
        removed = true;
      }
    }
    return removed;
  }
}
