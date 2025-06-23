package de.gematik.demis.ars.service.api;

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

import static de.gematik.demis.ars.service.service.NotificationService.BUNDLE_IDENTIFIER_PARAMETER_NAME;
import static de.gematik.demis.ars.service.service.NotificationService.OPERATION_OUTCOME_PARAMETER_NAME;
import static de.gematik.demis.ars.service.service.NotificationService.SPECIMEN_IDENTIFIER_PARAMETER_NAME;
import static de.gematik.demis.ars.service.utils.TestUtils.TEST_TOKEN;
import static de.gematik.demis.ars.service.utils.TestUtils.VALID_ARS_NOTIFICATION_JSON;
import static de.gematik.demis.ars.service.utils.TestUtils.VALID_ARS_NOTIFICATION_TWO_SPECIMEN_JSON;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.ERROR;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.INFORMATION;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.WARNING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;
import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.demis.ars.service.service.fss.FhirStorageServiceClient;
import de.gematik.demis.ars.service.service.pseudonymisation.PseudonymisationService;
import de.gematik.demis.ars.service.service.validation.ValidationServiceClient;
import de.gematik.demis.ars.service.utils.TestUtils;
import de.gematik.demis.service.base.error.ServiceCallException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "feature.flag.ars_validation_enabled=true")
class NotificationControllerIT {

  public static final String NOTIFICATION_URL = "/fhir/$process-notification";
  private static final TestUtils testUtil = new TestUtils();
  public static final String UUID_REGEX =
      "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private ValidationServiceClient validationClient;
  @MockitoBean private FhirStorageServiceClient fssClient;
  @SpyBean private PseudonymisationService pseudonymisationService;

  private static Stream<Arguments> shouldAcceptXmlAndJsonContentType() {
    return Stream.of(
        Arguments.of(
            APPLICATION_JSON_VALUE,
            (Consumer<ValidationServiceClient>)
                client ->
                    when(client.validateJsonBundle(anyString()))
                        .thenReturn(testUtil.createOutcomeResponse(INFORMATION))),
        Arguments.of(
            APPLICATION_XML_VALUE,
            (Consumer<ValidationServiceClient>)
                client ->
                    when(client.validateXmlBundle(anyString()))
                        .thenReturn(testUtil.createOutcomeResponse(INFORMATION))));
  }

  @ParameterizedTest
  @MethodSource
  void shouldAcceptXmlAndJsonContentType(
      String contentType, Consumer<ValidationServiceClient> setup) throws Exception {
    setup.accept(validationClient);
    mockMvc
        .perform(
            post(NOTIFICATION_URL)
                .header("Authorization", TEST_TOKEN)
                .contentType(contentType)
                .accept(APPLICATION_JSON_VALUE)
                .content(testUtil.getValidArsNotification(contentType)))
        .andDo(print())
        .andExpectAll(
            status().is2xxSuccessful(),
            jsonPath("$.resourceType").value("Parameters"),
            jsonPath("$.parameter[0].name").value(BUNDLE_IDENTIFIER_PARAMETER_NAME),
            jsonPath("$.parameter[0].valueIdentifier.value").value(matchesRegex(UUID_REGEX)),
            jsonPath("$.parameter[1].name").value(SPECIMEN_IDENTIFIER_PARAMETER_NAME),
            jsonPath("$.parameter[1].valueIdentifier.value").value("23-000034"),
            jsonPath("$.parameter[2].name").value(OPERATION_OUTCOME_PARAMETER_NAME));
  }

  @Test
  void shouldReturnTwoSpecimenIdentifier() throws Exception {
    when(validationClient.validateJsonBundle(anyString()))
        .thenReturn(testUtil.createOutcomeResponse(INFORMATION));
    when(fssClient.sendNotification(startsWith("Bearer "), anyString()))
        .thenReturn(ResponseEntity.ok().build());
    mockMvc
        .perform(
            post(NOTIFICATION_URL)
                .header("Authorization", TEST_TOKEN)
                .contentType(APPLICATION_JSON_VALUE)
                .accept(APPLICATION_JSON_VALUE)
                .content(testUtil.readFileToString(VALID_ARS_NOTIFICATION_TWO_SPECIMEN_JSON)))
        .andExpectAll(
            status().is2xxSuccessful(),
            jsonPath("$.resourceType").value("Parameters"),
            jsonPath("$.parameter[0].name").value(BUNDLE_IDENTIFIER_PARAMETER_NAME),
            jsonPath("$.parameter[1].name").value(SPECIMEN_IDENTIFIER_PARAMETER_NAME),
            jsonPath("$.parameter[1].valueIdentifier.value").value("O24-001081"),
            jsonPath("$.parameter[2].name").value(SPECIMEN_IDENTIFIER_PARAMETER_NAME),
            jsonPath("$.parameter[2].valueIdentifier.value").value("24-003257"),
            jsonPath("$.parameter[3].name").value(OPERATION_OUTCOME_PARAMETER_NAME))
        .andDo(print());
  }

