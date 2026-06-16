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

import static lombok.AccessLevel.PRIVATE;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public class Constants {

  public static final String PROFILE_BASE_URL = "https://demis.rki.de/fhir/";

  public static final String NOTIFICATION_BUNDLE_IDENTIFIER_SYSTEM =
      PROFILE_BASE_URL + "NamingSystem/NotificationBundleId";

  public static final String EXTENSION_URL_RECEPTION_TIME_STAMP_TYPE =
      PROFILE_BASE_URL + "StructureDefinition/ReceptionTimeStamp";

  public static final String CODE_SYSTEM_BATCH_ID = PROFILE_BASE_URL + "CodeSystem/BatchId";
  public static final String RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM =
      PROFILE_BASE_URL + "CodeSystem/ResponsibleDepartment";

  public static final String RKI_DEPARTMENT_IDENTIFIER = "1.";
}
