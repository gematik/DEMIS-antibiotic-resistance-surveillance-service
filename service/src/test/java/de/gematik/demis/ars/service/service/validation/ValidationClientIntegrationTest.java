package de.gematik.demis.ars.service.service.validation;

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

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.demis.fhirparserlibrary.MessageType;
import feign.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.wiremock.spring.EnableWireMock;

@ActiveProfiles("without-database")
@SpringBootTest(
    properties = {
      "ars.validation.url=http://localhost:${wiremock.server.port}",
      "base.feign.extension.enabled=false"
    })
@EnableWireMock
class ValidationClientIntegrationTest {

  @Autowired ValidationServiceClient underTest;

  @ParameterizedTest
  @EnumSource(MessageType.class)
  void headers(final MessageType type) {
    final String headerName = "x-fhir-package";
    final String headerValue = "my-test";
    stubFor(
        post("/$validate").withHeader(headerName, equalTo(headerValue)).willReturn(okJson("{}")));

    final HttpHeaders headers = new HttpHeaders();
    headers.add(headerName, headerValue);
    final var headerMap = headers.asMultiValueMap();

    final Response response =
        switch (type) {
          case JSON -> underTest.validateJsonBundle(headerMap, "{blah}");
          case XML -> underTest.validateXmlBundle(headerMap, "<xml>blah");
        };

    assertThat(response.status()).isEqualTo(200);
  }
}