  @Test
  void shouldReturn200IfSeverityOnlyWarning() throws Exception {
    when(validationClient.validateJsonBundle(anyString()))
        .thenReturn(testUtil.createOutcomeResponse(WARNING));
    when(fssClient.sendNotification(startsWith("Bearer "), anyString()))
        .thenReturn(ResponseEntity.ok().build());
    mockMvc
        .perform(
            post(NOTIFICATION_URL)
                .header("Authorization", TEST_TOKEN)
                .contentType(APPLICATION_JSON_VALUE)
                .content(testUtil.getValidArsNotification(APPLICATION_JSON_VALUE)))
        .andExpect(status().is2xxSuccessful());
  }

  @Test
  void shouldReturnContentTypeXmlWhenAcceptHeaderSet() throws Exception {
    when(validationClient.validateJsonBundle(anyString()))
        .thenReturn(testUtil.createOutcomeResponse(WARNING));
    when(fssClient.sendNotification(startsWith("Bearer "), anyString()))
        .thenReturn(ResponseEntity.ok().build());
    mockMvc
        .perform(
            post(NOTIFICATION_URL)
                .header("Authorization", TEST_TOKEN)
                .contentType(APPLICATION_JSON_VALUE)
                .accept(APPLICATION_XML_VALUE)
                .content(testUtil.getValidArsNotification(APPLICATION_JSON_VALUE)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(
            result ->
                result
                    .getResponse()
                    .getContentType()
                    .equals(APPLICATION_XML_VALUE + ";charset=UTF-8"));
  }

  @Test
  void shouldReturn422IfSeverityOnError() throws Exception {
    when(validationClient.validateJsonBundle(anyString()))
        .thenReturn(testUtil.createOutcomeResponse(ERROR));
    mockMvc
        .perform(
            post(NOTIFICATION_URL)
                .header("Authorization", TEST_TOKEN)
                .contentType(APPLICATION_JSON_VALUE)
                .content(testUtil.getValidArsNotification(APPLICATION_JSON_VALUE)))
        .andExpectAll(
            status().isUnprocessableEntity(),
            jsonPath("$.resourceType").value("OperationOutcome"),
            jsonPath("$.issue").isArray(),
            jsonPath("$.issue", hasSize(2)),
            jsonPath("$.issue[0].severity").value("error"));
  }

  @ParameterizedTest
  @ValueSource(strings = {APPLICATION_OCTET_STREAM_VALUE, APPLICATION_PDF_VALUE, ""})
  void shouldReturn415IfWrongContentType(String contentType) throws Exception {
    mockMvc
        .perform(
            post(NOTIFICATION_URL)
                .contentType(contentType)
                .content(testUtil.getValidArsNotification(APPLICATION_JSON_VALUE)))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void shouldReturn405IfNoContentTypeProvided() throws Exception {
    mockMvc
        .perform(
            post(NOTIFICATION_URL)
                .content(testUtil.getValidArsNotification(APPLICATION_JSON_VALUE)))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void shouldReturn400IfNoBodyProvided() throws Exception {
    mockMvc
        .perform(post(NOTIFICATION_URL).contentType(APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldCallPseudonymisationServiceMethodIfPostSuccesfully() throws Exception {
    when(validationClient.validateJsonBundle(anyString()))
        .thenReturn(testUtil.createOutcomeResponse(INFORMATION));
    when(fssClient.sendNotification(startsWith("Bearer "), anyString()))
        .thenReturn(ResponseEntity.ok().build());
    mockMvc
        .perform(
            post(NOTIFICATION_URL)
                .header("Authorization", TEST_TOKEN)
                .contentType(APPLICATION_JSON_VALUE)
                .accept(APPLICATION_JSON_VALUE)
                .content(testUtil.readFileToString(VALID_ARS_NOTIFICATION_JSON)))
        .andExpect(status().is2xxSuccessful());
    verify(pseudonymisationService, times(1)).replacePatientIdentifier(any(Bundle.class));
  }

  @Test
  @SneakyThrows
  void shouldReturn500IfFssThrowsException() {
    when(fssClient.sendNotification(startsWith("Bearer "), anyString()))
        .thenThrow(ServiceCallException.class);
    mockMvc
        .perform(
            post(NOTIFICATION_URL)
                .header("Authorization", TEST_TOKEN)
                .contentType(APPLICATION_JSON_VALUE)
                .accept(APPLICATION_JSON_VALUE)
                .content(testUtil.readFileToString(VALID_ARS_NOTIFICATION_JSON)))
        .andExpect(status().isInternalServerError());
  }
}
