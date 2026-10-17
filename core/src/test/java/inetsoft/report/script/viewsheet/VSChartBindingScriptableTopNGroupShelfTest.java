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
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #76862 (reopened, S6b): a dimension bound only on the chart's group shelf. The
 * documented script API is the 2-arg {@code setTopNReverse(col, bool)} /
 * {@code setTopN(col, n)} / {@code getTopN(col)} (no type), which reaches every shelf. The
 * 3-arg {@code Chart.BINDING_FIELD} form searches the x/y shelves only, so on a group-shelf
 * dimension it must not report success while changing nothing.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class VSChartBindingScriptableTopNGroupShelfTest {
   private VSChartBindingScriptable bindingInfo;
   private VSChartDimensionRef stateRef;
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

      // STATE on the group shelf only; x and y hold nothing named STATE
      stateRef = new VSChartDimensionRef(new AttributeRef("STATE"));
      stateRef.setRankingOptionValue(XCondition.TOP_N + "");
      stateRef.setRankingNValue("5");
      chartVSAssemblyInfo.getVSChartInfo().addGroupField(stateRef);

      viewsheet.addAssembly(chartVSAssembly);

      ViewsheetSandbox viewsheetSandbox = mock(ViewsheetSandbox.class);
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
   void bindingFieldOnGroupShelfDimensionIsNotASilentNoOp() {
      RuntimeException thrown = null;

      try {
         bindingInfo.setTopNReverse("STATE", true, ChartConstants.BINDING_FIELD);
      }
      catch(RuntimeException ex) {
         thrown = ex;
      }

      boolean applied = ("" + XCondition.BOTTOM_N).equals(stateRef.getRankingOptionValue());
      // the message is a supplier so that the getTopN read in it only runs on failure
      assertTrue(thrown != null || applied, () ->
         "setTopNReverse(\"STATE\", true, Chart.BINDING_FIELD) on a group-shelf dimension " +
         "neither applied nor failed: no exception, rankingOptionValue=" +
         stateRef.getRankingOptionValue() + " (TOP_N=" + XCondition.TOP_N + "), " +
         "getTopN(BINDING_FIELD)=" + bindingInfo.getTopN("STATE", ChartConstants.BINDING_FIELD));
   }

   @Test
   void typeZeroReachesGroupShelfDimension() {
      bindingInfo.setTopNReverse("STATE", true, 0);

      assertEquals("" + XCondition.BOTTOM_N, stateRef.getRankingOptionValue());
      assertTrue(bindingInfo.isTopNReverse("STATE", 0));
      assertEquals(5, bindingInfo.getTopN("STATE", 0));
   }

   @Test
   void documentedTwoArgSetTopNReverseReachesGroupShelf() {
      ctx.getBindings("js").putMember("setTopNReverse", bindingInfo.getMember("setTopNReverse"));

      assertDoesNotThrow(() -> ctx.eval("js", "setTopNReverse('STATE', true)"),
         "the documented 2-arg setTopNReverse(col, bool) must be accepted");
      assertEquals("" + XCondition.BOTTOM_N, stateRef.getRankingOptionValue());
   }

   @Test
   void documentedTwoArgSetTopNAndOneArgGetTopNReachGroupShelf() {
      ctx.getBindings("js").putMember("setTopN", bindingInfo.getMember("setTopN"));
      ctx.getBindings("js").putMember("getTopN", bindingInfo.getMember("getTopN"));

      assertDoesNotThrow(() -> ctx.eval("js", "setTopN('STATE', 3)"),
         "the documented 2-arg setTopN(col, n) must be accepted");
      assertEquals("3", stateRef.getRankingNValue());

      Value n = assertDoesNotThrow(() -> ctx.eval("js", "getTopN('STATE')"),
         "the documented 1-arg getTopN(col) must be accepted");
      assertEquals(3, n.asInt());
   }

   @Test
   void oneArgSetTopNReverseStaysRejected() {
      // #5447's real target: setTopNReverse(true) has no field and must keep failing loud
      ctx.getBindings("js").putMember("setTopNReverse", bindingInfo.getMember("setTopNReverse"));

      assertThrows(RuntimeException.class, () -> ctx.eval("js", "setTopNReverse(true)"));
      assertEquals("" + XCondition.TOP_N, stateRef.getRankingOptionValue());
   }
}
