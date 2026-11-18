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

import inetsoft.uql.viewsheet.internal.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81702: a group (or tab) holding a calendar that is switched to a double calendar in a
 * device layout must grow with it. The growth is overflow-only and uses unscaled layout
 * geometry, and a container that is not part of the layout or is a pop component is not
 * touched (Bug #59133).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class GroupContainerLayoutGrowTest {
   @Test
   void groupGrowsToCoverTheWidenedCalendar() {
      build(false);
      layout("Calendar1", 9, 9, 390, 156);

      List<String> changed = grow();

      assertEquals(List.of("Group1"), changed);
      assertEquals(new Dimension(390, 204), size("Group1"));
   }

   @Test
   void calendarOffsetInsideTheGroupIsAccountedFor() {
      build(false);
      layout("Calendar1", 59, 9, 390, 156);

      grow();

      assertEquals(new Dimension(440, 204), size("Group1"));
   }

   @Test
   void growsHeightOnVerticalOverflow() {
      build(false);
      layout("Calendar1", 9, 9, 100, 300);

      grow();

      assertEquals(new Dimension(291, 300), size("Group1"));
   }

   @Test
   void groupThatAlreadyFitsIsUnchanged() {
      build(false);
      layout("Calendar1", 9, 9, 195, 156);

      assertTrue(grow().isEmpty());
      assertEquals(new Dimension(291, 204), size("Group1"));
   }

   @Test
   void groupIsNeverShrunkBelowItsDesignedSize() {
      build(false);
      layout("Calendar1", 9, 9, 390, 156);
      grow();
      layout("Calendar1", 9, 9, 195, 156);

      assertTrue(grow().isEmpty());
      assertEquals(new Dimension(390, 204), size("Group1"));
   }

   @Test
   void popComponentGroupIsLeftAlone() {
      build(true);
      layout("Calendar1", 9, 9, 390, 156);

      assertTrue(grow().isEmpty());
      assertEquals(new Dimension(291, 204), size("Group1"));
   }

   @Test
   void groupWithoutLayoutSizeIsLeftAlone() {
      build(false);
      info("Group1").setLayoutSize(null);
      layout("Calendar1", 9, 9, 390, 156);

      assertTrue(grow().isEmpty());
      assertNull(info("Group1").getLayoutSize(false));
   }

   @Test
   void calendarWithoutLayoutGeometryChangesNothing() {
      build(false);
      info("Calendar1").setLayoutSize(null);

      assertTrue(grow().isEmpty());
      assertEquals(new Dimension(291, 204), size("Group1"));
   }

   @Test
   void calendarOutsideAnyContainerChangesNothing() {
      build(false);
      CalendarVSAssembly loose = new CalendarVSAssembly(vs, "Loose");
      vs.addAssembly(loose);

      assertTrue(GroupContainerVSAssembly.growContainersToFitLayout(vs, loose).isEmpty());
   }

   @Test
   void tabInsideGroupGrowsBothTabAndGroup() {
      build(false);
      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      vs.addAssembly(tab);
      tab.setAssemblies(new String[] { "Calendar1" });
      ((ContainerVSAssembly) vs.getAssembly("Group1")).setAssemblies(new String[] { "Tab1" });
      info("Tab1").setLayoutPosition(new Point(9, 9));
      info("Tab1").setLayoutSize(new Dimension(195, 180));
      layout("Calendar1", 9, 30, 390, 156);

      List<String> changed = grow();

      assertEquals(List.of("Tab1", "Group1"), changed);
      assertEquals(new Dimension(390, 180), size("Tab1"));
      assertEquals(new Dimension(390, 204), size("Group1"));
   }

   @Test
   void scaledGeometryIsIgnoredAndTheScaledSizeIsDropped() {
      build(false);
      layout("Calendar1", 9, 9, 390, 156);
      // the scaled copies are what a scale-to-screen layout reads; they must not be mixed in
      info("Calendar1").setScaledPosition(new Point(0, 0));
      info("Calendar1").setScaledSize(new Dimension(10, 10));
      info("Group1").setScaledPosition(new Point(0, 0));
      info("Group1").setScaledSize(new Dimension(5000, 5000));

      assertEquals(List.of("Group1"), grow());

      assertEquals(new Dimension(390, 204), info("Group1").getLayoutSize(false));
      assertEquals(new Dimension(390, 204), info("Group1").getLayoutSize());
   }

   private void build(boolean popGroup) {
      vs = new Viewsheet();
      GroupContainerVSAssembly group = new GroupContainerVSAssembly(vs, "Group1");
      CalendarVSAssembly calendar = new CalendarVSAssembly(vs, "Calendar1");
      vs.addAssembly(group);
      vs.addAssembly(calendar);
      group.setAssemblies(new String[] { "Calendar1" });
      info("Group1").setLayoutPosition(new Point(9, 9));
      info("Group1").setLayoutSize(new Dimension(291, 204));
      layout("Calendar1", 9, 9, 195, 156);

      if(popGroup) {
         TextVSAssembly text = new TextVSAssembly(vs, "Text1");
         vs.addAssembly(text);
         TextVSAssemblyInfo tinfo = (TextVSAssemblyInfo) text.getVSAssemblyInfo();
         tinfo.setPopOptionValue(PopVSAssemblyInfo.POP_OPTION);
         tinfo.setPopComponentValue("Group1");
      }
   }

   private List<String> grow() {
      return GroupContainerVSAssembly.growContainersToFitLayout(vs, vs.getAssembly("Calendar1"));
   }

   private void layout(String name, int x, int y, int w, int h) {
      info(name).setLayoutPosition(new Point(x, y));
      info(name).setLayoutSize(new Dimension(w, h));
   }

   private VSAssemblyInfo info(String name) {
      return ((VSAssembly) vs.getAssembly(name)).getVSAssemblyInfo();
   }

   private Dimension size(String name) {
      return info(name).getLayoutSize(false);
   }

   private Viewsheet vs;
}
