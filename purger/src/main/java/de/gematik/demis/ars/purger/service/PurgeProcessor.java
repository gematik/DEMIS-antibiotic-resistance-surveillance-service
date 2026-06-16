package de.gematik.demis.ars.purger.service;

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

import de.gematik.demis.ars.purger.config.PurgerConfigProps;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.IntUnaryOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PurgeProcessor {

  private final DeleteService deleteService;
  private final PurgerConfigProps configProps;

  public void purgeDatabase() {
    final Instant deleteBeforeDate = calculateDeleteBeforeDate();
    log.info("delete all batches older than {}", deleteBeforeDate);

    deleteInChunks(
        "batch-success", limit -> deleteService.deleteBatchSuccess(deleteBeforeDate, limit));
    deleteInChunks(
        "batch-failures", limit -> deleteService.deleteBatchFailures(deleteBeforeDate, limit));
    deleteInChunks("batch", limit -> deleteService.deleteBatches(deleteBeforeDate, limit));

    // now we delete all results where the batch parent row is missing (there was no close message)
    final Instant orphanDeleteBeforeDate = calculateOrphanDeleteDate(deleteBeforeDate);
    deleteInChunks(
        "batch-success orphaned entries",
        limit -> deleteService.deleteOrphanedBatchSuccess(orphanDeleteBeforeDate, limit));
    deleteInChunks(
        "batch-failures orphaned entries",
        limit -> deleteService.deleteOrphanedBatchFailures(orphanDeleteBeforeDate, limit));
  }

  private void deleteInChunks(final String type, final IntUnaryOperator sqlExecutor) {
    log.debug("Executing delete in chunks for type {}", type);
    final int limit = configProps.chunkSize();
    int total = 0;
    int batchCount = 0;
    int rowsDeleted;
    do {
      rowsDeleted = sqlExecutor.applyAsInt(limit);
      log.debug("{} {} rows deleted", rowsDeleted, type);
      batchCount++;
      total += rowsDeleted;
    } while (rowsDeleted == limit);
    log.info("Total {} rows deleted from {} in {} chunks", total, type, batchCount);
  }

  private Instant calculateDeleteBeforeDate() {
    return Instant.now().minus(configProps.retentionDays(), ChronoUnit.DAYS);
  }

  private Instant calculateOrphanDeleteDate(final Instant deleteBeforeDate) {
    return deleteBeforeDate.minus(
        configProps.additionalRetentionDaysForOrphanResults(), ChronoUnit.DAYS);
  }
}
