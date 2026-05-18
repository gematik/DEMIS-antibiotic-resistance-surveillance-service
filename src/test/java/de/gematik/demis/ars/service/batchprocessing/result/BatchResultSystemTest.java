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

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import de.gematik.demis.ars.service.batchprocessing.entity.BatchEntity;
import de.gematik.demis.ars.service.batchprocessing.entity.BatchFailureEntity;
import de.gematik.demis.ars.service.batchprocessing.entity.BatchResultBase;
import de.gematik.demis.ars.service.batchprocessing.entity.BatchSuccessEntity;
import de.gematik.demis.ars.service.batchprocessing.entity.ErrorReasonEnum;
import de.gematik.demis.ars.service.batchprocessing.repository.BatchRepository;
import de.gematik.demis.ars.service.batchprocessing.repository.BatchResultDAO;
import de.gematik.demis.ars.service.batchprocessing.test.PostgresTestContainer;
import de.gematik.demis.ars.service.exception.ErrorCode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "ars.batch-processing.result.server-url=" + BatchResultSystemTest.SERVER_URL,
      "ars.batch-processing.result.retention-period=" + BatchResultSystemTest.RETENTION_DAYS
    })
@ActiveProfiles({"test-without-rabbitmq", "batch-test"})
@AutoConfigureTestRestTemplate
@Slf4j
class BatchResultSystemTest extends PostgresTestContainer {

  static final String SERVER_URL = "https://demis.de/ars/v1";
  static final int RETENTION_DAYS = 5;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private BatchRepository batchRepository;
  @Autowired private BatchResultDAO batchResultDAO;
  @Autowired private DataSource dataSource;

  private void saveBatch(final UUID batchId, final int notificationCount) {
    final BatchEntity batch = new BatchEntity();
    batch.setBatchId(batchId);
    batch.setNumberOfNotifications(notificationCount);
    batchRepository.save(batch);
  }

  private BatchSuccessEntity saveBatchSuccess(final UUID batchId) {
    final BatchSuccessEntity entity = new BatchSuccessEntity();
    entity.setBatchId(batchId);
    entity.setDocumentId(UUID.randomUUID().toString());
    entity.setNotificationBundleId(UUID.randomUUID().toString());
    entity.setWarningCount(10);
    batchResultDAO.save(entity);
    return entity;
  }

  private BatchFailureEntity saveBatchFailureEntity(
      final UUID batchId, final ErrorReasonEnum errorReason) {
    final BatchFailureEntity entity = new BatchFailureEntity();
    entity.setBatchId(batchId);
    entity.setDocumentId(UUID.randomUUID().toString());
    entity.setErrorReason(errorReason);
    entity.setErrorCount(5);
    entity.setWarningCount(10);
    entity.setDetail("TEST");
    batchResultDAO.save(entity);
    return entity;
  }

  @Nested
  class StatisticsEndpoint {

    private static final String ENDPOINT_STATISTICS = "/batch/fhir/bundle/{batchId}/$statistics";

    private static void assertParameters(
        final Parameters parameters,
        final UUID batchId,
        final int successCount,
        final Map<String, String> errorCounts) {
      final int errorCount = errorCounts.values().stream().mapToInt(Integer::parseInt).sum();
      final int total = successCount + errorCount;
      final String url = SERVER_URL + "/batch/fhir/bundle/" + batchId + "/$results?query=";
      final Instant now = Instant.now();
      final Instant expiresAt = now.plus(Duration.ofDays(RETENTION_DAYS));

      assertThat(ParametersUtil.toMap(parameters.getParameter()))
          .containsEntry("batchId", batchId.toString())
          .containsEntry("total", String.valueOf(total))
          .hasEntrySatisfying(
              "batchClosedAt",
              timestamp ->
                  assertThat(Instant.parse((String) timestamp))
                      .isCloseTo(now, within(3, ChronoUnit.SECONDS)))
          .hasEntrySatisfying(
              "resultsAvailableUntil",
              timestamp ->
                  assertThat(Instant.parse((String) timestamp))
                      .isCloseTo(expiresAt, within(3, ChronoUnit.SECONDS)))
          .hasEntrySatisfying(
              "success",
              map ->
                  assertThat(map)
                      .isInstanceOf(Map.class)
                      .asInstanceOf(InstanceOfAssertFactories.MAP)
                      .containsEntry("count", String.valueOf(successCount))
                      .containsEntry("url", url + "success"))
          .hasEntrySatisfying(
              "error",
              map ->
                  assertThat(map)
                      .isInstanceOf(Map.class)
                      .asInstanceOf(InstanceOfAssertFactories.MAP)
                      .containsEntry("count", String.valueOf(errorCount))
                      .containsEntry("url", url + "error")
                      .hasEntrySatisfying(
                          "countsByErrorCode",
                          countsByErrorCode ->
                              assertThat(countsByErrorCode)
                                  .isInstanceOf(Map.class)
                                  .asInstanceOf(InstanceOfAssertFactories.MAP)
                                  .containsExactlyInAnyOrderEntriesOf(errorCounts)));
    }

