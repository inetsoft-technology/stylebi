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
package inetsoft.sree.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80158: the import loop follows the sortFiles order, so a data source must sort before its
 * partitions, VPMs, queries and logical models whatever order listFiles() returned.
 */
@Tag("core")
class DeployManagerServiceSortFilesTest {
   // entry names of the community examples.zip, in jar order
   private static final String[] ENTRIES = {
      "DEVICE_inetsoft.util.dep.DeviceAsset^72d90961-cfc1-4b88-9ba6-d5431f63e1ac",
      "WORKSHEET_inetsoft.util.dep.WorksheetAsset^1^2^__NULL__^Examples^_^Hurricane^host-org",
      "WORKSHEET_inetsoft.util.dep.WorksheetAsset^1^2^__NULL__^Examples^_^Map Points^host-org",
      "DEVICE_inetsoft.util.dep.DeviceAsset^8795d12e-90e7-4146-8b1c-7daafc0f2806",
      "WORKSHEET_inetsoft.util.dep.WorksheetAsset^1^2^__NULL__^Examples^_^Census Data^host-org",
      "WORKSHEET_inetsoft.util.dep.WorksheetAsset^1^2^__NULL__^Examples^_^Call Center Monitoring^host-org",
      "SCRIPT_inetsoft.util.dep.ScriptAsset^setDimColor",
      "TABLESTYLE_inetsoft.util.dep.TableStyleAsset^BorderedLightBands",
      "WORKSHEET_inetsoft.util.dep.WorksheetAsset^1^2^__NULL__^Examples^_^Construction Data^host-org",
      "XDATASOURCE_inetsoft.util.dep.XDataSourceAsset^Examples^_^Orders",
      "DEVICE_inetsoft.util.dep.DeviceAsset^409f3e4a-505e-41f3-a199-0f2b5eb63139",
      "WORKSHEET_inetsoft.util.dep.WorksheetAsset^1^2^__NULL__^Examples^_^Sales Revenue^host-org",
      "DEVICE_inetsoft.util.dep.DeviceAsset^75a882db-8a62-4d4e-904f-854e8ad1e7ac",
      "WORKSHEET_inetsoft.util.dep.WorksheetAsset^1^2^__NULL__^Examples^_^Order Details^host-org",
      "VIEWSHEET_inetsoft.util.dep.ViewsheetAsset^1^128^__NULL__^Examples^_^HurricaneL^host-org",
      "VIEWSHEET_inetsoft.util.dep.ViewsheetAsset^1^128^__NULL__^Examples^_^HurricaneR^host-org",
      "XPARTITION_inetsoft.util.dep.XPartitionAsset^Examples^_^Orders^Order View",
      "WORKSHEET_inetsoft.util.dep.WorksheetAsset^1^2^__NULL__^Sample Queries^_^All Sales^host-org",
      "WORKSHEET_inetsoft.util.dep.WorksheetAsset^1^2^__NULL__^Sample Queries^_^Fast Go Game^host-org",
      "WORKSHEET_inetsoft.util.dep.WorksheetAsset^1^2^__NULL__^Sample Queries^_^Wireless Mouse^host-org",
      "WORKSHEET_inetsoft.util.dep.WorksheetAsset^1^2^__NULL__^Sample Queries^_^customers^host-org",
      "WORKSHEET_inetsoft.util.dep.WorksheetAsset^1^2^__NULL__^Sample Queries^_^sales by state^host-org",
      "VIEWSHEET_inetsoft.util.dep.ViewsheetAsset^1^128^__NULL__^Examples^_^Census^host-org",
      "VIEWSHEET_inetsoft.util.dep.ViewsheetAsset^1^128^__NULL__^Examples^_^Call Center Monitoring^host-org",
      "VIEWSHEET_inetsoft.util.dep.ViewsheetAsset^1^128^__NULL__^Examples^_^Construction Dashboard^host-org",
      "VIEWSHEET_inetsoft.util.dep.ViewsheetAsset^1^128^__NULL__^Examples^_^Sales Summary^host-org",
      "VIEWSHEET_inetsoft.util.dep.ViewsheetAsset^1^128^__NULL__^Examples^_^Return Analysis^host-org",
      "VIEWSHEET_inetsoft.util.dep.ViewsheetAsset^1^128^__NULL__^Examples^_^Hurricane^host-org",
      "XLOGICALMODEL_inetsoft.util.dep.XLogicalModelAsset^Examples^_^Orders^Order Model",
      "XLOGICALMODEL_inetsoft.util.dep.XLogicalModelAsset^Examples^_^Orders^Return Model",
      "DASHBOARD_inetsoft.util.dep.DashboardAsset^Census__GLOBAL^__NULL__",
      "DASHBOARD_inetsoft.util.dep.DashboardAsset^Call Center__GLOBAL^__NULL__",
      "DASHBOARD_inetsoft.util.dep.DashboardAsset^Construction__GLOBAL^__NULL__",
      "DASHBOARD_inetsoft.util.dep.DashboardAsset^Sales Summary__GLOBAL^__NULL__",
      "DASHBOARD_inetsoft.util.dep.DashboardAsset^Analysis__GLOBAL^__NULL__",
      "DASHBOARD_inetsoft.util.dep.DashboardAsset^Hurricane__GLOBAL^__NULL__",
      "WORKSHEET_inetsoft.util.dep.WorksheetAsset^1^2^__NULL__^Examples^_^Projection^host-org",
      "WORKSHEET_inetsoft.util.dep.WorksheetAsset^1^2^__NULL__^Examples^_^ProductSales^host-org",
      "WORKSHEET_inetsoft.util.dep.WorksheetAsset^1^2^__NULL__^Examples^_^Sales Explore^host-org",
      "WORKSHEET_inetsoft.util.dep.WorksheetAsset^1^2^__NULL__^Examples^_^Analysis^host-org",
      "VIEWSHEET_inetsoft.util.dep.ViewsheetAsset^1^128^__NULL__^Examples^_^Projection^host-org",
      "DASHBOARD_inetsoft.util.dep.DashboardAsset^Projection What-if__GLOBAL^__NULL__",
      "WORKSHEET_inetsoft.util.dep.WorksheetAsset^1^2^__NULL__^Sample Queries^_^Sales by Date Range^host-org",
      "VIEWSHEET_inetsoft.util.dep.ViewsheetAsset^1^128^__NULL__^Examples^_^OrderList^host-org",
      "JarFileInfo.xml"
   };

