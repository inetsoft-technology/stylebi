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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.*;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Bug #77393: an assembly that shares another assembly's date comparison
 * (comparisonShareFrom) still stores its own dc, a snapshot taken when the share was set that
 * goes stale when the source dc is edited. Consumers must read the share-resolved dc.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateComparisonUtilShareResolutionTest {
   private final List<ViewsheetSandbox> sandboxes = new ArrayList<>();

   @AfterEach
   void disposeSandboxes() {
      sandboxes.forEach(ViewsheetSandbox::dispose);
      sandboxes.clear();
   }

   @Test
   void descriptionUsesSharedDateComparisonNotStaleOwnDc() {
      DateComparisonInfo ownDc = mock(DateComparisonInfo.class);
      when(ownDc.getDescription()).thenReturn("stale own dc");
      DateComparisonInfo sharedDc = mock(DateComparisonInfo.class);
      when(sharedDc.getDescription()).thenReturn("shared dc");

      Viewsheet vs = mock(Viewsheet.class);
      mockCrosstab(vs, "Crosstab1", sharedDc, null);

      VSChartInfo chartInfo = mock(VSChartInfo.class);
      when(chartInfo.isAppliedDateComparison()).thenReturn(true);
      ChartVSAssemblyInfo info = mock(ChartVSAssemblyInfo.class);
      when(info.getVSChartInfo()).thenReturn(chartInfo);
      when(info.getViewsheet()).thenReturn(vs);
      when(info.getComparisonShareFrom()).thenReturn("Crosstab1");
      when(info.getDateComparisonInfo()).thenReturn(ownDc);

      Assertions.assertEquals("shared dc", DateComparisonUtil.getDateComparisonDescription(info));
   }

   @Test
   void descriptionUsesOwnDateComparisonWhenNotShared() {
      DateComparisonInfo ownDc = mock(DateComparisonInfo.class);
      when(ownDc.getDescription()).thenReturn("own dc");

      VSChartInfo chartInfo = mock(VSChartInfo.class);
      when(chartInfo.isAppliedDateComparison()).thenReturn(true);
      ChartVSAssemblyInfo info = mock(ChartVSAssemblyInfo.class);
      when(info.getVSChartInfo()).thenReturn(chartInfo);
      when(info.getDateComparisonInfo()).thenReturn(ownDc);

      Assertions.assertEquals("own dc", DateComparisonUtil.getDateComparisonDescription(info));
   }

   @Test
   void descriptionTerminatesOnShareCycle() {
      Viewsheet vs = mock(Viewsheet.class);
      // Crosstab1 and Crosstab2 share from each other, both holding a stale own dc
      mockCrosstab(vs, "Crosstab1", mock(DateComparisonInfo.class), "Crosstab2");
      mockCrosstab(vs, "Crosstab2", mock(DateComparisonInfo.class), "Crosstab1");
      when(vs.getAssembly("Crosstab1").getVSAssemblyInfo().getViewsheet()).thenReturn(vs);
      when(vs.getAssembly("Crosstab2").getVSAssemblyInfo().getViewsheet()).thenReturn(vs);

      Assertions.assertNull(DateComparisonUtil.getDateComparisonDescription(
         vs.getAssembly("Crosstab1").getVSAssemblyInfo()));
   }

   @Test
   void getDateComparisonReturnsNullWhenNestedShareTargetIsMissing() {
      Viewsheet vs = mock(Viewsheet.class);
      // Crosstab1 shares from Crosstab2, which no longer exists
      mockCrosstab(vs, "Crosstab1", null, "Crosstab2");

      ChartVSAssemblyInfo info = mock(ChartVSAssemblyInfo.class);
      when(info.isDateComparisonEnabled()).thenReturn(true);
      when(info.getComparisonShareFrom()).thenReturn("Crosstab1");

      Assertions.assertNull(DateComparisonUtil.getDateComparison(info, vs));
   }

   @Test
   void crosstabWeekSyncUsesPassedDateComparisonNotOwnDc() {
      DateComparisonInfo ownDc = mock(DateComparisonInfo.class);
      when(ownDc.alignWeek()).thenReturn(false);
      DateComparisonInfo sharedDc = mock(DateComparisonInfo.class);
      when(sharedDc.alignWeek()).thenReturn(true);

      CrosstabVSAssemblyInfo info = mock(CrosstabVSAssemblyInfo.class);
      when(info.getComparisonShareFrom()).thenReturn("Crosstab2");
      when(info.getDateComparisonInfo()).thenReturn(ownDc);

      DateComparisonUtil.syncWeekGroupingLevels(info, sharedDc);
      verify(info).getFullLevelNameMap();

      clearInvocations(info);
      DateComparisonUtil.syncWeekGroupingLevels(info, null);
      verify(info, never()).getFullLevelNameMap();
   }

   /**
    * The source chart's date comparison is edited after the share was set: the sharer keeps its
    * own copy of the old one. What the sharer's date comparison, its description and its query
    * conditions are must all come from the source, not from that copy. Real assemblies, so a
    * consumer that reads the sharer's own date comparison cannot be hidden by an unstubbed mock.
    */
   @Test
   void sharerOfAChangedSourceResolvesToTheSourceEverywhere() {
      Viewsheet vs = new Viewsheet();
      DateComparisonInfo stale = dc(1);
      chart(vs, "Source", dc(2), null);
      chart(vs, "Sharer", stale, "Source");
      // the source is edited later; the sharer's copy is not
      DateComparisonInfo edited = dc(3);
      chartInfo(vs, "Source").setDateComparisonInfo(edited);
      ChartVSAssemblyInfo sharer = chartInfo(vs, "Sharer");
      ViewsheetSandbox box = sandbox(vs);

      Assertions.assertSame(edited, DateComparisonUtil.getDateComparison(sharer, vs));
      Assertions.assertSame(stale, sharer.getDateComparisonInfo(), "the stale copy is still there");

      // the chart is not executed here, so the description ends with a warning that DC is not applied
      String description = DateComparisonUtil.getDateComparisonDescription(sharer);
      Assertions.assertTrue(description.startsWith(edited.getDescription()), description);
      Assertions.assertNotEquals(stale.getDescription(), edited.getDescription(), "fixture");
      Assertions.assertFalse(description.contains(stale.getDescription()), description);

      ConditionList conditions = box.getDateComparisonConditions("Sharer");
      Assertions.assertNotNull(conditions);
      Assertions.assertEquals(box.getDateComparisonConditions("Source").toString(),
                              conditions.toString());
      Assertions.assertNotEquals(
         stale.getDateComparisonConditions(chartInfo(vs, "Sharer").getVSChartInfo()
                                              .getXField(0)).toString(),
         conditions.toString(), "the stale copy has other periods");
   }

   /**
    * A shares from B and B shares from C, both holding a stale copy: A resolves to C, the final
    * source. When C has no date comparison or the link B points to is gone, A gets no date
    * comparison and no query conditions, and the stale copies are not used instead.
    */
   @Test
   void nestedShareResolvesToTheFinalSourceAndNotToAStaleCopy() {
      Viewsheet vs = new Viewsheet();
      DateComparisonInfo source = dc(3);
      chart(vs, "C", source, null);
      chart(vs, "B", dc(1), "C");
      chart(vs, "A", dc(2), "B");
      ChartVSAssemblyInfo a = chartInfo(vs, "A");
      ChartVSAssemblyInfo b = chartInfo(vs, "B");
      ViewsheetSandbox box = sandbox(vs);

      Assertions.assertSame(source, DateComparisonUtil.getDateComparison(a, vs));
      Assertions.assertSame(source, DateComparisonUtil.getDateComparison(b, vs));
      Assertions.assertEquals(box.getDateComparisonConditions("C").toString(),
                              box.getDateComparisonConditions("A").toString());

      // C's date comparison is cleared (unchecked in its own Advanced pane, which does not
      // release its sharers): A and B get none, not their stale copies
      chartInfo(vs, "C").setDateComparisonInfo(null);
      assertNoDateComparison(vs, box, a, b);

      // B's link points to an assembly that is gone, and the sharers were not released
      chartInfo(vs, "C").setDateComparisonInfo(source);
      Assertions.assertSame(source, DateComparisonUtil.getDateComparison(a, vs), "fixture");
      b.setComparisonShareFrom("Gone");
      assertNoDateComparison(vs, box, a, b);
   }

   private static void assertNoDateComparison(Viewsheet vs, ViewsheetSandbox box,
                                              ChartVSAssemblyInfo a, ChartVSAssemblyInfo b)
   {
      Assertions.assertNull(DateComparisonUtil.getDateComparison(a, vs));
      Assertions.assertNull(DateComparisonUtil.getDateComparison(b, vs));
      Assertions.assertNull(box.getDateComparisonConditions("A"));
      Assertions.assertNull(box.getDateComparisonConditions("B"));
   }

   /** Year periods, Previous {@code preCount}, ending 2021-11-10, Compare All by month. */
   private static DateComparisonInfo dc(int preCount) {
      StandardPeriods periods = new StandardPeriods();
      periods.setPreCount(preCount);
      periods.setDateLevel(XConstants.YEAR_DATE_GROUP);
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue("2021-11-10");
      periods.setToDate(false);
      periods.setInclusive(false);

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setLevel(DateComparisonInfo.ALL);
      interval.setGranularity(DateComparisonInfo.MONTH);

      DateComparisonInfo dc = new DateComparisonInfo();
      dc.setDateComparisonPeriods(periods);
      dc.setDateComparisonInterval(interval);
      dc.setComparisonOption(DateComparisonInfo.VALUE);

      return dc;
   }

   /** A chart with a date on X and a sum on Y, with the given date comparison and share. */
   private static void chart(Viewsheet vs, String name, DateComparisonInfo dc, String shareFrom) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      VSChartInfo info = chart.getVSChartInfo();
      ColumnRef date = new ColumnRef(new AttributeRef(null, "Date"));
      date.setDataType(XSchema.DATE);
      VSChartDimensionRef dim = new VSChartDimensionRef(date);
      dim.setGroupColumnValue("Date");
      info.addXField(dim);
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("Paid");
      agg.setFormulaValue("Sum");
      info.addYField(agg);

      ChartVSAssemblyInfo chartInfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      chartInfo.setDateComparisonEnabled(true);
      chartInfo.setDateComparisonInfo(dc);
      chartInfo.setComparisonShareFrom(shareFrom);
      vs.addAssembly(chart);
   }

   private static ChartVSAssemblyInfo chartInfo(Viewsheet vs, String name) {
      return (ChartVSAssemblyInfo) vs.getAssembly(name).getVSAssemblyInfo();
   }

   /**
    * A sandbox that only reads the date comparison of the assemblies, nothing is executed. It is
    * disposed after the test.
    */
   private ViewsheetSandbox sandbox(Viewsheet vs) {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/ShareResolution", null,
         OrganizationManager.getInstance().getCurrentOrgID());

      ViewsheetSandbox box =
         new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
      sandboxes.add(box);

      return box;
   }

   private static void mockCrosstab(Viewsheet vs, String name, DateComparisonInfo dc,
                                    String shareFrom)
   {
      VSCrosstabInfo vinfo = mock(VSCrosstabInfo.class);
      when(vinfo.isAppliedDateComparison()).thenReturn(true);
      CrosstabVSAssemblyInfo crosstabInfo = mock(CrosstabVSAssemblyInfo.class);
      when(crosstabInfo.getVSCrosstabInfo()).thenReturn(vinfo);
      when(crosstabInfo.getDateComparisonInfo()).thenReturn(dc);
      when(crosstabInfo.getComparisonShareFrom()).thenReturn(shareFrom);
      CrosstabVSAssembly crosstab = mock(CrosstabVSAssembly.class);
      when(crosstab.getVSAssemblyInfo()).thenReturn(crosstabInfo);
      when(vs.getAssembly(name)).thenReturn(crosstab);
   }
}
