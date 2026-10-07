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
package inetsoft.web.portal.service.database;

import inetsoft.uql.erm.XPartition;
import inetsoft.web.portal.controller.database.RuntimePartitionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Portal-first round trips of the physical view layout (Bug #77853): a layout shown in the
 * portal is saved ({@link PhysicalGraphService#unfoldColumnHeight}) and opened again
 * ({@link PhysicalGraphService#shrinkColumnHeight}) the way PhysicalModelManagerService does
 * it, and must come back where it was shown.
 */
@Tag("core")
class PhysicalGraphServiceRoundTripTest {
   private final PhysicalGraphService service = new PhysicalGraphService();

   // the tables of the report, in partition units (portal px / 1.5)
   private static Map<String, Rectangle> reportLayout() {
      Map<String, Rectangle> layout = new TreeMap<>();
      layout.put("CUSTOMERS", new Rectangle(41, 10, 101, 130));
      layout.put("ORDERS", new Rectangle(54, 66, 83, 102));
      layout.put("ORDER_DETAILS", new Rectangle(30, 122, 125, 60));
      layout.put("PRODUCTS", new Rectangle(30, 178, 95, 130));
      return layout;
   }

   @Test
   void tableDroppedBelowOtherColumnReopensWhereDropped() {
      Map<String, Rectangle> portal = reportLayout();
      portal.put("PRODUCTS", new Rectangle(180, 253, 95, 130));

      Saved saved = save(portal, null);

      assertEquals(portal, saved.opened);
      assertStable(portal, saved, 5);
   }

   @Test
   void twoTablesDroppedIntoEmptySpaceReopenWhereDropped() {
      Map<String, Rectangle> portal = reportLayout();
      portal.put("PRODUCTS", new Rectangle(180, 150, 95, 130));
      portal.put("SHIPPERS", new Rectangle(300, 100, 80, 60));

      Saved saved = save(portal, null);

      assertEquals(portal, saved.opened);
      assertStable(portal, saved, 5);
   }

   @Test
   void tableDroppedBetweenRowsOfOtherColumnStaysClose() {
      Map<String, Rectangle> portal = reportLayout();
      portal.put("PRODUCTS", new Rectangle(180, 90, 95, 130));

      Saved saved = save(portal, null);

      // no stored y opens exactly at 90 without changing the order to the other column
      assertTrue(maxDy(portal, saved.opened) <= DEVIATION_LIMIT, () -> describe(portal, saved));
      assertStable(saved.opened, saved, 5);
   }

   @Test
   void horizontalLayoutWithDroppedTableReopensWhereDropped() {
      Map<String, Rectangle> portal = new TreeMap<>();
      portal.put("CUSTOMERS", new Rectangle(20, 10, 101, 130));
      portal.put("ORDERS", new Rectangle(20, 60, 83, 102));
      portal.put("ORDER_DETAILS", new Rectangle(260, 10, 125, 60));
      portal.put("PRODUCTS", new Rectangle(410, 120, 95, 130));

      Saved saved = save(portal, null);

      assertEquals(portal, saved.opened);
      assertStable(portal, saved, 5);
   }

   @Test
   void layoutsWithoutDragStillRoundTrip() {
      Map<String, Rectangle> vertical = reportLayout();
      Map<String, Rectangle> horizontal = new TreeMap<>();
      horizontal.put("CUSTOMERS", new Rectangle(20, 10, 101, 130));
      horizontal.put("ORDERS", new Rectangle(140, 10, 83, 102));
      horizontal.put("ORDER_DETAILS", new Rectangle(260, 10, 125, 60));
      horizontal.put("PRODUCTS", new Rectangle(410, 10, 95, 130));
      Map<String, Rectangle> top = reportLayout();
      top.put("PRODUCTS", new Rectangle(180, 5, 95, 130));

      for(Map<String, Rectangle> portal : List.of(vertical, horizontal, top)) {
         assertEquals(portal, save(portal, null).opened);
      }
   }

   @Test
   void storedMiddleTableStillOpensAboveTheNextTable() {
      // stored by an older build: PRODUCTS above ORDER_DETAILS, which moves up on open
      Map<String, Rectangle> stored = reportLayout();
      stored.put("ORDERS", new Rectangle(54, 170, 83, 102));
      stored.put("ORDER_DETAILS", new Rectangle(30, 302, 125, 60));
      stored.put("PRODUCTS", new Rectangle(180, 253, 95, 130));

      assertEquals(112, open(stored).get("PRODUCTS").y);
   }

   @Test
   void storedBottomTableFollowsTheLowestTableAboveIt() {
      // nothing is below PRODUCTS, so it moves up as far as ORDERS (the lowest table above it)
      Map<String, Rectangle> stored = reportLayout();
      stored.put("ORDERS", new Rectangle(54, 170, 83, 102));
      stored.put("ORDER_DETAILS", new Rectangle(330, 40, 125, 60));
      stored.put("PRODUCTS", new Rectangle(180, 253, 95, 130));

      Map<String, Rectangle> opened = open(stored);

      assertEquals(66, opened.get("ORDERS").y);
      assertEquals(149, opened.get("PRODUCTS").y);
   }

   /**
    * Seeded random layouts: never further from the shown layout than before the fix
    * ({@link #HEAD_BASELINE}), x never changes, no new overlap, stable when saved again.
    */
   @Test
   void randomLayoutsAreNeverWorseThanBefore() {
      String[] baseline = HEAD_BASELINE.split(";");

      for(int g = 0; g < GENERATORS.size(); g++) {
         Random random = new Random(SEEDS[g]);
         String[] head = baseline[g].split(",");

         for(int n = 0; n < RANDOM_LAYOUTS; n++) {
            Map<String, Rectangle> portal = GENERATORS.get(g).apply(random);
            Saved saved = save(portal, null);
            int headValue = Integer.parseInt(head[n]);
            int headMaxDy = headValue / 2;
            boolean headOverlaps = headValue % 2 == 1;
            String label = "generator " + g + " layout " + n + ": ";

            assertTrue(maxDy(portal, saved.opened) <= headMaxDy,
               () -> label + "worse than before the fix (" + headMaxDy + ") " + describe(portal, saved));

            if(!overlaps(portal) && !headOverlaps) {
               assertFalse(overlaps(saved.opened), () -> label + "new overlap " + describe(portal, saved));
            }

            assertStable(saved.opened, saved, 2);
         }
      }
   }

   /**
    * Large view: 60 columns of 6 tables, 20 tables dragged between rows, and one table dropped
    * below everything. The dropped table must open where it was dropped even when the search
    * stops at its time limit, and no table may open further away than before the fix (61).
    */
   @Test
   void largeViewKeepsTableDroppedBelowEverything() {
      Random random = new Random(102);
      int columns = 60;
      Map<String, Rectangle> portal = new TreeMap<>();
      int t = 0;

      for(int c = 0; c < columns; c++) {
         for(int d = 0; d < 6; d++) {
            portal.put(String.format("C%04d", t++),
                       new Rectangle(10 + c * 170, 10 + d * 40, 100, height(random)));
         }
      }

      for(int c = 0; c < columns; c += 3) {
         String name = String.format("C%04d", c * 6 + 3);
         Rectangle bounds = portal.get(name);
         portal.put(name, new Rectangle(bounds.x + 110, 110, 50, bounds.height));
      }

      portal.put("ZBOTTOM", new Rectangle(10 + columns * 170 + 20, 1500, 100, 130));

      Saved saved = save(portal, null);

      assertEquals(1500, saved.opened.get("ZBOTTOM").y);
      assertTrue(maxDy(portal, saved.opened) <= 61, () -> describe(portal, saved));
      assertStable(saved.opened, saved, 1);
   }

   private void assertStable(Map<String, Rectangle> expected, Saved saved, int cycles) {
      for(int i = 0; i < cycles; i++) {
         saved = save(saved.opened, saved.stored);
         assertEquals(expected, saved.opened, "changed in save/open cycle " + (i + 2));
      }
   }

   private record Saved(Map<String, Rectangle> stored, Map<String, Rectangle> opened) {
   }

   /**
    * Save as PhysicalModelManagerService.updateAndSaveModel does: unfold the runtime partition
    * against the stored layout, then shrink a deep clone of it again for the portal.
    */
   private Saved save(Map<String, Rectangle> portal, Map<String, Rectangle> original) {
      RuntimePartitionService.RuntimeXPartition runtime = runtime(portal);
      service.unfoldColumnHeight(runtime, original == null ? null : partition(original));
      Map<String, Rectangle> stored = read(runtime.getPartition(), portal.keySet());
      runtime.setPartition((XPartition) runtime.getPartition().deepClone(true));
      service.shrinkColumnHeight(runtime);
      return new Saved(stored, read(runtime.getPartition(), portal.keySet()));
   }

   private Map<String, Rectangle> open(Map<String, Rectangle> stored) {
      RuntimePartitionService.RuntimeXPartition runtime = runtime(stored);
      runtime.setPartition((XPartition) runtime.getPartition().deepClone(true));
      service.shrinkColumnHeight(runtime);
      return read(runtime.getPartition(), stored.keySet());
   }

   private static XPartition partition(Map<String, Rectangle> layout) {
      XPartition partition = new XPartition();
      layout.forEach((name, bounds) -> partition.addTable(name, new Rectangle(bounds)));
      return partition;
   }

   private static RuntimePartitionService.RuntimeXPartition runtime(Map<String, Rectangle> layout) {
      return new RuntimePartitionService.RuntimeXPartition(
         partition(layout), UUID.randomUUID().toString(), "test-ignore");
   }

   private static Map<String, Rectangle> read(XPartition partition, Collection<String> names) {
      Map<String, Rectangle> result = new TreeMap<>();
      names.forEach(name -> result.put(name, new Rectangle(partition.getBounds(name))));
      return result;
   }

   /**
    * Largest y difference; x must not change.
    */
   static int maxDy(Map<String, Rectangle> expected, Map<String, Rectangle> actual) {
      int max = 0;

      for(String name : expected.keySet()) {
         assertEquals(expected.get(name).x, actual.get(name).x, "x changed for " + name);
         max = Math.max(max, Math.abs(expected.get(name).y - actual.get(name).y));
      }

      return max;
   }

   /**
    * Whether two tables overlap at the portal node height, or a table is at y <= 0.
    */
   static boolean overlaps(Map<String, Rectangle> layout) {
      List<Rectangle> nodes = new ArrayList<>();
      layout.values().forEach(b -> nodes.add(new Rectangle(b.x, b.y, b.width, 26)));

      for(int i = 0; i < nodes.size(); i++) {
         if(nodes.get(i).y <= 0) {
            return true;
         }

         for(int j = i + 1; j < nodes.size(); j++) {
            if(nodes.get(i).intersects(nodes.get(j))) {
               return true;
            }
         }
      }

      return false;
   }

   private static String describe(Map<String, Rectangle> portal, Saved saved) {
      return "\n  shown=" + portal + "\n  opened=" + saved.opened;
   }

   private static int height(Random random) {
      return (2 + random.nextInt(11)) * 14 + 18;
   }

   /**
    * Vertical columns starting at the top as shown in the portal (each table 26 high, so it
    * sits right under the one above it), then 1-3 tables dragged.
    */
   static Map<String, Rectangle> columnsWithDrags(Random random) {
      Map<String, Rectangle> portal = new TreeMap<>();
      int columns = 2 + random.nextInt(4);
      int t = 0;

      for(int c = 0; c < columns; c++) {
         int y = 10;
         int x = 10 + c * 170 + random.nextInt(10);
         int count = 1 + random.nextInt(4);

         for(int i = 0; i < count; i++) {
            int h = height(random);
            portal.put(String.format("T%03d", t++), new Rectangle(x, y, 60 + random.nextInt(90), h));
            y += 26 + 20 + random.nextInt(30);
         }
      }

      return drag(random, portal);
   }

   /**
    * Column-priority auto layout (columns centered vertically) as shown in the portal.
    */
   static Map<String, Rectangle> autoColumns(Random random) {
      Map<String, Rectangle> layout = new TreeMap<>();
      int columns = 1 + random.nextInt(5);
      int[] counts = new int[columns];
      int max = 0;

      for(int c = 0; c < columns; c++) {
         counts[c] = 1 + random.nextInt(6);
         max = Math.max(max, counts[c]);
      }

      int pitch = new int[] { 37, 40, 56 }[random.nextInt(3)];
      int x = 10;
      int t = 0;

      for(int c = 0; c < columns; c++) {
         int y = 10 + (max - counts[c]) * pitch / 2;
         int columnWidth = 0;

         for(int i = 0; i < counts[c]; i++) {
            int w = 60 + random.nextInt(90);
            columnWidth = Math.max(columnWidth, w);
            layout.put(String.format("C%03d", t++), new Rectangle(x, y, w, height(random)));
            y += pitch;
         }

         x += columnWidth + 30 + random.nextInt(30);
      }

      return layout;
   }

   /**
    * Row-priority auto layout (rows centered horizontally) as shown in the portal.
    */
   static Map<String, Rectangle> autoRows(Random random) {
      Map<String, Rectangle> layout = new TreeMap<>();
      int rows = 1 + random.nextInt(5);
      List<int[]> widths = new ArrayList<>();
      int maxWidth = 0;

      for(int r = 0; r < rows; r++) {
         int[] row = new int[1 + random.nextInt(5)];
         int total = 0;

         for(int i = 0; i < row.length; i++) {
            row[i] = 60 + random.nextInt(90);
            total += row[i] + 30;
         }

         widths.add(row);
         maxWidth = Math.max(maxWidth, total);
      }

      int pitch = new int[] { 37, 40, 56 }[random.nextInt(3)];
      int t = 0;

      for(int r = 0; r < rows; r++) {
         int total = Arrays.stream(widths.get(r)).map(w -> w + 30).sum();
         int left = (maxWidth - total) / 2 + 20;

         for(int w : widths.get(r)) {
            layout.put(String.format("R%03d", t++),
                       new Rectangle(left, 10 + r * pitch, w, height(random)));
            left += w + 30;
         }
      }

      return layout;
   }

   /**
    * Drag 1-3 tables to random free places.
    */
   static Map<String, Rectangle> drag(Random random, Map<String, Rectangle> portal) {
      Map<String, Rectangle> layout = new TreeMap<>();
      portal.forEach((name, bounds) -> layout.put(name, new Rectangle(bounds)));
      List<String> names = new ArrayList<>(layout.keySet());
      int drags = 1 + random.nextInt(3);

      for(int d = 0; d < drags; d++) {
         String name = names.get(random.nextInt(names.size()));
         Rectangle bounds = layout.get(name);

         for(int tries = 0; tries < 200; tries++) {
            Rectangle candidate = new Rectangle(random.nextInt(800), 5 + random.nextInt(350),
                                                bounds.width, bounds.height);
            Rectangle area = new Rectangle(candidate.x - 6, candidate.y - 6,
                                           candidate.width + 12, 26 + 12);
            boolean free = layout.entrySet().stream()
               .filter(e -> !e.getKey().equals(name))
               .noneMatch(e -> area.intersects(
                  new Rectangle(e.getValue().x, e.getValue().y, e.getValue().width, 26)));

            if(free) {
               layout.put(name, candidate);
               break;
            }
         }
      }

      return layout;
   }

   static final List<Function<Random, Map<String, Rectangle>>> GENERATORS = List.of(
      PhysicalGraphServiceRoundTripTest::columnsWithDrags,
      PhysicalGraphServiceRoundTripTest::autoColumns,
      PhysicalGraphServiceRoundTripTest::autoRows,
      random -> drag(random, autoColumns(random)),
      random -> drag(random, autoRows(random)));
   static final long[] SEEDS = { 77853, 1, 2, 3, 4 };
   static final int RANDOM_LAYOUTS = 100;
   private static final int DEVIATION_LIMIT = 5;

   /**
    * Per generator (";"), per layout (","): 2 * the largest y difference after a save and
    * open with PhysicalGraphService before the fix (f84a84a674), + 1 if that result overlaps.
    */
   static final String HEAD_BASELINE =
      "160,0,0,0,0,0,0,0,0,0,0,158,0,0,0,80,438,0,0,0,38,36,0,0,188,0,0,292,349,0,28," +
      "140,16,0,0,26,0,0,134,168,0,0,416,16,78,0,0,114,0,190,200,0,0,0,0,0,0,0,220,0," +
      "70,0,66,0,96,40,0,0,0,0,0,0,270,0,314,0,196,0,24,0,0,0,328,0,0,612,246,250,0,0," +
      "0,50,0,0,42,0,0,332,0,28;" +
      "0,0,0,0,112,0,148,58,48,0,52,170,116,86,0,0,0,86,0,92,0,0,0,0,0,56,118,56,40,0," +
      "86,0,192,122,148,0,82,0,112,0,0,0,92,0,150,0,0,0,0,0,0,0,0,0,176,0,58,0,0,116,0," +
      "72,0,46,88,124,0,136,0,128,0,44,0,0,88,0,0,0,112,0,0,124,0,64,0,0,0,134,0,0,0,0," +
      "0,0,0,60,0,118,140,0;" +
      "169,0,102,197,0,197,57,85,56,0,0,225,376,141,0,0,120,0,0,85,265,0,84,169,125,0," +
      "143,28,0,281,196,0,209,168,225,96,28,0,281,169,169,176,161,0,113,0,140,0,0,225," +
      "209,84,0,141,224,197,0,0,56,0,140,196,84,141,0,169,0,96,168,0,169,0,29,0,0,56," +
      "224,140,169,197,197,97,0,253,321,349,253,85,0,169,281,28,57,28,56,252,0,309,0,0;" +
      "52,236,116,0,94,388,54,0,202,0,0,0,0,0,500,52,0,0,330,0,0,713,254,0,264,146,0," +
      "145,106,0,0,292,0,172,86,0,168,0,212,102,0,0,0,94,0,0,209,0,228,0,0,486,192,288," +
      "209,178,270,96,110,50,0,0,62,114,168,202,78,0,0,168,180,70,0,0,0,262,228,0,0,0," +
      "0,168,0,292,150,0,0,125,354,0,200,0,210,52,308,0,0,82,0,252;" +
      "84,349,98,140,224,94,181,349,152,153,477,141,314,88,153,190,0,0,0,309,220,0,0," +
      "116,348,150,0,0,0,0,489,0,224,125,0,225,84,225,84,0,42,69,16,126,601,0,164,29,0," +
      "113,364,0,0,256,57,169,153,293,0,0,237,28,214,113,28,0,0,192,84,276,180,0,86,0," +
      "140,113,208,113,0,141,151,0,225,0,110,377,84,28,264,112,117,0,141,0,137,113,0," +
      "28,0,112";
}
