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

import inetsoft.uql.erm.XPartition;
import inetsoft.web.wiz.controller.WizDataModelWire.Failure;
import inetsoft.web.wiz.controller.WizPhysicalViewWire.*;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The position of a physical view's tables, Auto Layout and the viewport, through the bridge
 * against the real registry, runtime service and manager service (bug #82173).
 */
class WizPhysicalViewLayoutTest extends WizPhysicalViewTestBase {
   private static Map<String, Object> at(Map<String, Object> table, int x, int y) {
      table.put("x", x);
      table.put("y", y);
      return table;
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Object> boundsOf(Map<String, Object> definition, String name) {
      for(Object table : (List<?>) definition.get("tables")) {
         Map<String, Object> t = (Map<String, Object>) table;

         if(name.equals(t.get("name"))) {
            return (Map<String, Object>) t.get("bounds");
         }
      }

      fail("no table " + name + " in " + definition);
      return null;
   }

   /** An update that names tables only: the joins list is absent, so the joins stay. */
   private static Map<String, Object> tablesOnly(Map<String, Object> definition) {
      definition.remove("joins");
      return definition;
   }

   private static Rectangle stored(XPartition view, String table) {
      return view.getBounds(table);
   }

   @Test
   void tablesCreatedWithPositionsReadBackAtThem() throws Exception {
      WriteResponse created = createView(definition(
         "v", tables(at(plain("T1"), 40, 50), at(plain("T2"), 300, 60)),
         join("T2", "T1_ID", "T1", "ID")));

      assertEquals(40, boundsOf(created.definition(), "T1").get("x"));
      assertEquals(50, boundsOf(created.definition(), "T1").get("y"));
      assertEquals(300, boundsOf(created.definition(), "T2").get("x"));
      assertEquals(40, stored(storedView("v"), "T1").x);
      assertEquals(300, stored(storedView("v"), "T2").x);

      GetResponse got = getView("v");
      assertEquals(40, boundsOf(got.definition(), "T1").get("x"));
      assertEquals(300, boundsOf(got.definition(), "T2").get("x"));
      assertTrue(runtimes.isEmpty());
   }

   @Test
   void anUpdateMovesOnlyTheTablesItNamesAWorkingPosition() throws Exception {
      createView(definition("v", tables(at(plain("T1"), 40, 50), at(plain("T2"), 300, 60)),
                            join("T2", "T1_ID", "T1", "ID")));
      int widthBefore = stored(storedView("v"), "T1").width;

      Object result = updateView("v", tablesOnly(definition("v", tables(at(plain("T1"), 700, 10),
                                                                    plain("T2")))), null);
      assertTrue(((WriteResponse) result).ok(), String.valueOf(result));

      Rectangle t1 = stored(storedView("v"), "T1");
      assertEquals(700, t1.x);
      assertEquals(10, t1.y);
      assertEquals(widthBefore, t1.width, "the table keeps its size");
      assertEquals(300, stored(storedView("v"), "T2").x, "an omitted position is left alone");
      assertTrue(runtimes.isEmpty());
   }

   @Test
   void aGetResultFedBackChangesNothingAndOpensNoRuntime() throws Exception {
      createView(definition("v", tables(at(plain("T1"), 40, 50), plain("T2")),
                            join("T2", "T1_ID", "T1", "ID")));
      GetResponse got = getView("v");
      int puts = runtimePuts.get();

      WriteResponse result = ok(updateView("v", got.definition(), null));

      assertTrue(result.ok());
      assertEquals(puts, runtimePuts.get(), "the echoed bounds are the stored ones: no edit");
      assertEquals(40, stored(storedView("v"), "T1").x);
   }

   @Test
   void movingATableKeepsTheRevisionOfAnEarlierRead() throws Exception {
      createView(definition("v", tables(at(plain("T1"), 40, 50), plain("T2")),
                            join("T2", "T1_ID", "T1", "ID")));
      String revision = getView("v").revision();

      ok(updateView("v", tablesOnly(definition("v", tables(at(plain("T1"), 500, 500), plain("T2")))),
                    null));

      assertEquals(revision, getView("v").revision(), "a move is not a structural change");
      Object stale = physicalController.update(
         new WriteRequest(ds, "v", null,
                          tablesOnly(definition("v", tables(at(plain("T1"), 1, 1), plain("T2")))),
                          null, revision), bob);
      assertTrue(stale instanceof WriteResponse, String.valueOf(stale));
   }

   @Test
   void aDryRunMovesNothing() throws Exception {
      createView(definition("v", tables(at(plain("T1"), 40, 50), plain("T2")),
                            join("T2", "T1_ID", "T1", "ID")));

      ok(updateView("v", tablesOnly(definition("v", tables(at(plain("T1"), 999, 999), plain("T2")))),
                    true));

      assertEquals(40, stored(storedView("v"), "T1").x);
      assertTrue(runtimes.isEmpty());
   }

   @Test
   void aNewTableInAnUpdateIsPlacedWhereAsked() throws Exception {
      createView(definition("v", tables(at(plain("T1"), 40, 50))));

      ok(updateView("v", definition("v", tables(plain("T1"), at(plain("T2"), 640, 480)),
                                    join("T2", "T1_ID", "T1", "ID")), null));

      assertEquals(640, stored(storedView("v"), "T2").x);
      assertEquals(480, stored(storedView("v"), "T2").y);
      assertEquals(40, stored(storedView("v"), "T1").x);
   }

   @Test
   void aPositionOnAnAliasAndAnInlineViewIsSetToo() throws Exception {
      createView(definition("v", tables(plain("T1"), alias("A1", "T1"), inline("V1", "select 1")),
                            (Map<String, Object>[]) new Map[0]));

      ok(updateView("v", tablesOnly(definition("v", tables(
         plain("T1"), at(alias("A1", "T1"), 210, 220), at(inline("V1", "select 1"), 410, 420)))),
                    null));

      assertEquals(210, stored(storedView("v"), "A1").x);
      assertEquals(420, stored(storedView("v"), "V1").y);
   }

   @Test
   void aBrokenPositionIsRefusedAndNothingIsOpened() throws Exception {
      createView(definition("v", tables(plain("T1"))));
      int puts = runtimePuts.get();

      Map<String, Object> half = plain("T1");
      half.put("x", 5);
      Failure failure = (Failure) updateView("v", tablesOnly(definition("v", tables(half))), null);

      assertEquals(WizDataModelWire.INVALID, failure.reason());
      assertEquals("tables[T1].y", failure.field());
      assertEquals(puts, runtimePuts.get());
   }

   @Test
   void aPositionOnAnInheritedTableIsRefusedAndOneOnItsOwnTableIsNot() throws Exception {
      createView(definition("base", tables(at(plain("T1"), 10, 10), plain("T2")),
                            join("T2", "T1_ID", "T1", "ID")));
      Map<String, Object> extended = definition(
         "ext", tables(plain("T3")), join("T3", "T2_CODE", "T2", "CODE"));
      extended.put("parent", "base");
      ok(createViewRaw(extended, null));
      int puts = runtimePuts.get();

      // the effective bounds of an inherited table are the base view's
      Map<String, Object> got = ((GetResponse) physicalController.get(
         new GetRequest(ds, "ext", "base"), bob)).definition();
      assertEquals(10, boundsOf(got, "T1").get("x"));

      Failure refused = (Failure) physicalController.update(
         new WriteRequest(ds, "ext", "base", tablesOnly(definition(
            "ext", tables(at(plain("T1"), 999, 999), plain("T2"), plain("T3")))), null), bob);
      assertEquals(WizDataModelWire.BASE_ELEMENT_READ_ONLY, refused.reason());
      assertEquals(puts, runtimePuts.get());

      Object own = physicalController.update(
         new WriteRequest(ds, "ext", "base", tablesOnly(definition(
            "ext", tables(plain("T1"), plain("T2"), at(plain("T3"), 321, 123)))), null), bob);
      assertTrue(own instanceof WriteResponse, String.valueOf(own));
      assertEquals(321, stored(storedView("base").getPartition("ext"), "T3").x);
      assertEquals(10, stored(storedView("base"), "T1").x, "the base view is untouched");
   }

   @Test
   void autoLayoutArrangesTheTablesAndRefusesExplicitPositions() throws Exception {
      ok(createViewRaw(definition("v", tables(at(plain("T1"), 9000, 9000),
                                              at(plain("T2"), 9000, 9100)),
                                  join("T2", "T1_ID", "T1", "ID")), null));

      Map<String, Object> both = tablesOnly(definition("v", tables(at(plain("T1"), 1, 1),
                                                                plain("T2"))));
      both.put("autoLayout", true);
      Failure refused = (Failure) updateView("v", both, null);
      assertEquals(WizDataModelWire.INVALID, refused.reason());
      assertEquals("definition.autoLayout", refused.field());
      assertEquals(9000, stored(storedView("v"), "T1").x);

      Map<String, Object> layout = new LinkedHashMap<>();
      layout.put("autoLayout", "horizontal");
      Map<String, Object> viewport = new LinkedHashMap<>();
      viewport.put("width", 1600);
      viewport.put("height", 900);
      layout.put("viewport", viewport);
      WriteResponse result = ok(updateView("v", layout, null));

      assertTrue(result.ok());
      assertNotEquals(9000, stored(storedView("v"), "T1").x, "the layout moved the table");
      assertTrue(runtimes.isEmpty());
   }

   @Test
   void aTableTheSaveLaidOutElsewhereIsReportedNotRefused() throws Exception {
      WriteResponse created = ok(createViewRaw(definition(
         "v", tables(at(plain("T1"), 20, 20), at(plain("T2"), 20, 30)),
         join("T2", "T1_ID", "T1", "ID")), null));

      Rectangle t2 = stored(storedView("v"), "T2");
      Map<String, Object> bounds = boundsOf(created.definition(), "T2");
      assertEquals(t2.y, bounds.get("y"), "the answer is the stored position");

      if(t2.y != 30) {
         assertTrue(created.warnings().stream().anyMatch(
            w -> "POSITION_ADJUSTED".equals(w.code()) && "T2".equals(w.table1())),
                    String.valueOf(created.warnings()));
      }
   }
}
