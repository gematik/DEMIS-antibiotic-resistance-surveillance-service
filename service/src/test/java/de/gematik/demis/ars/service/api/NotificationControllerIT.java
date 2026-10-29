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

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static de.gematik.demis.ars.service.api.FhirParametersResponseMapper.BUNDLE_IDENTIFIER_PARAMETER_NAME;
import static de.gematik.demis.ars.service.api.FhirParametersResponseMapper.OPERATION_OUTCOME_PARAMETER_NAME;
import static de.gematik.demis.ars.service.api.FhirParametersResponseMapper.SPECIMEN_IDENTIFIER_PARAMETER_NAME;
import static de.gematik.demis.ars.service.api.NotificationControllerIT.NotificationController_DEFAULT.FUTS;
import static de.gematik.demis.ars.service.utils.TestUtils.ARS_NOTIFICATION_DUPLICATE_PATIENT_IDENTIFIER_JSON;
import static de.gematik.demis.ars.service.utils.TestUtils.HEADER_FHIR_PACKAGE;
import static de.gematik.demis.ars.service.utils.TestUtils.HEADER_FHIR_PACKAGE_VERSION;
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
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.jayway.jsonpath.JsonPath;
import de.gematik.demis.ars.service.exception.ArsServiceException;
import de.gematik.demis.ars.service.exception.ErrorCode;
import de.gematik.demis.ars.service.service.NotificationService;
import de.gematik.demis.ars.service.service.fss.FhirStorageServiceClient;
import de.gematik.demis.ars.service.service.pseudonymisation.PseudonymResponse;
import de.gematik.demis.ars.service.service.pseudonymisation.PseudonymsBelongsToDifferentChainsException;
import de.gematik.demis.ars.service.service.pseudonymisation.SurveillancePseudonymServiceClient;
import de.gematik.demis.ars.service.service.sentinel.terminology.ValueSet;
import de.gematik.demis.ars.service.service.validation.ValidationServiceClient;
import de.gematik.demis.ars.service.utils.TestUtils;
import de.gematik.demis.service.base.error.ServiceCallException;
import de.gematik.demis.service.base.error.rest.ErrorFieldProvider;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.minidev.json.JSONArray;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.MultiValueMap;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

class NotificationControllerIT {

  private static final String NOTIFICATION_URL = "$process-notification";
  private static final String UUID_REGEX =
      "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
  private static final TestUtils testUtil = new TestUtils();
  private static final String ERROR_MESSAGE_DUPLICATE_PSEUDONYMS = "Pseudonyms must be different";
  private static final String ERROR_MESSAGE_PSEUDONYMS_BELONGS_TO_DIFFERENT_CHAINS =
      "Pseudonyms must not belong to different patients";
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
  @ActiveProfiles("without-database")
  @SpringBootTest(
      properties = {"feature.flag.move-error-id-to-diagnostics=true", "ars.sentinel.enabled=true"})
  @AutoConfigureMockMvc
  @EnableWireMock(
      @ConfigureWireMock(
          name = FUTS,
          filesUnderClasspath = "wiremock/futs",
          baseUrlProperties = "ars.futs.url"))
  class NotificationController_DEFAULT {

    static final String FUTS = "futs";

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
              status().is(422),
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
      sendValidRequest().andExpect(status().isInternalServerError());
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
              .andExpect(status().is(422))
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
    void shouldReturn422_ifPseudonymsBelongsToDifferentChains() throws Exception {
      when(validationClient.validateJsonBundle(any(), anyString()))
          .thenReturn(testUtil.createOutcomeResponse(INFORMATION));
      when(pseudonymClient.createPseudonym(any()))
          .thenThrow(PseudonymsBelongsToDifferentChainsException.class);
      mockErrorUuid();

      MvcResult mvcResult = sendValidRequest().andExpect(status().is(422)).andReturn();

      final var expectedIssue = new OperationOutcomeIssueComponent();
      expectedIssue
          .setSeverity(OperationOutcome.IssueSeverity.ERROR)
          .setCode(OperationOutcome.IssueType.PROCESSING)
          .setDetails(
              new CodeableConcept().setCoding(List.of(new Coding().setCode("INVALID_PSEUDONYMS"))))
          .setDiagnostics(
              ERROR_MESSAGE_PSEUDONYMS_BELONGS_TO_DIFFERENT_CHAINS + " (" + ERROR_ID + ")");

      assertResponse(
          mvcResult.getResponse().getContentAsString(), APPLICATION_JSON_VALUE, expectedIssue);
    }

