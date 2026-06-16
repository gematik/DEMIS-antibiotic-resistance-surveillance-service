package de.gematik.demis.ars.service.batchprocessing.result;

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

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import de.gematik.demis.ars.service.batchprocessing.entity.ErrorReasonEnum;
import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;
import org.springframework.web.util.UriComponentsBuilder;

@Setter
@Accessors(chain = true, fluent = true)
class StatisticsParametersBuilder {
  private static final String PARAMETER_BATCH_ID = "batchId";
  private static final String PARAMETER_BATCH_CLOSED_AT = "batchClosedAt";
  private static final String PARAMETER_RESULTS_AVAILABLE_UNTIL = "resultsAvailableUntil";
  private static final String PARAMETER_TOTAL = "total";
  private static final String PARAMETER_SUCCESS = "success";
  private static final String PARAMETER_ERROR = "error";

  private static final String QUERY_VALUE_SUCCESS = "success";
  private static final String QUERY_VALUE_ERROR = "error";

  private UUID batchId;
  private Instant completedAt;
  private Instant expiresAt;
  private int total;
  private int successCount;
  private int failureCount;
  private Map<ErrorReasonEnum, Long> errorCountsPerReason;
  private URI detailUrl;

  private static DateTimeType toDateTimeType(final Instant instant) {
    return new DateTimeType(Date.from(instant), TemporalPrecisionEnum.SECOND);
  }

  public Parameters build() {
    return new Parameters()
        .addParameter(PARAMETER_BATCH_ID, batchId.toString())
        .addParameter(PARAMETER_BATCH_CLOSED_AT, toDateTimeType(completedAt))
        .addParameter(PARAMETER_RESULTS_AVAILABLE_UNTIL, toDateTimeType(expiresAt))
        .addParameter(PARAMETER_TOTAL, total)
        .addParameter(buildSuccessParameter())
        .addParameter(buildErrorParameter());
  }

  private ParametersParameterComponent buildSuccessParameter() {
    final ParametersParameterComponent success =
        new ParametersParameterComponent().setName(PARAMETER_SUCCESS);
    success.addPart().setName("url").setValue(new UriType(getDetailUrl(QUERY_VALUE_SUCCESS)));
    success.addPart().setName("contentType").setValue(new StringType("text/csv"));
    success.addPart().setName("count").setValue(new IntegerType(successCount));
    return success;
  }

  private ParametersParameterComponent buildErrorParameter() {
    final ParametersParameterComponent error =
        new ParametersParameterComponent().setName(PARAMETER_ERROR);
    error.addPart().setName("url").setValue(new UriType(getDetailUrl(QUERY_VALUE_ERROR)));
    error.addPart().setName("contentType").setValue(new StringType("text/csv"));
    error.addPart().setName("count").setValue(new IntegerType(failureCount));

    // countsByErrorCode
    final ParametersParameterComponent countsByErrorCode =
        error.addPart().setName("countsByErrorCode");
    for (final ErrorReasonEnum reason : ErrorReasonEnum.values()) {
      final Long count = errorCountsPerReason.getOrDefault(reason, 0L);
      countsByErrorCode.addPart().setName(reason.name()).setValue(new IntegerType(count));
    }
    return error;
  }

  private String getDetailUrl(final String queryValue) {
    return UriComponentsBuilder.fromUri(detailUrl).queryParam("query", queryValue).toUriString();
  }
}
