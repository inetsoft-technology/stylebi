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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79782: {@code set_input_value} on an input whose Submit on Change is off left the sandbox
 * variable table holding the previous value, so the next viewsheet refresh copied that value back
 * onto the input. The write must commit the value into the table, as the browser's Submit button
 * does, only for such an input.
 *
 * <p>The stack is the real {@code VSInputService} and the real {@code ViewsheetSandbox}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
               InputValueServiceSubmitOnChangeTest.Config.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class InputValueServiceSubmitOnChangeTest {
   @Configuration
   static class Config {
      @Bean
      AssetDataCache assetDataCache() {
         return mock(AssetDataCache.class);
      }
   }

   private static final String SPINNER = "Months";

   @Test
   void submitOffInputValueIsCommittedToTheVariableTable() throws Exception {
      Stack s = Stack.create(false);

      s.setValue(34);

      assertEquals(34, s.selected());
      assertEquals(34, ((Number) s.box.getVariableTable().get(SPINNER)).intValue(),
                   "the value must reach the variable table a later refresh reads back");
      verify(s.lifecycle).execute(any(), eq(SPINNER), any(), any(ChangedAssemblyList.class),
                                  any(), eq(true));
   }

   @Test
   void submitOffValueSurvivesTheNextRefresh() throws Exception {
      Stack s = Stack.create(false);

      s.setValue(34);
      // what execute_script's post-run refresh does: reset the runtime, then initialise again
      s.box.resetRuntime();
      s.box.reset(new ChangedAssemblyList());

      assertEquals(34, s.selected());
   }

   @Test
   void submitOnInputIsNotCommittedAgain() throws Exception {
      Stack s = Stack.create(true);

      s.setValue(34);

      assertEquals(34, s.selected());
      verify(s.lifecycle, never()).createList(anyBoolean(), any(), any(), any());
      verify(s.lifecycle, never()).execute(any(), any(), any(), any(ChangedAssemblyList.class),
                                           any(), anyBoolean());
   }

   private static final class Stack {
      final SpinnerVSAssembly spinner;
      final ViewsheetSandbox box;
      final CoreLifecycleService lifecycle;
      final InputValueService service;
      final Principal principal = mock(Principal.class);

      static Stack create(boolean submitOnChange) throws Exception {
         return new Stack(submitOnChange);
      }

      private Stack(boolean submitOnChange) throws Exception {
         Viewsheet root = new Viewsheet();
         Method m = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
         m.setAccessible(true);
         m.invoke(root, new Worksheet());
         root.setEntry(new AssetEntry(
            AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
            "test/InputValueServiceSubmitOnChangeTest/root", null,
            OrganizationManager.getInstance().getCurrentOrgID()));

         spinner = new SpinnerVSAssembly(root, SPINNER);
         root.addAssembly(spinner);
         SpinnerVSAssemblyInfo info = (SpinnerVSAssemblyInfo) spinner.getVSAssemblyInfo();
         info.setMin("1");
         info.setMax("36");
         // an unbound input as the viewsheet model reports it: an empty table name, not null
         info.setTableName("");
         info.setSelectedObject(12);
         info.setSubmitOnChangeValue(Boolean.toString(submitOnChange));

         box = new ViewsheetSandbox(
            root, AbstractSheet.SHEET_RUNTIME_MODE, null, false, root.getEntry());
         box.reset(new ChangedAssemblyList());
         // the table as the last write made while the input submitted on change left it
         box.lockWrite();

         try {
            box.processChange(SPINNER, VSAssembly.OUTPUT_DATA_CHANGED, new ChangedAssemblyList());
         }
         finally {
            box.unlockWrite();
         }
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(root);
         when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
         when(rvs.getID()).thenReturn("rt1");

         VSObjectService objects = mock(VSObjectService.class);
         when(objects.getRuntimeViewsheet(anyString(), any())).thenReturn(rvs);
         when(objects.createList(anyBoolean(), any(), any(), any()))
            .thenAnswer(inv -> new ChangedAssemblyList());
         lifecycle = mock(CoreLifecycleService.class);
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

      void setValue(int value) throws Exception {
         service.setValue("tok", principal, SPINNER, List.of(value), null);
      }

      int selected() {
         return ((Number) ((SpinnerVSAssemblyInfo) spinner.getVSAssemblyInfo())
            .getSelectedObject()).intValue();
      }
   }
}
