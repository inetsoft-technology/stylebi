/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import inetsoft.mv.MVWorksheetStorage;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityEngine;
import org.junit.jupiter.api.*;
import org.mockito.AdditionalAnswers;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82454: worksheets persisted for MV analyses that were never cleaned up (left before
 * #82407, or when the analysis map was lost) stayed in the worksheet storage forever. The
 * periodic sweep removes the old worksheets nothing references.
 */
@Tag("core")
class MVSupportServiceOrphanWorksheetSweepTest {
   private static final Instant OLD = Instant.now().minus(3, ChronoUnit.DAYS);

   private MVManager mvManager;
   private MVWorksheetStorage storage;
   private SecurityEngine security;
   private Cluster cluster;
   private MVSupportService service;
   private Map<String, MVSupportService.AnalysisStatus> backing;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<MVWorksheetStorage> storageStatic;

   @BeforeEach
   @SuppressWarnings("unchecked")
   void setUp() {
      mvManager = mock(MVManager.class);
      storage = mock(MVWorksheetStorage.class);
      security = mock(SecurityEngine.class);
      when(security.getOrganizations()).thenReturn(new String[] { "org1" });
      backing = new ConcurrentHashMap<>();
      DistributedMap<String, MVSupportService.AnalysisStatus> map =
         mock(DistributedMap.class, AdditionalAnswers.delegatesTo(backing));
      cluster = mock(Cluster.class);
      when(cluster.getMap(anyString())).thenAnswer(inv -> map);

      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn("org1");
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      storageStatic = mockStatic(MVWorksheetStorage.class);
      storageStatic.when(MVWorksheetStorage::getInstance).thenReturn(storage);

      service = new MVSupportService(mvManager, security, mock(ScheduleManager.class), cluster);
   }

   @AfterEach
   void tearDown() {
      service.shutdown();
      storageStatic.close();
      orgManagerStatic.close();
   }

   @Test
   void periodicCleanupSweepsOldUnreferencedWorksheet() throws Exception {
      files("org1", "stale.ws", OLD);

      service.removeExpiredAnalysis();

      verify(storage).removeWorksheet("stale.ws", "org1");
   }

   @Test
   void oldUnreferencedWorksheetIsDeleted() throws Exception {
      files("org1", "stale.ws", OLD);

      service.sweepOrphanWorksheets();

      verify(storage).removeWorksheet("stale.ws", "org1");
   }

   @Test
   void registeredMVWorksheetIsKept() throws Exception {
      files("org1", "reg.ws", OLD, "stale.ws", OLD);
      registered("org1", "reg");

      service.sweepOrphanWorksheets();

      verify(storage, never()).removeWorksheet(eq("reg.ws"), anyString());
      verify(storage).removeWorksheet("stale.ws", "org1");
   }

   @Test
   void analysisMapWorksheetIsKept() throws Exception {
      files("org1", "live.ws", OLD);
      MVDef def = mock(MVDef.class);
      when(def.getWorksheetPath()).thenReturn("live.ws");
      backing.put("a1", status(def));

      service.sweepOrphanWorksheets();

      verify(storage, never()).removeWorksheet(anyString(), anyString());
   }

   @Test
   void freshWorksheetIsKept() throws Exception {
      files("org1", "fresh.ws", Instant.now().minus(5, ChronoUnit.MINUTES));

      service.sweepOrphanWorksheets();

      verify(storage, never()).removeWorksheet(anyString(), anyString());
   }

   @Test
   void longNameUsesHashedPath() throws Exception {
      String name = "m".repeat(100);
      String path = MVDef.getWorksheetPath(name);
      Assertions.assertNotEquals(name + ".ws", path);
      files("org1", path, OLD, name + ".ws", OLD);
      registered("org1", name);

      service.sweepOrphanWorksheets();

      verify(storage, never()).removeWorksheet(eq(path), anyString());
      verify(storage).removeWorksheet(name + ".ws", "org1");
   }

   @Test
   void organizationsAreIsolatedAndListingFailureSkipsOnlyThatOrg() throws Exception {
      when(security.getOrganizations()).thenReturn(new String[] { "org1", "org2" });
      when(storage.listWorksheets("org1")).thenThrow(new IOException("boom"));
      files("org2", "stale2.ws", OLD);

      service.sweepOrphanWorksheets();

      verify(storage, never()).removeWorksheet(anyString(), eq("org1"));
      verify(storage).removeWorksheet("stale2.ws", "org2");
   }

   @Test
   void unparsableRegisteredDefinitionKeepsWorksheet() throws Exception {
      files("org1", "bad.ws", OLD);
      when(mvManager.getRegisteredNames("org1")).thenReturn(Set.of("bad"));
      when(mvManager.get("bad", "org1")).thenThrow(new RuntimeException("parse"));

      service.sweepOrphanWorksheets();

      verify(storage, never()).removeWorksheet(anyString(), anyString());
   }

   @Test
   void referenceSetFailureDeletesNothing() throws Exception {
      files("org1", "stale.ws", OLD);
      when(mvManager.getRegisteredNames("org1")).thenThrow(new RuntimeException("boom"));

      service.sweepOrphanWorksheets();

      verify(storage, never()).removeWorksheet(anyString(), anyString());
   }

   @Test
   void keyOfOtherOrganizationProtectsWorksheet() throws Exception {
      when(security.getOrganizations()).thenReturn(new String[] { "org1", "org2" });
      files("org1", "x.ws", OLD);
      when(storage.listWorksheets("org2")).thenReturn(Map.of());
      registered("org2", "x");

      service.sweepOrphanWorksheets();

      verify(storage, never()).removeWorksheet(anyString(), anyString());
   }

   @Test
   void analysisMapIsReadBeforeRegisteredNamesAndRecheckedBeforeDelete() throws Exception {
      files("org1", "stale.ws", OLD);

      service.sweepOrphanWorksheets();

      InOrder order = inOrder(cluster, mvManager, storage);
      order.verify(cluster).getMap(anyString());
      order.verify(mvManager).getRegisteredNames("org1");
      order.verify(storage).listWorksheets("org1");
      // second read of the registered names, right before the delete
      order.verify(mvManager).getRegisteredNames("org1");
      order.verify(storage).removeWorksheet("stale.ws", "org1");
   }

   @Test
   void worksheetRegisteredAfterReferenceSetIsKept() throws Exception {
      files("org1", "late.ws", OLD);
      when(mvManager.getRegisteredNames("org1"))
         .thenReturn(Set.of()).thenReturn(Set.of("late"));

      service.sweepOrphanWorksheets();

      verify(storage, never()).removeWorksheet(anyString(), anyString());
   }

   private void files(String org, Object... pairs) throws Exception {
      Map<String, Instant> files = new HashMap<>();

      for(int i = 0; i < pairs.length; i += 2) {
         files.put((String) pairs[i], (Instant) pairs[i + 1]);
      }

      when(storage.listWorksheets(org)).thenReturn(files);
   }

   private void registered(String org, String name) {
      when(mvManager.getRegisteredNames(org)).thenReturn(Set.of(name));
      MVDef def = mock(MVDef.class);
      when(def.getWorksheetPath()).thenReturn(MVDef.getWorksheetPath(name));
      when(mvManager.get(name, org)).thenReturn(def);
   }

   private static MVSupportService.AnalysisStatus status(MVDef... defs) {
      List<MVSupportService.MVStatus> results = new ArrayList<>();

      for(MVDef def : defs) {
         MVSupportService.MVStatus s = mock(MVSupportService.MVStatus.class);
         when(s.getDefinition()).thenReturn(def);
         results.add(s);
      }

      MVSupportService.AnalysisStatus status = mock(MVSupportService.AnalysisStatus.class);
      when(status.getResults()).thenReturn(results);
      return status;
   }
}
