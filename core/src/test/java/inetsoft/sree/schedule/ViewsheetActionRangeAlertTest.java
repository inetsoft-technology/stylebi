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

package inetsoft.sree.schedule;

import inetsoft.analytic.composition.VSPortalHelper;
import inetsoft.report.io.viewsheet.AbstractVSExporter;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.RangeOutputVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Tier: [integration-lite] - BaseTestConfiguration + @SreeHome; no RuntimeViewsheet, no
 *       ScheduleTask, no storage-backed viewsheet.
 *
 * Covered:
 *  - AlertExporter.checkHighlight(VSAssembly) called directly on a hand-built assembly whose
 *    runtime ranges are shrunk with info.setRanges(...) (the setter the "ranges" script
 *    property uses): removed range, index > count, empty ranges, Range_0, multi-alert,
 *    in-range and design (no script) cases, for gauge / thermometer / cylinder / sliding scale.
 *  - The real script path: an element script "ranges = ['40','80']" run by Rhino through a
 *    real ViewsheetSandbox and AlertExporter.export() (clone, prepareSheet, executeScript,
 *    writeX, checkHighlight), with the alert map keyed on a separate viewsheet clone the way
 *    ViewsheetAction.checkAlerts() keys it on rvs.getViewsheet().
 */

/*
 * Cases deferred - require integration context:
 *
 * [ViewsheetAction] checkAlerts() validation of RangeOutput_Range_N against getRangeValues()
 *             -> needs RuntimeViewsheet from ScheduleViewsheetService; NOT covered here
 * [ViewsheetAction] run(Principal) recording the task result when an alert check throws
 *             -> needs a full scheduled viewsheet run; NOT covered here
 */

