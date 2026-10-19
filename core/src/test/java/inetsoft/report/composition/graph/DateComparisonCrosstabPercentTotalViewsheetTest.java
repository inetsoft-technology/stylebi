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
package inetsoft.report.composition.graph;

import inetsoft.report.TableLens;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.calc.ChangeCalc;
import inetsoft.report.composition.graph.calc.ValueOfCalc;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78858, #78859: a viewsheet crosstab with a percent change calc (date comparison
 * "percent change", or a plain "% change from previous") and visible subtotals. With
 * "Compute Trend and Comparison for Totals" off, the total cells used to show the raw period
 * sum with a percent format (e.g. 32,105,100%). They are now blank. The cells are read through
 * the rendered text of the VS table lens, which is what the viewer and the exports show.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("slow")
class DateComparisonCrosstabPercentTotalViewsheetTest {
   @BeforeEach
   void setUp() {
      box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      vs = box.getViewsheet();
      source = (SourceInfo) ((TableVSAssembly) vs.getAssembly("TableView1")).getSourceInfo().clone();

      // D covers 2024/2025, January to March
      CalculateRef dateCalc = new CalculateRef(true);
      dateCalc.setName("D");
      ExpressionRef dateExpr = new ExpressionRef(null, "D");
      dateExpr.setExpression("new Date(2024 + (field['day'] % 2), field['day'] % 3, 1)");
      dateExpr.setDataType(XSchema.DATE);
      dateCalc.setDataRef(dateExpr);
      dateCalc.setSQL(false);
      vs.addCalcField(source.getSource(), dateCalc);
   }

   @Test
   void dcPercentChangeTotalsAreBlankWhenTotalsAreNotComputed() throws Exception {
      VSTableLens lens = run("DcPctOff", rowDcCrosstab(dc(DateComparisonInfo.PERCENT)), false);

      assertEquals("", rowText(lens, "2024", "Total", null));
      assertEquals("", rowText(lens, "2025", "Total", null));
      // detail months are unchanged
      assertEquals("6%", rowText(lens, "2025", "Jan", null));
      assertEquals("6%", rowText(lens, "2025", "Feb", null));
      assertEquals("0%", rowText(lens, "2025", "Mar", null));
   }

   @Test
   void dcPercentValueChangeTotalIsBlankAndValueTotalKept() throws Exception {
      VSTableLens lens =
         run("DcPctValueOff", rowDcCrosstab(dc(DateComparisonInfo.PERCENT_VALUE)), false);

      assertEquals("", rowText(lens, "2024", "Total", "%"));
      assertEquals("", rowText(lens, "2025", "Total", "%"));
      assertEquals("321051", rowText(lens, "2024", "Total", "Sum"));
      assertEquals("332681", rowText(lens, "2025", "Total", "Sum"));
      assertEquals("6%", rowText(lens, "2025", "Jan", "%"));
   }

   @Test
   void dcPercentChangeTotalIsComputedWhenTotalsAreComputed() throws Exception {
      VSTableLens lens = run("DcPctOn", rowDcCrosstab(dc(DateComparisonInfo.PERCENT)), true);

      assertEquals("", rowText(lens, "2024", "Total", null));
      assertEquals("4%", rowText(lens, "2025", "Total", null));
      assertEquals("6%", rowText(lens, "2025", "Jan", null));
   }

   @Test
   void dcAbsoluteChangeTotalKeepsPlainAggregate() throws Exception {
      VSTableLens lens = run("DcChangeOff", rowDcCrosstab(dc(DateComparisonInfo.CHANGE)), false);

      assertEquals("321051", rowText(lens, "2024", "Total", null));
      assertEquals("332681", rowText(lens, "2025", "Total", null));
      assertEquals("5965", rowText(lens, "2025", "Jan", null));
   }

   /**
    * The date dimension in columns, so the totals are column totals.
    */
   @Test
   void dcPercentChangeColumnTotalsAreBlankWhenTotalsAreNotComputed() throws Exception {
      VSDimensionRef dim = dateDim(XConstants.MONTH_DATE_GROUP);
      dim.setSubTotalVisibleValue("true");
      CrosstabVSAssembly crosstab = crosstab(dc(DateComparisonInfo.PERCENT));
      crosstab.getVSCrosstabInfo().setDesignColHeaders(new DataRef[] { dim });
      VSTableLens lens = run("ColDcPctOff", crosstab, false);

      int yearRow = findRow(lens, "2024");
      int monthRow = yearRow + 1;
      int valueRow = lens.getRowCount() - 1;
      List<String> totals = new ArrayList<>();
      List<String> months2025 = new ArrayList<>();

      for(int c = 0; c < lens.getColCount(); c++) {
         if("Total".equals(lens.getText(monthRow, c))) {
            totals.add(lens.getText(valueRow, c));
         }
         else if("2025".equals(lens.getText(yearRow, c))) {
            months2025.add(lens.getText(valueRow, c));
         }
      }

      assertEquals(List.of("", ""), totals, dump(lens));
      assertEquals(List.of("6%", "6%", "0%"), months2025, dump(lens));
   }

