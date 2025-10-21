package de.gematik.demis.ars.service.service;

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

import static de.gematik.demis.ars.service.utils.Constants.NOTIFICATION_BUNDLE_IDENTIFIER_SYSTEM;

import de.gematik.demis.ars.service.service.contextenrichment.ContextEnrichmentService;
import de.gematik.demis.ars.service.service.fhir.FhirBundleOperator;
import de.gematik.demis.ars.service.service.fss.FssService;
import de.gematik.demis.ars.service.service.pseudonymisation.PseudonymisationService;
import de.gematik.demis.ars.service.service.validation.NotificationValidationService;
import de.gematik.demis.fhirparserlibrary.MessageType;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Device;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

  public static final String BUNDLE_IDENTIFIER_PARAMETER_NAME = "bundleIdentifier";
  public static final String SPECIMEN_IDENTIFIER_PARAMETER_NAME = "specimenIdentifier";
  public static final String OPERATION_OUTCOME_PARAMETER_NAME = "operationOutcome";
  public static final String OPERATION_OUTCOME_PARAMETER_PROFILE =
      "https://demis.rki.de/fhir/ars/StructureDefinition/ParametersOutput";
  private final NotificationValidationService validationService;
  private final PseudonymisationService pseudonymisationService;
  private final FhirBundleOperator fhirBundleOperator;
  private final ContextEnrichmentService contextEnrichmentService;
  private final FssService fssService;

  public Parameters process(String content, MessageType messageType, String authorization) {
    try {
      OperationOutcome validationOutcome = validationService.validateFhir(content, messageType);
      Bundle bundle = fhirBundleOperator.parseBundleFromNotification(content, messageType);
      pseudonymisationService.replacePatientIdentifier(bundle);
      String notificationId = UUID.randomUUID().toString();
      contextEnrichmentService.enrichBundleWithContextInformation(bundle, authorization);
      fhirBundleOperator.enrichBundle(bundle, notificationId);
      List<Identifier> specimenIdentifier =
          fhirBundleOperator.getSpecimenAccessionIdentifier(bundle);
      fssService.sendNotificationToFss(bundle);

      logInfos(notificationId, bundle);

      return buildResponse(validationOutcome, notificationId, specimenIdentifier);
    } catch (Exception e) {
      log.info(
          "Notification: bundleId=not available, sender=<placeholder>, primarySystem=not available, type=ARS");
      throw e;
    }
  }

  private Parameters buildResponse(
      OperationOutcome validationOutcome,
      String notificationId,
      List<Identifier> specimenIdentifier) {
    Parameters response = new Parameters();
    response.getMeta().addProfile(OPERATION_OUTCOME_PARAMETER_PROFILE);
    response.addParameter(
        BUNDLE_IDENTIFIER_PARAMETER_NAME,
        new Identifier().setValue(notificationId).setSystem(NOTIFICATION_BUNDLE_IDENTIFIER_SYSTEM));
    specimenIdentifier.forEach(
        identifier -> response.addParameter(SPECIMEN_IDENTIFIER_PARAMETER_NAME, identifier));
    response.addParameter(getOperationOutput(validationOutcome));
    return response;
  }

  private ParametersParameterComponent getOperationOutput(OperationOutcome validationOutcome) {
    ParametersParameterComponent operationOutcome = new ParametersParameterComponent();
    operationOutcome.setName(OPERATION_OUTCOME_PARAMETER_NAME);
    operationOutcome.setResource(validationOutcome);
    return operationOutcome;
  }

  private void logInfos(final String notificationId, final Bundle bundle) {
    String primarySystem = getPrimarySystemFromBundle(bundle);
    String type = "ARS";

    log.info(
        "Notification: bundleId={}, sender=<placeholder>, primarySystem={}, type={}",
        notificationId,
        primarySystem,
        type);
  }

  private String getPrimarySystemFromBundle(Bundle bundle) {
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      if (entry.getResource() instanceof Device device) {
        if (!device.getDeviceName().isEmpty()) {
          return device.getDeviceNameFirstRep().getName();
        }
      }
    }
    return "unknown";
  }
}