/**
 * Tests for the range-output alert check in {@code ViewsheetAction.AlertExporter}
 * ({@code checkHighlight(VSAssembly)}), shared by gauge, thermometer, cylinder and
 * sliding scale.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetActionRangeAlertTest {
   /**
    * Bug #77182: the alert ids are based on the design ranges, but an element script
    * may set fewer ranges. An alert on a removed range must not fail the task with
    * an ArrayIndexOutOfBoundsException, it just can't match.
    */
   @ParameterizedTest
   @ValueSource(strings = { "gauge", "thermometer", "cylinder", "slidingScale" })
   void alertOnRangeRemovedByScriptIsNotTriggered(String type) throws Exception {
      VSAssembly assembly = createAssembly(type, 90);
      shrinkRanges(assembly, "40", "80");

      assertFalse(check(assembly, "RangeOutput_Range_3"));
      assertFalse(check(assembly, "RangeOutput_Range_5"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "gauge", "thermometer", "cylinder", "slidingScale" })
   void alertOnRemainingRangeUsesRuntimeRanges(String type) throws Exception {
      VSAssembly assembly = createAssembly(type, 50);
      shrinkRanges(assembly, "40", "80");

      assertFalse(check(assembly, "RangeOutput_Range_1"));
      assertTrue(check(assembly, "RangeOutput_Range_2"));
   }

   @Test
   void emptyRuntimeRangesDoNotTriggerFirstRange() throws Exception {
      VSAssembly assembly = createAssembly("gauge", 10);
      shrinkRanges(assembly);

      assertFalse(check(assembly, "RangeOutput_Range_1"));
   }

   @Test
   void removedRangeDoesNotStopLaterAlertsFromMatching() throws Exception {
      VSAssembly assembly = createAssembly("gauge", 50);
      shrinkRanges(assembly, "40", "80");

      assertTrue(check(assembly, "RangeOutput_Range_3", "RangeOutput_Range_2"));
   }

   @Test
   void outOfRangeIndexBelowFirstRangeIsNotTriggered() throws Exception {
      VSAssembly assembly = createAssembly("gauge", 10);

      assertFalse(check(assembly, "RangeOutput_Range_0"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "gauge", "thermometer", "cylinder", "slidingScale" })
   void designRangesStillTrigger(String type) throws Exception {
      assertTrue(check(createAssembly(type, 10), "RangeOutput_Range_1"));
      assertFalse(check(createAssembly(type, 10), "RangeOutput_Range_2"));
      assertTrue(check(createAssembly(type, 45), "RangeOutput_Range_2"));
      assertTrue(check(createAssembly(type, 90), "RangeOutput_Range_3"));
      assertFalse(check(createAssembly(type, 60), "RangeOutput_Range_1"));
   }

   /**
    * Bug #77182, through the real script path: the element script runs in the export
    * sandbox and shrinks the runtime ranges after the alert ids were validated against
    * the design ranges.
    */
   @ParameterizedTest
   @ValueSource(strings = { "gauge", "thermometer", "cylinder", "slidingScale" })
   void alertOnRangeRemovedByElementScriptIsNotTriggered(String type) throws Exception {
      assertFalse(exportWithScript(type, 90, "ranges = ['40','80'];", "RangeOutput_Range_3"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "gauge", "thermometer", "cylinder", "slidingScale" })
   void alertOnRangeKeptByElementScriptUsesScriptRanges(String type) throws Exception {
      // design Range_2 is [30,60), the script makes it [40,80)
      assertTrue(exportWithScript(type, 70, "ranges = ['40','80'];", "RangeOutput_Range_2"));
      assertFalse(exportWithScript(type, 35, "ranges = ['40','80'];", "RangeOutput_Range_2"));
      assertTrue(exportWithScript(type, 35, "ranges = ['40','80'];", "RangeOutput_Range_1"));
   }

   @Test
   void designAlertStillTriggersThroughExportWithoutScript() throws Exception {
      assertTrue(exportWithScript("gauge", 90, null, "RangeOutput_Range_3"));
   }

   private static VSAssembly createAssembly(String type, double value) {
      Viewsheet vs = new Viewsheet();
      VSAssembly assembly = switch(type) {
         case "gauge" -> new GaugeVSAssembly(vs, "Gauge1");
         case "thermometer" -> new ThermometerVSAssembly(vs, "Thermometer1");
         case "cylinder" -> new CylinderVSAssembly(vs, "Cylinder1");
         case "slidingScale" -> new SlidingScaleVSAssembly(vs, "SlidingScale1");
         default -> throw new IllegalArgumentException(type);
      };
      vs.addAssembly(assembly);

      RangeOutputVSAssemblyInfo info = (RangeOutputVSAssemblyInfo) assembly.getInfo();
      info.setRangeValues(new Object[] { "30", "60", "100" });
      info.setValue(value);
      return assembly;
   }

   // same path as an element script "ranges = [...]"
   private static void shrinkRanges(VSAssembly assembly, Object... ranges) {
      RangeOutputVSAssemblyInfo info = (RangeOutputVSAssemblyInfo) assembly.getInfo();
      info.setRanges(ranges);
      assertEquals(3, info.getRangeValues().length);
      assertEquals(ranges.length, info.getRanges().length);
   }

   /**
    * Runs the real AlertExporter.export() on a viewsheet sandbox, so the element script is
    * executed by the script engine before checkHighlight().
    */
   private static boolean exportWithScript(String type, double value, String script,
                                           String... highlights)
      throws Exception
   {
      VSAssembly assembly = createAssembly(type, value);
      Viewsheet vs = assembly.getViewsheet();

      if(script != null) {
         assembly.getVSAssemblyInfo().setScriptEnabled(true);
         assembly.getVSAssemblyInfo().setScript(script);
      }

      // checkAlerts() keys the alerts on the assemblies of a different viewsheet instance
      Assembly alertKey = vs.clone().getAssembly(assembly.getAbsoluteName());
      List<ScheduleAlert> alerts = new ArrayList<>();

      for(String highlight : highlights) {
         ScheduleAlert alert = new ScheduleAlert();
         alert.setElementId(assembly.getAbsoluteName());
         alert.setHighlightName(highlight);
         alerts.add(alert);
      }

      Map<Assembly, List<ScheduleAlert>> map = new HashMap<>();
      map.put(alertKey, alerts);
      AtomicBoolean triggered = new AtomicBoolean(false);

      Class<?> cls = Class.forName(ViewsheetAction.class.getName() + "$AlertExporter");
      Constructor<?> cstr = cls.getDeclaredConstructor(
         Map.class, AtomicBoolean.class, ViewsheetSandbox.class);
      cstr.setAccessible(true);
      AbstractVSExporter exporter =
         (AbstractVSExporter) cstr.newInstance(map, triggered, null);

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.VIEWSHEET, "RangeAlertTest", null);
      ViewsheetSandbox box = new ViewsheetSandbox(vs, Viewsheet.SHEET_RUNTIME_MODE, null, entry);

      try {
         assertDoesNotThrow(() -> exporter.export(box, "Current View", new VSPortalHelper()));
      }
      finally {
         box.dispose();
      }

      return triggered.get();
   }

   private static boolean check(VSAssembly assembly, String... highlights) throws Exception {
      List<ScheduleAlert> alerts = new ArrayList<>();

      for(String highlight : highlights) {
         ScheduleAlert alert = new ScheduleAlert();
         alert.setElementId(assembly.getAbsoluteName());
         alert.setHighlightName(highlight);
         alerts.add(alert);
      }

      Map<Assembly, List<ScheduleAlert>> map = new HashMap<>();
      map.put(assembly, alerts);
      AtomicBoolean triggered = new AtomicBoolean(false);

      Class<?> cls = Class.forName(ViewsheetAction.class.getName() + "$AlertExporter");
      Constructor<?> cstr = cls.getDeclaredConstructor(
         Map.class, AtomicBoolean.class, ViewsheetSandbox.class);
      cstr.setAccessible(true);
      Object exporter = cstr.newInstance(map, triggered, null);
      Method method = cls.getDeclaredMethod("checkHighlight", VSAssembly.class);
      method.setAccessible(true);

      try {
         method.invoke(exporter, assembly);
      }
      catch(InvocationTargetException ex) {
         fail("checkHighlight threw " + ex.getCause(), ex.getCause());
      }

      return triggered.get();
   }
}
