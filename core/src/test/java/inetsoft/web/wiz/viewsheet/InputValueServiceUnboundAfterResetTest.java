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
package inetsoft.web.wiz.viewsheet;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.SpinnerVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.SpinnerVSAssemblyInfo;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80864: after {@code RuntimeViewsheet.resetRuntime()} (what saving the base worksheet does
 * to a connected viewsheet), the first value written to an unbound submit-on-change input was put
 * back to the value it held before. The property dialog stores an unbound input's table name as
 * an empty string, and the write skipped the variable table for it, so the initialising refresh
 * that follows the write re-applied the stale variable-table entry. A null and an empty table name
 * must both be treated as unbound.
 *
 * <p>The stack is the real {@code VSInputService} and the real {@code ViewsheetSandbox}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
               InputValueServiceUnboundAfterResetTest.Config.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class InputValueServiceUnboundAfterResetTest {
   @Configuration
   static class Config {
      @Bean
      AssetDataCache assetDataCache() {
         return mock(AssetDataCache.class);
      }
   }

   private static final String SPINNER = "Months";

   @ParameterizedTest
   @NullAndEmptySource
   void firstWriteAfterRuntimeResetIsKept(String tableName) throws Exception {
      Stack s = new Stack(tableName);

      s.box.resetRuntime();
      Map<String, Object> result = s.setValue(34);

      assertEquals(34, s.selected(), "the write must not be put back to the pre-reset value");
      assertEquals(false, result.get("sameAsBefore"));
      assertEquals(34, ((Number) s.box.getVariableTable().get(SPINNER)).intValue(),
                   "an unbound input writes its value under its own name");
   }

   @ParameterizedTest
   @NullAndEmptySource
   void writeOfTheHeldValueReportsSameAsBefore(String tableName) throws Exception {
      Stack s = new Stack(tableName);

      Map<String, Object> result = s.setValue(12);

      assertEquals(12, s.selected());
      assertEquals(true, result.get("sameAsBefore"));
   }

   private static final class Stack {
      final SpinnerVSAssembly spinner;
      final ViewsheetSandbox box;
      final InputValueService service;
      final Principal principal = mock(Principal.class);

      private Stack(String tableName) throws Exception {
         Viewsheet root = new Viewsheet();
         Method m = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
         m.setAccessible(true);
         m.invoke(root, new Worksheet());
         root.setEntry(new AssetEntry(
            AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
            "test/InputValueServiceUnboundAfterResetTest/root", null,
            OrganizationManager.getInstance().getCurrentOrgID()));

         spinner = new SpinnerVSAssembly(root, SPINNER);
         root.addAssembly(spinner);
         SpinnerVSAssemblyInfo info = (SpinnerVSAssemblyInfo) spinner.getVSAssemblyInfo();
         info.setMin("1");
         info.setMax("36");
         info.setTableName(tableName);
         info.setSelectedObject(12);
         info.setSubmitOnChangeValue("true");

         box = new ViewsheetSandbox(
            root, AbstractSheet.SHEET_RUNTIME_MODE, null, false, root.getEntry());
         box.reset(new ChangedAssemblyList());
         // the variable table as an earlier refresh left it: the input's value under its name
         box.lockWrite();

         try {
            box.processChange(SPINNER, VSAssembly.OUTPUT_DATA_CHANGED, new ChangedAssemblyList());
         }
         finally {
            box.unlockWrite();
         }

         assertEquals(12, ((Number) box.getVariableTable().get(SPINNER)).intValue());

         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(root);
         when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
         when(rvs.getID()).thenReturn("rt1");

         VSObjectService objects = mock(VSObjectService.class);
         when(objects.getRuntimeViewsheet(anyString(), any())).thenReturn(rvs);
         when(objects.createList(anyBoolean(), any(), any(), any()))
            .thenAnswer(inv -> new ChangedAssemblyList());
         CoreLifecycleService lifecycle = mock(CoreLifecycleService.class);
         when(lifecycle.getSandbox(any(), anyString())).thenCallRealMethod();
         when(lifecycle.createList(anyBoolean(), any(), any(), any()))
            .thenAnswer(inv -> new ChangedAssemblyList());

         VSInputService inputs = new VSInputService(
            objects, lifecycle, mock(ViewsheetService.class), mock(VSObjectPropertyService.class),
            mock(VSDialogService.class), mock(VSTrapService.class),
            mock(DataRefModelFactoryService.class), mock(VSAssemblyInfoHandler.class),
            mock(VSColumnHandler.class), mock(QueryManagerService.class));

         ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
         when(sessions.mutate(anyString(), any(), any())).thenAnswer(inv -> {
            ViewsheetSessionService.Mutation mutation = inv.getArgument(2);
            return CapturingCommandDispatcher.withCapturingDispatcher(principal, d -> {
               mutation.run(rvs, "rt1", d);
               return d.getWarnings();
            });
         });
         service = new InputValueService(sessions, inputs, lifecycle);
      }

      Map<String, Object> setValue(int value) throws Exception {
         return service.setValue("tok", principal, SPINNER, List.of(value), null);
      }

      int selected() {
         return ((Number) ((SpinnerVSAssemblyInfo) spinner.getVSAssemblyInfo())
            .getSelectedObject()).intValue();
      }
   }
}
