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
package inetsoft.web.admin.security.user;


/*
 * Bug #79867: createOrganization() chose the next free "organization<i>" id and registered it
 * without mutual exclusion, so two concurrent requests picked the same id and the later add
 * overwrote the earlier one. Both requests must now get distinct ids and both organizations
 * must exist.
 */

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.util.ThreadContext;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.general.model.LocalizationSettingsModel;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class UserTreeServiceConcurrentCreateOrganizationTest {
   @BeforeEach
   void setUp() {
      stored = ConcurrentHashMap.newKeySet();
      addCalls = Collections.synchronizedList(new ArrayList<>());
      firstInAdd = new CountDownLatch(1);
      secondSelecting = new CountDownLatch(1);
      firstThread = null;

      provider = mock(EditableAuthenticationProvider.class, withSettings().lenient());
      when(provider.getGroups()).thenReturn(new IdentityID[0]);
      when(provider.getRoles()).thenReturn(new IdentityID[0]);
      when(provider.getUsers()).thenReturn(new IdentityID[0]);
      doAnswer(inv -> {
         String id = ((Organization) inv.getArgument(0)).getId();
         addCalls.add(id);

         synchronized(this) {
            if(firstThread == null) {
               firstThread = Thread.currentThread();
            }
         }

         if(Thread.currentThread() == firstThread) {
            firstInAdd.countDown();
            // lets the second request reach the id selection, a correct fix keeps it out, so the
            // wait is bounded
            secondSelecting.await(1500, TimeUnit.MILLISECONDS);
         }

         stored.add(id);
         return true;
      }).when(provider).addOrganization(any());

      AuthenticationProviderService providerService =
         mock(AuthenticationProviderService.class, withSettings().lenient());
      when(providerService.getProviderByName("Primary")).thenReturn(provider);

      SecurityProvider securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityProvider.getOrganizationIDs()).thenAnswer(inv -> {
         if(firstThread != null && Thread.currentThread() != firstThread) {
            secondSelecting.countDown();
         }

         List<String> ids = new ArrayList<>(stored);
         ids.add("host-org");
         return ids.toArray(new String[0]);
      });
      when(securityProvider.getOrganizationNames()).thenReturn(new String[] { "Host Organization" });
      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      LocalizationSettingsService localizationService =
         mock(LocalizationSettingsService.class, withSettings().lenient());
      when(localizationService.getModel()).thenReturn(mock(LocalizationSettingsModel.class));

      principal = mock(Principal.class, withSettings().lenient());
      when(principal.getName()).thenReturn(new IdentityID("admin", "host-org").convertToKey());

      lockToUse = new ReentrantLock();
      service = new UserTreeService(
         providerService, null, mock(IdentityService.class), localizationService, securityEngine,
         mock(IdentityThemeService.class), null, null, null, mock(LicenseManager.class), null, null,
         mock(CustomThemesManager.class), null, null, null, null) {
         @Override
         java.util.concurrent.locks.Lock createOrganizationLock() {
            return lockToUse;
         }
      };
   }

   @Test
   void concurrentAutoIdCreates_getDistinctIds() throws Exception {
      ExecutorService pool = Executors.newFixedThreadPool(2);

      try {
         Future<String> first = pool.submit(this::create);
         assertTrue(firstInAdd.await(10, TimeUnit.SECONDS), "first request never reached add");
         Future<String> second = pool.submit(this::create);

         String id1 = first.get(20, TimeUnit.SECONDS);
         String id2 = second.get(20, TimeUnit.SECONDS);

         assertNotEquals(id1, id2, "both requests were given the same organization id");
         assertEquals(2, addCalls.size());
         assertEquals(new HashSet<>(Arrays.asList(id1, id2)), stored);
      }
      finally {
         pool.shutdownNow();
      }
   }

   // static mocks are thread-local, so each request thread registers its own
   private String create() {
      try(MockedStatic<SUtil> sUtil =
             mockStatic(SUtil.class, withSettings().strictness(Strictness.LENIENT));
          MockedStatic<Audit> audit =
             mockStatic(Audit.class, withSettings().strictness(Strictness.LENIENT));
          MockedStatic<OrganizationManager> orgManager =
             mockStatic(OrganizationManager.class, withSettings().strictness(Strictness.LENIENT)))
      {
         sUtil.when(() -> SUtil.getActionRecord(any(Principal.class), anyString(), any(), anyString()))
            .thenReturn(mock(ActionRecord.class));
         audit.when(Audit::getInstance).thenReturn(mock(Audit.class));
         orgManager.when(OrganizationManager::getInstance)
            .thenReturn(mock(OrganizationManager.class, withSettings().lenient()));

         EditOrganizationPaneModel model =
            service.createOrganization(null, "Primary", null, null, principal, null);
         return model.id();
      }
   }

   @Test
   void lockFailure_propagatesWithoutUnlock_andRestoresContext() {
      java.util.concurrent.locks.Lock failing = mock(java.util.concurrent.locks.Lock.class);
      doThrow(new IllegalStateException("lock failed")).when(failing).lock();
      lockToUse = failing;
      Principal marker = mock(Principal.class);
      Principal before = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(marker);

      try {
         IllegalStateException e = assertThrows(IllegalStateException.class, this::create);
         assertEquals("lock failed", e.getMessage());
         verify(failing, never()).unlock();
         assertSame(marker, ThreadContext.getContextPrincipal());
      }
      finally {
         ThreadContext.setContextPrincipal(before);
      }
   }

   private Set<String> stored;
   private List<String> addCalls;
   private CountDownLatch firstInAdd;
   private CountDownLatch secondSelecting;
   private volatile Thread firstThread;
   private EditableAuthenticationProvider provider;
   private Principal principal;
   private UserTreeService service;
   private java.util.concurrent.locks.Lock lockToUse;
}
