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

package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.IntegrationTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.XCondition;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #76862: the six bindingInfo TopN functions take an optional trailing type (an omitted
 * type is 0, any shelf), and a Chart.BINDING_FIELD write to a field bound only on another
 * shelf fails loud, naming the call that reaches it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class VSChartBindingScriptableTopNTypeTest {
   private VSChartBindingScriptable bindingInfo;
   private VSChartDimensionRef stateRef;
   private VSChartDimensionRef yearRef;
   private ViewsheetSandbox viewsheetSandbox;
   private Context ctx;

   @BeforeEach
   void setUp() {
      ctx = Context.create("js");
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs-chart-1");

      ChartVSAssembly chartVSAssembly = new ChartVSAssembly();
      ChartVSAssemblyInfo chartVSAssemblyInfo =
         (ChartVSAssemblyInfo) chartVSAssembly.getVSAssemblyInfo();
      chartVSAssemblyInfo.setName("chart1");

      // STATE on the group shelf only; x and y hold nothing named STATE, YEAR on x
      stateRef = new VSChartDimensionRef(new AttributeRef("STATE"));
      stateRef.setRankingOptionValue(XCondition.TOP_N + "");
      stateRef.setRankingNValue("5");
      chartVSAssemblyInfo.getVSChartInfo().addGroupField(stateRef);
      yearRef = new VSChartDimensionRef(new AttributeRef("YEAR"));
      chartVSAssemblyInfo.getVSChartInfo().addXField(yearRef);

      viewsheet.addAssembly(chartVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs-chart-1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      ChartVSAScriptable chartVSAScriptable = new ChartVSAScriptable(viewsheetSandbox);
      chartVSAScriptable.setAssembly("chart1");
      chartVSAScriptable.addProperties();
      bindingInfo = (VSChartBindingScriptable) chartVSAScriptable.getMember("bindingInfo");
   }

   @AfterEach
   void tearDown() {
      ctx.close();
   }

   @Test
   void omittedTypeReachesEveryTopNFunction() {
      bind("setTopNReverse", "isTopNReverse", "setTopN", "getTopN", "setTopNSummaryCol",
           "getTopNSummaryCol");

      ctx.eval("js", "setTopNReverse('STATE', true); setTopN('STATE', 3); " +
                     "setTopNSummaryCol('STATE', 'Sum(NUM)');");

      assertEquals("" + XCondition.BOTTOM_N, stateRef.getRankingOptionValue());
      assertEquals("3", stateRef.getRankingNValue());
      assertEquals("Sum(NUM)", stateRef.getRankingColValue());
      assertTrue(ctx.eval("js", "isTopNReverse('STATE')").asBoolean());
      assertEquals(3, ctx.eval("js", "getTopN('STATE')").asInt());
      assertEquals("Sum(NUM)", ctx.eval("js", "getTopNSummaryCol('STATE')").asString());
   }

   @Test
   void callsWithoutAFieldStayRejected() {
      bind("setTopNReverse", "isTopNReverse", "setTopN", "getTopN");

      assertThrows(RuntimeException.class, () -> ctx.eval("js", "setTopN(3)"));
      assertThrows(RuntimeException.class, () -> ctx.eval("js", "getTopN()"));
      assertEquals("5", stateRef.getRankingNValue());

      bind("setTopNSummaryCol", "getTopNSummaryCol");
      String col = stateRef.getRankingColValue();
      assertThrows(RuntimeException.class, () -> ctx.eval("js", "setTopNSummaryCol('STATE')"));
      assertThrows(RuntimeException.class, () -> ctx.eval("js", "getTopNSummaryCol()"));
      assertEquals(col, stateRef.getRankingColValue());

      // the message VSD-018 pins for the 0-arg getter
      Exception ex = assertThrows(RuntimeException.class,
                                  () -> ctx.eval("js", "isTopNReverse()"));
      assertTrue(ex.getMessage().contains("expects 2 arguments, got 0"), ex.getMessage());
   }

   @Test
   void bindingFieldWriteOnAnotherShelfFailsLoud() {
      int type = ChartConstants.BINDING_FIELD;
      assertBindingFieldRejected(() -> bindingInfo.setTopNReverse("STATE", true, type));
      assertBindingFieldRejected(() -> bindingInfo.setTopN("STATE", 3, type));
      assertBindingFieldRejected(() -> bindingInfo.setTopNSummaryCol("STATE", "Sum(NUM)", type));

      assertEquals("" + XCondition.TOP_N, stateRef.getRankingOptionValue());
      assertEquals("5", stateRef.getRankingNValue());
   }

   @Test
   void bindingFieldReadOnAnotherShelfKeepsItsNotFoundValue() {
      int type = ChartConstants.BINDING_FIELD;
      assertFalse(bindingInfo.isTopNReverse("STATE", type));
      assertEquals(0, bindingInfo.getTopN("STATE", type));
      assertNull(bindingInfo.getTopNSummaryCol("STATE", type));
   }

   @Test
   void bindingFieldStillReachesTheXShelf() {
      bindingInfo.setTopN("YEAR", 2, ChartConstants.BINDING_FIELD);

      assertEquals("2", yearRef.getRankingNValue());
   }

   @Test
   void bindingFieldOnAFieldBoundNowhereIsNotAnError() {
      assertDoesNotThrow(() -> bindingInfo.setTopNReverse("NOPE", true,
                                                          ChartConstants.BINDING_FIELD));
      assertEquals(0, bindingInfo.getTopN("NOPE", ChartConstants.BINDING_FIELD));
   }

   @Test
   void rankingWriteResetsTheChartDataAndGraphOnlyWhenItChanges() {
      // the reset is made around the script function call, so call it as a script does
      bind("setTopNReverse", "setTopN", "setTopNSummaryCol");
      ctx.eval("js", "setTopNReverse('STATE', false); setTopN('STATE', 5);");
      verify(viewsheetSandbox, never()).resetDataMap(anyString());
      verify(viewsheetSandbox, never()).removeGraph(anyString());

      ctx.eval("js", "setTopNReverse('STATE', true);");
      verify(viewsheetSandbox, times(1)).resetDataMap("chart1");
      verify(viewsheetSandbox, times(1)).removeGraph("chart1");

      ctx.eval("js", "setTopNSummaryCol('STATE', 'Sum(NUM)');");
      verify(viewsheetSandbox, times(2)).resetDataMap("chart1");
   }

   private void assertBindingFieldRejected(Executable call) {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, call);
      assertTrue(ex.getMessage().contains("STATE is not on the x or y shelf"), ex.getMessage());
      assertTrue(ex.getMessage().contains("omit the type (or pass 0)"), ex.getMessage());
   }

   private void bind(String... names) {
      for(String name : names) {
         ctx.getBindings("js").putMember(name, bindingInfo.getMember(name));
      }
   }
}
