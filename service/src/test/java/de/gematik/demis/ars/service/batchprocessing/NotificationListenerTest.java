package de.gematik.demis.ars.service.batchprocessing;

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

import static de.gematik.demis.ars.service.batchprocessing.messages.constants.BatchMessageType.ERROR;
import static de.gematik.demis.ars.service.batchprocessing.messages.constants.BatchMessageType.NOTIFICATION;
import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageHeaderConstants.HEADER_AUTHORIZATION;
import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageHeaderConstants.HEADER_BATCH_ID;
import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageHeaderConstants.HEADER_DOCUMENT_ID;
import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageHeaderConstants.HEADER_TYPE;
import static de.gematik.demis.ars.service.utils.TestUtils.HEADER_FHIR_PACKAGE;
import static de.gematik.demis.ars.service.utils.TestUtils.HEADER_FHIR_PACKAGE_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.demis.ars.service.batchprocessing.entity.BatchFailureEntity;
import de.gematik.demis.ars.service.batchprocessing.entity.BatchResultBase;
import de.gematik.demis.ars.service.batchprocessing.entity.BatchSuccessEntity;
import de.gematik.demis.ars.service.batchprocessing.entity.ErrorReasonEnum;
import de.gematik.demis.ars.service.batchprocessing.repository.BatchResultDAO;
import de.gematik.demis.ars.service.batchprocessing.resilience.RetryableNotificationProcessor;
import de.gematik.demis.ars.service.batchprocessing.resilience.SaveResultFailedException;
import de.gematik.demis.ars.service.exception.ArsServiceException;
import de.gematik.demis.ars.service.exception.ArsValidationException;
import de.gematik.demis.ars.service.exception.ErrorCode;
import de.gematik.demis.ars.service.service.NotificationContext;
import de.gematik.demis.ars.service.service.NotificationProcessingResult;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.service.base.error.ServiceCallException;
import de.gematik.demis.service.base.security.crypto.AESEncryptionService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@Slf4j
class NotificationListenerTest {

  private static final byte[] ENCRYPTED_MESSAGE = "some-encrypted-payload".getBytes();
  private static final String DECRYPTED_MESSAGE = "some-decrypted-payload";
  private static final byte[] ENCRYPTED_AUTHORIZATION = "some-encrypted-authorization".getBytes();
  private static final String DECRYPTED_AUTHORIZATION = "some-decrypted-authorization";

  private static final String ERROR_MESSAGE_TEMPLATE =
"""
  {"error":"%s", "other_property":"is ignored"}
""";

  @Mock private RetryableNotificationProcessor notificationService;
  @Mock private AESEncryptionService encryptionService;
  @Mock private BatchResultDAO batchResultDAO;

  private NotificationListener underTest;

  @Captor private ArgumentCaptor<NotificationContext> notificationContextCaptor;
  @Captor private ArgumentCaptor<BatchResultBase> batchResultCaptor;

  private static Map<String, Object> createNotificationMessageHeaders(
      final UUID batchId, final String documentId) {
    final Map<String, Object> headers = new HashMap<>();
    headers.put(HEADER_AUTHORIZATION, ENCRYPTED_AUTHORIZATION);
    headers.put(HEADER_TYPE, NOTIFICATION.name());
    headers.put(HEADER_BATCH_ID, batchId.toString());
    headers.put(HEADER_DOCUMENT_ID, documentId);
    headers.put(HEADER_FHIR_PACKAGE_VERSION, "FHIR_PACKAGE_VERSION");
    headers.put(HEADER_FHIR_PACKAGE, "FHIR_PACKAGE");
    return headers;
  }

  private static OperationOutcome createValidationOutcome(int errors, int warnings, int infos) {
    OperationOutcome outcome = new OperationOutcome();
    addIssues(outcome, IssueSeverity.ERROR, errors);
    addIssues(outcome, IssueSeverity.WARNING, warnings);
    addIssues(outcome, IssueSeverity.INFORMATION, infos);
    return outcome;
  }

  private static void addIssues(
      final OperationOutcome outcome, final IssueSeverity severity, final int count) {
    for (int i = 0; i < count; i++) {
      outcome.addIssue().setSeverity(severity);
    }
  }

