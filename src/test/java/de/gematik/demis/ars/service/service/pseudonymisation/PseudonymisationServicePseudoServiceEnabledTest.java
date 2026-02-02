package de.gematik.demis.ars.service.service.pseudonymisation;

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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.demis.ars.service.utils.TestUtils;
import de.gematik.demis.service.base.error.ServiceCallException;
import java.time.LocalDate;
import java.util.Optional;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    classes = {PseudonymisationService.class},
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "feature.flag.surveillance_pseudonym_service_enabled=true")
class PseudonymisationServicePseudoServiceEnabledTest {

  private static final String PSEUDONYM_ONE_OF_DEFAULT_BUNDLE =
      "urn:uuid:E2539906-3C28-4969-B150-724D6BAAC0D1";
  private static final String PSEUDONYM_TWO_OF_DEFAULT_BUNDLE =
      "urn:uuid:1836C39D-F0F5-420E-AACE-E476F3D3D440";
  private static final LocalDate REFERENCE_DATE = LocalDate.of(2023, 1, 4);

  private final TestUtils testUtils = new TestUtils();

  @MockitoBean SurveillancePseudonymServiceClient pseudonymServiceClientMock;
  @Autowired PseudonymisationService underTest;
  @Captor ArgumentCaptor<PseudonymRequest> requestCaptor;

  private static <T> Optional<T> findFirstResource(final Bundle bundle, final Class<T> clazz) {
    return bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(clazz::isInstance)
        .map(clazz::cast)
        .findFirst();
  }

  @Test
  void replacePatientIdentifier() {
    final Bundle bundle = testUtils.getDefaultBundle();

    final PseudonymResponse response =
        new PseudonymResponse(
            "http://my.test/Pseudo", "urn:uuid:10101010-1010-1010-1010-101010101010");

    when(pseudonymServiceClientMock.createPseudonym(any())).thenReturn(response);

    underTest.replacePatientIdentifier(bundle);

    final Patient patient = findFirstResource(bundle, Patient.class).orElseThrow();
    assertThat(patient.getIdentifier()).hasSize(1);
    final Identifier identifier = patient.getIdentifierFirstRep();
    assertThat(identifier.getSystem()).isEqualTo("http://my.test/Pseudo");
    assertThat(identifier.getValue()).isEqualTo("urn:uuid:10101010-1010-1010-1010-101010101010");

    verify(pseudonymServiceClientMock).createPseudonym(requestCaptor.capture());
    final PseudonymRequest request = requestCaptor.getValue();

    assertThat(request.pseudonym1()).isEqualTo(PSEUDONYM_ONE_OF_DEFAULT_BUNDLE);
    assertThat(request.pseudonym2()).isEqualTo(PSEUDONYM_TWO_OF_DEFAULT_BUNDLE);
    assertThat(request.date()).isEqualTo(REFERENCE_DATE);
  }

  @Test
  void serviceCallError() {
    final Bundle bundle = testUtils.getDefaultBundle();

    final ServiceCallException exception =
        new ServiceCallException("just for test", "Pseudo", 400, null);
    when(pseudonymServiceClientMock.createPseudonym(any())).thenThrow(exception);

    final Throwable actual = catchThrowable(() -> underTest.replacePatientIdentifier(bundle));
    assertThat(actual).isInstanceOf(ServiceCallException.class);
  }
}
