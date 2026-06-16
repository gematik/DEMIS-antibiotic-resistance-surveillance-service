package de.gematik.demis.ars.service.batchprocessing.result;

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
import ca.uhn.fhir.parser.IParser;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
class ParametersUtil {
  public static String resourceToString(final Parameters result) {
    return newJsonParser().encodeResourceToString(result);
  }

  public static Parameters stringToResource(final String jsonString) {
    return newJsonParser().parseResource(Parameters.class, jsonString);
  }

  public static Map<String, Object> toMap(final List<ParametersParameterComponent> params) {
    return params.stream()
        .collect(
            Collectors.toMap(
                ParametersParameterComponent::getName, ParametersUtil::valueOrPartsRecursive));
  }

  private static Object valueOrPartsRecursive(final ParametersParameterComponent p) {
    if (p.hasPart()) {
      return p.getPart().stream()
          .collect(
              Collectors.toMap(
                  ParametersParameterComponent::getName, ParametersUtil::valueOrPartsRecursive));
    } else {
      return p.getValue().primitiveValue();
    }
  }

  private static IParser newJsonParser() {
    return FhirContext.forR4Cached().newJsonParser();
  }
}
