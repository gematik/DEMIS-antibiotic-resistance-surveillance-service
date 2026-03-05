package de.gematik.demis.ars.service.service;

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

import de.gematik.demis.ars.service.batchprocessing.AsyncNotificationListener;
import java.util.HashMap;
import java.util.Map;
import lombok.Value;
import org.springframework.http.HttpHeaders;

/**
 * Immutable context object that encapsulates notification metadata from various sources.
 *
 * <p>This value object provides a unified representation of notification headers, regardless of
 * whether they originate from HTTP requests or AMQP messages. It serves as a transport mechanism
 * for contextual information needed during notification processing.
 *
 * <p>The context is immutable - all headers are stored in an unmodifiable map to prevent accidental
 * modification after creation.
 *
 * <p><b>Usage examples:</b>
 *
 * <pre>{@code
 * // From HTTP request
 * NotificationContext context = NotificationContext.fromHttpHeaders(httpHeaders);
 *
 * // From RabbitMQ message
 * NotificationContext context = NotificationContext.fromAmqpHeaders(amqpHeaders);
 * }</pre>
 *
 * @see NotificationService
 * @see AsyncNotificationListener
 */
@Value
public class NotificationContext {

  Map<String, String> headers;

  private NotificationContext(Map<String, String> headers) {
    this.headers = Map.copyOf(headers);
  }

  public static NotificationContext fromHttpHeaders(HttpHeaders httpHeaders) {
    Map<String, String> headerMap = new HashMap<>();
    httpHeaders.forEach(
        (key, values) -> {
          if (!values.isEmpty()) {
            headerMap.put(key, values.getFirst());
          }
        });
    return new NotificationContext(headerMap);
  }

  public static NotificationContext fromAmqpHeaders(Map<String, Object> amqpHeaders) {
    Map<String, String> headerMap = new HashMap<>();
    amqpHeaders.forEach(
        (key, value) -> {
          if (value != null) {
            headerMap.put(key, value.toString());
          }
        });
    return new NotificationContext(headerMap);
  }
}
