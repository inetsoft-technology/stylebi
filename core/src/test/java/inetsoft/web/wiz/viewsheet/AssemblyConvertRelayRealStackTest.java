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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.ExpressionValue;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.CurrentSelectionVSAssembly;
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.composer.vs.objects.controller.ComposerRangeSliderService;
import inetsoft.web.composer.vs.objects.controller.ComposerVSSelectionListService;
import inetsoft.web.composer.vs.objects.controller.ComposerVSTableService;
import inetsoft.web.viewsheet.controller.table.BaseTableService;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78406 round 5: {@code convert_assembly} replaces a selection list / range slider and
 * re-executes the tables it filters, but {@link AssemblyConvertService#convert} threw away the
 * warning {@link ViewsheetSessionService#mutate} captured, so a table whose script failed read as a
 * clean conversion. The test drives the REAL {@code AssemblyConvertService},
 * {@code ComposerVSSelectionListService} and {@code ComposerRangeSliderService} (EmbeddedVS1 plus a
 * selection list in a selection container, filtering a table whose JS pre-condition throws) through
 * a seam that does what {@code mutate} does around the mutation, and asserts the script failure is
 * returned in both directions.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class AssemblyConvertRelayRealStackTest {
   @Test
   void listToSliderReturnsTheScriptFailure() throws Exception {
      RuntimeViewsheet rvs = open();
      addSelectionList(rvs);
      addFailingExpressionCondition(rvs);

      assertScriptFailure(convert(rvs, "range_slider"));
   }

   @Test
   void sliderToListReturnsTheScriptFailure() throws Exception {
      RuntimeViewsheet rvs = open();
      addSelectionList(rvs);
      convert(rvs, "range_slider");
      addFailingExpressionCondition(rvs);

      assertScriptFailure(convert(rvs, "selection_list"));
   }

   @Test
   void convertsAreSilentWhenTheScriptIsFine() throws Exception {
      RuntimeViewsheet rvs = open();
      addSelectionList(rvs);

      assertFalse(convert(rvs, "range_slider").containsKey("warnings"));
      assertFalse(convert(rvs, "selection_list").containsKey("warnings"));
   }

   @SuppressWarnings("unchecked")
   private static void assertScriptFailure(Map<String, Object> result) {
      List<String> warnings = (List<String>) result.get("warnings");

      assertNotNull(warnings, "the failing table's warning must be relayed, got: " + result);
      assertFalse(warnings.isEmpty(), warnings.toString());
      // The table re-runs more than once inside a convert and getWarnings does not de-duplicate,
      // so match every entry rather than counting them.
      assertTrue(warnings.stream().allMatch(w -> w.startsWith(BaseTableService.SCRIPT_FAILURE_PREFIX)),
                 warnings.toString());
      assertTrue(warnings.stream().allMatch(w -> w.contains("undefinedFn")), warnings.toString());
   }

   private Map<String, Object> convert(RuntimeViewsheet rvs, String to) throws Exception {
      AssemblyConvertService service = new AssemblyConvertService(
         seam(rvs), new ComposerVSSelectionListService(viewsheetService, coreLifecycleService),
         new ComposerRangeSliderService(viewsheetService, coreLifecycleService),
         mock(ComposerVSTableService.class));

      return service.convert("tok", principal(), FILTER, to, "");
   }

   /**
    * Does around the mutation what {@link ViewsheetSessionService#mutate} does (run it under the
    * capturing dispatcher, return its warnings); the checkpoint and the broadcast are not part of
    * what is under test.
    */
   private static ViewsheetSessionService seam(RuntimeViewsheet rvs) throws Exception {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.mutate(anyString(), any(), any())).thenAnswer(inv -> {
         Principal agent = inv.getArgument(1);
         ViewsheetSessionService.Mutation mutation = inv.getArgument(2);
         return CapturingCommandDispatcher.withCapturingDispatcher(agent, dispatcher -> {
            mutation.run(rvs, rvs.getID(), dispatcher);
            return dispatcher.getWarnings();
         });
      });
      return sessions;
   }

   /** A selection list inside a selection container, both filtering the existing table. */
   private static void addSelectionList(RuntimeViewsheet rvs) {
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly(TABLE);
      DataRef type = table.getColumnSelection().getAttribute("type");
      assertNotNull(type, "type column");

      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, FILTER);
      list.setTableName(table.getTableName());
      list.setDataRef(type);
      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "Container1");
      container.setAssemblies(new String[] { FILTER });
      vs.addAssembly(container);
      vs.addAssembly(list);
      box.resetDataMap(TABLE);
   }

   private static void addFailingExpressionCondition(RuntimeViewsheet rvs) {
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      TableVSAssembly table = (TableVSAssembly) box.getViewsheet().getAssembly(TABLE);
      DataRef type = table.getColumnSelection().getAttribute("type");
      assertNotNull(type, "type column");

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
      box.resetDataMap(TABLE);
   }

   /** The runtime plus the principal it was opened with; a different one is "Invalid user". */
   private RuntimeViewsheet open() {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      user = rvs.getUser();
      return rvs;
   }

   private XPrincipal principal() {
      return (XPrincipal) user;
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   @Autowired ViewsheetService viewsheetService;
   @Autowired CoreLifecycleService coreLifecycleService;

   private Principal user;
   private static final String TABLE = "TableView1";
   private static final String FILTER = "Filter1";
}
