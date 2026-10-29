package de.gematik.demis.ars.service.service.sentinel.terminology;

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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import de.gematik.demis.service.base.error.ServiceCallException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.wiremock.spring.EnableWireMock;

@ActiveProfiles("without-database")
@SpringBootTest(
    properties = {
      "ars.futs.url=http://localhost:${wiremock.server.port}",
      "ars.futs.fhir-package=test-package",
    })
@EnableWireMock
class FutsClientIntegrationTest {

  private static final String SYSTEM =
      "https://demis.rki.de/fhir/ars/ValueSet/interpretationSNOMED";
  private static final String RESPONSE =
      """
        [
          {
            "system": "http://snomed.info/sct",
            "code": "260385009"
          },
          {
            "system": "http://snomed.info/sct",
            "designations": [
                {
                    "language": "de-DE",
                    "value": "translation is irrelevant",
                    "use": {}
                }
            ],
            "code": "123456789",
            "display": "display string is irrelevant"
          }
        ]
        """;

  @Autowired FutsClient underTest;

  @Test
  void getValueSet() {
    stubFor(
        get(urlPathEqualTo("/ValueSet"))
            .withQueryParam("system", equalTo(SYSTEM))
            .withHeader("x-fhir-package", equalTo("test-package"))
            .willReturn(okJson(RESPONSE)));

    final List<CodeKey> expectedResponse =
        List.of(
            new CodeKey("http://snomed.info/sct", "260385009"),
            new CodeKey("http://snomed.info/sct", "123456789"));

    final List<CodeKey> actualResponse = underTest.getValueSet(SYSTEM);

    assertThat(actualResponse).isEqualTo(expectedResponse);
  }

  @Test
  void invalidRequest() {
    stubFor(
        get(urlPathEqualTo("/ValueSet"))
            .withQueryParam("system", equalTo(SYSTEM))
            .willReturn(status(400)));

    final ServiceCallException exception =
        catchThrowableOfType(ServiceCallException.class, () -> underTest.getValueSet(SYSTEM));

    assertThat(exception)
        .isNotNull()
        .returns(400, ServiceCallException::getHttpStatus)
        .returns("FUTS", ServiceCallException::getErrorCode);
  }
}
