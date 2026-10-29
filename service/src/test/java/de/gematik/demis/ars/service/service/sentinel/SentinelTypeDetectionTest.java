package de.gematik.demis.ars.service.service.sentinel;

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

import static de.gematik.demis.ars.service.service.sentinel.SentinelType.CHLAMYDIA;
import static de.gematik.demis.ars.service.service.sentinel.SentinelType.MEASLES;
import static java.util.EnumSet.of;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.LoadingCache;
import de.gematik.demis.ars.service.service.sentinel.SentinelConfigurationProperties.ValueSetNames;
import de.gematik.demis.ars.service.service.sentinel.terminology.CodeKey;
import de.gematik.demis.ars.service.service.sentinel.terminology.ValueSet;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SentinelTypeDetectionTest {

  private static final String MEASLES_CODE_VALUESET =
      "https://demis.rki.de/fhir/ars/ValueSet/testMasernLOINC";
  private static final String MEASLES_VALUE_VALUESET =
      "https://demis.rki.de/fhir/ars/ValueSet/antwortMasernSNOMED";
  private static final String CHLAMYDIA_CODE_VALUESET =
      "https://demis.rki.de/fhir/ars/ValueSet/testChlamydienLOINC";
  private static final String CHLAMYDIA_VALUE_VALUESET =
      "https://demis.rki.de/fhir/ars/ValueSet/antwortChlamydienSNOMED";

  private static final String LOINC_SYSTEM = "http://loinc.org";
  private static final String SNOMED_SYSTEM = "http://snomed.info/sct";

  private static final Map<String, List<CodeKey>> VALUE_SETS =
      Map.ofEntries(
          entry(MEASLES_CODE_VALUESET, List.of(new CodeKey(LOINC_SYSTEM, "L-MAS-1"))),
          entry(MEASLES_VALUE_VALUESET, List.of(new CodeKey(SNOMED_SYSTEM, "S-MAS-1"))),
          entry(CHLAMYDIA_CODE_VALUESET, List.of(new CodeKey(LOINC_SYSTEM, "L-CHL-1"))),
          entry(CHLAMYDIA_VALUE_VALUESET, List.of(new CodeKey(SNOMED_SYSTEM, "S-CHL-1"))));

  private static final Map<SentinelType, ValueSetNames> VALUESET_NAMES =
      Map.of(
          MEASLES, new ValueSetNames(MEASLES_CODE_VALUESET, MEASLES_VALUE_VALUESET),
          CHLAMYDIA, new ValueSetNames(CHLAMYDIA_CODE_VALUESET, CHLAMYDIA_VALUE_VALUESET));

  private static final Observation randomObservation =
      observationWithCodeAndValue(
          new CodeKey("randomSystem-1", "randomCode"), new CodeKey("randomSystem-2", "randomCode"));

  @Mock private LoadingCache<String, ValueSet> valueSetCache;
  @Mock private SentinelConfigurationProperties properties;

  @InjectMocks private SentinelTypeDetection underTest;

  private static Observation observationWithCodeAndValue(final CodeKey code, final CodeKey value) {
    final Observation observation = new Observation();

    if (code != null) {
      observation.getCode().addCoding(new Coding().setSystem(code.system()).setCode(code.code()));
    }
    if (value != null) {
      observation.setValue(
          new CodeableConcept()
              .addCoding(new Coding().setSystem(value.system()).setCode(value.code())));
    }

    return observation;
  }

  private static Bundle bundleWith(final Observation... observations) {
    final Bundle bundle = new Bundle();
    Arrays.asList(observations).forEach(observation -> bundle.addEntry().setResource(observation));
    return bundle;
  }

  @BeforeEach
  void setUp() {
    when(properties.types()).thenReturn(VALUESET_NAMES);

    when(valueSetCache.get(anyString()))
        .thenAnswer(
            invocation -> {
              final String valueSetKey = invocation.getArgument(0, String.class);
              return ValueSet.of(VALUE_SETS.getOrDefault(valueSetKey, List.of()));
            });
  }

  @Test
  void noSentinelIfNeitherCodeNorValueMatch() {
    executeTest(EnumSet.noneOf(SentinelType.class), randomObservation);
  }

  @Test
  void noSentinelIfNoObservations() {
    executeTest(EnumSet.noneOf(SentinelType.class));
  }

  @Test
  void noSentinelIfJustCodeMatchButSystemNot() {
    final var valueSetNames = VALUESET_NAMES.get(MEASLES);
    final CodeKey code = VALUE_SETS.get(valueSetNames.codeValueSet()).getFirst();
    final CodeKey value = VALUE_SETS.get(valueSetNames.valueValueSet()).getFirst();
    final Observation observation =
        observationWithCodeAndValue(
            new CodeKey("random", code.code()), new CodeKey("random", value.code()));
    executeTest(EnumSet.noneOf(SentinelType.class), observation);
  }

  @ParameterizedTest
  @EnumSource(SentinelType.class)
  void shouldDetectSentinelIfCodeMatch(final SentinelType sentinelType) {
    final var valueSetNames = VALUESET_NAMES.get(sentinelType);
    final Observation observation =
        observationWithCodeAndValue(
            VALUE_SETS.get(valueSetNames.codeValueSet()).getLast(),
            new CodeKey("randomSystem", "randomCode"));

    executeTest(of(sentinelType), randomObservation, observation);
  }

  @ParameterizedTest
  @EnumSource(SentinelType.class)
  void shouldDetectSentinelIfValueMatch(final SentinelType sentinelType) {
    final var valueSetNames = VALUESET_NAMES.get(sentinelType);
    final Observation observation =
        observationWithCodeAndValue(
            new CodeKey("randomSystem", "randomCode"),
            VALUE_SETS.get(valueSetNames.valueValueSet()).getLast());

    executeTest(of(sentinelType), randomObservation, observation);
  }

  @ParameterizedTest
  @EnumSource(SentinelType.class)
  void shouldDetectSentinelIfCodeAndValueMatch(final SentinelType sentinelType) {
    final var valueSetNames = VALUESET_NAMES.get(sentinelType);
    final Observation observation =
        observationWithCodeAndValue(
            VALUE_SETS.get(valueSetNames.codeValueSet()).getLast(),
            VALUE_SETS.get(valueSetNames.valueValueSet()).getLast());

    executeTest(of(sentinelType), randomObservation, observation);
  }

  @Test
  void shouldDetectAllSentinels() {
    final Observation[] observations =
        VALUESET_NAMES.values().stream()
            .map(
                valueSetNames ->
                    observationWithCodeAndValue(
                        VALUE_SETS.get(valueSetNames.codeValueSet()).getLast(),
                        VALUE_SETS.get(valueSetNames.valueValueSet()).getLast()))
            .toArray(Observation[]::new);

    executeTest(EnumSet.allOf(SentinelType.class), observations);
  }

  private void executeTest(
      final EnumSet<SentinelType> expected, final Observation... observations) {
    final Bundle bundle = bundleWith(observations);
    assertThat(underTest.detect(bundle)).isEqualTo(expected);
  }
}
