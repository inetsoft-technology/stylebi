/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.test;

/*
 * Bugs #77759, #77760. ConfigurationContextInitializer waits for the change events still queued
 * in the closed contexts before it installs the next context. When they do not run in time, the
 * test whose context is loaded fails, and only that test. Throwing from the initializer would
 * instead count as a failure to load the context configuration, and Spring would then skip the
 * later loads of the same configuration in every class that shares it.
 *
 * The first method holds its event thread past a short timeout. The error of the second method's
 * context load is taken from SreeHomeExtension before its beforeEach() fails the test with it,
 * and checked by the test. The third method's context, with the same configuration, must load.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.storage.BlobStorageTestSupport;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ExtendWith({ SpringExtension.class, ContextBoundaryEventBarrierTimeoutTest.TakeError.class })
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SreeHome
@Tag("core")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContextBoundaryEventBarrierTimeoutTest {
   @Autowired
   private DataSpace dataSpace;

   @Autowired
   private ApplicationContext applicationContext;

   @BeforeAll
   static void shortenTimeout() {
      BlobStorageTestSupport.setEventBarrierTimeout(1, TimeUnit.SECONDS);
   }

   @AfterAll
   static void restoreTimeout() {
      BlobStorageTestSupport.resetEventBarrierTimeout();
      RELEASE.countDown();
   }

   @Test
   @Order(1)
   void holdEventThreadPastTheTimeout() throws Exception {
      String path = SreeEnv.getPath("$(sree.home)/portal/barrier_timeout_hold.txt");
      CountDownLatch held = new CountDownLatch(1);
      dataSpace.addChangeListener(null, path, e -> {
         if(held.getCount() > 0) {
            held.countDown();

            try {
               RELEASE.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }
      });
      dataSpace.withOutputStream(null, path, out -> out.write('x'));
      assertTrue(held.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                 "the BlobStorageEvent thread was never held");
      firstContext = applicationContext;
   }

   @Test
   @Order(2)
   void timeoutFailsTheTestWhoseContextIsLoaded() {
      assumeTrue(firstContext != null, "runs after holdEventThreadPastTheTimeout only");
      String error = takenError;
      RELEASE.countDown();

      assertAll(
         () -> assertNotNull(error, "the timeout was not recorded"),
         () -> assertTrue(error != null && error.contains(getClass().getName()),
                          "the error must name the test class: " + error),
         () -> assertTrue(error != null && error.contains("BlobStorageEvent"),
                          "the error must show the event threads: " + error),
         () -> assertNotSame(firstContext, applicationContext));
   }

   @Test
   @Order(3)
   void sameConfigurationLoadsAfterTheTimeout() {
      assumeTrue(firstContext != null, "runs after holdEventThreadPastTheTimeout only");

      assertNull(takenError, "no timeout is expected for this context");
      assertTrue(((ConfigurableApplicationContext) applicationContext).isActive());
   }

   // the per-method context is loaded when the test instance is post-processed, before
   // SreeHomeExtension.beforeEach() fails the test with the recorded error
   static class TakeError implements TestInstancePostProcessor {
      @Override
      public void postProcessTestInstance(Object testInstance, ExtensionContext context) {
         takenError = SreeHomeExtension.takePendingBarrierError();
      }
   }

   private static final int TIMEOUT_SECONDS = 30;
   private static final CountDownLatch RELEASE = new CountDownLatch(1);

   private static volatile ApplicationContext firstContext;
   private static volatile String takenError;
}