    @Test
    @SneakyThrows
    void shouldReturn500AndSendNothingToFss_ifPseudoServiceThrowsException() {
      when(validationClient.validateJsonBundle(any(), anyString()))
          .thenReturn(testUtil.createOutcomeResponse(INFORMATION));
      when(pseudonymClient.createPseudonym(any()))
          .thenThrow(new ServiceCallException("just for test", "Pseudo", 500, null));

      sendValidRequest().andExpect(status().is(502));

      verifyNoInteractions(fssClient);
    }

    private ResultActions sendValidRequest() throws Exception {
      return mockMvc.perform(
          post(contextPath + NOTIFICATION_URL)
              .header("Authorization", TEST_TOKEN)
              .contentType(APPLICATION_JSON_VALUE)
              .accept(APPLICATION_JSON_VALUE)
              .content(testUtil.readFileToString(VALID_ARS_NOTIFICATION_JSON)));
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

    @Test
    @SneakyThrows
    void shouldSetHeaderCorrectlyForVsWithFeatureFlagNewRoutsTrue() {
      String apiVersion = "apiVersion";
      String profile = "profile";
      mockMvc.perform(
          post(contextPath + NOTIFICATION_URL)
              .header("Authorization", TEST_TOKEN)
              .header("x-fhir-package-version", apiVersion)
              .header("x-fhir-package", profile)
              .contentType(APPLICATION_JSON_VALUE)
              .accept(APPLICATION_JSON_VALUE)
              .content(testUtil.readFileToString(VALID_ARS_NOTIFICATION_TWO_SPECIMEN_JSON)));

      final ArgumentCaptor<MultiValueMap<String, String>> headerCaptor = ArgumentCaptor.captor();
      verify(validationClient)
          .validateJsonBundle(
              headerCaptor.capture(),
              eq(testUtil.readFileToString(VALID_ARS_NOTIFICATION_TWO_SPECIMEN_JSON)));
      assertThat(headerCaptor.getValue())
          .isNotNull()
          .hasSize(2)
          .containsKey(HEADER_FHIR_PACKAGE)
          .extractingByKey(HEADER_FHIR_PACKAGE)
          .isEqualTo(List.of(profile));
      assertThat(headerCaptor.getValue())
          .extractingByKey(HEADER_FHIR_PACKAGE_VERSION)
          .isEqualTo(List.of(apiVersion));
    }

    @Nested
    class SentinelTest {
      // Observation.code in ARS_NOTIFICATION_SENTINEL_JSON (not part of any sentinel value set)
      private static final String NO_SPECIAL_SENTINEL_TYPE_CODE = "41852-5";
      // codes must match with src/test/resources/wiremock/futs/mappings/sentinel-value-sets.json
      private static final String MEASLES_CODE = "MAS-CODE";
      private static final String CHLAMYDIA_CODE = "CHY-CODE";

      @InjectWireMock(FUTS)
      private WireMockServer futsServer;

      @Autowired private LoadingCache<String, ValueSet> sentinelValueSetCache;

      @Test
      void cacheShouldBePrefilledAndUsed() throws Exception {
        // assert cache warm up
        assertThat(sentinelValueSetCache.asMap()).isNotEmpty();

        sendSentinelNotificationAndCaptureFssBundle(NO_SPECIAL_SENTINEL_TYPE_CODE);

        // During request processing FUTS must not be called. The cache is used.
        futsServer.verify(0, anyRequestedFor(anyUrl()));
      }

      @Test
      void shouldRemoveAllSentinelDataBeforeSendingToFssIfNoSentinelTypeMatches() throws Exception {
        final String capturedBundle =
            sendSentinelNotificationAndCaptureFssBundle(NO_SPECIAL_SENTINEL_TYPE_CODE);

        assertRemoved(capturedBundle, SentinelPath.COVERAGE);
        assertRemoved(capturedBundle, SentinelPath.PATIENT_ADDRESS);
        assertRemoved(capturedBundle, SentinelPath.SERVICE_REQUEST_INSURANCE);
        assertRemoved(capturedBundle, SentinelPath.SERVICE_REQUEST_REASON_CODE);
      }

      @Test
      void shouldKeepOnlyReasonCodeBeforeSendingToFssIfMeaslesMatches() throws Exception {
        final String capturedBundle = sendSentinelNotificationAndCaptureFssBundle(MEASLES_CODE);

        assertRemoved(capturedBundle, SentinelPath.COVERAGE);
        assertRemoved(capturedBundle, SentinelPath.PATIENT_ADDRESS);
        assertRemoved(capturedBundle, SentinelPath.SERVICE_REQUEST_INSURANCE);
        assertPresent(capturedBundle, SentinelPath.SERVICE_REQUEST_REASON_CODE);
      }

      @Test
      void shouldKeepAllSentinelDataBeforeSendingToFssIfChlamydiaMatches() throws Exception {
        final String capturedBundle = sendSentinelNotificationAndCaptureFssBundle(CHLAMYDIA_CODE);

        assertPresent(capturedBundle, SentinelPath.COVERAGE);
        assertPresent(capturedBundle, SentinelPath.PATIENT_ADDRESS);
        assertPresent(capturedBundle, SentinelPath.SERVICE_REQUEST_INSURANCE);
        assertPresent(capturedBundle, SentinelPath.SERVICE_REQUEST_REASON_CODE);
      }

      /** Replaces Observation.code of the first Observation in the bundle. */
      private String replaceFirstObservationCode(final String bundleAsJson, final String code) {
        final Bundle bundle = testUtil.jsonToResource(bundleAsJson, Bundle.class);
        bundle.getEntry().stream()
            .map(Bundle.BundleEntryComponent::getResource)
            .filter(Observation.class::isInstance)
            .map(Observation.class::cast)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No Observation in sentinel notification"))
            .getCode()
            .getCodingFirstRep()
            .setCode(code);
        return testUtil.resourceToJson(bundle);
      }

      private String sendSentinelNotificationAndCaptureFssBundle(final String observationCode)
          throws Exception {
        final String sentinelNotification =
            replaceFirstObservationCode(
                testUtil.readFileToString(TestUtils.ARS_NOTIFICATION_SENTINEL_JSON),
                observationCode);

        when(validationClient.validateJsonBundle(any(), anyString()))
            .thenReturn(testUtil.createOutcomeResponse(INFORMATION));
        mockPseudoServiceOkay();

        final ArgumentCaptor<String> bundleCaptor = ArgumentCaptor.forClass(String.class);
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

        return bundleCaptor.getValue();
      }

      private void assertRemoved(final String bundle, final SentinelPath path) {
        assertThat(read(bundle, path)).as(path + " is removed").isEmpty();
      }

      private void assertPresent(final String bundle, final SentinelPath path) {
        assertThat(read(bundle, path)).as(path + " is kept").isNotEmpty();
      }

      private JSONArray read(final String bundle, final SentinelPath path) {
        return JsonPath.parse(bundle).read(path.getJsonPath());
      }

      /** JsonPaths into the bundle for the data affected by the sentinel cleanup. */
      @RequiredArgsConstructor
      @Getter
      private enum SentinelPath {
        COVERAGE("$.entry[0].resource.entry[?(@.resource.resourceType == 'Coverage')]"),
        PATIENT_ADDRESS(
            "$.entry[0].resource.entry[?(@.resource.resourceType == 'Patient')].resource.address"),
        SERVICE_REQUEST_INSURANCE(
            "$.entry[0].resource.entry[?(@.resource.resourceType == 'ServiceRequest')].resource.insurance"),
        SERVICE_REQUEST_REASON_CODE(
            "$.entry[0].resource.entry[?(@.resource.resourceType == 'ServiceRequest')].resource.reasonCode");

        private final String jsonPath;
      }
    }
  }

  @Nested
  @ActiveProfiles("without-database")
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
      when(notificationService.process(anyString(), any(), anyString(), any()))
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
              .andExpect(status().is(422))
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