   @TempDir
   Path dir;
   private final Map<String, String> names = new HashMap<>();
   private final List<String> jarOrder = new ArrayList<>();
   private final Map<String, File> files = new LinkedHashMap<>();

   @BeforeEach
   void setUp() throws Exception {
      for(String entry : ENTRIES) {
         String fname = "f" + Math.abs(entry.hashCode());
         File file = dir.resolve(fname).toFile();
         assertTrue(file.createNewFile());
         assertTrue(file.setLastModified(1_000_000_000_000L));
         names.put(fname, entry);
         jarOrder.add(fname);
         files.put(entry, file);
      }
   }

   private List<String> sort(List<String> entries, List<String> order) throws Exception {
      File[] input = new File[entries.size()];

      for(int i = 0; i < input.length; i++) {
         input[i] = files.get(entries.get(i));
      }

      Method m = DeployManagerService.class.getDeclaredMethod(
         "sortFiles", File[].class, List.class, Map.class);
      m.setAccessible(true);
      m.invoke(null, input, order, names);
      List<String> result = new ArrayList<>();

      for(File f : input) {
         result.add(names.get(f.getName()));
      }

      return result;
   }

   private static int firstIndex(List<String> sorted, String prefix) {
      for(int i = 0; i < sorted.size(); i++) {
         if(sorted.get(i).startsWith(prefix)) {
            return i;
         }
      }

      return -1;
   }

   private void assertDependencyOrder(List<String> sorted) {
      int ds = firstIndex(sorted, "XDATASOURCE_");

      for(String prefix : new String[] { "XPARTITION_", "VPM_", "XQUERY_", "XLOGICALMODEL_" }) {
         int dep = firstIndex(sorted, prefix);
         assertTrue(dep < 0 || ds < dep, prefix + " sorted before its data source");
      }

      assertTrue(ds < firstIndex(sorted, "WORKSHEET_"));
      assertTrue(firstIndex(sorted, "WORKSHEET_") < firstIndex(sorted, "VIEWSHEET_"));
      assertTrue(firstIndex(sorted, "VIEWSHEET_") < firstIndex(sorted, "DASHBOARD_"));
   }

   @Test
   void threeFilePermutationsAreIdentical() throws Exception {
      List<String> three = Arrays.asList(ENTRIES[6], ENTRIES[9], ENTRIES[1]);
      Set<List<String>> outputs = new HashSet<>();

      for(List<String> p : permutations(three)) {
         List<String> sorted = sort(p, jarOrder);
         outputs.add(sorted);
         assertTrue(sorted.indexOf(ENTRIES[9]) < sorted.indexOf(ENTRIES[1]));
      }

      assertEquals(1, outputs.size());
   }

   @Test
   void fullSetIsTotalForShuffledInput() throws Exception {
      checkShuffles(jarOrder);
   }

   @Test
   void nullOrderIsTotalToo() throws Exception {
      checkShuffles(null);
   }

   private void checkShuffles(List<String> order) throws Exception {
      Random random = new Random(2000);
      List<String> canonical = null;

      for(int i = 0; i < 2000; i++) {
         List<String> input = new ArrayList<>(Arrays.asList(ENTRIES));
         Collections.shuffle(input, random);
         List<String> sorted = sort(input, order);
         assertDependencyOrder(sorted);

         if(canonical == null) {
            canonical = sorted;
         }

         assertEquals(canonical, sorted);
      }

      assertEquals("JarFileInfo.xml", canonical.get(canonical.size() - 1));
   }

   private static List<List<String>> permutations(List<String> items) {
      if(items.size() <= 1) {
         return Collections.singletonList(new ArrayList<>(items));
      }

      List<List<String>> result = new ArrayList<>();

      for(int i = 0; i < items.size(); i++) {
         List<String> rest = new ArrayList<>(items);
         String head = rest.remove(i);

         for(List<String> p : permutations(rest)) {
            p.add(0, head);
            result.add(p);
         }
      }

      return result;
   }
}
