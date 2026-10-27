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

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.schedule.AbstractAction;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ApplyResult;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.schedule.ScheduleConditionService;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.admin.schedule.ScheduleTaskService;
import inetsoft.web.api.schedule.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79431: a task created through {@code POST /api/wiz/v1/admin/schedule/apply} stored no
 * LinkURI, so the emailLink/notifyLink/deliverLink flags were inert. Wires the REAL
 * {@link ScheduleChangesetApplyService} and {@link AdminScheduleGateway} (only the repository
 * layer is mocked) and asserts on the action that reaches {@code setScheduleTask}, not on a
 * mocked {@code addScheduleTask(..., any(), ...)}.
 */
@Tag("core")
class ScheduleChangesetApplyServiceLinkUriTest {
   @BeforeEach
   void setUp() throws Exception {
      AnalyticRepository repository = mock(AnalyticRepository.class, withSettings().lenient());
      scheduleManager = mock(ScheduleManager.class, withSettings().lenient());
      when(repository.checkPermission(any(), eq(ResourceType.SCHEDULER), eq("*"),
                                      eq(ResourceAction.ACCESS))).thenReturn(true);
      when(repository.getScheduleTask(anyString())).thenReturn(null);
      when(repository.checkPermission(any(), eq(ResourceType.SCHEDULE_TASK), anyString(),
                                      eq(ResourceAction.DELETE))).thenReturn(true);
      when(scheduleManager.getScheduleTask(anyString())).thenAnswer(inv -> stored.get());
      doAnswer(inv -> {
         stored.set(inv.getArgument(1));
         return null;
      }).when(scheduleManager).setScheduleTask(anyString(), any(), any());

      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      AdminScheduleGateway gateway = new AdminScheduleGateway(
         repository, scheduleManager,
         mock(ScheduleService.class, withSettings().lenient()),
         mock(ScheduleConditionService.class, withSettings().lenient()),
         mock(ScheduleTaskService.class, withSettings().lenient()), securityEngine);
      planService = new ScheduleChangePlanService(gateway, scheduleManager);
      AdminBackupService backupService = mock(AdminBackupService.class);
      when(backupService.backup(anyString())).thenReturn("snap-ref");
      service = new ScheduleChangesetApplyService(planService, gateway, scheduleManager,
                                                   backupService);

      owner = new IdentityID("caller", "host-org");
      user = mock(Principal.class, withSettings().lenient());
      when(user.getName()).thenReturn(owner.convertToKey());

      sreeEnv = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      tool.when(() -> Tool.decryptPassword(anyString()))
         .thenAnswer(inv -> ((String) inv.getArgument(0)).substring(4));
      auditStatic = mockStatic(Audit.class, withSettings().strictness(Strictness.LENIENT));
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));
      sessionStatic = mockStatic(XSessionService.class);
      XSessionService sessionService = mock(XSessionService.class);
      when(sessionService.createSessionID(anyString(), any())).thenReturn("session-id");
      sessionStatic.when(XSessionService::getService).thenReturn(sessionService);
      orgStatic = mockStatic(OrganizationManager.class);
      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.isSiteAdmin((Principal) any())).thenReturn(true);
      orgStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
   }

   @AfterEach
   void tearDown() {
      orgStatic.close();
      sessionStatic.close();
      auditStatic.close();
      tool.close();
      sreeEnv.close();
   }

   @Test
   void createStoresTheRequestLinkUriOnTheViewsheetAction() throws Exception {
      ApplyResult result = service.apply(request(), user, "http://host:8080/sree/");

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status(), String.valueOf(result.results()) + result.rollbackFailures());
      assertNotNull(stored.get());
      AbstractAction action = (AbstractAction) stored.get().getAction(0);
      assertEquals("http://host:8080/sree/", action.getLinkURI());
   }

   @Test
   void createWithoutALinkUriStoresNone() throws Exception {
      ApplyResult result = service.apply(request(), user, null);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status(), String.valueOf(result.results()) + result.rollbackFailures());
      assertNull(((AbstractAction) stored.get().getAction(0)).getLinkURI());
   }

   private ScheduleApplyRequest request() throws Exception {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet("1^128^__NULL__^Examples/Census^host-org");
      action.setEmailLink(true);
      action.setNotifyLink(true);

      CreateScheduleTaskRequest spec = new CreateScheduleTaskRequest();
      spec.setName("t1");
      spec.setOwner(owner);
      spec.setEnabled(false);
      TimeCondition condition = new TimeCondition();
      condition.setType(TimeCondition.Type.EVERY_DAY);
      condition.setHour(9);
      condition.setMinute(0);
      spec.setConditions(List.of(condition));
      spec.setActions(List.of(action));

      ScheduleChangeRequest change = new ScheduleChangeRequest();
      change.setVerb(ScheduleChangeRequest.VERB_CREATE);
      change.setSpec(spec);

      ScheduleChangePlanRequest probe = new ScheduleChangePlanRequest();
      probe.setTask("create a task");
      probe.setChanges(List.of(change));
      ResolvedPlan resolved = planService.resolve(probe, user);

      ScheduleApplyRequest req = new ScheduleApplyRequest();
      req.setTask("create a task");
      req.setChanges(List.of(change));
      req.setPlanHash(resolved.planHash());
      req.setTaskToken(resolved.taskToken());
      req.setReviewOutcome("approved");
      return req;
   }

   private final AtomicReference<inetsoft.sree.schedule.ScheduleTask> stored =
      new AtomicReference<>();
   private ScheduleManager scheduleManager;
   private ScheduleChangePlanService planService;
   private ScheduleChangesetApplyService service;
   private IdentityID owner;
   private Principal user;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<Tool> tool;
   private MockedStatic<Audit> auditStatic;
   private MockedStatic<XSessionService> sessionStatic;
   private MockedStatic<OrganizationManager> orgStatic;
}
