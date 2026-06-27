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
package inetsoft.web.wiz.service;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.web.wiz.model.DimensionFieldInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.mockito.Mockito.*;

/**
 * Regression tests for {@link WizAutoBindingService#applyDateGroup}.
 *
 * <p>The bug: an explicit {@code dateGroupLevel:"none"} on a date dimension was silently dropped —
 * the old guard only applied non-NONE levels, so the recommender's default (YEAR) survived and a
 * worksheet column already bucketed to month (e.g. {@code Month(date_entered)}) got re-grouped by
 * year, collapsing a 13-month series into 2 yearly points. The fix makes the caller's level
 * authoritative, including "none".
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizAutoBindingServiceDateGroupTest {
   private static DimensionFieldInfo dimFc(String dateGroupLevel, boolean timeSeries) {
      DimensionFieldInfo fc = new DimensionFieldInfo();
      fc.setField("Month(date_entered)");
      fc.setDateGroupLevel(dateGroupLevel);
      fc.setTimeSeries(timeSeries);
      return fc;
   }

   @Test
   void explicitNoneOverridesRecommenderDefault() {
      VSDimensionRef dim = mock(VSDimensionRef.class);

      // The caller asks for no grouping on an already-month-bucketed column.
      WizAutoBindingService.applyDateGroup(dim, dimFc("none", true));

      // NONE_DATE_GROUP must be pushed onto the dimension so it overrides the recommender's
      // default YEAR level — previously this call never happened and the bug surfaced.
      verify(dim).setDateLevelValue(String.valueOf(XConstants.NONE_DATE_GROUP));
      verify(dim).setTimeSeries(true);
   }

   @Test
   void explicitMonthIsApplied() {
      VSDimensionRef dim = mock(VSDimensionRef.class);
      int month = WizDateLevelUtil.getDateGroupLevel("month");

      WizAutoBindingService.applyDateGroup(dim, dimFc("month", true));

      verify(dim).setDateLevelValue(String.valueOf(month));
      verify(dim).setTimeSeries(true);
   }

   @Test
   void timeSeriesFlagIsForwarded() {
      VSDimensionRef dim = mock(VSDimensionRef.class);

      WizAutoBindingService.applyDateGroup(dim, dimFc("year", false));

      verify(dim).setTimeSeries(false);
   }

   @Test
   void nullLevelIsANoOp() {
      VSDimensionRef dim = mock(VSDimensionRef.class);

      WizAutoBindingService.applyDateGroup(dim, dimFc(null, true));

      verify(dim, never()).setDateLevelValue(anyString());
      verify(dim, never()).setTimeSeries(anyBoolean());
   }

   @Test
   void unsupportedLevelIsIgnoredWithoutThrowing() {
      VSDimensionRef dim = mock(VSDimensionRef.class);

      // Must not propagate the IllegalArgumentException from WizDateLevelUtil; lenient by design.
      WizAutoBindingService.applyDateGroup(dim, dimFc("fortnight", true));

      verify(dim, never()).setDateLevelValue(anyString());
      verify(dim, never()).setTimeSeries(anyBoolean());
   }
}
