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
package inetsoft.sree.security;

import inetsoft.storage.KeyValueStorage;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79355: a chain reload publishes the new providers and then tears the old ones down, so an
 * old and a new File provider are live together and share the {@code KeyValueStorage} instances
 * that {@code KeyValueStorageManager} caches per id. {@code tearDown()} must release only what the
 * provider owns (its own listeners), not close those shared storages, or a stream or key listing
 * of the new provider that overlaps the old one's teardown comes back empty. Real storages and
 * providers, no mocks.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FileProviderSharedStorageTest {
   private static final String ORG = "sharedStorageOrg";
   private static final int ROUNDS = 3000;
   private static final long WAIT_SECONDS = 20L;

   // the listener fields of FileAuthenticationProvider, and the shared storage of each
   private static final Object[][] LISTENERS = {
      { "userCacheListener", "defaultSecurityUsers" },
      { "groupCacheListener", "defaultSecurityGroups" },
      { "roleCacheListener", "defaultSecurityRoles" }
   };

   private final AtomicBoolean stop = new AtomicBoolean();
   private Thread churner;

   @AfterEach
   void stopChurner() throws Exception {
      stop.set(true);

      if(churner != null) {
         churner.join(WAIT_SECONDS * 1000L);
         assertFalse(churner.isAlive(), "the churn thread is still running");
      }
   }

   // a File authentication provider that is live keeps listing every user while old providers
   // are initialized and torn down next to it

   @Test
   void usersAreNotShortWhileOldProvidersTearDown() throws Exception {
      FileAuthenticationProvider live = new FileAuthenticationProvider();

      try {
         for(int i = 0; i < 3; i++) {
            live.addUser(new FSUser(new IdentityID("user" + i, ORG)));
         }

         int expected = live.getUsers().length;
         assertTrue(expected >= 3);
         churn(() -> {
            FileAuthenticationProvider old = new FileAuthenticationProvider();
            old.getUsers();
            old.tearDown();
         });

         int shortReads = 0;

         for(int i = 0; i < ROUNDS; i++) {
            if(live.getUsers().length != expected) {
               shortReads++;
            }
         }

         assertEquals(0, shortReads, "getUsers() calls that came back short");
      }
      finally {
         stop.set(true);
         live.tearDown();
      }
   }

   // the same for the authorization provider

   @Test
   void permissionsAreNotShortWhileOldProvidersTearDown() throws Exception {
      FileAuthorizationProvider live = new FileAuthorizationProvider();

      try {
         for(int i = 0; i < 5; i++) {
            live.setPermission(ResourceType.VIEWSHEET, "sales" + i, new Permission(), ORG);
         }

         int expected = live.getPermissions().size();
         assertTrue(expected >= 5);
         churn(() -> {
            FileAuthorizationProvider old = new FileAuthorizationProvider();
            old.getPermissions();
            old.tearDown();
         });

         int shortReads = 0;

         for(int i = 0; i < ROUNDS; i++) {
            if(live.getPermissions().size() != expected) {
               shortReads++;
            }
         }

         assertEquals(0, shortReads, "getPermissions() calls that came back short");
      }
      finally {
         stop.set(true);
         live.tearDown();
      }
   }

   // tearDown() removes the provider's own cache listeners from the shared storages, so a
   // provider that was replaced is not invalidated by every later change, and it leaves the
   // listeners of the provider that replaced it alone

   @Test
   void tearDownRemovesOnlyItsOwnListeners() throws Exception {
      CountingAuthn old = new CountingAuthn();
      CountingAuthn live = new CountingAuthn();
      FileAuthenticationProvider writer = new FileAuthenticationProvider();

      try {
         old.getUsers();
         live.getUsers();
         old.tearDown();
         int oldCleared = old.cleared.get();
         int liveCleared = live.cleared.get();

         writer.addUser(new FSUser(new IdentityID("listener-user", ORG)));

         long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);

         while(live.cleared.get() == liveCleared && System.nanoTime() < deadline) {
            Thread.onSpinWait();
         }

         assertTrue(live.cleared.get() > liveCleared,
                    "the live provider's listener must still fire after the old one is torn down");
         // the events of one change are delivered together: the old provider has had its chance
         assertEquals(oldCleared, old.cleared.get(),
                      "the torn down provider's listener must no longer fire");
      }
      finally {
         writer.removeUser(new IdentityID("listener-user", ORG));
         writer.tearDown();
         live.tearDown();
         old.tearDown();
      }
   }

   // a reader that still holds the replaced provider reads after its tearDown(): that must
   // neither fail nor add the dead provider's listeners to the shared storages again. This
   // checks the registration itself. Counting the clearCache() calls of the provider does not
   // work: the storages deliver a change on another thread, so a change made just before the
   // provider registered can still reach its listener after tearDown() removed it

   @Test
   void readAfterTearDownOnTheOldProviderDoesNotFailOrReAddListeners() throws Exception {
      FileAuthenticationProvider old = new FileAuthenticationProvider();
      FileAuthenticationProvider writer = new FileAuthenticationProvider();
      IdentityID id = new IdentityID("after-teardown-user", ORG);

      try {
         writer.addUser(new FSUser(id));
         old.getUsers();

         for(Object[] registration : LISTENERS) {
            assertTrue(isRegistered(old, (String) registration[0], (String) registration[1]),
                       "a provider that has read must listen to " + registration[1]);
         }

         old.tearDown();

         assertNotNull(old.getUser(id), "a read on the torn down provider must still work");
         assertTrue(old.getUsers().length > 0);

         for(Object[] registration : LISTENERS) {
            assertFalse(isRegistered(old, (String) registration[0], (String) registration[1]),
                        "a read after tearDown must not register the old provider's listener " +
                        "on " + registration[1] + " again");
         }
      }
      finally {
         writer.removeUser(id);
         writer.tearDown();
         old.tearDown();
      }
   }

   /**
    * Whether the cache listener held by the field {@code listenerField} of the provider is
    * registered with the shared storage {@code storageId}.
    */
   private static boolean isRegistered(FileAuthenticationProvider provider, String listenerField,
                                       String storageId) throws Exception
   {
      Field field = FileAuthenticationProvider.class.getDeclaredField(listenerField);
      field.setAccessible(true);
      Object listener = field.get(provider);
      KeyValueStorage<?> storage = KeyValueStorageManager.getInstance().peekStorage(storageId);
      assertNotNull(storage, "the storage " + storageId + " has not been created");
      Field listeners = storage.getClass().getDeclaredField("listeners");
      listeners.setAccessible(true);
      return ((Set<?>) listeners.get(storage)).contains(listener);
   }

   private void churn(Runnable round) {
      CountDownLatch started = new CountDownLatch(1);
      churner = new Thread(() -> {
         started.countDown();

         while(!stop.get()) {
            round.run();
         }
      }, "churn");
      churner.setDaemon(true);
      churner.start();

      try {
         assertTrue(started.await(WAIT_SECONDS, TimeUnit.SECONDS));
      }
      catch(InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }

   private static final class CountingAuthn extends FileAuthenticationProvider {
      final AtomicInteger cleared = new AtomicInteger();

      @Override
      public void clearCache() {
         cleared.incrementAndGet();
         super.clearCache();
      }
   }
}
