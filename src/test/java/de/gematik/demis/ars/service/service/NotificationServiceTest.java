package de.gematik.demis.ars.service.service;

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

import static de.gematik.demis.ars.service.service.NotificationService.BUNDLE_IDENTIFIER_PARAMETER_NAME;
import static de.gematik.demis.ars.service.service.NotificationService.OPERATION_OUTCOME_PARAMETER_NAME;
import static de.gematik.demis.ars.service.service.NotificationService.OPERATION_OUTCOME_PARAMETER_PROFILE;
import static de.gematik.demis.ars.service.service.NotificationService.SPECIMEN_IDENTIFIER_PARAMETER_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.demis.ars.service.service.contextenrichment.ContextEnrichmentService;
import de.gematik.demis.ars.service.service.fhir.FhirBundleOperator;
import de.gematik.demis.ars.service.service.fss.FssService;
import de.gematik.demis.ars.service.service.pseudonymisation.PseudonymisationService;
import de.gematik.demis.ars.service.service.validation.NotificationValidationService;
import de.gematik.demis.ars.service.utils.TestUtils;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  private final TestUtils testUtil = new TestUtils();
  @Mock NotificationValidationService validationService;
  @Mock FhirBundleOperator fhirBundleOperator;
  @Mock ContextEnrichmentService contextEnrichmentService;
  @Mock PseudonymisationService pseudonymisationService;
  @Mock FssService fssService;
  @Captor private ArgumentCaptor<String> uuidCaptor;
  @Captor private ArgumentCaptor<Bundle> bundleCaptor;
  @InjectMocks private NotificationService underTest;

  @Test
  void shouldAddOperationOutcomeCorrectly() {
    when(validationService.validateFhir(any(), any()))
        .thenReturn(testUtil.generateOutcome(1, 0, 0, 0));
    when(fhirBundleOperator.parseBundleFromNotification(any(), any()))
        .thenReturn(testUtil.getDefaultBundle());
    doNothing().when(contextEnrichmentService).enrichBundleWithContextInformation(any(), any());
    doNothing().when(pseudonymisationService).replacePatientIdentifier(any());
    doNothing().when(fssService).sendNotificationToFss(any());
    Parameters output = underTest.process("test", MediaType.APPLICATION_JSON, "");
    assertNotNull(output);
    OperationOutcome operationOutcome =
        (OperationOutcome)
            output.getParameter().stream()
                .filter(p -> p.getName().equals(OPERATION_OUTCOME_PARAMETER_NAME))
                .findFirst()
                .orElseThrow()
                .getResource();
    assertEquals(
        OperationOutcome.IssueSeverity.INFORMATION,
        operationOutcome.getIssue().getFirst().getSeverity());
    verify(contextEnrichmentService, times(1)).enrichBundleWithContextInformation(any(), any());
    verify(pseudonymisationService, times(1)).replacePatientIdentifier(any());
    verify(fssService, times(1)).sendNotificationToFss(any());
  }

  @Test
  void shouldAddNewNotificationIdCorrectly() {
    when(validationService.validateFhir(any(), any()))
        .thenReturn(testUtil.generateOutcome(1, 0, 0, 0));
    when(fhirBundleOperator.parseBundleFromNotification(any(), any()))
        .thenReturn(testUtil.getDefaultBundle());
    doNothing().when(fhirBundleOperator).enrichBundle(bundleCaptor.capture(), uuidCaptor.capture());
    Parameters output = underTest.process("test", MediaType.APPLICATION_JSON, "");
    assertNotNull(output);
    ParametersParameterComponent notificationParam =
        output.getParameter().stream()
            .filter(p -> p.getName().equals(BUNDLE_IDENTIFIER_PARAMETER_NAME))
            .findFirst()
            .orElseThrow();
    assertThat(((Identifier) notificationParam.getValue()).getValue())
        .isEqualTo(uuidCaptor.getValue());
  }

  @Test
  void shouldAddSpecimenIdCorrectly() {
    when(validationService.validateFhir(any(), any()))
        .thenReturn(testUtil.generateOutcome(1, 0, 0, 0));
    when(fhirBundleOperator.parseBundleFromNotification(any(), any()))
        .thenReturn(testUtil.getDefaultBundle());
    when(fhirBundleOperator.getSpecimenAccessionIdentifier(any()))
        .thenReturn(List.of(new Identifier().setValue("specimenId")));

    Parameters output = underTest.process("test", MediaType.APPLICATION_JSON, "");
    assertNotNull(output);
    List<ParametersParameterComponent> specimenParams =
        output.getParameter().stream()
            .filter(p -> p.getName().equals(SPECIMEN_IDENTIFIER_PARAMETER_NAME))
            .toList();
    assertThat(specimenParams).hasSize(1);
    assertThat(specimenParams.stream().map(p -> ((Identifier) p.getValue()).getValue()))
        .containsExactly("specimenId");
  }

  @Test
  void shouldAddSpecimenIdsCorrectly() {
    when(validationService.validateFhir(any(), any()))
        .thenReturn(testUtil.generateOutcome(1, 0, 0, 0));
    when(fhirBundleOperator.parseBundleFromNotification(any(), any()))
        .thenReturn(testUtil.getDefaultBundle());
    when(fhirBundleOperator.getSpecimenAccessionIdentifier(any()))
        .thenReturn(
            List.of(
                new Identifier().setValue("specimenId"), new Identifier().setValue("specimenId2")));

    Parameters output = underTest.process("test", MediaType.APPLICATION_JSON, "");
    assertNotNull(output);
    List<ParametersParameterComponent> specimenParams =
        output.getParameter().stream()
            .filter(p -> p.getName().equals(SPECIMEN_IDENTIFIER_PARAMETER_NAME))
            .toList();
    assertThat(specimenParams).hasSize(2);
    assertThat(specimenParams.stream().map(p -> ((Identifier) p.getValue()).getValue()))
        .containsExactly("specimenId", "specimenId2");
  }

  @Test
  void shouldAddMetaProfileCorrectly() {
    when(validationService.validateFhir(any(), any()))
        .thenReturn(testUtil.generateOutcome(1, 0, 0, 0));
    when(fhirBundleOperator.parseBundleFromNotification(any(), any()))
        .thenReturn(testUtil.getDefaultBundle());
    when(fhirBundleOperator.getSpecimenAccessionIdentifier(any()))
        .thenReturn(List.of(new Identifier().setValue("specimenId")));

    Parameters output = underTest.process("test", MediaType.APPLICATION_JSON, "");

    assertNotNull(output.getMeta());
    assertNotNull(output.getMeta().getProfile());
    assertEquals(
        OPERATION_OUTCOME_PARAMETER_PROFILE, output.getMeta().getProfile().get(0).getValue());
  }
}
