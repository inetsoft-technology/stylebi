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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.controller.table.BaseTableService;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78378: a table whose query throws a script error is built to nothing and the failure is
 * only dispatched as an INFO toast, so a wiz write that ran it read as a plain success. The
 * capturing dispatcher now reports that one INFO kind as a warning and nothing else.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class CapturingCommandDispatcherScriptFailureTest {
   /** The real stack: a failing JS condition expression, the table path, the capturing seam. */
   @Test
   void aThrowingConditionExpressionIsReturnedAsAWarning() throws Exception {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      addFailingExpressionCondition(rvs);

      List<String> warnings =
         CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
            BaseTableService.loadTableData(rvs, TABLE, 0, 0, 100, null, dispatcher);
            assertTrue(dispatcher.getErrors().isEmpty(), "must not become an error");
            return dispatcher.getWarnings();
         });

      assertEquals(1, warnings.size(), warnings.toString());
      assertTrue(warnings.get(0).startsWith(BaseTableService.SCRIPT_FAILURE_PREFIX + TABLE),
                 warnings.get(0));
      assertTrue(warnings.get(0).contains("undefinedFn"), warnings.get(0));
   }

   @Test
   void warningsBodyWrapsTheCapturedWarning() throws Exception {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      addFailingExpressionCondition(rvs);

      List<String> warnings =
         CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
            BaseTableService.loadTableData(rvs, TABLE, 0, 0, 100, null, dispatcher);
            return dispatcher.getWarnings();
         });

      assertEquals(Map.of("warnings", warnings), ViewsheetSessionService.warningsBody(warnings));
   }

   @Test
   void aRepeatedFailureIsReportedOnce() throws Exception {
      List<String> warnings =
         CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
            dispatcher.sendCommand(info(FAILURE));
            dispatcher.sendCommand(info(FAILURE));
            return dispatcher.getWarnings();
         });

      assertEquals(1, warnings.size(), warnings.toString());
   }

   @Test
   void theWarningSaysTheTableMayShowNoData() throws Exception {
      List<String> warnings =
         CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
            dispatcher.sendCommand(info(FAILURE));
            return dispatcher.getWarnings();
         });

      assertTrue(warnings.get(0).startsWith(FAILURE));
      assertTrue(warnings.get(0).endsWith("The table may show no data; the failure can predate this call."),
                 warnings.get(0));
      assertFalse(warnings.get(0).contains("applied"), warnings.get(0));
   }

   @Test
   void aBenignInfoIsNeitherAWarningNorAnError() throws Exception {
      List<List<String>> out =
         CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
            dispatcher.sendCommand(info("The source table of Table1 is hidden"));
            dispatcher.sendCommand(info("alert() from a user script"));
            dispatcher.sendCommand(info("Loading timed out"));
            return List.of(dispatcher.getWarnings(), dispatcher.getErrors());
         });

      assertEquals(List.of(), out.get(0));
      assertEquals(List.of(), out.get(1));
   }

   /** Only an INFO is a script failure; the same text at another type keeps its own meaning. */
   @Test
   void theScriptFailureTextAtOtherTypesIsNotDoubleCounted() throws Exception {
      List<String> warnings =
         CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
            MessageCommand w = new MessageCommand();
            w.setType(MessageCommand.Type.WARNING);
            w.setMessage("plain warning");
            dispatcher.sendCommand(w);
            dispatcher.sendCommand(info(FAILURE));
            return dispatcher.getWarnings();
         });

      assertEquals(2, warnings.size(), warnings.toString());
      assertEquals("plain warning", warnings.get(0));
      assertTrue(warnings.get(1).startsWith(FAILURE));
   }

   @Test
   void warningsBodyIsNullWhenNothingWarned() {
      assertNull(ViewsheetSessionService.warningsBody(List.of()));
      assertNull(ViewsheetSessionService.warningsBody(null));
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

   private static MessageCommand info(String message) {
      MessageCommand command = new MessageCommand();
      command.setType(MessageCommand.Type.INFO);
      command.setMessage(message);
      return command;
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
   private static final String FAILURE = BaseTableService.SCRIPT_FAILURE_PREFIX + "T1 boom";
}
