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
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityEngine;
import org.junit.jupiter.api.*;
import org.mockito.AdditionalAnswers;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82407: the worksheet persisted for each MV analysis candidate was never removed when
 * the analysis was discarded. Removing an analysis status must delete the persisted worksheet of
 * every definition that was not registered as an MV, and keep the registered ones.
 */
@Tag("core")
class MVSupportServiceAnalysisWorksheetCleanupTest {
   private static final String CURRENT_ORG = "currentorg";
   private static final String ANALYSIS_ORG = "analysisorg";

   private MVManager mvManager;
   private MVSupportService service;
   private Map<String, MVSupportService.AnalysisStatus> backing;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   @BeforeEach
   @SuppressWarnings("unchecked")
   void setUp() {
      mvManager = mock(MVManager.class);
      backing = new ConcurrentHashMap<>();
      DistributedMap<String, MVSupportService.AnalysisStatus> map =
         mock(DistributedMap.class, AdditionalAnswers.delegatesTo(backing));
      Cluster cluster = mock(Cluster.class);
      when(cluster.getMap(anyString())).thenAnswer(inv -> map);

      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn(CURRENT_ORG);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      service = new MVSupportService(mvManager, mock(SecurityEngine.class),
                                     mock(ScheduleManager.class), cluster);
   }

   @AfterEach
   void tearDown() {
      service.shutdown();
      orgManagerStatic.close();
   }

   @Test
   void expiredAnalysisRemovesUnregisteredWorksheetOnly() {
      MVDef orphan = def("orphan");
      MVDef registered = def("registered");
      when(mvManager.get("registered", ANALYSIS_ORG)).thenReturn(registered);
      backing.put("a1", status(ANALYSIS_ORG, orphan, registered));

      service.removeExpiredAnalysis();

      verify(orphan).removeWorksheet(ANALYSIS_ORG);
      verify(registered, never()).removeWorksheet(anyString());
      Assertions.assertTrue(backing.isEmpty());
   }

   @Test
   void removeAnalysisStatusEntriesCleansUnregisteredSiblings() throws Exception {
      MVDef deleted = def("deleted");
      MVDef sibling = def("sibling");
      backing.put("a1", status(ANALYSIS_ORG, deleted, sibling));

      Method method = MVSupportService.class
         .getDeclaredMethod("removeAnalysisStatusEntries", List.class);
      method.setAccessible(true);
      method.invoke(service, List.of("deleted"));

      verify(sibling).removeWorksheet(ANALYSIS_ORG);
      Assertions.assertTrue(backing.isEmpty());
   }

   @Test
   void fallsBackToCurrentOrgWhenStatusHasNoOrg() {
      MVDef orphan = def("orphan");
      backing.put("a1", status(null, orphan));

      service.removeExpiredAnalysis();

      verify(mvManager).get("orphan", CURRENT_ORG);
      verify(orphan).removeWorksheet(CURRENT_ORG);
   }

   @Test
   void deleteFailureDoesNotBreakMapRemoval() {
      MVDef bad = def("bad");
      doThrow(new RuntimeException("boom")).when(bad).removeWorksheet(anyString());
      backing.put("a1", status(ANALYSIS_ORG, bad));

      service.removeExpiredAnalysis();

      Assertions.assertTrue(backing.isEmpty());
   }

   private static MVDef def(String name) {
      MVDef def = mock(MVDef.class);
      when(def.getName()).thenReturn(name);
      when(def.getMVName()).thenReturn(name);
      return def;
   }

   // a mock status reads createdTime as 0, i.e. long expired
   private static MVSupportService.AnalysisStatus status(String orgId, MVDef... defs) {
      List<MVSupportService.MVStatus> results = new ArrayList<>();

      for(MVDef def : defs) {
         MVSupportService.MVStatus s = mock(MVSupportService.MVStatus.class);
         when(s.getDefinition()).thenReturn(def);
         results.add(s);
      }

      MVSupportService.AnalysisStatus status = mock(MVSupportService.AnalysisStatus.class);
      when(status.getResults()).thenReturn(results);
      when(status.getOrgId()).thenReturn(orgId);
      return status;
   }
}
