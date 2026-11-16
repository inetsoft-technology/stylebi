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

import inetsoft.graph.scale.TimeScale;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XCondition;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.XFormatInfo;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.Format;
import java.text.SimpleDateFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The date level, order and format rules behind compact dates.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CompactDateRuleTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void unitsMapFromDateLevels() {
      assertEquals(TimeScale.YEAR, CompactDateRule.unit(DateRangeRef.YEAR_INTERVAL));
      assertEquals(TimeScale.YEAR, CompactDateRule.unit(DateRangeRef.YEAR_OF_FULL_WEEK));
      assertEquals(TimeScale.QUARTER, CompactDateRule.unit(DateRangeRef.QUARTER_INTERVAL));
      assertEquals(TimeScale.QUARTER, CompactDateRule.unit(DateRangeRef.QUARTER_OF_FULL_WEEK));
      assertEquals(TimeScale.MONTH, CompactDateRule.unit(DateRangeRef.MONTH_INTERVAL));
      assertEquals(TimeScale.MONTH, CompactDateRule.unit(DateRangeRef.MONTH_OF_FULL_WEEK));
      assertEquals(TimeScale.WEEK, CompactDateRule.unit(DateRangeRef.WEEK_INTERVAL));
      assertEquals(TimeScale.DAY, CompactDateRule.unit(DateRangeRef.DAY_INTERVAL));
      assertEquals(TimeScale.HOUR, CompactDateRule.unit(DateRangeRef.HOUR_INTERVAL));
      assertEquals(TimeScale.MINUTE, CompactDateRule.unit(DateRangeRef.MINUTE_INTERVAL));
      assertEquals(TimeScale.SECOND, CompactDateRule.unit(DateRangeRef.SECOND_INTERVAL));
   }

   @Test
   void partLevelsHaveNoUnit() {
      assertEquals(-1, CompactDateRule.unit(DateRangeRef.MONTH_OF_YEAR_PART));
      assertEquals(-1, CompactDateRule.unit(DateRangeRef.DAY_OF_WEEK_PART));
      assertEquals(-1, CompactDateRule.unit(DateRangeRef.NONE_INTERVAL));
   }

   @Test
   void ascendingAndDescendingAreDateOrder() {
      VSChartDimensionRef dim = month();
      dim.setOrder(XConstants.SORT_ASC);
      assertTrue(CompactDateRule.inDateOrder(dim));
      dim.setOrder(XConstants.SORT_DESC);
      assertTrue(CompactDateRule.inDateOrder(dim));
   }

   @Test
   void byValueSpecificAndTopNAreNot() {
      VSChartDimensionRef dim = month();
      dim.setOrder(XConstants.SORT_VALUE_DESC);
      assertFalse(CompactDateRule.inDateOrder(dim));
      dim.setOrder(XConstants.SORT_SPECIFIC);
      assertFalse(CompactDateRule.inDateOrder(dim));
      dim.setOrder(XConstants.SORT_ASC);
      dim.setRankingOptionValue(String.valueOf(XCondition.TOP_N));
      assertFalse(CompactDateRule.inDateOrder(dim));
   }

   @Test
   void thePaneShowsTheFullPatternOnAMarkedChart() {
      Format today = new SimpleDateFormat("yyyy-MM");
      Format shown = CompactDateRule.paneDefault(context(VizMark.MODERN_LIGHT), month(), today);

      assertNotSame(today, shown);
      assertEquals("MMM yyyy", ((SimpleDateFormat) shown).toPattern());
      assertSame(today, CompactDateRule.paneDefault(context(null), month(), today));
   }

   @Test
   void aYearHasNoCompactPattern() {
      Format today = new SimpleDateFormat("yyyy");
      VSChartDimensionRef year = month();
      year.setDateLevelValue(String.valueOf(XConstants.YEAR_DATE_GROUP));

      assertSame(today, CompactDateRule.paneDefault(context(VizMark.MODERN_LIGHT), year, today));
   }

   @Test
   void aQuarterPaneDefaultSurvivesTheFormatInfoRoundTrip() {
      VSChartDimensionRef quarter = month();
      quarter.setDateLevelValue(String.valueOf(XConstants.QUARTER_DATE_GROUP));
      Format shown = CompactDateRule.paneDefault(context(VizMark.MODERN_LIGHT), quarter,
                                                 new SimpleDateFormat("yyyy"));
      XFormatInfo info = new XFormatInfo(shown);
      Format read = GraphUtil.getFormat(info);

      assertEquals("Q2 2025", read.format(java.sql.Date.valueOf("2025-04-01")));
   }

   @Test
   void theXPaneOfADateComparisonChartKeepsToday() {
      Format today = new SimpleDateFormat("yyyy-MM");
      VSChartInfo spy = Mockito.spy(new DefaultVSChartInfo());
      Mockito.doReturn(true).when(spy).isAppliedDateComparison();
      VizContext ctx = context(VizMark.MODERN_LIGHT);

      assertSame(today, CompactDateRule.paneDefault(ctx, spy, true, month(), today));
      assertNotSame(today, CompactDateRule.paneDefault(ctx, spy, false, month(), today));
      assertNotSame(today, CompactDateRule.paneDefault(
         ctx, new DefaultVSChartInfo(), true, month(), today));
   }

   private static VizContext context(VizMark mark) {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(mark);
      return VizContext.of(info);
   }

   private static VSChartDimensionRef month() {
      VSChartDimensionRef dim = new VSChartDimensionRef(new BaseField("Date"));
      dim.setGroupColumnValue("Date");
      dim.setDataType(XSchema.DATE);
      dim.setDateLevelValue(String.valueOf(XConstants.MONTH_DATE_GROUP));
      return dim;
   }
}
