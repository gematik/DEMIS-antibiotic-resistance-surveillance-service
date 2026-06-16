package de.gematik.demis.ars.service.service.validation;

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

import static de.gematik.demis.ars.service.exception.ErrorCode.FHIR_VALIDATION_ERROR;
import static de.gematik.demis.ars.service.exception.ErrorCode.FHIR_VALIDATION_FATAL;
import static de.gematik.demis.ars.service.exception.ServiceCallErrorCode.VS;
import static de.gematik.demis.ars.service.utils.TestUtils.HEADER_FHIR_API_VERSION_LEGACY;
import static de.gematik.demis.ars.service.utils.TestUtils.HEADER_FHIR_PACKAGE;
import static de.gematik.demis.ars.service.utils.TestUtils.HEADER_FHIR_PACKAGE_VERSION;
import static de.gematik.demis.ars.service.utils.TestUtils.HEADER_FHIR_PROFILE_LEGACY;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_XML;

import ca.uhn.fhir.context.FhirContext;
import de.gematik.demis.ars.service.exception.ArsValidationException;
import de.gematik.demis.ars.service.service.NotificationContext;
import de.gematik.demis.ars.service.service.fhir.FhirParser;
import de.gematik.demis.ars.service.utils.TestUtils;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.service.base.error.ServiceCallException;
import de.gematik.demis.service.base.fhir.outcome.FhirOperationOutcomeService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.util.MultiValueMap;

@ExtendWith(MockitoExtension.class)
class NotificationValidationServiceTest {

  private final TestUtils testUtil = new TestUtils();
  @Mock ValidationServiceClient client;
  @Mock FhirOperationOutcomeService outcomeService;

  private NotificationValidationService underTest;

  @Captor ArgumentCaptor<MultiValueMap<String, String>> headerCaptor;
  private final NotificationContext context =
      NotificationContext.fromHttpRequest(new HttpHeaders());

  @BeforeEach
  void setUp() {
    final FhirParser fhirParser = new FhirParser(FhirContext.forR4Cached());
    underTest = new NotificationValidationService(client, outcomeService, fhirParser);
    lenient()
        .when(outcomeService.allOk())
        .thenReturn(new OperationOutcome.OperationOutcomeIssueComponent().setSeverity(INFORMATION));
  }

  @Test
  void shouldValidateSuccessfully() {
    String bundleString = testUtil.getDefaultBundleAsString(APPLICATION_JSON);
    when(client.validateJsonBundle(any(), eq(bundleString)))
        .thenReturn(testUtil.createOutcomeResponse(INFORMATION));
    OperationOutcome outcome = underTest.validateFhir(bundleString, MessageType.JSON, context);
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
    OperationOutcome outcome = underTest.validateFhir(bundleString, MessageType.JSON, context);
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
    OperationOutcome outcome = underTest.validateFhir(bundleString, MessageType.JSON, context);
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
    OperationOutcome outcome = underTest.validateFhir(bundleString, MessageType.XML, context);
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
            () -> underTest.validateFhir(bundleString, MessageType.JSON, context));
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
            () -> underTest.validateFhir(bundleString, MessageType.JSON, context));
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
            () -> underTest.validateFhir(bundleString, MessageType.JSON, context));
    assertThat(exception.getErrorCode()).contains(VS);
  }

  @ParameterizedTest
  @MethodSource("routingHeaders")
  void shouldSetRoutingHeaders(
      final String packageVersionHeaderName, final String packageHeaderName) {
    final String bundleString = testUtil.getDefaultBundleAsString(APPLICATION_JSON);
    when(client.validateJsonBundle(any(), eq(bundleString)))
        .thenReturn(testUtil.createOutcomeResponse(INFORMATION));
    final String fhirPackageVersion = "v1";
    final String fhirPackage = "ars-profile";
    final Map<String, String> headers =
        Map.of(packageVersionHeaderName, fhirPackageVersion, packageHeaderName, fhirPackage);
    final NotificationContext contextForRoutingHeaders =
        new NotificationContext(headers, UUID.randomUUID());
    underTest.validateFhir(bundleString, MessageType.JSON, contextForRoutingHeaders);

    verify(client).validateJsonBundle(headerCaptor.capture(), eq(bundleString));
    assertThat(headerCaptor.getValue())
        .isNotNull()
        .containsKey(packageVersionHeaderName)
        .extractingByKey(packageVersionHeaderName)
        .isEqualTo(List.of(fhirPackageVersion));
    assertThat(headerCaptor.getValue())
        .isNotNull()
        .containsKey(packageHeaderName)
        .extractingByKey(packageHeaderName)
        .isEqualTo(List.of(fhirPackage));
  }

  static Stream<Arguments> routingHeaders() {
    return Stream.of(
        Arguments.of(HEADER_FHIR_PACKAGE_VERSION, HEADER_FHIR_PACKAGE),
        Arguments.of(HEADER_FHIR_API_VERSION_LEGACY, HEADER_FHIR_PROFILE_LEGACY));
  }

  @Test
  void shouldForwardHeaderCorrectly() {
    String bundleString = testUtil.getDefaultBundleAsString(APPLICATION_JSON);
    String version = "v1";
    String profile = "ars-profile-snapshots";
    final HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(HEADER_FHIR_PACKAGE_VERSION, version);
    httpHeaders.add(HEADER_FHIR_PACKAGE, profile);
    final NotificationContext servletContext = NotificationContext.fromHttpRequest(httpHeaders);
    when(client.validateJsonBundle(any(), eq(bundleString)))
        .thenReturn(testUtil.createOutcomeResponse(INFORMATION));

    underTest.validateFhir(bundleString, MessageType.JSON, servletContext);

    verify(client).validateJsonBundle(headerCaptor.capture(), eq(bundleString));
    assertThat(headerCaptor.getValue())
        .isNotNull()
        .hasSize(2)
        .containsKey(HEADER_FHIR_PACKAGE)
        .extractingByKey(HEADER_FHIR_PACKAGE)
        .isEqualTo(List.of(profile));
    assertThat(headerCaptor.getValue())
        .extractingByKey(HEADER_FHIR_PACKAGE_VERSION)
        .isEqualTo(List.of(version));
  }
}
