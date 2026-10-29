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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.hl7.fhir.r4.model.Coding;
import org.junit.jupiter.api.Test;

class ValueSetTest {

  private static final String LOINC = "http://loinc.org";
  private static final String SNOMED = "http://snomed.info/sct";

  private final ValueSet underTest =
      ValueSet.of(
          List.of(new CodeKey(LOINC, "A"), new CodeKey(LOINC, "B"), new CodeKey(SNOMED, "A")));

  @Test
  void containsMatchingSystemAndCode() {
    assertThat(underTest.contains(new Coding(LOINC, "A", null))).isTrue();
    assertThat(underTest.contains(new Coding(LOINC, "B", null))).isTrue();
    assertThat(underTest.contains(new Coding(SNOMED, "A", null))).isTrue();
  }

  @Test
  void doesNotContainCodeOfOtherSystem() {
    assertThat(underTest.contains(new Coding(SNOMED, "B", null))).isFalse();
  }

  @Test
  void doesNotContainUnknownSystemOrCode() {
    assertThat(underTest.contains(new Coding("other", "A", null))).isFalse();
    assertThat(underTest.contains(new Coding(LOINC, "X", null))).isFalse();
  }

  @Test
  void doesNotContainCodingWithoutSystemOrCode() {
    assertThat(underTest.contains(new Coding())).isFalse();
  }

  @Test
  void emptyValueSetContainsNothing() {
    assertThat(ValueSet.EMPTY.contains(new Coding(LOINC, "A", null))).isFalse();
    assertThat(ValueSet.of(List.of())).isEqualTo(ValueSet.EMPTY);
  }
}
