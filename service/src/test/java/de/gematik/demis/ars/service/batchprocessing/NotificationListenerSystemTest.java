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

import static de.gematik.demis.ars.service.batchprocessing.messages.constants.BatchMessageType.NOTIFICATION;
import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageHeaderConstants.HEADER_AUTHORIZATION;
import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageHeaderConstants.HEADER_BATCH_ID;
import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageHeaderConstants.HEADER_DOCUMENT_ID;
import static de.gematik.demis.ars.service.batchprocessing.messages.constants.MessageHeaderConstants.HEADER_TYPE;
import static de.gematik.demis.ars.service.utils.TestUtils.PROVENANCE_RESOURCE;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.ERROR;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.WARNING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.gematik.demis.ars.service.batchprocessing.entity.BatchFailureEntity;
import de.gematik.demis.ars.service.batchprocessing.entity.BatchSuccessEntity;
import de.gematik.demis.ars.service.batchprocessing.entity.ErrorReasonEnum;
import de.gematik.demis.ars.service.batchprocessing.resilience.SaveResultFailedException;
import de.gematik.demis.ars.service.batchprocessing.test.RabbitAndPostgresTestContainer;
import de.gematik.demis.ars.service.batchprocessing.test.RabbitConfig;
import de.gematik.demis.ars.service.service.contextenrichment.ContextEnrichmentServiceClient;
import de.gematik.demis.ars.service.service.fss.FhirStorageServiceClient;
import de.gematik.demis.ars.service.service.pseudonymisation.PseudonymResponse;
import de.gematik.demis.ars.service.service.pseudonymisation.SurveillancePseudonymServiceClient;
import de.gematik.demis.ars.service.service.validation.ValidationServiceClient;
import de.gematik.demis.ars.service.utils.TestUtils;
import de.gematik.demis.service.base.security.crypto.AESEncryptionService;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.MultiValueMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("batch-test")
@EnableJpaRepositories(
    basePackageClasses = NotificationListenerSystemTest.class,
    considerNestedRepositories = true)
@Slf4j
class NotificationListenerSystemTest extends RabbitAndPostgresTestContainer {

  private static final Duration MAX_WAIT_TIMEOUT = Duration.of(2, SECONDS);

  private final TestUtils testUtils = new TestUtils();

  @Value("${ars.batch-processing.secure-queue}")
  private String messageQueue;

  @Autowired private RabbitTemplate rabbitTemplate;
  @Autowired private BatchSuccessRepository batchSuccessRepository;
  @Autowired private BatchFailureRepository batchFailureRepository;
  @Autowired private AESEncryptionService encryptionService;

  @MockitoSpyBean private NotificationListener notificationListener;

  // mock external service calls
  @MockitoBean private ContextEnrichmentServiceClient contextEnrichmentServiceClient;
  @MockitoBean private ValidationServiceClient validationClient;
  @MockitoBean private SurveillancePseudonymServiceClient pseudonymClient;
  @MockitoBean private FhirStorageServiceClient fssClient;

  private static Map<String, Object> createNotificationMessageHeaders(
      final UUID batchId,
      final String documentId,
      final byte[] encryptedAuthorizationToken,
      final String fhirPackageVersion,
      final String fhirPackage) {
    final Map<String, Object> headers = new HashMap<>();
    headers.put(HEADER_AUTHORIZATION, encryptedAuthorizationToken);
    headers.put(HEADER_TYPE, NOTIFICATION.name());
    headers.put(HEADER_BATCH_ID, batchId.toString());
    headers.put(HEADER_DOCUMENT_ID, documentId);
    headers.put("x-fhir-package-version", fhirPackageVersion);
    headers.put("x-fhir-package", fhirPackage);
    return headers;
  }

  @AfterEach
  void cleanUp() {
    getDlqMessageNonBlocking();
  }

