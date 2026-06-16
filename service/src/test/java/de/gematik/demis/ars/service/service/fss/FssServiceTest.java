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

import ca.uhn.fhir.context.FhirContext;
import de.gematik.demis.ars.service.service.fhir.FhirParser;
import de.gematik.demis.ars.service.utils.TestUtils;
import de.gematik.demis.service.base.error.ServiceCallException;
import feign.RetryableException;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class FssServiceTest {

  @Captor private ArgumentCaptor<String> jsonStringCaptor;
  private final TestUtils testUtils = new TestUtils();

  @Mock FhirStorageServiceClient client;

  private FssService underTest;

  @BeforeEach
  void setup() {
    underTest = new FssService(client, new FhirParser(FhirContext.forR4Cached()));
  }

  @Test
  void shouldSendBundleInBundle() {
    Bundle bundle = testUtils.getDefaultBundle();
    when(client.sendNotification(jsonStringCaptor.capture()))
        .thenReturn(ResponseEntity.ok("Call successful"));

    underTest.sendNotificationToFss(bundle);

    Bundle transactionBundle = testUtils.getBundleFromJsonString(jsonStringCaptor.getValue());
    assertThat(transactionBundle.getEntry()).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(classes = {ServiceCallException.class, RetryableException.class})
  void shouldNotCatchFeignCallException(final Class<? extends Exception> exceptionClass) {
    Bundle bundle = testUtils.getDefaultBundle();
    when(client.sendNotification(jsonStringCaptor.capture())).thenThrow(exceptionClass);

    assertThrows(exceptionClass, () -> underTest.sendNotificationToFss(bundle));
  }
}
