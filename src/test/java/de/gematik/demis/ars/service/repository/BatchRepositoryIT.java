package de.gematik.demis.ars.service.repository;

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

import de.gematik.demis.ars.service.PostgresTestContainer;
import de.gematik.demis.ars.service.batchprocessing.entity.Batch;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BatchRepositoryIT extends PostgresTestContainer {
  @Autowired private BatchRepository batchRepository;

  @Test
  void shouldSaveAndFind() {
    UUID batchId = UUID.randomUUID();
    Batch entity = new Batch();
    entity.setBatchId(batchId);
    entity.setNumberOfNotifications(5);

    Batch saved = batchRepository.save(entity);

    Optional<Batch> found = batchRepository.findById(batchId);
    assertThat(found).isPresent();

    Batch fromDb = found.get();
    assertThat(fromDb).usingRecursiveComparison().ignoringFields("createdAt").isEqualTo(saved);
    assertThat(fromDb.getCreatedAt())
        .isNotNull()
        .isCloseTo(Instant.now(), within(3, ChronoUnit.SECONDS));
  }
}
