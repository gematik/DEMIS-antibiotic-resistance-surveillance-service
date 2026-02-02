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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.demis.ars.service.service.NotificationService;
import de.gematik.demis.service.base.error.rest.ErrorHandlerConfiguration;
import de.gematik.demis.service.base.fhir.FhirSupportAutoConfiguration;
import de.gematik.demis.service.base.fhir.error.FhirErrorResponseAutoConfiguration;
import java.nio.charset.StandardCharsets;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(NotificationController.class)
@ImportAutoConfiguration({
  ErrorHandlerConfiguration.class,
  FhirSupportAutoConfiguration.class,
  FhirErrorResponseAutoConfiguration.class
})
class NotificationControllerTest {

  @MockitoBean private NotificationService notificationService;

  @Autowired private MockMvc mockMvc;

  @Value("${ars.context-path}$process-notification")
  private String endpointPath;

  @CsvSource({
    // permute accept
    "application/json, application/json, application/fhir+json",
    "application/json, application/json+fhir, application/fhir+json",
    "application/json, application/fhir+json, application/fhir+json",
    "application/json, application/xml, application/fhir+xml",
    "application/json, application/xml+fhir, application/fhir+xml",
    "application/json, application/fhir+xml, application/fhir+xml",
    // wildcard accept, permute content type
    "application/json, */*, application/fhir+json",
    "application/json;charset=UTF-8, */*, application/fhir+json",
    "application/json+fhir, */*, application/fhir+json",
    "application/fhir+json, */*, application/fhir+json",
    "application/xml, */*, application/fhir+xml",
    "application/xml;charset=UTF-8, */*, application/fhir+xml",
    "application/xml+fhir, */*, application/fhir+xml",
    "application/fhir+xml, */*, application/fhir+xml",
    "application/xml, , application/fhir+xml",
  })
  @ParameterizedTest
  void acceptedContentTypes(
      final String contentType, final String accept, final String expectedContentType)
      throws Exception {
    final String expectedBodyAsJson = "{\"resourceType\":\"Parameters\"}";
    final String expectedBodyAsXml = "<Parameters xmlns=\"http://hl7.org/fhir\"></Parameters>";
    when(notificationService.process(any(), any(), any())).thenReturn(new Parameters());
    final MockHttpServletRequestBuilder request =
        post(endpointPath)
            .contentType(contentType)
            .content("is-ignored")
            .header(HttpHeaders.AUTHORIZATION, "my-token");
    if (accept != null) {
      request.accept(accept);
    }
    mockMvc
        .perform(request)
        .andExpectAll(
            status().isOk(),
            content().contentTypeCompatibleWith(expectedContentType),
            content().encoding(StandardCharsets.UTF_8),
            content()
                .string(
                    expectedContentType.contains("xml") ? expectedBodyAsXml : expectedBodyAsJson));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"text/json", "text/json+fhir", "text/xml", "text/xml+fhir", "unknown/xml", ""})
  void unsupportedMediaType(final String contentType) throws Exception {
    verifyNoInteractions(notificationService);
    final MockHttpServletRequestBuilder request =
        post(endpointPath)
            .content("is-ignored")
            .accept("*/*")
            .header(HttpHeaders.AUTHORIZATION, "my-token");
    if (contentType != null) {
      request.contentType(contentType);
    }

    mockMvc.perform(request).andExpect(status().isUnsupportedMediaType());
  }
}
