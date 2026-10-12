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

import inetsoft.sree.SreeEnv;
import inetsoft.storage.*;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77798: the FileAuthorizationProvider writers must report a failed storage write to the
 * caller instead of only logging it, so that a failed grant or revoke is not reported as saved.
 * The org cleanup and the legacy-storage migration in init() stay best-effort per entry.
 *
 * <p>The tests build a fresh Permission for every write: the test cluster's replicated map
 * returns the stored instance, so mutating a read permission would make a failed write look
 * applied.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
public class FileAuthorizationProviderWriteFailureTest {
   @BeforeEach
   void setUp() throws Exception {
      FileAuthenticationProvider authc = new FileAuthenticationProvider();
      AuthenticationChain authcChain = new AuthenticationChain();
      authcChain.setProviders(List.of(authc));
      authcChain.saveConfiguration();

      FileAuthorizationProvider authz = new FileAuthorizationProvider();
      authz.setProviderName("Primary");
      AuthorizationChain authzChain = new AuthorizationChain();
      authzChain.setProviders(List.of(authz));
      authzChain.saveConfiguration();

      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.save();
      SecurityEngine.getSecurity().init();

      chain = (AuthorizationChain)
         SecurityEngine.getSecurity().getSecurityProvider().getAuthorizationProvider();
      provider = (FileAuthorizationProvider) chain.getProviders().get(0);
      // opens the storage
      provider.getPermission(ResourceType.VIEWSHEET, "probe", ORG);
   }

   @AfterEach
   void tearDown() {
      if(provider != null) {
         provider.tearDown();
      }

      SreeEnv.remove("security.enabled");
   }

   @Test
   void setPermissionStringThrowsWhenPutFails() throws Exception {
      withFailing(true, false, () -> {
         MessageException e = assertThrows(MessageException.class,
            () -> provider.setPermission(ResourceType.VIEWSHEET, "vsA", grant("alice"), ORG));
         assertMaySaveMessage(e, "VIEWSHEET", "vsA");
      });

      assertNull(provider.getPermission(ResourceType.VIEWSHEET, "vsA", ORG));
   }

   @Test
   void setPermissionIdentityThrowsWhenPutFails() throws Exception {
      IdentityID id = new IdentityID("bob", ORG);

      withFailing(true, false, () -> {
         MessageException e = assertThrows(MessageException.class,
            () -> provider.setPermission(ResourceType.SECURITY_USER, id, grant("alice"), ORG));
         assertMaySaveMessage(e, "SECURITY_USER", "bob");
      });

      assertNull(provider.getPermission(ResourceType.SECURITY_USER, id, ORG));
   }

   @Test
   void removePermissionStringThrowsWhenRemoveFails() throws Exception {
      provider.setPermission(ResourceType.VIEWSHEET, "vsC", grant("mallory"), ORG);

      withFailing(false, true, () -> {
         MessageException e = assertThrows(MessageException.class,
            () -> provider.removePermission(ResourceType.VIEWSHEET, "vsC", ORG));
         assertMaySaveMessage(e, "VIEWSHEET", "vsC");
      });

      assertTrue(granted(provider.getPermission(ResourceType.VIEWSHEET, "vsC", ORG), "mallory"));
   }

   @Test
   void removePermissionIdentityThrowsWhenRemoveFails() throws Exception {
      IdentityID id = new IdentityID("bob", ORG);
      provider.setPermission(ResourceType.SECURITY_USER, id, grant("mallory"), ORG);

      withFailing(false, true, () -> {
         MessageException e = assertThrows(MessageException.class,
            () -> provider.removePermission(ResourceType.SECURITY_USER, id, ORG));
         assertMaySaveMessage(e, "SECURITY_USER", "bob");
      });

      assertTrue(granted(provider.getPermission(ResourceType.SECURITY_USER, id, ORG), "mallory"));
   }

