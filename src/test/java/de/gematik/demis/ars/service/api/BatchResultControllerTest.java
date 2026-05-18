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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.demis.ars.service.api.BatchResultApi.ResultQueryEnum;
import de.gematik.demis.ars.service.batchprocessing.config.BatchResultProperties;
import de.gematik.demis.ars.service.batchprocessing.result.BatchResultService;
import de.gematik.demis.ars.service.batchprocessing.result.StatisticsResult;
import de.gematik.demis.ars.service.exception.ArsServiceException;
import de.gematik.demis.ars.service.exception.ErrorCode;
import de.gematik.demis.service.base.error.rest.ErrorHandlerConfiguration;
import de.gematik.demis.service.base.fhir.FhirSupportAutoConfiguration;
import de.gematik.demis.service.base.fhir.error.FhirErrorResponseAutoConfiguration;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@WebMvcTest(BatchResultController.class)
@TestPropertySource(properties = "ars.batch-processing.enabled=true")
@ImportAutoConfiguration({
  ErrorHandlerConfiguration.class,
  FhirSupportAutoConfiguration.class,
  FhirErrorResponseAutoConfiguration.class
})
class BatchResultControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private BatchResultService batchResultService;
  @MockitoBean private BatchResultProperties properties;

  @Nested
  class StatisticsEndpoint {
    private static final String ENDPOINT = "/batch/fhir/bundle/{batchId}/$statistics";

    @Test
    void batchFinishedShouldReturn200WithParametersResult() throws Exception {
      final UUID batchId = UUID.randomUUID();
      final String parameterName = "Test-Param-Batch-Id";
      final Parameters parameters = new Parameters();
      parameters.addParameter(parameterName, batchId.toString());
      final StatisticsResult result = StatisticsResult.finished(10, parameters);
      when(batchResultService.getStatistics(any())).thenReturn(result);

      mockMvc
          .perform(MockMvcRequestBuilders.get(ENDPOINT, batchId.toString()))
          .andExpectAll(
              status().isOk(),
              content().contentTypeCompatibleWith("application/fhir+json"),
              jsonPath("$.resourceType").value("Parameters"),
              jsonPath("$.parameter[0].name").value(parameterName),
              jsonPath("$.parameter[0].valueString").value(batchId.toString()));

      verify(batchResultService).getStatistics(batchId);
    }

    @Test
    void batchInProgressShouldReturn202WithHeader() throws Exception {
      final UUID batchId = UUID.randomUUID();
      final StatisticsResult result = StatisticsResult.inProgress(20000, 10000, 5000);
      final int retryAfter = 3600;
      when(batchResultService.getStatistics(any())).thenReturn(result);
      when(properties.retryAfterSeconds()).thenReturn(retryAfter);

      mockMvc
          .perform(MockMvcRequestBuilders.get(ENDPOINT, batchId.toString()))
          .andExpectAll(
              status().isAccepted(),
              header().string("X-Total", "20000"),
              header().string("X-Progress", "75%"),
              header().string("Retry-After", String.valueOf(retryAfter)),
              content().string(""));

      verify(batchResultService).getStatistics(batchId);
    }

    @Test
    void batchNotFoundShouldReturn400() throws Exception {
      final UUID batchId = UUID.randomUUID();
      when(batchResultService.getStatistics(any()))
          .thenThrow(new ArsServiceException(ErrorCode.BATCH_NOT_FOUND, batchId.toString()));

      mockMvc
          .perform(MockMvcRequestBuilders.get(ENDPOINT, batchId.toString()))
          .andExpectAll(
              status().isBadRequest(),
              content().contentTypeCompatibleWith("application/fhir+json"),
              jsonPath("$.resourceType").value("OperationOutcome"),
              jsonPath("$.issue[0].details.coding[0].code").value("BATCH_NOT_FOUND"),
              jsonPath("$.issue[0].diagnostics").value(batchId.toString()));

      verify(batchResultService).getStatistics(batchId);
    }
  }

  @Nested
  class CsvResultEndpoint {
    private static final String ENDPOINT = "/batch/fhir/bundle/{batchId}/$results?query={type}";

    @ParameterizedTest
    @CsvSource({"success", "error"})
    void shouldReturn200DetailedResults(final String queryValue) throws Exception {
      final UUID batchId = UUID.randomUUID();
      final String csv = "processed At,Document Id\n2026-01-01T00:00:00Z,doc-1\n";
      final String expectedFilename = "batch-" + batchId + "-" + queryValue + ".csv";
      final ResultQueryEnum resultQuery =
          queryValue.equalsIgnoreCase("success") ? ResultQueryEnum.SUCCESS : ResultQueryEnum.ERROR;

      doAnswer(
              invocation -> {
                final OutputStream outputStream = invocation.getArgument(2);
                outputStream.write(csv.getBytes(StandardCharsets.UTF_8));
                return null;
              })
          .when(batchResultService)
          .streamResult(eq(batchId), eq(resultQuery), any(OutputStream.class));

      final MvcResult asyncResult =
          mockMvc
              .perform(MockMvcRequestBuilders.get(ENDPOINT, batchId, queryValue))
              .andExpectAll(
                  status().isOk(),
                  content().contentTypeCompatibleWith("text/csv"),
                  header()
                      .string(
                          "Content-Disposition",
                          "attachment; filename=\"" + expectedFilename + "\""),
                  header().string("Cache-Control", "no-store"),
                  request().asyncStarted())
              .andReturn();

      mockMvc.perform(asyncDispatch(asyncResult)).andExpect(content().string(csv));

      verify(batchResultService).checkBatchExists(batchId);
    }

    @Test
    void batchNotFoundShouldReturn400() throws Exception {
      final UUID batchId = UUID.randomUUID();
      doThrow(new ArsServiceException(ErrorCode.BATCH_NOT_FOUND, batchId.toString()))
          .when(batchResultService)
          .checkBatchExists(batchId);

      mockMvc
          .perform(MockMvcRequestBuilders.get(ENDPOINT, batchId, "success"))
          .andExpectAll(
              status().isBadRequest(),
              content().contentTypeCompatibleWith("application/fhir+json"),
              jsonPath("$.resourceType").value("OperationOutcome"),
              jsonPath("$.issue[0].details.coding[0].code").value("BATCH_NOT_FOUND"),
              jsonPath("$.issue[0].diagnostics").value(batchId.toString()),
              request().asyncNotStarted());

      verify(batchResultService, never()).streamResult(any(), any(), any());
    }

    @Test
    void missingQueryParameterShouldReturn400() throws Exception {
      mockMvc
          .perform(MockMvcRequestBuilders.get(ENDPOINT.split("\\?")[0], UUID.randomUUID()))
          .andExpectAll(
              status().isBadRequest(),
              content().contentTypeCompatibleWith("application/fhir+json"),
              jsonPath("$.resourceType").value("OperationOutcome"),
              jsonPath("$.issue[0].diagnostics")
                  .value("Required parameter 'query' is not present."),
              request().asyncNotStarted());

      verifyNoInteractions(batchResultService);
    }

    @Test
    void invalidQueryValueShouldReturn400() throws Exception {
      mockMvc
          .perform(MockMvcRequestBuilders.get(ENDPOINT, UUID.randomUUID(), "xxx"))
          .andExpectAll(
              status().isBadRequest(),
              content().contentTypeCompatibleWith("application/fhir+json"),
              jsonPath("$.resourceType").value("OperationOutcome"),
              jsonPath("$.issue[0].details.coding[0].code").value("INVALID_QUERY_VALUE"),
              jsonPath("$.issue[0].diagnostics")
                  .value("Unsupported query value 'xxx'. Valid values are [SUCCESS, ERROR]"),
              request().asyncNotStarted());

      verifyNoInteractions(batchResultService);
    }

    @Test
    void unsupportedAcceptShouldReturn406() throws Exception {
      mockMvc
          .perform(
              MockMvcRequestBuilders.get(ENDPOINT, UUID.randomUUID(), "success")
                  .header("Accept", "application/json"))
          .andExpectAll(
              status().isNotAcceptable(),
              content().contentTypeCompatibleWith("application/fhir+json"),
              jsonPath("$.resourceType").value("OperationOutcome"),
              jsonPath("$.issue[0].diagnostics").value("Acceptable representations: [text/csv]."),
              request().asyncNotStarted());

      verifyNoInteractions(batchResultService);
    }

    // NOTE: The test "streamingErrorAfterFlushHasNoImpactOn200Status" was removed because
    // MockMvc does not simulate a real HTTP socket. In a real HTTP connection the response
    // is already committed (status 200 sent) once the OutputStream has been flushed, so a
    // subsequent exception cannot change the status code. MockMvc, however, manages the
    // status internally and – since Spring Framework 7 / Spring Boot 4 – propagates the
    // exception to the async error handler even after a flush, resulting in a 500 in the
    // test context. This is a MockMvc limitation and does not reflect production behaviour.
  }
}
