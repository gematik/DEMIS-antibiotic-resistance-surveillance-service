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

import de.gematik.demis.ars.service.batchprocessing.entity.ErrorReasonEnum;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.Test;

class StatisticsParametersBuilderTest {
  private static final String EXPECTED =
"""
{
  "resourceType": "Parameters",
  "parameter": [
    { "name": "batchId", "valueString": "2e46bd82-5ce0-4253-bbe0-502ebead2607" },
    { "name": "batchClosedAt", "valueDateTime": "2026-03-10T17:34:59+01:00" },
    { "name": "resultsAvailableUntil", "valueDateTime": "2026-03-24T17:34:59+01:00" },
    { "name": "total", "valueInteger": 1000000 },
    {
      "name": "success",
      "part": [
        { "name": "url", "valueUri": "https://ingress.local/surveillance/antibiotic-resistance/v1/batch/fhir/bundle/2e46bd82-5ce0-4253-bbe0-502ebead2607/$results?query=success" },
        { "name": "contentType", "valueString": "text/csv" },
        { "name": "count", "valueInteger": 995000 }
      ]
    },
    {
      "name": "error",
      "part": [
        { "name": "url", "valueUri": "https://ingress.local/surveillance/antibiotic-resistance/v1/batch/fhir/bundle/2e46bd82-5ce0-4253-bbe0-502ebead2607/$results?query=error" },
        { "name": "contentType", "valueString": "text/csv" },
        { "name": "count", "valueInteger": 5000 },
        {
          "name": "countsByErrorCode",
          "part": [
            { "name": "WAF", "valueInteger": 1500 },
            { "name": "VALIDATION", "valueInteger": 3200 },
            { "name": "INVALID", "valueInteger": 0 },
            { "name": "INTERNAL_ERROR", "valueInteger": 300 }
          ]
        }
      ]
    }
  ]
}
""";

  @Test
  void build() {
    final var errorCounts =
        Map.of(
            ErrorReasonEnum.WAF,
            1500L,
            ErrorReasonEnum.VALIDATION,
            3200L,
            ErrorReasonEnum.INTERNAL_ERROR,
            300L);
    final StatisticsParametersBuilder underTest = new StatisticsParametersBuilder();
    final Parameters result =
        underTest
            .batchId(UUID.fromString("2e46bd82-5ce0-4253-bbe0-502ebead2607"))
            .completedAt(Instant.parse("2026-03-10T16:34:59Z"))
            .expiresAt(Instant.parse("2026-03-24T16:34:59Z"))
            .total(1000000)
            .successCount(995000)
            .failureCount(5000)
            .errorCountsPerReason(errorCounts)
            .detailUrl(
                URI.create(
                    "https://ingress.local/surveillance/antibiotic-resistance/v1/batch/fhir/bundle/2e46bd82-5ce0-4253-bbe0-502ebead2607/$results"))
            .build();

    final String resultAsJsonString = ParametersUtil.resourceToString(result);
    assertThat(resultAsJsonString).isEqualToIgnoringWhitespace(EXPECTED);
  }
}
