package de.gematik.demis.ars.purger.test;

/*-
 * #%L
 * ars-purger
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

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;

@RequiredArgsConstructor
@Slf4j
public class DbImporter {

  private final DataSource dataSource;

  public void insertBatchSuccess(final int rows, final UUID batchId, final Instant createdAt)
      throws Exception {
    insertManyRowsInDb(
        rows,
        "batch_success",
        "batch_id, created_at, document_id, notification_bundle_id, warning_count",
        row -> writeBatchSuccessCsvLine(batchId, createdAt));
  }

  public void insertBatchFailure(final int rows, final UUID batchId, final Instant createdAt)
      throws Exception {
    insertManyRowsInDb(
        rows,
        "batch_failure",
        "batch_id, created_at, document_id, error_reason",
        row -> writeBatchFailureCsvLine(batchId, createdAt));
  }

  private String writeBatchSuccessCsvLine(final UUID batchId, final Instant createdAt) {
    final String docId = UUID.randomUUID().toString();
    final String bundleId = UUID.randomUUID().toString();
    return String.format("%s,%s,%s,%s,%d%n", batchId, createdAt, docId, bundleId, 0);
  }

  private String writeBatchFailureCsvLine(final UUID batchId, final Instant createdAt) {
    final String docId = UUID.randomUUID().toString();
    return String.format("%s,%s,%s,%s%n", batchId, createdAt, docId, "I");
  }

  private void insertManyRowsInDb(
      final int rows,
      final String tableName,
      final String columns,
      final Function<Integer, String> csvLineSupplier)
      throws Exception {
    final PipedOutputStream out = new PipedOutputStream();
    final PipedInputStream in = new PipedInputStream(out);
    final Thread csvDataWriter =
        new Thread(() -> writeInsertData(rows, out, csvLineSupplier), "csv-data-writer-thread");
    csvDataWriter.start();

    try (final var conn = dataSource.getConnection()) {
      try (final Statement statement = conn.createStatement()) {
        statement.execute("SET ROLE " + TestWithPostgresContainer.DB_ADMIN_USER);
      }
      final PGConnection pgConn = conn.unwrap(PGConnection.class);
      final CopyManager copyManager = pgConn.getCopyAPI();
      final String sql = "COPY %s (%s) FROM STDIN WITH (FORMAT csv)".formatted(tableName, columns);
      final long inserted = copyManager.copyIn(sql, in);
      assertThat(inserted).isEqualTo(rows);
    }

    csvDataWriter.join();
  }

  private void writeInsertData(
      final int rows,
      final PipedOutputStream out,
      final Function<Integer, String> csvLineSupplier) {
    try {
      for (int i = 0; i < rows; i++) {
        out.write(csvLineSupplier.apply(i).getBytes());
      }
      out.close();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
