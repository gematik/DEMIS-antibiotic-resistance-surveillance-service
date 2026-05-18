package de.gematik.demis.ars.service.api;

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

import static java.util.Objects.requireNonNull;

import de.gematik.demis.ars.service.batchprocessing.config.BatchResultProperties;
import de.gematik.demis.ars.service.batchprocessing.result.BatchResultService;
import de.gematik.demis.ars.service.batchprocessing.result.StatisticsResult;
import de.gematik.demis.ars.service.exception.ArsServiceException;
import de.gematik.demis.ars.service.exception.ErrorCode;
import de.gematik.demis.service.base.fhir.response.FhirResponseConverter;
import java.util.Arrays;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@ConditionalOnProperty("ars.batch-processing.enabled")
@RequiredArgsConstructor
@Slf4j
class BatchResultController implements BatchResultApi {

  private final BatchResultService batchResultService;
  private final FhirResponseConverter fhirResponseConverter;
  private final BatchResultProperties properties;

  private static ResultQueryEnum toResultQueryEnum(final String value) {
    try {
      return ResultQueryEnum.valueOf(value.toUpperCase());
    } catch (final IllegalArgumentException _) {
      final String msg =
          "Unsupported query value '%s'. Valid values are %s"
              .formatted(value, Arrays.toString(ResultQueryEnum.values()));
      throw new ArsServiceException(ErrorCode.INVALID_QUERY_VALUE, msg);
    }
  }

  @Override
  public ResponseEntity<Object> getStatistics(final UUID batchId, final WebRequest webRequest) {
    final StatisticsResult result = batchResultService.getStatistics(batchId);
    if (result.inProgress()) {
      return ResponseEntity.accepted()
          .header(HEADER_X_TOTAL, String.valueOf(result.total()))
          .header(HEADER_X_PROGRESS, result.progressPercent() + "%")
          .header(HEADER_RETRY_AFTER, String.valueOf(properties.retryAfterSeconds()))
          .build();
    } else {
      return fhirResponseConverter.buildResponse(
          ResponseEntity.ok(), result.parameters(), webRequest);
    }
  }

  @Override
  public ResponseEntity<StreamingResponseBody> exportCsv(final UUID batchId, final String query) {
    final ResultQueryEnum type = toResultQueryEnum(requireNonNull(query));
    log.info("details for batch {} - {}", batchId, type);
    batchResultService.checkBatchExists(batchId);

    final StreamingResponseBody body =
        outputStream -> batchResultService.streamResult(batchId, type, outputStream);

    final String filename = "batch-" + batchId + "-" + type.name().toLowerCase() + ".csv";
    return ResponseEntity.ok()
        .contentType(MEDIA_TYPE_CSV)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(body);
  }
}
