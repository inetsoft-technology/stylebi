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
package inetsoft.web.admin.content.repository;

import inetsoft.mv.MVDef;
import inetsoft.mv.MVManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XPrincipal;
import org.junit.jupiter.api.*;
import org.mockito.AdditionalAnswers;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80511: with no scheduler running, a background MVSupportService.createMV must register
 * the definitions on the calling thread, before the hand-off to the pool, so a read-back right
 * after it returns (existsInOrg) never races the pool thread's registration.
 */
@Tag("core")
class MVSupportServiceCreateRegistrationTest {
   private static final String ORG = "orga";

   private final Map<String, MVDef> registered = new ConcurrentHashMap<>();
   private final Map<String, String> addThreads = new ConcurrentHashMap<>();
   private final CountDownLatch releasePool = new CountDownLatch(1);
   private final CountDownLatch poolFinished = new CountDownLatch(1);
   private Thread callerThread;
   private MVManager mvManager;
   private MVSupportService service;
   private Principal principal;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   @BeforeEach
   @SuppressWarnings("unchecked")
   void setUp() {
      callerThread = Thread.currentThread();
      mvManager = mock(MVManager.class);

      // register on first add (the caller thread, once fixed); any add from another thread
      // blocks until released, so the pool thread can never have run when the caller returns
      doAnswer(inv -> {
         MVDef def = inv.getArgument(0);

         if(Thread.currentThread() != callerThread) {
            releasePool.await(30, TimeUnit.SECONDS);
         }

         addThreads.putIfAbsent(def.getName(), Thread.currentThread().getName());
         registered.put(def.getName(), def);
         return null;
      }).when(mvManager).add(any(MVDef.class), anyBoolean());
      when(mvManager.get(anyString(), eq(ORG)))
         .thenAnswer(inv -> registered.get(inv.<String>getArgument(0)));
      doAnswer(inv -> {
         poolFinished.countDown();
         return null;
      }).when(mvManager).fireEvent(anyString(), anyString(), any(), any());

      Map<String, String> backing = new ConcurrentHashMap<>();
      DistributedMap<String, String> map =
         mock(DistributedMap.class, AdditionalAnswers.delegatesTo(backing));
      Cluster cluster = mock(Cluster.class);
      when(cluster.getMap(anyString())).thenAnswer(inv -> map);
      when(cluster.isSchedulerRunning()).thenReturn(false);

      principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn("admin");
      when(((XPrincipal) principal).getGroups()).thenReturn(new String[0]);
      when(((XPrincipal) principal).getRoles()).thenReturn(new IdentityID[0]);
      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn(ORG);
      when(orgManager.getCurrentOrgID(any())).thenReturn(ORG);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      service = new MVSupportService(mvManager, mock(SecurityEngine.class),
                                     mock(ScheduleManager.class), cluster);
   }

   @AfterEach
   void tearDown() {
      releasePool.countDown();
      service.shutdown();
      orgManagerStatic.close();
   }

   @Test
   void backgroundWithDataRegistersOnCallerThreadBeforeReturn() throws Throwable {
      MVStatusHolder a = new MVStatusHolder("mv_a_" + ORG);

      service.createMV(List.of(a.name), List.of(a.status), true, false, principal);

      // the pool thread is still blocked in its own add, so this can only come from the caller
      assertTrue(service.existsInOrg(a.name, ORG));
      assertEquals(callerThread.getName(), addThreads.get(a.name));
      verify(a.def).setSuccess(false);
      verify(a.def, never()).setSuccess(true);
   }

   @Test
   void backgroundNoDataRegistersWithoutMarkingUnsuccessful() throws Throwable {
      MVStatusHolder a = new MVStatusHolder("mv_a_" + ORG);

      service.createMV(List.of(a.name), List.of(a.status), true, true, principal);

      assertTrue(service.existsInOrg(a.name, ORG));
      assertEquals(callerThread.getName(), addThreads.get(a.name));
      verify(a.def, never()).setSuccess(anyBoolean());
   }

   @Test
   void foregroundStillRegistersOnCallerThread() throws Throwable {
      MVStatusHolder a = new MVStatusHolder("mv_a_" + ORG);

      service.createMV(List.of(a.name), List.of(a.status), false, false, principal);

      assertTrue(service.existsInOrg(a.name, ORG));
      assertEquals(callerThread.getName(), addThreads.get(a.name));
      verify(a.def).setSuccess(false);
   }

   @Test
   void everyMvOfABatchIsRegisteredOnReturn() throws Throwable {
      MVStatusHolder a = new MVStatusHolder("mv_a_" + ORG);
      MVStatusHolder b = new MVStatusHolder("mv_b_" + ORG);

      service.createMV(List.of(a.name, b.name), List.of(a.status, b.status), true, false,
                       principal);

      assertTrue(service.existsInOrg(a.name, ORG));
      assertTrue(service.existsInOrg(b.name, ORG));
   }

   @Test
   void failedBackgroundGenerationLeavesDefinitionRegisteredAsUnsuccessful() throws Throwable {
      MVStatusHolder a = new MVStatusHolder("mv_a_" + ORG);

      service.createMV(List.of(a.name), List.of(a.status), true, false, principal);
      releasePool.countDown();
      assertTrue(poolFinished.await(30, TimeUnit.SECONDS), "pool thread did not finish");

      // generation on the mock definition cannot succeed: the definition must stay registered
      // and flagged unsuccessful (needs regeneration), never removed and never marked successful
      assertTrue(service.existsInOrg(a.name, ORG));
      verify(a.def, never()).setSuccess(true);
      verify(mvManager, never()).remove(any(MVDef.class), anyBoolean(), anyString());
   }

   private static final class MVStatusHolder {
      MVStatusHolder(String name) {
         this.name = name;
         def = mock(MVDef.class);
         when(def.getName()).thenReturn(name);
         when(def.getMVName()).thenReturn(name);
         status = mock(MVSupportService.MVStatus.class);
         when(status.getDefinition()).thenReturn(def);
      }

      final String name;
      final MVDef def;
      final MVSupportService.MVStatus status;
   }
}
