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
import static de.gematik.demis.ars.service.utils.Constants.EXTENSION_URL_RECEPTION_TIME_STAMP_TYPE;
import static de.gematik.demis.ars.service.utils.Constants.NOTIFICATION_BUNDLE_IDENTIFIER_SYSTEM;

import ca.uhn.fhir.context.FhirContext;
import de.gematik.demis.ars.service.exception.ArsServiceException;
import de.gematik.demis.fhirparserlibrary.FhirParser;
import de.gematik.demis.fhirparserlibrary.MessageType;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Specimen;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FhirBundleOperator {

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
    return fhirParser().parseBundleOrParameter(content, messageType);
  }

  private FhirParser fhirParser() {
    return new FhirParser(fhirContext);
  }

  /**
   * Enriches the notification bundle with the given identifier, updates the composition reception
   * timestamp and last updated date of the resources in the bundle.
   *
   * @param bundle the notification bundle
   */
  public void enrichBundle(Bundle bundle, final String uuid) {
    updateBundleId(bundle, uuid);
    updateCompositionReceptionTimeStamp(bundle);
    updateResources(bundle);
  }

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

  private <T extends Resource> List<T> getEntriesOfType(Bundle bundle, Class<T> resource) {
    return bundle.getEntry().stream()
        .map(BundleEntryComponent::getResource)
        .filter(resource::isInstance)
        .map(e -> (T) e)
        .toList();
  }

  private void updateBundleId(final Bundle bundle, String uuid) {
    bundle.setIdentifier(
        new Identifier().setSystem(NOTIFICATION_BUNDLE_IDENTIFIER_SYSTEM).setValue(uuid));
  }

  private void updateCompositionReceptionTimeStamp(Bundle bundle) {
    Composition composition = getEntryOfType(bundle, Composition.class);
    Extension ex = new Extension();
    ex.setUrl(EXTENSION_URL_RECEPTION_TIME_STAMP_TYPE);
    ex.setValue(new DateTimeType(new Date()));
    composition.addExtension(ex);
  }

  private void updateResources(final IBaseResource... resources) {
    for (final var resource : resources) {
      resource.getMeta().setLastUpdated(new Date());
    }
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

  private <T extends Resource> T getEntryOfType(Bundle bundle, Class<T> resource) {
    return getOptionalEntryOfType(bundle, resource, null)
        .orElseThrow(
            () ->
                new ArsServiceException(
                    MISSING_RESOURCE,
                    "No resource of type " + resource.getSimpleName() + " found in Bundle"));
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

  /**
   * Adds an entry to the bundle
   *
   * @param bundle the <Bundle> to add the entry to
   * @param entry the <BundleEntryComponent> to add
   */
  public void addEntry(Bundle bundle, BundleEntryComponent entry) {
    bundle.addEntry(entry);
    updated(bundle);
  }

  private void updated(final IBaseResource... resources) {
    for (final var resource : resources) {
      resource.getMeta().setLastUpdated(new Date());
    }
  }
}
