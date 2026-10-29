package de.gematik.demis.ars.service.service.sentinel.terminology;

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

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hl7.fhir.r4.model.Coding;

/**
 * Codes of a value set, indexed by system and then by code for a fast lookup.
 *
 * @param codesBySystem system -> codes of this system
 */
public record ValueSet(Map<String, Set<String>> codesBySystem) {

  /** A value set without any codes. */
  public static final ValueSet EMPTY = new ValueSet(Map.of());

  /** Creates a value set from the given codes. */
  public static ValueSet of(final List<CodeKey> codes) {
    return new ValueSet(
        codes.stream().collect(groupingBy(CodeKey::system, mapping(CodeKey::code, toSet()))));
  }

  /** Returns true if system and code of the given coding are part of this value set. */
  public boolean contains(final Coding coding) {
    final String system = coding.getSystem();
    final String code = coding.getCode();
    // note: immutable collections (Map.of, Set.of) throw a NPE on null lookups
    if (system == null || code == null) {
      return false;
    }
    return codesBySystem.getOrDefault(system, Set.of()).contains(code);
  }
}
