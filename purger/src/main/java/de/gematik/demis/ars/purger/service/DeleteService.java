package de.gematik.demis.ars.purger.service;

/*-
 * #%L
 * bulk-inbound-purger
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

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import java.time.Instant;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
class DeleteService {

  private static final String DELETE_BATCH_SQL =
"""
DELETE FROM batch WHERE batch.batch_id in (
    SELECT b.batch_id FROM batch b WHERE b.created_at < :deleteBeforeDate
    LIMIT :limit
)
""";

  private static final String DELETE_RESULT_SQL_TEMPLATE =
"""
DELETE from ${batchresult_tablename} WHERE id IN (
    SELECT result.id from ${batchresult_tablename} result
    WHERE result.batch_id in (
        SELECT b.batch_id FROM batch b WHERE b.created_at < :deleteBeforeDate
    )
    LIMIT :limit
)
""";

  private static final String DELETE_ORPHANED_RESULT_SQL_TEMPLATE =
"""
WITH ORPHANED_BATCH AS (
    SELECT DISTINCT result.batch_id
    FROM ${batchresult_tablename} result
    WHERE result.batch_id NOT IN (SELECT b.batch_id from batch b)
),
OLD_UNCLOSED_BATCHES AS (
    SELECT combined_result.batch_id
    FROM (
        SELECT s.batch_id, max(s.created_at) as max_created_at FROM batch_success s
        WHERE s.batch_id in (SELECT o.batch_id from ORPHANED_BATCH o)
        GROUP BY s.batch_id
        UNION ALL
        SELECT f.batch_id, max(f.created_at) as max_created_at FROM batch_failure f
        WHERE f.batch_id in (SELECT o.batch_id from ORPHANED_BATCH o)
        GROUP BY f.batch_id
    ) AS combined_result
    GROUP BY combined_result.batch_id
    HAVING MAX(combined_result.max_created_at) < :deleteBeforeDate
)
DELETE FROM ${batchresult_tablename}
WHERE id IN (
    SELECT result.id
    FROM ${batchresult_tablename} result
    WHERE result.batch_id IN (SELECT batchesToDelete.batch_id FROM OLD_UNCLOSED_BATCHES batchesToDelete)
    LIMIT :limit
)
""";
  private final EntityManager em;

  @Transactional
  public int deleteBatches(final Instant deleteBeforeDate, final int limit) {
    final Query query =
        em.createNativeQuery(DELETE_BATCH_SQL)
            .setParameter("deleteBeforeDate", deleteBeforeDate)
            .setParameter("limit", limit);
    return query.executeUpdate();
  }

  @Transactional
  public int deleteBatchSuccess(final Instant deleteBeforeDate, final int limit) {
    return executeBatchResultDelete(
        DELETE_RESULT_SQL_TEMPLATE, BatchResultTable.SUCCESS, deleteBeforeDate, limit);
  }

  @Transactional
  public int deleteBatchFailures(final Instant deleteBeforeDate, final int limit) {
    return executeBatchResultDelete(
        DELETE_RESULT_SQL_TEMPLATE, BatchResultTable.FAILURE, deleteBeforeDate, limit);
  }

  @Transactional
  public int deleteOrphanedBatchSuccess(final Instant deleteBeforeDate, final int limit) {
    return executeBatchResultDelete(
        DELETE_ORPHANED_RESULT_SQL_TEMPLATE, BatchResultTable.SUCCESS, deleteBeforeDate, limit);
  }

  @Transactional
  public int deleteOrphanedBatchFailures(final Instant deleteBeforeDate, final int limit) {
    return executeBatchResultDelete(
        DELETE_ORPHANED_RESULT_SQL_TEMPLATE, BatchResultTable.FAILURE, deleteBeforeDate, limit);
  }

  private int executeBatchResultDelete(
      final String sqlTemplate,
      final BatchResultTable table,
      final Instant deleteBeforeDate,
      final int limit) {
    final Query query =
        em.createNativeQuery(getSqlForResultTable(sqlTemplate, table))
            .setParameter("deleteBeforeDate", deleteBeforeDate)
            .setParameter("limit", limit);
    return query.executeUpdate();
  }

  private String getSqlForResultTable(final String sqlTemplate, final BatchResultTable table) {
    return sqlTemplate.replace("${batchresult_tablename}", table.getTableName());
  }

  @RequiredArgsConstructor
  @Getter
  private enum BatchResultTable {
    SUCCESS("batch_success"),
    FAILURE("batch_failure");
    private final String tableName;
  }
}
