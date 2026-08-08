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
package inetsoft.web.wiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.graph.internal.LabelValue;
import inetsoft.report.filter.DCMergeDatesCell;
import inetsoft.uql.asset.DateRangeRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A DataSet cell is not always a scalar, and handing a non-scalar to Jackson is expensive in a way that
 * is invisible until it breaks something: a {@link DCMergeDatesCell}'s {@code getFormat()} drags in
 * SimpleDateFormat -> DateFormatSymbols -> zoneStrings, the whole locale timezone table, per cell.
 *
 * <p>Measured on a 14-row year-over-year comparison (openproject B6): ~69KB per cell, 97% of it
 * zoneStrings, and since the cells appear twice (rows + the facts table computed from them) the
 * response reached ~1.9M characters — over the caller's token limit, so its own result was unreadable.
 */
@Tag("core")
class WizUtilResponseCellTest {
   private static Date date(int year, int month, int day) {
      Calendar cal = Calendar.getInstance();
      cal.clear();
      cal.set(year, month - 1, day);
      return cal.getTime();
   }

   private static DCMergeDatesCell mergedCell(int month) {
      DCMergeDatesCell cell = new DCMergeDatesCell(
         true, DateRangeRef.MONTH_INTERVAL, Map.of(), false);
      cell.setDates(List.of(date(2025, month, 1), date(2026, month, 1)));
      cell.setFormat(new SimpleDateFormat("yyyy MMM"));
      return cell;
   }

   private static DCMergeDatesCell mergedCell() {
      return mergedCell(6);
   }

   @Test
   void passesScalarsThrough() {
      assertNull(WizUtil.toResponseCell(null));
      assertEquals(23, WizUtil.toResponseCell(23));
      assertEquals(4.5, WizUtil.toResponseCell(4.5));
      assertEquals("2026 Jun", WizUtil.toResponseCell("2026 Jun"));
      assertEquals(Boolean.TRUE, WizUtil.toResponseCell(true));

      Date d = date(2025, 6, 1);
      assertSame(d, WizUtil.toResponseCell(d));
   }

   /**
    * The label the chart actually draws. Deliberately NOT the outer toString(), which joins every merged
    * date with "&" ("2025 Jun&2026 Jun") instead of naming the point.
    */
   @Test
   void reducesAMergedDateCellToTheLabelTheChartDraws() {
      Object out = WizUtil.toResponseCell(mergedCell().getMergeLabelCell());

      assertInstanceOf(String.class, out);
      assertFalse(((String) out).contains("&"), "should be the point's label, not every merged date: " + out);
   }

   /** The point of the whole exercise: what goes on the wire has to be small. */
   @Test
   void collapsesTheSerializedSizeByOrdersOfMagnitude() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      LabelValue raw = (LabelValue) mergedCell().getMergeLabelCell();

      int before = mapper.writeValueAsString(raw).length();
      int after = mapper.writeValueAsString(WizUtil.toResponseCell(raw)).length();

      assertTrue(before > 10_000, "the raw cell should be the huge thing this test is about: " + before);
      assertTrue(after < 100, "the converted cell should be a short label, was " + after + " chars");
      assertTrue(after * 100 < before, before + " -> " + after + " is not the collapse expected");
   }

   /** zoneStrings is 97% of the payload — it must not survive the conversion. */
   @Test
   void dropsTheLocaleTimezoneTable() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      String out = mapper.writeValueAsString(WizUtil.toResponseCell(mergedCell().getMergeLabelCell()));

      assertFalse(out.contains("zoneStrings"), out);
      assertFalse(out.contains("dateFormatSymbols"), out);
      assertFalse(out.contains("Pacific Standard Time"), out);
   }

   /** Distinct points must stay distinct, or a consumer keying on them collapses them together. */
   @Test
   void keepsDistinctPointsDistinct() {
      DCMergeDatesCell jun = mergedCell(6);
      DCMergeDatesCell jul = mergedCell(7);

      assertNotEquals(WizUtil.toResponseCell(jun.getMergeLabelCell()),
                      WizUtil.toResponseCell(jul.getMergeLabelCell()));
   }
}