    @Test
    void batchFinishedShouldReturn200WithParametersResult() {
      final UUID batchId = UUID.randomUUID();
      saveBatchSuccess(batchId);
      saveBatchFailureEntity(batchId, ErrorReasonEnum.VALIDATION);
      saveBatchFailureEntity(batchId, ErrorReasonEnum.INTERNAL_ERROR);
      saveBatch(batchId, 3);
      final Map<String, String> errorCounts =
          Map.of(
              "WAF", "0",
              "VALIDATION", "1",
              "INVALID", "0",
              "INTERNAL_ERROR", "1");

      final ResponseEntity<String> response = callBatchStatisticsEndpoint(batchId);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      final String body = response.getBody();
      assertThat(body).isNotBlank();
      final Parameters parameters = ParametersUtil.stringToResource(body);
      assertParameters(parameters, batchId, 1, errorCounts);
    }

    @Test
    void batchInProgressShouldReturn202WithHeader() {
      final UUID batchId = UUID.randomUUID();
      saveBatch(batchId, 10);
      saveBatchSuccess(batchId);
      saveBatchFailureEntity(batchId, ErrorReasonEnum.VALIDATION);
      saveBatchFailureEntity(batchId, ErrorReasonEnum.INTERNAL_ERROR);

      final ResponseEntity<String> response = callBatchStatisticsEndpoint(batchId);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
      assertThat(response.getHeaders().asMultiValueMap())
          .containsEntry("X-Progress", List.of("30%"))
          .containsEntry("X-Total", List.of("10"));
    }

    @Test
    void unknownBatchShouldReturn400() {
      final UUID batchId = UUID.randomUUID();
      final ResponseEntity<String> response = callBatchStatisticsEndpoint(batchId);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).contains(ErrorCode.BATCH_NOT_FOUND.getCode());
    }

