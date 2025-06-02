package de.gematik.demis.ars.service.api;

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

import static lombok.AccessLevel.PRIVATE;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;

/** Helper class to map content types of incoming FHIR resources to a standardized mimetype. */
@NoArgsConstructor(access = PRIVATE)
public class FhirContentTypeMapper {
  private static final Map<String, MediaType> representationMap =
      Map.of(
          "application/json", MediaType.APPLICATION_JSON,
          "application/xml", MediaType.APPLICATION_XML,
          "application/json+fhir", MediaType.APPLICATION_JSON,
          "application/fhir+json", MediaType.APPLICATION_JSON,
          "application/json;charset=utf-8", MediaType.APPLICATION_JSON,
          "application/xml;charset=utf-8", MediaType.APPLICATION_XML,
          "application/json+fhir;charset=utf-8", MediaType.APPLICATION_JSON,
          "application/fhir+json;charset=utf-8", MediaType.APPLICATION_JSON);

  /**
   * Maps content type of the incoming FHIR resource to a standardized mimetype
   *
   * @param mediaType content type of incoming resource
   * @return matching mimetype
   */
  public static MediaType mapStringToMediaType(final @NotNull String mediaType) {
    if (StringUtils.isBlank(mediaType)) {
      return null;
    }
    String mediaTypeResult = mediaType.toLowerCase().replace(" ", "");
    return representationMap.get(mediaTypeResult);
  }
}
