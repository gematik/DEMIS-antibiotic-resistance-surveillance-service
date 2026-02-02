package de.gematik.demis.ars.service.service.pseudonymisation;

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

import static de.gematik.demis.ars.service.exception.ErrorCode.INVALID_PSEUDONYMS;
import static de.gematik.demis.ars.service.exception.ErrorCode.MISSING_RESOURCE;
import static java.util.stream.Collectors.toCollection;

import de.gematik.demis.ars.service.exception.ArsServiceException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Specimen;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Provides functionality to pseudonymize {@link Patient} resources within a {@link Bundle}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PseudonymisationService {

  private static final String UUID_PREFIX = "urn:uuid:";

  private final SurveillancePseudonymServiceClient pseudonymServiceClient;

  @Value("${feature.flag.surveillance_pseudonym_service_enabled}")
  private boolean pseudonymServiceClientEnabled;

  private static LocalDate toLocalDate(final DateTimeType date) {
    final var timeZone =
        date.getTimeZone() != null ? date.getTimeZone().toZoneId() : ZoneId.systemDefault();
    return date.getValue().toInstant().atZone(timeZone).toLocalDate();
  }

  /**
   * Replaces the identifiers of {@link Patient} resources within the given {@link Bundle} with a
   * pseudonymized UUID.
   *
   * <p>This method iterates through all entries in the provided {@link Bundle} and modifies any
   * {@link Patient} resource, by removing existing identifiers and adding a pseudonymized
   * identifier (UUID).
   *
   * @param bundle the {@link Bundle} containing the resources to process
   */
  public void replacePatientIdentifier(final Bundle bundle) {
    final LocalDate referenceDate = getReferenceDate(bundle);

    for (final Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      final Resource resource = entry.getResource();
      if (resource instanceof Patient patient) {
        replacePseudonyms(patient, referenceDate);
      }
    }
  }

  private void replacePseudonyms(final Patient patient, final LocalDate referenceDate) {
    final List<String> pseudonyms = getPseudonyms(patient);
    validatePseudonym(pseudonyms);
    removePseudonyms(patient);
    final PseudonymResponse newPseudonym;
    if (pseudonymServiceClientEnabled) {
      newPseudonym = callPseudoService(pseudonyms, referenceDate);
    } else {
      newPseudonym = generateFixPseudonym();
    }
    addPseudonym(patient, newPseudonym);
  }

  private void validatePseudonym(final List<String> pseudonyms) {
    if (pseudonyms.size() != 2) {
      throw new ArsServiceException(
          MISSING_RESOURCE, "Validation missing. Patient must have exactly 2 identifiers");
    }

    if (Objects.equals(pseudonyms.get(0), pseudonyms.get(1))) {
      throw new ArsServiceException(INVALID_PSEUDONYMS, "Pseudonyms must be different");
    }
  }

  /**
   * Extracts the reference date from the given bundle.
   *
   * <p>Fhir Path of reference date: Specimen/collection/collectedDateTime
   *
   * <p>Multiple specimens are possible, but than all collectedDateTimes should be equal. Otherwise,
   * the smallest date is taken and a warning is logged.
   */
  private LocalDate getReferenceDate(final Bundle bundle) {
    final SortedSet<LocalDate> dates =
        bundle.getEntry().stream()
            .map(Bundle.BundleEntryComponent::getResource)
            .filter(Specimen.class::isInstance)
            .map(Specimen.class::cast)
            .filter(
                specimen ->
                    specimen.hasCollection() && specimen.getCollection().hasCollectedDateTimeType())
            .map(specimen -> specimen.getCollection().getCollectedDateTimeType())
            .map(PseudonymisationService::toLocalDate)
            .collect(toCollection(TreeSet::new));

    if (dates.isEmpty()) {
      throw new ArsServiceException(MISSING_RESOURCE, "No reference date");
    }
    if (dates.size() > 1) {
      log.warn("{} different reference dates found. Take the smallest one.", dates.size());
    }

    return dates.first();
  }

  private PseudonymResponse callPseudoService(
      final List<String> pseudonyms, final LocalDate referenceDate) {
    final PseudonymRequest request =
        PseudonymRequest.builder()
            .pseudonym1(pseudonyms.get(0))
            .pseudonym2(pseudonyms.get(1))
            .date(referenceDate)
            .build();

    return pseudonymServiceClient.createPseudonym(request);
  }

  private List<String> getPseudonyms(final Patient patient) {
    return patient.getIdentifier().stream().map(Identifier::getValue).map(String::trim).toList();
  }

  private void removePseudonyms(final Patient patient) {
    patient.getIdentifier().clear();
  }

  private void addPseudonym(final Patient patient, final PseudonymResponse newPseudonym) {
    patient.addIdentifier().setSystem(newPseudonym.system()).setValue(newPseudonym.value());
  }

  private PseudonymResponse generateFixPseudonym() {
    final UUID tempUuid = UUID.fromString("10101010-1010-1010-1010-101010101010");
    final String pseudonym = UUID_PREFIX + tempUuid;
    return new PseudonymResponse(
        "https://demis.rki.de/fhir/sid/SurveillancePatientPseudonym", pseudonym);
  }
}
