package de.gematik.demis.ars.service.batchprocessing.repository;

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

import de.gematik.demis.ars.service.batchprocessing.entity.BatchFailureEntity;
import de.gematik.demis.ars.service.batchprocessing.entity.BatchResultBase;
import de.gematik.demis.ars.service.batchprocessing.entity.BatchSuccessEntity;
import de.gematik.demis.ars.service.batchprocessing.entity.ErrorReasonEnum;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty("ars.batch-processing.enabled")
@RequiredArgsConstructor
@Slf4j
public class BatchResultDAO {
  private final EntityManager entityManager;
  private final SessionFactory sessionFactory;

  @Transactional
  public void save(final BatchResultBase entity) {
    entityManager.persist(entity);
  }

  @Transactional(readOnly = true)
  public int countSuccess(final UUID batchId) {
    // select count(e) from BatchSuccessEntity e where e.batchId = ?
    return count(BatchSuccessEntity.class.getSimpleName(), batchId);
  }

  @Transactional(readOnly = true)
  public int countFailures(final UUID batchId) {
    // select count(e) from BatchFailureEntity e where e.batchId = ?
    return count(BatchFailureEntity.class.getSimpleName(), batchId);
  }

  private int count(final String entityName, final UUID batchId) {
    final String hqlQuery = "select count(e) from " + entityName + " e where e.batchId = :batch";
    return entityManager
        .createQuery(hqlQuery, Long.class)
        .setParameter("batch", batchId)
        .getSingleResult()
        .intValue();
  }

  @Transactional(readOnly = true)
  public Map<ErrorReasonEnum, Long> countFailuresGroupByErrorReason(final UUID batchId) {
    // select reason, count(*) from BatchFailureEntity e where e.batchId = ?
    final String hqlQuery =
"""
        SELECT e.errorReason, count(e)
        FROM BatchFailureEntity e
        WHERE e.batchId = :batch
        GROUP BY e.errorReason
""";
    return entityManager
        .createQuery(hqlQuery, Object[].class)
        .setParameter("batch", batchId)
        .getResultList()
        .stream()
        .collect(
            Collectors.toMap(
                columns -> (ErrorReasonEnum) columns[0], columns -> (Long) columns[1]));
  }

  /**
   * Streams all batch result entities for the given batch to the provided consumer.
   *
   * <p>Uses a stateless Hibernate session and a forward-only cursor for efficient memory usage. The
   * results are ordered by entity ID.
   *
   * @param batchId UUID of the batch
   * @param entityClass Entity class (e.g., BatchSuccessEntity or BatchFailureEntity)
   * @param consumer Consumer that processes each entity instance
   */
  public <T extends BatchResultBase> void forEachBatchResult(
      final UUID batchId, final Class<T> entityClass, final Consumer<T> consumer) {
    try (final StatelessSession stateless = sessionFactory.openStatelessSession()) {
      final Transaction transaction = stateless.beginTransaction();

      final String hql =
          "select e from "
              + entityClass.getSimpleName()
              + " e where e.batchId = :batchId order by e.id";

      try (final ScrollableResults<T> cursor =
          stateless
              .createQuery(hql, entityClass)
              .setParameter("batchId", batchId)
              .setFetchSize(1_000)
              .scroll(ScrollMode.FORWARD_ONLY)) {
        while (cursor.next()) {
          consumer.accept(cursor.get());
        }

        transaction.commit();
      } finally {
        rollbackIfNotCommited(transaction);
      }
    }
  }

  private void rollbackIfNotCommited(final Transaction transaction) {
    if (transaction.isActive()) {
      try {
        transaction.rollback();
      } catch (final Exception ex) {
        log.warn("ignore error rollback transaction", ex);
      }
    }
  }
}
