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

import de.gematik.demis.ars.service.api.FhirParametersResponseMapper;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;

/**
 * Immutable result object representing the outcome of notification processing.
 *
 * <p>This record encapsulates all relevant data produced during the processing of a FHIR
 * notification, including the original notification bundle, validation results, and extracted
 * specimen identifiers. It serves as a comprehensive data transfer object between the {@link
 * NotificationService} and other components such as controllers and message listeners.
 *
 * <p>The result contains:
 *
 * <ul>
 *   <li><b>notificationId</b> - Unique identifier for the processed notification
 *   <li><b>bundle</b> - The FHIR Bundle resource containing the notification data
 *   <li><b>validationOutcome</b> - FHIR OperationOutcome with validation results
 *   <li><b>specimenIdentifiers</b> - List of specimen identifiers extracted from the bundle
 * </ul>
 *
 * <p>This result is typically transformed into a FHIR Parameters resource by {@link
 * FhirParametersResponseMapper} for API responses.
 *
 * @param notificationId the unique identifier of the processed notification
 * @param bundle the FHIR Bundle containing the notification data
 * @param validationOutcome the validation results as a FHIR OperationOutcome
 * @param specimenIdentifiers the list of specimen identifiers extracted from the bundle
 * @see NotificationService
 * @see FhirParametersResponseMapper
 */
public record NotificationProcessingResult(
    String notificationId,
    Bundle bundle,
    OperationOutcome validationOutcome,
    List<Identifier> specimenIdentifiers) {}
