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

import static de.gematik.demis.ars.service.utils.Constants.NOTIFICATION_BUNDLE_IDENTIFIER_SYSTEM;

import de.gematik.demis.ars.service.service.NotificationProcessingResult;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.stereotype.Component;

/**
 * Maps notification processing results to FHIR Parameters resources.
 *
 * <p>This mapper transforms {@link NotificationProcessingResult} objects into FHIR Parameters
 * resources that conform to the ARS (Antibiotic Resistance Surveillance) output structure. The
 * resulting Parameters resource includes:
 *
 * <ul>
 *   <li>Bundle identifier - the notification ID
 *   <li>Specimen identifiers - extracted from the processed notification
 *   <li>Operation outcome - validation results
 * </ul>
 */
@Component
public class FhirParametersResponseMapper {
  public static final String BUNDLE_IDENTIFIER_PARAMETER_NAME = "bundleIdentifier";
  public static final String SPECIMEN_IDENTIFIER_PARAMETER_NAME = "specimenIdentifier";
  public static final String OPERATION_OUTCOME_PARAMETER_NAME = "operationOutcome";
  public static final String OPERATION_OUTCOME_PARAMETER_PROFILE =
      "https://demis.rki.de/fhir/ars/StructureDefinition/ParametersOutput";

  public Parameters mapToParameters(NotificationProcessingResult processingResult) {
    Parameters response = new Parameters();
    response.getMeta().addProfile(OPERATION_OUTCOME_PARAMETER_PROFILE);
    response.addParameter(
        BUNDLE_IDENTIFIER_PARAMETER_NAME,
        new Identifier()
            .setValue(processingResult.notificationId())
            .setSystem(NOTIFICATION_BUNDLE_IDENTIFIER_SYSTEM));
    processingResult
        .specimenIdentifiers()
        .forEach(
            identifier -> response.addParameter(SPECIMEN_IDENTIFIER_PARAMETER_NAME, identifier));
    response.addParameter(getOperationOutput(processingResult.validationOutcome()));
    return response;
  }

  private Parameters.ParametersParameterComponent getOperationOutput(
      OperationOutcome validationOutcome) {
    Parameters.ParametersParameterComponent operationOutcome =
        new Parameters.ParametersParameterComponent();
    operationOutcome.setName(OPERATION_OUTCOME_PARAMETER_NAME);
    operationOutcome.setResource(validationOutcome);
    return operationOutcome;
  }
}
