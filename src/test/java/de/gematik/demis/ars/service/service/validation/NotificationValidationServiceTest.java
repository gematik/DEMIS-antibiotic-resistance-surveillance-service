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
import static org.assertj.core.api.Assertions.assertThat;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.ERROR;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.FATAL;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.INFORMATION;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.NULL;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.WARNING;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_XML;

import de.gematik.demis.ars.service.exception.ArsValidationException;
import de.gematik.demis.ars.service.service.fhir.FhirOperationOutcomeOperationService;
import de.gematik.demis.ars.service.utils.TestUtils;
import de.gematik.demis.service.base.error.ServiceCallException;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class NotificationValidationServiceTest {

  private final TestUtils testUtil = new TestUtils();
  @Mock ValidationServiceClient client;
  @Mock FhirOperationOutcomeOperationService outcomeService;
  @InjectMocks private NotificationValidationService underTest;

  @BeforeEach
  void setUp() {
    underTest.setValidationEnabled(true);
    // outcomeService should only response what he was given
    lenient()
        .when(outcomeService.error(any(), any(), any(), any()))
        .thenAnswer((Answer<OperationOutcome>) invocationOnMock -> invocationOnMock.getArgument(0));
    lenient()
        .when(outcomeService.success(any()))
        .thenAnswer((Answer<OperationOutcome>) invocationOnMock -> invocationOnMock.getArgument(0));
  }

  @Test
  void shouldValidateSuccessfully() {
    String bundleString = testUtil.getDefaultBundleAsString(APPLICATION_JSON);
    when(client.validateJsonBundle(bundleString))
        .thenReturn(testUtil.createOutcomeResponse(INFORMATION));
    OperationOutcome outcome = underTest.validateFhir(bundleString, APPLICATION_JSON);
    assertThat(
            outcome.getIssue().stream()
                .map(OperationOutcome.OperationOutcomeIssueComponent::getSeverity))
        .isNotEmpty()
        .allMatch(severity -> severity.equals(INFORMATION));
  }

  @Test
  void shouldValidateSuccessfullyIfValidationWarning() {
    String bundleString = testUtil.getDefaultBundleAsString(APPLICATION_JSON);
    when(client.validateJsonBundle(bundleString))
        .thenReturn(testUtil.createOutcomeResponse(WARNING));
    OperationOutcome outcome = underTest.validateFhir(bundleString, APPLICATION_JSON);
    assertThat(
            outcome.getIssue().stream()
                .map(OperationOutcome.OperationOutcomeIssueComponent::getSeverity))
        .isNotEmpty()
        .allMatch(severity -> severity.equals(WARNING));
  }

  @Test
  void shouldCallJsonClientIfMediaTypeJson() {
    String bundleString = testUtil.getDefaultBundleAsString(APPLICATION_JSON);
    when(client.validateJsonBundle(bundleString))
        .thenReturn(testUtil.createOutcomeResponse(INFORMATION));
    OperationOutcome outcome = underTest.validateFhir(bundleString, APPLICATION_JSON);
    assertThat(
            outcome.getIssue().stream()
                .map(OperationOutcome.OperationOutcomeIssueComponent::getSeverity))
        .isNotEmpty()
        .allMatch(severity -> severity.equals(INFORMATION));
  }

  @Test
  void shouldCallXmlClientIfMediaTypeXml() {
    String bundleString = testUtil.getDefaultBundleAsString(APPLICATION_XML);
    when(client.validateXmlBundle(bundleString))
        .thenReturn(testUtil.createOutcomeResponse(INFORMATION));
    OperationOutcome outcome = underTest.validateFhir(bundleString, APPLICATION_XML);
    assertThat(
            outcome.getIssue().stream()
                .map(OperationOutcome.OperationOutcomeIssueComponent::getSeverity))
        .isNotEmpty()
        .allMatch(severity -> severity.equals(INFORMATION));
  }

  @Test
  void shouldThrowExceptionIfValidationError() {
    String bundleString = testUtil.getDefaultBundleAsString(APPLICATION_JSON);
    when(client.validateJsonBundle(bundleString)).thenReturn(testUtil.createOutcomeResponse(ERROR));
    ArsValidationException exception =
        assertThrows(
            ArsValidationException.class,
            () -> underTest.validateFhir(bundleString, APPLICATION_JSON));
    assertThat(exception.getErrorCode()).contains(FHIR_VALIDATION_ERROR.toString());
  }

  @Test
  void shouldThrowExceptionIfValidationFatal() {
    String bundleString = testUtil.getDefaultBundleAsString(APPLICATION_JSON);
    when(client.validateJsonBundle(bundleString)).thenReturn(testUtil.createOutcomeResponse(FATAL));
    ArsValidationException exception =
        assertThrows(
            ArsValidationException.class,
            () -> underTest.validateFhir(bundleString, APPLICATION_JSON));
    assertThat(exception.getErrorCode()).contains(FHIR_VALIDATION_FATAL.toString());
  }

  @Test
  void shouldThrowExceptionIfValidationCallFails() {
    String bundleString = testUtil.getDefaultBundleAsString(APPLICATION_JSON);
    when(client.validateJsonBundle(bundleString)).thenReturn(testUtil.createOutcomeResponse(NULL));
    ServiceCallException exception =
        assertThrows(
            ServiceCallException.class,
            () -> underTest.validateFhir(bundleString, APPLICATION_JSON));
    assertThat(exception.getErrorCode()).contains(VS);
  }

  @Test
  void shouldReturnPositiveOutcomeIfValidationDisabledAndDoNotCallClient() {
    underTest.setValidationEnabled(false);
    String bundleString = testUtil.getDefaultBundleAsString(APPLICATION_JSON);
    underTest.validateFhir(bundleString, APPLICATION_JSON);
    verify(outcomeService).generatePositiveOutcome();
    verify(client, times(0)).validateJsonBundle(any());
    verify(client, times(0)).validateXmlBundle(any());
  }
}