    @Test
    void openBatchShouldReturn400() {
      final UUID batchId = UUID.randomUUID();
      saveBatchSuccess(batchId);
      final ResponseEntity<String> response = callBatchStatisticsEndpoint(batchId);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).contains(ErrorCode.BATCH_NOT_CLOSED.getCode());
    }

    private ResponseEntity<String> callBatchStatisticsEndpoint(final UUID batchId) {
      return restTemplate.getForEntity(ENDPOINT_STATISTICS, String.class, batchId);
    }
  }

  @Nested
  class ResultsCsvEndpoint {
    private static final String ENDPOINT_CSV = "/batch/fhir/bundle/{batchId}/$results?query={type}";
    private static final String CSV_DELIMITER = ";";
    private static final String DOC_ID_HEADER = "Document id (Client)";

    private static BufferedReader toReader(final InputStream is) {
      return new BufferedReader(new InputStreamReader(is));
    }

    @ParameterizedTest
    @CsvSource({"success, 3", "error, 5"})
    void streamResultAsCsv(final String type, final int expectedCsvColumns) throws Exception {
      final UUID batchId = UUID.randomUUID();
      final int numberOfDataRows = 5;

      final Supplier<BatchResultBase>[] saveEntitySuppliers = getSaveEntitySuppliers(type, batchId);

      final List<String> expectedDocumentIds =
          range(0, numberOfDataRows)
              .mapToObj(i -> saveEntitySuppliers[0].get())
              .map(BatchResultBase::getDocumentId)
              .toList();
      // and one row of the other type to check that only the requested type is included in the
      // result
      saveEntitySuppliers[1].get();
      saveBatch(batchId, numberOfDataRows + 1);

      final ResponseEntity<Resource> response = callBatchResultsEndpoint(batchId, type);
      log.info("Response: {}", response);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();

      try (final BufferedReader reader = toReader(response.getBody().getInputStream())) {
        final String headerLine = reader.readLine();
        log.info("CSV Header: {}", headerLine);
        assertThat(headerLine).isNotBlank();
        final String[] headerCols = headerLine.split(CSV_DELIMITER);
        assertThat(headerCols).hasSize(expectedCsvColumns);
        final int docIdIndex = asList(headerCols).indexOf(DOC_ID_HEADER);
        assertThat(docIdIndex).as("no docId header").isGreaterThanOrEqualTo(0);

        final List<String[]> csvRows =
            reader
                .lines()
                .peek(line -> log.info("csv row: {}", line))
                .map(line -> line.split(CSV_DELIMITER))
                .toList();
        assertThat(csvRows).allSatisfy(arr -> assertThat(arr).hasSize(expectedCsvColumns));
        assertThat(csvRows.stream().map(arr -> arr[docIdIndex]).toList())
            .containsExactlyElementsOf(expectedDocumentIds);
      }
    }

    private Supplier<BatchResultBase>[] getSaveEntitySuppliers(
        final String type, final UUID batchId) {
      final Supplier<BatchResultBase> successSupplier = () -> saveBatchSuccess(batchId);
      final Supplier<BatchResultBase> errorSupplier =
          () -> saveBatchFailureEntity(batchId, ErrorReasonEnum.VALIDATION);
      return switch (type) {
        case "success" -> new Supplier[] {successSupplier, errorSupplier};
        case "error" -> new Supplier[] {errorSupplier, successSupplier};
        default -> throw new IllegalArgumentException("unknown type: " + type);
      };
    }

    @Test
    @Timeout(value = 15, unit = SECONDS)
    void performanceTest() throws Exception {
      final UUID batchId = UUID.randomUUID();
      final int rows = 100_000;
      insertManyBatchSuccessRowsInDb(rows, batchId);
      saveBatch(batchId, rows);

      final int parallelClients = 5;
      try (final ExecutorService executor = Executors.newFixedThreadPool(parallelClients)) {
        final var tasks =
            range(0, parallelClients)
                .mapToObj(i -> (Callable<Void>) () -> executeRestCallAndAssertResult(batchId, rows))
                .toList();
        final List<Future<Void>> futures = executor.invokeAll(tasks);
        for (final Future<Void> future : futures) {
          future.get(5, SECONDS);
        }
        executor.shutdown();
      }
    }

    private Void executeRestCallAndAssertResult(final UUID batchId, final int expectedRows)
        throws IOException {
      final ResponseEntity<Resource> response = callBatchResultsEndpoint(batchId, "success");
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();

      try (final BufferedReader reader = toReader(response.getBody().getInputStream())) {
        // skip header
        reader.readLine();
        String line;
        int rowCounter = 0;
        while ((line = reader.readLine()) != null) {
          assertThat(line)
              .as("row: " + rowCounter)
              .contains(lupDocId(rowCounter))
              .contains(lupBundleId(rowCounter));
          rowCounter++;
        }
        assertThat(rowCounter).isEqualTo(expectedRows);
        log.info("Successfully read {} rows from CSV response", rowCounter);
      }

      return null;
    }

    private void insertManyBatchSuccessRowsInDb(final int rows, final UUID batchId)
        throws Exception {
      final PipedOutputStream out = new PipedOutputStream();
      final PipedInputStream in = new PipedInputStream(out);
      final Thread csvDataWriter =
          new Thread(() -> writeInsertData(rows, batchId, out), "csv-data-writer-thread");
      csvDataWriter.start();

      try (final var conn = dataSource.getConnection()) {
        final PGConnection pgConn = conn.unwrap(PGConnection.class);
        final CopyManager copyManager = pgConn.getCopyAPI();
        final String sql =
            "COPY batch_success (batch_id, document_id, notification_bundle_id, warning_count) FROM STDIN WITH (FORMAT csv)";
        final long inserted = copyManager.copyIn(sql, in);
        assertThat(inserted).isEqualTo(rows);
      }

      csvDataWriter.join();
    }

    private void writeInsertData(final int rows, final UUID batchId, final PipedOutputStream out) {
      try {
        for (int i = 0; i < rows; i++) {
          final String docId = lupDocId(i);
          final String bundleId = lupBundleId(i);
          final int warningCount = i % 10;
          final String line =
              String.format("%s,%s,%s,%d%n", batchId, docId, bundleId, warningCount);
          out.write(line.getBytes());
        }
        out.close();
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    private String lupDocId(final int index) {
      return "doc-" + String.format("%07d", index);
    }

    private String lupBundleId(final int index) {
      return "bundle-" + String.format("%07d", index);
    }

    private ResponseEntity<Resource> callBatchResultsEndpoint(
        final UUID batchId, final String type) {
      final HttpHeaders headers = new HttpHeaders();
      headers.set(HttpHeaders.ACCEPT, "text/csv");
      return restTemplate.exchange(
          ENDPOINT_CSV, HttpMethod.GET, new HttpEntity<>(headers), Resource.class, batchId, type);
    }
  }
}
