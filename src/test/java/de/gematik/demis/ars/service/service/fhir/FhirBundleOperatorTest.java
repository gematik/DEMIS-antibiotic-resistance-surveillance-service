package de.gematik.demis.ars.service.service.fhir;

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

import static de.gematik.demis.ars.service.utils.Constants.EXTENSION_URL_RECEPTION_TIME_STAMP_TYPE;
import static de.gematik.demis.ars.service.utils.TestUtils.NO_COMPOSITION_ARS_NOTIFICATION_JSON;
import static de.gematik.demis.ars.service.utils.TestUtils.VALID_ARS_NOTIFICATION_JSON;
import static de.gematik.demis.ars.service.utils.TestUtils.VALID_ARS_NOTIFICATION_TWO_SPECIMEN_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import ca.uhn.fhir.context.FhirContext;
import de.gematik.demis.ars.service.exception.ArsServiceException;
import de.gematik.demis.ars.service.exception.ErrorCode;
import de.gematik.demis.ars.service.utils.TestUtils;
import de.gematik.demis.fhirparserlibrary.FhirParser;
import java.util.Date;
import java.util.List;
import lombok.SneakyThrows;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Provenance;
import org.junit.jupiter.api.Test;

class FhirBundleOperatorTest {

  public static final String EXAMPLE_UUID = "1234-5678-9101-1121";
  TestUtils testUtils = new TestUtils();
  FhirBundleOperator underTest = new FhirBundleOperator(new FhirParser(FhirContext.forR4Cached()));

  @Test
  @SneakyThrows
  void shouldExtractBundleCorrectly() {
    Bundle bundle =
        underTest.parseBundleFromNotification(
            testUtils.readFileToString(VALID_ARS_NOTIFICATION_JSON), APPLICATION_JSON);
    assertThat(bundle).isNotNull();
  }

  @Test
  @SneakyThrows
  void shouldReplaceNotificationIdCorrectly() {
    Bundle bundle = testUtils.getDefaultBundle();
    underTest.enrichBundle(bundle, EXAMPLE_UUID);
    assertThat(bundle.getIdentifier().getValue()).isEqualTo(EXAMPLE_UUID);
  }

  @Test
  @SneakyThrows
  void shouldSetReceptionTimestampCorrectly() {
    Bundle bundle = testUtils.getDefaultBundle();
    underTest.enrichBundle(bundle, EXAMPLE_UUID);
    Composition composition = (Composition) bundle.getEntry().getFirst().getResource();
    assertNotNull(composition);
    assertThat(composition.getExtensionByUrl(EXTENSION_URL_RECEPTION_TIME_STAMP_TYPE)).isNotNull();
    assertThat(composition.getExtensionByUrl(EXTENSION_URL_RECEPTION_TIME_STAMP_TYPE).getValue())
        .isInstanceOf(DateTimeType.class);
    DateTimeType dateTimeType =
        (DateTimeType)
            composition.getExtensionByUrl(EXTENSION_URL_RECEPTION_TIME_STAMP_TYPE).getValue();
    assertThat(dateTimeType.getValue()).isNotNull();
    assertThat(dateTimeType.getValue())
        .isAfter(new DateTimeType(new Date(System.currentTimeMillis() - 1000)).getValue());
  }

  @Test
  @SneakyThrows
  void shouldSetLastUpdatedCorrectlyOnBundle() {
    Bundle bundle = testUtils.getDefaultBundle();
    underTest.enrichBundle(bundle, EXAMPLE_UUID);
    assertThat(bundle.getMeta().getLastUpdated()).isNotNull();
    assertThat(bundle.getMeta().getLastUpdated())
        .isAfter(new Date(System.currentTimeMillis() - 1000));
  }

  @Test
  void shouldThrowExceptionIfCompositionNotExist() {
    Bundle bundle = testUtils.readFileToBundle(NO_COMPOSITION_ARS_NOTIFICATION_JSON);
    ArsServiceException ex =
        assertThrows(ArsServiceException.class, () -> underTest.enrichBundle(bundle, EXAMPLE_UUID));
    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MISSING_RESOURCE.toString());
  }

  @Test
  @SneakyThrows
  void shouldParseOneSpecimenAccessionIdentifierCorrectly() {
    Bundle bundle = testUtils.readFileToBundle(VALID_ARS_NOTIFICATION_JSON);
    List<Identifier> identifierts = underTest.getSpecimenAccessionIdentifier(bundle);
    assertThat(identifierts).hasSize(1);
    assertThat(identifierts.getFirst().getSystem())
        .isEqualTo("http://www.Labor-Celle.de/identifiers/specimen/accessionIdentifier");
    assertThat(identifierts.getFirst().getValue()).isEqualTo("23-000034");
  }

  @Test
  @SneakyThrows
  void shouldParseTwoSpecimenAccessionIdentifierCorrectly() {
    Bundle bundle = testUtils.readFileToBundle(VALID_ARS_NOTIFICATION_TWO_SPECIMEN_JSON);
    List<Identifier> identifierts = underTest.getSpecimenAccessionIdentifier(bundle);
    assertThat(identifierts).hasSize(2);
    assertThat(identifierts.stream().map(Identifier::getSystem))
        .containsExactly(
            "http://www.Labor-Ostholstein.de/identifiers/specimen/accessionIdentifier",
            "http://www.Labor-Ostholstein.de/identifiers/specimen/accessionIdentifier");
    assertThat(identifierts.stream().map(Identifier::getValue))
        .containsExactly("O24-001081", "24-003257");
  }

  @Test
  void shouldAddEntryAndUpdateLastUpdated() {
    Bundle bundle = new Bundle();
    int initialEntryCount = bundle.getEntry().size();
    Provenance provenance = new Provenance();
    provenance.setId("Provenance/1");
    Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
    entry.setResource(provenance);
    underTest.addEntry(bundle, entry);
    assertThat(bundle.getEntry())
        .as("The bundle does not contain the correct number of entries")
        .hasSize(initialEntryCount + 1);
    assertThat(bundle.getEntry())
        .extracting("resource")
        .extracting("resourceType")
        .map(Object::toString)
        .containsExactlyInAnyOrder("Provenance");
  }
}
