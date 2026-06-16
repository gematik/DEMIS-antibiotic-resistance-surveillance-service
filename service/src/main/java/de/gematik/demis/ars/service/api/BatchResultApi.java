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

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Tag(
    name = "batch",
    description =
        "Batch processing results. Available only when the batch feature flag is enabled "
            + "(ars.batch-processing.enabled=true).")
@RequestMapping(BatchResultApi.BATCH_RESULT_PATH)
public interface BatchResultApi {

  String BATCH_RESULT_PATH = "/batch/fhir/bundle/{batchId}";
  String OPERATION_STATISTICS = "$statistics";
  String OPERATION_DETAIL_RESULT = "$results";

  String HEADER_X_TOTAL = "X-Total";
  String HEADER_X_PROGRESS = "X-Progress";
  String HEADER_RETRY_AFTER = "Retry-After";

  String MEDIA_TYPE_FHIR_JSON = "application/fhir+json";
  MediaType MEDIA_TYPE_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);
  String MEDIA_TYPE_CSV_VALUE = "text/csv";

  @Operation(
      summary = "Get batch statistics",
      description = "Returns processing status and results as statistics for the given batch.")
  @ApiResponse(
      responseCode = "400",
      description = "Unknown batch or batch not closed. See ErrorCode.",
      content =
          @Content(
              mediaType = MEDIA_TYPE_FHIR_JSON,
              schema = @Schema(implementation = OperationOutcome.class)))
  @ApiResponse(
      responseCode = "202",
      description = "Batch still in progress. Response body has no content.",
      headers = {
        @Header(
            name = HEADER_X_TOTAL,
            description = "Total number of uploaded notifications",
            schema = @Schema(type = "integer", example = "1500")),
        @Header(
            name = HEADER_X_PROGRESS,
            description = "Processing progress in percent (0-99)",
            schema = @Schema(type = "integer", example = "73")),
        @Header(
            name = HEADER_RETRY_AFTER,
            description = "Number of seconds the client should wait before retrying",
            schema = @Schema(type = "integer", example = "1800"))
      },
      content = @Content // empty Body
      )
  @ApiResponse(
      responseCode = "200",
      description =
          "Batch fully processed. Statistics of the batch result is returned in the body as Fhir Parameters Resource.",
      content =
          @Content(
              mediaType = MEDIA_TYPE_FHIR_JSON,
              schema = @Schema(implementation = Parameters.class)))
  @GetMapping(
      value = OPERATION_STATISTICS,
      produces = {MEDIA_TYPE_FHIR_JSON, "application/json+fhir", APPLICATION_JSON_VALUE})
  ResponseEntity<Object> getStatistics(@PathVariable UUID batchId, final WebRequest webRequest);

  @Operation(
      summary = "Export batch results as CSV",
      description =
          "Exports the detailed results of a batch as a CSV file. The query parameter controls whether successful or erroneous results are exported.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "CSV export stream successfully triggered.",
            content =
                @Content(mediaType = MEDIA_TYPE_CSV_VALUE, schema = @Schema(type = "string"))),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request or unknown batch.",
            content =
                @Content(
                    mediaType = MEDIA_TYPE_FHIR_JSON,
                    schema = @Schema(implementation = OperationOutcome.class))),
      })
  @GetMapping(value = OPERATION_DETAIL_RESULT, produces = MEDIA_TYPE_CSV_VALUE)
  ResponseEntity<StreamingResponseBody> exportCsv(
      @PathVariable final UUID batchId,
      @Parameter(
              name = "query",
              description =
                  "Filter for the results: success (only successfully processed notifications), error (only erroneous)",
              required = true,
              schema = @Schema(allowableValues = {"success", "error"}))
          @RequestParam
          final String query,
      @RequestHeader(value = HttpHeaders.ACCEPT_ENCODING, required = false)
          final String acceptEncoding);

  enum ResultQueryEnum {
    SUCCESS,
    ERROR
  }
}
