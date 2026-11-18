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
package inetsoft.web.composer.vs.dialog;

import inetsoft.test.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.model.condition.ConditionModel;
import inetsoft.web.composer.model.condition.JunctionOperatorModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81634: a condition of a chart measure highlight on a field that is no longer bound is
 * reported to the highlight dialog (display only). The check must only run when the dialog's
 * field list is the authoritative list of the bound fields.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class HighlightDialogServiceUnboundFieldsTest {
   private static DataRefModel field(String view, String attribute, String entity) {
      ColumnRefModel model = new ColumnRefModel();
      model.setView(view);
      model.setName(view);
      model.setAttribute(attribute);
      model.setEntity(entity);
      return model;
   }

   private static ConditionModel condition(DataRefModel field) {
      ConditionModel cond = new ConditionModel();
      cond.setField(field);
      return cond;
   }

   private static VSChartAggregateRef measure() {
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setColumnValue("Total");
      ref.setDataRef(new AttributeRef(null, "Total"));
      ref.setFormulaValue("Sum");
      return ref;
   }

   private static DefaultVSChartInfo chart(int type) {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(type);
      info.setRTChartType(type);
      return info;
   }

   private final DataRefModel[] fields = {
      field("Sum(Product:Total)", "Sum(Product:Total)", "Product"),
      field("Year(Order:Date)", "Year(Order:Date)", "Order")
   };

   @Test
   void reportedCaseIsFlagged() {
      Object[] conds = { condition(field("QuarterOfYear(Order:Date)",
                                         "QuarterOfYear(Order:Date)", "Order")) };
      assertArrayEquals(new String[]{ "QuarterOfYear(Order:Date)" },
                        HighlightDialogService.findUnboundFields(conds, fields));
   }

   @Test
   void boundFieldsAreNotFlagged() {
      Object[] conds = {
         condition(field("Year(Order:Date)", "Year(Order:Date)", "Order")),
         new JunctionOperatorModel(),
         condition(field("Sum(Product:Total)", "Sum(Product:Total)", "Product"))
      };
      assertEquals(0, HighlightDialogService.findUnboundFields(conds, fields).length);
   }

   @Test
   void localizedViewMatchesByAttributeEntityAndType() {
      // the list view is localized, the stored condition view is not: attribute, entity and
      // type still identify the same field
      DataRefModel[] localized = {
         field("年(Order:Date)", "Year(Order:Date)", "Order")
      };
      Object[] conds = { condition(field("Year(Order:Date)", "Year(Order:Date)", "Order")) };
      assertEquals(0, HighlightDialogService.findUnboundFields(conds, localized).length);
   }

   @Test
   void aggregateRenameIsFlagged() {
      Object[] conds = { condition(field("Average(Product:Total)", "Average(Product:Total)",
                                         "Product")) };
      assertArrayEquals(new String[]{ "Average(Product:Total)" },
                        HighlightDialogService.findUnboundFields(conds, fields));
   }

   @Test
   void unusableFieldListIsNotChecked() {
      Object[] conds = { condition(field("QuarterOfYear(Order:Date)",
                                         "QuarterOfYear(Order:Date)", "Order")) };
      assertNull(HighlightDialogService.findUnboundFields(conds, null));
      assertNull(HighlightDialogService.findUnboundFields(conds, new DataRefModel[0]));
      assertNull(HighlightDialogService.findUnboundFields(null, fields));
   }

   @Test
   void measureHighlightOfBarLineAreaIsChecked() {
      for(int type : new int[]{ GraphTypes.CHART_BAR, GraphTypes.CHART_BAR_STACK,
                                GraphTypes.CHART_LINE, GraphTypes.CHART_AREA })
      {
         assertTrue(HighlightDialogService.isUnboundCheckApplicable(
            chart(type), measure(), false, false, false, false), "type " + type);
      }
   }

   @Test
   void otherChartTypesAreNotChecked() {
      for(int type : new int[]{ GraphTypes.CHART_TREEMAP, GraphTypes.CHART_SUNBURST,
                                GraphTypes.CHART_ICICLE, GraphTypes.CHART_RADAR,
                                GraphTypes.CHART_GANTT, GraphTypes.CHART_MAP,
                                GraphTypes.CHART_PIE, GraphTypes.CHART_BOXPLOT,
                                GraphTypes.CHART_NETWORK, GraphTypes.CHART_TREE })
      {
         assertFalse(HighlightDialogService.isUnboundCheckApplicable(
            chart(type), measure(), false, false, false, false), "type " + type);
      }
   }

   @Test
   void axisTextBoxplotWordCloudAreNotChecked() {
      DefaultVSChartInfo info = chart(GraphTypes.CHART_BAR);
      assertFalse(HighlightDialogService.isUnboundCheckApplicable(
         info, measure(), true, false, false, false));
      assertFalse(HighlightDialogService.isUnboundCheckApplicable(
         info, measure(), false, true, false, false));
      assertFalse(HighlightDialogService.isUnboundCheckApplicable(
         info, measure(), false, false, true, false));
      assertFalse(HighlightDialogService.isUnboundCheckApplicable(
         info, measure(), false, false, false, true));
   }

   @Test
   void discreteMeasureAndDimensionAreNotChecked() {
      VSChartAggregateRef discrete = measure();
      discrete.setDiscrete(true);
      DefaultVSChartInfo info = chart(GraphTypes.CHART_BAR);
      assertFalse(HighlightDialogService.isUnboundCheckApplicable(
         info, discrete, false, false, false, false));
      assertFalse(HighlightDialogService.isUnboundCheckApplicable(
         info, new VSChartDimensionRef(new AttributeRef(null, "Category")),
         false, false, false, false));
      assertFalse(HighlightDialogService.isUnboundCheckApplicable(
         info, null, false, false, false, false));
   }

   @Test
   void mergedAndMultiStyleChartsAreNotChecked() {
      MergedVSChartInfo merged = new MergedVSChartInfo();
      merged.setChartType(GraphTypes.CHART_BAR);
      merged.setRTChartType(GraphTypes.CHART_BAR);
      assertFalse(HighlightDialogService.isUnboundCheckApplicable(
         merged, measure(), false, false, false, false));

      DefaultVSChartInfo multi = chart(GraphTypes.CHART_BAR);
      multi.setMultiStyles(true);
      assertFalse(HighlightDialogService.isUnboundCheckApplicable(
         multi, measure(), false, false, false, false));
   }
}
