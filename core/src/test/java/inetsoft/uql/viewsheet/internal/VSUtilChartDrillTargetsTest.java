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
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The date levels a drillable chart field leads to, for the labels of its drill buttons.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSUtilChartDrillTargetsTest {
   @Test
   void aContinuousDateLevelDrillsUpAndDownInPlace() {
      VSDimensionRef month = date(DateRangeRef.MONTH_INTERVAL, XSchema.TIME_INSTANT);

      assertArrayEquals(new String[] { "Month", "Quarter", "Day" },
                        VSUtil.getChartDrillTargets(month, null, new VSDimensionRef[] { month }));
   }

   @Test
   void theEndsOfTheContinuousLevelsHaveOneTarget() {
      // a date has no level below Day (it does not drill into the time)
      VSDimensionRef day = date(DateRangeRef.DAY_INTERVAL, XSchema.DATE);
      String[] targets = VSUtil.getChartDrillTargets(day, null, new VSDimensionRef[] { day });

      assertEquals("Day", targets[0]);
      assertEquals("Month", targets[1]);
      assertEquals("", targets[2]);

      // a time instant drills on into the hour
      VSDimensionRef instant = date(DateRangeRef.DAY_INTERVAL, XSchema.TIME_INSTANT);
      assertEquals("Hour",
                   VSUtil.getChartDrillTargets(instant, null, new VSDimensionRef[] { instant })[2]);
   }

   @Test
   void aDrillThatAddsAFieldOfTheHierarchyHasTheLevelAndNoTarget() {
      // Year drills down by adding a Quarter of Year field, which is not an in-place change
      VSDimensionRef year = date(DateRangeRef.YEAR_INTERVAL, XSchema.TIME_INSTANT);

      assertArrayEquals(new String[] { "Year", "", "" },
                        VSUtil.getChartDrillTargets(year, null, new VSDimensionRef[] { year }));
   }

   @Test
   void aFieldThatCannotBeDrilledHasNoTargets() {
      VSDimensionRef text = new VSDimensionRef(new AttributeRef("Region"));
      text.setDataType(XSchema.STRING);

      assertNull(VSUtil.getChartDrillTargets(text, null, new VSDimensionRef[] { text }));
      assertNull(VSUtil.getChartDrillTargets(null, null, new VSDimensionRef[0]));
      assertNull(VSUtil.getChartDrillTargets(
         date(DateRangeRef.MONTH_INTERVAL, XSchema.TIME_INSTANT), null, null));
   }

   private static VSDimensionRef date(int level, String type) {
      VSDimensionRef dim = new VSDimensionRef(new AttributeRef("OrderDate"));
      dim.setDataType(type);
      dim.setDateLevelValue(level + "");
      return dim;
   }
}
