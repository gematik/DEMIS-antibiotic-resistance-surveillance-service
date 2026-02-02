package de.gematik.demis.ars.service.api;

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

import de.gematik.demis.ars.service.service.NotificationService;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.service.base.fhir.response.FhirResponseConverter;
import lombok.AllArgsConstructor;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

@RestController
@AllArgsConstructor
public class NotificationController implements NotificationsApi {

  public final NotificationService service;
  public final FhirResponseConverter fhirConverter;

  @Override
  public ResponseEntity<Object> fhirProcessNotificationPost(
      final String authorization,
      final String contentType,
      final String content,
      final WebRequest webRequest) {
    final MessageType messageType = MessageType.getMessageType(contentType);
    final Parameters savedNotification = service.process(content, messageType, authorization);
    return fhirConverter.buildResponse(ResponseEntity.ok(), savedNotification, webRequest);
  }
}
