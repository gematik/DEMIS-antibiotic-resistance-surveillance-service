package de.gematik.demis.ars.service.service.validation;

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

import static de.gematik.demis.ars.service.exception.ErrorCode.FHIR_VALIDATION_ERROR;
import static de.gematik.demis.ars.service.exception.ErrorCode.FHIR_VALIDATION_FATAL;
import static de.gematik.demis.ars.service.exception.ServiceCallErrorCode.VS;
import static de.gematik.demis.ars.service.service.validation.ValidationServiceClient.HEADER_FHIR_API_VERSION;
import static de.gematik.demis.ars.service.service.validation.ValidationServiceClient.HEADER_FHIR_PROFILE;
import static de.gematik.demis.ars.service.service.validation.ValidationServiceClient.HEADER_FHIR_PROFILE_OLD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.ERROR;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.FATAL;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.INFORMATION;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.NULL;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.WARNING;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_XML;

import de.gematik.demis.ars.service.exception.ArsValidationException;
import de.gematik.demis.ars.service.utils.TestUtils;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.service.base.error.ServiceCallException;
import de.gematik.demis.service.base.fhir.outcome.FhirOperationOutcomeService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

@ExtendWith(MockitoExtension.class)
class NotificationValidationServiceTest {

  private final TestUtils testUtil = new TestUtils();
  @Mock ValidationServiceClient client;
  @Mock FhirOperationOutcomeService outcomeService;
  @Mock HttpServletRequest httpServletRequest;
  @Captor ArgumentCaptor<HttpHeaders> headerCaptor;

  @InjectMocks private NotificationValidationService underTest;

  @BeforeEach
  void setUp() {
    underTest.setValidationEnabled(true);
    lenient()
        .when(outcomeService.allOk())
        .thenReturn(new OperationOutcome.OperationOutcomeIssueComponent().setSeverity(INFORMATION));
  }

  @Test
  void shouldValidateSuccessfully() {
    String bundleString = testUtil.getDefaultBundleAsString(APPLICATION_JSON);
    when(client.validateJsonBundle(any(), eq(bundleString)))
        .thenReturn(testUtil.createOutcomeResponse(INFORMATION));
    OperationOutcome outcome = underTest.validateFhir(bundleString, MessageType.JSON);
    assertThat(
            outcome.getIssue().stream()
                .map(OperationOutcome.OperationOutcomeIssueComponent::getSeverity))
        .isNotEmpty()
        .containsOnly(INFORMATION);
  }

  @Test
  void shouldValidateSuccessfullyIfValidationWarning() {
    String bundleString = testUtil.getDefaultBundleAsString(APPLICATION_JSON);
    when(client.validateJsonBundle(any(), eq(bundleString)))
        .thenReturn(testUtil.createOutcomeResponse(WARNING));
    OperationOutcome outcome = underTest.validateFhir(bundleString, MessageType.JSON);
    assertThat(
            outcome.getIssue().stream()
                .map(OperationOutcome.OperationOutcomeIssueComponent::getSeverity))
        .isNotEmpty()
        .contains(WARNING, INFORMATION);
  }

  @Test
  void shouldCallJsonClientIfMediaTypeJson() {
    String bundleString = testUtil.getDefaultBundleAsString(APPLICATION_JSON);
    when(client.validateJsonBundle(any(), eq(bundleString)))
        .thenReturn(testUtil.createOutcomeResponse(INFORMATION));
    OperationOutcome outcome = underTest.validateFhir(bundleString, MessageType.JSON);
    assertThat(
            outcome.getIssue().stream()
                .map(OperationOutcome.OperationOutcomeIssueComponent::getSeverity))
        .isNotEmpty()
        .allMatch(severity -> severity.equals(INFORMATION));
  }

  @Test
  void shouldCallXmlClientIfMediaTypeXml() {
    String bundleString = testUtil.getDefaultBundleAsString(APPLICATION_XML);
    when(client.validateXmlBundle(any(), eq(bundleString)))
        .thenReturn(testUtil.createOutcomeResponse(INFORMATION));
    OperationOutcome outcome = underTest.validateFhir(bundleString, MessageType.XML);
    assertThat(
            outcome.getIssue().stream()
                .map(OperationOutcome.OperationOutcomeIssueComponent::getSeverity))
        .isNotEmpty()
        .allMatch(severity -> severity.equals(INFORMATION));
  }

  @Test
  void shouldThrowExceptionIfValidationError() {
    String bundleString = testUtil.getDefaultBundleAsString(APPLICATION_JSON);
    when(client.validateJsonBundle(any(), eq(bundleString)))
        .thenReturn(testUtil.createOutcomeResponse(ERROR));
    ArsValidationException exception =
        assertThrows(
            ArsValidationException.class,
            () -> underTest.validateFhir(bundleString, MessageType.JSON));
    assertThat(exception.getErrorCode()).contains(FHIR_VALIDATION_ERROR.toString());
  }

