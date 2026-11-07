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
package inetsoft.web.wiz.viewsheet;

import inetsoft.test.*;
import inetsoft.uql.viewsheet.SingleTimeInfo;
import inetsoft.uql.viewsheet.TimeInfo;
import inetsoft.uql.viewsheet.TimeSliderVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80654: the step a NUMBER range slider really uses is the runtime range size, which the
 * query raises independently of the design value the dialog model reports.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, IntegrationTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AssemblyPropertyServiceEffectiveRangeSizeTest {
   private static TimeSliderVSAssembly slider(int rangeType, double design) {
      TimeSliderVSAssembly slider = new TimeSliderVSAssembly(new Viewsheet(), "S1");
      SingleTimeInfo info = new SingleTimeInfo();
      info.setRangeTypeValue(rangeType);
      info.setRangeSizeValue(design);
      slider.setTimeInfo(info);
      return slider;
   }

   @Test
   void reportsRuntimeStepWhenItDiffersFromDesign() {
      TimeSliderVSAssembly slider = slider(TimeInfo.NUMBER, 5);
      ((SingleTimeInfo) slider.getTimeInfo()).setRangeSize(10);

      assertEquals(10.0, AssemblyPropertyService.effectiveRangeSize(slider));
   }

   @Test
   void fallsBackToDesignWhenNothingOverrodeIt() {
      assertEquals(100.0, AssemblyPropertyService.effectiveRangeSize(slider(TimeInfo.NUMBER, 100)));
   }

   @Test
   void absentForNonNumberSlider() {
      assertNull(AssemblyPropertyService.effectiveRangeSize(slider(TimeInfo.MONTH, 0)));
   }

   @Test
   void absentForLogScaleSlider() {
      TimeSliderVSAssembly slider = slider(TimeInfo.NUMBER, 5);
      slider.getTimeSliderInfo().setLogScaleValue(true);

      assertNull(AssemblyPropertyService.effectiveRangeSize(slider));
   }

   @Test
   void absentForOtherAssemblies() {
      assertNull(AssemblyPropertyService.effectiveRangeSize(null));
      assertNull(AssemblyPropertyService.effectiveRangeSize("not an assembly"));
   }
}
