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

import inetsoft.mv.MVManager;
import inetsoft.mv.data.MVStorage;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.util.ThreadPool;
import inetsoft.web.admin.content.repository.model.CreateMVResponse;
import inetsoft.web.admin.content.repository.model.CreateUpdateMVRequest;
import org.junit.jupiter.api.*;
import org.mockito.AdditionalAnswers;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77279: the MV create and update status maps are shared by all organizations and keyed
 * only by a client generated id, so an entry may only be read, removed or replaced by the user
 * and org that started the job.
 */
@Tag("core")
class MVServiceStatusOwnershipTest {
   private static final String ORG_A = "orgA";
   private static final String ORG_B = "orgB";
   private static final String DEFAULT_ORG = "host-org";
   private static final String ID = "job-1";
   private static final String ANALYSIS_ID = "analysis-1";
   private static final String SECRET = "orgB secret error";

   private Map<String, Object> createBacking;
   private Map<String, Object> updateBacking;
   private MVSupportService support;
   private MVService service;
   private List<Runnable> jobs;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<ThreadPool> threadPoolStatic;

   private Principal owner;
   private Principal foreignOrgAdmin;
   private Principal sameOrgOtherUser;
   private Principal ownerInOtherOrg;

   @BeforeEach
   @SuppressWarnings("unchecked")
   void setUp() {
      createBacking = new HashMap<>();
      updateBacking = new HashMap<>();
      DistributedMap<String, Object> createMap =
         mock(DistributedMap.class, AdditionalAnswers.delegatesTo(createBacking));
      DistributedMap<String, Object> updateMap =
         mock(DistributedMap.class, AdditionalAnswers.delegatesTo(updateBacking));
      Cluster cluster = mock(Cluster.class);
      when(cluster.getMap("CREATE_MV_STATUS_MAP")).thenAnswer(inv -> createMap);
      when(cluster.getMap("UPDATE_MV_STATUS_MAP")).thenAnswer(inv -> updateMap);

      owner = principal("owner~;~" + ORG_B);
      foreignOrgAdmin = principal("admin~;~" + ORG_A);
      sameOrgOtherUser = principal("other~;~" + ORG_B);
      // same principal name, but currently managing another org (site admin switched org)
      ownerInOtherOrg = principal("owner~;~" + ORG_B);

      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID(any())).thenAnswer(inv -> {
         Principal p = inv.getArgument(0);

         if(p == owner || p == sameOrgOtherUser) {
            return ORG_B;
         }

         if(p == foreignOrgAdmin || p == ownerInOtherOrg) {
            return ORG_A;
         }

         return DEFAULT_ORG;
      });
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      // capture background jobs so that the test runs them without the 20 second cleanup sleep
      jobs = new ArrayList<>();
      threadPoolStatic = mockStatic(ThreadPool.class);
      threadPoolStatic.when(() -> ThreadPool.addOnDemand(any(Runnable.class)))
         .thenAnswer(inv -> jobs.add(inv.getArgument(0)));

