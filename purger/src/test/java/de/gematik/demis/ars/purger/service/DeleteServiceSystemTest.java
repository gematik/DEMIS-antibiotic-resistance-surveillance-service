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

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.demis.ars.purger.test.TestDataDAO;
import de.gematik.demis.ars.purger.test.TestWithPostgresContainer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestDataDAO.class)
@Slf4j
class DeleteServiceSystemTest extends TestWithPostgresContainer {

  private static final Instant CUTOFF = Instant.now().minus(14, ChronoUnit.DAYS);
  private static final Instant BEFORE_CUTOFF = CUTOFF.minus(1, ChronoUnit.SECONDS);
  private static final Instant AFTER_CUTOFF = CUTOFF.plus(1, ChronoUnit.SECONDS);

  private static final int LIMIT = 100;

  @Autowired TestDataDAO testDataDAO;
  @Autowired DeleteService underTest;

  @BeforeEach
  void cleanDatabase() {
    testDataDAO.truncateAllTables();
  }

  private Long insertResult(final UUID batchId, final Instant ts, final ResultType type) {
    return switch (type) {
      case SUCCESS -> testDataDAO.insertBatchSuccess(batchId, ts);
      case FAILURE -> testDataDAO.insertBatchFailure(batchId, ts);
    };
  }

  private boolean resultExists(final Long id, final ResultType type) {
    return switch (type) {
      case SUCCESS -> testDataDAO.batchSuccessExists(id);
      case FAILURE -> testDataDAO.batchFailureExists(id);
    };
  }

  private long countResultsForBatch(final UUID batchId, final ResultType type) {
    return switch (type) {
      case SUCCESS -> testDataDAO.countBatchSuccessForBatch(batchId);
      case FAILURE -> testDataDAO.countBatchFailureForBatch(batchId);
    };
  }

  private int deleteResults(final int limit, final ResultType type) {
    return switch (type) {
      case SUCCESS -> underTest.deleteBatchSuccess(CUTOFF, limit);
      case FAILURE -> underTest.deleteBatchFailures(CUTOFF, limit);
    };
  }

  private int deleteOrphanedResults(final int limit, final ResultType type) {
    return switch (type) {
      case SUCCESS -> underTest.deleteOrphanedBatchSuccess(CUTOFF, limit);
      case FAILURE -> underTest.deleteOrphanedBatchFailures(CUTOFF, limit);
    };
  }

  private enum ResultType {
    SUCCESS,
    FAILURE;

    ResultType other() {
      return this == SUCCESS ? FAILURE : SUCCESS;
    }
  }

  @Nested
  class DeleteBatches {

    @Test
    void shouldDeleteOldBatch() {
      final UUID oldBatch = testDataDAO.insertBatch(BEFORE_CUTOFF);

      final int deleted = underTest.deleteBatches(CUTOFF, LIMIT);

      assertThat(deleted).isEqualTo(1);
      assertThat(testDataDAO.batchExists(oldBatch)).isFalse();
    }

    @Test
    void shouldNotDeleteRecentBatch() {
      final UUID recentBatch = testDataDAO.insertBatch(AFTER_CUTOFF);

      final int deleted = underTest.deleteBatches(CUTOFF, LIMIT);

      assertThat(deleted).isZero();
      assertThat(testDataDAO.batchExists(recentBatch)).isTrue();
    }

    @Test
    void shouldOnlyDeleteOldBatches_whenMixed() {
      final UUID oldBatch = testDataDAO.insertBatch(BEFORE_CUTOFF);
      final UUID recentBatch = testDataDAO.insertBatch(AFTER_CUTOFF);

      final int deleted = underTest.deleteBatches(CUTOFF, LIMIT);

      assertThat(deleted).isEqualTo(1);
      assertThat(testDataDAO.batchExists(oldBatch)).isFalse();
      assertThat(testDataDAO.batchExists(recentBatch)).isTrue();
    }

    @Test
    void deleteBatch_shouldNotDeleteDetailResults() {
      final UUID oldBatch = testDataDAO.insertBatch(BEFORE_CUTOFF);
      final Long successId = testDataDAO.insertBatchSuccess(oldBatch, BEFORE_CUTOFF);
      final Long failureId = testDataDAO.insertBatchFailure(oldBatch, BEFORE_CUTOFF);

      underTest.deleteBatches(CUTOFF, LIMIT);

      assertThat(testDataDAO.batchExists(oldBatch)).isFalse();
      assertThat(testDataDAO.batchSuccessExists(successId)).isTrue();
      assertThat(testDataDAO.batchFailureExists(failureId)).isTrue();
    }

    @Test
    void shouldRespectLimit() {
      testDataDAO.insertBatch(BEFORE_CUTOFF);
      testDataDAO.insertBatch(BEFORE_CUTOFF);
      testDataDAO.insertBatch(BEFORE_CUTOFF);

      final int deleted = underTest.deleteBatches(CUTOFF, 2);

      assertThat(deleted).isEqualTo(2);
    }
  }

  @Nested
  class DeleteBatchResults {

