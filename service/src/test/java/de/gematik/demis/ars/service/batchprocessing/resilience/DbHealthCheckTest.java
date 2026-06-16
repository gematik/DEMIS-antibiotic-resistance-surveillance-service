package de.gematik.demis.ars.service.batchprocessing.resilience;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DbHealthCheckTest {

  @Mock private DataSource dataSource;
  @Mock private Connection connection;

  private DbHealthCheck underTest;

  @BeforeEach
  void setUp() throws SQLException {
    underTest = new DbHealthCheck(dataSource);
  }

  @Test
  void whenConnectionIsValid_should_returnTrue() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.isValid(anyInt())).thenReturn(true);

    assertThat(underTest.isValid()).isTrue();
  }

  @Test
  void whenConnectionIsNotValid_should_returnFalse() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.isValid(anyInt())).thenReturn(false);

    assertThat(underTest.isValid()).isFalse();
  }

  @Test
  void whenSqlExceptionIsThrown_shouldReturnFalse() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.isValid(anyInt())).thenThrow(new SQLException("For test"));

    assertThat(underTest.isValid()).isFalse();
  }

  @Test
  void whenDataSourceIsNotReachable_shouldReturnFalse() throws SQLException {
    when(dataSource.getConnection()).thenThrow(new SQLException("Datasource has no connection"));

    assertThat(underTest.isValid()).isFalse();
  }
}
