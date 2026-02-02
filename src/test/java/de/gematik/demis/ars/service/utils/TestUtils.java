package de.gematik.demis.ars.service.utils;

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

import static java.nio.file.Files.readString;
import static java.util.Objects.requireNonNull;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.ERROR;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.FATAL;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.INFORMATION;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.WARNING;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.IntStream;
import lombok.Getter;
import lombok.SneakyThrows;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.springframework.http.MediaType;

public class TestUtils {

  public static final String TEST_TOKEN =
      "Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJsS2VZeHdrbTBFdEN5eTJLUWhPZ0lvelZRYWlHX1BRZGZobFZOeW5yTTVFIn0.eyJleHAiOjE3NDQ3MjcwNDksImlhdCI6MTc0NDcyNjQ0OSwianRpIjoiNmU5MTVkYjItNmU4MC00YjkzLWI5M2ItZDY0MWM4NWZkMWJiIiwiaXNzIjoiaHR0cHM6Ly9hdXRoLmRldi5ma2IuZGVtaXMucmtpLmRlL3JlYWxtcy9MQUIiLCJhdWQiOlsibm90aWZpY2F0aW9uLWFwaSIsIm5vdGlmaWNhdGlvbi1lbnRyeS1zZXJ2aWNlIiwiYWNjb3VudCJdLCJzdWIiOiJjZTRkN2ExYy0zNGUxLTQ1MjQtYjY2OC1kNzg1NDg1ODI0YTQiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJkZW1pcy1hZGFwdGVyIiwic2lkIjoiMjQ5YzhkMTItYTE5ZC00NDkzLWI2MmItOGQzY2FmMmJhNDY2IiwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbImRpc2Vhc2Utbm90aWZpY2F0aW9uLXNlbmRlciIsInBhdGhvZ2VuLW5vdGlmaWNhdGlvbi1zZW5kZXIiLCJhcnMtZGF0YS1zZW5kZXIiXX0sInJlc291cmNlX2FjY2VzcyI6eyJub3RpZmljYXRpb24tYXBpIjp7InJvbGVzIjpbImxhYi1ub3RpZmljYXRpb24tc2VuZGVyIl19LCJub3RpZmljYXRpb24tZW50cnktc2VydmljZSI6eyJyb2xlcyI6WyJsYWItbm90aWZpY2F0aW9uLXNlbmRlciJdfSwiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIiwidmlldy1wcm9maWxlIl19fSwic2NvcGUiOiJwcm9maWxlIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiMzMzMzMifQ.ggEcInrgm2LuX34Ptl3u_IbDoKZ1pdKGixHEez0fZ9CbFbVpvBfZ5pdEkdrjdu_o8R4EYA6UlKGoDcVdt1u-hz6oR86viJlr0jtNRn5la_kg_dPZBhBYHYLtTjJOlSlJGEoWWFqHosjbeSSXDsMAutKksDGeI51QP5ep7xELoNp-yHGWBFovHL-jKnrCB5r5OflDMYGuIrFXuO0g5ZP81rup8DFFYQ7BJgi5cn73G_OZd11_-kcnwVgepazoFxfniDFlBCy_ZJ3fGp1gCmljG-5SKtz5FJbetSPBR-CZ3fc8o0YWkJ3UNmoJNcMjP26fec2pMd84_9Tr_1rvYXyN4w";

  public static final String VALID_ARS_NOTIFICATION_JSON = "testdata/valid-ars-notification.json";
  public static final String VALID_ARS_NOTIFICATION_TWO_SPECIMEN_JSON =
      "testdata/valid-ars-notification-two-specimen.json";
  public static final String VALID_ARS_NOTIFICATION_XML = "testdata/valid-ars-notification.xml";
  public static final String NO_COMPOSITION_ARS_NOTIFICATION_JSON =
      "testdata/ars-notification-no-composition.json";
  public static final String ARS_NOTIFICATION_NO_PATIENT_IDENTIFIER_JSON =
      "testdata/ars-notification-no-patient-identifier.json";
  public static final String ARS_NOTIFICATION_DUPLICATE_PATIENT_IDENTIFIER_JSON =
      "testdata/ars-notification-duplicate-patient-identifiers.json";
  public static final String ARS_NOTIFICATION_INVALID_PATIENT_IDENTIFIER_JSON =
      "testdata/ars-notification-no-patient-identifier.json";
  public static final String ARS_NOTIFICATION_THREE_PATIENT_IDENTIFIER =
      "testdata/ars-notification-three-patient-identifier.json";
  public static final String ARS_NOTIFICATION_SENTINEL_JSON =
      "testdata/ars-notification-sentinel.json";
  public static final String ARS_NOTIFICATION_SENTINEL_PROCESSED_JSON =
      "testdata/ars-notification-sentinel-processed.json";
  private static final FhirContext fhirContext = FhirContext.forR4Cached();
  public final String VALID_ARS_NOTIFICATION_JSON_STRING =
      readFileToString(VALID_ARS_NOTIFICATION_JSON);
  public final String VALID_ARS_NOTIFICATION_XML_STRING =
      readFileToString(VALID_ARS_NOTIFICATION_XML);
  public static final String PROVENANCE_RESOURCE = "provenance/provenanceResource.json";
  @Getter private final Bundle defaultBundle;

