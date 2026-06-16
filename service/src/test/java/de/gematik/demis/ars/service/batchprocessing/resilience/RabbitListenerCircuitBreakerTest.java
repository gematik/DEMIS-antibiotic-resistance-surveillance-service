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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RabbitListenerCircuitBreakerTest {

  // is a white box test
  private static final String FLAG_LISTENER_ACTIVE = "listenerActive";
  private static final String FLAG_STOPPING_IN_PROGRESS = "stoppingInProgress";

  @Mock private RabbitListenerEndpointRegistry rabbitRegistry;
  @Mock private DbHealthCheck dbHealthCheck;
  @Mock private MessageListenerContainer container;

  private RabbitListenerCircuitBreaker underTest;

  @BeforeEach
  void setUp() {
    underTest = new RabbitListenerCircuitBreaker(rabbitRegistry, dbHealthCheck);
    lenient().when(rabbitRegistry.getListenerContainers()).thenReturn(List.of(container));
  }

  private void setListenerInactive() {
    ReflectionTestUtils.setField(underTest, FLAG_LISTENER_ACTIVE, false);
  }

  @Nested
  class TriggerDatabaseCheck {
    @Test
    void triggerDatabaseCheck_whenDbIsUp_should_notStopAnyContainer() {
      when(dbHealthCheck.isValid()).thenReturn(true);

      underTest.triggerDatabaseCheck();

      verify(container, never()).stop();
    }

    @Test
    void triggerDatabaseCheck_whenStoppingAlreadyInProgress_should_doNothing() {
      ReflectionTestUtils.setField(underTest, FLAG_STOPPING_IN_PROGRESS, true);

      underTest.triggerDatabaseCheck();

      verifyNoInteractions(dbHealthCheck);
      verifyNoInteractions(container);
    }

    @Test
    void triggerDatabaseCheck_whenListenerAlreadyInactive_should_doNothing() {
      setListenerInactive();

      underTest.triggerDatabaseCheck();

      verifyNoInteractions(dbHealthCheck);
      verifyNoInteractions(container);
    }

    @Test
    void triggerDatabaseCheck_whenDbIsDown_should_stopRunningContainer()
        throws InterruptedException {
      final CountDownLatch stopLatch = new CountDownLatch(1);
      when(dbHealthCheck.isValid()).thenReturn(false);
      when(container.isRunning()).thenReturn(true);
      doAnswer(
              inv -> {
                stopLatch.countDown();
                return null;
              })
          .when(container)
          .stop();

      underTest.triggerDatabaseCheck();

      assertThat(stopLatch.await(100, TimeUnit.MILLISECONDS)).isTrue();
      verify(container).stop();
    }

    @Test
    void triggerDatabaseCheck_whenCalledTwiceSequentially_shouldStopOnlyOnce()
        throws InterruptedException {
      final CountDownLatch blockLatch = new CountDownLatch(1);
      final CountDownLatch stopLatch = new CountDownLatch(1);
      when(dbHealthCheck.isValid()).thenReturn(false);
      when(container.isRunning()).thenReturn(true);
      doAnswer(
              inv -> {
                assertThat(blockLatch.await(100, TimeUnit.MILLISECONDS)).isTrue();
                stopLatch.countDown();
                return null;
              })
          .when(container)
          .stop();

      underTest.triggerDatabaseCheck();
      underTest.triggerDatabaseCheck();
      blockLatch.countDown();

      assertThat(stopLatch.await(100, TimeUnit.MILLISECONDS)).isTrue();
      verify(container, times(1)).stop();
    }
  }

  @Nested
  class CheckDatabaseReconnection {

    @Test
    void checkDatabaseReconnection_whenListenerActive_should_doNothing() {
      underTest.checkDatabaseReconnection();

      verifyNoInteractions(dbHealthCheck);
      verifyNoInteractions(container);
    }

    @Test
    void checkDatabaseReconnection_whenListenerInactiveButDbStillDown_should_notStartContainers() {
      setListenerInactive();
      when(dbHealthCheck.isValid()).thenReturn(false);

      underTest.checkDatabaseReconnection();

      verify(container, never()).start();
    }

    @Test
    void checkDatabaseReconnection_whenListenerInactiveAndDbUp_should_startStoppedContainer() {
      setListenerInactive();
      when(dbHealthCheck.isValid()).thenReturn(true);
      when(container.isRunning()).thenReturn(false);

      underTest.checkDatabaseReconnection();

      verify(container).start();
    }
  }
}
