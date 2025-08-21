package de.gematik.demis.ars.service.service.pseudonymisation;

/*-
 * #%L
 * Antibiotic-Resistance-Surveillance-Service
 * %%
 * Copyright (C) 2025 gematik GmbH
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
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import de.gematik.demis.service.base.error.ServiceCallException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;

@SpringBootTest(properties = {"ars.pseudo.url=http://localhost:${wiremock.server.port}"})
@AutoConfigureWireMock(port = 0)
class SurveillancePseudonymServiceClientIntegrationTest {

  private static final String EXPECTED_REQUEST_BODY =
      """
        {
          "pseudonym1": "11111111-1111-1111-1111-111111111111",
          "pseudonym2": "22222222-2222-2222-2222-222222222222",
          "date": "2025-07-30"
        }
        """;
  private static final String RESPONSE =
      """
        {
          "system": "http://my.test/Pseudo",
          "value": "urn:uuid:10101010-1010-1010-1010-101010101010"
        }
      """;

  @Autowired SurveillancePseudonymServiceClient underTest;

  @Test
  void createPseudonym() {
    stubFor(
        post("/pseudonym")
            .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
            .withHeader(ACCEPT, equalTo(APPLICATION_JSON_VALUE))
            .withRequestBody(equalToJson(EXPECTED_REQUEST_BODY))
            .willReturn(okJson(RESPONSE)));

    final PseudonymRequest request =
        PseudonymRequest.builder()
            .pseudonym1("11111111-1111-1111-1111-111111111111")
            .pseudonym2("22222222-2222-2222-2222-222222222222")
            .date(LocalDate.of(2025, 7, 30))
            .build();
    final PseudonymResponse expectedResponse =
        new PseudonymResponse(
            "http://my.test/Pseudo", "urn:uuid:10101010-1010-1010-1010-101010101010");

    final PseudonymResponse actualResponse = underTest.createPseudonym(request);

    assertThat(actualResponse).isEqualTo(expectedResponse);
  }

  @Test
  void invalidRequest() {
    stubFor(post("/pseudonym").willReturn(status(400)));

    final var request = new PseudonymRequest("", "", null);
    final ServiceCallException exception =
        catchThrowableOfType(ServiceCallException.class, () -> underTest.createPseudonym(request));

    assertThat(exception)
        .isNotNull()
        .returns(400, ServiceCallException::getHttpStatus)
        .returns("PSEUDO", ServiceCallException::getErrorCode);
  }
}
