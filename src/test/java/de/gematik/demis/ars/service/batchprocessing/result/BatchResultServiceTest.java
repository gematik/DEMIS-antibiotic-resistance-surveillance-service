package de.gematik.demis.ars.service.batchprocessing.result;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import de.gematik.demis.ars.service.api.BatchResultApi.ResultQueryEnum;
import de.gematik.demis.ars.service.batchprocessing.config.BatchResultProperties;
import de.gematik.demis.ars.service.batchprocessing.entity.BatchEntity;
import de.gematik.demis.ars.service.batchprocessing.entity.BatchFailureEntity;
import de.gematik.demis.ars.service.batchprocessing.entity.BatchResultBase;
import de.gematik.demis.ars.service.batchprocessing.entity.BatchSuccessEntity;
import de.gematik.demis.ars.service.batchprocessing.entity.ErrorReasonEnum;
import de.gematik.demis.ars.service.batchprocessing.repository.BatchRepository;
import de.gematik.demis.ars.service.batchprocessing.repository.BatchResultDAO;
import de.gematik.demis.ars.service.exception.ArsServiceException;
import de.gematik.demis.ars.service.exception.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class BatchResultServiceTest {

  @Mock private BatchRepository batchRepository;
  @Mock private BatchResultDAO batchResultDAO;
  @Mock private BatchResultProperties properties;

  @InjectMocks private BatchResultService underTest;

  @Nested
  class GetStatisticsTests {

    @Test
    void unknownBatchShouldThrowException() {
      final UUID batchId = UUID.randomUUID();
      when(batchRepository.findById(any())).thenReturn(Optional.empty());
      when(batchResultDAO.countSuccess(any())).thenReturn(0);
      when(batchResultDAO.countFailures(any())).thenReturn(0);

      assertThatThrownBy(() -> underTest.getStatistics(batchId))
          .isInstanceOfSatisfying(
              ArsServiceException.class,
              ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BATCH_NOT_FOUND.name());
                assertThat(ex.getResponseStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(ex.getMessage()).isEqualTo("Batch " + batchId + " does not exist.");
              });

      verify(batchRepository).findById(batchId);
      verify(batchResultDAO).countSuccess(batchId);
      verify(batchResultDAO).countFailures(batchId);
      verifyNoMoreInteractions(batchRepository, batchResultDAO);
    }

    @ParameterizedTest
    @CsvSource({"1, 0", "0, 1", "7, 8"})
    void openBatchShouldThrowException(final int successCount, final int errorCount) {
      final UUID batchId = UUID.randomUUID();
      when(batchRepository.findById(any())).thenReturn(Optional.empty());
      when(batchResultDAO.countSuccess(any())).thenReturn(successCount);
      when(batchResultDAO.countFailures(any())).thenReturn(errorCount);

      assertThatThrownBy(() -> underTest.getStatistics(batchId))
          .isInstanceOfSatisfying(
              ArsServiceException.class,
              ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BATCH_NOT_CLOSED.name());
                assertThat(ex.getResponseStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(ex.getMessage())
                    .isEqualTo("Batch " + batchId + " is still open. Close the batch first.");
              });

      verify(batchRepository).findById(batchId);
      verify(batchResultDAO).countSuccess(batchId);
      verify(batchResultDAO).countFailures(batchId);
      verifyNoMoreInteractions(batchRepository, batchResultDAO);
    }

    @Test
    void batchInProgress() {
      final UUID batchId = UUID.randomUUID();
      final StatisticsResult expected = new StatisticsResult(4, true, 75, null);
      final BatchEntity batch = new BatchEntity();
      batch.setBatchId(batchId);
      batch.setNumberOfNotifications(4);
      when(batchRepository.findById(any())).thenReturn(Optional.of(batch));
      when(batchResultDAO.countSuccess(any())).thenReturn(2);
      when(batchResultDAO.countFailures(any())).thenReturn(1);

      final StatisticsResult result = underTest.getStatistics(batchId);

      assertThat(result).isEqualTo(expected);

      verify(batchRepository).findById(batchId);
      verify(batchResultDAO).countSuccess(batchId);
      verify(batchResultDAO).countFailures(batchId);
      verifyNoMoreInteractions(batchRepository, batchResultDAO);
    }

    @Test
    void batchFinishedShouldReturnStatistics() {
      final UUID batchId = UUID.randomUUID();
      final var errorCounts =
          Map.of(
              ErrorReasonEnum.WAF,
              15L,
              ErrorReasonEnum.VALIDATION,
              32L,
              ErrorReasonEnum.INTERNAL_ERROR,
              3L);

      final BatchEntity batch = new BatchEntity();
      batch.setBatchId(batchId);
      batch.setNumberOfNotifications(150);
      batch.setCreatedAt(Instant.parse("2026-03-11T16:34:59Z"));

      final String detailUrlBase = "https://ingress.local/surveillance/antibiotic-resistance/v1";
      final String detailPath = "/batch/fhir/bundle/" + batchId + "/$results";
      final Map<String, Object> expectedParameters =
          Map.of(
              "batchId",
              batchId.toString(),
              "batchClosedAt",
              "2026-03-11T17:34:59+01:00",
              "resultsAvailableUntil",
              "2026-03-16T17:34:59+01:00",
              "total",
              String.valueOf(batch.getNumberOfNotifications()),
              "success",
              Map.of(
                  "url", detailUrlBase + detailPath + "?query=success",
                  "contentType", "text/csv",
                  "count", "100"),
              "error",
              Map.of(
                  "url",
                  detailUrlBase + detailPath + "?query=error",
                  "contentType",
                  "text/csv",
                  "count",
                  "50",
                  "countsByErrorCode",
                  Map.of(
                      "WAF", "15",
                      "VALIDATION", "32",
                      "INVALID", "0",
                      "INTERNAL_ERROR", "3")));

      when(batchRepository.findById(any())).thenReturn(Optional.of(batch));
      when(batchResultDAO.countSuccess(any())).thenReturn(100);
      when(batchResultDAO.countFailures(any())).thenReturn(50);
      when(batchResultDAO.countFailuresGroupByErrorReason(any())).thenReturn(errorCounts);
      when(properties.retentionPeriod()).thenReturn(Duration.of(5, ChronoUnit.DAYS));
      when(properties.serverUrl()).thenReturn(URI.create(detailUrlBase));

      final StatisticsResult result = underTest.getStatistics(batchId);

      assertThat(result).isNotNull();
      assertThat(result.inProgress()).isFalse();
      assertThat(result.progressPercent()).isEqualTo(100);

      final Parameters parameters = result.parameters();
      assertThat(parameters).isNotNull();
      assertThat(ParametersUtil.toMap(parameters.getParameter()))
          .usingRecursiveComparison()
          .isEqualTo(expectedParameters);

      verify(batchRepository).findById(batchId);
      verify(batchResultDAO).countSuccess(batchId);
      verify(batchResultDAO).countFailures(batchId);
      verify(batchResultDAO).countFailuresGroupByErrorReason(batchId);
      verifyNoMoreInteractions(batchRepository, batchResultDAO);
    }

    @Test
    void closedBatchWithZeroNotificationsShouldReturnStatistics() {
      final UUID batchId = UUID.randomUUID();
      final BatchEntity batch = new BatchEntity();
      batch.setBatchId(batchId);
      batch.setNumberOfNotifications(0);
      batch.setCreatedAt(Instant.parse("2026-03-11T16:34:59Z"));

      when(batchRepository.findById(any())).thenReturn(Optional.of(batch));
      when(batchResultDAO.countSuccess(any())).thenReturn(0);
      when(batchResultDAO.countFailures(any())).thenReturn(0);
      when(batchResultDAO.countFailuresGroupByErrorReason(any())).thenReturn(Map.of());
      when(properties.retentionPeriod()).thenReturn(Duration.of(5, ChronoUnit.DAYS));
      when(properties.serverUrl()).thenReturn(URI.create("https://demis.de/ars"));

      final StatisticsResult result = underTest.getStatistics(batchId);

      assertThat(result).isNotNull();
      assertThat(result.inProgress()).isFalse();
      assertThat(result.progressPercent()).isEqualTo(100);
      assertThat(result.parameters()).isNotNull();
    }
  }

  @Nested
  class GetResultCsv {

    @Test
    void streamSuccessResult() {
      final UUID batchId = UUID.randomUUID();
      final List<BatchSuccessEntity> entities =
          List.of(success("doc-success-1", "bundle-1", 2), success("doc-success-2", "bundle-2", 0));
      stubForEach(batchId, BatchSuccessEntity.class, entities);

      final ByteArrayOutputStream output = new ByteArrayOutputStream();
      underTest.streamResult(batchId, ResultQueryEnum.SUCCESS, output);

      assertThat(lines(output))
          .containsExactly(
              "Document id (Client);Document id (RKI);Validation warnings",
              "doc-success-1;bundle-1;2",
              "doc-success-2;bundle-2;0");
    }

    @Test
    void streamErrorResult() {
      final UUID batchId = UUID.randomUUID();
      final List<BatchFailureEntity> entities =
          List.of(
              failure("doc-error-1", ErrorReasonEnum.VALIDATION, 4, 1, null),
              failure("doc-error-2", ErrorReasonEnum.INVALID, null, null, "pseudonyms"));
      stubForEach(batchId, BatchFailureEntity.class, entities);

      final ByteArrayOutputStream output = new ByteArrayOutputStream();
      underTest.streamResult(batchId, ResultQueryEnum.ERROR, output);

      assertThat(lines(output))
          .containsExactly(
              "Document id (Client);Error reason;Validation errors;Validation warnings;Details",
              "doc-error-1;VALIDATION;4;1;",
              "doc-error-2;INVALID;;;pseudonyms");
    }

    @Test
    void streamNoResultsShouldWriteHeaderOnly() {
      final UUID batchId = UUID.randomUUID();
      stubForEach(batchId, BatchSuccessEntity.class, List.of());

      final ByteArrayOutputStream output = new ByteArrayOutputStream();
      underTest.streamResult(batchId, ResultQueryEnum.SUCCESS, output);

      assertThat(lines(output)).hasSize(1);
    }

    @Test
    void shouldFlushOnLargeResult() {
      final UUID batchId = UUID.randomUUID();
      final int rows = 1001;
      final List<BatchSuccessEntity> entities = new ArrayList<>(rows);
      for (int i = 1; i <= rows; i++) {
        entities.add(success("doc-success-" + i, "bundle-" + i, i % 3));
      }
      stubForEach(batchId, BatchSuccessEntity.class, entities);

      final AtomicInteger flushCounter = new AtomicInteger(0);
      final ByteArrayOutputStream output =
          new ByteArrayOutputStream() {
            @Override
            public void flush() throws IOException {
              super.flush();
              flushCounter.incrementAndGet();
            }
          };
      underTest.streamResult(batchId, ResultQueryEnum.SUCCESS, output);

      assertThat(lines(output)).hasSize(rows + 1);
      assertThat(flushCounter).hasValueGreaterThanOrEqualTo(2);
    }

    private List<String> lines(final ByteArrayOutputStream output) {
      return output.toString(StandardCharsets.UTF_8).lines().toList();
    }

    private BatchSuccessEntity success(
        final String documentId, final String bundleId, final int warningCount) {
      final BatchSuccessEntity entity = new BatchSuccessEntity();
      entity.setCreatedAt(Instant.now());
      entity.setDocumentId(documentId);
      entity.setNotificationBundleId(bundleId);
      entity.setWarningCount(warningCount);
      return entity;
    }

    private BatchFailureEntity failure(
        final String documentId,
        final ErrorReasonEnum reason,
        final Integer errorCount,
        final Integer warningCount,
        final String detail) {
      final BatchFailureEntity entity = new BatchFailureEntity();
      entity.setCreatedAt(Instant.now());
      entity.setDocumentId(documentId);
      entity.setErrorReason(reason);
      entity.setErrorCount(errorCount);
      entity.setWarningCount(warningCount);
      entity.setDetail(detail);
      return entity;
    }

    private <T extends BatchResultBase> void stubForEach(
        final UUID batchId, final Class<T> entityClass, final List<T> entities) {
      doAnswer(
              invocation -> {
                final Consumer<T> consumer = invocation.getArgument(2);
                entities.forEach(consumer);
                return null;
              })
          .when(batchResultDAO)
          .forEachBatchResult(eq(batchId), eq(entityClass), notNull());
    }
  }
}
