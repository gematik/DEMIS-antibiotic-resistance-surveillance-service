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

import de.gematik.demis.ars.service.api.BatchResultApi;
import de.gematik.demis.ars.service.api.BatchResultApi.ResultQueryEnum;
import de.gematik.demis.ars.service.batchprocessing.config.BatchResultProperties;
import de.gematik.demis.ars.service.batchprocessing.entity.BatchEntity;
import de.gematik.demis.ars.service.batchprocessing.entity.BatchFailureEntity;
import de.gematik.demis.ars.service.batchprocessing.entity.BatchResultBase;
import de.gematik.demis.ars.service.batchprocessing.entity.BatchSuccessEntity;
import de.gematik.demis.ars.service.batchprocessing.repository.BatchRepository;
import de.gematik.demis.ars.service.batchprocessing.repository.BatchResultDAO;
import de.gematik.demis.ars.service.exception.ArsServiceException;
import de.gematik.demis.ars.service.exception.ErrorCode;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.hl7.fhir.r4.model.Parameters;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty("ars.batch-processing.enabled")
@Slf4j
public class BatchResultService {
  private static final String MSG_BATCH_NOT_CLOSED =
      "Batch %s is still open. Close the batch first.";
  private static final String MSG_BATCH_NOT_FOUND = "Batch %s does not exist.";
  private static final String MSG_BATCH_EXPIRED =
      "Batch %s has expired on %s and results no longer available.";
  private static final int CSV_FLUSH_SIZE = 1000;
  private static final int CSV_BUFFER_SIZE = 64 * 1024;
  private static final char CSV_DELIMITER = ';';

  private static final CsvDefinition<BatchSuccessEntity> CSV_DEFINITION_SUCCESS =
      new CsvDefinition<>(
          BatchSuccessEntity.class,
          List.of(
              new CsvColumn<>("Document id (Client)", BatchSuccessEntity::getDocumentId),
              new CsvColumn<>("Document id (RKI)", BatchSuccessEntity::getNotificationBundleId),
              new CsvColumn<>("Validation warnings", BatchSuccessEntity::getWarningCount)));

  private static final CsvDefinition<BatchFailureEntity> CSV_DEFINITION_ERROR =
      new CsvDefinition<>(
          BatchFailureEntity.class,
          List.of(
              new CsvColumn<>("Document id (Client)", BatchFailureEntity::getDocumentId),
              new CsvColumn<>("Error reason", BatchFailureEntity::getErrorReason),
              new CsvColumn<>("Validation errors", BatchFailureEntity::getErrorCount),
              new CsvColumn<>("Validation warnings", BatchFailureEntity::getWarningCount),
              new CsvColumn<>("Details", BatchFailureEntity::getDetail)));

  private final BatchRepository batchRepository;
  private final BatchResultDAO batchResultDAO;
  private final BatchResultProperties properties;

  /**
   * Returns statistics of the given batch.
   *
   * <p>If the batch is still in progress, only the percentage of already processed notifications is
   * returned.
   *
   * @param batchId the UUID of the batch
   * @return StatisticsResult, never {@code null}
   * @throws ArsServiceException with {@code ErrorCode.UNKNOWN_BATCH} -> if no batch exists for the
   *     given ID and no notifications have been processed for it
   * @throws ArsServiceException with {@code ErrorCode.OPEN_BATCH} -> if notifications have been
   *     processed but the batch is not yet closed (i.e. upload is still in progress)
   */
  public StatisticsResult getStatistics(final UUID batchId) {
    final BatchEntity batch = batchRepository.findById(batchId).orElse(null);

    final int successCount = batchResultDAO.countSuccess(batchId);
    final int failuresCount = batchResultDAO.countFailures(batchId);

    if (batch == null) {
      if (successCount > 0 || failuresCount > 0) {
        final String msg = MSG_BATCH_NOT_CLOSED.formatted(batchId);
        throw new ArsServiceException(ErrorCode.BATCH_NOT_CLOSED, msg);
      } else {
        final String msg = MSG_BATCH_NOT_FOUND.formatted(batchId);
        throw new ArsServiceException(ErrorCode.BATCH_NOT_FOUND, msg);
      }
    }

    final Instant expireDate = determineExpireDateAndThrowExceptionIfExpired(batch);

    final int total = batch.getNumberOfNotifications();
    if (successCount + failuresCount < total) {
      return StatisticsResult.inProgress(
          batch.getNumberOfNotifications(), successCount, failuresCount);
    }

    if (successCount + failuresCount > total) {
      log.warn(
          "Batch {}: more notifications processed as uploaded. NumberOfNotifications={}, SuccessCount={}, ErrorCount={}",
          batchId,
          total,
          successCount,
          failuresCount);
    }

    final Parameters parameters =
        new StatisticsParametersBuilder()
            .batchId(batchId)
            .completedAt(batch.getCreatedAt())
            .expiresAt(expireDate)
            .total(batch.getNumberOfNotifications())
            .successCount(successCount)
            .failureCount(failuresCount)
            .errorCountsPerReason(batchResultDAO.countFailuresGroupByErrorReason(batchId))
            .detailUrl(determineDetailUrl(batchId))
            .build();

    return StatisticsResult.finished(total, parameters);
  }

