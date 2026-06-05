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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import de.gematik.demis.ars.service.batchprocessing.repository.BatchRepository;
import de.gematik.demis.ars.service.batchprocessing.result.BatchResultService;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import org.hibernate.SessionFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"batch-test", "without-database", "test-without-rabbitmq"})
class BatchResultControllerIT {

  private static final String ENDPOINT = "/batch/fhir/bundle/%s/$results?query=%s";

  @LocalServerPort private int port;

  @MockitoBean private BatchResultService batchResultService;

  // The following MockBeans are required to start the ApplicationContext
  @MockitoBean BatchRepository batchRepository;
  @MockitoBean EntityManager entityManager;
  @MockitoBean SessionFactory sessionFactory;

  @ParameterizedTest
  @CsvSource({"success", "error"})
  void shouldReturnDetailedResultsCompressed(final String queryValue) throws Exception {
    final UUID batchId = UUID.randomUUID();
    final String csv = "processed At,Document Id\n2026-01-01T00:00:00Z,doc-1\n";
    final String expectedFilename = "batch-" + batchId + "-" + queryValue + ".gzip";
    final BatchResultApi.ResultQueryEnum resultQuery =
        queryValue.equalsIgnoreCase("success")
            ? BatchResultApi.ResultQueryEnum.SUCCESS
            : BatchResultApi.ResultQueryEnum.ERROR;

    doAnswer(
            invocation -> {
              final OutputStream outputStream = invocation.getArgument(2);
              outputStream.write(csv.getBytes(StandardCharsets.UTF_8));
              return null;
            })
        .when(batchResultService)
        .streamResult(eq(batchId), eq(resultQuery), any(OutputStream.class));

    final HttpResponse<byte[]> response;
    try (HttpClient httpClient = HttpClient.newHttpClient()) {
      HttpRequest httpRequest =
          HttpRequest.newBuilder()
              .uri(
                  URI.create(
                      "http://localhost:" + port + String.format(ENDPOINT, batchId, queryValue)))
              .header("Accept-Encoding", "deflate, gzip")
              .GET()
              .build();

      response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
    }

    final String contentEncoding = response.headers().firstValue("Content-Encoding").orElse("");
    assertThat(contentEncoding).isEqualTo("gzip");

    final String contentDisposition =
        response.headers().firstValue("Content-Disposition").orElse("");
    assertThat(contentDisposition).isEqualTo("attachment; filename=\"" + expectedFilename + "\"");

    final String csvData = decompressGzip(response.body());
    assertThat(csvData).isNotNull().isEqualTo(csv);
  }

  private String decompressGzip(byte[] compressedBytes) throws IOException {
    try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressedBytes);
        GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream);
        InputStreamReader inputStreamReader =
            new InputStreamReader(gzipInputStream, StandardCharsets.UTF_8); ) {
      return inputStreamReader.readAllAsString();
    }
  }

  @ParameterizedTest
  @CsvSource({"success", "error"})
  void shouldReturnDetailedResultsUncompressed(final String queryValue) throws Exception {
    final UUID batchId = UUID.randomUUID();
    final String csv = "processed At,Document Id\n2026-01-01T00:00:00Z,doc-1\n";
    final String expectedFilename = "batch-" + batchId + "-" + queryValue + ".csv";
    final BatchResultApi.ResultQueryEnum resultQuery =
        queryValue.equalsIgnoreCase("success")
            ? BatchResultApi.ResultQueryEnum.SUCCESS
            : BatchResultApi.ResultQueryEnum.ERROR;

    doAnswer(
            invocation -> {
              final OutputStream outputStream = invocation.getArgument(2);
              outputStream.write(csv.getBytes(StandardCharsets.UTF_8));
              return null;
            })
        .when(batchResultService)
        .streamResult(eq(batchId), eq(resultQuery), any(OutputStream.class));

    final HttpResponse<byte[]> response;
    try (HttpClient httpClient = HttpClient.newHttpClient()) {
      HttpRequest httpRequest =
          HttpRequest.newBuilder()
              .uri(
                  URI.create(
                      "http://localhost:" + port + String.format(ENDPOINT, batchId, queryValue)))
              .GET()
              .build();

      response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
    }

    final String contentEncoding = response.headers().firstValue("Content-Encoding").orElse("");
    assertThat(contentEncoding).isEmpty();

    final String contentDisposition =
        response.headers().firstValue("Content-Disposition").orElse("");
    assertThat(contentDisposition).isEqualTo("attachment; filename=\"" + expectedFilename + "\"");

    final String csvData = new String(response.body(), StandardCharsets.UTF_8);
    assertThat(csvData).isNotNull().isEqualTo(csv);
  }
}
