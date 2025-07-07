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
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;

import inetsoft.util.Tool;
import inetsoft.web.composer.model.vs.DynamicValueModel;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@SreeHome()
public class BatchActionTest {

   /**
    * because didn't run task actually, so only test the method be execute.
    */
   @Test
   void testRunScheduleTaskWithEmbeddedParameters() throws Throwable {
      BatchAction batchAction = spy(BatchAction.class);
      batchAction.setTaskId("Test Batch Action");

      List<Map<String, Object>> embeddedParameters = createEMParameters();
      batchAction.setEmbeddedParameters(embeddedParameters);
      assertEquals(embeddedParameters, batchAction.getEmbeddedParameters());

      ScheduleTask tk1 = createScheduleTask("Test Batch Action", batchAction);
      ScheduleTask spyTask = spy(ScheduleTask.class);

      try (MockedStatic<ScheduleTask> mockedStatic = Mockito.mockStatic(ScheduleTask.class)) {
         mockedStatic.when(() -> ScheduleTask.copyScheduleTask(spyTask)).thenReturn(tk1);
         doNothing().when(tk1).run(admin);

         //  Use reflection to access the private method
         Method method = BatchAction.class.getDeclaredMethod("runScheduleTaskWithEmbeddedParameters",
                                                             ScheduleTask.class, Principal.class);
         method.setAccessible(true);
         method.invoke(batchAction, spyTask, admin);

         verify(tk1, atLeastOnce()).run(admin);
      }
   }

   /**
    * because didn't run task actually, so only test the method be execute.
    */
   @Test
   void testRunScheduleTaskWithQueryParameters() throws Throwable {
      BatchAction batchAction = spy(BatchAction.class);
      batchAction.setTaskId("Test Query Batch Action");

      AssetEntry queryEntry = AssetEntry.createAssetEntry("1^2^__NULL__^ws1^host-org");
      batchAction.setQueryEntry(queryEntry);
      assertEquals(queryEntry, batchAction.getQueryEntry());
      Map<String, Object> queryParameters = createQueryParameters();
      batchAction.setQueryParameters(queryParameters);
      assertEquals(queryParameters, batchAction.getQueryParameters());

      // mock AssetRepository and Worksheet to get the Worksheet from AssetEntry
      MockedStatic<AssetUtil> mockedAssetUtil = Mockito.mockStatic(AssetUtil.class);
      AssetRepository mockAssetRepository = mock(AssetRepository.class);
      Worksheet mockWorksheet = mock(Worksheet.class);
      mockedAssetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(mockAssetRepository);

      TableAssembly mockTableAssembly = mock(TableAssembly.class);
      when(mockWorksheet.getPrimaryAssembly()).thenReturn(mockTableAssembly);
      when(mockAssetRepository.getSheet(any(AssetEntry.class), eq(admin), eq(true), eq(AssetContent.ALL)))
         .thenReturn(mockWorksheet);

       //  Mock AssetQuery.createAssetQuery
      AssetQuery mockAssetQuery = mock(AssetQuery.class);
      MockedStatic<AssetQuery> mockedAssetQuery = Mockito.mockStatic(AssetQuery.class);
      mockedAssetQuery.when(() -> AssetQuery.createAssetQuery(
            eq(mockTableAssembly), eq(AssetQuerySandbox.RUNTIME_MODE), any(AssetQuerySandbox.class),
            eq(false), eq(-1L), eq(true), eq(false)
         )).thenReturn(mockAssetQuery);

      when(mockAssetQuery.getTableLens(any(VariableTable.class))).thenReturn(tableLens);

      //  Use reflection to access the private method
      ScheduleTask tk1 = createScheduleTask("Test Query Batch Action", batchAction);
      ScheduleTask spyTask = spy(ScheduleTask.class);

      try (MockedStatic<ScheduleTask> mockedStatic = Mockito.mockStatic(ScheduleTask.class)) {
         mockedStatic.when(() -> ScheduleTask.copyScheduleTask(spyTask)).thenReturn(tk1);
         doNothing().when(tk1).run(admin);

         //  Use reflection to access the private method, check is Query
         Method method = BatchAction.class.getDeclaredMethod("runScheduleTaskWithQueryParameters",
                                                             ScheduleTask.class, Principal.class);
         method.setAccessible(true);
         method.invoke(batchAction, spyTask, admin);

         verify(tk1, atLeastOnce()).run(admin);

         //check is Table
         AssetEntry tableEntry = mock(AssetEntry .class);
         when(tableEntry.isTable()).thenReturn(true);
         when(tableEntry.getName()).thenReturn("table1");
         when(mockWorksheet.getAssembly(tableEntry.getName())).thenReturn(mockTableAssembly);
         batchAction.setQueryEntry(tableEntry);

         method.invoke(batchAction, spyTask, admin);
         verify(tk1, atLeastOnce()).run(admin);
      } catch(Throwable e) {
         e.printStackTrace();
      } finally {
         mockedAssetUtil.close();
         mockedAssetQuery.close();
      }
   }

   private List<Map<String, Object>> createEMParameters() {
      List<Map<String, Object>> embeddedParameters = new ArrayList<>();
      Map<String, Object> param1 = new HashMap<>();
      param1.put("col1", "value1");
      param1.put("key2", 123);

      DynamicParameterValue dynamicParam1 = new DynamicParameterValue(2,
                                                                      DynamicValueModel.VALUE, "integer");
      param1.put("key3", dynamicParam1);
      embeddedParameters.add(param1);

      return embeddedParameters;
   }

   private Map<String, Object> createQueryParameters() {
      Map<String, Object> queryParameters = new HashMap<>();
      queryParameters.put("key1", "col1");

      return queryParameters;
   }

   private ScheduleTask createScheduleTask(String taskName, BatchAction batchAction) {
      TimeCondition condition = TimeCondition.at(10,35,59);

      ScheduleTask vstk1 = spy(ScheduleTask.class);
      vstk1.setName(taskName);
      vstk1.setOwner(new IdentityID("admin", "host-org"));
      vstk1.addCondition(condition);
      vstk1.setCondition(0, condition);

      ViewsheetAction viewsheetAction = spy(ViewsheetAction.class);
      viewsheetAction.setViewsheetName("vsActions");
      vstk1.addAction(viewsheetAction);
      vstk1.setAction(0, viewsheetAction);

      vstk1.addAction(batchAction );
      vstk1.setAction(1, batchAction);

      return vstk1;
   }
   SRPrincipal admin = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                                       new IdentityID[] { new IdentityID("Administrator", null)},
                                       new String[] {"g0"}, "host-org",
                                       Tool.getSecureRandom().nextLong());
   DefaultTableLens tableLens = new DefaultTableLens(new Object[][] {
      {"col1", "col2", "col3"},
      {"a", 1, 5.0},
      {"b", 3, 10.0},
      {"b", 1, 2.5},
      {"c", 1, 3.0}
   });
}
