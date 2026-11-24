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
package inetsoft.web.wiz.controller;

import inetsoft.web.wiz.controller.WizDataModelErrors.DmFailure;
import inetsoft.web.wiz.controller.WizPhysicalViewDiff.Plan;
import inetsoft.web.wiz.controller.WizPhysicalViewMapper.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The layout of a physical view in the wire form and in the plan, with no server: the position of
 * a table (read as {@code bounds}, written as {@code x}/{@code y}), Auto Layout and the viewport.
 */
@Tag("core")
class WizPhysicalViewLayoutPlanTest {
   private static Map<String, Object> table(Object... keyValues) {
      Map<String, Object> map = new LinkedHashMap<>();

      for(int i = 0; i < keyValues.length; i += 2) {
         map.put((String) keyValues[i], keyValues[i + 1]);
      }

      return map;
   }

   private static Map<String, Object> definition(Map<String, Object>... tables) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("name", "v");
      map.put("tables", new ArrayList<>(List.of(tables)));
      return map;
   }

   private static TableDef parsedTable(Map<String, Object> table) {
      return WizPhysicalViewMapper.parse(definition(table)).view().tables().get(0);
   }

   private static String problems(Map<String, Object> definition) {
      DmFailure failure = assertThrows(DmFailure.class,
                                       () -> WizPhysicalViewMapper.parse(definition));
      StringBuilder text = new StringBuilder();
      failure.toBody().errors().forEach(e -> text.append(e.field()).append(": ")
         .append(e.message()).append(" | "));
      return text.toString();
   }

   private static TableDef placed(String name, int x, int y, boolean base) {
      return new TableDef(name, Kind.PLAIN, null, null, null, null, null, base, x, y, 100, 50);
   }

   private static TableDef unplaced(String name) {
      return new TableDef(name, Kind.PLAIN, null, null, null, null, null, false);
   }

   private static TableDef wanted(String name, Integer x, Integer y) {
      return new TableDef(name, Kind.PLAIN, null, null, null, null, null, false, x, y, null,
                          null);
   }

   private static View view(TableDef... tables) {
      return new View("v", null, null, null, null, List.of(tables), List.of());
   }

   private static Desired desired(AutoLayout layout, Viewport viewport, TableDef... tables) {
      return new Desired(view(tables), false, false, false, true, false, layout, viewport);
   }

   // -- reading --------------------------------------------------------------------------------

   @Test
   void aPlacedTableIsReadWithItsBoundsAndAnUnplacedOneWithout() {
      View stored = view(placed("T1", 10, 20, false), unplaced("T2"));
      List<?> tables = (List<?>) WizPhysicalViewMapper.toWire(stored).get("tables");

      assertEquals(Map.of("x", 10, "y", 20, "width", 100, "height", 50),
                   ((Map<?, ?>) tables.get(0)).get("bounds"));
      assertFalse(((Map<?, ?>) tables.get(1)).containsKey("bounds"));
   }

   @Test
   void theRevisionIgnoresTheLayout() {
      View a = view(placed("T1", 10, 20, false));
      View b = view(placed("T1", 300, 400, false));

      assertNotEquals(WizPhysicalViewMapper.toWire(a), WizPhysicalViewMapper.toWire(b));
      assertEquals(WizPhysicalViewMapper.toRevisionForm(a),
                   WizPhysicalViewMapper.toRevisionForm(b));
   }

   // -- parsing a position ---------------------------------------------------------------------

   @Test
   void aPositionIsAcceptedUnderItsNaturalSpellings() {
      TableDef xy = parsedTable(table("name", "T1", "x", 5, "y", 6));
      TableDef leftTop = parsedTable(table("name", "T1", "left", 5, "top", 6));
      TableDef position = parsedTable(table("name", "T1", "position", table("x", 5, "y", 6)));
      TableDef echoed = parsedTable(table(
         "name", "T1", "bounds", table("x", 5, "y", 6, "width", 100, "height", 40)));

      for(TableDef t : List.of(xy, leftTop, position, echoed)) {
         assertEquals(5, t.x());
         assertEquals(6, t.y());
      }
   }

   @Test
   void noPositionStaysNoPosition() {
      assertFalse(parsedTable(table("name", "T1")).hasPosition());
   }

   @Test
   void aBrokenPositionIsRefusedWithTheFieldNamed() {
      assertTrue(problems(definition(table("name", "T1", "x", 5)))
                    .contains("tables[T1].y: a position needs both x and y"));
      assertTrue(problems(definition(table("name", "T1", "x", -1, "y", 2)))
                    .contains("tables[T1].x: x must be a whole number of at least 0"));
      assertTrue(problems(definition(table("name", "T1", "x", 1.5, "y", 2)))
                    .contains("tables[T1].x"));
      assertTrue(problems(definition(table("name", "T1", "x", "5", "y", 2)))
                    .contains("tables[T1].x"));
      assertTrue(problems(definition(table("name", "T1", "x", 1, "left", 2, "y", 3)))
                    .contains("tables[T1].left: left disagrees"));
      assertTrue(problems(definition(table("name", "T1", "x", 1, "y", 3,
                                           "bounds", table("x", 9, "y", 3))))
                    .contains("tables[T1].bounds: bounds disagrees"));
      assertTrue(problems(definition(table("name", "T1", "bounds", table("z", 1))))
                    .contains("tables[T1].bounds.z: unknown key"));
      assertTrue(problems(definition(table("name", "T1", "width", 100)))
                    .contains("tables[T1].width: unknown key"));
   }

   // -- parsing Auto Layout and the viewport ---------------------------------------------------

   @Test
   void autoLayoutTakesTheNaturalValues() {
      Map<String, Object> on = definition(table("name", "T1"));
      on.put("autoLayout", true);
      assertFalse(WizPhysicalViewMapper.parse(on).autoLayout().colPriority());

      on.put("autoLayout", "Horizontal");
      assertTrue(WizPhysicalViewMapper.parse(on).autoLayout().colPriority());

      on.put("autoLayout", false);
      assertNull(WizPhysicalViewMapper.parse(on).autoLayout());

      on.remove("autoLayout");
      on.put("auto_layout", "vertical");
      assertFalse(WizPhysicalViewMapper.parse(on).autoLayout().colPriority());
   }

   @Test
   void aBrokenAutoLayoutOrViewportIsRefusedWithTheFieldNamed() {
      Map<String, Object> bad = definition(table("name", "T1"));
      bad.put("autoLayout", "diagonal");
      assertTrue(problems(bad).contains("definition.autoLayout: autoLayout must be true"));

      Map<String, Object> noLayout = definition(table("name", "T1"));
      noLayout.put("viewport", table("width", 800, "height", 600));
      assertTrue(problems(noLayout).contains("definition.viewport: viewport is the diagram size"));

      Map<String, Object> half = definition(table("name", "T1"));
      half.put("autoLayout", true);
      half.put("viewport", table("width", 800));
      assertTrue(problems(half).contains("definition.viewport: viewport needs both"));

      Map<String, Object> zero = definition(table("name", "T1"));
      zero.put("autoLayout", true);
      zero.put("viewport", table("width", 0, "height", 600));
      assertTrue(problems(zero).contains("definition.viewport.width"));

      Map<String, Object> good = definition(table("name", "T1"));
      good.put("autoLayout", true);
      good.put("viewport", table("width", 1200, "height", 700));
      assertEquals(new Viewport(1200, 700), WizPhysicalViewMapper.parse(good).viewport());
   }

   // -- the plan -------------------------------------------------------------------------------

   @Test
   void aMovedTableIsPlannedAndAnUnchangedOneIsNot() {
      View stored = view(placed("T1", 10, 20, false), placed("T2", 30, 40, false));

      Plan moved = WizPhysicalViewDiff.plan(
         stored, desired(null, null, wanted("T1", 99, 20), wanted("T2", 30, 40)), false);
      assertEquals(List.of("T1"), moved.moveTables.stream().map(TableDef::name).toList());
      assertFalse(moved.isEmpty());

      Plan same = WizPhysicalViewDiff.plan(
         stored, desired(null, null, wanted("T1", 10, 20), wanted("T2", 30, 40)), false);
      assertTrue(same.isEmpty());

      // a table without a position keeps the stored one
      Plan omitted = WizPhysicalViewDiff.plan(
         stored, desired(null, null, unplaced("T1"), unplaced("T2")), false);
      assertTrue(omitted.isEmpty());
   }

   @Test
   void aTableNeverPlacedTakesTheGivenPosition() {
      View stored = view(unplaced("T1"));
      Plan plan = WizPhysicalViewDiff.plan(
         stored, desired(null, null, wanted("T1", 0, 0)), false);

      assertEquals(1, plan.moveTables.size());
   }

   @Test
   void aNewTableWithAPositionIsAddedThenPlaced() {
      View stored = view(placed("T1", 10, 20, false));
      Plan plan = WizPhysicalViewDiff.plan(
         stored, desired(null, null, wanted("T1", 10, 20), wanted("T2", 500, 60)), false);

      assertEquals(List.of("T2"), plan.addPlain.stream().map(TableDef::name).toList());
      assertEquals(List.of("T2"), plan.moveTables.stream().map(TableDef::name).toList());
   }

   @Test
   void aPositionOfAnInheritedTableIsRefusedUnlessItIsTheSame() {
      View stored = view(placed("T1", 10, 20, true));

      DmFailure failure = assertThrows(DmFailure.class, () -> WizPhysicalViewDiff.plan(
         stored, desired(null, null, new TableDef("T1", Kind.PLAIN, null, null, null, null, null,
                                                  true, 77, 20, null, null)), false));
      assertEquals(WizDataModelWire.BASE_ELEMENT_READ_ONLY, failure.toBody().reason());

      Plan echoed = WizPhysicalViewDiff.plan(
         stored, desired(null, null, new TableDef("T1", Kind.PLAIN, null, null, null, null, null,
                                                  true, 10, 20, null, null)), false);
      assertTrue(echoed.isEmpty());
   }

   @Test
   void autoLayoutIsAPlanOfItsOwnAndRefusesExplicitPositions() {
      View stored = view(placed("T1", 10, 20, false));
      Plan layout = WizPhysicalViewDiff.plan(
         stored, desired(new AutoLayout(true), new Viewport(900, 500), unplaced("T1")), false);

      assertFalse(layout.isEmpty());
      assertTrue(layout.autoLayout.colPriority());
      assertEquals(new Viewport(900, 500), layout.viewport);
      assertTrue(layout.moveTables.isEmpty());

      // an echoed, unchanged position next to autoLayout is not a position to apply
      Plan echoed = WizPhysicalViewDiff.plan(
         stored, desired(new AutoLayout(false), null, wanted("T1", 10, 20)), false);
      assertTrue(echoed.moveTables.isEmpty());

      DmFailure both = assertThrows(DmFailure.class, () -> WizPhysicalViewDiff.plan(
         stored, desired(new AutoLayout(false), null, wanted("T1", 11, 20)), false));
      assertEquals("definition.autoLayout", both.toBody().errors().get(0).field());
   }

   @Test
   void theReadBackDoesNotRefuseAPositionTheServerAdjusted() {
      // the save lays tables out as the Portal's does: the stored position is reported by a
      // warning of the session, not refused here
      View stored = view(placed("T1", 10, 20, false));
      Desired submitted = desired(null, null, wanted("T1", 99, 20));

      assertNull(WizPhysicalViewDiff.mismatch(submitted.view(), submitted, stored));
   }
}
