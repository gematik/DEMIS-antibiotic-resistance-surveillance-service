package de.gematik.demis.ars.service.service.contextenrichment;

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

import static de.gematik.demis.ars.service.utils.TestUtils.PROVENANCE_RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.gematik.demis.ars.service.service.fhir.FhirParser;
import de.gematik.demis.ars.service.service.fhir.NotificationEnrichmentService;
import de.gematik.demis.ars.service.service.fhir.NotificationReader;
import de.gematik.demis.ars.service.utils.TestUtils;
import lombok.SneakyThrows;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Provenance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

@ExtendWith(MockitoExtension.class)
class ContextEnrichmentServiceTest {

  TestUtils testUtils = new TestUtils();

  @Captor private ArgumentCaptor<Bundle.BundleEntryComponent> entityComponentCaptor;
  @Mock private ContextEnrichmentServiceClient contextEnrichmentServiceClient;
  @Mock private FhirParser fhirParser;
  @Mock private NotificationReader notificationReader;
  @Mock private NotificationEnrichmentService notificationEnrichmentService;
  @InjectMocks private ContextEnrichmentService underTest;

  private Bundle bundle;
  private static final String TOKEN = "SomeToken";

  @BeforeEach
  void setUp() {
    bundle = testUtils.getDefaultBundle();
  }

  @Test
  @DisplayName(
      "Test that if authorization is null, contextEnrichmentServiceClient doesn't got called")
  void testShouldNotInteractWithClientIfAuthorizationIsNotSet() {
    underTest.enrichBundleWithContextInformation(bundle, null);
    verifyNoInteractions(contextEnrichmentServiceClient);
  }

  @Test
  @DisplayName(
      "Test that no provenance resource is added to notification if client throws an error")
  void testNoProvenanceIsAddedToNotificationIfClientError() {
    when(contextEnrichmentServiceClient.enrichBundleWithContextInformation(any(), any()))
        .thenThrow(new RuntimeException("Some error"));
    String bundleString = testUtils.resourceToJson(bundle);
    underTest.enrichBundleWithContextInformation(bundle, TOKEN);
    verify(notificationEnrichmentService, never()).addEntry(any(), any());
    assertThat(testUtils.resourceToJson(bundle)).isEqualTo(bundleString);
  }

  @Test
  @DisplayName(
      "Test that the bundle have not been changed if the contextEnrichmentServiceClient returns invalid data")
  void testIfTheSameBundleIsReturnedAsFallbackWhenBundleHaveNoProvenance() {
    String bundleString = testUtils.resourceToJson(bundle);
    when(contextEnrichmentServiceClient.enrichBundleWithContextInformation(any(), any()))
        .thenReturn("invalidJSONResponse");

    underTest.enrichBundleWithContextInformation(bundle, TOKEN);

    assertThat(testUtils.resourceToJson(bundle)).isEqualTo(bundleString);
  }

  @Test
  @SneakyThrows
  @DisplayName("Test that the bundle enriched correctly")
  void testProvenanceGotAppendCorrectly() {
    final String response = testUtils.readFileToString(PROVENANCE_RESOURCE);
    final Provenance provenance =
        testUtils.jsonToResource(testUtils.readFileToString(PROVENANCE_RESOURCE), Provenance.class);
    when(contextEnrichmentServiceClient.enrichBundleWithContextInformation(
            TOKEN, testUtils.getDefaultCompositionId()))
        .thenReturn(response);
    when(fhirParser.deserializeResource(response, MediaType.APPLICATION_JSON, Provenance.class))
        .thenReturn(provenance);
    when(notificationReader.getCompositionId(bundle))
        .thenReturn(testUtils.getDefaultCompositionId());

    underTest.enrichBundleWithContextInformation(bundle, TOKEN);

    assertAll(
        () ->
            verify(notificationEnrichmentService)
                .addEntry(eq(bundle), entityComponentCaptor.capture()),
        () ->
            assertThat(testUtils.resourceToJson(entityComponentCaptor.getValue().getResource()))
                .isEqualTo(testUtils.resourceToJson(provenance)),
        () ->
            assertThat(entityComponentCaptor.getValue().getFullUrl())
                .isEqualTo(
                    "https://demis.rki.de/fhir/Provenance/0161eba5-e6b2-401f-8966-2d1559abca56"),
        () -> assertThat(entityComponentCaptor.getValue().getFullUrl()).contains("Provenance"));
  }
}
