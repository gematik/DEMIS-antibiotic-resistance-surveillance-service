package de.gematik.demis.ars.service.service.pseudonymisation;

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

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Service;

/** Provides functionality to pseudonymize {@link Patient} resources within a {@link Bundle}. */
@Slf4j
@Service
public class PseudonymisationService {

  private static final String UUID_PREFIX = "urn:uuid:";

  /**
   * Replaces the identifiers of {@link Patient} resources within the given {@link Bundle} with a
   * pseudonymized UUID.
   *
   * <p>This method iterates through all entries in the provided {@link Bundle} and modifies any
   * {@link Patient} resource, by removing existing identifiers and adding a pseudonymized
   * identifier (UUID).
   *
   * @param bundle the {@link Bundle} containing the resources to process
   */
  public void replacePatientIdentifier(Bundle bundle) {
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      Resource resource = entry.getResource();
      if (resource instanceof Patient patient) {
        removeIdentifiers(patient);
        addPseudonymizedIdentifier(patient);
      }
    }
  }

  private void removeIdentifiers(Patient patient) {
    patient.getIdentifier().clear();
  }

  private void addPseudonymizedIdentifier(Patient patient) {
    Identifier newIdentifier = new Identifier();
    newIdentifier.setSystem("https://demis.rki.de/fhir/sid/SurveillancePatientPseudonymPeriod");
    newIdentifier.setValue(generatePseudonym());
    patient.getIdentifier().add(newIdentifier);
  }

  private String generatePseudonym() {
    UUID tempUuid = UUID.fromString("10101010-1010-1010-1010-101010101010");
    return UUID_PREFIX + tempUuid;
  }
}
