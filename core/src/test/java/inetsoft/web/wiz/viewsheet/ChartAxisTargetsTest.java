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
package inetsoft.web.wiz.viewsheet;

import inetsoft.graph.data.PairsDataSet;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.viewsheet.controller.chart.VSChartAxesVisibilityService;
import inetsoft.test.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79110: an axis target that matches no axis hid a different axis (the primary, or with
 * {@code secondary} the secondary, measure axis on a merged layout) and reported success.
 *
 * <p>These pin the pre-check against real {@link DefaultVSChartInfo} refs. Every refusal asserts
 * the three things the tool's contract promises: the message names the target, says "axis", and
 * lists the valid names.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartAxisTargetsTest {
   private static final String MONTH = "Month(Order:Date)";

   @Test
   void controlsStillResolveOnAMergedChart() {
      ChartVSAssembly chart = chart(false);

      assertDoesNotThrow(() -> ChartAxisTargets.requireAxisTarget(chart, MONTH, false));
      assertDoesNotThrow(() -> ChartAxisTargets.requireAxisTarget(chart, "Sum(Revenue)", false));
      assertDoesNotThrow(() -> ChartAxisTargets.requireAxisTarget(chart, "Sum(Orders)", true));
   }

   @Test
   void refusesANameThatMatchesNoAxisOnAMergedChart() {
      ChartVSAssembly chart = chart(false);

      for(String target : List.of("NoSuchColumn", "123")) {
         for(boolean secondary : new boolean[] { false, true }) {
            String message = refusal(chart, target, secondary);
            assertTrue(message.contains("'" + target + "'"), message);
            assertTrue(message.matches("(?s).*\\b(axis|axes)\\b.*"), message);
            assertTrue(message.contains(MONTH) && message.contains("Sum(Revenue)") &&
                       message.contains("Sum(Orders)"), "lists the valid names: " + message);
         }
      }
   }

   /** A separated layout did not fall through to a visible axis, but it still said ok. */
   @Test
   void refusesANameThatMatchesNoAxisOnASeparatedChart() {
      String message = refusal(chart(true), "NoSuchColumn", false);

      assertTrue(message.contains("'NoSuchColumn'"), message);
      assertTrue(message.contains("axis"), message);
   }

   /** The raw column of a Month-grouped x is refused, with a hint at the qualified name. */
   @Test
   void refusesTheRawColumnOfADateGroupedDimensionAndSuggestsTheQualifiedName() {
      String message = refusal(chart(false), "Order:Date", false);

      assertTrue(message.contains("'Order:Date'"), message);
      assertTrue(message.contains("Did you mean '" + MONTH + "'"), message);
   }

   /** Exact and case-sensitive, as hideAxis is: a lenient pre-check would let the fallthrough in. */
   @Test
   void matchingIsExactAndCaseSensitive() {
      ChartVSAssembly chart = chart(false);

      assertNotNull(refusal(chart, "month(order:date)", false));
      assertNotNull(refusal(chart, "sum(revenue)", false));
      assertNotNull(refusal(chart, " Sum(Revenue)", false));
   }

   /** The axis types are not column names; a column named like one still resolves. */
   @Test
   void refusesAxisTypeNamesAndSaysToUseTheColumnName() {
      for(String target : List.of("x", "y", "x2", "y2")) {
         String message = refusal(chart(false), target, false);
         assertTrue(message.contains("axis type"), message);
      }
   }

   @Test
   void refusesAMeasureWhoseSecondaryFlagNamesTheOtherAxisOnAMergedChart() {
      ChartVSAssembly chart = chart(false);

      String primaryAsSecondary = refusal(chart, "Sum(Revenue)", true);
      assertTrue(primaryAsSecondary.contains("Pass secondary: false"), primaryAsSecondary);

      String secondaryAsPrimary = refusal(chart, "Sum(Orders)", false);
      assertTrue(secondaryAsPrimary.contains("Pass secondary: true"), secondaryAsPrimary);
   }

   /** The separated layout takes each axis from its own ref, so the flag is not consulted. */
   @Test
   void doesNotCheckTheSecondaryFlagOnASeparatedChart() {
      ChartVSAssembly chart = chart(true);

      assertDoesNotThrow(() -> ChartAxisTargets.requireAxisTarget(chart, "Sum(Revenue)", true));
      assertDoesNotThrow(() -> ChartAxisTargets.requireAxisTarget(chart, "Sum(Orders)", false));
   }

   /** A dimension owns its descriptor, so the flag does not matter for it. */
   @Test
   void doesNotCheckTheSecondaryFlagForADimension() {
      assertDoesNotThrow(() -> ChartAxisTargets.requireAxisTarget(chart(false), MONTH, true));
   }

   @Test
   void everyListedAxisNameResolvesThroughTheHandlersOwnLookup() {
      ChartVSAssembly chart = chart(false);
      List<String> names = ChartAxisTargets.axisNames(chart.getVSChartInfo());

      assertEquals(List.of(MONTH, "Sum(Revenue)", "Sum(Orders)"), names);

      for(String name : names) {
         assertNotNull(VSChartAxesVisibilityService.findAxisRef(
            chart.getChartInfo(), chart.getVSChartInfo(), name), name);
      }
   }

   /** The date-comparison ref is what hideAxis looks up first, so the pre-check must accept it. */
   @Test
   void acceptsADateComparisonBindingRef() {
      ChartVSAssembly chart = chart(false);
      ChartRef dc = new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, "DcField")));
      when(chart.getChartInfo().getDCBIndingRef("DcField")).thenReturn(dc);

      assertDoesNotThrow(() -> ChartAxisTargets.requireAxisTarget(chart, "DcField", false));
   }

   /** A runtime-only field (a dynamically created one) is the last lookup hideAxis tries. */
   @Test
   void acceptsARuntimeAxisField() {
      ChartVSAssembly chart = chart(false);
      chart.getVSChartInfo().setRTXFields(new ChartRef[] {
         new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, "Dyn"))) });

      assertDoesNotThrow(() -> ChartAxisTargets.requireAxisTarget(chart, "Dyn", false));
   }

   @Test
   void radarLabelAxisIsAcceptedOnARadarChartOnly() {
      ChartVSAssembly radar = chart(new RadarVSChartInfo());
      assertDoesNotThrow(
         () -> ChartAxisTargets.requireAxisTarget(radar, "_Parallel_Label_", false));
      assertTrue(ChartAxisTargets.axisNames(radar.getVSChartInfo()).contains("_Parallel_Label_"));

      assertNotNull(refusal(chart(false), "_Parallel_Label_", false));
   }

   /** The x/y measure-name pseudo columns resolve to the chart descriptor, except on a matrix. */
   @Test
   void measureNamePseudoColumnsAreAcceptedExceptOnAScatterMatrix() {
      ChartVSAssembly chart = chart(false);

      assertDoesNotThrow(() -> ChartAxisTargets.requireAxisTarget(
         chart, PairsDataSet.XMEASURE_NAME, false));
      assertDoesNotThrow(() -> ChartAxisTargets.requireAxisTarget(
         chart, PairsDataSet.YMEASURE_NAME, false));

      DefaultVSChartInfo matrix = new DefaultVSChartInfo();
      matrix.addXField(measure("Revenue", false));
      matrix.addXField(measure("Orders", false));
      matrix.addYField(measure("Revenue", false));
      matrix.addYField(measure("Orders", false));
      ChartVSAssembly scatter = chart(matrix);

      assertNotNull(refusal(scatter, PairsDataSet.XMEASURE_NAME, false));
   }

   private static String refusal(ChartVSAssembly chart, String target, boolean secondary) {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> ChartAxisTargets.requireAxisTarget(chart, target, secondary),
         "'" + target + "' secondary=" + secondary + " must be refused");
      return e.getMessage();
   }

   /** x = Month(Order:Date); y = Sum(Revenue) primary + Sum(Orders) secondary. */
   private static ChartVSAssembly chart(boolean separated) {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.setSeparatedGraph(separated);
      ColumnRef date = new ColumnRef(new AttributeRef(null, "Order:Date"));
      date.setDataType(inetsoft.uql.schema.XSchema.DATE);
      VSChartDimensionRef x = new VSChartDimensionRef(date);
      x.setDateLevel(inetsoft.uql.asset.DateRangeRef.MONTH_INTERVAL);
      info.addXField(x);
      info.addYField(measure("Revenue", false));
      info.addYField(measure("Orders", true));
      return chart(info);
   }

   private static ChartVSAssembly chart(VSChartInfo info) {
      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      when(assembly.getVSChartInfo()).thenReturn(info);
      when(assembly.getChartInfo()).thenReturn(mock(ChartVSAssemblyInfo.class));
      return assembly;
   }

   private static VSChartAggregateRef measure(String column, boolean secondary) {
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setColumnValue(column);
      ref.setFormulaValue("Sum");
      ref.setSecondaryY(secondary);
      return ref;
   }
}
