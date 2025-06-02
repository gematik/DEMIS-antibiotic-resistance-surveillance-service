package de.gematik.demis.ars.service.utils;

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

import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.PRIVATE;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Resource;

@NoArgsConstructor(access = PRIVATE)
public class Constants {

  public static final String PROFILE_BASE_URL = "https://demis.rki.de/fhir/";
  public static final Profile<OperationOutcome> PROCESS_NOTIFICATION_RESPONSE_PROFILE =
      new Profile<>(PROFILE_BASE_URL + "StructureDefinition/ProcessNotificationResponse");
  public static final String NOTIFICATION_BUNDLE_IDENTIFIER_SYSTEM =
      PROFILE_BASE_URL + "NamingSystem/NotificationBundleId";
  public static final String EXTENSION_URL_RECEPTION_TIME_STAMP_TYPE =
      PROFILE_BASE_URL + "StructureDefinition/ReceptionTimeStamp";

  @Getter
  public static class Profile<T extends Resource> {

    private final String url;

    private Profile(String url) {
      requireNonNull(url, "url missing");
      this.url = url;
    }

    /**
     * Applies the URL wrapped by this object as the profile on the give resource {@code t} if not
     * already present.
     *
     * @param t the resource on which the profile will be applied
     */
    public void applyTo(T t) {
      if (t.getMeta().getProfile().stream()
          .map(CanonicalType::asStringValue)
          .toList()
          .contains(url)) {
        return;
      }
      t.getMeta().addProfile(url);
    }
  }
}
