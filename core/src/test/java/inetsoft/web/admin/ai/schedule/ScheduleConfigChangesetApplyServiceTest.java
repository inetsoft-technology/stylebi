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
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.TimeRange;
import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ApplyResult;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.schedule.SchedulerConfigurationService;
import inetsoft.web.admin.schedule.model.ScheduleConfigurationModel;
import inetsoft.web.admin.schedule.model.ServerLocation;
import inetsoft.web.admin.schedule.model.ServerPathInfoModel;
import inetsoft.web.viewsheet.model.dialog.schedule.TimeRangeModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Same mocking shape as {@code ScheduleFolderChangesetApplyServiceTest} (a REAL {@link
 * ScheduleConfigChangePlanService} wired to a mocked {@link AdminScheduleConfigGateway}, {@code
 * Tool}/{@code Audit} statics mocked), with one deliberate addition: the gateway's {@code
 * writeFullConfig}/{@code getFullConfig} pair is NOT a plain in-memory echo. It runs the model
 * through the REAL persistence round trip -- the real {@code
 * SchedulerConfigurationService#setServerLocations} encoder and the real {@link
 * SUtil#getServerLocations()} parser, sharing the real {@code server.save.locations} property, plus
 * the real {@link TimeRange#setTimeRanges}/{@link TimeRange#getTimeRanges()} pair -- against a
 * {@code SreeEnv} backed by an in-memory map.
 *
 * <p>That fidelity is the whole point of this class. Every failure it guards is a case where the
 * write SUCCEEDS but the re-read comes back in a different shape than the caller proposed, and
 * {@code ScheduleConfigChangesetApplyService}'s exact-projection comparison then reports "the
 * applied value did not verify after write" and rolls the entire batch back. An echoing fake would
 * pass all of these while the server still failed.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ScheduleConfigChangesetApplyServiceTest {
   @Mock private AdminScheduleConfigGateway gateway;
   @Mock private Principal user;
   private ScheduleConfigChangePlanService planService;
   private ScheduleConfigChangesetApplyService service;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<Tool> tool;
   private MockedStatic<Audit> auditStatic;
   private final Map<String, String> properties = new HashMap<>();

   @BeforeEach void setUp() throws Exception {
      planService = new ScheduleConfigChangePlanService(gateway);
      service = new ScheduleConfigChangesetApplyService(planService, gateway);

      sreeEnv = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      sreeEnv.when(() -> SreeEnv.getProperty(anyString()))
         .thenAnswer(inv -> properties.get(inv.<String>getArgument(0)));
      sreeEnv.when(() -> SreeEnv.setProperty(anyString(), any()))
         .thenAnswer(inv -> {
            String name = inv.getArgument(0);
            String value = inv.getArgument(1);

            if(value == null) {
               properties.remove(name);
            }
            else {
               properties.put(name, value);
            }

            return null;
         });

      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString())).thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      tool.when(() -> Tool.decryptPassword(anyString())).thenAnswer(inv -> {
         String s = inv.getArgument(0);

         if(!s.startsWith("TKN:")) {
            throw new IllegalArgumentException("not a token");
         }

         return s.substring(4);
      });

      Audit auditMock = mock(Audit.class);
      auditStatic = mockStatic(Audit.class, withSettings().strictness(Strictness.LENIENT));
      auditStatic.when(Audit::getInstance).thenReturn(auditMock);

      lenient().when(gateway.findTimeRangeDependents(anyString(), anyList())).thenReturn(List.of());
      lenient().when(gateway.getFullConfig(user)).thenAnswer(inv -> readBack());
      lenient().doAnswer(inv -> {
         persist(inv.getArgument(0));
         return null;
      }).when(gateway).writeFullConfig(any(ScheduleConfigurationModel.class), eq(user));
   }

   @AfterEach void tearDown() {
      sreeEnv.close();
      tool.close();
      auditStatic.close();
   }

   // -------------------------------------------------------------------------
   // time ranges -- both documented time formats must survive the round trip
   // -------------------------------------------------------------------------

   @Test void appliesATimeRangeCreateWithMinutePrecision() throws Exception {
      ApplyResult result = apply(createRange("Morning", "09:00", "12:00", false));

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals("09:00:00", readBack().timeRanges().get(0).startTime());
   }

   // "09:00:00" is the OTHER format the tool's own schema documents as valid. Both are
   // canonicalized at resolve time to "HH:mm:00", the one form a re-read produces (ISO_LOCAL_TIME
   // prints the zero seconds field) -- without that, the "09:00" call above wrote successfully and
   // was then rolled back.
   @Test void appliesATimeRangeCreateWithSecondsPrecision() throws Exception {
      ApplyResult result = apply(createRange("Morning", "09:00:00", "12:00:00", false));

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals("09:00:00", readBack().timeRanges().get(0).startTime());
   }

   @Test void refusesATimeRangeWithSubMinutePrecision() {
      ScheduleConfigChangePlanRequest req =
         planRequest(createRange("Morning", "09:30:15", "12:00", false));
      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> planService.resolve(req, user));

      assertTrue(ex.getMessage().contains("spec.startTime"), ex.getMessage());
      assertTrue(ex.getMessage().contains("sub-minute"), ex.getMessage());
   }

   // -------------------------------------------------------------------------
   // server locations
   // -------------------------------------------------------------------------

   // The reported failure: useCredential=false with username/password OMITTED, which the tool's
   // own schema documents as allowed. This used to write an EMPTY entry into the ";"-joined
   // property, so the location genuinely never existed afterwards.
   @Test void appliesAServerLocationCreateWithNoCredentials() throws Exception {
      ApplyResult result = apply(createLocation(locationSpec("/qa/sl-bs04", "BS04")));

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      List<ServerLocation> locations = readBack().serverLocations();
      assertEquals(1, locations.size());
      assertEquals("/qa/sl-bs04", locations.get(0).path());
      assertEquals("BS04", locations.get(0).label());
      assertNotNull(locations.get(0).pathInfoModel());
      assertNull(locations.get(0).pathInfoModel().username());
   }

   // The reporter's own working case -- it must stay working.
   @Test void appliesAServerLocationCreateWithUsernameAndPassword() throws Exception {
      Map<String, Object> spec = locationSpec("/qa/sl-bs05", "BS05");
      spec.put("username", "ftpuser");
      spec.put("password", "s3cret");
      spec.put("ftp", true);

      ApplyResult result = apply(createLocation(spec));

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      ServerPathInfoModel info = readBack().serverLocations().get(0).pathInfoModel();
      assertEquals("ftpuser", info.username());
      assertTrue(info.ftp());
      assertEquals(Util.PLACEHOLDER_PASSWORD, info.password());
   }

   // A username with no password must not persist the literal string "null" as the password.
   @Test void appliesAServerLocationCreateWithUsernameAndNoPassword() throws Exception {
      Map<String, Object> spec = locationSpec("/qa/sl-bs06", "BS06");
      spec.put("username", "ftpuser");

      ApplyResult result = apply(createLocation(spec));

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      ServerPathInfoModel info = readBack().serverLocations().get(0).pathInfoModel();
      assertEquals("ftpuser", info.username());
      assertNull(info.password());
   }

   // Canonicalization must never silently cost a credential: the write side cannot persist a
   // password without a username, so this is refused rather than normalized away.
   @Test void refusesAServerLocationPasswordWithNoUsername() {
      Map<String, Object> spec = locationSpec("/qa/sl-bs10", "BS10");
      spec.put("password", "s3cret");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> planService.resolve(planRequest(createLocation(spec)), user));
      assertTrue(ex.getMessage().contains("spec.username"), ex.getMessage());
   }

   // Likewise: useCredential=true with a blank secretId would store no credential at all.
   @Test void refusesAServerLocationUseCredentialWithNoSecretId() {
      Map<String, Object> spec = locationSpec("/qa/sl-bs11", "BS11");
      spec.put("useCredential", true);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> planService.resolve(planRequest(createLocation(spec)), user));
      assertTrue(ex.getMessage().contains("spec.secretId"), ex.getMessage());
   }

   // The read side strips trailing slashes, so a raw-path comparison could never match.
   @Test void appliesAServerLocationCreateWithATrailingSlash() throws Exception {
      ApplyResult result = apply(createLocation(locationSpec("/qa/sl-bs07/", "BS07")));

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals("/qa/sl-bs07", readBack().serverLocations().get(0).path());
   }

   // ftp is DERIVED on read from whether a credential is present, never persisted, so ftp=true
   // with no credential comes back false.
   @Test void appliesAServerLocationCreateWithFtpButNoCredentials() throws Exception {
      Map<String, Object> spec = locationSpec("/qa/sl-bs08", "BS08");
      spec.put("ftp", true);

      ApplyResult result = apply(createLocation(spec));

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertFalse(readBack().serverLocations().get(0).pathInfoModel().ftp());
   }

   // An update that resends the masked placeholder with the oldPasswordKey a read returned keeps
   // the real stored password rather than persisting the placeholder.
   @Test void appliesAServerLocationUpdateThatReplaysTheMaskedPassword() throws Exception {
      Map<String, Object> created = locationSpec("/qa/sl-bs09", "BS09");
      created.put("username", "ftpuser");
      created.put("password", "s3cret");
      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, apply(createLocation(created)).status());

      ServerPathInfoModel info = readBack().serverLocations().get(0).pathInfoModel();
      Map<String, Object> updated = locationSpec("/qa/sl-bs09-moved", "BS09");
      updated.put("username", "ftpuser");
      updated.put("password", Util.PLACEHOLDER_PASSWORD);
      updated.put("oldPasswordKey", info.oldPasswordKey());

      ApplyResult result = apply(updateLocation("/qa/sl-bs09", updated));

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals("/qa/sl-bs09-moved", readBack().serverLocations().get(0).path());
      assertEquals("s3cret", properties.get("server.save.locations").split("\\|")[3]);
   }

   // -------------------------------------------------------------------------
   // the gates are still gates
   // -------------------------------------------------------------------------

   @Test void applyThrowsPlanHashMismatchWhenHashMissing() {
      ScheduleConfigApplyRequest req = applyRequest(null, null, createRange("M", "09:00", "12:00", false));
      req.setReviewOutcome("approved");

      assertThrows(AdminChangesetApplyService.PlanHashMismatchException.class,
                   () -> service.apply(req, user));
   }

   @Test void applyThrowsWhenReviewOutcomeMissing() throws Exception {
      ScheduleConfigChangeRequest change = createRange("M", "09:00", "12:00", false);
      ResolvedPlan preview = planService.resolve(planRequest(change), user);
      ScheduleConfigApplyRequest req = applyRequest(preview.planHash(), preview.taskToken(), change);

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.apply(req, user));
      assertTrue(ex.getMessage().contains("reviewOutcome"));
   }

   // -------------------------------------------------------------------------
   // the real persistence round trip
   // -------------------------------------------------------------------------

   /** Runs the model through the REAL write encoders -- see this class's javadoc. */
   private void persist(ScheduleConfigurationModel model) throws Exception {
      Method setServerLocations = SchedulerConfigurationService.class
         .getDeclaredMethod("setServerLocations", List.class);
      setServerLocations.setAccessible(true);
      setServerLocations.invoke(new SchedulerConfigurationService(null, null, null, null),
                                model.serverLocations());

      List<TimeRange> ranges = model.timeRanges().stream()
         .map(r -> new TimeRange(r.name(), r.startTime(), r.endTime(), r.defaultRange()))
         .collect(Collectors.toList());
      TimeRange.setTimeRanges(ranges);
   }

   /** Reads back through the REAL parsers, mirroring {@code SchedulerConfigurationService#getConfiguration}. */
   private ScheduleConfigurationModel readBack() {
      List<TimeRangeModel> ranges = TimeRange.getTimeRanges().stream()
         .sorted()
         .map(r -> TimeRangeModel.builder()
            .name(r.getName())
            // identical to TimeRangeModel.Builder#from, without needing a Catalog
            .startTime(r.getStartTime().truncatedTo(ChronoUnit.MINUTES)
                          .format(DateTimeFormatter.ISO_LOCAL_TIME))
            .endTime(r.getEndTime().truncatedTo(ChronoUnit.MINUTES)
                        .format(DateTimeFormatter.ISO_LOCAL_TIME))
            .defaultRange(r.isDefault())
            .build())
         .collect(Collectors.toList());

      return model(SUtil.getServerLocations(), ranges);
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private ApplyResult apply(ScheduleConfigChangeRequest change) throws Exception {
      ResolvedPlan preview = planService.resolve(planRequest(change), user);
      ScheduleConfigApplyRequest req = applyRequest(preview.planHash(), preview.taskToken(), change);
      req.setReviewOutcome("approved");
      return service.apply(req, user);
   }

   private static ScheduleConfigurationModel model(
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

   private static Map<String, Object> locationSpec(String path, String label) {
      Map<String, Object> spec = new HashMap<>();
      spec.put("path", path);
      spec.put("label", label);
      return spec;
   }

   private static ScheduleConfigChangeRequest createLocation(Map<String, Object> spec) {
      ScheduleConfigChangeRequest change = new ScheduleConfigChangeRequest();
      change.setUnitType(ScheduleConfigChangeRequest.UNIT_SERVER_LOCATION);
      change.setVerb(ScheduleConfigChangeRequest.VERB_CREATE);
      change.setSpec(nest(spec));
      return change;
   }

   private static ScheduleConfigChangeRequest updateLocation(String key, Map<String, Object> spec) {
      ScheduleConfigChangeRequest change = new ScheduleConfigChangeRequest();
      change.setUnitType(ScheduleConfigChangeRequest.UNIT_SERVER_LOCATION);
      change.setVerb(ScheduleConfigChangeRequest.VERB_UPDATE);
      change.setKey(key);
      change.setSpec(nest(spec));
      return change;
   }

   /**
    * Moves the credential fields into the nested {@code pathInfoModel} the Java DTO uses, the same
    * way the calling tool does -- and, like it, omits {@code pathInfoModel} ENTIRELY when no
    * credential field was supplied, which is the shape the reported create failure used.
    */
   private static Map<String, Object> nest(Map<String, Object> flat) {
      Map<String, Object> spec = new HashMap<>();
      Map<String, Object> info = new HashMap<>();

      for(Map.Entry<String, Object> e : flat.entrySet()) {
         if("path".equals(e.getKey()) || "label".equals(e.getKey())) {
            spec.put(e.getKey(), e.getValue());
         }
         else {
            info.put(e.getKey(), e.getValue());
         }
      }

      if(!info.isEmpty()) {
         info.put("path", spec.get("path"));
         spec.put("pathInfoModel", info);
      }

      return spec;
   }

   private static ScheduleConfigChangeRequest createRange(
      String name, String start, String end, boolean isDefault)
   {
      ScheduleConfigChangeRequest change = new ScheduleConfigChangeRequest();
      change.setUnitType(ScheduleConfigChangeRequest.UNIT_TIME_RANGE);
      change.setVerb(ScheduleConfigChangeRequest.VERB_CREATE);
      Map<String, Object> spec = new HashMap<>();
      spec.put("name", name);
      spec.put("startTime", start);
      spec.put("endTime", end);
      spec.put("defaultRange", isDefault);
      change.setSpec(spec);
      return change;
   }

   private static ScheduleConfigChangePlanRequest planRequest(ScheduleConfigChangeRequest... changes) {
      ScheduleConfigChangePlanRequest req = new ScheduleConfigChangePlanRequest();
      req.setTask("a task");
      req.setChanges(List.of(changes));
      return req;
   }

   private static ScheduleConfigApplyRequest applyRequest(
      String planHash, String taskToken, ScheduleConfigChangeRequest... changes)
   {
      ScheduleConfigApplyRequest req = new ScheduleConfigApplyRequest();
      req.setTask("a task");
      req.setChanges(List.of(changes));
      req.setPlanHash(planHash);
      req.setTaskToken(taskToken);
      return req;
   }
}
