package de.gematik.demis.ars.service.service.validation;

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

import static de.gematik.demis.ars.service.exception.ErrorCode.FHIR_VALIDATION_ERROR;
import static de.gematik.demis.ars.service.exception.ErrorCode.FHIR_VALIDATION_FATAL;
import static de.gematik.demis.ars.service.exception.ServiceCallErrorCode.VS;
import static de.gematik.demis.ars.service.parser.FhirParser.deserializeResource;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import de.gematik.demis.ars.service.exception.ArsValidationException;
import de.gematik.demis.ars.service.exception.ErrorCode;
import de.gematik.demis.ars.service.service.fhir.FhirOperationOutcomeOperationService;
import de.gematik.demis.service.base.error.ServiceCallException;
import feign.Response;
import feign.codec.Decoder;
import feign.codec.StringDecoder;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationValidationService {

  private final ValidationServiceClient validationServiceClient;
  private final FhirOperationOutcomeOperationService outcomeService;
  private final Decoder decoder = new StringDecoder();

  @Value("${feature.flag.ars_validation_enabled}")
  @Setter
  boolean validationEnabled;

  public OperationOutcome validateFhir(String content, MediaType mediaType) {
    if (!validationEnabled) {
      return outcomeService.generatePositiveOutcome();
    }
    HttpStatusCode status;
    String body;
    try (Response response = getValidationResponse(content, mediaType)) {
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
      handleValidationError(status, operationOutcome);
    }
    log.debug("Fhir Bundle successfully validated.");
    return outcomeService.success(operationOutcome);
  }

  private void handleValidationError(HttpStatusCode status, OperationOutcome operationOutcome) {
    final boolean hasFatalIssue =
        operationOutcome.getIssue().stream()
            .anyMatch(issue -> issue.getSeverity() == IssueSeverity.FATAL);
    final ErrorCode errorCode = hasFatalIssue ? FHIR_VALIDATION_FATAL : FHIR_VALIDATION_ERROR;
    operationOutcome =
        outcomeService.error(operationOutcome, status, errorCode, "Fhir Bundle validation failed.");
    throw new ArsValidationException(errorCode, operationOutcome);
  }

  private Response getValidationResponse(String content, MediaType mediaType) {
    return mediaType.equals(APPLICATION_JSON)
        ? validationServiceClient.validateJsonBundle(content)
        : validationServiceClient.validateXmlBundle(content);
  }

  private String readResponse(final Response response) {
    try {
      return (String) decoder.decode(response, String.class);
    } catch (final IOException e) {
      throw new ServiceCallException("error reading response", VS, response.status(), e);
    }
  }
}