  @SneakyThrows
  public TestUtils() {
    defaultBundle =
        fhirContext.newJsonParser().parseResource(Bundle.class, VALID_ARS_NOTIFICATION_JSON_STRING);
  }

  private static OperationOutcome generateOutcome() {
    OperationOutcome operationOutcome = new OperationOutcome();

    operationOutcome.setMeta(
        new Meta()
            .addProfile(
                "https://demis.rki.de/fhir/StructureDefinition/ProcessNotificationResponse"));

    Narrative text = new Narrative();
    text.setStatus(Narrative.NarrativeStatus.GENERATED);
    XhtmlNode value = new XhtmlNode();
    value.setValue("http://www.w3.org/1999/xhtml");
    text.setDiv(value);
    operationOutcome.setText(text);
    return operationOutcome;
  }

  @SneakyThrows
  public String readFileToString(String relativePath) {
    Path pathToDocumentReferenceRequest =
        Path.of(requireNonNull(getClass().getClassLoader().getResource(relativePath)).toURI());
    return readString(pathToDocumentReferenceRequest);
  }

  @SneakyThrows
  public Bundle readFileToBundle(String relativePath) {
    IParser parser;
    if (relativePath.endsWith(".xml")) {
      parser = fhirContext.newXmlParser();
    } else {

      parser = fhirContext.newJsonParser();
    }
    return parser.parseResource(Bundle.class, readFileToString(relativePath));
  }

  public Bundle getBundleFromJsonString(String value) {
    return fhirContext.newJsonParser().parseResource(Bundle.class, value);
  }

  public Response createOutcomeResponse(IssueSeverity lvl) {
    String body =
        switch (lvl) {
          case INFORMATION -> getSuccessOutcome();
          case WARNING -> getWarningOutcome();
          case ERROR -> getErrorOutcome();
          case FATAL -> getFatalOutcome();
          case NULL -> null;
        };
    int status =
        switch (lvl) {
          case WARNING, INFORMATION -> 200;
          case ERROR, FATAL -> 422;
          case NULL -> 500;
        };
    return buildResponseWithDefaultRequest(status, body);
  }

  public OperationOutcome generateOutcome(int info, int warning, int error, int fatal) {
    OperationOutcome outcome = generateOutcome();
    IntStream.range(0, info)
        .forEach(i -> outcome.addIssue().setSeverity(INFORMATION).getDetails().setText("All OK"));
    IntStream.range(0, warning)
        .forEach(
            i -> outcome.addIssue().setSeverity(WARNING).getDetails().setText("This is a warning"));
    IntStream.range(0, error)
        .forEach(
            i -> outcome.addIssue().setSeverity(ERROR).getDetails().setText("This is an error"));
    IntStream.range(0, fatal)
        .forEach(
            i -> outcome.addIssue().setSeverity(FATAL).getDetails().setText("This failed fatal"));
    return outcome;
  }

  public byte[] getValidArsNotification(String contentType) {
    return switch (contentType) {
      case "application/json" -> VALID_ARS_NOTIFICATION_JSON_STRING.getBytes();
      case "application/xml" -> VALID_ARS_NOTIFICATION_XML_STRING.getBytes();
      default -> throw new IllegalArgumentException("Unsupported content type: " + contentType);
    };
  }

  public String getDefaultBundleAsString(MediaType contentType) {
    return switch (contentType.toString()) {
      case "application/json" -> fhirContext.newJsonParser().encodeResourceToString(defaultBundle);
      case "application/xml" -> fhirContext.newXmlParser().encodeResourceToString(defaultBundle);
      default -> throw new IllegalArgumentException("Unsupported content type: " + contentType);
    };
  }

  private Response buildResponseWithDefaultRequest(int status, String body) {
    return Response.builder()
        .status(status)
        .request(
            Request.create(
                Request.HttpMethod.GET,
                "https://example.com",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8))
        .body(body, StandardCharsets.UTF_8)
        .build();
  }

  private String getSuccessOutcome() {
    return generateOutcomeAsJsonString(1, 0, 0, 0);
  }

  private String getFatalOutcome() {
    return generateOutcomeAsJsonString(0, 0, 0, 1);
  }

  private String getErrorOutcome() {
    return generateOutcomeAsJsonString(0, 0, 1, 0);
  }

  private String getWarningOutcome() {
    return generateOutcomeAsJsonString(0, 1, 0, 0);
  }

  private String generateOutcomeAsJsonString(int inform, int warning, int error, int fatal) {
    return fhirContext
        .newJsonParser()
        .encodeResourceToString(generateOutcome(inform, warning, error, fatal));
  }

  public String getDefaultCompositionId() {
    return getDefaultBundle().getEntry().getFirst().getResource().getIdPart();
  }
}