   // the revoke the EM permission pane and REST make: a narrower permission put through the
   // security engine
   @Test
   void narrowingRevokeThroughSecurityEngineThrowsWhenPutFails() throws Exception {
      Permission both = new Permission();
      both.setUserGrantsForOrg(ResourceAction.READ, Set.of("mallory", "alice"), ORG);
      chain.setPermission(ResourceType.VIEWSHEET, "vsE", both, ORG);

      withFailing(true, false, () -> assertThrows(MessageException.class,
         () -> SecurityEngine.getSecurity().setPermission(
            ResourceType.VIEWSHEET, "vsE", grant("alice"))));

      assertTrue(granted(chain.getPermission(ResourceType.VIEWSHEET, "vsE", ORG), "mallory"));
   }

   @Test
   void nullPermissionRevokeThroughChainThrowsWhenRemoveFails() throws Exception {
      chain.setPermission(ResourceType.VIEWSHEET, "vsF", grant("mallory"), ORG);

      withFailing(false, true, () -> assertThrows(MessageException.class,
         () -> chain.setPermission(ResourceType.VIEWSHEET, "vsF", null, ORG)));

      assertTrue(granted(chain.getPermission(ResourceType.VIEWSHEET, "vsF", ORG), "mallory"));
   }

   @Test
   void interruptedWriteKeepsTheInterruptFlag() throws Exception {
      KeyValueStorage<Permission> real = storage();
      KeyValueStorage<Permission> failing = mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(real));
      doReturn(new InterruptedFuture<>()).when(failing).put(anyString(), any());
      setStorage(failing);