  static Stream<Arguments> notificationProcessingFailedDueException() {
    return Stream.of(
        Arguments.of(
            new ServiceCallException("test", "PSEUDO", 500, null),
            ErrorReasonEnum.INTERNAL_ERROR,
            "PSEUDO"),
        Arguments.of(
            new ArsServiceException(ErrorCode.INVALID_PSEUDONYMS, "test"),
            ErrorReasonEnum.INVALID,
            "INVALID_PSEUDONYMS"),
        Arguments.of(
            new ArsServiceException(ErrorCode.INTERNAL_SERVER_ERROR, "test"),
            ErrorReasonEnum.INTERNAL_ERROR,
            "INTERNAL_SERVER_ERROR"),
        Arguments.of(new RuntimeException("test"), ErrorReasonEnum.INTERNAL_ERROR, null));
  }

  @BeforeEach
  void setup() {
    underTest =
        new NotificationListener(
            encryptionService, notificationService, batchResultDAO, new JsonMapper());
  }

  @Test
  void notificationSuccessfullyProcessed() {
    final UUID batchId = UUID.randomUUID();
    final String documentId = UUID.randomUUID().toString();
    final String demisNotificationId = "111-aaa";
    final Map<String, Object> headers = createNotificationMessageHeaders(batchId, documentId);
    final int warnings = 3;
    final OperationOutcome validationOutcome = createValidationOutcome(0, warnings, 1);
    final var notificationProcessingResult =
        new NotificationProcessingResult(
            demisNotificationId, new Bundle(), validationOutcome, List.of(), documentId);

    mockDecryptionService();
    when(notificationService.process(any(), any(), any(), any()))
        .thenReturn(notificationProcessingResult);

    underTest.processMessage(ENCRYPTED_MESSAGE, headers);

    verify(notificationService, times(1))
        .process(
            eq(DECRYPTED_MESSAGE),
            eq(MessageType.JSON),
            eq(DECRYPTED_AUTHORIZATION),
            notificationContextCaptor.capture());
    final NotificationContext capturedContext = notificationContextCaptor.getValue();

    assertThat(capturedContext.headers())
        // Note that the authorization header is not decrypted, but converted to string
        .containsEntry("x-authorization", headers.get("x-authorization").toString())
        .usingRecursiveComparison()
        .ignoringFields("x-authorization")
        .isEqualTo(headers);

    final BatchSuccessEntity expectedBatchResult = new BatchSuccessEntity();
    expectedBatchResult.setBatchId(batchId);
    expectedBatchResult.setDocumentId(documentId);
    expectedBatchResult.setNotificationBundleId(demisNotificationId);
    expectedBatchResult.setWarningCount(warnings);
    assertSavedBatchResult(expectedBatchResult);
  }

  @Test
  void notificationProcessingFailedDueToValidationError() {
    final UUID batchId = UUID.randomUUID();
    final String documentId = "DOCUMENT_ID";
    final Map<String, Object> headers = createNotificationMessageHeaders(batchId, documentId);
    final int errors = 5;
    final int warnings = 1;
    final OperationOutcome validationOutcome = createValidationOutcome(errors, warnings, 1);
    final ArsValidationException arsValidationException =
        new ArsValidationException(ErrorCode.FHIR_VALIDATION_ERROR, "for test", validationOutcome);

    mockDecryptionService();
    when(notificationService.process(any(), any(), any(), any())).thenThrow(arsValidationException);

    underTest.processMessage(ENCRYPTED_MESSAGE, headers);

    final BatchFailureEntity expectedBatchResult = new BatchFailureEntity();
    expectedBatchResult.setBatchId(batchId);
    expectedBatchResult.setDocumentId(documentId);
    expectedBatchResult.setErrorReason(ErrorReasonEnum.VALIDATION);
    expectedBatchResult.setErrorCount(errors);
    expectedBatchResult.setWarningCount(warnings);
    assertSavedBatchResult(expectedBatchResult);
  }