      support = mock(MVSupportService.class);
      service = spy(new MVService(mock(ContentRepositoryTreeService.class), support, cluster,
                                  mock(MVManager.class), mock(DataCycleManager.class),
                                  mock(SecurityEngine.class), mock(MVStorage.class)));
   }

   @AfterEach
   void tearDown() {
      threadPoolStatic.close();
      orgManagerStatic.close();
   }

   // ---- create ----

   @Test
   void foreignOrgCallerCannotReadOrRemoveCreateStatus() throws Throwable {
      assertForeignCreateRefused(foreignOrgAdmin);
   }

   @Test
   void otherUserInSameOrgCannotReadOrRemoveCreateStatus() throws Throwable {
      assertForeignCreateRefused(sameOrgOtherUser);
   }

   @Test
   void sameUserManagingAnotherOrgCannotReadCreateStatus() throws Throwable {
      assertForeignCreateRefused(ownerInOtherOrg);
   }

   @Test
   void foreignCallerCannotReplaceRunningCreateJob() throws Throwable {
      createBacking.put(ID, status(false, false, null));

      CreateMVResponse response = service.create(ID, ANALYSIS_ID, null, foreignOrgAdmin);

      assertFalse(response.complete());
      assertTrue(jobs.isEmpty());
      assertTrue(isOwnedByOwner(createBacking.get(ID)));
   }

   @Test
   void foreignCreateCallerWithInvalidAnalysisIsRefusedLikeMissingId() {
      createBacking.put(ID, status(true, false, null));
      when(support.getAnalysisResult(eq("bad"), same(foreignOrgAdmin)))
         .thenThrow(new IllegalStateException("The analysis job is not valid"));

      assertThrows(IllegalStateException.class,
                   () -> service.create(ID, "bad", null, foreignOrgAdmin));
      assertTrue(createBacking.containsKey(ID));
   }

   @Test
   void ownerSeesCreateFailureOnceAndEntryIsRemoved() throws Throwable {
      CreateUpdateMVRequest request = mock(CreateUpdateMVRequest.class);
      doThrow(new RuntimeException(SECRET)).when(service)
         .create0(ANALYSIS_ID, request, owner);

      CreateMVResponse first = service.create(ID, ANALYSIS_ID, request, owner);
      assertFalse(first.complete());
      assertEquals(1, jobs.size());
      assertTrue(isOwnedByOwner(createBacking.get(ID)));

      runJobs();

      // a foreign poll neither sees the error nor consumes the entry
      assertFalse(service.create(ID, ANALYSIS_ID, null, foreignOrgAdmin).complete());
      assertTrue(createBacking.containsKey(ID));

      RuntimeException error = assertThrows(RuntimeException.class,
                                             () -> service.create(ID, ANALYSIS_ID, request, owner));
      assertEquals(SECRET, error.getMessage());
      assertFalse(createBacking.containsKey(ID));
   }

   @Test
   void ownerSeesCreateCompletionOnceAndEntryIsRemoved() throws Throwable {
      createBacking.put(ID, status(true, false, null));

      CreateMVResponse response = service.create(ID, ANALYSIS_ID, null, owner);

      assertTrue(response.complete());
      assertFalse(response.failed());
      assertFalse(createBacking.containsKey(ID));
      assertTrue(jobs.isEmpty());
   }

   // ---- update ----

   @Test
   void foreignOrgCallerCannotReadOrRemoveUpdateStatus() throws Throwable {
      assertForeignUpdateRefused(foreignOrgAdmin);
   }

   @Test
   void otherUserInSameOrgCannotReadOrRemoveUpdateStatus() throws Throwable {
      assertForeignUpdateRefused(sameOrgOtherUser);
   }

   @Test
   void sameUserManagingAnotherOrgCannotReadUpdateStatus() throws Throwable {
      assertForeignUpdateRefused(ownerInOtherOrg);
   }

   @Test
   void foreignCallerCannotReplaceRunningUpdateJob() throws Throwable {
      updateBacking.put(ID, status(false, false, null));

      CreateMVResponse response = service.update(ID, new String[0], false, foreignOrgAdmin);

      assertFalse(response.complete());
      assertTrue(jobs.isEmpty());
      assertTrue(isOwnedByOwner(updateBacking.get(ID)));
   }

   @Test
   void ownerSeesUpdateFailureOnceAndJobIsNotRestarted() throws Throwable {
      String[] names = { "mv1" };
      when(support.recreateMV(names, false, owner)).thenReturn(SECRET);

      assertFalse(service.update(ID, names, false, owner).complete());
      assertEquals(1, jobs.size());
      runJobs();

      assertFalse(service.update(ID, new String[0], false, foreignOrgAdmin).complete());
      assertTrue(updateBacking.containsKey(ID));
      assertTrue(jobs.isEmpty());

      RuntimeException error = assertThrows(RuntimeException.class,
                                            () -> service.update(ID, names, false, owner));
      assertEquals(SECRET, error.getMessage());
      assertFalse(updateBacking.containsKey(ID));
      verify(support, times(1)).recreateMV(any(), anyBoolean(), any());
   }

   @Test
   void ownerSeesUpdateCompletionOnce() throws Throwable {
      String[] names = { "mv1" };
      when(support.recreateMV(names, false, owner)).thenReturn(null);

      service.update(ID, names, false, owner);
      runJobs();

      CreateMVResponse response = service.update(ID, names, false, owner);
      assertTrue(response.complete());
      assertFalse(response.failed());
      assertFalse(updateBacking.containsKey(ID));
   }

   @Test
   void nullPrincipalUpdateIsStampedWithoutError() throws Throwable {
      when(support.recreateMV(any(), anyBoolean(), isNull())).thenReturn(null);

      assertFalse(service.update(ID, new String[0], false, null).complete());
      runJobs();

      assertFalse(service.update(ID, new String[0], false, owner).complete());
      assertTrue(service.update(ID, new String[0], false, null).complete());
   }

   private void assertForeignCreateRefused(Principal caller) throws Throwable {
      createBacking.put(ID, status(true, true, SECRET));

      CreateMVResponse response = service.create(ID, ANALYSIS_ID, null, caller);

      assertFalse(response.complete());
      assertNull(response.error());
      assertTrue(createBacking.containsKey(ID));
      assertTrue(isOwnedByOwner(createBacking.get(ID)));
      assertTrue(jobs.isEmpty());
      verify(service, never()).create0(any(), any(), any());
   }

   private void assertForeignUpdateRefused(Principal caller) throws Throwable {
      updateBacking.put(ID, status(true, true, SECRET));

      CreateMVResponse response = service.update(ID, new String[] { "mine" }, false, caller);

      assertFalse(response.complete());
      assertNull(response.error());
      assertTrue(updateBacking.containsKey(ID));
      assertTrue(isOwnedByOwner(updateBacking.get(ID)));
      assertTrue(jobs.isEmpty());
      verify(support, never()).recreateMV(any(), anyBoolean(), any());
   }

   private void runJobs() {
      List<Runnable> pending = new ArrayList<>(jobs);
      jobs.clear();
      // interrupt the cleanup sleep so that the job keeps its final status in the map
      pending.forEach(job -> {
         Thread.currentThread().interrupt();
         job.run();
         Thread.interrupted();
      });
   }

   private boolean isOwnedByOwner(Object status) {
      return ((MVService.MVJobStatus) status).isOwnedBy(owner);
   }

   private MVService.MVJobStatus status(boolean complete, boolean failed, String error) {
      return new MVService.MVJobStatus(owner.getName(), ORG_B, complete, failed, error);
   }

   private static Principal principal(String name) {
      Principal principal = mock(Principal.class);
      when(principal.getName()).thenReturn(name);
      return principal;
   }
}
