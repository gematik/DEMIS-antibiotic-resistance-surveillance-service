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

import static de.gematik.demis.ars.service.api.FhirParametersResponseMapper.BUNDLE_IDENTIFIER_PARAMETER_NAME;
import static de.gematik.demis.ars.service.api.FhirParametersResponseMapper.OPERATION_OUTCOME_PARAMETER_NAME;
import static de.gematik.demis.ars.service.api.FhirParametersResponseMapper.OPERATION_OUTCOME_PARAMETER_PROFILE;
import static de.gematik.demis.ars.service.api.FhirParametersResponseMapper.SPECIMEN_IDENTIFIER_PARAMETER_NAME;
import static de.gematik.demis.ars.service.utils.Constants.NOTIFICATION_BUNDLE_IDENTIFIER_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.demis.ars.service.service.NotificationProcessingResult;
import de.gematik.demis.ars.service.utils.TestUtils;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.Test;

class FhirParametersResponseMapperTest {

  private static final String NOTIFICATION_ID = "test-notification-id";
  private final TestUtils testUtils = new TestUtils();

  private final FhirParametersResponseMapper underTest = new FhirParametersResponseMapper();

  @Test
  void shouldAddMetaProfileCorrectly() {
    NotificationProcessingResult result = createResult(List.of(), new OperationOutcome());

    Parameters parameters = underTest.mapToParameters(result);

    assertThat(parameters.getMeta()).isNotNull();
    assertThat(parameters.getMeta().getProfile()).isNotNull();
    assertThat(OPERATION_OUTCOME_PARAMETER_PROFILE)
        .isEqualTo(parameters.getMeta().getProfile().getFirst().getValue());
  }

  @Test
  void shouldAddBundleIdentifierCorrectly() {
    NotificationProcessingResult result = createResult(List.of(), new OperationOutcome());

    Parameters parameters = underTest.mapToParameters(result);

    Parameters.ParametersParameterComponent notificationParam =
        parameters.getParameter().stream()
            .filter(p -> p.getName().equals(BUNDLE_IDENTIFIER_PARAMETER_NAME))
            .findFirst()
            .orElseThrow();
    assertThat(((Identifier) notificationParam.getValue()).getValue()).isEqualTo(NOTIFICATION_ID);
    assertThat(((Identifier) notificationParam.getValue()).getSystem())
        .isEqualTo(NOTIFICATION_BUNDLE_IDENTIFIER_SYSTEM);
  }

  @Test
  void shouldAddSpecimenIdentifiersParameter() {
    List<Identifier> specimenIds =
        List.of(new Identifier().setValue("specimenId"), new Identifier().setValue("specimenId2"));
    NotificationProcessingResult result = createResult(specimenIds, new OperationOutcome());

    Parameters parameters = underTest.mapToParameters(result);

    List<Parameters.ParametersParameterComponent> specimenParams =
        parameters.getParameter().stream()
            .filter(p -> p.getName().equals(SPECIMEN_IDENTIFIER_PARAMETER_NAME))
            .toList();
    assertThat(specimenParams).hasSize(2);
    assertThat(specimenParams.stream().map(p -> ((Identifier) p.getValue()).getValue()))
        .containsExactly("specimenId", "specimenId2");
  }

  @Test
  void shouldHandleEmptySpecimenIdentifiersList() {
    NotificationProcessingResult result = createResult(List.of(), new OperationOutcome());

    Parameters parameters = underTest.mapToParameters(result);

    long specimenParamCount =
        parameters.getParameter().stream()
            .filter(p -> p.getName().equals(SPECIMEN_IDENTIFIER_PARAMETER_NAME))
            .count();
    assertThat(specimenParamCount).isZero();
  }

  @Test
  void shouldAddOperationOutcomeCorrectly() {
    // Create validation outcome with 1 info, 0 warning, 0 error, 0 fatal
    OperationOutcome validationOutcome = testUtils.generateOutcome(1, 0, 0, 0);
    NotificationProcessingResult result = createResult(List.of(), validationOutcome);

    Parameters parameters = underTest.mapToParameters(result);

    OperationOutcome operationOutcome =
        (OperationOutcome)
            parameters.getParameter().stream()
                .filter(p -> p.getName().equals(OPERATION_OUTCOME_PARAMETER_NAME))
                .findFirst()
                .orElseThrow()
                .getResource();
    assertThat(OperationOutcome.IssueSeverity.INFORMATION)
        .isEqualTo(operationOutcome.getIssue().getFirst().getSeverity());
  }

  @Test
  void shouldHandleMultipleOperationOutcomeIssues() {
    // Create validation outcome with 2 info, 1 warning, 1 error, 0 fatal
    OperationOutcome outcome = testUtils.generateOutcome(2, 1, 1, 0);

    Parameters parameters = underTest.mapToParameters(createResult(List.of(), outcome));

    OperationOutcome result =
        (OperationOutcome)
            parameters.getParameter().stream()
                .filter(p -> p.getName().equals(OPERATION_OUTCOME_PARAMETER_NAME))
                .findFirst()
                .orElseThrow()
                .getResource();

    assertThat(result.getIssue()).hasSize(4);
  }

  private NotificationProcessingResult createResult(
      List<Identifier> specimenIds, OperationOutcome outcome) {
    return new NotificationProcessingResult(NOTIFICATION_ID, new Bundle(), outcome, specimenIds);
  }
}
