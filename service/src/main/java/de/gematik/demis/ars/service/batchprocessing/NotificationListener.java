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

import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageHeaderConstants.HEADER_AUTHORIZATION;
import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageHeaderConstants.HEADER_BATCH_ID;
import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageHeaderConstants.HEADER_DOCUMENT_ID;
import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageHeaderConstants.HEADER_TYPE;

import de.gematik.demis.ars.service.batchprocessing.entity.BatchFailureEntity;
import de.gematik.demis.ars.service.batchprocessing.entity.BatchResultBase;
import de.gematik.demis.ars.service.batchprocessing.entity.BatchSuccessEntity;
import de.gematik.demis.ars.service.batchprocessing.entity.ErrorReasonEnum;
import de.gematik.demis.ars.service.batchprocessing.messages.ErrorMessage;
import de.gematik.demis.ars.service.batchprocessing.messages.constants.BatchMessageType;
import de.gematik.demis.ars.service.batchprocessing.repository.BatchResultDAO;
import de.gematik.demis.ars.service.batchprocessing.resilience.RetryableNotificationProcessor;
import de.gematik.demis.ars.service.batchprocessing.resilience.SaveResultFailedException;
import de.gematik.demis.ars.service.exception.ArsServiceException;
import de.gematik.demis.ars.service.exception.ArsValidationException;
import de.gematik.demis.ars.service.service.NotificationContext;
import de.gematik.demis.ars.service.service.NotificationProcessingResult;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.service.base.error.ServiceCallException;
import de.gematik.demis.service.base.security.crypto.AESEncryptionService;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty("ars.batch-processing.enabled")
public class NotificationListener {

  private final AESEncryptionService encryptionService;
  private final RetryableNotificationProcessor notificationService;
  private final BatchResultDAO batchResultDAO;
  private final JsonMapper jsonMapper;

  /**
   * Processes an incoming encrypted notification message from the RabbitMQ queue and stores the
   * result persistent.
   *
   * <p>This method is invoked automatically by Spring AMQP when a message arrives in the {@code
   * bulk.secure} queue.
   *
   * @param message the encrypted message body from the queue
   * @param amqpHeaders the AMQP message headers containing
   */
  @RabbitListener(queues = "${ars.batch-processing.secure-queue}")
  public void processMessage(final byte[] message, @Headers final Map<String, Object> amqpHeaders) {
    final BatchMessageType type = extractMessageTypeFromHeader(amqpHeaders);
    final UUID batchId = extractBatchIdFromHeader(amqpHeaders);
    final String documentId = extractDocumentIdFromHeader(amqpHeaders);

    final BatchResultBase batchProcessingResult =
        switch (type) {
          case ERROR -> processErrorMessage(batchId, documentId, message);
          case NOTIFICATION -> processNotification(batchId, documentId, amqpHeaders, message);
          default -> throw new IllegalArgumentException("not supported");
        };

    try {
      batchResultDAO.save(batchProcessingResult);
    } catch (final Exception ex) {
      log.error(
          "{} / {} - error saving batch result. Reason: {}", batchId, documentId, ex.getMessage());
      throw new SaveResultFailedException(
          batchId + " / " + documentId + " - error saving batch result", ex);
    }
  }

  private BatchFailureEntity processErrorMessage(
      final UUID batchId, final String documentId, final byte[] message) {
    final BatchFailureEntity batchResult = createBatchFailure(batchId, documentId);
    batchResult.setErrorReason(
        isWafError(message) ? ErrorReasonEnum.WAF : ErrorReasonEnum.INTERNAL_ERROR);
    return batchResult;
  }

  private boolean isWafError(final byte[] message) {
    try {
      final ErrorMessage errorMessage = jsonMapper.readValue(message, ErrorMessage.class);
      return ErrorMessage.ErrorType.WAF.name().equalsIgnoreCase(errorMessage.error());
    } catch (final Exception ex) {
      log.error("error reading error message. ", ex);
      return false;
    }
  }

  private BatchResultBase processNotification(
      final UUID batchId,
      final String documentIdFromHeader,
      final Map<String, Object> amqpHeaders,
      final byte[] encryptedMessage) {
    try {
      final byte[] encryptedAuthorization =
          (byte[]) getRequiredHeader(amqpHeaders, HEADER_AUTHORIZATION);
      final String decryptedMessage = encryptionService.decryptData(encryptedMessage);
      final String decryptedAuthorization = encryptionService.decryptData(encryptedAuthorization);
      final NotificationContext context =
          NotificationContext.fromMessageQueue(amqpHeaders, batchId);

      final NotificationProcessingResult processingResult =
          notificationService.process(
              decryptedMessage, MessageType.JSON, decryptedAuthorization, context);

      logProcessingResult(batchId, documentIdFromHeader, processingResult);

      return createBatchSuccess(batchId, processingResult);
    } catch (final Exception e) {
      return handleProcessingException(batchId, documentIdFromHeader, e);
    }
  }

