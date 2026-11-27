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
package inetsoft.web.admin.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.admin.deploy.DeployService;
import inetsoft.web.admin.schedule.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #82325: building the portal Edit dialog's model of an action is a read. The action it is
 * given is the cached task's instance, so the read must not turn on its HTML message flag or
 * rewrite its message, which every later reader of that task (export, the admin plugin's read and
 * update) would otherwise see and an update that keeps the action would store.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleActionModelReadTest {
   @BeforeEach
   void setUp() {
      scheduleService = new ScheduleService(null, null, null, new ScheduleConditionService(), null,
                                            mock(DeployService.class), null, null, null, null,
                                            null, null, null);
      principal = mock(Principal.class);
      when(principal.getName()).thenReturn("oa~;~org1");
   }

   @Test
   void theModelIsHtmlButTheActionKeepsItsFlagAndMessage() {
      ViewsheetAction action = action("plain message", false);

      GeneralActionModel model = (GeneralActionModel) scheduleService.getActionModel(
         action, principal, true);

      assertTrue(model.htmlMessage());
      assertEquals("plain message", model.message());
      assertFalse(action.isMessageHtml());
      assertEquals("plain message", action.getMessage());
   }

   // the existing rewrite is kept as it is, now only in the model
   @Test
   void theRewrittenMessageIsOnlyInTheModel() {
      ViewsheetAction action = action("a/\n/b", false);

      GeneralActionModel model = (GeneralActionModel) scheduleService.getActionModel(
         action, principal, true);

      assertEquals("a<br/>b", model.message());
      assertTrue(model.htmlMessage());
      assertEquals("a/\n/b", action.getMessage());
      assertFalse(action.isMessageHtml());
   }

   @Test
   void anHtmlActionIsReadAsItIs() {
      ViewsheetAction action = action("a/\n/b", true);

      GeneralActionModel model = (GeneralActionModel) scheduleService.getActionModel(
         action, principal, true);

      assertTrue(model.htmlMessage());
      assertEquals("a/\n/b", model.message());
      assertTrue(action.isMessageHtml());
   }

   @Test
   void aMissingMessageStaysMissing() {
      ViewsheetAction action = action(null, false);

      GeneralActionModel model = (GeneralActionModel) scheduleService.getActionModel(
         action, principal, true);

      assertNull(model.message());
      assertTrue(model.htmlMessage());
      assertFalse(action.isMessageHtml());
   }

   // the model is the same one the editor has always been given
   @Test
   void theModelIsTheSameForAnActionBeforeAndAfterBeingRead() throws Exception {
      ViewsheetAction action = action("a/\n/b", false);
      ObjectMapper mapper = new ObjectMapper();

      String first = mapper.writeValueAsString(scheduleService.getActionModel(action, principal, true));
      String second = mapper.writeValueAsString(scheduleService.getActionModel(action, principal, true));

      assertEquals(first, second);
   }

   // the dialog's read path: the task the schedule manager returns is not changed
   @Test
   void theTaskTheDialogReadsIsLeftUnchanged() throws Exception {
      ScheduleService spied = spy(scheduleService);
      doReturn(new AssetEntry[0]).when(spied).getViewsheets(any());
      doReturn(List.of()).when(spied).getServerLocations(any());
      ViewsheetAction action = action("plain message", false);
      ScheduleTask task = new ScheduleTask("alice~;~org1:t1");
      task.addAction(action);
      ScheduleManager scheduleManager = mock(ScheduleManager.class);
      when(scheduleManager.getScheduleTask("t1")).thenReturn(task);
      AnalyticRepository repository = mock(AnalyticRepository.class);
      when(repository.getFolders(any(), any())).thenReturn(new RepositoryEntry[0]);
      ScheduleTaskService service = new ScheduleTaskService(
         repository, scheduleManager, spied, null, mock(SecurityProvider.class), null,
         mock(SecurityEngine.class));
      XPrincipal user = new XPrincipal(new IdentityID("alice", "org1"));

      TaskActionPaneModel model = service.getTaskActions("t1", user, false);

      GeneralActionModel shown = (GeneralActionModel) model.actions().get(0);
      assertTrue(shown.htmlMessage());
      assertFalse(((ViewsheetAction) task.getAction(0)).isMessageHtml());
      assertEquals("plain message", ((ViewsheetAction) task.getAction(0)).getMessage());
   }

   private static ViewsheetAction action(String message, boolean html) {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet("1^128^__NULL__^Examples/Census^org1");
      action.setEmails("a@example.com");
      action.setMessage(message);
      action.setMessageHtml(html);
      return action;
   }

   private ScheduleService scheduleService;
   private Principal principal;
}
