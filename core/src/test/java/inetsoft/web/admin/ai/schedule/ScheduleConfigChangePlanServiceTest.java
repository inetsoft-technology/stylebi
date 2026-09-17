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

import inetsoft.util.Tool;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.schedule.model.ScheduleConfigurationModel;
import inetsoft.web.admin.schedule.model.ServerLocation;
import inetsoft.web.viewsheet.model.dialog.schedule.TimeRangeModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Focuses on what is genuinely new/load-bearing in this area (per {@code 01-design.md}): the
 * preview-time duplicate-name/duplicate-default Time Range pre-checks and auto-clear-on-new-default
 * behavior, the Server Location label/path-containment conflict check, and -- the most important
 * behavior in this whole track -- the delete-dependency refusal for a Time Range a live task still
 * references, including the exact silent-reassignment target it names.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ScheduleConfigChangePlanServiceTest {
   @Mock private AdminScheduleConfigGateway gateway;
   @Mock private Principal user;
   private ScheduleConfigChangePlanService service;
   private MockedStatic<Tool> tool;

   @BeforeEach void setUp() throws Exception {
      service = new ScheduleConfigChangePlanService(gateway);
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString())).thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      lenient().when(gateway.findTimeRangeDependents(anyString(), anyList()))
         .thenReturn(List.of());
   }

   @AfterEach void tearDown() {
      tool.close();
   }

   // -------------------------------------------------------------------------
   // basic request validation
   // -------------------------------------------------------------------------

   @Test void resolveThrowsOnBlankTask() {
      ScheduleConfigChangePlanRequest req = request("   ", List.of(deleteLocation("/foo")));
      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("task"));
   }

   @Test void resolveThrowsOnEmptyChanges() {
      ScheduleConfigChangePlanRequest req = request("task", List.of());
      assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
   }

   @Test void resolveThrowsOnUnrecognizedUnitType() throws Exception {
      stubConfig(List.of(), List.of());
      ScheduleConfigChangeRequest change = new ScheduleConfigChangeRequest();
      change.setUnitType("scheduleTask");
      change.setVerb("delete");
      change.setKey("x");
      ScheduleConfigChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("unitType"));
   }

   // -------------------------------------------------------------------------
   // server locations
   // -------------------------------------------------------------------------

   @Test void resolveServerLocationCreateSucceeds() throws Exception {
      stubConfig(List.of(), List.of());
      ScheduleConfigChangePlanRequest req = request("add", List.of(
         createLocation("/data/exports", "Exports")));

      ResolvedPlan plan = service.resolve(req, user);
      assertEquals(1, plan.changes().size());
      assertEquals("/data/exports", plan.changes().get(0).property());
      assertTrue(plan.requiresAgentSignoff());
      assertFalse(plan.requiresStorageBackup());
   }

   @Test void resolveServerLocationCreateThrowsOnDuplicatePath() throws Exception {
      stubConfig(List.of(location("/data/exports", "Exports")), List.of());
      ScheduleConfigChangePlanRequest req = request("add", List.of(
         createLocation("/data/exports", "Exports2")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("already exists"));
   }

   @Test void resolveServerLocationCreateThrowsOnDuplicateLabel() throws Exception {
      stubConfig(List.of(location("/data/exports", "Exports")), List.of());
      ScheduleConfigChangePlanRequest req = request("add", List.of(
         createLocation("/data/other", "Exports")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("label"));
   }

   @Test void resolveServerLocationCreateThrowsOnOverlappingPath() throws Exception {
      stubConfig(List.of(location("/data/exports", "Exports")), List.of());
      ScheduleConfigChangePlanRequest req = request("add", List.of(
         createLocation("/data/exports/nested", "Nested")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("overlaps"));
   }

   @Test void resolveServerLocationDeleteThrowsOnUnknownKey() throws Exception {
      stubConfig(List.of(), List.of());
      ScheduleConfigChangePlanRequest req = request("remove", List.of(deleteLocation("/nope")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("no server location"));
   }

   @Test void resolveServerLocationDeleteNeverConsultsTimeRangeDependencyScan() throws Exception {
      stubConfig(List.of(location("/data/exports", "Exports")), List.of());
      ScheduleConfigChangePlanRequest req = request("remove", List.of(deleteLocation("/data/exports")));

      service.resolve(req, user);
      verify(gateway, never()).findTimeRangeDependents(anyString(), anyList());
   }

   // -------------------------------------------------------------------------
   // time ranges: duplicate name / duplicate default / auto-clear
   // -------------------------------------------------------------------------

   @Test void resolveTimeRangeCreateThrowsOnDuplicateName() throws Exception {
      stubConfig(List.of(), List.of(range("Morning", "06:00", "12:00", true)));
      ScheduleConfigChangePlanRequest req = request("add", List.of(
         createRange("Morning", "13:00", "18:00", false)));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("already exists"));
   }

   @Test void resolveTimeRangeCreateNewDefaultAutoClearsExistingDefault() throws Exception {
      stubConfig(List.of(), List.of(range("Morning", "06:00", "12:00", true)));
      ScheduleConfigChangePlanRequest req = request("add", List.of(
         createRange("Afternoon", "12:00", "18:00", true)));

      // Must NOT throw "Multiple default time ranges" -- the new default silently clears the old one,
      // matching the real EM dialog's own client-side behavior.
      ResolvedPlan plan = service.resolve(req, user);
      assertEquals(1, plan.changes().size());
   }

   @Test void resolveTimeRangeUpdateNonIdentityChangeSkipsDependencyScan() throws Exception {
      stubConfig(List.of(), List.of(range("Morning", "06:00", "12:00", false)));
      ScheduleConfigChangePlanRequest req = request("edit", List.of(
         updateRange("Morning", "Morning", "06:00", "12:00", true)));

      service.resolve(req, user);
      verify(gateway, never()).findTimeRangeDependents(anyString(), anyList());
   }

   // -------------------------------------------------------------------------
   // time ranges: the delete-dependency check (the most important behavior here)
   // -------------------------------------------------------------------------

   @Test void resolveTimeRangeDeleteRefusesWhenLiveTaskReferencesRange() throws Exception {
      stubConfig(List.of(), List.of(
         range("Morning", "06:00", "12:00", false),
         range("Afternoon", "12:00", "18:00", true)));
      when(gateway.findTimeRangeDependents(eq("Morning"), anyList()))
         .thenReturn(List.of(new TimeRangeDependency("admin:nightly-export", "Afternoon")));

      ScheduleConfigChangePlanRequest req = request("cleanup", List.of(deleteRange("Morning")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("nightly-export"), ex.getMessage());
      assertTrue(ex.getMessage().contains("Afternoon"), ex.getMessage());
      assertTrue(ex.getMessage().contains("force"), ex.getMessage());
   }

   @Test void resolveTimeRangeDeleteWithForceAllowsPlanAndCarriesAdvisory() throws Exception {
      stubConfig(List.of(), List.of(
         range("Morning", "06:00", "12:00", false),
         range("Afternoon", "12:00", "18:00", true)));
      when(gateway.findTimeRangeDependents(eq("Morning"), anyList()))
         .thenReturn(List.of(new TimeRangeDependency("admin:nightly-export", "Afternoon")));

      ScheduleConfigChangeRequest change = deleteRange("Morning");
      change.setForce(true);
      ScheduleConfigChangePlanRequest req = request("cleanup", List.of(change));

      ResolvedPlan plan = service.resolve(req, user);
      PlanChange only = plan.changes().get(0);
      assertTrue(only.description().contains("nightly-export"));
      assertTrue(only.description().contains("force=true"));
   }

   @Test void resolveTimeRangeIdentityChangingUpdateTriggersDependencyScan() throws Exception {
      stubConfig(List.of(), List.of(range("Morning", "06:00", "12:00", false)));
      when(gateway.findTimeRangeDependents(eq("Morning"), anyList()))
         .thenReturn(List.of(new TimeRangeDependency("admin:t1", null)));

      // startTime changes -- an identity-changing update (name/startTime/endTime), not just a
      // cosmetic edit -- must trigger the same scan a delete does.
      ScheduleConfigChangePlanRequest req = request("edit", List.of(
         updateRange("Morning", "Morning", "07:00", "12:00", false)));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("dangling reference"), ex.getMessage());
   }

   @Test void resolveTimeRangeDeleteThrowsOnUnknownKey() throws Exception {
      stubConfig(List.of(), List.of());
      ScheduleConfigChangePlanRequest req = request("cleanup", List.of(deleteRange("Nope")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("no time range"));
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private void stubConfig(List<ServerLocation> locations, List<TimeRangeModel> ranges) throws Exception {
      when(gateway.getFullConfig(user)).thenReturn(sampleModel(locations, ranges));
   }

   private static ScheduleConfigurationModel sampleModel(
      List<ServerLocation> locations, List<TimeRangeModel> ranges)
   {
      return ScheduleConfigurationModel.builder()
         .concurrency(1)
         .rmiPort(1099)
         .classpath("")
         .notificationEmail(false)
         .saveToDisk(false)
         .emailDelivery(false)
         .enableEmailBrowser(false)
         .minMemory(64)
         .maxMemory(512)
         .emailAddress("")
         .emailSubject("")
         .emailMessage("")
         .notifyIfDown(false)
         .notifyIfTaskFailed(false)
         .shareTaskInSameGroup(false)
         .deleteTaskOnlyByOwner(false)
         .timeRanges(ranges)
         .serverLocations(locations)
         .build();
   }

   private static ServerLocation location(String path, String label) {
      return ServerLocation.builder().path(path).label(label).build();
   }

   private static TimeRangeModel range(String name, String start, String end, boolean isDefault) {
      return TimeRangeModel.builder()
         .name(name).startTime(start).endTime(end).defaultRange(isDefault)
         .build();
   }

   private static Map<String, Object> locationSpec(String path, String label) {
      Map<String, Object> spec = new HashMap<>();
      spec.put("path", path);
      spec.put("label", label);
      return spec;
   }

   private static Map<String, Object> rangeSpec(String name, String start, String end, boolean isDefault) {
      Map<String, Object> spec = new HashMap<>();
      spec.put("name", name);
      spec.put("startTime", start);
      spec.put("endTime", end);
      spec.put("defaultRange", isDefault);
      return spec;
   }

   private static ScheduleConfigChangeRequest createLocation(String path, String label) {
      ScheduleConfigChangeRequest change = new ScheduleConfigChangeRequest();
      change.setUnitType(ScheduleConfigChangeRequest.UNIT_SERVER_LOCATION);
      change.setVerb(ScheduleConfigChangeRequest.VERB_CREATE);
      change.setSpec(locationSpec(path, label));
      return change;
   }

   private static ScheduleConfigChangeRequest deleteLocation(String path) {
      ScheduleConfigChangeRequest change = new ScheduleConfigChangeRequest();
      change.setUnitType(ScheduleConfigChangeRequest.UNIT_SERVER_LOCATION);
      change.setVerb(ScheduleConfigChangeRequest.VERB_DELETE);
      change.setKey(path);
      return change;
   }

   private static ScheduleConfigChangeRequest createRange(
      String name, String start, String end, boolean isDefault)
   {
      ScheduleConfigChangeRequest change = new ScheduleConfigChangeRequest();
      change.setUnitType(ScheduleConfigChangeRequest.UNIT_TIME_RANGE);
      change.setVerb(ScheduleConfigChangeRequest.VERB_CREATE);
      change.setSpec(rangeSpec(name, start, end, isDefault));
      return change;
   }

   private static ScheduleConfigChangeRequest updateRange(
      String key, String name, String start, String end, boolean isDefault)
   {
      ScheduleConfigChangeRequest change = new ScheduleConfigChangeRequest();
      change.setUnitType(ScheduleConfigChangeRequest.UNIT_TIME_RANGE);
      change.setVerb(ScheduleConfigChangeRequest.VERB_UPDATE);
      change.setKey(key);
      change.setSpec(rangeSpec(name, start, end, isDefault));
      return change;
   }

   private static ScheduleConfigChangeRequest deleteRange(String name) {
      ScheduleConfigChangeRequest change = new ScheduleConfigChangeRequest();
      change.setUnitType(ScheduleConfigChangeRequest.UNIT_TIME_RANGE);
      change.setVerb(ScheduleConfigChangeRequest.VERB_DELETE);
      change.setKey(name);
      return change;
   }

   private static ScheduleConfigChangePlanRequest request(
      String task, List<ScheduleConfigChangeRequest> changes)
   {
      ScheduleConfigChangePlanRequest req = new ScheduleConfigChangePlanRequest();
      req.setTask(task);
      req.setChanges(changes);
      return req;
   }
}
