package de.gematik.demis.ars.service.service.fss;

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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import de.gematik.demis.ars.service.exception.ArsServiceException;
import de.gematik.demis.ars.service.utils.TestUtils;
import de.gematik.demis.service.base.error.ServiceCallException;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class FssServiceTest {

  @Captor private ArgumentCaptor<String> jsonStringCaptor;
  private TestUtils testUtils = new TestUtils();

  @Mock FhirStorageServiceClient client;

  @InjectMocks FssService service;

  @Test
  void shouldSendBundleInBundle() {
    Bundle bundle = testUtils.getDefaultBundle();
    when(client.sendNotification(jsonStringCaptor.capture()))
        .thenReturn(ResponseEntity.ok("Call successful"));
    service.sendNotificationToFss(bundle);
    Bundle transactionBundle = testUtils.getBundleFromJsonString(jsonStringCaptor.getValue());
    assertThat(transactionBundle.getEntry()).hasSize(1);
  }

  @Test
  void shouldThrowArsFssExceptionIfRequestIsNotOk() {
    Bundle bundle = testUtils.getDefaultBundle();
    when(client.sendNotification(jsonStringCaptor.capture())).thenThrow(ServiceCallException.class);
    assertThrows(ArsServiceException.class, () -> service.sendNotificationToFss(bundle));
  }

  @Test
  void shouldSetTagForRkiCorrectly() {
    Bundle bundle = testUtils.getDefaultBundle();
    service.sendNotificationToFss(bundle);
    assertThat(bundle.getMeta().getTag()).hasSize(1);
    assertThat(bundle.getMeta().getTag().getFirst().getCode()).isEqualTo("1.");
    assertThat(bundle.getMeta().getTag().getFirst().getSystem())
        .isEqualTo("https://demis.rki.de/fhir/CodeSystem/ResponsibleDepartment");
  }
}
