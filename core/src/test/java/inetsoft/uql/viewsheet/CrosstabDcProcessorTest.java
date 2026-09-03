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
package inetsoft.uql.viewsheet;

import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.internal.DateComparisonInfo;
import inetsoft.uql.viewsheet.internal.DateComparisonInterval;
import inetsoft.uql.viewsheet.internal.StandardPeriods;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CrosstabDcProcessorTest {
   /**
    * updatePeriodDim()'s guard is only reachable when the outer updateRuntimeHeaders() guard
    * did NOT already build a periodDim -- i.e. periods are StandardPeriods and the period
    * level matches the interval's granularity level -- while the interval itself is not
    * "compare all" (a real, non-ALL level). Before the fix, a typo'd local ("peroidDim") meant
    * the computed clone was thrown away and null was always returned here.
    */
   @Test
   void buildsPeriodDimWhenIntervalIsNotCompareAllButLevelMatchesGranularity() throws Exception {
      StandardPeriods periods = new StandardPeriods();
      periods.setPreCountValue("1");
      periods.setDateLevelValue(String.valueOf(XConstants.YEAR_DATE_GROUP));

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setGranularityValue(String.valueOf(DateComparisonInfo.YEAR));
      interval.setLevelValue(String.valueOf(DateComparisonInfo.YEAR_TO_DATE));

      DateComparisonInfo dcInfo = new DateComparisonInfo();
      dcInfo.setDateComparisonPeriods(periods);
      dcInfo.setDateComparisonInterval(interval);

      Assertions.assertFalse(dcInfo.isCompareAll());
      Assertions.assertTrue(dcInfo.periodLevelSameAsGranularityLevel());

      CrosstabDcProcessor processor =
         new CrosstabDcProcessor(null, dcInfo, null, null, null);

      VSDimensionRef dateDim = new VSDimensionRef();
      dateDim.setDataRef(new inetsoft.uql.erm.AttributeRef(null, "Order Date"));
      dateDim.setDateLevelValue(String.valueOf(XConstants.MONTH_DATE_GROUP));

      Method updatePeriodDim =
         CrosstabDcProcessor.class.getDeclaredMethod(
            "updatePeriodDim", XDimensionRef.class, DataRef.class);
      updatePeriodDim.setAccessible(true);

      XDimensionRef result =
         (XDimensionRef) updatePeriodDim.invoke(processor, (XDimensionRef) null, dateDim);

      Assertions.assertNotNull(result);
      Assertions.assertEquals(dcInfo.getPeriodDateLevel(), result.getDateLevel());
      Assertions.assertTrue(((VSDimensionRef) result).isDcRange());
   }
}
