package de.gematik.demis.ars.service.service.fhir;

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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import de.gematik.demis.ars.service.exception.ArsServiceException;
import de.gematik.demis.ars.service.exception.ErrorCode;
import de.gematik.demis.fhirparserlibrary.MessageType;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FhirParser {

  private final FhirContext fhirContext;

  /**
   * Takes a Fhir parameters resource as string, parses it and returns the contained <Bundle>. It`s
   * expecting that there is only one <Bundle> in <Parameters>
   *
   * @param content string representation of the Fhir parameters
   * @param messageType the type of the content
   * @return <Bundle> resource
   */
  public Bundle parseBundleFromNotification(String content, MessageType messageType) {
    return new de.gematik.demis.fhirparserlibrary.FhirParser(fhirContext)
        .parseBundleOrParameter(content, messageType);
  }

  /**
   * Deserializes a FHIR resource from a string while specify the required type
   *
   * @param content the content to deserialize
   * @param mediaType the media type of the content
   * @param resource the class of the resource to deserialize
   * @param <T> the type of the resource
   * @return the deserialized resource
   */
  public <T extends IBaseResource> T deserializeResource(
      String content, MediaType mediaType, Class<T> resource) {
    try {
      return getParser(mediaType).parseResource(resource, content);
    } catch (DataFormatException dataFormatException) {
      throw new ArsServiceException(
          ErrorCode.NOTIFICATION_ERROR, "Could not deserialize resource", dataFormatException);
    }
  }

  /**
   * Deserializes a FHIR resource from a string
   *
   * @param content the content to deserialize
   * @param mediaType the media type of the content
   * @return the deserialized resource
   */
  public IBaseResource deserializeResource(String content, MediaType mediaType) {
    try {
      return getParser(mediaType).parseResource(content);
    } catch (DataFormatException dataFormatException) {
      throw new ArsServiceException(
          ErrorCode.NOTIFICATION_ERROR, "Could not deserialize resource", dataFormatException);
    }
  }

  /**
   * Serializes a FHIR resource to a string
   *
   * @param resource the resource to serialize
   * @param mediaType the media type to serialize the resource to
   * @return the serialized resource
   */
  public String serializeResource(Resource resource, MediaType mediaType) {
    try {
      return getParser(mediaType).encodeResourceToString(resource);
    } catch (DataFormatException dataFormatException) {
      throw new ArsServiceException(
          ErrorCode.NOTIFICATION_ERROR, "Could not serialize resource", dataFormatException);
    }
  }

  private IParser getParser(MediaType mediaType) {
    if (mediaType.equalsTypeAndSubtype(MediaType.APPLICATION_JSON)) {
      return fhirContext.newJsonParser();
    } else {
      return fhirContext.newXmlParser();
    }
  }
}
