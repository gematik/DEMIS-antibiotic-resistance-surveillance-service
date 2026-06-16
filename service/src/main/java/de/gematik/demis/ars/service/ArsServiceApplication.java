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

import de.gematik.demis.ars.service.batchprocessing.config.BatchResultProperties;
import de.gematik.demis.service.base.apidoc.EnableDefaultApiSpecConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

/** Application Entrypoint. */
@EnableFeignClients
@SpringBootApplication
@EnableDefaultApiSpecConfig
@EnableConfigurationProperties(BatchResultProperties.class)
@Slf4j
public class ArsServiceApplication {

  public static void main(final String[] args) {
    final SpringApplication app = new SpringApplication(ArsServiceApplication.class);
    boolean ffBulkEnabled = Boolean.parseBoolean(System.getenv("FEATURE_FLAG_ARS_BULK_ENABLED"));
    if (!ffBulkEnabled) {
      log.info(
          "FEATURE_FLAG_ARS_BULK_ENABLED is not enabled. Exclude Autoconfiguration of database");
      app.setAdditionalProfiles("no-db");
    }
    app.run(args);
  }
}
