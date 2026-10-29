package de.gematik.demis.ars.service.service.sentinel;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SentinelServiceTest {

  private final Bundle bundle = new Bundle();

  @Mock private SentinelTypeDetection typeDetection;
  @Mock private SentinelCleanupService cleanupService;
  @InjectMocks private SentinelService underTest;

  @BeforeEach
  void setUp() {
    underTest.setFeatureFlagSentinelEnabled(true);
  }

  @Test
  void shouldSkipCleanupForChlamydia() {
    executeTest(SentinelType.CHLAMYDIA);
    verifyNoInteractions(cleanupService);
  }

  @Test
  void shouldSkipCleanupForChlamydiaAndMeasles() {
    executeTest(SentinelType.MEASLES, SentinelType.CHLAMYDIA);
    verifyNoInteractions(cleanupService);
  }

  @Test
  void shouldRemovePatientAddressAndCoverageForMeasles() {
    executeTest(SentinelType.MEASLES);

    verify(cleanupService).removePatientAddress(bundle);
    verify(cleanupService).removeCoverage(bundle);
    verify(cleanupService, never()).removeReasonCodes(bundle);
  }

  @Test
  void shouldRemoveAllSentinelRelatedDataForNoSpecialType() {
    executeTest();

    verify(cleanupService).removePatientAddress(bundle);
    verify(cleanupService).removeCoverage(bundle);
    verify(cleanupService).removeReasonCodes(bundle);
  }

  @Test
  void shouldSkipDetectionAndRemoveAllSentinelRelatedDataWhenFeatureFlagIsDisabled() {
    underTest.setFeatureFlagSentinelEnabled(false);

    underTest.removeSentinelData(bundle);

    verifyNoInteractions(typeDetection);
    verify(cleanupService).removePatientAddress(bundle);
    verify(cleanupService).removeCoverage(bundle);
    verify(cleanupService).removeReasonCodes(bundle);
  }

  private void executeTest(SentinelType... detectedTypes) {
    when(typeDetection.detect(any())).thenReturn(Set.of(detectedTypes));
    underTest.removeSentinelData(bundle);
    verify(typeDetection).detect(bundle);
  }
}
