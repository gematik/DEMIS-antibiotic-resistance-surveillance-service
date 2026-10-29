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

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.jupiter.api.Test;

class SentinelCleanupServiceTest {

  private final IParser jsonParser = FhirContext.forR4Cached().newJsonParser();

  private final SentinelCleanupService underTest = new SentinelCleanupService();

  private static final String PATIENT_WITH_ADDRESS =
"""
    {
      "resourceType": "Patient",
      "id": "2DA21025-AF83-3A4C-028A-F7118F7E2118",
      "meta": {
        "profile": [
          "https://demis.rki.de/fhir/ars/StructureDefinition/Patient"
        ]
      },
      "identifier": [
        {
          "system": "https://demis.rki.de/fhir/sid/SurveillancePatientPseudonym",
          "value": "urn:uuid:E2539906-3C28-4969-B150-724D6BAAC0D1"
        }
      ],
      "gender": "female",
      "birthDate": "1969-02",
      "address": [
        {
          "postalCode": "130"
        }
      ]
    }
""";

  private static final String SERVICE_REQUEST =
"""
{
  "resourceType": "ServiceRequest",
  "id": "6E217BD2-C513-9906-4902-63694FD409F9",
  "meta": {
    "profile": [
      "https://demis.rki.de/fhir/ars/StructureDefinition/ServiceRequest"
    ]
  },
  "status": "completed",
  "intent": "order",
  "code": {
    "coding": [
      {
        "system": "http://snomed.info/sct",
        "version": "http://snomed.info/sct/900000000000207008/version/20240801",
        "code": "168186002"
      }
    ]
  },
  "reasonCode": [
    {
      "coding": [
        {
          "system": "http://snomed.info/sct",
          "code": "168186002",
          "version": "http://snomed.info/sct/11000274103/version/20250515"
        }
      ]
    },
    {
      "coding": [
        {
          "system": "http://snomed.info/sct",
          "code": "168186002",
          "version": "http://snomed.info/sct/11000274103/version/20250515"
        }
      ]
    }
  ],
  "insurance": [
    {
      "reference": "Coverage/7d31688f-8e42-4742-be37-a066376f19cb"
    }
  ],
  "subject": {
    "reference": "Patient/2DA21025-AF83-3A4C-028A-F7118F7E2118"
  },
  "requester": {
    "reference": "PractitionerRole/594F5E05-B1DE-4778-BD08-B7B1236A07D1"
  },
  "specimen": [
    {
      "reference": "Specimen/2FFCC1F9-5EA8-7ECB-2AEF-AA06832C74E4"
    }
  ]
}
""";

  private static final String COVERAGE =
"""
{
  "resourceType": "Coverage",
  "id": "7d31688f-8e42-4742-be37-a066376f19cb",
  "meta": {
    "profile": [
      "https://demis.rki.de/fhir/ars/StructureDefinition/Coverage"
    ]
  },
  "status": "active",
  "type": {
    "coding": [
      {
        "system": "http://fhir.de/CodeSystem/versicherungsart-de-basis",
        "code": "GKV",
        "version": "1.5.3"
      }
    ]
  }
}
""";

  @Test
  void removePatientAddress() {
    final var bundle = new Bundle();
    final Patient patient = jsonParser.parseResource(Patient.class, PATIENT_WITH_ADDRESS);
    bundle.addEntry().setResource(patient);
    assertThat(patient.hasAddress()).isTrue();

    assertThat(underTest.removePatientAddress(bundle)).isTrue();

    assertThat(patient.hasAddress()).isFalse();
  }

  @Test
  void removePatientAddress_shouldDoNothing() {
    final var bundle = new Bundle();
    final Patient patient = jsonParser.parseResource(Patient.class, PATIENT_WITH_ADDRESS);
    bundle.addEntry().setResource(patient);
    patient.getAddress().clear();
    assertThat(patient.hasAddress()).isFalse();
    final var bundleWithoutAddressSnapshot = jsonParser.encodeResourceToString(bundle);

    assertThat(underTest.removePatientAddress(bundle)).isFalse();

    assertThat(patient.hasAddress()).isFalse();
    assertThat(jsonParser.encodeResourceToString(bundle)).isEqualTo(bundleWithoutAddressSnapshot);
  }

  @Test
  void removeCoverage() {
    final var bundle = new Bundle();
    bundle.addEntry().setResource(jsonParser.parseResource(Coverage.class, COVERAGE));
    final var serviceRequest = jsonParser.parseResource(ServiceRequest.class, SERVICE_REQUEST);
    bundle.addEntry().setResource(serviceRequest);

    assertThat(serviceRequest.hasInsurance()).isTrue();
    assertThat(bundle.getEntry()).hasSize(2);
    assertThat(bundle.getEntryFirstRep().getResource()).isInstanceOf(Coverage.class);

    assertThat(underTest.removeCoverage(bundle)).isTrue();

    assertThat(serviceRequest.hasInsurance()).isFalse();
    assertThat(bundle.getEntry()).hasSize(1);
    assertThat(bundle.getEntryFirstRep().getResource()).isSameAs(serviceRequest);
  }

  @Test
  void removeCoverage_shouldDoNothing() {
    final var bundle = new Bundle();
    final var serviceRequest = jsonParser.parseResource(ServiceRequest.class, SERVICE_REQUEST);
    bundle.addEntry().setResource(serviceRequest);
    serviceRequest.getInsurance().clear();

    final var bundleWithoutCoverageSnapshot = jsonParser.encodeResourceToString(bundle);
    assertThat(bundleWithoutCoverageSnapshot).doesNotContain("Coverage");

    assertThat(underTest.removeCoverage(bundle)).isFalse();

    assertThat(serviceRequest.hasInsurance()).isFalse();
    assertThat(jsonParser.encodeResourceToString(bundle)).isEqualTo(bundleWithoutCoverageSnapshot);
  }

  @Test
  void removeReasonCodes() {
    final var bundle = new Bundle();
    final var serviceRequest = jsonParser.parseResource(ServiceRequest.class, SERVICE_REQUEST);
    bundle.addEntry().setResource(serviceRequest);
    assertThat(serviceRequest.getReasonCode()).isNotEmpty();

    assertThat(underTest.removeReasonCodes(bundle)).isTrue();

    assertThat(serviceRequest.getReasonCode()).isEmpty();
  }

  @Test
  void removeReasonCodes_shouldDoNothing() {
    final var bundle = new Bundle();
    final var serviceRequest = jsonParser.parseResource(ServiceRequest.class, SERVICE_REQUEST);
    bundle.addEntry().setResource(serviceRequest);
    serviceRequest.getReasonCode().clear();
    assertThat(serviceRequest.getReasonCode()).isEmpty();
    final var bundleWithoutReasonCodesSnapshot = jsonParser.encodeResourceToString(bundle);

    assertThat(underTest.removeReasonCodes(bundle)).isFalse();
    assertThat(jsonParser.encodeResourceToString(bundle))
        .isEqualTo(bundleWithoutReasonCodesSnapshot);
  }
}
