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
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.viewsheet.service.VSCompositionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77451: the children of a tab, and of containers nested in a tab or a group, must keep
 * their z-index across the composer open/save cycle instead of gaining the container z-index
 * on every cycle.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TabVSAssemblyZIndexTest {
   private static final int CYCLES = 3;

   @Test
   void tabChildrenKeepZIndex() throws Exception {
      assertStable(() -> {
         Viewsheet vs = new Viewsheet();
         add(vs, new TextVSAssembly(vs, "Back"), 1);
         TabVSAssembly tab = add(vs, new TabVSAssembly(vs, "Tab1"), 2);
         add(vs, new TextVSAssembly(vs, "After"), 52);
         add(vs, new ChartVSAssembly(vs, "Chart1"), 3);
         add(vs, new TableVSAssembly(vs, "Table1"), 4);
         tab.setAssemblies(new String[] { "Chart1", "Table1" });
         return vs;
      }, Map.of("Back", 1, "Tab1", 2, "After", 52, "Chart1", 3, "Table1", 4));
   }

   /**
    * A file saved before the fix already holds crept values. The first open puts them back
    * directly above the tab, in the same order.
    */
   @Test
   void creptTabChildrenAreRepaired() throws Exception {
      assertStable(() -> {
         Viewsheet vs = new Viewsheet();
         add(vs, new TextVSAssembly(vs, "Back"), 1);
         TabVSAssembly tab = add(vs, new TabVSAssembly(vs, "Tab1"), 2);
         add(vs, new TextVSAssembly(vs, "After"), 52);
         add(vs, new ChartVSAssembly(vs, "Chart1"), 61);
         add(vs, new TableVSAssembly(vs, "Table1"), 60);
         tab.setAssemblies(new String[] { "Chart1", "Table1" });
         return vs;
      }, Map.of("Tab1", 2, "After", 52, "Table1", 3, "Chart1", 4));
   }

   /**
    * Children created by grouping get the tab's own z-index, and a tab after another
    * container sits at 51. The children stay below the next top-level object.
    */
   @Test
   void tabAfterContainerKeepsChildrenBelowNextObject() throws Exception {
      assertStable(() -> {
         Viewsheet vs = new Viewsheet();
         GroupContainerVSAssembly g0 = add(vs, new GroupContainerVSAssembly(vs, "G0"), 1);
         add(vs, new TextVSAssembly(vs, "A"), 2);
         add(vs, new TextVSAssembly(vs, "B"), 3);
         g0.setAssemblies(new String[] { "A", "B" });
         TabVSAssembly tab = add(vs, new TabVSAssembly(vs, "Tab1"), 51);
         add(vs, new TextVSAssembly(vs, "After"), 101);
         add(vs, new ChartVSAssembly(vs, "C1"), 51);
         add(vs, new ChartVSAssembly(vs, "C2"), 51);
         tab.setAssemblies(new String[] { "C1", "C2" });
         return vs;
      }, Map.of("G0", 1, "A", 2, "B", 3, "Tab1", 51, "After", 101, "C1", 52, "C2", 53));
   }

   /**
    * Groups in a tab are spaced 1 apart, not by the container gap, so the second group stays
    * below the next top-level object (Bug #72999). Their children are numbered from the tab's
    * z-index, as before.
    */
   @Test
   void tabWithGroupsKeepsZIndex() throws Exception {
      assertStable(() -> {
         Viewsheet vs = new Viewsheet();
         add(vs, new TextVSAssembly(vs, "Back"), 1);
         TabVSAssembly tab = add(vs, new TabVSAssembly(vs, "Tab1"), 2);
         add(vs, new TextVSAssembly(vs, "After"), 52);
         GroupContainerVSAssembly g1 = add(vs, new GroupContainerVSAssembly(vs, "G1"), 5);
         GroupContainerVSAssembly g2 = add(vs, new GroupContainerVSAssembly(vs, "G2"), 6);
         add(vs, new TextVSAssembly(vs, "T1"), 10);
         add(vs, new TextVSAssembly(vs, "T2"), 11);
         add(vs, new TextVSAssembly(vs, "T3"), 12);
         add(vs, new TextVSAssembly(vs, "T4"), 13);
         g1.setAssemblies(new String[] { "T1", "T2" });
         g2.setAssemblies(new String[] { "T3", "T4" });
         tab.setAssemblies(new String[] { "G1", "G2" });
         return vs;
      }, Map.of("Tab1", 2, "After", 52, "G1", 3, "G2", 4,
                "T1", 3, "T2", 4, "T3", 3, "T4", 4));
   }

   @Test
   void tabInGroupKeepsZIndex() throws Exception {
      assertStable(() -> {
         Viewsheet vs = new Viewsheet();
         add(vs, new TextVSAssembly(vs, "Back"), 1);
         GroupContainerVSAssembly g = add(vs, new GroupContainerVSAssembly(vs, "G"), 2);
         add(vs, new TextVSAssembly(vs, "After"), 52);
         TabVSAssembly tab = add(vs, new TabVSAssembly(vs, "Tab1"), 3);
         add(vs, new TextVSAssembly(vs, "X"), 4);
         add(vs, new ChartVSAssembly(vs, "C1"), 5);
         add(vs, new ChartVSAssembly(vs, "C2"), 6);
         tab.setAssemblies(new String[] { "C1", "C2" });
         g.setAssemblies(new String[] { "Tab1", "X" });
         return vs;
      }, Map.of("G", 2, "After", 52, "Tab1", 3, "X", 53, "C1", 4, "C2", 5));
   }

   /**
    * Without the group recursion, the selection container override from Bug #77418 is never
    * reached for a selection container inside a group.
    */
   @Test
   void selectionContainerInGroupKeepsZIndex() throws Exception {
      assertStable(() -> {
         Viewsheet vs = new Viewsheet();
         add(vs, new TextVSAssembly(vs, "Back"), 1);
         GroupContainerVSAssembly g = add(vs, new GroupContainerVSAssembly(vs, "G"), 2);
         add(vs, new TextVSAssembly(vs, "After"), 52);
         CurrentSelectionVSAssembly cs = add(vs, new CurrentSelectionVSAssembly(vs, "CS"), 3);
         add(vs, new TextVSAssembly(vs, "X"), 4);
         add(vs, new SelectionListVSAssembly(vs, "L1"), 5);
         add(vs, new SelectionListVSAssembly(vs, "L2"), 6);
         cs.setAssemblies(new String[] { "L1", "L2" });
         g.setAssemblies(new String[] { "CS", "X" });
         return vs;
      }, Map.of("G", 2, "After", 52, "CS", 3, "X", 53, "L1", 4, "L2", 5));
   }

   @Test
   void tabInTabKeepsZIndex() throws Exception {
      assertStable(() -> {
         Viewsheet vs = new Viewsheet();
         add(vs, new TextVSAssembly(vs, "Back"), 1);
         TabVSAssembly outer = add(vs, new TabVSAssembly(vs, "Outer"), 2);
         add(vs, new TextVSAssembly(vs, "After"), 52);
         TabVSAssembly inner = add(vs, new TabVSAssembly(vs, "Inner"), 3);
         add(vs, new ChartVSAssembly(vs, "Chart0"), 4);
         add(vs, new ChartVSAssembly(vs, "I1"), 5);
         add(vs, new ChartVSAssembly(vs, "I2"), 6);
         inner.setAssemblies(new String[] { "I1", "I2" });
         outer.setAssemblies(new String[] { "Inner", "Chart0" });
         return vs;
      }, Map.of("Outer", 2, "After", 52, "Inner", 3, "Chart0", 4, "I1", 3, "I2", 4));
   }

   /**
    * The grouping UI flattens groups, but older or imported files can nest them.
    */
   @Test
   void groupInGroupKeepsZIndex() throws Exception {
      assertStable(() -> {
         Viewsheet vs = new Viewsheet();
         add(vs, new TextVSAssembly(vs, "Back"), 1);
         GroupContainerVSAssembly g = add(vs, new GroupContainerVSAssembly(vs, "G"), 2);
         add(vs, new TextVSAssembly(vs, "After"), 52);
         GroupContainerVSAssembly gi = add(vs, new GroupContainerVSAssembly(vs, "Gi"), 3);
         add(vs, new TextVSAssembly(vs, "X"), 4);
         add(vs, new TextVSAssembly(vs, "Y1"), 5);
         add(vs, new TextVSAssembly(vs, "Y2"), 6);
         gi.setAssemblies(new String[] { "Y1", "Y2" });
         g.setAssemblies(new String[] { "Gi", "X" });
         return vs;
      }, Map.of("G", 2, "After", 52, "Gi", 3, "X", 53, "Y1", 4, "Y2", 5));
   }

   /**
    * Export calls calcChildZIndex without the refresh that prunes missing children, so a tab
    * that names a missing assembly must not fail.
    */
   @Test
   void missingTabChildIsSkipped() {
      Viewsheet vs = new Viewsheet();
      TabVSAssembly tab = add(vs, new TabVSAssembly(vs, "Tab1"), 2);
      add(vs, new ChartVSAssembly(vs, "Chart1"), 9);
      add(vs, new ChartVSAssembly(vs, "Chart2"), 8);
      tab.setAssemblies(new String[] { "Chart1", "Missing", "Chart2" });

      assertDoesNotThrow(() -> vs.calcChildZIndex());
      int tabZ = vs.getAssembly("Tab1").getZIndex();
      assertEquals(tabZ + 1, vs.getAssembly("Chart2").getZIndex());
      assertEquals(tabZ + 2, vs.getAssembly("Chart1").getZIndex());
   }

   /**
    * Runs the composer open/save loop CYCLES times and checks the expected z-indexes after
    * each open and after each save.
    */
   private static void assertStable(Supplier<Viewsheet> factory, Map<String, Integer> expected)
      throws Exception
   {
      Viewsheet vs = reload(factory.get());

      for(int cycle = 1; cycle <= CYCLES; cycle++) {
         composerOpen(vs);
         assertZIndexes(vs, expected, "open " + cycle);
         vs = reload(vs);
         assertZIndexes(vs, expected, "save " + cycle);
      }
   }

   private static void assertZIndexes(Viewsheet vs, Map<String, Integer> expected, String step) {
      for(Map.Entry<String, Integer> entry : new TreeMap<>(expected).entrySet()) {
         assertEquals(entry.getValue().intValue(), vs.getAssembly(entry.getKey()).getZIndex(),
                      step + ": " + entry.getKey());
      }
   }

   /**
    * What an initing refresh does in the composer and the viewer:
    * CoreLifecycleService.refreshContainer adds each container's z-index to its children, then
    * VSCompositionService.shrinkZIndex renormalizes. refreshContainer uses no fields, so it is
    * called on a mock.
    */
   private static void composerOpen(Viewsheet vs) throws Exception {
      CoreLifecycleService service = Mockito.mock(CoreLifecycleService.class);
      Method refreshContainer =
         CoreLifecycleService.class.getDeclaredMethod("refreshContainer", Viewsheet.class);
      refreshContainer.setAccessible(true);
      refreshContainer.invoke(service, vs);
      new VSCompositionService().shrinkZIndex(vs);
   }

   private static <T extends VSAssembly> T add(Viewsheet vs, T assembly, int zIndex) {
      assembly.setZIndex(zIndex);
      vs.addAssembly(assembly, false);
      return assembly;
   }

   /**
    * Write the viewsheet and parse it back, as a save and the next open do.
    */
   private static Viewsheet reload(Viewsheet vs) throws Exception {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      writer.println("<viewsheet>");
      vs.writeXML(writer);
      writer.println("</viewsheet>");
      writer.flush();

      Viewsheet result = new Viewsheet();
      result.parseXML(Tool.parseXML(new ByteArrayInputStream(
         buffer.toString().getBytes(StandardCharsets.UTF_8))).getDocumentElement(), false);
      return result;
   }
}
