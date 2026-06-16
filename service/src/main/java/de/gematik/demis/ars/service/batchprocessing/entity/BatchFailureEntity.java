package de.gematik.demis.ars.service.batchprocessing.entity;

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

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

/** Stores not successfully processed notifications. */
@Entity
@Immutable
@Table(name = "batch_failure")
@Setter
@Getter
@ToString(callSuper = true)
public class BatchFailureEntity extends BatchResultBase {

  /** The reason why the notification could not process */
  @Convert(converter = ErrorReasonEnumConverter.class)
  @Column(nullable = false, length = 1)
  private ErrorReasonEnum errorReason;

  /** number of validation errors. Null if notification was not validated */
  @Column private Integer errorCount;

  /** number of validation warnings. Null if notification was not validated */
  @Column private Integer warningCount;

  /** Optional more details of the error */
  @Column private String detail;
}
