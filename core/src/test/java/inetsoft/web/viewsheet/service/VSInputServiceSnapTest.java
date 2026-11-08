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
package inetsoft.web.viewsheet.service;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.SliderVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/*
 * Test strategy
 *
 * snapToIncrement used to compute round((v - min) / inc) * inc + min in binary doubles, so an
 * inexact increment (0.05, 0.1) left noise such as 0.15000000000000002, which then failed an
 * exact worksheet comparison like DISCOUNT >= $(MinDiscount). Each case asserts the exact
 * double (assertEquals on doubles with no delta), so any residual noise fails.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSInputServiceSnapTest {
   private static SliderVSAssemblyInfo slider(String min, String inc, boolean snap) {
      SliderVSAssemblyInfo info = new SliderVSAssemblyInfo();
      info.setMin(min);
      info.setIncrement(inc);
      info.setSnap(snap);
      return info;
   }

   @Test
   void onGridValueIsExact() {
      Object r = VSInputService.snapToIncrement(slider("0", "0.05", true), 0.15);
      assertEquals(0.15, (Double) r);
   }

   @Test
   void offGridValueSnapsToExactGridPoint() {
      Object r = VSInputService.snapToIncrement(slider("0", "0.05", true), 0.17);
      assertEquals(0.15, (Double) r);
   }

   @Test
   void minOffsetIsExact() {
      Object r = VSInputService.snapToIncrement(slider("0.01", "0.05", true), 0.17);
      assertEquals(0.16, (Double) r);
   }

   @Test
   void classicPointThreeCase() {
      Object r = VSInputService.snapToIncrement(slider("0", "0.1", true), 0.31);
      assertEquals(0.3, (Double) r);
   }

   @Test
   void integerSliderKeepsIntegerType() {
      Object r = VSInputService.snapToIncrement(slider("0", "5", true), 13);
      assertEquals(15, r);
   }

   @Test
   void snapOffLeavesValueUntouched() {
      Object v = 0.17;
      assertSame(v, VSInputService.snapToIncrement(slider("0", "0.05", false), v));
   }
}
