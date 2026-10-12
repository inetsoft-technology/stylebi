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

import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

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
