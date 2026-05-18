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

import static de.gematik.demis.ars.service.utils.TestUtils.VALID_ARS_NOTIFICATION_JSON;
import static de.gematik.demis.ars.service.utils.TestUtils.VALID_ARS_NOTIFICATION_TWO_SPECIMEN_JSON;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.demis.ars.service.utils.TestUtils;
import java.util.List;
import lombok.SneakyThrows;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.junit.jupiter.api.Test;

class NotificationReaderTest {

  private final NotificationReader underTest = new NotificationReader();

  private final TestUtils testUtils = new TestUtils();

  @Test
  @SneakyThrows
  void shouldParseOneSpecimenAccessionIdentifierCorrectly() {
    Bundle bundle = testUtils.readFileToBundle(VALID_ARS_NOTIFICATION_JSON);
    List<Identifier> identifierts = underTest.getSpecimenAccessionIdentifier(bundle);
    assertThat(identifierts).hasSize(1);
    assertThat(identifierts.getFirst().getSystem())
        .isEqualTo("http://www.Labor-Celle.de/identifiers/specimen/accessionIdentifier");
    assertThat(identifierts.getFirst().getValue()).isEqualTo("23-000034");
  }

  @Test
  @SneakyThrows
  void shouldParseTwoSpecimenAccessionIdentifierCorrectly() {
    Bundle bundle = testUtils.readFileToBundle(VALID_ARS_NOTIFICATION_TWO_SPECIMEN_JSON);
    List<Identifier> identifierts = underTest.getSpecimenAccessionIdentifier(bundle);
    assertThat(identifierts).hasSize(2);
    assertThat(identifierts.stream().map(Identifier::getSystem))
        .containsExactly(
            "http://www.Labor-Ostholstein.de/identifiers/specimen/accessionIdentifier",
            "http://www.Labor-Ostholstein.de/identifiers/specimen/accessionIdentifier");
    assertThat(identifierts.stream().map(Identifier::getValue))
        .containsExactly("O24-001081", "24-003257");
  }
}
