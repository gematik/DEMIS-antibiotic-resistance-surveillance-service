package de.gematik.demis.ars.purger;

/*-
 * #%L
 * surveillance-pseudonym-purger
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

import de.gematik.demis.ars.purger.test.DbImporter;
import de.gematik.demis.ars.purger.test.TestDataDAO;
import de.gematik.demis.ars.purger.test.TestWithPostgresContainer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "app.purger.retention-days=" + PurgerIntegrationSystemTest.RETENTION_DAYS,
      "app.purger.additional-retention-days-for-orphan-results="
          + PurgerIntegrationSystemTest.ADDITIONAL_RETENTION_DAYS_FOR_ORPHAN_RESULTS
    })
@Import({TestDataDAO.class, DbImporter.class})
@Slf4j
class PurgerIntegrationSystemTest extends TestWithPostgresContainer {

  static final int RETENTION_DAYS = 14;
  static final int ADDITIONAL_RETENTION_DAYS_FOR_ORPHAN_RESULTS = 2;

  @Autowired PurgerApplication underTest;
  @Autowired TestDataDAO testDataDAO;
  @Autowired DbImporter dbImporter;

  @BeforeEach
  void cleanDatabase() {
    testDataDAO.truncateAllTables();
  }

  @Test
  @Timeout(60)
  void deleteManyRows() throws Exception {
    log.info("---- TEST START -----");
    log.info("fill database...");

    final Instant cutOffDate = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
    final Instant orphanCutOffDate =
        cutOffDate.minus(ADDITIONAL_RETENTION_DAYS_FOR_ORPHAN_RESULTS, ChronoUnit.DAYS);

    final int orphanSuccessToDeleteCount = 950000;
    final int orphanFailureToDeleteCount = 50000;
    final int orphanSuccessToRetentCount = 80000;
    final int orphanFailureToRetentCount = 10;
    final int successToDeleteCount = 200000;
    final int failureToDeleteCount = 10;
    final int successToRetentCount = 150000;
    final int failureToRetentCount = 100000;

    final UUID orphanBatchToDelete = UUID.randomUUID();
    dbImporter.insertBatchSuccess(
        orphanSuccessToDeleteCount,
        orphanBatchToDelete,
        orphanCutOffDate.minus(1, ChronoUnit.HOURS));
    dbImporter.insertBatchFailure(
        orphanFailureToDeleteCount,
        orphanBatchToDelete,
        orphanCutOffDate.minus(4, ChronoUnit.HOURS));

    final UUID orphanBatchToRetent = UUID.randomUUID();
    dbImporter.insertBatchSuccess(
        orphanSuccessToRetentCount,
        orphanBatchToRetent,
        orphanCutOffDate.plus(1, ChronoUnit.HOURS));
    dbImporter.insertBatchFailure(
        orphanFailureToRetentCount,
        orphanBatchToRetent,
        orphanCutOffDate.plus(2, ChronoUnit.HOURS));

    final UUID batchToDelete = testDataDAO.insertBatch(cutOffDate.minus(1, ChronoUnit.DAYS));
    dbImporter.insertBatchSuccess(successToDeleteCount, batchToDelete, cutOffDate);
    dbImporter.insertBatchFailure(failureToDeleteCount, batchToDelete, cutOffDate);

    final UUID batchToRetent = testDataDAO.insertBatch(cutOffDate.plus(1, ChronoUnit.DAYS));
    dbImporter.insertBatchSuccess(successToRetentCount, batchToRetent, cutOffDate);
    dbImporter.insertBatchFailure(failureToRetentCount, batchToRetent, cutOffDate);

    final int totalSuccessCount =
        orphanSuccessToDeleteCount
            + orphanSuccessToRetentCount
            + successToDeleteCount
            + successToRetentCount;
    final int totalFailureCount =
        orphanFailureToDeleteCount
            + orphanFailureToRetentCount
            + failureToDeleteCount
            + failureToRetentCount;
    assertThat(testDataDAO.countBatchSuccess()).isEqualTo(totalSuccessCount);
    assertThat(testDataDAO.countBatchFailure()).isEqualTo(totalFailureCount);
    log.info(
        "database filled with {} success and {} failure entries",
        totalSuccessCount,
        totalFailureCount);

    log.info("execute purger...");
    underTest.run();

    assertThat(testDataDAO.countBatchSuccessForBatch(orphanBatchToDelete)).isZero();
    assertThat(testDataDAO.countBatchFailureForBatch(orphanBatchToDelete)).isZero();

    assertThat(testDataDAO.countBatchSuccessForBatch(orphanBatchToRetent))
        .isEqualTo(orphanSuccessToRetentCount);

    assertThat(testDataDAO.countBatchSuccessForBatch(batchToDelete)).isZero();
    assertThat(testDataDAO.countBatchFailureForBatch(batchToDelete)).isZero();
    assertThat(testDataDAO.batchExists(batchToDelete)).isFalse();

    assertThat(testDataDAO.countBatchSuccessForBatch(batchToRetent))
        .isEqualTo(successToRetentCount);
    assertThat(testDataDAO.countBatchFailureForBatch(batchToRetent))
        .isEqualTo(failureToRetentCount);
    assertThat(testDataDAO.batchExists(batchToRetent)).isTrue();

    final int expectedSuccessCountAfterPurge = orphanSuccessToRetentCount + successToRetentCount;
    final int expectedFailureCountAfterPurge = orphanFailureToRetentCount + failureToRetentCount;
    assertThat(testDataDAO.countBatchSuccess()).isEqualTo(expectedSuccessCountAfterPurge);
    assertThat(testDataDAO.countBatchFailure()).isEqualTo(expectedFailureCountAfterPurge);
  }
}
