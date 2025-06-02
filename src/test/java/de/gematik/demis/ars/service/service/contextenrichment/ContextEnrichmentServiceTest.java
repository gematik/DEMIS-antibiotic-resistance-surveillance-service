package de.gematik.demis.ars.service.service.contextenrichment;

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

import static de.gematik.demis.ars.service.parser.FhirParser.deserializeResource;
import static de.gematik.demis.ars.service.parser.FhirParser.serializeResource;
import static de.gematik.demis.ars.service.utils.TestUtils.PROVENANCE_RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import de.gematik.demis.ars.service.service.fhir.FhirBundleOperator;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContextEnrichmentServiceTest {

  TestUtils testUtils = new TestUtils();

  @Captor private ArgumentCaptor<Bundle.BundleEntryComponent> entityComponentCaptor;
  @Mock private ContextEnrichmentServiceClient contextEnrichmentServiceClient;
  @Mock FhirBundleOperator fhirBundleOperator;
  ContextEnrichmentService underTest;

  private Bundle bundle;
  private final String TOKEN = "SomeToken";

  @BeforeEach
  void setUp() {
    bundle = testUtils.getDefaultBundle();
    underTest = new ContextEnrichmentService(contextEnrichmentServiceClient, fhirBundleOperator);
  }

  @Test
  @DisplayName(
      "Test that if authorization is null, contextEnrichmentServiceClient doesn't got called")
  void testShouldNotInteractWithClientIfAuthorizationIsNotSet() {
    underTest.enrichBundleWithContextInformation(bundle, null);
    verifyNoInteractions(contextEnrichmentServiceClient);
  }

  @Test
  @DisplayName("Test that fhirParser is not called if client throws an error")
  void testFhirParserDoesNotGetCalledIfClientError() {
    when(contextEnrichmentServiceClient.enrichBundleWithContextInformation(any(), any()))
        .thenThrow(new RuntimeException("Some error"));
    underTest.enrichBundleWithContextInformation(bundle, TOKEN);
    verify(fhirBundleOperator, times(0)).addEntry(any(), any());
  }

  @Test
  @DisplayName("Test that the bundle have not been changed if client throws an error")
  void testIfTheSameBundleIsReturnedAsFallbackWhenClientError() {
    String bundleString = serializeResource(bundle, APPLICATION_JSON);
    when(contextEnrichmentServiceClient.enrichBundleWithContextInformation(any(), any()))
        .thenThrow(new RuntimeException("Some error"));
    underTest.enrichBundleWithContextInformation(bundle, TOKEN);
    assertThat(serializeResource(bundle, APPLICATION_JSON)).isEqualTo(bundleString);
  }

  @Test
  @DisplayName(
      "Test that the bundle have not been changed if the contextEnrichmentServiceClient returns invalid data")
  void testIfTheSameBundleIsReturnedAsFallbackWhenBundleHaveNoProvenance() {
    String bundleString = serializeResource(bundle, APPLICATION_JSON);
    when(contextEnrichmentServiceClient.enrichBundleWithContextInformation(any(), any()))
        .thenReturn("invalidJSONResponse");

    underTest.enrichBundleWithContextInformation(bundle, TOKEN);

    assertThat(serializeResource(bundle, APPLICATION_JSON)).isEqualTo(bundleString);
  }

  @Test
  @SneakyThrows
  @DisplayName("Test that the bundle enriched correctly")
  void testProvenanceGotAppendCorrectly() {
    String response = testUtils.readFileToString(PROVENANCE_RESOURCE);
    Provenance provenance =
        deserializeResource(
            testUtils.readFileToString(PROVENANCE_RESOURCE), APPLICATION_JSON, Provenance.class);
    when(contextEnrichmentServiceClient.enrichBundleWithContextInformation(
            TOKEN, testUtils.getDefaultCompositionId()))
        .thenReturn(response);
    when(fhirBundleOperator.getCompositionId(bundle))
        .thenReturn(testUtils.getDefaultCompositionId());
    underTest.enrichBundleWithContextInformation(bundle, TOKEN);

    assertAll(
        () -> verify(fhirBundleOperator).addEntry(eq(bundle), entityComponentCaptor.capture()),
        () ->
            assertThat(
                    serializeResource(
                        entityComponentCaptor.getValue().getResource(), APPLICATION_JSON))
                .isEqualTo(serializeResource(provenance, APPLICATION_JSON)),
        () ->
            assertThat(entityComponentCaptor.getValue().getFullUrl())
                .isEqualTo(
                    "https://demis.rki.de/fhir/Provenance/0161eba5-e6b2-401f-8966-2d1559abca56"),
        () -> assertThat(entityComponentCaptor.getValue().getFullUrl()).contains("Provenance"));
  }
}
