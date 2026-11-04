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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79996: clearing a gauge's fixed range must restore the automatic data scale even when
 * a Target is set.
 */
@Tag("core")
@SreeHome
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RangeOutputClearedRangeAutoScaleTest {
   private static final double VALUE = 17656854;

   private static void clearRange(RangeOutputVSAssemblyInfo info) {
      info.setMaxValue(null);
      info.setMinValue(null);
      info.setMajorIncValue(null);
      info.setMinorIncValue(null);
   }

   private static void refresh(RangeOutputVSAssemblyInfo info) {
      info.setValue(VALUE);
      info.setDefaultValues(true);
   }

   private static void assertAutoScaled(RangeOutputVSAssemblyInfo info) {
      assertNotNull(info.getMaxValue());
      assertTrue(Double.parseDouble(info.getMaxValue()) >= VALUE);
      assertNotEquals("20000000", info.getMaxValue());
      assertNotNull(info.getMajorIncValue());
      assertNotNull(info.getMinorIncValue());
   }

   private void reportedScenario(Supplier<RangeOutputVSAssemblyInfo> factory, String target) {
      RangeOutputVSAssemblyInfo info = factory.get();
      info.setTargetValue(target);
      info.setMaxValue("20000000");
      info.setMinValue("0");
      clearRange(info);
      refresh(info);
      assertAutoScaled(info);
      assertEquals(target, info.getTargetValue());
   }

   @Test
   void clearedRangeWithTargetAutoScales() {
      reportedScenario(GaugeVSAssemblyInfo::new, "15000000");
      reportedScenario(GaugeVSAssemblyInfo::new, "50");
   }

   @Test
   void clearedRangeWithoutTargetAutoScales() {
      RangeOutputVSAssemblyInfo info = new GaugeVSAssemblyInfo();
      clearRange(info);
      refresh(info);
      assertAutoScaled(info);
   }

   @Test
   void targetAloneWithDefaultRangeStaysUnscaled() {
      RangeOutputVSAssemblyInfo info = new GaugeVSAssemblyInfo();
      info.setTargetValue("50");
      refresh(info);
      assertEquals("100", info.getMaxValue());
   }

   @Test
   void explicitlyFixedRangeWithTargetStaysFixed() {
      RangeOutputVSAssemblyInfo info = new GaugeVSAssemblyInfo();
      info.setTargetValue("15000000");
      info.setMaxValue("20000000");
      info.setMinValue("0");
      refresh(info);
      assertEquals("20000000", info.getMaxValue());
   }

   @Test
   void siblingClassesShareTheBehavior() {
      reportedScenario(ThermometerVSAssemblyInfo::new, "15000000");
      reportedScenario(CylinderVSAssemblyInfo::new, "15000000");
   }

   // the live flow: auto scale first (increments get stored), then Target and a fixed range
   private static RangeOutputVSAssemblyInfo autoScaledThenFixed(
      Supplier<RangeOutputVSAssemblyInfo> factory, String target)
   {
      RangeOutputVSAssemblyInfo info = factory.get();
      refresh(info);

      if(target != null) {
         info.setTargetValue(target);
      }

      info.setMaxValue("20000000");
      info.setMinValue("0");
      return info;
   }

   private static void assertAutoMax(RangeOutputVSAssemblyInfo info) {
      assertEquals("3.0E7", info.getMaxValue());
      assertEquals("0", info.getMinValue());
   }

   @Test
   void partialClearAfterAutoScaleWithTarget() {
      for(String target : new String[] { "15000000", "50" }) {
         RangeOutputVSAssemblyInfo info = autoScaledThenFixed(GaugeVSAssemblyInfo::new, target);
         info.setMaxValue(null);
         info.setMinValue(null);
         refresh(info);
         assertAutoMax(info);

         info = autoScaledThenFixed(GaugeVSAssemblyInfo::new, target);
         info.setMaxValue(null);
         refresh(info);
         assertAutoMax(info);
      }
   }

   @Test
   void partialClearAfterAutoScaleWithoutTarget() {
      RangeOutputVSAssemblyInfo info = autoScaledThenFixed(GaugeVSAssemblyInfo::new, null);
      info.setMaxValue(null);
      info.setMinValue(null);
      refresh(info);
      assertAutoMax(info);
   }

   @Test
   void allFourClearAfterAutoScaleWithTarget() {
      RangeOutputVSAssemblyInfo info = autoScaledThenFixed(GaugeVSAssemblyInfo::new, "15000000");
      clearRange(info);
      refresh(info);
      assertAutoMax(info);
   }

   @Test
   void clearingOnlyIncrementsKeepsFixedMaxWithTarget() {
      RangeOutputVSAssemblyInfo info = autoScaledThenFixed(GaugeVSAssemblyInfo::new, "15000000");
      info.setMajorIncValue(null);
      info.setMinorIncValue(null);
      refresh(info);
      assertEquals("20000000", info.getMaxValue());
   }

   @Test
   void defaultTargetAfterAutoScaleUnchanged() {
      RangeOutputVSAssemblyInfo info = autoScaledThenFixed(GaugeVSAssemblyInfo::new, "80");
      info.setMaxValue(null);
      info.setMinValue(null);
      refresh(info);
      assertAutoMax(info);
   }

   @Test
   void siblingsPartialClearAfterAutoScale() {
      for(Supplier<RangeOutputVSAssemblyInfo> f : java.util.List.<Supplier<RangeOutputVSAssemblyInfo>>of(
         ThermometerVSAssemblyInfo::new, CylinderVSAssemblyInfo::new, SlidingScaleVSAssemblyInfo::new))
      {
         RangeOutputVSAssemblyInfo info = autoScaledThenFixed(f, "15000000");
         info.setMaxValue(null);
         info.setMinValue(null);
         refresh(info);
         assertAutoMax(info);
      }
   }
}
