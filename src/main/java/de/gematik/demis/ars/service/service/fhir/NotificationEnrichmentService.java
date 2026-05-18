package de.gematik.demis.ars.service.service.fhir;

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

import static de.gematik.demis.ars.service.utils.Constants.CODE_SYSTEM_BATCH_ID;
import static de.gematik.demis.ars.service.utils.Constants.EXTENSION_URL_RECEPTION_TIME_STAMP_TYPE;
import static de.gematik.demis.ars.service.utils.Constants.NOTIFICATION_BUNDLE_IDENTIFIER_SYSTEM;
import static de.gematik.demis.ars.service.utils.Constants.RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM;
import static de.gematik.demis.ars.service.utils.Constants.RKI_DEPARTMENT_IDENTIFIER;

import de.gematik.demis.ars.service.service.NotificationContext;
import java.util.Date;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationEnrichmentService {

  private final NotificationReader notificationReader;

  /**
   * Enriches the notification bundle with the given identifier, updates the composition reception
   * timestamp and last updated date of the resources in the bundle.
   *
   * @param bundle the notification bundle
   * @param context
   */
  public void updateBundle(
      final Bundle bundle, final String uuid, final NotificationContext context) {
    replaceBundleId(bundle, uuid);
    updateCompositionReceptionTimeStamp(bundle);
    addBundleMetaTags(bundle, context);
    updated(bundle);
  }

  private void addBundleMetaTags(final Bundle bundle, final NotificationContext context) {
    setRkiDepartmentIdentifierTag(bundle);

    if (context.isBatchProcessing()) {
      addBatchIdAsMetaTag(bundle, context.batchId());
    }
  }

  private void addBatchIdAsMetaTag(final Bundle bundle, final UUID batchId) {
    bundle.getMeta().addTag().setSystem(CODE_SYSTEM_BATCH_ID).setCode(batchId.toString());
  }

  private void setRkiDepartmentIdentifierTag(Bundle bundle) {
    bundle
        .getMeta()
        .addTag(RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM, RKI_DEPARTMENT_IDENTIFIER, null);
  }

  private void replaceBundleId(final Bundle bundle, String uuid) {
    bundle.setIdentifier(
        new Identifier().setSystem(NOTIFICATION_BUNDLE_IDENTIFIER_SYSTEM).setValue(uuid));
  }

  private void updateCompositionReceptionTimeStamp(Bundle bundle) {
    Composition composition = notificationReader.getEntryOfType(bundle, Composition.class);
    Extension ex = new Extension();
    ex.setUrl(EXTENSION_URL_RECEPTION_TIME_STAMP_TYPE);
    ex.setValue(new DateTimeType(new Date()));
    composition.addExtension(ex);
  }

  /**
   * Adds an entry to the bundle
   *
   * @param bundle the <Bundle> to add the entry to
   * @param entry the <BundleEntryComponent> to add
   */
  public void addEntry(Bundle bundle, Bundle.BundleEntryComponent entry) {
    bundle.addEntry(entry);
    updated(bundle);
  }

  private void updated(final IBaseResource... resources) {
    for (final var resource : resources) {
      resource.getMeta().setLastUpdated(new Date());
    }
  }
}
