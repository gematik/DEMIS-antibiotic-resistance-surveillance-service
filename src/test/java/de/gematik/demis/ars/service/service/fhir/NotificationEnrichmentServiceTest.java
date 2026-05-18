package de.gematik.demis.ars.service.service.fhir;

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

import static de.gematik.demis.ars.service.utils.Constants.EXTENSION_URL_RECEPTION_TIME_STAMP_TYPE;
import static de.gematik.demis.ars.service.utils.TestUtils.NO_COMPOSITION_ARS_NOTIFICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.gematik.demis.ars.service.exception.ArsServiceException;
import de.gematik.demis.ars.service.exception.ErrorCode;
import de.gematik.demis.ars.service.service.NotificationContext;
import de.gematik.demis.ars.service.utils.TestUtils;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.SneakyThrows;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Provenance;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class NotificationEnrichmentServiceTest {

  private static final String EXAMPLE_UUID = "1234-5678-9101-1121";
  private static final NotificationContext HTTP_CONTEXT =
      NotificationContext.fromHttpRequest(new HttpHeaders());

  private final NotificationEnrichmentService underTest =
      new NotificationEnrichmentService(new NotificationReader());

  private final TestUtils testUtils = new TestUtils();

  @Test
  @SneakyThrows
  void shouldReplaceNotificationIdCorrectly() {
    Bundle bundle = testUtils.getDefaultBundle();
    underTest.updateBundle(bundle, EXAMPLE_UUID, HTTP_CONTEXT);
    assertThat(bundle.getIdentifier().getValue()).isEqualTo(EXAMPLE_UUID);
  }

  @Test
  @SneakyThrows
  void shouldSetReceptionTimestampCorrectly() {
    Bundle bundle = testUtils.getDefaultBundle();
    underTest.updateBundle(bundle, EXAMPLE_UUID, HTTP_CONTEXT);
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
    underTest.updateBundle(bundle, EXAMPLE_UUID, HTTP_CONTEXT);
    assertThat(bundle.getMeta().getLastUpdated()).isNotNull();
    assertThat(bundle.getMeta().getLastUpdated())
        .isAfter(new Date(System.currentTimeMillis() - 1000));
  }

  @Test
  void shouldSetTagForRkiCorrectlyWithHttpContext() {
    Bundle bundle = testUtils.getDefaultBundle();
    underTest.updateBundle(bundle, EXAMPLE_UUID, HTTP_CONTEXT);
    assertThat(bundle.getMeta().getTag()).hasSize(1);
    assertThat(bundle.getMeta().getTag().getFirst().getCode()).isEqualTo("1.");
    assertThat(bundle.getMeta().getTag().getFirst().getSystem())
        .isEqualTo("https://demis.rki.de/fhir/CodeSystem/ResponsibleDepartment");
  }

  @Test
  void shouldSetTagForRkiCorrectlyWithBatchContext() {
    Bundle bundle = testUtils.getDefaultBundle();
    final NotificationContext batchContext =
        NotificationContext.fromMessageQueue(Map.of(), UUID.randomUUID());
    underTest.updateBundle(bundle, EXAMPLE_UUID, batchContext);
    final List<Coding> tags = bundle.getMeta().getTag();
    assertThat(tags)
        .anySatisfy(
            tag -> {
              assertThat(tag.getSystem())
                  .isEqualTo("https://demis.rki.de/fhir/CodeSystem/ResponsibleDepartment");
              assertThat(tag.getCode()).isEqualTo("1.");
            });
  }

  @Test
  void shouldSetBatchIdAsMetaTag() {
    final Bundle bundle = testUtils.getDefaultBundle();
    final UUID batchId = UUID.randomUUID();
    final NotificationContext batchContext =
        NotificationContext.fromMessageQueue(Map.of(), batchId);
    underTest.updateBundle(bundle, EXAMPLE_UUID, batchContext);
    final List<Coding> tags = bundle.getMeta().getTag();
    assertThat(tags)
        .anySatisfy(
            tag -> {
              assertThat(tag.getSystem()).isEqualTo("https://demis.rki.de/fhir/CodeSystem/BatchId");
              assertThat(tag.getCode()).isEqualTo(batchId.toString());
            });
  }

  @Test
  void shouldNotSetBatchIdInHttpContext() {
    final Bundle bundle = testUtils.getDefaultBundle();
    underTest.updateBundle(bundle, EXAMPLE_UUID, HTTP_CONTEXT);
    final List<Coding> tags = bundle.getMeta().getTag();
    assertThat(tags)
        .noneMatch(tag -> tag.getSystem().equals("https://demis.rki.de/fhir/CodeSystem/BatchId"));
  }

  @Test
  void shouldThrowExceptionIfCompositionNotExist() {
    Bundle bundle = testUtils.readFileToBundle(NO_COMPOSITION_ARS_NOTIFICATION_JSON);
    ArsServiceException ex =
        assertThrows(
            ArsServiceException.class,
            () -> underTest.updateBundle(bundle, EXAMPLE_UUID, HTTP_CONTEXT));
    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MISSING_RESOURCE.toString());
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
