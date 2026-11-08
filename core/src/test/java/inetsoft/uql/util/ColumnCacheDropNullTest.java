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
package inetsoft.uql.util;

import inetsoft.uql.asset.DateRangeRef;
import inetsoft.web.composer.model.BrowseDataModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80658. Model-bound browse data ran through ColumnCache.format(), which turned a null
 * value into a "" label (and, for date-level columns, a "" value) without telling the caller.
 * The opt-in dropNull variant drops raw null values and reports it; the legacy overload is
 * unchanged. Axis: value type (string/date-level) x label-carrying data x caller.
 */
@Tag("core")
class ColumnCacheDropNullTest {
   private static BrowseDataModel values(Object... v) {
      return BrowseDataModel.builder().values(v).build();
   }

   @Test
   void legacyFormatKeepsNullAsEmptyLabel() {
      String[][] data = ColumnCache.format(values(null, "CA", "NY"), "string", false, null,
                                           DateRangeRef.NONE_DATE_GROUP);

      assertArrayEquals(new String[] { null, "CA", "NY" }, data[0]);
      assertArrayEquals(new String[] { "", "CA", "NY" }, data[1]);
   }

   @Test
   void dropNullRemovesNullAndFlagsIt() {
      ColumnCache.FormatResult r = ColumnCache.format(values(null, "CA", "NY"), "string", false,
                                                      null, DateRangeRef.NONE_DATE_GROUP, true);

      assertTrue(r.nullExcluded());
      assertArrayEquals(new String[] { "CA", "NY" }, r.data()[0]);
      assertArrayEquals(new String[] { "CA", "NY" }, r.data()[1]);
   }

   @Test
   void dropNullKeepsGenuineEmptyString() {
      ColumnCache.FormatResult r = ColumnCache.format(values(null, "", "CA"), "string", false,
                                                      null, DateRangeRef.NONE_DATE_GROUP, true);

      assertTrue(r.nullExcluded());
      assertArrayEquals(new String[] { "", "CA" }, r.data()[0]);
      assertArrayEquals(new String[] { "", "CA" }, r.data()[1]);
   }

   @Test
   void dropNullWithoutNullsDoesNotFlag() {
      ColumnCache.FormatResult r = ColumnCache.format(values("CA", "NY"), "string", false, null,
                                                      DateRangeRef.NONE_DATE_GROUP, true);

      assertFalse(r.nullExcluded());
      assertArrayEquals(new String[] { "CA", "NY" }, r.data()[0]);
   }

   @Test
   void dropNullOnDateLevelColumnDropsNullBeforeEmptyConversion() {
      ColumnCache.FormatResult r = ColumnCache.format(values(null, "a", "b"), "string", false,
                                                      null, DateRangeRef.YEAR_INTERVAL, true);

      assertTrue(r.nullExcluded());
      assertFalse(java.util.Arrays.asList(r.data()[0]).contains(""));
      assertFalse(java.util.Arrays.asList(r.data()[1]).contains(""));
      assertEquals(2, r.data()[0].length);
   }

   @Test
   void dropNullLeavesLabelCarryingDataUntouched() {
      BrowseDataModel model = BrowseDataModel.builder()
         .values(null, "CA")
         .labels("No Region", "California")
         .build();

      ColumnCache.FormatResult r = ColumnCache.format(model, "string", false, null,
                                                      DateRangeRef.NONE_DATE_GROUP, true);

      assertFalse(r.nullExcluded());
      assertArrayEquals(new String[] { null, "CA" }, r.data()[0]);
      assertArrayEquals(new String[] { "No Region", "California" }, r.data()[1]);
   }
}
