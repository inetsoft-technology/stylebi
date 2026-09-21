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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #76862: Chart.bindingInfo.setTopN/setTopNReverse/isTopNReverse etc. are field-name +
 * type-discriminator-keyed methods, not 0/1-arg property accessors. A wrong-arity call must
 * be rejected loudly instead of silently no-op'ing (setTopNReverse(true) padding into
 * setTopNReverse("true", false, 0)) or NPEing (isTopNReverse() padding field to null).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class VSChartBindingScriptableTopNTest {
   private ChartVSAScriptable chartVSAScriptable;
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

      stateRef = new VSChartDimensionRef(new AttributeRef("STATE"));
      stateRef.setRankingOptionValue(XCondition.TOP_N + "");
      stateRef.setRankingNValue("5");
      chartVSAssemblyInfo.getVSChartInfo().addXField(stateRef);

      viewsheet.addAssembly(chartVSAssembly);

      ViewsheetSandbox viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs-chart-1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      chartVSAScriptable = new ChartVSAScriptable(viewsheetSandbox);
      chartVSAScriptable.setAssembly("chart1");
      chartVSAScriptable.addProperties();
      bindingInfo = (VSChartBindingScriptable) chartVSAScriptable.getMember("bindingInfo");
   }

   @AfterEach
   void tearDown() {
      ctx.close();
   }

   @Test
   void setTopNReverseRoundTripsAgainstRealRankingOptionValue() {
      assertFalse(bindingInfo.isTopNReverse("STATE", ChartConstants.BINDING_FIELD),
         "STATE starts ranked TOP_N, not reversed");

      bindingInfo.setTopNReverse("STATE", true, ChartConstants.BINDING_FIELD);

      assertEquals("" + XCondition.BOTTOM_N, stateRef.getRankingOptionValue(),
         "the correctly-called 3-arg setTopNReverse must flip the real ranking option " +
         "to BOTTOM_N, the same field the Composer's own Ranking dialog drives");
      assertTrue(bindingInfo.isTopNReverse("STATE", ChartConstants.BINDING_FIELD));

      bindingInfo.setTopNReverse("STATE", false, ChartConstants.BINDING_FIELD);

      assertEquals("" + XCondition.TOP_N, stateRef.getRankingOptionValue());
      assertFalse(bindingInfo.isTopNReverse("STATE", ChartConstants.BINDING_FIELD));
   }

   @Test
   void setTopNReverseRejectsWrongArityInsteadOfSilentNoOp() {
      // Bug #76862 symptom 1: a script calling setTopNReverse(true) -- a natural but wrong
      // 1-arg call shape -- must be rejected outright, not silently padded into
      // setTopNReverse("true", false, 0) (a no-op on a bogus "true"-named field).
      ctx.getBindings("js").putMember("setTopNReverse", bindingInfo.getMember("setTopNReverse"));

      assertThrows(RuntimeException.class, () -> ctx.eval("js", "setTopNReverse(true)"));
      assertEquals("" + XCondition.TOP_N, stateRef.getRankingOptionValue(),
         "a rejected wrong-arity call must leave the real ranking option untouched");
   }

   @Test
   void isTopNReverseRejectsWrongArityInsteadOfNpe() {
      // Bug #76862 symptom 2: isTopNReverse() with zero arguments must be rejected outright,
      // not padded into isTopNReverse(null, 0) and NPE inside createDataRef.
      ctx.getBindings("js").putMember("isTopNReverse", bindingInfo.getMember("isTopNReverse"));

      assertThrows(RuntimeException.class, () -> ctx.eval("js", "isTopNReverse()"));
   }

   @Test
   void isTopNReverseRejectsMissingFieldInsteadOfNpe() {
      // Bug #76862 symptom 2: isTopNReverse() with a null/blank field must fail loud with a
      // clear, field-named error, not an NPE inside createDataRef.
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> bindingInfo.isTopNReverse(null, ChartConstants.BINDING_FIELD));
      assertTrue(ex.getMessage().contains("isTopNReverse"));

      ex = assertThrows(IllegalArgumentException.class,
         () -> bindingInfo.isTopNReverse("", ChartConstants.BINDING_FIELD));
      assertTrue(ex.getMessage().contains("isTopNReverse"));
   }

   @Test
   void setTopNReverseRejectsMissingField() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> bindingInfo.setTopNReverse(null, true, ChartConstants.BINDING_FIELD));
      assertTrue(ex.getMessage().contains("setTopNReverse"));
   }
}