  @Test
  void arsNotificationSuccessfullyProcessed() {
    final UUID batchId = UUID.randomUUID();
    final String documentId = "DOCUMENT-ID-IN-HEADER-MATCH-NOT-BUNDLE-ID";
    final String arsNotification = testUtils.VALID_ARS_NOTIFICATION_JSON_STRING;
    final String notificationBundleIdInNotification = "5ED0E3B6-5BF9-4B80-B05E-F44D6F51CE83";
    final byte[] encryptedNotification = encrypt(arsNotification);
    final String authorizationToken = "eyABC";
    final byte[] encryptedAuthorizationToken = encrypt(authorizationToken);
    final String fhirApiVersion = "v5";
    final String fhirProfile = "ars-profile";

    final BatchSuccessEntity expected = new BatchSuccessEntity();
    expected.setBatchId(batchId);
    expected.setDocumentId(notificationBundleIdInNotification);
    expected.setWarningCount(1);

    mockValidationServiceCall(WARNING);
    mockPseudoServiceCallOkay();
    mockContextEnrichmentServiceCallOkay();
    mockFssCallOkay();

    rabbitTemplate.send(
        messageQueue,
        MessageBuilder.withBody(encryptedNotification)
            .copyHeaders(
                createNotificationMessageHeaders(
                    batchId, documentId, encryptedAuthorizationToken, fhirApiVersion, fhirProfile))
            .build());

    await().atMost(MAX_WAIT_TIMEOUT).untilAsserted(() -> assertSuccessDbResult(expected));

    final String rkiBundleId =
        batchSuccessRepository.findByBatchId(batchId).getFirst().getNotificationBundleId();

    assertValidationCall(fhirApiVersion, fhirProfile, arsNotification);
    assertCESCall(authorizationToken);
    assertFSSCall(batchId, rkiBundleId);
  }

  @Test
  void arsNotificationValidationError() {
    final UUID batchId = UUID.randomUUID();
    final String documentId = "5ED0E3B6-5BF9-4B80-B05E-F44D6F51CE83";
    final String arsNotification = testUtils.VALID_ARS_NOTIFICATION_JSON_STRING;
    final byte[] encryptedNotification = encrypt(arsNotification);
    final byte[] encryptedAuthorizationToken = encrypt("{}");
    final String fhirApiVersion = "v5";
    final String fhirProfile = "ars-profile";

    final BatchFailureEntity expected = new BatchFailureEntity();
    expected.setBatchId(batchId);
    expected.setDocumentId(documentId);
    expected.setErrorReason(ErrorReasonEnum.VALIDATION);
    expected.setErrorCount(1);
    expected.setWarningCount(0);

    mockValidationServiceCall(ERROR);

    rabbitTemplate.send(
        messageQueue,
        MessageBuilder.withBody(encryptedNotification)
            .copyHeaders(
                createNotificationMessageHeaders(
                    batchId, documentId, encryptedAuthorizationToken, fhirApiVersion, fhirProfile))
            .build());

    await().atMost(MAX_WAIT_TIMEOUT).untilAsserted(() -> assertFailureDbResult(expected));
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
  void wafErrorMessage(final String errorType, final ErrorReasonEnum expectedErrorReason) {
    final UUID batchId = UUID.randomUUID();
    final String documentId = UUID.randomUUID().toString();
    final String errorMessage =
        (errorType != null) ? "{\"error\":\"%s\"}".formatted(errorType) : "{}";
    log.info("error message = {}", errorMessage);

    final BatchFailureEntity expected = new BatchFailureEntity();
    expected.setBatchId(batchId);
    expected.setDocumentId(documentId);
    expected.setErrorReason(expectedErrorReason);

    rabbitTemplate.send(
        messageQueue,
        MessageBuilder.withBody(errorMessage.getBytes())
            .setHeader(HEADER_BATCH_ID, batchId)
            .setHeader(HEADER_DOCUMENT_ID, documentId)
            .setHeader(HEADER_TYPE, "ERROR")
            .build());

    await().atMost(MAX_WAIT_TIMEOUT).untilAsserted(() -> assertFailureDbResult(expected));

    verifyNoInteractions(validationClient, pseudonymClient, fssClient);
  }

  @Test
  void saveResultFailedException_should_requeueMessage() {
    Mockito.doThrow(new SaveResultFailedException("just for test", null))
        .doNothing()
        .when(notificationListener)
        .processMessage(any(), any());

    rabbitTemplate.send(
        messageQueue, MessageBuilder.withBody("does not matter".getBytes()).build());

    await()
        .atMost(MAX_WAIT_TIMEOUT)
        .untilAsserted(() -> verify(notificationListener, times(2)).processMessage(any(), any()));

    assertThat(getDlqMessageNonBlocking()).isNull();
  }

  @Test
  void messageReject() {
    final String batchId = UUID.randomUUID().toString();

    Mockito.doThrow(new RuntimeException("just for test"))
        .doNothing()
        .when(notificationListener)
        .processMessage(any(), any());

    rabbitTemplate.send(
        messageQueue,
        MessageBuilder.withBody("does not matter".getBytes())
            .setHeader(HEADER_BATCH_ID, batchId)
            .build());

    await().atMost(MAX_WAIT_TIMEOUT).untilAsserted(() -> assertDlqContainsMessage(batchId));

    verify(notificationListener, times(1)).processMessage(any(), any());
    assertThat(rabbitTemplate.receive(RabbitConfig.TEST_DLQ_SECURE)).isNull();
  }

  @Nullable
  private Message getDlqMessageNonBlocking() {
    return rabbitTemplate.receive(RabbitConfig.TEST_DLQ_SECURE);
  }

  private void assertDlqContainsMessage(final String batchId) {
    final Message dlqMessage = getDlqMessageNonBlocking();
    assertThat(dlqMessage).isNotNull();
    assertThat((String) dlqMessage.getMessageProperties().getHeader(HEADER_BATCH_ID))
        .isEqualTo(batchId);
  }

  private void mockValidationServiceCall(final OperationOutcome.IssueSeverity returnedSeverity) {
    when(validationClient.validateJsonBundle(any(), anyString()))
        .thenReturn(testUtils.createOutcomeResponse(returnedSeverity));
  }

  private void mockFssCallOkay() {
    when(fssClient.sendNotification(anyString())).thenReturn(ResponseEntity.ok().build());
  }

  private void mockPseudoServiceCallOkay() {
    final var response =
        new PseudonymResponse(
            "http://my.test/Pseudo", "urn:uuid:10101010-1010-1010-1010-101010101010");
    when(pseudonymClient.createPseudonym(any())).thenReturn(response);
  }

  private void mockContextEnrichmentServiceCallOkay() {
    final String response = testUtils.readFileToString(PROVENANCE_RESOURCE);
    when(contextEnrichmentServiceClient.enrichBundleWithContextInformation(any(), anyString()))
        .thenReturn(response);
  }

  private byte[] encrypt(final String s) {
    return encryptionService.encryptData(s);
  }

  private void assertSuccessDbResult(final BatchSuccessEntity expected) {
    final UUID batchId = expected.getBatchId();
    final List<BatchSuccessEntity> successes = batchSuccessRepository.findByBatchId(batchId);
    final List<BatchFailureEntity> failures = batchFailureRepository.findByBatchId(batchId);

    assertThat(successes)
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields(
            "id", "createdAt", "notificationBundleId")
        .containsExactly(expected);
    assertThat(failures).isEmpty();
  }

  private void assertFailureDbResult(final BatchFailureEntity expected) {
    final UUID batchId = expected.getBatchId();
    final List<BatchSuccessEntity> successes = batchSuccessRepository.findByBatchId(batchId);
    final List<BatchFailureEntity> failures = batchFailureRepository.findByBatchId(batchId);

    assertThat(successes).isEmpty();
    assertThat(failures)
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "createdAt")
        .containsExactly(expected);
  }

