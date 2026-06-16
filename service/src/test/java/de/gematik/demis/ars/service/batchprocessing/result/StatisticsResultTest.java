package de.gematik.demis.ars.service.batchprocessing.result;

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

import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StatisticsResultTest {
  @Test
  void finished() {
    final int total = 25;
    final Parameters parameters = new Parameters();
    final StatisticsResult result = StatisticsResult.finished(total, parameters);
    assertThat(result).isNotNull();
    assertThat(result.total()).isEqualTo(total);
    assertThat(result.inProgress()).isFalse();
    assertThat(result.progressPercent()).isEqualTo(100);
    assertThat(result.parameters()).isEqualTo(parameters);
  }

  @ParameterizedTest
  @CsvSource({"2,1,0,50", "2,0,1,50", "3,1,1,66"})
  void inProgress(
      final int total, final int successCount, final int errorCount, final int progressPercent) {
    final StatisticsResult result = StatisticsResult.inProgress(total, successCount, errorCount);
    assertThat(result).isNotNull();
    assertThat(result.total()).isEqualTo(total);
    assertThat(result.inProgress()).isTrue();
    assertThat(result.progressPercent()).isEqualTo(progressPercent);
    assertThat(result.parameters()).isNull();
  }
}