  private void logProcessingResult(
      final UUID batchId,
      final String documentIdFromHeader,
      final NotificationProcessingResult processingResult) {
    log.info(
        "{} / {} - message successfully processed. Rki DocId is {}",
        batchId,
        documentIdFromHeader,
        processingResult.notificationId());

    if (!documentIdFromHeader.equals(processingResult.originalDocumentId())) {
      log.warn(
          "{} / {} - document Id in bundle does not match id in header and is {}",
          batchId,
          documentIdFromHeader,
          processingResult.originalDocumentId());
    }
  }

  private BatchSuccessEntity createBatchSuccess(
      final UUID batchId, final NotificationProcessingResult processingResult) {
    final BatchSuccessEntity batchResult = new BatchSuccessEntity();
    batchResult.setBatchId(batchId);
    batchResult.setDocumentId(processingResult.originalDocumentId());
    batchResult.setNotificationBundleId(processingResult.notificationId());
    batchResult.setWarningCount(
        countIssues(processingResult.validationOutcome(), IssueSeverity.WARNING));
    return batchResult;
  }

  private BatchFailureEntity createBatchFailure(final UUID batchId, final String documentId) {
    final BatchFailureEntity batchResult = new BatchFailureEntity();
    batchResult.setBatchId(batchId);
    batchResult.setDocumentId(documentId);
    return batchResult;
  }

  private BatchFailureEntity handleProcessingException(
      final UUID batchId, final String documentId, final Exception e) {
    final BatchFailureEntity batchResult = createBatchFailure(batchId, documentId);
    final ErrorReasonEnum errorReason =
        switch (e) {
          case ArsValidationException arsValidationException -> {
            final OperationOutcome operationOutcome = arsValidationException.getOperationOutcome();
            batchResult.setErrorCount(
                countIssues(operationOutcome, IssueSeverity.ERROR)
                    + countIssues(operationOutcome, IssueSeverity.FATAL));
            batchResult.setWarningCount(countIssues(operationOutcome, IssueSeverity.WARNING));
            yield ErrorReasonEnum.VALIDATION;
          }
          case ArsServiceException arsException -> {
            batchResult.setDetail(arsException.getErrorCode());
            yield arsException.getResponseStatus().is4xxClientError()
                ? ErrorReasonEnum.INVALID
                : ErrorReasonEnum.INTERNAL_ERROR;
          }
          case ServiceCallException serviceCallException -> {
            batchResult.setDetail(serviceCallException.getErrorCode());
            yield ErrorReasonEnum.INTERNAL_ERROR;
          }
          case null, default -> ErrorReasonEnum.INTERNAL_ERROR;
        };
    batchResult.setErrorReason(errorReason);
    logProcessingException(batchResult, e);
    return batchResult;
  }

  private void logProcessingException(final BatchFailureEntity batchResult, final Exception e) {
    final boolean serverError = batchResult.getErrorReason() == ErrorReasonEnum.INTERNAL_ERROR;
    if (serverError) {
      log.error(
          "{} / {} - error processing notification",
          batchResult.getBatchId(),
          batchResult.getDocumentId(),
          e);
    } else {
      log.info(
          "{} / {} - invalid notification - {}",
          batchResult.getBatchId(),
          batchResult.getDocumentId(),
          e.getMessage());
    }
  }

  private BatchMessageType extractMessageTypeFromHeader(final Map<String, Object> amqpHeaders) {
    return BatchMessageType.valueOf(getRequiredHeader(amqpHeaders, HEADER_TYPE).toString());
  }

  private String extractDocumentIdFromHeader(final Map<String, Object> amqpHeaders) {
    return getRequiredHeader(amqpHeaders, HEADER_DOCUMENT_ID).toString();
  }

  private UUID extractBatchIdFromHeader(final Map<String, Object> amqpHeaders) {
    return UUID.fromString(getRequiredHeader(amqpHeaders, HEADER_BATCH_ID).toString());
  }

  private Object getRequiredHeader(final Map<String, Object> amqpHeaders, final String key) {
    final Object value = amqpHeaders.get(key);
    if (value == null) {
      throw new IllegalArgumentException("Missing required AMQP header: " + key);
    }
    return value;
  }

  private int countIssues(final OperationOutcome operationOutcome, final IssueSeverity severity) {
    if (operationOutcome == null || !operationOutcome.hasIssue()) {
      return 0;
    }
    return (int)
        operationOutcome.getIssue().stream()
            .filter(issue -> issue.getSeverity() == severity)
            .count();
  }
}
