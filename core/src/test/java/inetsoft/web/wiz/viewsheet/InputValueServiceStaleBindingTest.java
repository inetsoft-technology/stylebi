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
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CheckBoxVSAssemblyInfo;
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
 * Bug #81696: a check box whose query was turned off still carries the query binding. Writing an
 * embedded date to it converted the value with the stale integer column type, so the write
 * produced nothing. With the query off, the embedded list's own type must be used.
 *
 * <p>The stack is the real {@code VSInputService} and the real {@code ViewsheetSandbox}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
               InputValueServiceStaleBindingTest.Config.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class InputValueServiceStaleBindingTest {
   @Configuration
   static class Config {
      @Bean
      AssetDataCache assetDataCache() {
         return mock(AssetDataCache.class);
      }
   }

   private static final String CHECK_BOX = "Days";

   @Test
   void embeddedDateIsSelectedWhenQueryIsOffAndBindingIsStale() throws Exception {
      Stack s = new Stack(ListInputVSAssembly.EMBEDDED_SOURCE);

      s.service.setValue("tok", s.principal, CHECK_BOX, List.of("2026-10-20"), null);

      Object[] held = s.info().getSelectedObjects();
      assertEquals(1, held.length, "the embedded date must be selected");
      assertEquals(java.sql.Timestamp.valueOf("2026-10-20 00:00:00").getTime(),
                   ((java.util.Date) held[0]).getTime());
   }

   private static final class Stack {
      final CheckBoxVSAssembly checkBox;
      final InputValueService service;
      final Principal principal = mock(Principal.class);

      private Stack(int stype) throws Exception {
         Viewsheet root = new Viewsheet();
         Method m = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
         m.setAccessible(true);
         m.invoke(root, new Worksheet());
         root.setEntry(new AssetEntry(
            AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
            "test/InputValueServiceStaleBindingTest/root", null,
            OrganizationManager.getInstance().getCurrentOrgID()));

         checkBox = new CheckBoxVSAssembly(root, CHECK_BOX);
         root.addAssembly(checkBox);
         CheckBoxVSAssemblyInfo info = info(checkBox);

         // an embedded date list ...
         ListData data = new ListData();
         data.setDataType(XSchema.DATE);
         data.setValues(new Object[] { java.sql.Timestamp.valueOf("2026-10-20 00:00:00"),
                                       java.sql.Timestamp.valueOf("2026-10-10 00:00:00") });
         data.setLabels(new String[] { "2026-10-20", "2026-10-10" });
         info.setListData(data);
         info.setDataType(XSchema.DATE);

         // ... plus the query binding the dialog leaves behind when the query is turned off
         AttributeRef attr = new AttributeRef(null, "ORDER_ID");
         attr.setDataType(XSchema.INTEGER);
         ColumnRef column = new ColumnRef(attr);
         ListBindingInfo binding = new ListBindingInfo();
         binding.setTableName("ORDERS");
         binding.setLabelColumn(column);
         binding.setValueColumn(column);
         info.setListBindingInfo(binding);
         info.setSourceType(stype);
         info.setSubmitOnChangeValue("true");

         ViewsheetSandbox box = new ViewsheetSandbox(
            root, AbstractSheet.SHEET_RUNTIME_MODE, null, false, root.getEntry());
         box.reset(new ChangedAssemblyList());

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

      CheckBoxVSAssemblyInfo info() {
         return info(checkBox);
      }

      private static CheckBoxVSAssemblyInfo info(CheckBoxVSAssembly assembly) {
         return (CheckBoxVSAssemblyInfo) assembly.getVSAssemblyInfo();
      }
   }
}