  private Instant determineExpireDateAndThrowExceptionIfExpired(@NonNull final BatchEntity batch) {
    final Instant expireDate = determineExpireDate(batch.getCreatedAt());
    if (Instant.now().isAfter(expireDate)) {
      final String msg = MSG_BATCH_EXPIRED.formatted(batch.getBatchId(), expireDate);
      throw new ArsServiceException(ErrorCode.BATCH_NOT_FOUND, msg);
    }
    return expireDate;
  }

  private Instant determineExpireDate(final Instant createdAt) {
    return createdAt.plus(properties.retentionPeriod());
  }

  private URI determineDetailUrl(final UUID batchId) {
    return UriComponentsBuilder.fromUri(properties.serverUrl())
        .path(BatchResultApi.BATCH_RESULT_PATH)
        .pathSegment(BatchResultApi.OPERATION_DETAIL_RESULT)
        .build(batchId.toString());
  }

  /**
   * Checks if a batch with the given ID exists.
   *
   * @param batchId UUID of the batch
   * @throws ArsServiceException with {@code ErrorCode.BATCH_NOT_FOUND} if no batch exists
   */
  public void checkBatchExists(final UUID batchId) {
    final Optional<BatchEntity> optionalBatchEntity = batchRepository.findById(batchId);
    if (optionalBatchEntity.isEmpty()) {
      final String msg = MSG_BATCH_NOT_FOUND.formatted(batchId);
      throw new ArsServiceException(ErrorCode.BATCH_NOT_FOUND, msg);
    }

    determineExpireDateAndThrowExceptionIfExpired(optionalBatchEntity.get());
  }

  /**
   * Streams the batch results as CSV to the given OutputStream.
   *
   * @param batchId UUID of the batch
   * @param type Type of results (SUCCESS or ERROR)
   * @param outputStream Target OutputStream for CSV export
   */
  public void streamResult(
      final UUID batchId, final ResultQueryEnum type, final OutputStream outputStream) {
    final CsvDefinition<?> csvDefinition =
        switch (type) {
          case SUCCESS -> CSV_DEFINITION_SUCCESS;
          case ERROR -> CSV_DEFINITION_ERROR;
        };

    streamResult(batchId, csvDefinition, outputStream);
  }

  @SneakyThrows
  private <T extends BatchResultBase> void streamResult(
      final UUID batchId, CsvDefinition<T> csvDefinition, final OutputStream outputStream) {
    final AtomicInteger rowsCounter = new AtomicInteger(0);
    try (final BufferedWriter writer =
            new BufferedWriter(
                new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), CSV_BUFFER_SIZE);
        final CSVPrinter csv = new CSVPrinter(writer, csvFormat(csvDefinition))) {

      batchResultDAO.forEachBatchResult(
          batchId,
          csvDefinition.entityClass(),
          entity -> {
            try {
              csv.printRecord(csvDefinition.extractValues(entity));
              if (rowsCounter.incrementAndGet() % CSV_FLUSH_SIZE == 0) {
                csv.flush();
              }
            } catch (final IOException ex) {
              throw new UncheckedIOException(ex);
            }
          });
    }
    log.info("{} rows written to csv", rowsCounter.get());
  }

  private CSVFormat csvFormat(final CsvDefinition<?> def) {
    return CSVFormat.DEFAULT
        .builder()
        .setDelimiter(CSV_DELIMITER)
        .setHeader(def.getHeaders())
        .build();
  }

  private record CsvColumn<T>(String header, Function<T, Object> extractor) {}

  private record CsvDefinition<T extends BatchResultBase>(
      Class<T> entityClass, List<CsvColumn<T>> columns) {

    public String[] getHeaders() {
      return columns().stream().map(CsvColumn::header).toArray(String[]::new);
    }

    public Object[] extractValues(final T entity) {
      return columns().stream().map(col -> col.extractor().apply(entity)).toArray();
    }
  }
}
