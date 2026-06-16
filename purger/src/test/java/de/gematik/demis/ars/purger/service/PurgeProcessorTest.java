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
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.demis.ars.purger.config.PurgerConfigProps;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurgeProcessorTest {

  private static final int RETENTION_DAYS = 7;
  private static final int ADDITION_RETENTION_DAY_FOR_ORPHAN_RESULTS = 3;
  private static final int CHUNK_SIZE = 1000;

  @Mock DeleteService deleteService;
  @Mock PurgerConfigProps configProps;

  @InjectMocks PurgeProcessor underTest;

  @BeforeEach
  void setUp() {
    when(configProps.retentionDays()).thenReturn(RETENTION_DAYS);
    when(configProps.additionalRetentionDaysForOrphanResults())
        .thenReturn(ADDITION_RETENTION_DAY_FOR_ORPHAN_RESULTS);
    when(configProps.chunkSize()).thenReturn(CHUNK_SIZE);
  }

  @Test
  void allDeleteMethodsAreInvoked() {
    underTest.purgeDatabase();

    verify(deleteService).deleteBatchSuccess(any(Instant.class), eq(CHUNK_SIZE));
    verify(deleteService).deleteBatchFailures(any(Instant.class), eq(CHUNK_SIZE));
    verify(deleteService).deleteBatches(any(Instant.class), eq(CHUNK_SIZE));
    verify(deleteService).deleteOrphanedBatchSuccess(any(Instant.class), eq(CHUNK_SIZE));
    verify(deleteService).deleteOrphanedBatchFailures(any(Instant.class), eq(CHUNK_SIZE));
  }

  // it is important that we delete the old batch after deleting the corresponding results
  @Test
  void deleteOrder() {
    underTest.purgeDatabase();

    final InOrder batchAfterSuccessResult = inOrder(deleteService);
    batchAfterSuccessResult.verify(deleteService).deleteBatchSuccess(any(), anyInt());
    batchAfterSuccessResult.verify(deleteService).deleteBatches(any(), anyInt());

    final InOrder batchAfterFailureResult = inOrder(deleteService);
    batchAfterFailureResult.verify(deleteService).deleteBatchFailures(any(), anyInt());
    batchAfterFailureResult.verify(deleteService).deleteBatches(any(), anyInt());
  }

  @Test
  void deleteDateIsCalculatedFromRetentionDays() {
    final Instant deleteBatchesBefore = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);

    underTest.purgeDatabase();

    final ArgumentCaptor<Instant> batchSuccessCaptor = ArgumentCaptor.forClass(Instant.class);
    final ArgumentCaptor<Instant> batchFailuresCaptor = ArgumentCaptor.forClass(Instant.class);
    final ArgumentCaptor<Instant> batchesCaptor = ArgumentCaptor.forClass(Instant.class);
    final ArgumentCaptor<Instant> orphanedSuccessCaptor = ArgumentCaptor.forClass(Instant.class);
    final ArgumentCaptor<Instant> orphanedFailuresCaptor = ArgumentCaptor.forClass(Instant.class);

    verify(deleteService).deleteBatchSuccess(batchSuccessCaptor.capture(), anyInt());
    verify(deleteService).deleteBatchFailures(batchFailuresCaptor.capture(), anyInt());
    verify(deleteService).deleteBatches(batchesCaptor.capture(), anyInt());
    verify(deleteService).deleteOrphanedBatchSuccess(orphanedSuccessCaptor.capture(), anyInt());
    verify(deleteService).deleteOrphanedBatchFailures(orphanedFailuresCaptor.capture(), anyInt());

    final Instant referenceDate = batchesCaptor.getValue();
    assertThat(referenceDate).isCloseTo(deleteBatchesBefore, within(500, ChronoUnit.MILLIS));
    assertThat(batchSuccessCaptor.getValue()).isEqualTo(referenceDate);
    assertThat(batchFailuresCaptor.getValue()).isEqualTo(referenceDate);
    final Instant expectedOrphanDate =
        referenceDate.minus(ADDITION_RETENTION_DAY_FOR_ORPHAN_RESULTS, ChronoUnit.DAYS);
    assertThat(orphanedSuccessCaptor.getValue()).isEqualTo(expectedOrphanDate);
    assertThat(orphanedFailuresCaptor.getValue()).isEqualTo(expectedOrphanDate);
  }

  @Test
  void batchIsDeletedInMultipleChunks() {
    when(deleteService.deleteBatches(any(), anyInt()))
        .thenReturn(CHUNK_SIZE)
        .thenReturn(CHUNK_SIZE - 1);

    underTest.purgeDatabase();

    verify(deleteService, times(2)).deleteBatches(any(Instant.class), eq(CHUNK_SIZE));
  }

  @Test
  void batchSuccessIsDeletedInMultipleChunks() {
    when(deleteService.deleteBatchSuccess(any(), anyInt()))
        .thenReturn(CHUNK_SIZE)
        .thenReturn(CHUNK_SIZE)
        .thenReturn(CHUNK_SIZE - 1);

    underTest.purgeDatabase();

    verify(deleteService, times(3)).deleteBatchSuccess(any(Instant.class), eq(CHUNK_SIZE));
  }

  @Test
  void batchFailuresIsDeletedInMultipleChunks() {
    when(deleteService.deleteBatchFailures(any(), anyInt()))
        .thenReturn(CHUNK_SIZE)
        .thenReturn(CHUNK_SIZE)
        .thenReturn(0);

    underTest.purgeDatabase();

    verify(deleteService, times(3)).deleteBatchFailures(any(Instant.class), eq(CHUNK_SIZE));
  }

  @Test
  void orphanedSuccessIsDeletedInMultipleChunks() {
    when(deleteService.deleteOrphanedBatchSuccess(any(), anyInt()))
        .thenReturn(CHUNK_SIZE)
        .thenReturn(1);

    underTest.purgeDatabase();

    verify(deleteService, times(2)).deleteOrphanedBatchSuccess(any(Instant.class), eq(CHUNK_SIZE));
  }

  @Test
  void orphanedFailuresIsDeletedInMultipleChunks() {
    when(deleteService.deleteOrphanedBatchFailures(any(), anyInt()))
        .thenReturn(CHUNK_SIZE)
        .thenReturn(0);

    underTest.purgeDatabase();

    verify(deleteService, times(2)).deleteOrphanedBatchFailures(any(Instant.class), eq(CHUNK_SIZE));
  }
}
