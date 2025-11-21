package de.gematik.demis.ars.service.service.sentinel;

/*-
 * #%L
 * Antibiotic-Resistance-Surveillance-Service
 * %%
 * Copyright (C) 2025 gematik GmbH
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
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.springframework.stereotype.Service;

/**
 * Service that performs in-place removal of sentinel sensitive data from a FHIR {@link Bundle}. The
 * following modifications are applied:
 *
 * <ul>
 *   <li>Clears all addresses from {@link Patient} resources.
 *   <li>Removes all {@link Coverage} resources from the bundle.
 *   <li>Clears Coverage references from {@link ServiceRequest} resources.
 *   <li>Clears reason codes from {@link ServiceRequest} resources.
 * </ul>
 */
@Service
@Slf4j
public class SentinelService {

  /**
   * Removes sentinel data from the provided bundle. Modifies the bundle in-place; no copy is
   * created.
   *
   * @param bundle the FHIR bundle to sanitize; must be non-null
   */
  public void removeSentinelData(Bundle bundle) {
    removePatientAddress(bundle);
    removeCoverage(bundle);
    removeReasonCodes(bundle);
  }

  private void removePatientAddress(Bundle bundle) {
    for (final Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      final Resource resource = entry.getResource();
      if (resource instanceof Patient patient) {
        patient.getAddress().clear();
      }
    }
  }

  private void removeCoverage(Bundle bundle) {
    // remove Coverage resources from the bundle
    bundle.getEntry().removeIf(entry -> entry.getResource() instanceof Coverage);
    // remove references to Coverage resources in ServiceRequest resources
    for (final Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      final Resource resource = entry.getResource();
      if (resource instanceof ServiceRequest serviceRequest) {
        serviceRequest.getInsurance().clear();
      }
    }
  }

  private void removeReasonCodes(Bundle bundle) {
    for (final Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      final Resource resource = entry.getResource();
      if (resource instanceof ServiceRequest serviceRequest) {
        serviceRequest.getReasonCode().clear();
      }
    }
  }
}
