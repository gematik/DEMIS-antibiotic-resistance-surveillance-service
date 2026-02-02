package de.gematik.demis.ars.service;

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

import static de.gematik.demis.ars.service.parser.FhirParser.serializeResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import de.gematik.demis.ars.service.api.NotificationController;
import de.gematik.demis.ars.service.service.NotificationService;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.request.WebRequest;

@SpringBootTest
class ArsServiceApplicationTests {

  @MockitoBean private NotificationService notificationService;
  @Autowired private NotificationController notificationController;

  @Test
  void contextLoads() {
    Parameters params = new Parameters().addParameter("test", new StringType("success"));
    when(notificationService.process(any(), any(), any())).thenReturn(params);
    final WebRequest webRequest = Mockito.mock(WebRequest.class);
    assertThat(
            notificationController
                .fhirProcessNotificationPost("Bearer", "application/fhir+json", "test", webRequest)
                .getBody())
        .isEqualTo(serializeResource(params, APPLICATION_JSON));
  }
}
