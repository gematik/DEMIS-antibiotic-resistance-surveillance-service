package de.gematik.demis.ars.service.service.sentinel;

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

import static de.gematik.demis.ars.service.utils.TestUtils.ARS_NOTIFICATION_SENTINEL_JSON;
import static de.gematik.demis.ars.service.utils.TestUtils.ARS_NOTIFICATION_SENTINEL_PROCESSED_JSON;
import static de.gematik.demis.ars.service.utils.TestUtils.VALID_ARS_NOTIFICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.gematik.demis.ars.service.utils.TestUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;

class SentinelServiceTest {

  final IParser jsonParser = FhirContext.forR4Cached().newJsonParser();
  final TestUtils testUtils = new TestUtils();
  final SentinelService underTest = new SentinelService();

  @Test
  void shouldRemoveSentinelData() {
    final Bundle bundle = testUtils.readFileToBundle(ARS_NOTIFICATION_SENTINEL_JSON);
    final Bundle expected = testUtils.readFileToBundle(ARS_NOTIFICATION_SENTINEL_PROCESSED_JSON);

    underTest.removeSentinelData(bundle);

    assertThat(jsonParser.encodeResourceToString(bundle))
        .isEqualToIgnoringWhitespace(jsonParser.encodeResourceToString(expected));
  }

  @Test
  void shouldHandleEmptyBundle() {
    final Bundle emptyBundle = new Bundle();
    assertDoesNotThrow(() -> underTest.removeSentinelData(emptyBundle));
  }

  @Test
  void shouldHandleBundleWithNoRelevantResources() {
    final Bundle bundle = testUtils.readFileToBundle(VALID_ARS_NOTIFICATION_JSON);
    final Bundle expected = testUtils.readFileToBundle(VALID_ARS_NOTIFICATION_JSON);

    assertDoesNotThrow(() -> underTest.removeSentinelData(bundle));
    assertThat(jsonParser.encodeResourceToString(bundle))
        .isEqualToIgnoringWhitespace(jsonParser.encodeResourceToString(expected));
  }
}