    @ParameterizedTest
    @EnumSource(ResultType.class)
    void shouldDeleteResultOfOldBatch(final ResultType type) {
      testDataDAO.insertBatch(AFTER_CUTOFF);
      final UUID oldBatch = testDataDAO.insertBatch(BEFORE_CUTOFF);
      final Long resultId = insertResult(oldBatch, BEFORE_CUTOFF, type);

      final int deleted = deleteResults(LIMIT, type);

      assertThat(deleted).isEqualTo(1);
      assertThat(resultExists(resultId, type)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ResultType.class)
    void shouldNotDeleteResultOfRecentBatch(final ResultType type) {
      testDataDAO.insertBatch(BEFORE_CUTOFF);
      final UUID recentBatch = testDataDAO.insertBatch(AFTER_CUTOFF);
      final Long resultId = insertResult(recentBatch, AFTER_CUTOFF, type);

      final int deleted = deleteResults(LIMIT, type);

      assertThat(deleted).isZero();
      assertThat(resultExists(resultId, type)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(ResultType.class)
    void shouldRespectLimit(final ResultType type) {
      final UUID oldBatch = testDataDAO.insertBatch(BEFORE_CUTOFF);
      insertResult(oldBatch, BEFORE_CUTOFF, type);
      insertResult(oldBatch, BEFORE_CUTOFF, type);
      insertResult(oldBatch, BEFORE_CUTOFF, type);

      final int deleted = deleteResults(2, type);

      assertThat(deleted).isEqualTo(2);
      assertThat(countResultsForBatch(oldBatch, type)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(ResultType.class)
    void shouldDeleteResult_whenBatchIsOld_regardlessOfResultTimestamp(final ResultType type) {
      final UUID oldBatch = testDataDAO.insertBatch(BEFORE_CUTOFF);
      final Long resultId = insertResult(oldBatch, AFTER_CUTOFF, type);

      final int deleted = deleteResults(LIMIT, type);

      assertThat(deleted).isEqualTo(1);
      assertThat(resultExists(resultId, type)).isFalse();
    }
  }

  @Nested
  class DeleteOrphanedBatchResults {

    @ParameterizedTest
    @EnumSource(ResultType.class)
    void shouldDeleteOldOrphanedResult(final ResultType type) {
      final UUID orphanedBatchId = UUID.randomUUID();
      insertResult(UUID.randomUUID(), AFTER_CUTOFF, type);
      final Long resultId = insertResult(orphanedBatchId, BEFORE_CUTOFF, type);

      int deleted = deleteOrphanedResults(LIMIT, type);

      assertThat(deleted).isEqualTo(1);
      assertThat(resultExists(resultId, type)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ResultType.class)
    void shouldNotDeleteRecentOrphanedResult(final ResultType type) {
      final UUID orphanedBatchId = UUID.randomUUID();
      final Long resultId = insertResult(orphanedBatchId, AFTER_CUTOFF, type);

      final int deleted = deleteOrphanedResults(LIMIT, type);

      assertThat(deleted).isZero();
      assertThat(resultExists(resultId, type)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(ResultType.class)
    void shouldNotDeleteNonOrphanedResult(final ResultType type) {
      final UUID batchId = testDataDAO.insertBatch(BEFORE_CUTOFF);
      final Long resultId = insertResult(batchId, BEFORE_CUTOFF, type);

      final int deleted = deleteOrphanedResults(LIMIT, type);

      assertThat(deleted).isZero();
      assertThat(resultExists(resultId, type)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(ResultType.class)
    void shouldRespectLimit(final ResultType type) {
      final UUID orphanedBatchId = UUID.randomUUID();
      insertResult(orphanedBatchId, BEFORE_CUTOFF, type);
      insertResult(orphanedBatchId, BEFORE_CUTOFF, type);
      insertResult(orphanedBatchId, BEFORE_CUTOFF, type);

      final int deleted = deleteOrphanedResults(2, type);

      assertThat(deleted).isEqualTo(2);
      assertThat(countResultsForBatch(orphanedBatchId, type)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(ResultType.class)
    void shouldNotDeleteWhenOrphanedBatchHasRecentResultOfSameType(final ResultType type) {
      final UUID orphanedBatchId = UUID.randomUUID();
      final Long resultId = insertResult(orphanedBatchId, BEFORE_CUTOFF, type);
      insertResult(orphanedBatchId, AFTER_CUTOFF, type);

      final int deleted = deleteOrphanedResults(LIMIT, type);

      assertThat(deleted).isZero();
      assertThat(resultExists(resultId, type)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(ResultType.class)
    void shouldNotDeleteWhenOrphanedBatchHasRecentResultOfOtherType(final ResultType type) {
      final UUID orphanedBatchId = UUID.randomUUID();
      final Long resultId = insertResult(orphanedBatchId, BEFORE_CUTOFF, type);
      insertResult(orphanedBatchId, AFTER_CUTOFF, type.other());

      final int deleted = deleteOrphanedResults(LIMIT, type);

      assertThat(deleted).isZero();
      assertThat(resultExists(resultId, type)).isTrue();
    }
  }
}
