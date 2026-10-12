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
package inetsoft.util;

import inetsoft.report.internal.license.ClaimedLicenseListener;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78007: a {@link ThreadPool} whose owner never disposes it outlives its Spring context. Its
 * idle worker calls {@code cleanUp()} every 60 s, which must not reach {@link LicenseManager}, or
 * the worker dies with a {@code ShutdownException} once the context is closed. The claimed license
 * listener is removed on {@link ThreadPool#dispose()} instead, which must tolerate a closed context.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@TestPropertySource(properties = "mock.license.manager=true")
@SreeHome
@Tag("core")
class ThreadPoolLicenseListenerTest {
   @BeforeEach
   void stubLicenseManager() {
      reset(licenseManager);
      when(licenseManager.calculateThreadPoolSize(anyInt(), any(), anyInt()))
         .thenReturn(new int[] { 1, 2 });
   }

   @Test
   void idleCleanUpDoesNotReachLicenseManagerWithoutContext() throws Exception {
      ThreadPool pool = new ThreadPool("bug78007a", 1, "bug78007.hard", 2);
      verify(licenseManager).addClaimedLicenseListener(any());

      try {
         // skip the 5 s hold in cleanUp(); the license call came before it
         setField(pool, "lastCleanup", System.currentTimeMillis());
         Method cleanUp = ThreadPool.class.getDeclaredMethod("cleanUp");
         cleanUp.setAccessible(true);
         withoutSpringContext(() -> invoke(cleanUp, pool));
         verify(licenseManager, never()).removeClaimedLicenseListener(any());
      }
      finally {
         pool.dispose();
      }
   }

   @Test
   void disposeRemovesClaimedLicenseListener() {
      ThreadPool pool = new ThreadPool("bug78007b", 1, "bug78007.hard", 2);
      ArgumentCaptor<ClaimedLicenseListener> listener =
         ArgumentCaptor.forClass(ClaimedLicenseListener.class);
      verify(licenseManager).addClaimedLicenseListener(listener.capture());

      pool.dispose();
      verify(licenseManager).removeClaimedLicenseListener(listener.getValue());
   }

   @Test
   void disposeToleratesClosedContext() throws Exception {
      ThreadPool pool = new ThreadPool("bug78007c", 1, "bug78007.hard", 2);
      withoutSpringContext(pool::dispose);
      assertTrue((Boolean) getField(pool, "disposed"));
   }

   private static void withoutSpringContext(Runnable action) {
      ConfigurationContext context = ConfigurationContext.getContext();
      ApplicationContext applicationContext = context.getApplicationContext();
      context.setApplicationContext(null);

      try {
         assertDoesNotThrow(action::run);
      }
      finally {
         context.setApplicationContext(applicationContext);
      }
   }

   private static void invoke(Method method, Object target) {
      try {
         method.invoke(target);
      }
      catch(InvocationTargetException ex) {
         if(ex.getCause() instanceof RuntimeException runtime) {
            throw runtime;
         }

         throw new RuntimeException(ex.getCause());
      }
      catch(IllegalAccessException ex) {
         throw new RuntimeException(ex);
      }
   }

   private static void setField(Object target, String name, Object value) throws Exception {
      Field field = ThreadPool.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
   }

   private static Object getField(Object target, String name) throws Exception {
      Field field = ThreadPool.class.getDeclaredField(name);
      field.setAccessible(true);
      return field.get(target);
   }

   @Autowired
   private LicenseManager licenseManager;
}