  @Test
  void shouldThrowExceptionIfValidationFatal() {
    String bundleString = testUtil.getDefaultBundleAsString(APPLICATION_JSON);
    when(client.validateJsonBundle(any(), eq(bundleString)))
        .thenReturn(testUtil.createOutcomeResponse(FATAL));
    ArsValidationException exception =
        assertThrows(
            ArsValidationException.class,
            () -> underTest.validateFhir(bundleString, MessageType.JSON));
    assertThat(exception.getErrorCode()).contains(FHIR_VALIDATION_FATAL.toString());
  }

  @Test
  void shouldThrowExceptionIfValidationCallFails() {
    String bundleString = testUtil.getDefaultBundleAsString(APPLICATION_JSON);
    when(client.validateJsonBundle(any(), eq(bundleString)))
        .thenReturn(testUtil.createOutcomeResponse(NULL));
    ServiceCallException exception =
        assertThrows(
            ServiceCallException.class,
            () -> underTest.validateFhir(bundleString, MessageType.JSON));
    assertThat(exception.getErrorCode()).contains(VS);
  }

  @Test
  void shouldReturnPositiveOutcomeIfValidationDisabledAndDoNotCallClient() {
    underTest.setValidationEnabled(false);
    String bundleString = testUtil.getDefaultBundleAsString(APPLICATION_JSON);
    final OperationOutcome operationOutcome =
        underTest.validateFhir(bundleString, MessageType.JSON);
    verify(client, times(0)).validateJsonBundle(any(), any());
    verify(client, times(0)).validateXmlBundle(any(), any());
    assertThat(operationOutcome).isNotNull();
    assertThat(operationOutcome.getIssue())
        .hasSize(1)
        .first()
        .returns(INFORMATION, OperationOutcome.OperationOutcomeIssueComponent::getSeverity);
  }

  @Test
  void shouldSetHeader() {
    String bundleString = testUtil.getDefaultBundleAsString(APPLICATION_JSON);
    when(client.validateJsonBundle(any(), eq(bundleString)))
        .thenReturn(testUtil.createOutcomeResponse(INFORMATION));
    underTest.validateFhir(bundleString, MessageType.JSON);

    verify(client).validateJsonBundle(headerCaptor.capture(), eq(bundleString));
    assertThat(headerCaptor.getValue())
        .isNotNull()
        .hasSize(1)
        .containsKey(HEADER_FHIR_PROFILE_OLD)
        .extractingByKey(HEADER_FHIR_PROFILE_OLD)
        .isEqualTo(List.of("ars-profile-snapshots"));
  }

  @Nested
  class FeatureFlag_FEATURE_FLAG_NEW_API_ENDPOINTS {

    @Test
    void shouldForwardHeaderCorrectly() {
      underTest.setVersionHeaderForwardEnabled(true);
      String bundleString = testUtil.getDefaultBundleAsString(APPLICATION_JSON);
      String version = "v1";
      String profile = "igs-profile-snapshots";
      when(httpServletRequest.getHeader(HEADER_FHIR_API_VERSION)).thenReturn(version);
      when(httpServletRequest.getHeader(HEADER_FHIR_PROFILE)).thenReturn(profile);
      when(client.validateJsonBundle(any(), eq(bundleString)))
          .thenReturn(testUtil.createOutcomeResponse(INFORMATION));

      underTest.validateFhir(bundleString, MessageType.JSON);

      verify(client).validateJsonBundle(headerCaptor.capture(), eq(bundleString));
      assertThat(headerCaptor.getValue())
          .isNotNull()
          .hasSize(3)
          .containsKey(HEADER_FHIR_PROFILE)
          .extractingByKey(HEADER_FHIR_PROFILE)
          .isEqualTo(List.of(profile));
      assertThat(headerCaptor.getValue())
          .extractingByKey(HEADER_FHIR_API_VERSION)
          .isEqualTo(List.of(version));
    }

    @Test
    void shouldSetOldHeaderOnFeatureFlag() {
      underTest.setVersionHeaderForwardEnabled(true);
      String bundleString = testUtil.getDefaultBundleAsString(APPLICATION_JSON);
      when(client.validateJsonBundle(any(), eq(bundleString)))
          .thenReturn(testUtil.createOutcomeResponse(INFORMATION));
      underTest.validateFhir(bundleString, MessageType.JSON);

      verify(client).validateJsonBundle(headerCaptor.capture(), eq(bundleString));
      assertThat(headerCaptor.getValue())
          .isNotNull()
          .hasSize(1)
          .containsKey(HEADER_FHIR_PROFILE_OLD)
          .extractingByKey(HEADER_FHIR_PROFILE_OLD)
          .isEqualTo(List.of("ars-profile-snapshots"));
    }
  }
}
