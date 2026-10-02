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

import inetsoft.sree.schedule.*;
import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.uql.viewsheet.VSBookmarkInfo;
import inetsoft.web.admin.deploy.DeployService;
import inetsoft.web.admin.schedule.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77405: saving a task that runs with the roles of a site admin is refused for a caller
 * that isn't a site admin when the save adds or changes an action or condition. A stored item
 * may not have the defaults an item built from the editor model has (e.g. a task created
 * through the API or an import), so an item the editor sends back unchanged must still be the
 * same as the stored item. Uses the real ScheduleService / ScheduleConditionService model
 * conversion.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleTaskContentChangeTest {
   @BeforeEach
   void setUp() {
      conditionService = new ScheduleConditionService();
      scheduleService = new ScheduleService(null, null, null, conditionService, null,
                                            mock(DeployService.class), null, null, null, null,
                                            null, null, null);
      service = new ScheduleTaskService(null, null, scheduleService, conditionService, null, null,
                                        null);
      principal = mock(Principal.class);
      when(principal.getName()).thenReturn("oa~;~org1");
   }

   @Test
   void itemsSentBackUnchanged_areNotAChange() throws Exception {
      ScheduleTask stored = storedTask();

      assertFalse(addsOrChangesContent(stored, resaved(stored)));
   }

   @Test
   void itemsRemoved_areNotAChange() throws Exception {
      ScheduleTask stored = storedTask();
      ScheduleTask saved = resaved(stored);
      saved.removeAction(0);
      saved.removeCondition(0);

      assertFalse(addsOrChangesContent(stored, saved));
   }

   @Test
   void changedAction_isAChange() throws Exception {
      ScheduleTask stored = storedTask();
      ScheduleTask saved = resaved(stored);
      ((ViewsheetAction) saved.getAction(0)).setEmails("other@example.com");

      assertTrue(addsOrChangesContent(stored, saved));
   }

   @Test
   void changedCondition_isAChange() throws Exception {
      ScheduleTask stored = storedTask();
      ScheduleTask saved = resaved(stored);
      saved.setCondition(0, TimeCondition.at(2, 0, 0));

      assertTrue(addsOrChangesContent(stored, saved));
   }

   @Test
   void addedAction_isAChange() throws Exception {
      ScheduleTask stored = storedTask();
      ScheduleTask saved = resaved(stored);
      saved.addAction(resaved(stored).getAction(0));

      assertTrue(addsOrChangesContent(stored, saved));
   }

   // review r1: models with parameters (DynamicValueModel has no equals, the embedded
   // parameters of a batch action are a two-dimensional array) are compared on their content

   @Test
   void parameterizedItemsSentBackUnchanged_areNotAChange() throws Exception {
      ScheduleTask stored = parameterizedTask();

      assertFalse(addsOrChangesContent(stored, resaved(stored)));
   }

   @Test
   void parameterizedItemsRemoved_areNotAChange() throws Exception {
      ScheduleTask stored = parameterizedTask();
      ScheduleTask saved = resaved(stored);
      saved.removeAction(1);
      saved.removeCondition(0);

      assertFalse(addsOrChangesContent(stored, saved));
   }

   @Test
   void changedViewsheetParameter_isAChange() throws Exception {
      ScheduleTask stored = parameterizedTask();
      ScheduleTask saved = resaved(stored);
      ((ViewsheetAction) saved.getAction(0)).getViewsheetRequest().setParameter("state", "NY");

      assertTrue(addsOrChangesContent(stored, saved));
   }

   @Test
   void changedEmbeddedBatchParameter_isAChange() throws Exception {
      ScheduleTask stored = parameterizedTask();
      ScheduleTask saved = resaved(stored);
      BatchAction batch = (BatchAction) saved.getAction(1);
      List<Map<String, Object>> embedded = new ArrayList<>();
      embedded.add(new LinkedHashMap<>(Map.of("region", "East")));
      embedded.add(new LinkedHashMap<>(Map.of("region", "South")));
      batch.setEmbeddedParameters(embedded);

      assertTrue(addsOrChangesContent(stored, saved));
   }

   /**
    * A stored task with a viewsheet action with parameters (one an array) and a bookmark, a
    * batch action with query and embedded parameters, and a condition with a time range.
    */
   private static ScheduleTask parameterizedTask() {
      ViewsheetAction viewsheet = new ViewsheetAction();
      viewsheet.setViewsheet("1^128^__NULL__^Examples/Census^org1");
      viewsheet.setEmails("a@example.com");
      viewsheet.getViewsheetRequest().setParameter("state", "NJ");
      viewsheet.getViewsheetRequest().setParameter("years", new String[] { "2020", "2021" });
      viewsheet.setBookmarks(new String[] { "bm1" });
      viewsheet.setBookmarkTypes(new int[] { VSBookmarkInfo.ALLSHARE });
      viewsheet.setBookmarkUsers(new IdentityID[] { new IdentityID("oa", "org1") });

      BatchAction batch = new BatchAction();
      batch.setTaskId("oa~;~org1:child");
      batch.setQueryParameters(new LinkedHashMap<>(Map.of("limit", "10")));
      List<Map<String, Object>> embedded = new ArrayList<>();
      embedded.add(new LinkedHashMap<>(Map.of("region", "East")));
      embedded.add(new LinkedHashMap<>(Map.of("region", "West")));
      batch.setEmbeddedParameters(embedded);

      TimeCondition condition = TimeCondition.at(1, 30, 0);
      condition.setTimeRange(new TimeRange("night", "01:00:00", "02:00:00", false));

      ScheduleTask task = new ScheduleTask("Task1");
      task.addAction(viewsheet);
      task.addAction(batch);
      task.addCondition(condition);
      return task;
   }

   /**
    * A stored task with a viewsheet action that doesn't have the defaults of an action built
    * from the editor model (attachment name, CSV options).
    */
   private static ScheduleTask storedTask() {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet("1^128^__NULL__^Examples/Census^org1");
      action.setEmails("a@example.com");
      action.setFrom("b@example.com");
      action.setSubject("subject");
      action.setMessage("hello\nworld");
      action.setFilePath(FileFormatInfo.EXPORT_TYPE_PDF,
                         new ServerPathInfo("ftp://files.example/out", "bob", "password"));
      ScheduleTask task = new ScheduleTask("Task1");
      task.addAction(action);
      task.addCondition(TimeCondition.at(1, 30, 0));
      return task;
   }

   /**
    * The task saveTask() builds when the editor sends back the models of the stored task.
    */
   private ScheduleTask resaved(ScheduleTask stored) throws Exception {
      ScheduleTask task = new ScheduleTask("Task1");

      for(int i = 0; i < stored.getActionCount(); i++) {
         ScheduleAction action = stored.getAction(i);
         task.addAction(scheduleService.getActionFromModel(
            scheduleService.getActionModel(action, principal, true), action, principal, LINK));
      }

      for(int i = 0; i < stored.getConditionCount(); i++) {
         task.addCondition(conditionService.getConditionFromModel(
            conditionService.getConditionModel(stored.getCondition(i), principal)));
      }

      return task;
   }

   private boolean addsOrChangesContent(ScheduleTask stored, ScheduleTask saved) {
      Boolean result = ReflectionTestUtils.invokeMethod(
         service, "addsOrChangesContent", stored, saved, LINK, principal, true);
      assertNotNull(result);
      return result;
   }

   private static final String LINK = "http://host/";
   private ScheduleConditionService conditionService;
   private ScheduleService scheduleService;
   private ScheduleTaskService service;
   private Principal principal;
}
