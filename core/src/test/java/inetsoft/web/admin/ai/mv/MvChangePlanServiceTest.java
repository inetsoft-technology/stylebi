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
package inetsoft.web.admin.ai.mv;

import inetsoft.mv.MVDef;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.Tool;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.content.repository.MVSupportService;
import inetsoft.web.admin.model.NameLabelTuple;
import org.junit.jupiter.api.*;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #78900: {@code create}/{@code set_cycle} accepted a {@code cycle} that is not an existing
 * data cycle and bound the mv to a name that never fires. {@code resolve} now refuses it, comparing
 * against the cycle names {@code get_mv_analysis} reports as {@code availableCycles}.
 */
@Tag("core")
class MvChangePlanServiceTest {
   private AdminMvGateway mvGateway;
   private Principal user;
   private MvChangePlanService planService;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<Tool> tool;

   @BeforeEach
   void setUp() throws Exception {
      mvGateway = mock(AdminMvGateway.class);
      user = mock(Principal.class);
      planService = new MvChangePlanService(mvGateway);

      // A successful resolve issues a task audit token, which encrypts through Spring-backed Tool.
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));

      orgManagerStatic = mockStatic(OrganizationManager.class);
      OrganizationManager orgManager = mock(OrganizationManager.class);
      lenient().when(orgManager.getCurrentOrgID(any())).thenReturn("host-org");
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      MVSupportService.AnalysisResult result = mock(MVSupportService.AnalysisResult.class);
      List<MVSupportService.MVStatus> candidates = List.of(status("MV1"), status("MV2"));
      lenient().when(result.getStatus()).thenReturn(candidates);
      lenient().when(mvGateway.getAnalysisResult(eq("A1"), any())).thenReturn(result);
      availableCycles("MV Cycle");
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      tool.close();
   }

   private void availableCycles(String... names) throws Exception {
      List<NameLabelTuple> cycles = new java.util.ArrayList<>();

      for(String name : names) {
         // The label deliberately differs from the name: only the name may be accepted.
         cycles.add(NameLabelTuple.builder().name(name).label("Label of " + name).build());
      }

      lenient().when(mvGateway.getDataCycles(any())).thenReturn(cycles);
   }

   private static MVSupportService.MVStatus status(String name) {
      MVSupportService.MVStatus status = mock(MVSupportService.MVStatus.class);
      MVDef def = mock(MVDef.class);
      lenient().when(def.getName()).thenReturn(name);
      lenient().when(status.getDefinition()).thenReturn(def);
      return status;
   }

   private static MvChangeRequest change(String verb, String mvName, String cycle) {
      MvChangeRequest c = new MvChangeRequest();
      c.setVerb(verb);
      c.setAnalysisId("A1");
      c.setMvNames(List.of(mvName));
      c.setCycle(cycle);
      return c;
   }

   private ResolvedPlan resolve(MvChangeRequest... changes) throws Exception {
      MvChangePlanRequest req = new MvChangePlanRequest();
      req.setTask("test task");
      req.setChanges(List.of(changes));
      return planService.resolve(req, user);
   }

   private IllegalArgumentException refused(MvChangeRequest... changes) {
      return assertThrows(IllegalArgumentException.class, () -> resolve(changes));
   }

   @Test void unknownCycleIsRefusedForCreate() {
      String msg = refused(change(MvChangeRequest.VERB_CREATE, "MV1", "NoSuchCycle")).getMessage();
      assertTrue(msg.contains("changes[0].cycle"), msg);
      assertTrue(msg.contains("\"NoSuchCycle\""), msg);
      assertTrue(msg.contains("available: MV Cycle"), msg);
   }

   @Test void unknownCycleIsRefusedForSetCycle() {
      String msg = refused(change(MvChangeRequest.VERB_SET_CYCLE, "MV1", "NoSuchCycle"))
         .getMessage();
      assertTrue(msg.contains("changes[0].cycle"), msg);
      assertTrue(msg.contains("\"NoSuchCycle\""), msg);
   }

   @Test void knownCycleIsAccepted() throws Exception {
      assertNotNull(resolve(change(MvChangeRequest.VERB_CREATE, "MV1", "MV Cycle")));
      assertNotNull(resolve(change(MvChangeRequest.VERB_SET_CYCLE, "MV1", "MV Cycle")));
   }

   @Test void cycleLabelIsNotAccepted() {
      refused(change(MvChangeRequest.VERB_SET_CYCLE, "MV1", "Label of MV Cycle"));
   }

   @Test void absentAndEmptyCycleAreAccepted() throws Exception {
      assertNotNull(resolve(change(MvChangeRequest.VERB_CREATE, "MV1", null)));
      assertNotNull(resolve(change(MvChangeRequest.VERB_SET_CYCLE, "MV1", "")));
      verify(mvGateway, never()).getDataCycles(any());
   }

   @Test void emptyAvailableListRefusesNonEmptyCycle() throws Exception {
      availableCycles();
      String msg = refused(change(MvChangeRequest.VERB_CREATE, "MV1", "MV Cycle")).getMessage();
      assertTrue(msg.contains("changes[0].cycle"), msg);
      assertTrue(msg.contains("available: none"), msg);
   }

   @Test void secondEntryReportsItsOwnIndex() {
      String msg = refused(change(MvChangeRequest.VERB_CREATE, "MV1", "MV Cycle"),
                           change(MvChangeRequest.VERB_SET_CYCLE, "MV2", "NoSuchCycle"))
         .getMessage();
      assertTrue(msg.contains("changes[1].cycle"), msg);
   }

   @Test void cycleListIsFetchedOncePerResolve() throws Exception {
      resolve(change(MvChangeRequest.VERB_CREATE, "MV1", "MV Cycle"),
              change(MvChangeRequest.VERB_SET_CYCLE, "MV2", "MV Cycle"));
      verify(mvGateway, times(1)).getDataCycles(any());
   }

   @Test void unknownMvNameKeepsPrecedenceOverCycle() {
      String msg = refused(change(MvChangeRequest.VERB_CREATE, "Nope", "NoSuchCycle"))
         .getMessage();
      assertTrue(msg.contains("changes[0].mvNames"), msg);
   }
}
