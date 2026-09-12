/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.sree.schedule;

import inetsoft.report.composition.execution.AssetQuery;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.sree.DynamicParameterValue;
import inetsoft.sree.security.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.Tool;
import inetsoft.test.*;
import inetsoft.web.composer.model.vs.DynamicValueModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Tier: [mockStatic] — AssetUtil, AssetQuery, ScheduleTask.copyScheduleTask, ScheduleManager.
 * Spring (@SreeHome) is required only to construct SRPrincipal; parameter-dispatch logic is
 * exercised with static mocks, not a live ScheduleManager or worksheet runtime.
 *
 * Intent vs implementation suspects: none confirmed for BatchAction at this time.
 */

/*
 * Cases deferred - require integration context or covered elsewhere:
 *
 * [BatchAction] writeXML / parseXML
 *             -> covered by ScheduleActionXmlRoundTripTest; NOT duplicated here
 * [BatchAction] run(Principal) full path with live ScheduleManager + real child task execution
 *             -> partial coverage via ScheduleTaskCompletionFlowTest harness; NOT duplicated here
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class BatchActionTest {

   private SRPrincipal admin;

   private final DefaultTableLens tableLens = new DefaultTableLens(new Object[][] {
      { "col1", "col2", "col3" },
      { "a", 1, 5.0 },
      { "b", 3, 10.0 }
   });

   @BeforeEach
   void setUpPrincipal() {
      admin = new SRPrincipal(
         new IdentityID("admin", Organization.getDefaultOrganizationID()),
         new IdentityID[] { new IdentityID("Administrator", null) },
         new String[] { "g0" },
         "host-org",
         Tool.getSecureRandom().nextLong());
   }

   // -------------------------------------------------------------------------
   // run(Principal) entry
   // -------------------------------------------------------------------------

   @Nested
   class RunEntryPoint {

      @Test
      void run_missingScheduleTask_doesNotThrow() throws Throwable {
         BatchAction action = new BatchAction();
         action.setTaskId("missing-task");

         try(MockedStatic<ScheduleManager> scheduleManager = mockStatic(ScheduleManager.class)) {
            ScheduleManager manager = mock(ScheduleManager.class);
            scheduleManager.when(ScheduleManager::getScheduleManager).thenReturn(manager);
            when(manager.getScheduleTask("missing-task")).thenReturn(null);

            assertDoesNotThrow(() -> action.run(admin));
            verify(manager).getScheduleTask("missing-task");
            verifyNoMoreInteractions(manager);
         }
      }
   }

   // -------------------------------------------------------------------------
   // via: run() -> runScheduleTaskWithEmbeddedParameters(...)
   // -------------------------------------------------------------------------

   @Nested
   class EmbeddedParameterDispatch {

      // via: runScheduleTaskWithEmbeddedParameters -> replaceVariablesInScheduleAction
      @Test
      void embeddedParameters_appliedToViewsheetRepletRequest() throws Throwable {
         BatchAction batchAction = new BatchAction();
         batchAction.setEmbeddedParameters(createEmbeddedParameters());

         ScheduleTask sourceTask = buildTaskWithViewsheetAction("embedded-task");
         ScheduleTask clonedTask = spy(buildTaskWithViewsheetAction("embedded-task"));

         try(MockedStatic<ScheduleTask> copyMock = mockStatic(ScheduleTask.class)) {
            copyMock.when(() -> ScheduleTask.copyScheduleTask(sourceTask)).thenReturn(clonedTask);
            doNothing().when(clonedTask).run(admin);

            invokeEmbedded(batchAction, sourceTask, admin);

            ViewsheetAction vsAction = (ViewsheetAction) clonedTask.getAction(0);
            assertEquals("value1", vsAction.getViewsheetRequest().getParameter("col1"));
            assertEquals(123, vsAction.getViewsheetRequest().getParameter("key2"));
            verify(clonedTask).run(admin);
         }
      }

      // via: runScheduleTaskWithEmbeddedParameters -> replaceVariablesInScheduleAction
      @Test
      void embeddedParameters_replaceEmailVariables() throws Throwable {
         BatchAction batchAction = new BatchAction();
         batchAction.setEmbeddedParameters(List.of(Map.of("col1", "north")));

         ScheduleTask sourceTask = buildTaskWithViewsheetAction("embedded-vars");
         ScheduleTask clonedTask = spy(buildTaskWithViewsheetAction("embedded-vars"));
         ViewsheetAction vsAction = (ViewsheetAction) clonedTask.getAction(0);
         vsAction.setEmails("region=$(col1)@example.com");

         try(MockedStatic<ScheduleTask> copyMock = mockStatic(ScheduleTask.class)) {
            copyMock.when(() -> ScheduleTask.copyScheduleTask(sourceTask)).thenReturn(clonedTask);
            doNothing().when(clonedTask).run(admin);

            invokeEmbedded(batchAction, sourceTask, admin);

            assertEquals("region=north@example.com", vsAction.getEmails());
         }
      }

      @Test
      void embeddedParameters_nullList_skipsCloneAndRun() throws Throwable {
         BatchAction batchAction = new BatchAction();
         batchAction.setEmbeddedParameters(null);

         ScheduleTask sourceTask = buildTaskWithViewsheetAction("embedded-null");

         try(MockedStatic<ScheduleTask> copyMock = mockStatic(ScheduleTask.class)) {
            invokeEmbedded(batchAction, sourceTask, admin);

            copyMock.verifyNoInteractions();
         }
      }
   }

   // -------------------------------------------------------------------------
   // via: run() -> runScheduleTaskWithQueryParameters(...)
   // -------------------------------------------------------------------------

   @Nested
   class QueryParameterDispatch {

      @Test
      void queryParameters_worksheetEntry_mapsFirstDataRowToRepletRequest() throws Throwable {
         BatchAction batchAction = new BatchAction();
         batchAction.setQueryEntry(AssetEntry.createAssetEntry("1^2^__NULL__^ws1^host-org"));
         batchAction.setQueryParameters(Map.of("key1", "col1"));

         ScheduleTask sourceTask = buildTaskWithViewsheetAction("query-ws");
         ScheduleTask clonedTask = spy(buildTaskWithViewsheetAction("query-ws"));

         try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class);
             MockedStatic<AssetQuery> assetQuery = mockStatic(AssetQuery.class);
             MockedStatic<ScheduleTask> copyMock = mockStatic(ScheduleTask.class))
         {
            stubWorksheetQuery(assetUtil, assetQuery);
            copyMock.when(() -> ScheduleTask.copyScheduleTask(sourceTask)).thenReturn(clonedTask);
            doNothing().when(clonedTask).run(admin);

            invokeQuery(batchAction, sourceTask, admin);

            ViewsheetAction vsAction = (ViewsheetAction) clonedTask.getAction(0);
            // One clonedTask.run() per data row; RepletRequest keeps the last row's values.
            assertEquals("b", vsAction.getViewsheetRequest().getParameter("key1"));
            verify(clonedTask, times(2)).run(admin);
         }
      }

      @Test
      void queryParameters_tableEntry_resolvesAssemblyAndRuns() throws Throwable {
         BatchAction batchAction = new BatchAction();

         AssetEntry tableEntry = mock(AssetEntry.class);
         when(tableEntry.isTable()).thenReturn(true);
         when(tableEntry.isWorksheet()).thenReturn(false);
         when(tableEntry.getScope()).thenReturn(AssetRepository.USER_SCOPE);
         when(tableEntry.getParentPath()).thenReturn("folder");
         when(tableEntry.getUser()).thenReturn(new IdentityID("admin", "host-org"));
         when(tableEntry.getName()).thenReturn("table1");

         batchAction.setQueryEntry(tableEntry);
         batchAction.setQueryParameters(Map.of("key1", "col1"));

         ScheduleTask sourceTask = buildTaskWithViewsheetAction("query-table");
         ScheduleTask clonedTask = spy(buildTaskWithViewsheetAction("query-table"));

         try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class);
             MockedStatic<AssetQuery> assetQuery = mockStatic(AssetQuery.class);
             MockedStatic<ScheduleTask> copyMock = mockStatic(ScheduleTask.class))
         {
            AssetRepository repository = mock(AssetRepository.class);
            Worksheet worksheet = mock(Worksheet.class);
            TableAssembly tableAssembly = mock(TableAssembly.class);

            assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(repository);
            when(repository.getSheet(any(AssetEntry.class), eq(admin), eq(true), eq(AssetContent.ALL)))
               .thenReturn(worksheet);
            when(worksheet.getAssembly("table1")).thenReturn(tableAssembly);
            when(tableAssembly.getName()).thenReturn("table1");

            AssetQuery query = mock(AssetQuery.class);
            assetQuery.when(() -> AssetQuery.createAssetQuery(
                  eq(tableAssembly), eq(AssetQuerySandbox.RUNTIME_MODE), any(AssetQuerySandbox.class),
                  eq(false), eq(-1L), eq(true), eq(false)))
               .thenReturn(query);
            when(query.getTableLens(any(VariableTable.class))).thenReturn(tableLens);

            copyMock.when(() -> ScheduleTask.copyScheduleTask(sourceTask)).thenReturn(clonedTask);
            doNothing().when(clonedTask).run(admin);

            invokeQuery(batchAction, sourceTask, admin);

            verify(clonedTask, atLeastOnce()).run(admin);
         }
      }

      @Test
      void queryParameters_nullQueryEntry_skipsCloneAndRun() throws Throwable {
         BatchAction batchAction = new BatchAction();
         batchAction.setQueryEntry(null);
         batchAction.setQueryParameters(Map.of("key1", "col1"));

         ScheduleTask sourceTask = buildTaskWithViewsheetAction("query-null-entry");

         try(MockedStatic<ScheduleTask> copyMock = mockStatic(ScheduleTask.class)) {
            invokeQuery(batchAction, sourceTask, admin);

            copyMock.verifyNoInteractions();
         }
      }

      @Test
      void queryParameters_emptyMap_skipsCloneAndRun() throws Throwable {
         BatchAction batchAction = new BatchAction();
         batchAction.setQueryEntry(AssetEntry.createAssetEntry("1^2^__NULL__^ws1^host-org"));
         batchAction.setQueryParameters(Collections.emptyMap());

         ScheduleTask sourceTask = buildTaskWithViewsheetAction("query-empty-map");

         try(MockedStatic<ScheduleTask> copyMock = mockStatic(ScheduleTask.class)) {
            invokeQuery(batchAction, sourceTask, admin);

            copyMock.verifyNoInteractions();
         }
      }
   }

   // -------------------------------------------------------------------------
   // equals contract
   // -------------------------------------------------------------------------

   @Nested
   class EqualsContract {

      @Test
      void equals_sameFields_returnsTrue() {
         BatchAction left = new BatchAction();
         left.setTaskId("admin~;~host-org:child");
         left.setQueryParameters(Map.of("p", "v"));
         left.setEmbeddedParameters(List.of(Map.of("k", "v")));

         BatchAction right = new BatchAction();
         right.setTaskId("admin~;~host-org:child");
         right.setQueryParameters(Map.of("p", "v"));
         right.setEmbeddedParameters(List.of(Map.of("k", "v")));

         assertEquals(left, right);
      }

      @Test
      void equals_differentTaskId_returnsFalse() {
         BatchAction left = new BatchAction();
         left.setTaskId("task-a");

         BatchAction right = new BatchAction();
         right.setTaskId("task-b");

         assertNotEquals(left, right);
      }
   }

   // -------------------------------------------------------------------------
   // Helpers
   // -------------------------------------------------------------------------

   private static List<Map<String, Object>> createEmbeddedParameters() {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("col1", "value1");
      row.put("key2", 123);
      row.put("key3", new DynamicParameterValue(2, DynamicValueModel.VALUE, "integer"));
      return List.of(row);
   }

   private ScheduleTask buildTaskWithViewsheetAction(String taskName) {
      ScheduleTask task = new ScheduleTask(taskName);
      task.setOwner(new IdentityID("admin", "host-org"));

      ViewsheetAction viewsheetAction = new ViewsheetAction();
      viewsheetAction.setViewsheetName("vsActions");
      task.addAction(viewsheetAction);

      return task;
   }

   private void stubWorksheetQuery(MockedStatic<AssetUtil> assetUtil,
                                   MockedStatic<AssetQuery> assetQuery) throws Exception
   {
      AssetRepository repository = mock(AssetRepository.class);
      Worksheet worksheet = mock(Worksheet.class);
      TableAssembly tableAssembly = mock(TableAssembly.class);

      assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(repository);
      when(repository.getSheet(any(AssetEntry.class), eq(admin), eq(true), eq(AssetContent.ALL)))
         .thenReturn(worksheet);
      when(worksheet.getPrimaryAssembly()).thenReturn(tableAssembly);
      when(tableAssembly.getName()).thenReturn("ws1");

      AssetQuery query = mock(AssetQuery.class);
      assetQuery.when(() -> AssetQuery.createAssetQuery(
            eq(tableAssembly), eq(AssetQuerySandbox.RUNTIME_MODE), any(AssetQuerySandbox.class),
            eq(false), eq(-1L), eq(true), eq(false)))
         .thenReturn(query);
      when(query.getTableLens(any(VariableTable.class))).thenReturn(tableLens);
   }

   // via: run() -> runScheduleTaskWithEmbeddedParameters
   private static void invokeEmbedded(BatchAction action, ScheduleTask task, Principal principal)
      throws Throwable
   {
      Method method = BatchAction.class.getDeclaredMethod(
         "runScheduleTaskWithEmbeddedParameters", ScheduleTask.class, Principal.class);
      method.setAccessible(true);

      try {
         method.invoke(action, task, principal);
      }
      catch(java.lang.reflect.InvocationTargetException e) {
         throw e.getCause();
      }
   }

   // via: run() -> runScheduleTaskWithQueryParameters
   private static void invokeQuery(BatchAction action, ScheduleTask task, Principal principal)
      throws Throwable
   {
      Method method = BatchAction.class.getDeclaredMethod(
         "runScheduleTaskWithQueryParameters", ScheduleTask.class, Principal.class);
      method.setAccessible(true);

      try {
         method.invoke(action, task, principal);
      }
      catch(java.lang.reflect.InvocationTargetException e) {
         throw e.getCause();
      }
   }
}
