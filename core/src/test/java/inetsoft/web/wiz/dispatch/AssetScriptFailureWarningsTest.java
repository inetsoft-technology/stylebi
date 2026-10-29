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
package inetsoft.web.wiz.dispatch;

import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.viewsheet.controller.table.BaseTableService;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79623: assembly scripts that fail while a refresh resets the sheet were only recorded in
 * the thread-local {@code ASSET_EXCEPTIONS}, so the refresh reported no warning for them.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class AssetScriptFailureWarningsTest {
   @Test
   void aTextScriptCallingAMissingFunctionIsAWarningNamingTheAssemblyAndTheError() throws Exception {
      Viewsheet vs = box().getViewsheet();
      addScripted(new TextVSAssembly(vs, "T"), vs, "text = missingFn(value);");

      List<String> warnings = refresh();

      assertEquals(1, warnings.size(), warnings.toString());
      assertTrue(warnings.get(0).contains("Script execution error in assembly: T"), warnings.get(0));
      assertTrue(warnings.get(0).contains("missingFn is not defined"), warnings.get(0));
   }

   @Test
   void aChartScriptErrorIsAWarning() throws Exception {
      Viewsheet vs = box().getViewsheet();
      addScripted(new ChartVSAssembly(vs, "C"), vs, "missingFn();");

      List<String> warnings = refresh();

      assertEquals(1, warnings.size(), warnings.toString());
      assertTrue(warnings.get(0).contains("assembly: C"), warnings.get(0));
   }

   @Test
   void aTableScriptErrorIsReportedOnce() throws Exception {
      Viewsheet vs = box().getViewsheet();
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly(TABLE);
      DataRef type = table.getColumnSelection().getAttribute("type");
      ExpressionValue expression = new ExpressionValue();
      expression.setExpression("undefinedFn(1)");
      expression.setType(ExpressionValue.JAVASCRIPT);
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.EQUAL_TO);
      cond.setType(XSchema.STRING);
      cond.addValue(expression);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(type, cond, 0));
      table.setPreConditionList(list);
      box().resetDataMap(TABLE);

      List<String> warnings = refresh(true);

      long forTable = warnings.stream().filter(w -> w.contains("assembly: " + TABLE)).count();
      assertEquals(1, forTable, warnings.toString());
   }

   @Test
   void aNonScriptErrorIsNotAWarning() throws Exception {
      List<String> warnings = AssetScriptFailureWarnings.collect(() -> {
         WorksheetService.ASSET_EXCEPTIONS.get().add(new RuntimeException("query failed"));
         return new ArrayList<>();
      });

      assertEquals(List.of(), warnings);
   }

   @Test
   void theThreadLocalListIsRestored() throws Exception {
      List<Exception> previous = new ArrayList<>();
      WorksheetService.ASSET_EXCEPTIONS.set(previous);

      try {
         AssetScriptFailureWarnings.collect(() -> List.of());
         assertSame(previous, WorksheetService.ASSET_EXCEPTIONS.get());
         WorksheetService.ASSET_EXCEPTIONS.remove();
         AssetScriptFailureWarnings.collect(() -> List.of());
         assertNull(WorksheetService.ASSET_EXCEPTIONS.get());
      }
      finally {
         WorksheetService.ASSET_EXCEPTIONS.remove();
      }
   }

   private ViewsheetSandbox box() {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      return rvs.getViewsheetSandbox().orElseThrow();
   }

   private static void addScripted(VSAssembly assembly, Viewsheet vs, String script) {
      assembly.getVSAssemblyInfo().setScript(script);
      assembly.getVSAssemblyInfo().setScriptEnabled(true);
      vs.addAssembly(assembly);
   }

   private List<String> refresh() throws Exception {
      return refresh(false);
   }

   /** What the wiz refresh does: reset under the capturing dispatcher, optionally loading tables. */
   private List<String> refresh(boolean loadTable) throws Exception {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      ViewsheetSandbox box = box();

      return AssetScriptFailureWarnings.collect(() ->
         CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
            box.reset(null, box.getViewsheet().getAssemblies(), new ChangedAssemblyList(), true,
                      true, null);

            if(loadTable) {
               BaseTableService.loadTableData(rvs, TABLE, 0, 0, 100, null, dispatcher);
            }

            return dispatcher.getWarnings();
         }));
   }

   private static Principal principal() {
      return () -> "admin";
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   private static final String TABLE = "TableView1";
}
