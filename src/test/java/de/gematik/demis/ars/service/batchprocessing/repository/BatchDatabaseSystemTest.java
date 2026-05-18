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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import de.gematik.demis.ars.service.batchprocessing.entity.BatchEntity;
import de.gematik.demis.ars.service.batchprocessing.entity.BatchFailureEntity;
import de.gematik.demis.ars.service.batchprocessing.entity.BatchSuccessEntity;
import de.gematik.demis.ars.service.batchprocessing.entity.ErrorReasonEnum;
import de.gematik.demis.ars.service.batchprocessing.test.PostgresTestContainer;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test-without-rabbitmq", "batch-test"})
class BatchDatabaseSystemTest extends PostgresTestContainer {

  @Nested
  class BatchRepositoryTest {

    @Autowired private BatchRepository batchRepository;

    @Test
    void shouldSaveAndFind() {
      UUID batchId = UUID.randomUUID();
      BatchEntity entity = new BatchEntity();
      entity.setBatchId(batchId);
      entity.setNumberOfNotifications(5);

      BatchEntity saved = batchRepository.save(entity);

      Optional<BatchEntity> found = batchRepository.findById(batchId);
      assertThat(found).isPresent();

      BatchEntity fromDb = found.get();
      assertThat(fromDb).usingRecursiveComparison().ignoringFields("createdAt").isEqualTo(saved);
      assertThat(fromDb.getCreatedAt())
          .isNotNull()
          .isCloseTo(Instant.now(), within(3, ChronoUnit.SECONDS));
    }
  }

  @Nested
  class BatchResultDAOTest {

    @Autowired BatchResultDAO batchResultDAO;
    @Autowired EntityManager entityManager;

    private static BatchSuccessEntity createBatchSuccessEntity(final UUID batchId) {
      final BatchSuccessEntity entity = new BatchSuccessEntity();
      entity.setBatchId(batchId);
      entity.setDocumentId(UUID.randomUUID().toString());
      entity.setNotificationBundleId(UUID.randomUUID().toString());
      entity.setWarningCount(10);
      return entity;
    }

    private static BatchFailureEntity createBatchFailureEntity(
        final UUID batchId, final ErrorReasonEnum errorReason) {
      final BatchFailureEntity entity = new BatchFailureEntity();
      entity.setBatchId(batchId);
      entity.setDocumentId(UUID.randomUUID().toString());
      entity.setErrorReason(errorReason);
      entity.setErrorCount(5);
      entity.setWarningCount(10);
      entity.setDetail("TEST");
      return entity;
    }

    private static void repeat(final int times, final Runnable action) {
      for (int i = 0; i < times; ++i) {
        action.run();
      }
    }

    @Test
    void batchSuccessEntity() {
      final BatchSuccessEntity entity = createBatchSuccessEntity(UUID.randomUUID());

      batchResultDAO.save(entity);
      assertThat(entity.getId()).isNotNull();

      final BatchSuccessEntity fromDb =
          entityManager.find(BatchSuccessEntity.class, entity.getId());
      assertThat(fromDb).usingRecursiveComparison().ignoringFields("createdAt").isEqualTo(entity);
      assertThat(fromDb.getCreatedAt())
          .isNotNull()
          .isCloseTo(Instant.now(), within(3, ChronoUnit.SECONDS));
    }

    @Test
    void batchFailureEntity() {
      final BatchFailureEntity entity =
          createBatchFailureEntity(UUID.randomUUID(), ErrorReasonEnum.VALIDATION);

      batchResultDAO.save(entity);
      assertThat(entity.getId()).isNotNull();

      final BatchFailureEntity fromDb =
          entityManager.find(BatchFailureEntity.class, entity.getId());
      assertThat(fromDb).usingRecursiveComparison().ignoringFields("createdAt").isEqualTo(entity);
      assertThat(fromDb.getCreatedAt())
          .isNotNull()
          .isCloseTo(Instant.now(), within(3, ChronoUnit.SECONDS));
    }

    @Test
    void countSuccess() {
      final UUID batchId = UUID.randomUUID();
      final int expectedCount = 3;
      repeat(expectedCount, () -> batchResultDAO.save(createBatchSuccessEntity(batchId)));
      // other batch
      batchResultDAO.save(createBatchSuccessEntity(UUID.randomUUID()));

      final int actualCount = batchResultDAO.countSuccess(batchId);

      assertThat(actualCount).isEqualTo(expectedCount);
    }

    @Test
    void countFailure() {
      final UUID batchId = UUID.randomUUID();
      final int expectedCount = 3;
      repeat(
          expectedCount,
          () -> batchResultDAO.save(createBatchFailureEntity(batchId, ErrorReasonEnum.VALIDATION)));
      // other batch
      batchResultDAO.save(createBatchFailureEntity(UUID.randomUUID(), ErrorReasonEnum.VALIDATION));

      final int actualCount = batchResultDAO.countFailures(batchId);

      assertThat(actualCount).isEqualTo(expectedCount);
    }

    @Test
    void countErrorsPerReason() {
      final var expectedErrorCounts =
          Map.of(
              ErrorReasonEnum.WAF,
              2L,
              ErrorReasonEnum.VALIDATION,
              3L,
              ErrorReasonEnum.INTERNAL_ERROR,
              1L);
      final UUID batchId = UUID.randomUUID();
      expectedErrorCounts.forEach(
          (reason, count) ->
              repeat(
                  count.intValue(),
                  () -> batchResultDAO.save(createBatchFailureEntity(batchId, reason))));

      final Map<ErrorReasonEnum, Long> result =
          batchResultDAO.countFailuresGroupByErrorReason(batchId);

      assertThat(result).isEqualTo(expectedErrorCounts);
    }

    @Test
    void forEachBatchResult() {
      final UUID batchId = UUID.randomUUID();
      final int numberOfEntries = 10;
      // create n success entries to one batch, wich we will fetch
      final List<BatchSuccessEntity> expectedEntities = new ArrayList<>();
      for (int i = 0; i < numberOfEntries; ++i) {
        final BatchSuccessEntity entity = createBatchSuccessEntity(batchId);
        batchResultDAO.save(entity);
        expectedEntities.add(entity);
      }
      // and one entry to another batch, which is not fetched
      batchResultDAO.save(createBatchSuccessEntity(UUID.randomUUID()));

      final List<BatchSuccessEntity> resultSuccess = new ArrayList<>();
      batchResultDAO.forEachBatchResult(batchId, BatchSuccessEntity.class, resultSuccess::add);

      assertThat(resultSuccess)
          .usingRecursiveFieldByFieldElementComparatorIgnoringFields("createdAt")
          .containsExactlyElementsOf(expectedEntities);

      // and now we load the failures to this batch which are not present
      final List<BatchFailureEntity> resultFailure = new ArrayList<>();
      batchResultDAO.forEachBatchResult(batchId, BatchFailureEntity.class, resultFailure::add);
      assertThat(resultFailure).isEmpty();
    }
  }
}
