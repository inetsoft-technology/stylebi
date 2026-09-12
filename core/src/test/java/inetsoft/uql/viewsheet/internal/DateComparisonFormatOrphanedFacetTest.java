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

import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphtDataSelector;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.FieldPosition;

import static inetsoft.test.XTableUtil.date;

/**
 * Bug #76390: "Shared dc1.vso" Chart4 -- a faceted date-comparison chart (WeekOfMonth(Date)
 * facet groups 11-1..11-5, colored/grouped by YearOfWeek(Date), comparing 2019/2020/2021).
 * Only the first facet group's bars got a real calendar-date label; every bar in every
 * subsequent facet group rendered a blank label, even though the underlying data (bar
 * heights/values/colors) was byte-for-byte correct for all of them.
 *
 * Root cause: DateComparisonFormat.initPartDate() used to remove any partCol value not
 * present in DateComparisonUtil.computeValidParts(data, dateCol, partCol, null) -- the same
 * "does this part have a row in the single most recent comparison period" heuristic
 * DateComparisonUtil.applyDateRange()'s ValidPartsSelector uses to drop rows from the
 * plotted/exported dataset -- and blanked that part's label entirely (see the old
 * "orphanedCells" mechanism, format()'s early return).
 *
 * That heuristic is a poor fit here: in a faceted chart, one partCol value legitimately *is*
 * one facet group, and it is completely ordinary for different facet groups to finish
 * carrying the newest comparison period's data at different times (e.g. only the November
 * week-1 facet group has reached 2021 so far). Bug #76391 already fixed
 * DateComparisonUtil.computeValidParts() itself for a *different* symptom (a sparse-but-
 * already-reached bucket dropping older periods' real rows), but that fix does not rescue
 * this scenario: it only widens validParts to parts that sort at-or-before the single part
 * that reaches the newest period, and facet group 11-1 here is *both* the only part reaching
 * 2021 *and* numerically the smallest part, so the ordinal widening adds nothing -- groups
 * 11-2..11-5 would still come out orphaned under that fix alone.
 *
 * The actual fix for this bug (see DateComparisonFormat.initPartDate()) stops removing
 * partDates entries at all in this class: every entry partDates can ever contain is, by
 * construction, already backed by a real row with a real plotted value, so there is nothing
 * "orphaned" left to discover and strip once a part has made it into that map -- the
 * legitimate "hide a genuinely future/unreached bucket" behavior (Bug #75152/#76389) is
 * still enforced upstream, at the row level, by
 * DateComparisonUtil.applyDateRange()'s ValidPartsSelector.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateComparisonFormatOrphanedFacetTest {
   /**
    * Reproduces the exact "Shared dc1.vso" Chart4 shape: 5 WeekOfMonth facet groups
    * (encoded as month*10+week, i.e. 111 = "11-1" .. 115 = "11-5", the same MergePartCell-
    * style encoding DateComparisonUtilValidPartsTest uses), where every group has real,
    * plotted rows for 2019/2020 and *only* group 111 (11-1) additionally has a row for the
    * newest comparison year, 2021.
    */
   @Test
   void facetGroupsWithoutTheNewestPeriodStillGetRealLabels() {
      DataSet data = new DefaultDataSet(new Object[][] {
         { "date", "part", "calc" },
         { date("2019-11-03"), 111, 1.0 },
         { date("2020-11-01"), 111, 1.0 },
         { date("2021-11-07"), 111, 1.0 },   // only 11-1 reaches the newest year, 2021
         { date("2019-11-10"), 112, 1.0 },
         { date("2020-11-08"), 112, 1.0 },
         { date("2019-11-17"), 113, 1.0 },
         { date("2020-11-15"), 113, 1.0 },
         { date("2019-11-24"), 114, 1.0 },
         { date("2020-11-22"), 114, 1.0 },
         { date("2019-11-05"), 115, 1.0 },
         { date("2020-11-29"), 115, 1.0 },
         });

      GraphtDataSelector selector = (d, row, fields) -> true;
      DateComparisonFormat fmt = new DateComparisonFormat(
         data, selector, XConstants.YEAR_DATE_GROUP, DateComparisonInfo.WEEK, 0,
         "part", "date", "calc", new Object[0], null, false, true);

      String group1 = fmt.format(111, new StringBuffer(), new FieldPosition(0)).toString();
      Assertions.assertFalse(group1.isEmpty(), "the first facet group must keep its label");
      Assertions.assertTrue(group1.contains("2019") && group1.contains("2020") &&
                             group1.contains("2021"),
                             "the first facet group must show all 3 of its real dates: " +
                             group1);

      for(int part : new int[] { 112, 113, 114, 115 }) {
         String label = fmt.format(part, new StringBuffer(), new FieldPosition(0)).toString();
         Assertions.assertFalse(label.isEmpty(),
                                 "facet group " + part + " has real 2019/2020 data and must " +
                                 "not render a blank label merely because it hasn't reached " +
                                 "2021 yet (Bug #76390)");
         Assertions.assertTrue(label.contains("2019") && label.contains("2020"),
                                "facet group " + part + " must show both of its real dates: " +
                                label);
      }
   }
}