  @ParameterizedTest
  @MethodSource
  void notificationProcessingFailedDueException(
      final Exception ex, final ErrorReasonEnum errorReason, final String detail) {
    final UUID batchId = UUID.randomUUID();
    final String documentId = "DOCUMENT_ID";
    final Map<String, Object> headers = createNotificationMessageHeaders(batchId, documentId);

    mockDecryptionService();
    when(notificationService.process(any(), any(), any(), any())).thenThrow(ex);

    underTest.processMessage(ENCRYPTED_MESSAGE, headers);

    final BatchFailureEntity expectedBatchResult = new BatchFailureEntity();
    expectedBatchResult.setBatchId(batchId);
    expectedBatchResult.setDocumentId(documentId);
    expectedBatchResult.setErrorReason(errorReason);
    expectedBatchResult.setErrorCount(null);
    expectedBatchResult.setWarningCount(null);
    expectedBatchResult.setDetail(detail);
    assertSavedBatchResult(expectedBatchResult);
  }

  @Test
  void missingRequiredHeaderThrowsException() {
    final Map<String, Object> headers = createNotificationMessageHeaders(UUID.randomUUID(), "id");
    headers.remove(HEADER_BATCH_ID);
    assertThatThrownBy(() -> underTest.processMessage(ENCRYPTED_MESSAGE, headers))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required AMQP header: " + HEADER_BATCH_ID);
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "WAF, WAF",
        "PROCESSING_ERROR, INTERNAL_ERROR",
        "UNKNOWN, INTERNAL_ERROR",
        "NULL, INTERNAL_ERROR"
      },
      nullValues = "NULL")
  void errorMessage(final String errorType, final ErrorReasonEnum expectedErrorReason) {
    final UUID batchId = UUID.randomUUID();
    final String documentId = "DOCUMENT_ID";
    final Map<String, Object> headers = new HashMap<>();
    headers.put(HEADER_BATCH_ID, batchId.toString());
    headers.put(HEADER_DOCUMENT_ID, documentId);
    headers.put(HEADER_TYPE, ERROR);

    final String errorMessage =
        (errorType != null) ? ERROR_MESSAGE_TEMPLATE.formatted(errorType) : "{}";
    log.info("error message = {}", errorMessage);

    // Note: error message is not encrypted
    underTest.processMessage(errorMessage.getBytes(), headers);

    Mockito.verifyNoInteractions(encryptionService);
    Mockito.verifyNoInteractions(notificationService);

    final BatchFailureEntity expectedBatchResult = new BatchFailureEntity();
    expectedBatchResult.setBatchId(batchId);
    expectedBatchResult.setDocumentId(documentId);
    expectedBatchResult.setErrorReason(expectedErrorReason);
    assertSavedBatchResult(expectedBatchResult);
  }

  @Test
  void dbExceptionThrowsSpecialException() {
    final UUID batchId = UUID.randomUUID();
    final String documentId = UUID.randomUUID().toString();
    final Map<String, Object> headers = createNotificationMessageHeaders(batchId, documentId);
    final RuntimeException dbException = new RuntimeException();

    mockDecryptionService();
    when(notificationService.process(any(), any(), any(), any()))
        .thenReturn(mock(NotificationProcessingResult.class));
    doThrow(dbException).when(batchResultDAO).save(any());

    assertThatThrownBy(() -> underTest.processMessage(ENCRYPTED_MESSAGE, headers))
        .isInstanceOf(SaveResultFailedException.class)
        .hasMessageContaining(batchId + " / " + documentId)
        .hasCause(dbException);
  }

  private void mockDecryptionService() {
    when(encryptionService.decryptData(ENCRYPTED_MESSAGE)).thenReturn(DECRYPTED_MESSAGE);
    when(encryptionService.decryptData(ENCRYPTED_AUTHORIZATION))
        .thenReturn(DECRYPTED_AUTHORIZATION);
  }

  private void assertSavedBatchResult(final BatchResultBase expectedBatchResult) {
    verify(batchResultDAO).save(batchResultCaptor.capture());
    assertThat(batchResultCaptor.getValue())
        .usingRecursiveComparison()
        .ignoringFields("correlationId")
        .isEqualTo(expectedBatchResult);
  }
}
