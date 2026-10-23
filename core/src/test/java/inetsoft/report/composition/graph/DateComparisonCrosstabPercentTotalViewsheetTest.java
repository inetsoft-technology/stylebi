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
import inetsoft.test.*;
import inetsoft.test.dc.DcInMemoryViewsheetFixture;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78858, #78859 through the viewsheet: a crosstab with a date comparison "percent change"
 * and visible subtotals. With "Compute Trend and Comparison for Totals" off, the total cells
 * used to show the raw period sum with a percent format (e.g. 32,105,100%). They are now blank.
 * The cells are read through the rendered text of the VS table lens, which is what the viewer
 * and the exports show.
 *
 * <p>The table is in memory ({@link DcInMemoryViewsheetFixture}): the first of each month of
 * 2024 and 2025, January to March, with 2024 = 100 / 200 / 300 and 2025 = 106 / 212 / 300, so
 * the percent changes are 6% / 6% / 0%, the totals 600 and 618 (+3%). The layouts the
 * {@code CrossTabFilter} wiring makes (plain percent change, column totals, absolute change)
 * are in {@code CrossTabFilterPercentCalcTotalTest}.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  DcInMemoryViewsheetFixture.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DateComparisonCrosstabPercentTotalViewsheetTest {
   private final DcInMemoryViewsheetFixture fixture = new DcInMemoryViewsheetFixture();

   @BeforeEach
   void setUp() throws Exception {
      fixture.setUp();
      List<Object[]> rows = new ArrayList<>();
      int[][] wind = { { 100, 200, 300 }, { 106, 212, 300 } };

      for(int year = 0; year < 2; year++) {
         for(int month = 0; month < 3; month++) {
            rows.add(new Object[] { java.sql.Date.valueOf(LocalDate.of(2024 + year, month + 1, 1)),
                                    wind[year][month] });
         }
      }

      DcInMemoryViewsheetFixture.Sheet sheet = fixture.open("D", "wind", rows);
      box = sheet.box();
      vs = sheet.vs();
      source = sheet.source();
   }

   @AfterEach
   void tearDown() {
      fixture.tearDown();
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
      assertEquals("600", rowText(lens, "2024", "Total", "Sum"));
      assertEquals("618", rowText(lens, "2025", "Total", "Sum"));
      assertEquals("6%", rowText(lens, "2025", "Jan", "%"));
   }

   @Test
   void dcPercentChangeTotalIsComputedWhenTotalsAreComputed() throws Exception {
      VSTableLens lens = run("DcPctOn", rowDcCrosstab(dc(DateComparisonInfo.PERCENT)), true);

      assertEquals("", rowText(lens, "2024", "Total", null));
      assertEquals("3%", rowText(lens, "2025", "Total", null));
      assertEquals("6%", rowText(lens, "2025", "Jan", null));
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

   private ViewsheetSandbox box;
   private Viewsheet vs;
   private SourceInfo source;
}
