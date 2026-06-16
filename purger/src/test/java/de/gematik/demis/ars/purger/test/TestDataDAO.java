package de.gematik.demis.ars.purger.test;

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

import static de.gematik.demis.ars.purger.test.TestWithPostgresContainer.DB_ADMIN_USER;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class TestDataDAO {

  private static final AtomicLong ID_COUNTER = new AtomicLong(0);

  private final EntityManager em;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public UUID insertBatch(final Instant createdAt) {
    switchToAdminRole();

    final UUID batchId = UUID.randomUUID();
    final String sql = "insert into batch (batch_id, created_at) values (:batchId, :createdAt)";
    final int count =
        em.createNativeQuery(sql)
            .setParameter("batchId", batchId)
            .setParameter("createdAt", createdAt)
            .executeUpdate();
    assertThat(count).isEqualTo(1);
    return batchId;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Long insertBatchSuccess(final UUID batchId, final Instant createdAt) {
    switchToAdminRole();

    final Long id = ID_COUNTER.incrementAndGet();
    final String sql =
        "insert into batch_success (id, batch_id, created_at, document_id, notification_bundle_id, warning_count) values (:id, :batchId, :createdAt, :docId, :notificationId, :warnings)";
    final int count =
        em.createNativeQuery(sql)
            .setParameter("id", id)
            .setParameter("batchId", batchId)
            .setParameter("createdAt", createdAt)
            .setParameter("docId", UUID.randomUUID().toString())
            .setParameter("notificationId", UUID.randomUUID().toString())
            .setParameter("warnings", 0)
            .executeUpdate();
    assertThat(count).isEqualTo(1);
    return id;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Long insertBatchFailure(final UUID batchId, final Instant createdAt) {
    switchToAdminRole();

    final Long id = ID_COUNTER.incrementAndGet();
    final String sql =
        "insert into batch_failure (id, batch_id, created_at, document_id, error_reason) values (:id, :batchId, :createdAt, :docId, :errorReason)";
    final int count =
        em.createNativeQuery(sql)
            .setParameter("id", id)
            .setParameter("batchId", batchId)
            .setParameter("createdAt", createdAt)
            .setParameter("docId", UUID.randomUUID().toString())
            .setParameter("errorReason", 'I')
            .executeUpdate();
    assertThat(count).isEqualTo(1);
    return id;
  }

  /**
   * the purger-db-user is not permitted to insert, thus switch to admin role (is permitted in
   * db-init.sql)
   */
  private void switchToAdminRole() {
    em.createNativeQuery("SET ROLE " + DB_ADMIN_USER).executeUpdate();
  }

  @Transactional(readOnly = true)
  public boolean batchExists(final UUID batchId) {
    final Number count =
        (Number)
            em.createNativeQuery("SELECT COUNT(*) FROM batch WHERE batch_id = :batchId")
                .setParameter("batchId", batchId)
                .getSingleResult();
    return count.longValue() > 0;
  }

  @Transactional(readOnly = true)
  public boolean batchSuccessExists(final Long id) {
    final Number count =
        (Number)
            em.createNativeQuery("SELECT COUNT(*) FROM batch_success WHERE id = :id")
                .setParameter("id", id)
                .getSingleResult();
    return count.longValue() > 0;
  }

  @Transactional(readOnly = true)
  public boolean batchFailureExists(final Long id) {
    final Number count =
        (Number)
            em.createNativeQuery("SELECT COUNT(*) FROM batch_failure WHERE id = :id")
                .setParameter("id", id)
                .getSingleResult();
    return count.longValue() > 0;
  }

  @Transactional(readOnly = true)
  public long countBatchSuccessForBatch(final UUID batchId) {
    final Number count =
        (Number)
            em.createNativeQuery("SELECT COUNT(*) FROM batch_success WHERE batch_id = :batchId")
                .setParameter("batchId", batchId)
                .getSingleResult();
    return count.longValue();
  }

  @Transactional(readOnly = true)
  public long countBatchFailureForBatch(final UUID batchId) {
    final Number count =
        (Number)
            em.createNativeQuery("SELECT COUNT(*) FROM batch_failure WHERE batch_id = :batchId")
                .setParameter("batchId", batchId)
                .getSingleResult();
    return count.longValue();
  }

  @Transactional(readOnly = true)
  public long countBatchSuccess() {
    final Number count =
        (Number) em.createNativeQuery("SELECT COUNT(*) FROM batch_success").getSingleResult();
    return count.longValue();
  }

  @Transactional(readOnly = true)
  public long countBatchFailure() {
    final Number count =
        (Number) em.createNativeQuery("SELECT COUNT(*) FROM batch_failure").getSingleResult();
    return count.longValue();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void truncateAllTables() {
    switchToAdminRole();
    em.createNativeQuery("TRUNCATE TABLE batch_success, batch_failure, batch").executeUpdate();
  }
}
