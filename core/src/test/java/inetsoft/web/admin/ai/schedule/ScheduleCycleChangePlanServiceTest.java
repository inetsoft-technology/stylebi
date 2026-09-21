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
package inetsoft.web.admin.ai.schedule;

import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.Tool;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.api.schedule.TimeCondition;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers {@link ScheduleCycleChangePlanService} (bug #76848, design §5.3/§8): create/update/
 * delete resolution, name-collision refusal (charter assertion 6), rename-while-in-use refusal
 * vs. same-name-update-while-in-use ALLOWED (the twin-of-assertion-7 case), delete-while-in-use
 * refusal naming the real dependent MV names (assertion 7), and plan-hash stability/drift
 * detection.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ScheduleCycleChangePlanServiceTest {
   @Mock private AdminScheduleCycleGateway cycleGateway;
   @Mock private Principal user;
   private ScheduleCycleChangePlanService service;
   private MockedStatic<Tool> tool;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   @BeforeEach void setUp() {
      service = new ScheduleCycleChangePlanService(cycleGateway);

      // TaskAuditToken.issue calls Tool.encryptPassword, which needs a live Spring context for
      // its key outside a unit test -- mocked the same way ScheduleFolderChangePlanServiceTest
      // does.
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));

      OrganizationManager orgManager = mock(OrganizationManager.class, withSettings().lenient());
      when(orgManager.getCurrentOrgID(any(Principal.class))).thenReturn("host-org");
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
   }

   @AfterEach void tearDown() {
      tool.close();
      orgManagerStatic.close();
   }

   // -------------------------------------------------------------------------
   // basic request validation
   // -------------------------------------------------------------------------

   @Test void resolveThrowsOnBlankTask() {
      ScheduleCycleChangePlanRequest req = request("   ", List.of(deleteChange("A")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("task"));
   }

   @Test void resolveThrowsOnEmptyChanges() {
      ScheduleCycleChangePlanRequest req = request("do something", List.of());

      assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
   }

   @Test void resolveThrowsOnUnrecognizedVerb() {
      ScheduleCycleChangeRequest change = new ScheduleCycleChangeRequest("rename", "A", null);
      ScheduleCycleChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("verb"));
   }

   @Test void resolveThrowsOnDuplicateEntry() throws Exception {
      when(cycleGateway.cycleExists("A", user)).thenReturn(true);
      when(cycleGateway.currentAsset("A", "host-org")).thenReturn(asset("A"));

      ScheduleCycleChangePlanRequest req =
         request("task", List.of(deleteChange("A"), deleteChange("A")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("duplicate"));
   }

   // -------------------------------------------------------------------------
   // create
   // -------------------------------------------------------------------------

   @Test void resolveCreateThrowsWhenSpecMissing() {
      ScheduleCycleChangeRequest change = new ScheduleCycleChangeRequest("create", null, null);
      ScheduleCycleChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("spec"));
   }

   @Test void resolveCreateThrowsWhenConditionsEmpty() {
      ScheduleCycleChangeRequest change = createChange("NewCycle", List.of());
      ScheduleCycleChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("conditions"));
   }

   @Test void resolveCreateThrowsOnNameCollision() throws Exception {
      when(cycleGateway.cycleExists("Existing", user)).thenReturn(true);

      ScheduleCycleChangeRequest change = createChange("Existing", List.of(everyDay()));
      ScheduleCycleChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("already exists"));
   }

   @Test void resolveCreateSucceeds() throws Exception {
      when(cycleGateway.cycleExists("NewCycle", user)).thenReturn(false);

      ScheduleCycleChangeRequest change = createChange("NewCycle", List.of(everyDay()));
      ScheduleCycleChangePlanRequest req = request("task", List.of(change));

      ResolvedPlan plan = service.resolve(req, user);

      assertEquals(1, plan.changes().size());
      PlanChange result = plan.changes().get(0);
      assertEquals("NewCycle", result.property());
      assertNull(result.currentValue());
      assertNotNull(result.proposedValue());
      assertEquals(AdminChangeRecord.RISK_HIGH, result.risk());
      assertTrue(plan.requiresAgentSignoff());
      assertTrue(plan.requiresStorageBackup());
      assertNotNull(plan.planHash());
      assertNotNull(plan.taskToken());
   }

   // -------------------------------------------------------------------------
   // update
   // -------------------------------------------------------------------------

   @Test void resolveUpdateThrowsWhenCycleMissing() throws Exception {
      when(cycleGateway.cycleExists("Missing", user)).thenReturn(false);

      ScheduleCycleChangeRequest change = updateChange("Missing", null, List.of(everyDay()));
      ScheduleCycleChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("no scheduled cycle"));
   }

   @Test void resolveUpdateThrowsWhenNothingToChange() throws Exception {
      when(cycleGateway.cycleExists("Cycle1", user)).thenReturn(true);

      ScheduleCycleChangeRequest change =
         new ScheduleCycleChangeRequest("update", "Cycle1",
            new ScheduleCycleChangeRequest.ScheduleCycleSpec(null, null));
      ScheduleCycleChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("nothing to change"));
   }

   @Test void resolveUpdateRenameThrowsOnCollision() throws Exception {
      when(cycleGateway.cycleExists("Cycle1", user)).thenReturn(true);
      when(cycleGateway.cycleExists("Cycle2", user)).thenReturn(true);
      when(cycleGateway.currentAsset("Cycle1", "host-org")).thenReturn(asset("Cycle1"));

      ScheduleCycleChangeRequest change = updateChange("Cycle1", "Cycle2", null);
      ScheduleCycleChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("already exists"));
   }

   @Test void resolveUpdateRenameThrowsWhenInUse() throws Exception {
      when(cycleGateway.cycleExists("Cycle1", user)).thenReturn(true);
      when(cycleGateway.cycleExists("Cycle2", user)).thenReturn(false);
      when(cycleGateway.currentAsset("Cycle1", "host-org")).thenReturn(asset("Cycle1"));
      when(cycleGateway.dependentMvNames("Cycle1")).thenReturn(List.of("MV_A", "MV_B"));

      ScheduleCycleChangeRequest change = updateChange("Cycle1", "Cycle2", null);
      ScheduleCycleChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("MV_A"));
      assertTrue(ex.getMessage().contains("MV_B"));
   }

   /** Twin of charter assertion 7: a same-name (conditions-only) update is ALLOWED even while the
    * cycle is in use -- only a RENAME is refused. Mirrors {@code ScheduleCycleService#editCycle}'s
    * own real behavior exactly. */
   @Test void resolveUpdateSameNameAllowedWhenInUse() throws Exception {
      when(cycleGateway.cycleExists("Cycle1", user)).thenReturn(true);
      when(cycleGateway.currentAsset("Cycle1", "host-org")).thenReturn(asset("Cycle1"));
      // dependentMvNames is intentionally NOT stubbed to return anything but the Mockito default
      // (empty list) for a same-name update -- resolveUpdate must not even consult it when the
      // name is unchanged (verified below).

      ScheduleCycleChangeRequest change = updateChange("Cycle1", null, List.of(everyDay()));
      ScheduleCycleChangePlanRequest req = request("task", List.of(change));

      ResolvedPlan plan = service.resolve(req, user);

      assertEquals(1, plan.changes().size());
      verify(cycleGateway, never()).dependentMvNames(anyString());
   }

   // -------------------------------------------------------------------------
   // delete
   // -------------------------------------------------------------------------

   @Test void resolveDeleteThrowsWhenMissing() throws Exception {
      when(cycleGateway.cycleExists("Missing", user)).thenReturn(false);

      ScheduleCycleChangeRequest change = deleteChange("Missing");
      ScheduleCycleChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("no scheduled cycle"));
   }

   @Test void resolveDeleteThrowsWhenInUseNamingDependentMvs() throws Exception {
      when(cycleGateway.cycleExists("Cycle1", user)).thenReturn(true);
      when(cycleGateway.dependentMvNames("Cycle1")).thenReturn(List.of("MV_Sales"));

      ScheduleCycleChangeRequest change = deleteChange("Cycle1");
      ScheduleCycleChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("MV_Sales"));
   }

   @Test void resolveDeleteSucceeds() throws Exception {
      when(cycleGateway.cycleExists("Cycle1", user)).thenReturn(true);
      when(cycleGateway.dependentMvNames("Cycle1")).thenReturn(List.of());
      when(cycleGateway.currentAsset("Cycle1", "host-org")).thenReturn(asset("Cycle1"));

      ScheduleCycleChangeRequest change = deleteChange("Cycle1");
      ScheduleCycleChangePlanRequest req = request("task", List.of(change));

      ResolvedPlan plan = service.resolve(req, user);

      PlanChange result = plan.changes().get(0);
      assertEquals("Cycle1", result.property());
      assertNotNull(result.currentValue());
      assertNull(result.proposedValue());
      assertEquals(AdminChangeRecord.RISK_HIGH, result.risk());
   }

   // -------------------------------------------------------------------------
   // plan-hash stability / drift detection
   // -------------------------------------------------------------------------

   @Test void planHashIsStableForIdenticalInput() throws Exception {
      when(cycleGateway.cycleExists("NewCycle", user)).thenReturn(false);

      ScheduleCycleChangePlanRequest req1 =
         request("task", List.of(createChange("NewCycle", List.of(everyDay()))));
      ScheduleCycleChangePlanRequest req2 =
         request("task", List.of(createChange("NewCycle", List.of(everyDay()))));

      assertEquals(service.resolve(req1, user).planHash(), service.resolve(req2, user).planHash());
   }

   @Test void planHashDetectsDrift() throws Exception {
      when(cycleGateway.cycleExists("NewCycle", user)).thenReturn(false);

      ScheduleCycleChangePlanRequest req1 =
         request("task", List.of(createChange("NewCycle", List.of(everyDay()))));
      TimeCondition changedCondition = everyDay();
      changedCondition.setHour(23);
      ScheduleCycleChangePlanRequest req2 =
         request("task", List.of(createChange("NewCycle", List.of(changedCondition))));

      assertNotEquals(
         service.resolve(req1, user).planHash(), service.resolve(req2, user).planHash());
   }

   // -------------------------------------------------------------------------
   // fixtures
   // -------------------------------------------------------------------------

   private static TimeCondition everyDay() {
      TimeCondition condition = new TimeCondition();
      condition.setType(TimeCondition.Type.EVERY_DAY);
      condition.setHour(9);
      condition.setMinute(0);
      condition.setSecond(0);
      condition.setInterval(1);
      condition.setTimeZone("UTC");
      return condition;
   }

   private static DataCycleManager.DataCycleAsset asset(String name) {
      DataCycleManager.DataCycleAsset asset = new DataCycleManager.DataCycleAsset();
      asset.setName(name);
      asset.setOrgId("host-org");
      asset.setEnabled(true);
      asset.setConditions(List.of(
         inetsoft.sree.schedule.TimeCondition.at(9, 0, 0)));
      asset.setInfo(new DataCycleManager.CycleInfo(name, "host-org"));
      return asset;
   }

   private static ScheduleCycleChangeRequest createChange(String name, List<TimeCondition> conditions) {
      return new ScheduleCycleChangeRequest("create", null,
         new ScheduleCycleChangeRequest.ScheduleCycleSpec(name, conditions));
   }

   private static ScheduleCycleChangeRequest updateChange(String currentName, String newName,
                                                           List<TimeCondition> conditions)
   {
      return new ScheduleCycleChangeRequest("update", currentName,
         new ScheduleCycleChangeRequest.ScheduleCycleSpec(newName, conditions));
   }

   private static ScheduleCycleChangeRequest deleteChange(String name) {
      return new ScheduleCycleChangeRequest("delete", name, null);
   }

   private static ScheduleCycleChangePlanRequest request(String task,
                                                          List<ScheduleCycleChangeRequest> changes)
   {
      ScheduleCycleChangePlanRequest req = new ScheduleCycleChangePlanRequest();
      req.setTask(task);
      req.setChanges(changes);
      return req;
   }
}