      try {
         assertThrows(MessageException.class,
            () -> provider.setPermission(ResourceType.VIEWSHEET, "vsInt", grant("alice"), ORG));
         assertTrue(Thread.interrupted(), "the interrupt flag was not restored");
      }
      finally {
         Thread.interrupted();
         setStorage(real);
      }
   }

   // an org delete runs further cleanup after this call, so one failed remove must not stop
   // the other removes or escape
   @Test
   void cleanOrganizationFromPermissionsRemovesTheOthersWhenOneRemoveFails() throws Exception {
      String org = "cleanOrg";
      List<String> resources = List.of("vs1", "vs2", "vs3");

      for(String resource : resources) {
         provider.setPermission(ResourceType.VIEWSHEET, resource, grant("alice"), org);
      }

      KeyValueStorage<Permission> real = storage();
      KeyValueStorage<Permission> failing = mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(real));
      AtomicInteger removes = new AtomicInteger();
      List<String> failedKeys = new ArrayList<>();
      doAnswer(inv -> {
         String key = inv.getArgument(0);

         if(key.contains(":" + org + ":") && removes.incrementAndGet() == 2) {
            failedKeys.add(key);
            return CompletableFuture.failedFuture(new IOException("simulated write failure"));
         }

         return real.remove(key);
      }).when(failing).remove(anyString());
      setStorage(failing);

      try {
         assertDoesNotThrow(() -> provider.cleanOrganizationFromPermissions(org));
      }
      finally {
         setStorage(real);
      }

      assertEquals(3, removes.get(), "every permission of the organization was not attempted");
      assertEquals(1, failedKeys.size());
      long left = resources.stream()
         .filter(r -> provider.getPermission(ResourceType.VIEWSHEET, r, org) != null)
         .count();
      assertEquals(1, left, "only the permission whose remove failed should be left");
   }

   // init() runs the legacy-storage migration on the first call into the provider. Its re-puts
   // are best-effort per entry: a failed re-put must not escape init(), or from
   // authenticationChanged, which must never throw into the authentication listener chain
   @Test
   void legacyMigrationRePutFailureDoesNotEscapeInit() throws Exception {
      assertMigrationReturnsNormally(
         () -> provider.getPermission(ResourceType.VIEWSHEET, "legacyVs", ORG));
   }

   @Test
   void legacyMigrationRePutFailureDoesNotEscapeAuthenticationChanged() throws Exception {
      IdentityID alice = new IdentityID("alice", ORG);
      AuthenticationChangeEvent event = new AuthenticationChangeEvent(
         this, alice, null, ORG, ORG, Identity.USER, true);
      assertMigrationReturnsNormally(() -> provider.authenticationChanged(event));
   }

   private void assertMigrationReturnsNormally(Body call) throws Exception {
      KeyValueStorage<Permission> real = storage();
      real.removeAll(new HashSet<>(real.keys().toList())).get();
      // a legacy key has no organization part
      real.put("VIEWSHEET:legacyVs", grant("alice")).get();

      KeyValueStorage<Permission> failing = mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(real));
      AtomicInteger puts = new AtomicInteger();
      doAnswer(inv -> {
         puts.incrementAndGet();
         return CompletableFuture.failedFuture(new IOException("simulated write failure"));
      }).when(failing).put(anyString(), any());
      KeyValueStorageManager manager = mock(KeyValueStorageManager.class);
      doReturn(failing).when(manager).getStorage(eq("defaultSecurityPermissions"), any());

      // the next call into the provider opens the storage again, and so runs the migration
      setStorage(null);

      try(MockedStatic<KeyValueStorageManager> statics = mockStatic(KeyValueStorageManager.class)) {
         statics.when(KeyValueStorageManager::getInstance).thenReturn(manager);
         assertDoesNotThrow(call::run);
      }
      finally {
         setStorage(real);
      }

      assertTrue(puts.get() > 0, "the migration did not run");
   }

   private static void assertMaySaveMessage(MessageException e, String type, String resource) {
      assertTrue(e.getMessage().contains("may not have been saved"), e.getMessage());
      assertTrue(e.getMessage().contains(type), e.getMessage());
      assertTrue(e.getMessage().contains(resource), e.getMessage());
      assertNotNull(e.getCause());
   }

   private static Permission grant(String user) {
      Permission p = new Permission();
      p.setUserGrantsForOrg(ResourceAction.READ, Set.of(user), ORG);
      return p;
   }

   private static boolean granted(Permission p, String user) {
      return p != null && p.getUserGrants(ResourceAction.READ, ORG).stream()
         .anyMatch(i -> user.equals(i.getName()));
   }

   @SuppressWarnings("unchecked")
   private KeyValueStorage<Permission> storage() throws Exception {
      Field f = FileAuthorizationProvider.class.getDeclaredField("storage");
      f.setAccessible(true);
      return (KeyValueStorage<Permission>) f.get(provider);
   }

   private void setStorage(KeyValueStorage<Permission> s) throws Exception {
      Field f = FileAuthorizationProvider.class.getDeclaredField("storage");
      f.setAccessible(true);
      f.set(provider, s);
   }

   @SuppressWarnings("unchecked")
   private void withFailing(boolean failPut, boolean failRemove, Body body) throws Exception {
      KeyValueStorage<Permission> real = storage();
      KeyValueStorage<Permission> failing = mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(real));
      CompletableFuture<?> failed =
         CompletableFuture.failedFuture(new IOException("simulated write failure"));

      if(failPut) {
         doReturn(failed).when(failing).put(anyString(), any());
      }

      if(failRemove) {
         doReturn(failed).when(failing).remove(anyString());
      }

      setStorage(failing);

      try {
         body.run();
      }
      finally {
         setStorage(real);
      }
   }

   interface Body {
      void run() throws Exception;
   }

   private static final class InterruptedFuture<T> extends CompletableFuture<T> {
      @Override
      public T get(long timeout, TimeUnit unit) throws InterruptedException {
         throw new InterruptedException("simulated interrupt");
      }
   }

   private static final String ORG = Organization.getDefaultOrganizationID();
   private AuthorizationChain chain;
   private FileAuthorizationProvider provider;
}
