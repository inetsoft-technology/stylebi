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

import inetsoft.report.internal.Util;
import inetsoft.sree.security.ResourceAction;
import inetsoft.util.Tool;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.schedule.model.ScheduleConfigurationModel;
import inetsoft.web.admin.schedule.model.ServerLocation;
import inetsoft.web.admin.schedule.model.ServerPathInfoModel;
import inetsoft.web.admin.security.ResourcePermissionModel;
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
   // Bug #79545: renaming a time range must carry its permissions to the new name
   // -------------------------------------------------------------------------

   @Test void renamingTimeRangeMarksPermissionsModifiedAndCarriesExistingGrant() throws Exception {
      ResourcePermissionModel grant = permissionModel();
      stubConfig(List.of(), List.of(rangeWithPermissions("Morning", grant)));
      ScheduleConfigChangePlanRequest req = request("rename", List.of(
         updateRange("Morning", "Morning2", "06:00", "12:00", false)));

      TimeRangeModel after = service.resolveFull(req, user).after().timeRanges().get(0);

      assertEquals("Morning2", after.name());
      assertEquals(Boolean.TRUE, after.modified());
      assertSame(grant, after.permissions());
   }

   @Test void timeRangeUpdateWithoutRenameLeavesModifiedNull() throws Exception {
      stubConfig(List.of(), List.of(rangeWithPermissions("Morning", permissionModel())));
      ScheduleConfigChangePlanRequest req = request("edit", List.of(
         updateRange("Morning", "Morning", "07:00", "12:00", false)));

      TimeRangeModel after = service.resolveFull(req, user).after().timeRanges().get(0);

      assertNull(after.modified());
      assertNull(after.permissions());
   }

   @Test void renamingTimeRangeWithNoStoredGrantDoesNotWritePermissions() throws Exception {
      stubConfig(List.of(), List.of(range("Morning", "06:00", "12:00", false)));
      ScheduleConfigChangePlanRequest req = request("rename", List.of(
         updateRange("Morning", "Morning2", "06:00", "12:00", false)));

      TimeRangeModel after = service.resolveFull(req, user).after().timeRanges().get(0);

      assertNull(after.modified());
      assertNull(after.permissions());
   }

   @Test void carryPermissionsOnRenamePrefersTheCallersPermissions() {
      ResourcePermissionModel stored = permissionModel();
      ResourcePermissionModel supplied = permissionModel();
      TimeRangeModel existing = rangeWithPermissions("Morning", stored);
      TimeRangeModel spec = TimeRangeModel.builder().from(existing).name("Morning2")
         .permissions(supplied).build();

      TimeRangeModel result = ScheduleConfigChangePlanService.carryPermissionsOnRename(existing, spec);

      assertEquals(Boolean.TRUE, result.modified());
      assertSame(supplied, result.permissions());
   }

   @Test void carryPermissionsOnRenameHonoursAnExplicitModifiedFlag() {
      TimeRangeModel existing = rangeWithPermissions("Morning", permissionModel());
      TimeRangeModel spec = TimeRangeModel.builder().from(existing).name("Morning2")
         .permissions((ResourcePermissionModel) null).modified(false).build();

      assertSame(spec, ScheduleConfigChangePlanService.carryPermissionsOnRename(existing, spec));
   }

   private static ResourcePermissionModel permissionModel() {
      return ResourcePermissionModel.builder()
         .permissions(List.of())
         .displayActions(EnumSet.of(ResourceAction.ACCESS))
         .securityEnabled(true)
         .requiresBoth(false)
         .derivePermissionLabel("")
         .grantReadToAllVisible(false)
         .build();
   }

   private static TimeRangeModel rangeWithPermissions(String name, ResourcePermissionModel permissions) {
      return TimeRangeModel.builder()
         .name(name).startTime("06:00").endTime("12:00").defaultRange(false)
         .permissions(permissions)
         .build();
   }

   // -------------------------------------------------------------------------
   // Bug #79032: the preview must mirror the server's placeholder-password rule
   // -------------------------------------------------------------------------

   private static final String OLD_KEY = "/qa/sl-pw|PW|ftpuser";

   private static ServerLocation storedLocationWithPassword() {
      return storedLocation("/qa/sl-pw", "ftpuser", "PW");
   }

   private static ServerLocation storedLocation(String path, String username, String label) {
      return ServerLocation.builder().path(path).label(label).pathInfoModel(
         ServerPathInfoModel.builder().path(path).username(username)
            .password(Util.PLACEHOLDER_PASSWORD).ftp(true).useCredential(false)
            .oldPasswordKey(path + "|" + label + "|" + username).build()).build();
   }

   private static ScheduleConfigChangeRequest updateWithPlaceholder(
      String path, String username, String oldPasswordKey)
   {
      return updateWithPlaceholder(path, path, username, oldPasswordKey);
   }

   private static ScheduleConfigChangeRequest updateWithPlaceholder(
      String key, String path, String username, String oldPasswordKey)
   {
      Map<String, Object> info = new HashMap<>();
      info.put("path", path);
      info.put("username", username);
      info.put("password", Util.PLACEHOLDER_PASSWORD);
      info.put("oldPasswordKey", oldPasswordKey);
      Map<String, Object> spec = locationSpec(path, "PW");
      spec.put("pathInfoModel", info);
      ScheduleConfigChangeRequest change = new ScheduleConfigChangeRequest();
      change.setUnitType(ScheduleConfigChangeRequest.UNIT_SERVER_LOCATION);
      change.setVerb(ScheduleConfigChangeRequest.VERB_UPDATE);
      change.setKey(key);
      change.setSpec(spec);
      return change;
   }

   @Test void placeholderPasswordWithChangedUsernameIsNotProjectedAsSet() throws Exception {
      stubConfig(List.of(storedLocationWithPassword()), List.of());

      PlanChange change = service.resolve(
         request("rename user", List.of(updateWithPlaceholder("/qa/sl-pw", "otheruser", OLD_KEY))),
         user).changes().get(0);

      assertTrue(change.currentValue().contains("password=(set)"), change.currentValue());
      assertTrue(change.proposedValue().contains("password=(none)"), change.proposedValue());
      assertTrue(change.description().contains("CLEARED"), change.description());
   }

   @Test void placeholderPasswordWithPathOnADifferentHostIsNotProjectedAsSet() throws Exception {
      String oldPath = "ftp://hosta.example.com/dir";
      stubConfig(List.of(storedLocation(oldPath, "ftpuser", "PW")), List.of());

      PlanChange change = service.resolve(
         request("move host", List.of(updateWithPlaceholder(
            oldPath, "ftp://hostb.example.com/dir", "ftpuser", oldPath + "|PW|ftpuser"))),
         user).changes().get(0);

      assertTrue(change.proposedValue().contains("password=(none)"), change.proposedValue());
      assertTrue(change.description().contains("CLEARED"), change.description());
   }

   @Test void placeholderPasswordWithStaleOldPasswordKeyIsRefused() throws Exception {
      stubConfig(List.of(storedLocationWithPassword()), List.of());
      ScheduleConfigChangePlanRequest req = request(
         "stale key", List.of(updateWithPlaceholder("/qa/sl-pw", "ftpuser", "/old|PW|gone")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("oldPasswordKey"), ex.getMessage());
      assertTrue(ex.getMessage().contains("changes[0]"), ex.getMessage());
   }

   @Test void placeholderPasswordWithMissingOldPasswordKeyIsRefused() throws Exception {
      stubConfig(List.of(storedLocationWithPassword()), List.of());
      ScheduleConfigChangePlanRequest req = request(
         "no key", List.of(updateWithPlaceholder("/qa/sl-pw", "ftpuser", null)));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("oldPasswordKey"), ex.getMessage());
      assertTrue(ex.getMessage().contains("missing"), ex.getMessage());
   }

   @Test void createWithPlaceholderPasswordAndNoMatchingKeyIsRefused() throws Exception {
      stubConfig(List.of(), List.of());
      ScheduleConfigChangeRequest change = updateWithPlaceholder("/qa/sl-new", "ftpuser", null);
      change.setVerb(ScheduleConfigChangeRequest.VERB_CREATE);
      change.setKey(null);
      ScheduleConfigChangePlanRequest req = request("create", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("oldPasswordKey"), ex.getMessage());
   }

   @Test void placeholderPasswordForAStoredLocationWithoutPasswordProjectsNoneWithoutWarning()
      throws Exception
   {
      ServerLocation stored = ServerLocation.builder().path("/qa/sl-pw").label("PW").pathInfoModel(
         ServerPathInfoModel.builder().path("/qa/sl-pw").username("ftpuser").ftp(true)
            .useCredential(false).oldPasswordKey(OLD_KEY).build()).build();
      stubConfig(List.of(stored), List.of());

      PlanChange change = service.resolve(
         request("same login", List.of(updateWithPlaceholder("/qa/sl-pw", "ftpuser", OLD_KEY))),
         user).changes().get(0);

      assertTrue(change.proposedValue().contains("password=(none)"), change.proposedValue());
      assertFalse(change.description().contains("WARNING"), change.description());
   }

   @Test void placeholderPasswordWithSameLoginIsStillProjectedAsSet() throws Exception {
      stubConfig(List.of(storedLocationWithPassword()), List.of());

      PlanChange change = service.resolve(
         request("same login", List.of(updateWithPlaceholder("/qa/sl-pw", "ftpuser", OLD_KEY))),
         user).changes().get(0);

      assertTrue(change.proposedValue().contains("password=(set)"), change.proposedValue());
      assertFalse(change.description().contains("WARNING"), change.description());
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   // -------------------------------------------------------------------------
   // Bug #79546: a password embedded in the path is split out exactly as the write stores it
   // -------------------------------------------------------------------------

   private static final String PW = "QaProbeSecret123";

   private static ScheduleConfigChangeRequest withInfo(
      ScheduleConfigChangeRequest change, Map<String, Object> extra)
   {
      Map<String, Object> info = new HashMap<>(extra);
      info.put("path", change.getSpec().get("path"));
      change.getSpec().put("pathInfoModel", info);
      return change;
   }

   private ScheduleConfigChangePlanService.Resolution resolveFull(ScheduleConfigChangeRequest... changes) throws Exception {
      return service.resolveFull(request("task", List.of(changes)), user);
   }

   private static void assertNoSecret(ScheduleConfigChangePlanService.Resolution r) {
      assertFalse(r.plan().changes().toString().contains(PW), r.plan().changes().toString());
      assertFalse(r.entries().toString().contains(PW), r.entries().toString());
   }

   @Test void createWithPasswordInPathSplitsItOutOfThePlan() throws Exception {
      stubConfig(List.of(), List.of());
      ScheduleConfigChangePlanService.Resolution r = resolveFull(
         createLocation("ftp://qauser:" + PW + "@127.0.0.1/qa-probe", "L"));

      PlanChange c = r.plan().changes().get(0);
      assertEquals("ftp://qauser@127.0.0.1/qa-probe", c.property());
      assertTrue(c.proposedValue().contains("path=ftp://qauser@127.0.0.1/qa-probe"), c.proposedValue());
      assertTrue(c.proposedValue().contains("ftp=true"), c.proposedValue());
      assertTrue(c.proposedValue().contains("username=qauser"), c.proposedValue());
      assertTrue(c.proposedValue().contains("password=(set)"), c.proposedValue());
      assertEquals("ftp://qauser@127.0.0.1/qa-probe", r.entries().get(0).afterKey());
      assertNoSecret(r);
   }

   @Test void sftpPathPasswordIsSplit() throws Exception {
      stubConfig(List.of(), List.of());
      ScheduleConfigChangePlanService.Resolution r = resolveFull(
         createLocation("sftp://qauser:" + PW + "@host/dir", "L"));

      assertEquals("sftp://qauser@host/dir", r.plan().changes().get(0).property());
      assertEquals("sftp://qauser@host/dir", r.entries().get(0).afterKey());
      assertNoSecret(r);
   }

   @Test void passwordWithAtSignInPathIsSplitOnTheLastAt() throws Exception {
      stubConfig(List.of(), List.of());
      ScheduleConfigChangePlanService.Resolution r = resolveFull(
         createLocation("ftp://qauser:p@ss" + PW + "@host/dir", "L"));

      assertEquals("ftp://qauser@host/dir", r.plan().changes().get(0).property());
      assertFalse(r.plan().changes().toString().contains("ss" + PW));
   }

   @Test void passwordLessUserInPathIsLeftAlone() throws Exception {
      stubConfig(List.of(), List.of());
      ScheduleConfigChangePlanService.Resolution r = resolveFull(
         createLocation("ftp://qauser@host/dir", "L"));

      assertEquals("ftp://qauser@host/dir", r.plan().changes().get(0).property());
      assertTrue(r.plan().changes().get(0).proposedValue().contains("path=ftp://qauser@host/dir"));
   }

   @Test void nonFtpPathWithAtAndColonIsLeftAlone() throws Exception {
      stubConfig(List.of(), List.of());
      String path = "\\\\srv\\share\\a@b:c";
      ScheduleConfigChangePlanService.Resolution r = resolveFull(createLocation(path, "L"));

      assertEquals(path, r.plan().changes().get(0).property());
      assertEquals(path, r.entries().get(0).afterKey());
   }

   @Test void pathPasswordBeatsThePasswordField() throws Exception {
      stubConfig(List.of(), List.of());
      Map<String, Object> extra = new HashMap<>();
      extra.put("username", "other");
      extra.put("password", "FieldPw");
      ScheduleConfigChangePlanService.Resolution r = resolveFull(withInfo(
         createLocation("ftp://qauser:" + PW + "@host/dir", "L"), extra));

      PlanChange c = r.plan().changes().get(0);
      assertEquals("ftp://qauser@host/dir", c.property());
      assertTrue(c.proposedValue().contains("username=qauser"), c.proposedValue());
      assertNoSecret(r);
   }

   @Test void pathPasswordBeatsTheSecretId() throws Exception {
      stubConfig(List.of(), List.of());
      Map<String, Object> extra = new HashMap<>();
      extra.put("useCredential", true);
      extra.put("secretId", "sec-1");
      ScheduleConfigChangePlanService.Resolution r = resolveFull(withInfo(
         createLocation("ftp://qauser:" + PW + "@host/dir", "L"), extra));

      PlanChange c = r.plan().changes().get(0);
      assertEquals("ftp://qauser@host/dir", c.property());
      assertTrue(c.proposedValue().contains("username=qauser"), c.proposedValue());
      assertFalse(c.proposedValue().contains("sec-1"), c.proposedValue());
      assertNoSecret(r);
   }

   @Test void updateWithPasswordInPathSplitsItOutOfThePlan() throws Exception {
      stubConfig(List.of(storedLocation("ftp://qauser@host/old", "qauser", "Old")), List.of());
      ScheduleConfigChangeRequest change = createLocation("ftp://qauser:" + PW + "@host/new", "L");
      change.setVerb(ScheduleConfigChangeRequest.VERB_UPDATE);
      change.setKey("ftp://qauser:" + PW + "@host/old");

      ScheduleConfigChangePlanService.Resolution r = resolveFull(change);

      assertEquals("ftp://qauser@host/old", r.plan().changes().get(0).property());
      assertEquals("ftp://qauser@host/new", r.entries().get(0).afterKey());
      assertNoSecret(r);
   }

   @Test void deleteKeyWithPasswordMatchesTheStoredPathAndIsNotEchoed() throws Exception {
      stubConfig(List.of(storedLocation("ftp://qauser@host/old", "qauser", "Old")), List.of());

      ScheduleConfigChangePlanService.Resolution r =
         resolveFull(deleteLocation("ftp://qauser:" + PW + "@host/old"));

      assertEquals("ftp://qauser@host/old", r.plan().changes().get(0).property());
      assertNoSecret(r);
   }

   @Test void unknownKeyWithPasswordIsRefusedWithoutEchoingThePassword() throws Exception {
      stubConfig(List.of(), List.of());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> resolveFull(deleteLocation("ftp://qauser:" + PW + "@host/missing")));
      assertFalse(ex.getMessage().contains(PW), ex.getMessage());
   }

   @Test void duplicateAndOverlapErrorsDoNotEchoThePathPassword() throws Exception {
      stubConfig(List.of(storedLocation("ftp://qauser@host/dir", "qauser", "Old")), List.of());

      IllegalArgumentException dup = assertThrows(IllegalArgumentException.class,
         () -> resolveFull(createLocation("ftp://qauser:" + PW + "@host/dir", "New")));
      assertTrue(dup.getMessage().contains("already exists"), dup.getMessage());
      assertFalse(dup.getMessage().contains(PW), dup.getMessage());

      IllegalArgumentException overlap = assertThrows(IllegalArgumentException.class,
         () -> resolveFull(createLocation("ftp://qauser:" + PW + "@host/dir/sub", "New")));
      assertFalse(overlap.getMessage().contains(PW), overlap.getMessage());
   }

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

   // -------------------------------------------------------------------------
   // Bug #79016: password/secretId reach the plan hash; caller text is separator-escaped
   // -------------------------------------------------------------------------

   private static ScheduleConfigChangeRequest createLocationWithCredential(
      String path, String label, Map<String, Object> credential)
   {
      Map<String, Object> info = new HashMap<>(credential);
      info.put("path", path);
      ScheduleConfigChangeRequest change = createLocation(path, label);
      change.getSpec().put("pathInfoModel", info);
      return change;
   }

   private static Map<String, Object> userAndPassword(String username, String password) {
      Map<String, Object> credential = new HashMap<>();
      credential.put("username", username);
      credential.put("password", password);
      return credential;
   }

   private static Map<String, Object> secretId(String secretId) {
      Map<String, Object> credential = new HashMap<>();
      credential.put("useCredential", true);
      credential.put("secretId", secretId);
      return credential;
   }

   private ResolvedPlan preview(ScheduleConfigChangeRequest change) throws Exception {
      stubConfig(List.of(), List.of());
      return service.resolve(request("task", List.of(change)), user);
   }

   @Test void serverLocationDifferentPasswordsHashDifferently() throws Exception {
      ResolvedPlan a = preview(createLocationWithCredential(
         "/qa/loc", "Loc", userAndPassword("ftpuser", "pw-one")));
      ResolvedPlan b = preview(createLocationWithCredential(
         "/qa/loc", "Loc", userAndPassword("ftpuser", "pw-two")));

      assertNotEquals(a.planHash(), b.planHash());
      // the displayed projection still shows only (set), never the value
      assertEquals(a.changes().get(0).proposedValue(), b.changes().get(0).proposedValue());
      assertTrue(a.changes().get(0).proposedValue().contains("password=(set)"));
      assertFalse(a.changes().get(0).proposedValue().contains("pw-one"));
   }

   @Test void serverLocationSamePasswordHashesTheSame() throws Exception {
      assertEquals(
         preview(createLocationWithCredential(
            "/qa/loc", "Loc", userAndPassword("ftpuser", "pw-one"))).planHash(),
         preview(createLocationWithCredential(
            "/qa/loc", "Loc", userAndPassword("ftpuser", "pw-one"))).planHash());
   }

   @Test void serverLocationDifferentSecretIdsHashDifferently() throws Exception {
      ResolvedPlan a = preview(createLocationWithCredential("/qa/loc", "Loc", secretId("cred-a")));
      ResolvedPlan b = preview(createLocationWithCredential("/qa/loc", "Loc", secretId("cred-b")));

      assertNotEquals(a.planHash(), b.planHash());
      assertEquals(a.changes().get(0).proposedValue(), b.changes().get(0).proposedValue());
   }

   @Test void serverLocationPasswordWithNoUsernameIsNotCountedAsEffective() throws Exception {
      // refused outright (a password is only persisted alongside a username), so nothing to hash
      assertThrows(IllegalArgumentException.class, () -> preview(createLocationWithCredential(
         "/qa/loc", "Loc", userAndPassword(null, "pw-one"))));
   }

   @Test void locationWithoutCredentialHashesLikeBefore() throws Exception {
      // no password and no secretId: the hash has no hidden segment, so it differs from a plan
      // that supplies one but is stable across identical previews
      ResolvedPlan plain = preview(createLocation("/qa/loc", "Loc"));
      ResolvedPlan again = preview(createLocation("/qa/loc", "Loc"));
      ResolvedPlan withPassword = preview(createLocationWithCredential(
         "/qa/loc", "Loc", userAndPassword("ftpuser", "pw-one")));

      assertEquals(plain.planHash(), again.planHash());
      assertNotEquals(plain.planHash(), withPassword.planHash());
   }

   @Test void updateWithAChangedPasswordChangesTheHash() throws Exception {
      stubConfig(List.of(location("/qa/loc", "Loc")), List.of());
      ScheduleConfigChangeRequest a = createLocationWithCredential(
         "/qa/loc", "Loc", userAndPassword("ftpuser", "pw-one"));
      a.setVerb(ScheduleConfigChangeRequest.VERB_UPDATE);
      a.setKey("/qa/loc");
      ScheduleConfigChangeRequest b = createLocationWithCredential(
         "/qa/loc", "Loc", userAndPassword("ftpuser", "pw-two"));
      b.setVerb(ScheduleConfigChangeRequest.VERB_UPDATE);
      b.setKey("/qa/loc");

      assertNotEquals(service.resolve(request("task", List.of(a)), user).planHash(),
                      service.resolve(request("task", List.of(b)), user).planHash());
   }

   @Test void serverLocationProjectionEscapesPathLabelAndUsername() {
      ServerLocation location = ServerLocation.builder()
         .path("/a;label=x").label("L;ftp=true").build();

      String projection = ScheduleConfigChangePlanService.projectServerLocation(location);

      assertTrue(projection.startsWith("path=/a\\;label\\=x;label=L\\;ftp\\=true;ftp=false;"),
                 projection);
   }

   @Test void serverLocationLabelCannotForgeTheNextFieldInTheHash() throws Exception {
      // these two differ only in where the ";username=u" boundary sits
      ResolvedPlan forged = preview(createLocation("/qa/loc", "Loc;ftp=false"));
      ResolvedPlan plain = preview(createLocation("/qa/loc", "Loc"));

      assertNotEquals(forged.changes().get(0).proposedValue(),
                      plain.changes().get(0).proposedValue());
      assertNotEquals(forged.planHash(), plain.planHash());
   }

   @Test void timeRangeProjectionEscapesTheName() {
      TimeRangeModel range = range("Morning;defaultRange=true", "06:00:00", "12:00:00", false);

      assertTrue(ScheduleConfigChangePlanService.projectTimeRange(range)
                    .startsWith("name=Morning\\;defaultRange\\=true;startTime="));
   }
}
