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

import static de.gematik.demis.ars.service.service.NotificationService.BUNDLE_IDENTIFIER_PARAMETER_NAME;
import static de.gematik.demis.ars.service.service.NotificationService.OPERATION_OUTCOME_PARAMETER_NAME;
import static de.gematik.demis.ars.service.service.NotificationService.SPECIMEN_IDENTIFIER_PARAMETER_NAME;
import static de.gematik.demis.ars.service.service.validation.ValidationServiceClient.HEADER_FHIR_API_VERSION;
import static de.gematik.demis.ars.service.service.validation.ValidationServiceClient.HEADER_FHIR_PROFILE;
import static de.gematik.demis.ars.service.utils.TestUtils.ARS_NOTIFICATION_DUPLICATE_PATIENT_IDENTIFIER_JSON;
import static de.gematik.demis.ars.service.utils.TestUtils.TEST_TOKEN;
import static de.gematik.demis.ars.service.utils.TestUtils.VALID_ARS_NOTIFICATION_JSON;
import static de.gematik.demis.ars.service.utils.TestUtils.VALID_ARS_NOTIFICATION_TWO_SPECIMEN_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.ERROR;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.INFORMATION;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.WARNING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;
import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import de.gematik.demis.ars.service.exception.ArsServiceException;
import de.gematik.demis.ars.service.exception.ErrorCode;
import de.gematik.demis.ars.service.service.NotificationService;
import de.gematik.demis.ars.service.service.fss.FhirStorageServiceClient;
import de.gematik.demis.ars.service.service.pseudonymisation.PseudonymResponse;
import de.gematik.demis.ars.service.service.pseudonymisation.PseudonymisationService;
import de.gematik.demis.ars.service.service.pseudonymisation.SurveillancePseudonymServiceClient;
import de.gematik.demis.ars.service.service.validation.ValidationServiceClient;
import de.gematik.demis.ars.service.utils.TestUtils;
import de.gematik.demis.service.base.error.ServiceCallException;
import de.gematik.demis.service.base.error.rest.ErrorFieldProvider;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import net.minidev.json.JSONArray;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class NotificationControllerIT {

  private static final String NOTIFICATION_URL = "$process-notification";
  private static final String UUID_REGEX =
      "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
  private static final TestUtils testUtil = new TestUtils();
  private static final String ERROR_MESSAGE_DUPLICATE_PSEUDONYMS = "Pseudonyms must be different";
  private static final String ERROR_ID = "fab6005e-5686-4b7b-b6ee-98b0e98a9d42";

  private IParser determineParser(final String acceptType) {
    FhirContext ctx = FhirContext.forR4();
    if (acceptType.equals(APPLICATION_XML_VALUE)) {
      return ctx.newXmlParser();
    } else {
      return ctx.newJsonParser();
    }
  }

  private void assertResponse(
      final String responseBody,
      final String acceptType,
      final OperationOutcomeIssueComponent expectedIssue) {

    IParser parser = determineParser(acceptType);
    OperationOutcome actualOperationOutcome = (OperationOutcome) parser.parseResource(responseBody);
    OperationOutcomeIssueComponent actualIssue = actualOperationOutcome.getIssueFirstRep();
    assertThat(actualIssue)
        .usingRecursiveComparison()
        //          .ignoringFields("location")
        .isEqualTo(expectedIssue);
    assertThat(actualOperationOutcome.getMeta().getProfile())
        .singleElement()
        .extracting(CanonicalType::asStringValue)
        .isEqualTo("https://demis.rki.de/fhir/StructureDefinition/ProcessNotificationResponse");
  }

  @Nested
  @SpringBootTest(
      properties = {
        "feature.flag.ars_validation_enabled=true",
        "feature.flag.new_api_endpoints=false",
        "feature.flag.surveillance_pseudonym_service_enabled=true",
        "feature.flag.move-error-id-to-diagnostics=true",
      })
  @AutoConfigureMockMvc
  class NotificationController_DEFAULT {

    @Value("${ars.context-path}")
    private String contextPath;

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ValidationServiceClient validationClient;
    @MockitoBean private FhirStorageServiceClient fssClient;
    @MockitoBean private SurveillancePseudonymServiceClient pseudonymClient;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS)
    private ErrorFieldProvider errorFieldProvider;

    private static Stream<Arguments> shouldAcceptXmlAndJsonContentType() {
      return Stream.of(
          Arguments.of(
              APPLICATION_JSON_VALUE,
              (Consumer<ValidationServiceClient>)
                  client ->
                      when(client.validateJsonBundle(any(), anyString()))
                          .thenReturn(testUtil.createOutcomeResponse(INFORMATION))),
          Arguments.of(
              APPLICATION_XML_VALUE,
              (Consumer<ValidationServiceClient>)
                  client ->
                      when(client.validateXmlBundle(any(), anyString()))
                          .thenReturn(testUtil.createOutcomeResponse(INFORMATION))));
    }

    @ParameterizedTest
    @MethodSource
    void shouldAcceptXmlAndJsonContentType(
        String contentType, Consumer<ValidationServiceClient> setup) throws Exception {
      setup.accept(validationClient);
      mockPseudoServiceOkay();
      mockMvc
          .perform(
              post(contextPath + NOTIFICATION_URL)
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
    void shouldReturnContentTypeFromRequestInResponseWhenAcceptAll() throws Exception {
      when(validationClient.validateJsonBundle(any(), anyString()))
          .thenReturn(testUtil.createOutcomeResponse(INFORMATION));
      mockPseudoServiceOkay();
      when(fssClient.sendNotification(anyString())).thenReturn(ResponseEntity.ok().build());
      mockMvc
          .perform(
              post(contextPath + NOTIFICATION_URL)
                  .header("Authorization", TEST_TOKEN)
                  .contentType(APPLICATION_JSON_VALUE)
                  .accept(ALL_VALUE)
                  .content(testUtil.getValidArsNotification(APPLICATION_JSON_VALUE)))
          .andExpect(status().is2xxSuccessful())
          .andExpect(content().contentTypeCompatibleWith("application/fhir+json"));
    }

    @Test
    void shouldReturnTwoSpecimenIdentifier() throws Exception {
      when(validationClient.validateJsonBundle(any(), anyString()))
          .thenReturn(testUtil.createOutcomeResponse(INFORMATION));
      mockPseudoServiceOkay();
      when(fssClient.sendNotification(anyString())).thenReturn(ResponseEntity.ok().build());
      mockMvc
          .perform(
              post(contextPath + NOTIFICATION_URL)
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
      when(validationClient.validateJsonBundle(any(), anyString()))
          .thenReturn(testUtil.createOutcomeResponse(WARNING));
      mockPseudoServiceOkay();
      when(fssClient.sendNotification(anyString())).thenReturn(ResponseEntity.ok().build());
      mockMvc
          .perform(
              post(contextPath + NOTIFICATION_URL)
                  .header("Authorization", TEST_TOKEN)
                  .contentType(APPLICATION_JSON_VALUE)
                  .content(testUtil.getValidArsNotification(APPLICATION_JSON_VALUE)))
          .andExpect(status().is2xxSuccessful());
    }

    @Test
    void shouldReturnContentTypeXmlWhenAcceptHeaderSet() throws Exception {
      when(validationClient.validateJsonBundle(any(), anyString()))
          .thenReturn(testUtil.createOutcomeResponse(WARNING));
      mockPseudoServiceOkay();
      when(fssClient.sendNotification(anyString())).thenReturn(ResponseEntity.ok().build());
      mockMvc
          .perform(
              post(contextPath + NOTIFICATION_URL)
                  .header("Authorization", TEST_TOKEN)
                  .contentType(APPLICATION_JSON_VALUE)
                  .accept(APPLICATION_XML_VALUE)
                  .content(testUtil.getValidArsNotification(APPLICATION_JSON_VALUE)))
          .andExpect(status().is2xxSuccessful())
          .andExpect(content().contentTypeCompatibleWith("application/fhir+xml"));
    }

    @Test
    void shouldReturn422IfSeverityOnError() throws Exception {
      when(validationClient.validateJsonBundle(any(), anyString()))
          .thenReturn(testUtil.createOutcomeResponse(ERROR));
      mockMvc
          .perform(
              post(contextPath + NOTIFICATION_URL)
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
              post(contextPath + NOTIFICATION_URL)
                  .contentType(contentType)
                  .content(testUtil.getValidArsNotification(APPLICATION_JSON_VALUE)))
          .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void shouldReturn405IfNoContentTypeProvided() throws Exception {
      mockMvc
          .perform(
              post(contextPath + NOTIFICATION_URL)
                  .content(testUtil.getValidArsNotification(APPLICATION_JSON_VALUE)))
          .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void shouldReturn400IfNoBodyProvided() throws Exception {
      mockMvc
          .perform(post(contextPath + NOTIFICATION_URL).contentType(APPLICATION_JSON_VALUE))
          .andExpect(status().isBadRequest());
    }

    @Test
    @SneakyThrows
    void shouldReturn500IfFssThrowsException() {
      when(fssClient.sendNotification(anyString())).thenThrow(ServiceCallException.class);
      mockMvc
          .perform(
              post(contextPath + NOTIFICATION_URL)
                  .header("Authorization", TEST_TOKEN)
                  .contentType(APPLICATION_JSON_VALUE)
                  .accept(APPLICATION_JSON_VALUE)
                  .content(testUtil.readFileToString(VALID_ARS_NOTIFICATION_JSON)))
          .andExpect(status().isInternalServerError());
    }

    @ParameterizedTest
    @ValueSource(strings = {APPLICATION_JSON_VALUE, APPLICATION_XML_VALUE})
    @SneakyThrows
    void shouldReturn422OnIdenticalPseudonyms(String acceptType) {
      when(validationClient.validateJsonBundle(any(), anyString()))
          .thenReturn(testUtil.createOutcomeResponse(INFORMATION));
      mockErrorUuid();

      MvcResult mvcResult =
          mockMvc
              .perform(
                  post(contextPath + NOTIFICATION_URL)
                      .header("Authorization", TEST_TOKEN)
                      .contentType(APPLICATION_JSON_VALUE)
                      .accept(acceptType)
                      .content(
                          testUtil.readFileToString(
                              ARS_NOTIFICATION_DUPLICATE_PATIENT_IDENTIFIER_JSON)))
              .andExpect(status().isUnprocessableEntity())
              .andReturn();

      final var expectedIssue = new OperationOutcomeIssueComponent();
      expectedIssue
          .setSeverity(OperationOutcome.IssueSeverity.ERROR)
          .setCode(OperationOutcome.IssueType.PROCESSING)
          .setDetails(
              new CodeableConcept().setCoding(List.of(new Coding().setCode("INVALID_PSEUDONYMS"))))
          .setDiagnostics(ERROR_MESSAGE_DUPLICATE_PSEUDONYMS + " (" + ERROR_ID + ")");

      assertResponse(mvcResult.getResponse().getContentAsString(), acceptType, expectedIssue);
    }

    @Test
    void shouldRemoveSentinelDataBeforeSendingToFss() throws Exception {
      ArgumentCaptor<String> bundleCaptor = ArgumentCaptor.forClass(String.class);
      String sentinelNotification =
          testUtil.readFileToString(TestUtils.ARS_NOTIFICATION_SENTINEL_JSON);

      when(validationClient.validateJsonBundle(any(), anyString()))
          .thenReturn(testUtil.createOutcomeResponse(INFORMATION));
      mockPseudoServiceOkay();
      when(fssClient.sendNotification(bundleCaptor.capture()))
          .thenReturn(ResponseEntity.ok().build());

      mockMvc
          .perform(
              post(contextPath + NOTIFICATION_URL)
                  .header("Authorization", TEST_TOKEN)
                  .contentType(APPLICATION_JSON_VALUE)
                  .accept(APPLICATION_JSON_VALUE)
                  .content(sentinelNotification))
          .andExpect(status().isOk());

      String capturedBundle = bundleCaptor.getValue();
      assertSentinelDataIsRemoved(capturedBundle);
    }

    private void assertSentinelDataIsRemoved(String capturedBundle) {
      DocumentContext jsonContext = JsonPath.parse(capturedBundle);

      assertThat(
              (JSONArray)
                  jsonContext.read(
                      "$.entry[0].resource.entry[?(@.resource.resourceType == 'Coverage')]"))
          .as("Coverage resources is removed")
          .isEmpty();

      assertThat(
              (JSONArray)
                  jsonContext.read(
                      "$.entry[0].resource.entry[?(@.resource.resourceType == 'Patient')].resource.address"))
          .as("Patient address is removed")
          .isEmpty();

      assertThat(
              (JSONArray)
                  jsonContext.read(
                      "$.entry[0].resource.entry[?(@.resource.resourceType == 'ServiceRequest')].resource.insurance"))
          .as("ServiceRequest insurance is removed")
          .isEmpty();

      assertThat(
              (JSONArray)
                  jsonContext.read(
                      "$.entry[0].resource.entry[?(@.resource.resourceType == 'ServiceRequest')].resource.reasonCode"))
          .as("ServiceRequest reasonCodes are removed")
          .isEmpty();
    }

    @Test
    void shouldReturn500OnInternalServerErrorInPseudonymService() throws Exception {
      when(validationClient.validateJsonBundle(any(), anyString()))
          .thenReturn(testUtil.createOutcomeResponse(INFORMATION));
      ServiceCallException serviceCallException =
          new ServiceCallException(
              "pseudo-service-has-an-internal-server-error", "PSEUDO", 500, null);
      doThrow(serviceCallException).when(pseudonymClient).createPseudonym(any());
      mockErrorUuid();

      MvcResult mvcResult =
          mockMvc
              .perform(
                  post(contextPath + NOTIFICATION_URL)
                      .header("Authorization", TEST_TOKEN)
                      .contentType(APPLICATION_JSON_VALUE)
                      .accept(APPLICATION_JSON_VALUE)
                      .content(testUtil.getValidArsNotification(APPLICATION_JSON_VALUE)))
              .andExpect(status().isBadGateway())
              .andReturn();

      final var expectedIssue = new OperationOutcomeIssueComponent();
      expectedIssue
          .setSeverity(OperationOutcome.IssueSeverity.ERROR)
          .setCode(OperationOutcome.IssueType.EXCEPTION)
          .setDetails(new CodeableConcept().setCoding(List.of(new Coding().setCode("PSEUDO"))))
          .setDiagnostics("null (" + ERROR_ID + ")");

      assertResponse(
          mvcResult.getResponse().getContentAsString(), APPLICATION_JSON_VALUE, expectedIssue);
    }

    private void mockPseudoServiceOkay() {
      final var response =
          new PseudonymResponse(
              "http://my.test/Pseudo", "urn:uuid:10101010-1010-1010-1010-101010101010");
      when(pseudonymClient.createPseudonym(any())).thenReturn(response);
    }

    private void mockErrorUuid() {
      when(errorFieldProvider.generateId()).thenReturn(ERROR_ID);
    }
  }

  @Nested
  @SpringBootTest(
      properties = {
        "feature.flag.ars_validation_enabled=true",
        "feature.flag.new_api_endpoints=true",
      })
  @AutoConfigureMockMvc
  class NotificationController_FEATURE_FLAG_NEW_API_ENDPOINTS {

    @Captor ArgumentCaptor<HttpHeaders> headerCaptor;

    @Value("${ars.context-path}")
    private String contextPath;

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ValidationServiceClient validationClient;
    @MockitoBean private FhirStorageServiceClient fssClient;
    @MockitoSpyBean private PseudonymisationService pseudonymisationService;

    @Test
    @SneakyThrows
    void shouldSetHeaderCorrectlyForVsWithFeatureFlagNewRoutsTrue() {
      String apiVersion = "apiVersion";
      String profile = "profile";
      mockMvc.perform(
          post(contextPath + NOTIFICATION_URL)
              .header("Authorization", TEST_TOKEN)
              .header(HEADER_FHIR_API_VERSION, apiVersion)
              .header(HEADER_FHIR_PROFILE, profile)
              .contentType(APPLICATION_JSON_VALUE)
              .accept(APPLICATION_JSON_VALUE)
              .content(testUtil.readFileToString(VALID_ARS_NOTIFICATION_TWO_SPECIMEN_JSON)));
      verify(validationClient)
          .validateJsonBundle(
              headerCaptor.capture(),
              eq(testUtil.readFileToString(VALID_ARS_NOTIFICATION_TWO_SPECIMEN_JSON)));
      assertThat(headerCaptor.getValue())
          .isNotNull()
          .hasSize(3)
          .containsKey(HEADER_FHIR_PROFILE)
          .extractingByKey(HEADER_FHIR_PROFILE)
          .isEqualTo(List.of(profile));
      assertThat(headerCaptor.getValue())
          .extractingByKey(HEADER_FHIR_API_VERSION)
          .isEqualTo(List.of(apiVersion));
    }
  }

  @Nested
  @SpringBootTest(
      properties = {
        "feature.flag.move-error-id-to-diagnostics=false",
      })
  @AutoConfigureMockMvc
  class NotificationController_FEATURE_FLAG_MOVE_ERROR_ID_DISABLED {

    @Value("${ars.context-path}")
    private String contextPath;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private NotificationService notificationService;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS)
    private ErrorFieldProvider errorFieldProvider;

    @Test
    @SneakyThrows
    void shouldSetHeaderCorrectlyForVsWithFeatureFlagNewRoutsTrue() {
      final String errorMessage = "Just a test";
      when(notificationService.process(anyString(), any(), anyString()))
          .thenThrow(new ArsServiceException(ErrorCode.FHIR_VALIDATION_ERROR, errorMessage));
      mockErrorUuid();

      MvcResult mvcResult =
          mockMvc
              .perform(
                  post(contextPath + NOTIFICATION_URL)
                      .header("Authorization", TEST_TOKEN)
                      .contentType(APPLICATION_JSON_VALUE)
                      .accept(APPLICATION_JSON_VALUE)
                      .content(
                          testUtil.readFileToString(
                              ARS_NOTIFICATION_DUPLICATE_PATIENT_IDENTIFIER_JSON)))
              .andExpect(status().isUnprocessableEntity())
              .andReturn();

      final var expectedIssue = new OperationOutcomeIssueComponent();
      expectedIssue
          .setSeverity(OperationOutcome.IssueSeverity.ERROR)
          .setCode(OperationOutcome.IssueType.PROCESSING)
          .setDetails(
              new CodeableConcept()
                  .setCoding(
                      List.of(new Coding().setCode(ErrorCode.FHIR_VALIDATION_ERROR.getCode()))))
          .setDiagnostics(errorMessage)
          .addLocation(ERROR_ID);

      assertResponse(
          mvcResult.getResponse().getContentAsString(), APPLICATION_JSON_VALUE, expectedIssue);
    }

    private void mockErrorUuid() {
      when(errorFieldProvider.generateId()).thenReturn(ERROR_ID);
    }
  }
}