  private void assertValidationCall(
      final String fhirPackageVersion, final String fhirPackage, final String arsNotification) {
    final ArgumentCaptor<MultiValueMap<String, String>> headerCaptor = ArgumentCaptor.captor();
    final ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
    verify(validationClient).validateJsonBundle(headerCaptor.capture(), payloadCaptor.capture());
    assertThat(headerCaptor.getValue())
        .containsEntry("x-fhir-package-version", List.of(fhirPackageVersion))
        .containsEntry("x-fhir-package", List.of(fhirPackage));
    assertThat(payloadCaptor.getValue()).isEqualTo(arsNotification);
  }

  private void assertCESCall(final String authorizationToken) {
    final ArgumentCaptor<String> tokenArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(contextEnrichmentServiceClient)
        .enrichBundleWithContextInformation(tokenArgumentCaptor.capture(), anyString());
    assertThat(tokenArgumentCaptor.getValue()).isEqualTo(authorizationToken);
  }

  private void assertFSSCall(final UUID batchId, final String rkiBundleId) {
    final ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
    verify(fssClient).sendNotification(stringCaptor.capture());
    final Bundle txBundle = testUtils.jsonToResource(stringCaptor.getValue(), Bundle.class);
    final Bundle notificationBundle = (Bundle) txBundle.getEntryFirstRep().getResource();

    final List<Coding> tags = notificationBundle.getMeta().getTag();
    assertThat(tags)
        .anySatisfy(
            tag -> {
              assertThat(tag.getSystem()).isEqualTo("https://demis.rki.de/fhir/CodeSystem/BatchId");
              assertThat(tag.getCode()).isEqualTo(batchId.toString());
            });

    assertThat(notificationBundle.getIdentifier().getValue()).isEqualTo(rkiBundleId);
  }

  @Repository
  interface BatchSuccessRepository extends JpaRepository<BatchSuccessEntity, Long> {
    List<BatchSuccessEntity> findByBatchId(UUID batchId);
  }

  @Repository
  interface BatchFailureRepository extends JpaRepository<BatchFailureEntity, Long> {
    List<BatchFailureEntity> findByBatchId(UUID batchId);
  }
}
