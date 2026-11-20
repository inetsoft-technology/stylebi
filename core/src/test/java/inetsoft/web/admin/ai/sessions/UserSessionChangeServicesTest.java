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
package inetsoft.web.admin.ai.sessions;

import inetsoft.uql.XPrincipal;
import inetsoft.util.Tool;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.user.UserService;
import inetsoft.web.admin.user.UserSessionMonitoringTableModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** Plan and apply behavior of the user-session logout area (Bug #81847). */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class UserSessionChangeServicesTest {
   @Mock private UserService userService;
   @Mock private XPrincipal user;

   private UserSessionChangePlanService planService;
   private UserSessionChangesetApplyService applyService;
   private MockedStatic<Tool> tool;
   private MockedStatic<Audit> audit;
   private Audit auditInstance;
   private List<UserSessionMonitoringTableModel> sessions;

   @BeforeEach void setUp() {
      planService = new UserSessionChangePlanService(userService);
      applyService = new UserSessionChangesetApplyService(planService, userService)
         .withVerifyTiming(200L, 10L);
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
      tool.when(Tool::getHost).thenReturn("test-host");
      audit = mockStatic(Audit.class, withSettings().strictness(Strictness.LENIENT));
      auditInstance = mock(Audit.class);
      audit.when(Audit::getInstance).thenReturn(auditInstance);
      sessions = new ArrayList<>(List.of(session("s1", "alice"), session("s2", "bob")));
      lenient().when(userService.getServerSessionModel(any(), any())).thenAnswer(inv -> sessions);
      // a real principal name is the identity key name~;~orgID, never the bare name
      lenient().when(user.getName()).thenReturn("admin~;~host-org");
   }

   @AfterEach void tearDown() {
      tool.close();
      audit.close();
   }

   @Test void resolveRejectsBlankTask() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> planService.resolve(request(" ", "s1"), user));
      assertTrue(ex.getMessage().contains("task"));
   }

   @Test void resolveRejectsEmptyChanges() {
      UserSessionChangePlanRequest req = new UserSessionChangePlanRequest();
      req.setTask("t");
      req.setChanges(List.of());
      assertThrows(IllegalArgumentException.class, () -> planService.resolve(req, user));
   }

   @Test void resolveRejectsUnknownSessionNamingTheField() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> planService.resolve(request("t", "nope"), user));
      assertTrue(ex.getMessage().contains("changes[0].sessionId"));
      assertTrue(ex.getMessage().contains("nope"));
   }

   @Test void resolveRejectsDuplicateSession() {
      assertThrows(IllegalArgumentException.class,
         () -> planService.resolve(request("t", "s1", "s1"), user));
   }

   @Test void resolveBuildsHighRiskPlanNamingUser() {
      ResolvedPlan plan = planService.resolve(request("t", "s1", "s2"), user);
      assertEquals(2, plan.changes().size());
      assertTrue(plan.requiresAgentSignoff());
      assertFalse(plan.requiresStorageBackup());
      assertEquals("high", plan.changes().get(0).risk());
      assertTrue(plan.changes().get(0).description().contains("alice"));
      assertTrue(plan.changes().get(0).description().contains("cannot be undone"));
   }

   @Test void planHashChangesWhenSessionSetChanges() {
      String a = planService.resolve(request("t", "s1"), user).planHash();
      String b = planService.resolve(request("t", "s2"), user).planHash();
      assertNotEquals(a, b);
   }

   @Test void planHashIgnoresSessionAge() {
      String a = planService.resolve(request("t", "s1"), user).planHash();
      sessions.set(0, UserSessionMonitoringTableModel.builder()
         .sessionID("s1").sessionLabel("s1").user("alice").userName("alice")
         .address("10.0.0.1").age("2:00:07").roles("r").groups("g")
         .organization("host-org").build());
      String b = planService.resolve(request("t", "s1"), user).planHash();
      assertEquals(a, b);
   }

   @Test void applyRequiresMatchingHash() {
      UserSessionApplyRequest req = apply("t", "wrong", "TKN:x", "ok", "s1");
      assertThrows(AdminChangesetApplyService.PlanHashMismatchException.class,
         () -> applyService.apply(req, user));
      verify(userService, never()).logoutSession(any(), any(), any());
   }

   @Test void applyRequiresReviewOutcome() {
      ResolvedPlan plan = planService.resolve(request("t", "s1"), user);
      UserSessionApplyRequest req = apply("t", plan.planHash(), plan.taskToken(), " ", "s1");
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> applyService.apply(req, user));
      assertTrue(ex.getMessage().contains("reviewOutcome"));
      verify(userService, never()).logoutSession(any(), any(), any());
   }

   @Test void applyVerifiesSessionGoneByReadBack() {
      ResolvedPlan plan = planService.resolve(request("t", "s1"), user);
      doAnswer(inv -> {
         sessions.removeIf(s -> s.sessionID().equals("s1"));
         return null;
      }).when(userService).logoutSession(isNull(), any(), any());

      UserSessionApplyResult result = applyService.apply(
         apply("t", plan.planHash(), plan.taskToken(), "ok", "s1"), user);
      assertEquals("applied", result.status());
      assertEquals("verified", result.results().get(0).status());
      ArgumentCaptor<AdminChangeRecord> captor = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(auditInstance).auditAdminChange(captor.capture(), eq(user));
      assertEquals("user-session", captor.getValue().getObjectType());
      assertEquals("s1", captor.getValue().getProperty());
      assertEquals(AdminChangeRecord.RISK_HIGH, captor.getValue().getRiskLevel());
   }

   @Test void applyWaitsForDelayedSessionListRefresh() {
      ResolvedPlan plan = planService.resolve(request("t", "s1"), user);
      java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
      // the cached session list only drops s1 on the third read, as after a debounced refresh
      when(userService.getServerSessionModel(any(), any())).thenAnswer(inv -> {
         if(reads.incrementAndGet() >= 3) {
            sessions.removeIf(s -> s.sessionID().equals("s1"));
         }

         return sessions;
      });

      UserSessionApplyResult result = applyService.apply(
         apply("t", plan.planHash(), plan.taskToken(), "ok", "s1"), user);
      assertEquals("applied", result.status());
      assertEquals("verified", result.results().get(0).status());
   }

   @Test void applyReportsFailureWhenSessionStillListed() {
      ResolvedPlan plan = planService.resolve(request("t", "s1"), user);
      // logoutSession swallows its own errors, so the session simply stays listed
      UserSessionApplyResult result = applyService.apply(
         apply("t", plan.planHash(), plan.taskToken(), "ok", "s1"), user);
      assertEquals("failed", result.status());
      assertTrue(result.results().get(0).error().contains("still listed"));
   }

   @Test void applyReportsPartialWhenOnlySomeSessionsGone() {
      ResolvedPlan plan = planService.resolve(request("t", "s1", "s2"), user);
      doAnswer(inv -> {
         sessions.removeIf(s -> s.sessionID().equals("s1"));
         return null;
      }).when(userService).logoutSession(isNull(), any(), any());

      UserSessionApplyResult result = applyService.apply(
         apply("t", plan.planHash(), plan.taskToken(), "ok", "s1", "s2"), user);
      assertEquals("partial", result.status());
   }

   @Test void previewWarnsForASessionOfTheSignedInAdministratorOnly() throws Exception {
      sessions.add(session("s3", "admin"));
      List<inetsoft.web.admin.ai.PlanChange> changes =
         planService.resolve(request("t", "s3", "s1"), user).changes();
      assertTrue(changes.get(0).description().contains("WARNING"));
      assertFalse(changes.get(1).description().contains("WARNING"));
   }

   @Test void previewUsesTheLoginIdentityOfAnSRPrincipal() throws Exception {
      inetsoft.sree.security.SRPrincipal srp = mock(inetsoft.sree.security.SRPrincipal.class);
      when(srp.getClientUserID()).thenReturn(new inetsoft.sree.security.IdentityID("admin", "host-org"));
      sessions.add(session("s3", "admin"));
      assertTrue(planService.resolve(request("t", "s3"), srp).changes().get(0)
                    .description().contains("WARNING"));
   }

   @Test void previewDoesNotWarnForTheSameNameInAnotherOrganization() throws Exception {
      sessions.add(session("s3", "admin", "other-org"));
      assertFalse(planService.resolve(request("t", "s3"), user).changes().get(0)
                     .description().contains("WARNING"));
   }

   @Test void previewWarnsForAGlobalAdministrator() throws Exception {
      when(user.getName()).thenReturn("admin~;~__GLOBAL__");
      sessions.add(session("s3", "admin", ""));
      sessions.add(session("s4", "admin", "host-org"));
      List<inetsoft.web.admin.ai.PlanChange> changes =
         planService.resolve(request("t", "s3", "s4"), user).changes();
      assertTrue(changes.get(0).description().contains("WARNING"));
      assertFalse(changes.get(1).description().contains("WARNING"));
   }

   private static UserSessionMonitoringTableModel session(String id, String name) {
      return session(id, name, "host-org");
   }

   private static UserSessionMonitoringTableModel session(String id, String name, String org) {
      return UserSessionMonitoringTableModel.builder()
         .sessionID(id).sessionLabel(id).user(name).userName(name).address("10.0.0.1")
         .age("1 hour").roles("r").groups("g").organization(org).build();
   }

   private static UserSessionChangePlanRequest request(String task, String... ids) {
      UserSessionChangePlanRequest req = new UserSessionChangePlanRequest();
      req.setTask(task);
      req.setChanges(changes(ids));
      return req;
   }

   private static UserSessionApplyRequest apply(String task, String hash, String token,
                                                String review, String... ids)
   {
      UserSessionApplyRequest req = new UserSessionApplyRequest();
      req.setTask(task);
      req.setChanges(changes(ids));
      req.setPlanHash(hash);
      req.setTaskToken(token);
      req.setReviewOutcome(review);
      return req;
   }

   private static List<UserSessionChangeRequest> changes(String... ids) {
      List<UserSessionChangeRequest> list = new ArrayList<>();

      for(String id : ids) {
         UserSessionChangeRequest c = new UserSessionChangeRequest();
         c.setSessionId(id);
         list.add(c);
      }

      return list;
   }
}
