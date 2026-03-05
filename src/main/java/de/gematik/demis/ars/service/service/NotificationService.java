package de.gematik.demis.ars.service.service;

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

import de.gematik.demis.ars.service.service.contextenrichment.ContextEnrichmentService;
import de.gematik.demis.ars.service.service.fhir.FhirBundleOperator;
import de.gematik.demis.ars.service.service.fss.FssService;
import de.gematik.demis.ars.service.service.pseudonymisation.PseudonymisationService;
import de.gematik.demis.ars.service.service.sentinel.SentinelService;
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
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
  private final NotificationValidationService validationService;
  private final FhirBundleOperator fhirBundleOperator;
  private final PseudonymisationService pseudonymisationService;
  private final SentinelService sentinelService;
  private final ContextEnrichmentService contextEnrichmentService;
  private final FssService fssService;

  public NotificationProcessingResult process(
      String content, MessageType messageType, String authorization, NotificationContext context) {
    OperationOutcome validationOutcome =
        validationService.validateFhir(content, messageType, context);
    Bundle bundle = fhirBundleOperator.parseBundleFromNotification(content, messageType);
    pseudonymisationService.replacePatientIdentifier(bundle);
    sentinelService.removeSentinelData(bundle);
    String notificationId = UUID.randomUUID().toString();
    contextEnrichmentService.enrichBundleWithContextInformation(bundle, authorization);
    fhirBundleOperator.enrichBundle(bundle, notificationId);
    List<Identifier> specimenIdentifier = fhirBundleOperator.getSpecimenAccessionIdentifier(bundle);
    fssService.sendNotificationToFss(bundle);

    logInfos(notificationId, bundle);

    return new NotificationProcessingResult(
        notificationId, bundle, validationOutcome, specimenIdentifier);
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
