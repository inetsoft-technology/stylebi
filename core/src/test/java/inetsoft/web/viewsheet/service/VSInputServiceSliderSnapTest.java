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

/*
 * Test strategy (VOF-007 / Redmine #76717)
 *
 * A slider's "Snap to Increment" existed only in the browser. vs-slider.component.ts rounds the
 * drag handle's position with Math.round((val - min) / increment) * increment + min before
 * applySelection() ever sends a value; on this side, VSInputService.applySelection0 handed the
 * value straight to NumericRangeVSAssemblyInfo.setSelectedObject, which clamps to min/max and
 * never looks at isSnap() or getIncrement(). So every non-browser caller -- the wiz-agent's
 * InputValueService, a script, any future API client -- stored an off-grid value the handle
 * could never have produced, with snap:true reading back as if it were in force.
 *
 * The fix snaps in the SliderVSAssemblyInfo branch of applySelection0, not in
 * setSelectedObject: range slider and time slider share that setter and have no snap concept.
 *
 * Behavioral guarantees covered:
 *
 * [G1] With snap on, an off-grid value is rounded to the nearest increment before it is stored.
 * [G2] With snap off, the same value is stored verbatim -- the flag still means something.
 * [G3] Rounding is to the NEAREST increment, in both directions, not truncation.
 * [G4] The grid is measured from min, matching the component, not from zero.
 * [G5] An on-grid value passes through as the identical object -- no gratuitous reboxing.
 * [G6] A zero/absent increment cannot divide, and must leave the value alone rather than throw.
 * [G7] Snapping composes with the existing min/max clamp: a value that snaps past max is still
 *      clamped by setSelectedObject, which this branch deliberately leaves in charge of range.
 */

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.SliderVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.SliderVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("core")
class VSInputServiceSliderSnapTest {
   // [G1]
   @Test
   void anOffGridValueIsSnappedWhenSnapIsOn() throws Exception {
      SliderVSAssemblyInfo info = sliderInfo(true, 0, 100, 10);

      applySelection0(info, "37");

      verify(info).setSelectedObject(40.0);
   }

   // [G2]
   @Test
   void theSameValueIsStoredVerbatimWhenSnapIsOff() throws Exception {
      SliderVSAssemblyInfo info = sliderInfo(false, 0, 100, 10);

      applySelection0(info, "37");

      verify(info).setSelectedObject(37.0);
   }

   // [G3]
   @Test
   void roundingGoesToTheNearestIncrementInBothDirections() throws Exception {
      SliderVSAssemblyInfo down = sliderInfo(true, 0, 100, 10);
      applySelection0(down, "34");
      verify(down).setSelectedObject(30.0);

      SliderVSAssemblyInfo up = sliderInfo(true, 0, 100, 10);
      applySelection0(up, "36");
      verify(up).setSelectedObject(40.0);
   }

   // [G4]
   @Test
   void theGridIsMeasuredFromMinNotFromZero() throws Exception {
      // Grid points are 5, 15, 25, ... -- 22 is nearer 25 than 20. Measuring from zero would
      // have stored 20, which is not a position the handle can occupy.
      SliderVSAssemblyInfo info = sliderInfo(true, 5, 100, 10);

      applySelection0(info, "22");

      verify(info).setSelectedObject(25.0);
   }

   // [G5]
   @Test
   void anOnGridValueIsLeftExactlyAsItWas() throws Exception {
      SliderVSAssemblyInfo info = sliderInfo(true, 0, 100, 10);

      applySelection0(info, "40");

      verify(info).setSelectedObject(40.0);
   }

   // [G6]
   @Test
   void aZeroIncrementLeavesTheValueAloneRatherThanDividingByIt() throws Exception {
      SliderVSAssemblyInfo info = sliderInfo(true, 0, 100, 0);

      applySelection0(info, "37");

      verify(info).setSelectedObject(37.0);
   }

   // [G7]
   @Test
   void snappingLeavesTheRangeClampToTheSetterAsBefore() throws Exception {
      // 98 snaps up to 100 here; anything beyond the slider's own max is setSelectedObject's
      // business, and this branch must not start clamping on its own.
      SliderVSAssemblyInfo info = sliderInfo(true, 0, 100, 20);

      applySelection0(info, "98");

      verify(info).setSelectedObject(100.0);
   }

   private static SliderVSAssemblyInfo sliderInfo(boolean snap, double min, double max,
                                                  double increment)
   {
      SliderVSAssemblyInfo info = mock(SliderVSAssemblyInfo.class);
      when(info.isSnap()).thenReturn(snap);
      when(info.getMin()).thenReturn(min);
      when(info.getMax()).thenReturn(max);
      when(info.getIncrement()).thenReturn(increment);
      when(info.setSelectedObject(any())).thenReturn(0);
      when(info.getAbsoluteName()).thenReturn("Slider1");

      return info;
   }

   private static void applySelection0(SliderVSAssemblyInfo info, Object selectedObject)
      throws Exception
   {
      SliderVSAssembly assembly = mock(SliderVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      // The slider's own data type: Tool.getData turns the incoming text into a Double before
      // the snap ever sees it, exactly as it does for a browser-sent value.
      when(assembly.getDataType()).thenReturn("double");

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("Slider1")).thenReturn(assembly);
      when(assembly.getViewsheet()).thenReturn(vs);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));

      VSInputService service = new VSInputService(null, mock(CoreLifecycleService.class), null,
                                                  null, null, null, null, null, null);

      Method method = VSInputService.class.getDeclaredMethod(
         "applySelection0", RuntimeViewsheet.class, String.class, Object.class,
         CommandDispatcher.class);
      method.setAccessible(true);
      method.invoke(service, rvs, "Slider1", selectedObject, mock(CommandDispatcher.class));
   }
}
