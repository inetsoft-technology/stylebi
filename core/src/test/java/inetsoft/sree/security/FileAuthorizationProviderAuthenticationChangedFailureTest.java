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

import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.storage.KeyValueStorage;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77799: {@link FileAuthorizationProvider#authenticationChanged} must keep cleaning up the
 * remaining permission entries when one entry's storage put fails, and must not throw.
 *
 * The identity events are fired through the real provider methods (removeUser, setUser rename,
 * removeGroup), so the whole listener chain runs. The engine's authorization storage is swapped
 * for a delegating mock that fails one put. The 2nd put fails in most cases, so that "the cleanup
 * stops at the failure" (old code: the 3rd entry is never visited) is told apart from "only the
 * failed entry is lost". Copy-on-read storage is used because the plain MockCluster hands out live
 * references, and the in-place setGrants would hide a failed put. Each assertion is placed right
 * after the event, before any further identity change.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  PermissionMatrixOrgLifecycleTest.CopyOnReadClusterConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FileAuthorizationProviderAuthenticationChangedFailureTest {
   private static final String ORG = "o77799";
   private static final String[] RES = { "r77799/a", "r77799/b", "r77799/c" };

   private SecurityTestDataBuilder builder;
   private FileAuthorizationProvider authz;
   private FileAuthenticationProvider authc;
   private KeyValueStorage<Permission> realStorage;
   private final AtomicInteger puts = new AtomicInteger();
   private final List<String> putKeys = Collections.synchronizedList(new ArrayList<>());
   private AuthenticationChangeListener extraListener;

   @BeforeEach
   void setUp() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .withMultiTenant("true")
         .addOrg("O77799", ORG)
         .addUser("alice", ORG, "pw")
         .addUser("carol", ORG, "pw")
         .addUser("dave", ORG, "pw")
         .addGroup("g1", ORG)
         .addGroup("g2", ORG);

      for(String r : RES) {
         builder.grantPermission(ResourceType.VIEWSHEET, r, ResourceAction.READ, "alice", Identity.USER, ORG)
                .grantPermission(ResourceType.VIEWSHEET, r, ResourceAction.READ, "carol", Identity.USER, ORG)
                .grantPermission(ResourceType.VIEWSHEET, r, ResourceAction.READ, "g1", Identity.GROUP, ORG)
                .grantPermission(ResourceType.VIEWSHEET, r, ResourceAction.READ, "g2", Identity.GROUP, ORG)
                .markPermissionEdited(ResourceType.VIEWSHEET, r, ORG);
      }

      builder.setup();
      SecurityEngine engine = SecurityEngine.getSecurity();
      // the engine's providers are not the builder's instances (they share the storage)
      authz = (FileAuthorizationProvider) engine.getAuthorizationChain().get().getProviders().get(0);
      authc = (FileAuthenticationProvider) engine.getAuthenticationChain().get().getProviders().get(0);
   }

   @AfterEach
   void tearDown() throws Exception {
      Thread.interrupted();

      if(extraListener != null) {
         SecurityEngine.getSecurity().removeAuthenticationChangeListener(extraListener);
      }

      if(realStorage != null) {
         setStorage(realStorage);
      }

      for(String r : RES) {
         authz.removePermission(ResourceType.VIEWSHEET, r, ORG);
      }

      builder.teardown();
   }

   // Control: without a failure every entry is cleaned and the other grantees are untouched.
   @Test
   void removeUser_noFailure_cleansEveryEntry() {
      assertEquals(3, granted("alice", Identity.USER));

      authc.removeUser(new IdentityID("alice", ORG));

      assertEquals(0, granted("alice", Identity.USER));
      assertEquals(3, granted("carol", Identity.USER));
   }

   // Remove: the 2nd put fails; the 3rd entry must still be cleaned, and nothing is thrown.
   @Test
   void removeUser_secondPutFails_otherEntriesAreStillCleaned() throws Exception {
      assertEquals(3, granted("alice", Identity.USER));
      failPut(2, () -> CompletableFuture.failedFuture(new IOException("simulated write failure")));
      IdentityID alice = new IdentityID("alice", ORG);

      assertDoesNotThrow(() -> authc.removeUser(alice));
      setStorage(realStorage);

      assertEquals(3, puts.get(), "every changed entry must be put, puts=" + putKeys);
      assertEquals(1, granted("alice", Identity.USER),
                   "only the entry whose put failed may keep the grant, puts=" + putKeys);
      assertEquals(3, granted("carol", Identity.USER));
      assertNull(authc.getUser(alice));
   }

   // Remove, then re-create a user with the same name: it may inherit at most the one entry
   // whose put failed (before the fix: every entry after the failure, 3/3 with the 1st put failing).
   @Test
   void removeUser_firstPutFails_reCreatedSameNamedUserInheritsOnlyFailedEntry() throws Exception {
      failPut(1, () -> CompletableFuture.failedFuture(new IOException("simulated write failure")));
      IdentityID alice = new IdentityID("alice", ORG);

      assertDoesNotThrow(() -> authc.removeUser(alice));
      setStorage(realStorage);
      assertEquals(1, granted("alice", Identity.USER), "puts=" + putKeys);

      authc.addUser(new FSUser(alice));
      SRPrincipal newAlice = builder.principalOf("alice", ORG);
      SRPrincipal dave = builder.principalOf("dave", ORG);
      int newAliceAllowed = 0;
      int daveAllowed = 0;

      for(String r : RES) {
         if(SecurityEngine.getSecurity().checkPermission(newAlice, ResourceType.VIEWSHEET, r, ResourceAction.READ)) {
            newAliceAllowed++;
         }

         if(SecurityEngine.getSecurity().checkPermission(dave, ResourceType.VIEWSHEET, r, ResourceAction.READ)) {
            daveAllowed++;
         }
      }

      assertEquals(1, newAliceAllowed, "re-created alice may only inherit the failed entry");
      assertEquals(0, daveAllowed);
   }

   // Rename: the 2nd put fails; the other two entries must move to the new name.
   @Test
   void renameUser_secondPutFails_otherEntriesMoveToNewName() throws Exception {
      failPut(2, () -> CompletableFuture.failedFuture(new IOException("simulated write failure")));
      IdentityID alice = new IdentityID("alice", ORG);
      IdentityID bob = new IdentityID("bob", ORG);

      assertDoesNotThrow(() -> authc.setUser(alice, new FSUser(bob)));
      setStorage(realStorage);

      assertEquals(2, granted("bob", Identity.USER), "puts=" + putKeys);
      assertEquals(1, granted("alice", Identity.USER), "puts=" + putKeys);
      assertEquals(3, granted("carol", Identity.USER));
   }

   // Group remove: the same per-entry cleanup for GROUP events.
   @Test
   void removeGroup_secondPutFails_otherEntriesAreStillCleaned() throws Exception {
      assertEquals(3, granted("g1", Identity.GROUP));
      failPut(2, () -> CompletableFuture.failedFuture(new IOException("simulated write failure")));

      assertDoesNotThrow(() -> authc.removeGroup(new IdentityID("g1", ORG)));
      setStorage(realStorage);

      assertEquals(1, granted("g1", Identity.GROUP), "puts=" + putKeys);
      assertEquals(3, granted("g2", Identity.GROUP));
   }

   // The authorization listener must not throw: SecurityEngine's own change listeners (which run
   // after it in AuthenticationChain.changeDelegate) must still receive the event.
   @Test
   void removeUser_putFails_laterListenerStillRuns() throws Exception {
      List<AuthenticationChangeEvent> events = new CopyOnWriteArrayList<>();
      extraListener = events::add;
      SecurityEngine.getSecurity().addAuthenticationChangeListener(extraListener);
      failPut(1, () -> CompletableFuture.failedFuture(new IOException("simulated write failure")));
      IdentityID alice = new IdentityID("alice", ORG);

      authc.removeUser(alice);
      setStorage(realStorage);

      assertTrue(events.stream().anyMatch(e -> alice.equals(e.getOldID()) && e.isRemoved()),
                 "SecurityEngine listeners must still be notified, got " + events);
   }

   // A timed-out put (a hung backend): the remaining puts are still submitted, but are not waited
   // on, so the cleanup waits for at most one timeout while the provider lock is held.
   @Test
   void removeUser_firstPutTimesOut_remainingPutsSubmittedButNotAwaited() throws Exception {
      AtomicInteger laterGets = new AtomicInteger();
      failPut(1, TimingOutFuture::new, laterGets);
      IdentityID alice = new IdentityID("alice", ORG);

      assertDoesNotThrow(() -> authc.removeUser(alice));
      setStorage(realStorage);

      assertEquals(3, puts.get(), "every changed entry must still be put, puts=" + putKeys);
      assertEquals(0, laterGets.get(), "puts after a timeout must not be waited on");
      // the timed-out put never reached the store in this simulation; the others landed
      assertEquals(1, granted("alice", Identity.USER), "puts=" + putKeys);
      assertEquals(3, granted("carol", Identity.USER));
   }

   // An interrupted wait: the remaining entries are still cleaned, and the interrupt flag is
   // restored for the caller.
   @Test
   void removeUser_firstWaitInterrupted_remainingEntriesCleanedAndFlagRestored() throws Exception {
      failPut(1, InterruptedFuture::new);
      IdentityID alice = new IdentityID("alice", ORG);

      assertDoesNotThrow(() -> authc.removeUser(alice));
      boolean flag = Thread.interrupted();
      setStorage(realStorage);

      assertTrue(flag, "the interrupt flag must be restored");
      assertEquals(3, puts.get(), "puts=" + putKeys);
      assertEquals(1, granted("alice", Identity.USER), "puts=" + putKeys);
   }

   private long granted(String name, int identityType) {
      return Arrays.stream(RES)
         .map(r -> authz.getPermission(ResourceType.VIEWSHEET, r, ORG))
         .filter(p -> p != null && p.getGrants(ResourceAction.READ, identityType, ORG).stream()
            .anyMatch(pi -> name.equals(pi.getName()) && ORG.equals(pi.getOrganizationID())))
         .count();
   }

   private void failPut(int failingPut, Supplier<Future<Permission>> failure) throws Exception {
      failPut(failingPut, failure, null);
   }

   /**
    * Swaps the engine's authorization storage for a delegating mock whose {@code failingPut}-th
    * put returns {@code failure} without writing; every other put is delegated. When
    * {@code laterGets} is given, the futures of the delegated puts after the failing one count
    * their {@code get} calls in it.
    */
   @SuppressWarnings({ "unchecked", "rawtypes" })
   private void failPut(int failingPut, Supplier<Future<Permission>> failure,
                        AtomicInteger laterGets) throws Exception
   {
      authz.getPermission(ResourceType.VIEWSHEET, RES[0], ORG); // init()
      Field f = FileAuthorizationProvider.class.getDeclaredField("storage");
      f.setAccessible(true);
      realStorage = (KeyValueStorage<Permission>) f.get(authz);
      KeyValueStorage spy = Mockito.mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(realStorage));
      Mockito.doAnswer(inv -> {
         int n = puts.incrementAndGet();
         putKeys.add(inv.getArgument(0));

         if(n == failingPut) {
            return failure.get();
         }

         Future<Permission> result = realStorage.put(inv.getArgument(0), inv.getArgument(1));

         if(laterGets != null && n > failingPut) {
            result.get(10L, TimeUnit.SECONDS); // let the write land before the test reads it
            return new CountingFuture(result, laterGets);
         }

         return result;
      }).when(spy).put(ArgumentMatchers.anyString(), ArgumentMatchers.any());
      f.set(authz, spy);
   }

   private void setStorage(KeyValueStorage<Permission> storage) throws Exception {
      Field f = FileAuthorizationProvider.class.getDeclaredField("storage");
      f.setAccessible(true);
      f.set(authz, storage);
   }

   private abstract static class FailingFuture implements Future<Permission> {
      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
         return false;
      }

      @Override
      public boolean isCancelled() {
         return false;
      }

      @Override
      public boolean isDone() {
         return false;
      }

      @Override
      public Permission get() throws InterruptedException, ExecutionException {
         throw new UnsupportedOperationException("untimed get is not expected");
      }
   }

   private static final class TimingOutFuture extends FailingFuture {
      @Override
      public Permission get(long timeout, TimeUnit unit) throws TimeoutException {
         throw new TimeoutException("simulated put timeout");
      }
   }

   private static final class InterruptedFuture extends FailingFuture {
      @Override
      public Permission get(long timeout, TimeUnit unit) throws InterruptedException {
         // Future.get clears the flag when it throws InterruptedException
         throw new InterruptedException("simulated interrupt");
      }
   }

   private static final class CountingFuture implements Future<Permission> {
      CountingFuture(Future<Permission> delegate, AtomicInteger gets) {
         this.delegate = delegate;
         this.gets = gets;
      }

      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
         return delegate.cancel(mayInterruptIfRunning);
      }

      @Override
      public boolean isCancelled() {
         return delegate.isCancelled();
      }

      @Override
      public boolean isDone() {
         return delegate.isDone();
      }

      @Override
      public Permission get() throws InterruptedException, ExecutionException {
         gets.incrementAndGet();
         return delegate.get();
      }

      @Override
      public Permission get(long timeout, TimeUnit unit)
         throws InterruptedException, ExecutionException, TimeoutException
      {
         gets.incrementAndGet();
         return delegate.get(timeout, unit);
      }

      private final Future<Permission> delegate;
      private final AtomicInteger gets;
   }
}
