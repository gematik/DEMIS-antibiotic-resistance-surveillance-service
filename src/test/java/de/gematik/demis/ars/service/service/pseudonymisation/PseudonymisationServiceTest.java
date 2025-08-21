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

import static de.gematik.demis.ars.service.utils.TestUtils.ARS_NOTIFICATION_INVALID_PATIENT_IDENTIFIER_JSON;
import static de.gematik.demis.ars.service.utils.TestUtils.ARS_NOTIFICATION_NO_PATIENT_IDENTIFIER_JSON;
import static de.gematik.demis.ars.service.utils.TestUtils.ARS_NOTIFICATION_THREE_PATIENT_IDENTIFIER;
import static de.gematik.demis.ars.service.utils.TestUtils.VALID_ARS_NOTIFICATION_JSON;
import static de.gematik.demis.ars.service.utils.TestUtils.VALID_ARS_NOTIFICATION_XML;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

import de.gematik.demis.ars.service.utils.TestUtils;
import java.util.HashSet;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PseudonymisationServiceTest {

  TestUtils testUtils = new TestUtils();

  private final PseudonymisationService pseudonymisationService = new PseudonymisationService(null);
  private static final String FIXED_UUID_PREFIX = "urn:uuid:";
  private static final String FIXED_UUID = "10101010-1010-1010-1010-101010101010";
  private static final String FIXED_SYSTEM =
      "https://demis.rki.de/fhir/sid/SurveillancePatientPseudonym";

  @ParameterizedTest
  @ValueSource(
      strings = {
        VALID_ARS_NOTIFICATION_JSON,
        VALID_ARS_NOTIFICATION_XML,
        ARS_NOTIFICATION_NO_PATIENT_IDENTIFIER_JSON,
        ARS_NOTIFICATION_INVALID_PATIENT_IDENTIFIER_JSON,
        ARS_NOTIFICATION_THREE_PATIENT_IDENTIFIER
      })
  void testReplaceIds(String testDataPath) {
    Bundle bundle = testUtils.readFileToBundle(testDataPath);

    pseudonymisationService.replacePatientIdentifier(bundle);

    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      if (entry.getResource() instanceof Patient patient) {
        assertThat(patient.getIdentifier()).hasSize(1);
        Identifier newIdentifier = patient.getIdentifierFirstRep();
        assertThat(newIdentifier.getSystem()).isEqualTo(FIXED_SYSTEM);
        assertThat(newIdentifier.getValue()).isEqualTo(FIXED_UUID_PREFIX + FIXED_UUID);
      }
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        VALID_ARS_NOTIFICATION_JSON,
        VALID_ARS_NOTIFICATION_XML,
        ARS_NOTIFICATION_NO_PATIENT_IDENTIFIER_JSON,
        ARS_NOTIFICATION_THREE_PATIENT_IDENTIFIER
      })
  void shouldNotContainOldIdentifiersAfterPseudonymisation(String testDataPath) {
    Bundle bundle = testUtils.readFileToBundle(testDataPath);

    Set<String> oldIdentifiers = new HashSet<>();
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      if (entry.getResource() instanceof Patient) {
        Patient patient = (Patient) entry.getResource();
        for (Identifier identifier : patient.getIdentifier()) {
          oldIdentifiers.add(identifier.getValue());
        }
      }
    }
    pseudonymisationService.replacePatientIdentifier(bundle);

    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      if (entry.getResource() instanceof Patient) {
        Patient patient = (Patient) entry.getResource();
        for (Identifier identifier : patient.getIdentifier()) {
          String newIdentifier = identifier.getValue();
          assertFalse(
              oldIdentifiers.contains(newIdentifier),
              "New identifier value '"
                  + newIdentifier
                  + "' matches an old identifier in the bundle.");
        }
      }
    }
  }
}
