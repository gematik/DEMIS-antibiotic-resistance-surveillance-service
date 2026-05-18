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

import static java.util.Objects.requireNonNull;

import de.gematik.demis.ars.service.batchprocessing.NotificationListener;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
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
 * @see NotificationListener
 */
public record NotificationContext(Map<String, String> headers, UUID batchId) {

  public boolean isBatchProcessing() {
    return batchId != null;
  }

  public static NotificationContext fromHttpRequest(final HttpHeaders httpHeaders) {
    // TODO remove MultiValueMap
    final Map<String, String> headerMap =
        copyMap(httpHeaders.asMultiValueMap(), list -> list.isEmpty() ? null : list.getFirst());
    return new NotificationContext(headerMap, null);
  }

  public static NotificationContext fromMessageQueue(
      final Map<String, Object> amqpHeaders, final UUID batchId) {
    final Map<String, String> headerMap =
        copyMap(amqpHeaders, value -> value == null ? null : value.toString());
    return new NotificationContext(headerMap, requireNonNull(batchId, "batchId is required"));
  }

  private static <T> Map<String, String> copyMap(
      final Map<String, T> source, Function<T, String> valueToStringFunction) {
    final Map<String, String> target = new HashMap<>();
    source.forEach(
        (key, value) -> {
          final String valueString = valueToStringFunction.apply(value);
          if (valueString != null) {
            target.put(key, valueString);
          }
        });
    return target;
  }
}
