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

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.test.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77418: the children of a selection container must keep their z-index across save
 * and reload, and across the composer open/save cycle.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CurrentSelectionVSAssemblyZIndexTest {
   private static final String[] LISTS = { "L1", "L2", "L3" };

   @Test
   void parseAndWriteKeepsSelectionContainerChildZIndex() throws Exception {
      Viewsheet vs = reload(createSelectionContainerViewsheet());

      for(int cycle = 0; cycle < 4; cycle++) {
         assertEquals(20, vs.getAssembly("CurrentSelection").getZIndex(), "cycle " + cycle);
         assertEquals(44, vs.getAssembly("L1").getZIndex(), "cycle " + cycle);
         assertEquals(45, vs.getAssembly("L2").getZIndex(), "cycle " + cycle);
         assertEquals(46, vs.getAssembly("L3").getZIndex(), "cycle " + cycle);
         assertEquals(70, vs.getAssembly("After").getZIndex(), "cycle " + cycle);
         vs = reload(vs);
      }
   }

   @Test
   void composerOpenAndSaveKeepsSelectionContainerChildZIndex() throws Exception {
      Viewsheet vs = reload(createSelectionContainerViewsheet());
      int[] first = null;

      for(int cycle = 0; cycle < 4; cycle++) {
         composerOpen(vs);

         int containerZ = vs.getAssembly("CurrentSelection").getZIndex();
         int[] children = childZIndexes(vs);

         // children directly above the container, in their original order
         for(int i = 0; i < children.length; i++) {
            assertEquals(containerZ + 1 + i, children[i], "cycle " + cycle + " " + LISTS[i]);
         }

         assertTrue(children[children.length - 1] < vs.getAssembly("After").getZIndex(),
                    "cycle " + cycle + ": children must stay below the next top-level object");

         if(first == null) {
            first = children;
         }
         else {
            assertArrayEquals(first, children, "cycle " + cycle);
         }

         vs = reload(vs);
         assertArrayEquals(first, childZIndexes(vs), "reload after cycle " + cycle);
      }
   }

   /**
    * Tab children are not renumbered by calcChildZIndex (see Bug #72999). Guards against
    * generalizing the selection container fix to every container.
    */
   @Test
   void tabWithGroupsKeepsCurrentZIndexes() {
      Viewsheet vs = new Viewsheet();
      add(vs, new TextVSAssembly(vs, "Back"), 1);
      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      add(vs, tab, 2);
      add(vs, new TextVSAssembly(vs, "After"), 52);
      GroupContainerVSAssembly g1 = new GroupContainerVSAssembly(vs, "G1");
      GroupContainerVSAssembly g2 = new GroupContainerVSAssembly(vs, "G2");
      add(vs, g1, 5);
      add(vs, g2, 6);
      add(vs, new TextVSAssembly(vs, "T1"), 10);
      add(vs, new TextVSAssembly(vs, "T2"), 11);
      add(vs, new TextVSAssembly(vs, "T3"), 12);
      add(vs, new TextVSAssembly(vs, "T4"), 13);
      g1.setAssemblies(new String[] { "T1", "T2" });
      g2.setAssemblies(new String[] { "T3", "T4" });
      tab.setAssemblies(new String[] { "G1", "G2" });

      vs.calcChildZIndex();

      assertEquals(1, vs.getAssembly("Back").getZIndex());
      assertEquals(2, vs.getAssembly("Tab1").getZIndex());
      assertEquals(52, vs.getAssembly("After").getZIndex());
      assertEquals(5, vs.getAssembly("G1").getZIndex());
      assertEquals(6, vs.getAssembly("G2").getZIndex());
      assertEquals(3, vs.getAssembly("T1").getZIndex());
      assertEquals(4, vs.getAssembly("T2").getZIndex());
      assertEquals(3, vs.getAssembly("T3").getZIndex());
      assertEquals(4, vs.getAssembly("T4").getZIndex());
   }

   private static Viewsheet createSelectionContainerViewsheet() {
      Viewsheet vs = new Viewsheet();

      for(int i = 1; i <= 19; i++) {
         add(vs, new TextVSAssembly(vs, "Text" + i), i);
      }

      for(int i = 0; i < LISTS.length; i++) {
         add(vs, new SelectionListVSAssembly(vs, LISTS[i]), 44 + i);
      }

      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "CurrentSelection");
      add(vs, container, 20);
      container.setAssemblies(LISTS.clone());
      add(vs, new TextVSAssembly(vs, "After"), 70);
      return vs;
   }

   private static void add(Viewsheet vs, VSAssembly assembly, int zIndex) {
      assembly.setZIndex(zIndex);
      vs.addAssembly(assembly, false);
   }

   /**
    * What the composer does on open: CoreLifecycleService.refreshContainer adds the container
    * z-index to its children, then shrinkZIndex renormalizes.
    */
   private static void composerOpen(Viewsheet vs) {
      for(Assembly assembly : vs.getAssemblies()) {
         if(assembly instanceof ContainerVSAssembly) {
            VSEventUtil.updateZIndex(vs, assembly);
         }
      }

      vs.calcChildZIndex();
   }

   private static int[] childZIndexes(Viewsheet vs) {
      int[] result = new int[LISTS.length];

      for(int i = 0; i < LISTS.length; i++) {
         result[i] = vs.getAssembly(LISTS[i]).getZIndex();
      }

      return result;
   }

   /**
    * Write the viewsheet the way export does and parse it back the way import does.
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
