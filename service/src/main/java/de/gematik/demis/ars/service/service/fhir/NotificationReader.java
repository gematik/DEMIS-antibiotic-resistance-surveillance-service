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

import static de.gematik.demis.ars.service.exception.ErrorCode.MISSING_RESOURCE;

import de.gematik.demis.ars.service.exception.ArsServiceException;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Specimen;
import org.springframework.stereotype.Component;

@Component
public class NotificationReader {
  /**
   * Extracts the specimen identifiers from the notification bundle.
   *
   * @param bundle the notification bundle
   * @return
   */
  public List<Identifier> getSpecimenAccessionIdentifier(Bundle bundle) {
    List<Specimen> specimens = getEntriesOfType(bundle, Specimen.class);
    if (specimens.isEmpty()) {
      throw new ArsServiceException(
          MISSING_RESOURCE,
          "Tried to extract specimen identifier, but could not found needed resource");
    }
    return specimens.stream().map(Specimen::getAccessionIdentifier).toList();
  }

  public String getBundleId(final Bundle bundle) {
    return bundle.getIdentifier().getValue();
  }

  /**
   * Get the composition id from a <Bundle>
   *
   * @param bundle the <Bundle> to search in
   * @return the composition id
   */
  public String getCompositionId(Bundle bundle) {
    return getEntryOfType(bundle, Composition.class).getIdPart();
  }

  private <T extends Resource> List<T> getEntriesOfType(Bundle bundle, Class<T> resource) {
    return bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(resource::isInstance)
        .map(e -> (T) e)
        .toList();
  }

  /**
   * Extracts the first resource of a specific type from a <Bundle> out of its entries. Optional a
   * filter can be given. If filter is null the filter doesn't have any effect. This is needed if
   * the resource could appear multiple times in the <Bundle> and the correct one needs to be
   * selected.
   *
   * @param bundle <Bundle> to extract the resource from
   * @param resource Type of the resource to extract
   * @param filter Filter to apply to the resource. If null than it have no effect
   * @param <T> Type of the resource to extract
   * @return The first resource of the specified type that matches the filter and given type
   */
  @SuppressWarnings("unchecked")
  private <T extends Resource> Optional<T> getOptionalEntryOfType(
      Bundle bundle, Class<T> resource, Predicate<T> filter) {
    if (filter == null) {
      filter = res -> true;
    }
    return getEntriesOfType(bundle, resource).stream().filter(filter).findFirst();
  }

  <T extends Resource> T getEntryOfType(Bundle bundle, Class<T> resource) {
    return getOptionalEntryOfType(bundle, resource, null)
        .orElseThrow(
            () ->
                new ArsServiceException(
                    MISSING_RESOURCE,
                    "No resource of type " + resource.getSimpleName() + " found in Bundle"));
  }
}
