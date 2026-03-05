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

import static de.gematik.demis.ars.service.exception.ErrorCode.FHIR_VALIDATION_ERROR;
import static de.gematik.demis.ars.service.exception.ErrorCode.FHIR_VALIDATION_FATAL;
import static de.gematik.demis.ars.service.exception.ServiceCallErrorCode.VS;
import static de.gematik.demis.ars.service.parser.FhirParser.deserializeResource;
import static de.gematik.demis.ars.service.service.validation.ValidationServiceClient.HEADER_FHIR_API_VERSION;
import static de.gematik.demis.ars.service.service.validation.ValidationServiceClient.HEADER_FHIR_PROFILE;
import static de.gematik.demis.ars.service.service.validation.ValidationServiceClient.HEADER_FHIR_PROFILE_OLD;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.apicatalog.jsonld.StringUtils;
import de.gematik.demis.ars.service.exception.ArsValidationException;
import de.gematik.demis.ars.service.exception.ErrorCode;
import de.gematik.demis.ars.service.service.NotificationContext;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.service.base.error.ServiceCallException;
import de.gematik.demis.service.base.fhir.outcome.FhirOperationOutcomeService;
import feign.Response;
import feign.codec.Decoder;
import feign.codec.StringDecoder;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationValidationService {

  private final ValidationServiceClient validationServiceClient;
  private final FhirOperationOutcomeService outcomeService;
  private final Decoder decoder = new StringDecoder();

  public OperationOutcome validateFhir(
      String content, MessageType messageType, NotificationContext context) {
    HttpStatusCode status;
    String body;
    try (Response response = getValidationResponse(content, messageType, context.getHeaders())) {
      status = HttpStatus.valueOf(response.status());
      body = readResponse(response);
    }
    checkUnexpectedResult(status, body);
    return processOperationOutcome(status, body);
  }

  private void checkUnexpectedResult(HttpStatusCode status, String body) {
    if (status.value() != UNPROCESSABLE_ENTITY.value() && !status.is2xxSuccessful()) {
      throw new ServiceCallException("service response: " + body, VS, status.value(), null);
    }
  }

  private OperationOutcome processOperationOutcome(HttpStatusCode status, String body) {
    OperationOutcome operationOutcome =
        deserializeResource(body, APPLICATION_JSON, OperationOutcome.class);
    if (!status.is2xxSuccessful()) {
      handleValidationError(operationOutcome);
    }
    log.debug("Fhir Bundle successfully validated.");
    return processPositivOperationOutcome(operationOutcome);
  }

  private OperationOutcome processPositivOperationOutcome(final OperationOutcome operationOutcome) {
    outcomeService.processOutcome(operationOutcome);
    operationOutcome.getIssue().addFirst(outcomeService.allOk());
    return operationOutcome;
  }

  private void handleValidationError(OperationOutcome operationOutcome) {
    final boolean hasFatalIssue =
        operationOutcome.getIssue().stream()
            .anyMatch(issue -> issue.getSeverity() == IssueSeverity.FATAL);
    final ErrorCode errorCode = hasFatalIssue ? FHIR_VALIDATION_FATAL : FHIR_VALIDATION_ERROR;
    throw new ArsValidationException(errorCode, "Fhir Bundle validation failed.", operationOutcome);
  }

  private Response getValidationResponse(
      String content, MessageType messageType, Map<String, String> headers) {

    final HttpHeaders validationRequestHeaders = new HttpHeaders();

    String apiVersion = headers.get(HEADER_FHIR_API_VERSION);
    String profile = headers.get(HEADER_FHIR_PROFILE);
    if (StringUtils.isNotBlank(apiVersion) && StringUtils.isNotBlank(profile)) {
      validationRequestHeaders.put(HEADER_FHIR_API_VERSION, List.of(apiVersion));
      validationRequestHeaders.put(HEADER_FHIR_PROFILE, List.of(profile));
    }

    validationRequestHeaders.computeIfAbsent(
        HEADER_FHIR_PROFILE_OLD, ignored -> List.of("ars-profile-snapshots"));

    return messageType == MessageType.JSON
        ? validationServiceClient.validateJsonBundle(validationRequestHeaders, content)
        : validationServiceClient.validateXmlBundle(validationRequestHeaders, content);
  }

  private String readResponse(final Response response) {
    try {
      return (String) decoder.decode(response, String.class);
    } catch (final IOException e) {
      throw new ServiceCallException("error reading response", VS, response.status(), e);
    }
  }
}