   /**
    * The shape of Bug #78858: no date comparison, a percent change from the previous month
    * with a year subtotal.
    */
   @Test
   void plainPercentChangeSubtotalIsBlank() throws Exception {
      VSDimensionRef year = dateDim(XConstants.YEAR_DATE_GROUP);
      year.setSubTotalVisibleValue("true");
      VSDimensionRef month = dateDim(XConstants.MONTH_OF_YEAR_DATE_GROUP);
      CrosstabVSAssembly crosstab = crosstab(null);
      VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
      cinfo.setDesignRowHeaders(new DataRef[] { year, month });
      ChangeCalc calc = new ChangeCalc();
      calc.setFrom(ValueOfCalc.PREVIOUS);
      calc.setColumnName("MonthOfYear(D)");
      calc.setAsPercent(true);
      ((VSAggregateRef) cinfo.getDesignAggregates()[0]).setCalculator(calc);
      VSTableLens lens = run("PlainPctOff", crosstab, false);

      assertEquals("", rowText(lens, "2024", "Total", null));
      assertEquals("", rowText(lens, "2025", "Total", null));
      assertEquals("-3%", rowText(lens, "2024", "Feb", null));
      assertEquals("6%", rowText(lens, "2024", "Mar", null));
   }

   private CrosstabVSAssembly rowDcCrosstab(DateComparisonInfo dc) {
      VSDimensionRef dim = dateDim(XConstants.MONTH_DATE_GROUP);
      dim.setSubTotalVisibleValue("true");
      CrosstabVSAssembly crosstab = crosstab(dc);
      crosstab.getVSCrosstabInfo().setDesignRowHeaders(new DataRef[] { dim });
      return crosstab;
   }

   private CrosstabVSAssembly crosstab(DateComparisonInfo dc) {
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, "Crosstab");
      crosstab.setSourceInfo((SourceInfo) source.clone());
      VSAggregateRef agg = new VSAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      crosstab.getVSCrosstabInfo().setDesignAggregates(new DataRef[] { agg });
      ((CrosstabVSAssemblyInfo) crosstab.getVSAssemblyInfo()).setDateComparisonInfo(dc);
      return crosstab;
   }

   private VSTableLens run(String name, CrosstabVSAssembly crosstab, boolean calcTotal)
      throws Exception
   {
      crosstab.getVSAssemblyInfo().setName(name);
      crosstab.getVSCrosstabInfo().setCalculateTotalValue(calcTotal);
      vs.addAssembly(crosstab);
      TableLens data = (TableLens) box.getData(name);
      assertNotNull(data);
      data.moreRows(Integer.MAX_VALUE);
      VSTableLens lens = box.getVSTableLens(name, false);
      lens.moreRows(Integer.MAX_VALUE);
      return lens;
   }

   /**
    * Text of the last column on the row whose first cells are the year and the month. With
    * a measure, the row must also have a measure label starting with it (summary side by side
    * off with more than one measure).
    */
   private static String rowText(VSTableLens lens, String year, String month, String measure) {
      int last = lens.getColCount() - 1;

      for(int r = 0; r < lens.getRowCount(); r++) {
         if(year.equals(lens.getText(r, 0)) && month.equals(lens.getText(r, 1)) &&
            (measure == null || String.valueOf(lens.getText(r, 2)).startsWith(measure)))
         {
            return lens.getText(r, last);
         }
      }

      fail("No row " + year + "/" + month + "/" + measure + " in\n" + dump(lens));
      return null;
   }

   private static int findRow(VSTableLens lens, String text) {
      for(int r = 0; r < lens.getRowCount(); r++) {
         for(int c = 0; c < lens.getColCount(); c++) {
            if(text.equals(lens.getText(r, c))) {
               return r;
            }
         }
      }

      fail("No cell " + text + " in\n" + dump(lens));
      return -1;
   }

   private static String dump(VSTableLens lens) {
      StringBuilder sb = new StringBuilder();

      for(int r = 0; r < lens.getRowCount(); r++) {
         for(int c = 0; c < lens.getColCount(); c++) {
            sb.append('[').append(lens.getText(r, c)).append("] ");
         }

         sb.append('\n');
      }

      return sb.toString();
   }

   private static DateComparisonInfo dc(int option) {
      DateComparisonInfo dcInfo = new DateComparisonInfo();
      StandardPeriods periods = new StandardPeriods();
      periods.setDateLevel(XConstants.YEAR_DATE_GROUP);
      periods.setPreCount(1);
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue("2025-12-31");
      periods.setToDate(false);
      periods.setInclusive(true);
      dcInfo.setDateComparisonPeriods(periods);
      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setGranularity(DateComparisonInfo.MONTH);
      interval.setLevel(DateComparisonInfo.ALL);
      interval.setContextLevel(XConstants.YEAR_DATE_GROUP);
      dcInfo.setDateComparisonInterval(interval);
      dcInfo.setComparisonOption(option);
      return dcInfo;
   }

   private static VSDimensionRef dateDim(int level) {
      VSDimensionRef dim = new VSDimensionRef();
      AttributeRef attr = new AttributeRef(null, "D");
      attr.setDataType(XSchema.DATE);
      ColumnRef column = new ColumnRef(attr);
      column.setDataType(XSchema.DATE);
      dim.setDataRef(column);
      dim.setGroupColumnValue("D");
      dim.setDataType(XSchema.DATE);
      dim.setDateLevelValue(level + "");
      return dim;
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   private ViewsheetSandbox box;
   private Viewsheet vs;
   private SourceInfo source;
}
