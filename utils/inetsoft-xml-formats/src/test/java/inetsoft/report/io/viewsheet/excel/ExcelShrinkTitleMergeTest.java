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
package inetsoft.report.io.viewsheet.excel;

import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * A shrunk table's title merges the Excel rows its own height covers, and the body is anchored
 * below exactly those rows. Both come from the same arithmetic, so the two can never overlap:
 * {@code getTableTitleHeight} sizes the merge, {@code getExcelTitleHeight} places the body.
 *
 * <p>Before the fix the title was sized from the <em>data rows'</em> Excel spans instead, which
 * a density row height made two apiece — the title merged four rows where the body started two
 * down, POI rejected the region, and every table after it on the sheet failed the same
 * validation.</p>
 *
 * <p>Driving the merge itself is not possible here: it reaches {@code VSUtil} and the cell style
 * cache, whose static initializers need a bootstrapped server this module's tests do not have.
 * The end-to-end cover is the Excel re-export.</p>
 */
class ExcelShrinkTitleMergeTest {
   /** Legacy 20, compact 26, comfortable 30 - the merge and the anchor agree at every one. */
   @Test
   void theTitleMergesWhatTheBodyAnchorSkips() {
      for(int titleHeight : new int[] { 20, 26, 30 }) {
         assertEquals(PoiExcelVSUtil.getExcelTitleHeight(titledInfo(titleHeight)),
                      PoiExcelVSUtil.getTableTitleHeight(titleHeight),
                      "title height " + titleHeight);
      }
   }

   @Test
   void aHiddenTitleTakesNoRows() {
      TableVSAssemblyInfo info = Mockito.mock(TableVSAssemblyInfo.class);
      when(info.isTitleVisible()).thenReturn(false);

      assertEquals(0, PoiExcelVSUtil.getExcelTitleHeight(info));
   }

   private static TableVSAssemblyInfo titledInfo(int titleHeight) {
      TableVSAssemblyInfo info = Mockito.mock(TableVSAssemblyInfo.class);
      when(info.isTitleVisible()).thenReturn(true);
      when(info.getTitleHeight()).thenReturn(titleHeight);
      return info;
   }
}
