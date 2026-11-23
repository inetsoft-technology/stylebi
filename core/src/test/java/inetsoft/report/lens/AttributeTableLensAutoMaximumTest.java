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
package inetsoft.report.lens;

import inetsoft.report.*;
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.report.filter.Formula;
import inetsoft.report.filter.SumFormula;
import inetsoft.report.painter.*;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82043: an auto maximum Bar / Bar +/- presenter in a table lens scales to the maximum of
 * the cells that share its data path, scanned lazily and once per lens.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AttributeTableLensAutoMaximumTest {
   private static final int SALES = 1;
   private static final int DELTA = 2;

   @Test
   void defaultOffDrawsAgainstConfiguredMaximum() {
      AttributeTableLens lens = new AttributeTableLens(new CountingTable(table()));
      lens.setPresenter(SALES, new BarPresenter());

      assertEquals(50, width(lens, 2, SALES));  // 50 of max 100
      assertEquals(100, width(lens, 3, SALES)); // 200 fills the cell
      assertEquals(10, width(lens, 1, SALES));
   }

   @Test
   void barScalesToColumnMaximum() {
      AttributeTableLens lens = new AttributeTableLens(new CountingTable(table()));
      lens.setPresenter(SALES, autoBar());

      assertEquals(5, width(lens, 1, SALES));   // 10 of 200
      assertEquals(25, width(lens, 2, SALES));  // 50 of 200
      assertEquals(100, width(lens, 3, SALES)); // 200 of 200
      assertEquals(12, width(lens, 4, SALES));  // 25 of 200
   }

   @Test
   void bar2UsesLargestAbsoluteValueOfColumn() {
      AttributeTableLens lens = new AttributeTableLens(new CountingTable(table()));
      Bar2Presenter bar = new Bar2Presenter();
      bar.setAutoMaximum(true);
      lens.setPresenter(DELTA, bar);

      // largest |value| is 40 (from -40), so 20 draws a quarter cell to the right
      assertArrayEquals(new int[] { 50, 0 }, widths2(lens, 1, DELTA)); // -40
      assertArrayEquals(new int[] { 0, 25 }, widths2(lens, 2, DELTA)); // 20
      assertArrayEquals(new int[] { 7, 0 }, widths2(lens, 4, DELTA));  // -5
   }

   @Test
   void scriptPresenterSharedByCellsIsNotChanged() {
      AttributeTableLens lens = new AttributeTableLens(new CountingTable(table()));
      BarPresenter shared = autoBar();
      lens.setPresenter(SALES, shared);

      PresenterPainter painter = (PresenterPainter) lens.getObject(2, SALES);
      assertNotSame(shared, painter.getPresenter());
      assertEquals(25, barWidth(painter));
      // the shared instance has no resolver, so it still uses its configured maximum
      assertEquals(50, barWidth(new PresenterPainter(50.0, shared)));
   }

   @Test
   void getObjectAloneDoesNotScanTheColumn() {
      CountingTable base = new CountingTable(bigTable(1000));
      AttributeTableLens lens = new AttributeTableLens(base);
      lens.setPresenter(SALES, autoBar());

      base.reads.set(0);
      Object obj = lens.getObject(5, SALES);
      assertInstanceOf(PresenterPainter.class, obj);
      assertEquals(1, base.reads.get(), "only the cell itself is read");

      base.reads.set(0);
      ((PresenterPainter) obj).paint(graphics(), 0, 0, 100, 20);
      assertEquals(1000, base.reads.get(), "painting scans the column once");

      base.reads.set(0);
      ((PresenterPainter) lens.getObject(7, SALES)).paint(graphics(), 0, 0, 100, 20);
      assertEquals(1, base.reads.get(), "a second cell reuses the scan");
   }

   @Test
   void concurrentPaintsScanOnce() throws Exception {
      int rows = 200;
      int threads = 8;
      CountingTable base = new CountingTable(bigTable(rows));
      AttributeTableLens lens = new AttributeTableLens(base);
      lens.setPresenter(SALES, autoBar());

      List<PresenterPainter> painters = new ArrayList<>();

      for(int i = 0; i < threads; i++) {
         painters.add((PresenterPainter) lens.getObject(i + 1, SALES));
      }

      // the scan stops at the last row until every painter is waiting
      CountDownLatch gate = new CountDownLatch(1);
      base.gateRow = rows;
      base.gate = gate;
      base.reads.set(0);
      List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
      List<Thread> workers = new ArrayList<>();

      for(PresenterPainter painter : painters) {
         Thread thread = new Thread(() -> {
            try {
               painter.paint(graphics(), 0, 0, 100, 20);
            }
            catch(Throwable ex) {
               errors.add(ex);
            }
         });
         workers.add(thread);
         thread.start();
      }

      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);

      while(!workers.stream().allMatch(t -> t.getState() == Thread.State.WAITING ||
         t.getState() == Thread.State.TIMED_WAITING))
      {
         assertTrue(System.nanoTime() < deadline, "painters did not all wait for the scan");
         Thread.onSpinWait();
      }

      gate.countDown();

      for(Thread thread : workers) {
         thread.join(TimeUnit.SECONDS.toMillis(20));
         assertFalse(thread.isAlive());
      }

      assertTrue(errors.isEmpty(), () -> "paint failed: " + errors);
      assertEquals(rows, base.reads.get(), "the column is scanned exactly once");
   }

   @Test
   void invalidateDropsTheCachedMaximum() {
      DefaultTableLens data = table();
      AttributeTableLens lens = new AttributeTableLens(data);
      lens.setPresenter(SALES, autoBar());

      assertEquals(25, width(lens, 2, SALES)); // 50 of 200

      data.setObject(3, SALES, 400.0);
      lens.invalidate();
      assertEquals(12, width(lens, 2, SALES)); // 50 of 400
   }

   @Test
   void cloneDoesNotShareTheCachedMaximum() {
      DefaultTableLens data = table();
      AttributeTableLens lens = new AttributeTableLens(data);
      lens.setPresenter(SALES, autoBar());
      assertEquals(25, width(lens, 2, SALES));

      AttributeTableLens copy = lens.clone();
      // change the data without an event, so only the clone's own scan can see it
      data.setObject(3, SALES, 400.0);
      assertEquals(12, width(copy, 2, SALES));
   }

   @Test
   void crosstabGroupsByDataPath() {
      DefaultTableLens data = new DefaultTableLens(new Object[][] {
         { "Region", "Year", "Sales", "Qty" },
         { "East", 2020, 10.0, 100.0 },
         { "East", 2021, 30.0, 300.0 },
         { "West", 2020, 20.0, 200.0 },
         { "West", 2021, 40.0, 400.0 },
      });
      CrossTabFilter crosstab = new CrossTabFilter(data, new int[] { 0 }, new int[] { 1 },
         new int[] { 2, 3 }, new Formula[] { new SumFormula(), new SumFormula() });
      crosstab.setSuppressRowGrandTotal(false);
      crosstab.setSuppressColumnGrandTotal(false);
      AttributeTableLens lens = new AttributeTableLens(crosstab);
      TableDataDescriptor desc = lens.getDescriptor();
      assertNotEquals(TableDataDescriptor.NORMAL_TABLE, desc.getType());

      Map<TableDataPath, Double> expected = new HashMap<>();
      Map<TableDataPath, List<int[]>> cells = new HashMap<>();

      for(int r = 0; lens.moreRows(r); r++) {
         for(int c = 0; c < lens.getColCount(); c++) {
            if(crosstab.getObject(r, c) instanceof Number num) {
               TableDataPath path = desc.getCellDataPath(r, c);
               expected.merge(path, num.doubleValue(), Math::max);
               cells.computeIfAbsent(path, k -> new ArrayList<>()).add(new int[] { r, c });
            }
         }
      }

      Set<Double> bodyMaxes = new HashSet<>();
      Set<Double> totalMaxes = new HashSet<>();

      for(Map.Entry<TableDataPath, List<int[]>> e : cells.entrySet()) {
         if(e.getKey().getType() == TableDataPath.SUMMARY) {
            bodyMaxes.add(expected.get(e.getKey()));
         }
         else if(e.getKey().getType() == TableDataPath.GRAND_TOTAL) {
            totalMaxes.add(expected.get(e.getKey()));
         }

         for(int[] cell : e.getValue()) {
            assertEquals(expected.get(e.getKey()), lens.getAutoMaximum(cell[0], cell[1], false),
                         "cell " + Arrays.toString(cell) + " " + e.getKey());
         }
      }

      // the body of each aggregate has its own scale, and totals do not inflate it
      assertTrue(bodyMaxes.containsAll(Set.of(40.0, 400.0)), "body maxes " + bodyMaxes);
      assertFalse(bodyMaxes.contains(100.0) || bodyMaxes.contains(1000.0),
                  "body maxes " + bodyMaxes);
      assertFalse(totalMaxes.isEmpty(), "no grand total cells found");
   }

   private static BarPresenter autoBar() {
      BarPresenter bar = new BarPresenter();
      bar.setAutoMaximum(true);
      return bar;
   }

   private static DefaultTableLens table() {
      return new DefaultTableLens(new Object[][] {
         { "Name", "Sales", "Delta" },
         { "a", 10.0, -40.0 },
         { "b", 50.0, 20.0 },
         { "c", 200.0, 10.0 },
         { "d", 25.0, -5.0 },
         { "e", Double.NaN, Double.POSITIVE_INFINITY },
         { "f", "text", null },
      });
   }

   private static DefaultTableLens bigTable(int rows) {
      Object[][] data = new Object[rows + 1][3];
      data[0] = new Object[] { "Name", "Sales", "Delta" };

      for(int i = 1; i <= rows; i++) {
         data[i] = new Object[] { "n" + i, (double) i, (double) -i };
      }

      return new DefaultTableLens(data);
   }

   private static int width(AttributeTableLens lens, int r, int c) {
      return barWidth((PresenterPainter) lens.getObject(r, c));
   }

   private static int[] widths2(AttributeTableLens lens, int r, int c) {
      return bar2Widths((PresenterPainter) lens.getObject(r, c));
   }

   private static boolean[] paintLine(Painter painter) {
      BufferedImage img = new BufferedImage(100, 20, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = img.createGraphics();

      try {
         g.setColor(Color.WHITE);
         g.fillRect(0, 0, 100, 20);
         g.setColor(Color.BLACK);
         painter.paint(g, 0, 0, 100, 20);
      }
      finally {
         g.dispose();
      }

      boolean[] filled = new boolean[100];

      for(int x = 0; x < 100; x++) {
         filled[x] = (img.getRGB(x, 10) & 0xFFFFFF) != 0xFFFFFF;
      }

      return filled;
   }

   private static int barWidth(Painter painter) {
      int cnt = 0;

      for(boolean f : paintLine(painter)) {
         cnt += f ? 1 : 0;
      }

      return cnt;
   }

   private static int[] bar2Widths(Painter painter) {
      boolean[] filled = paintLine(painter);
      int[] widths = new int[2];

      for(int x = 0; x < filled.length; x++) {
         if(filled[x]) {
            widths[x < filled.length / 2 ? 0 : 1]++;
         }
      }

      return widths;
   }

   private static Graphics2D graphics() {
      return new BufferedImage(100, 20, BufferedImage.TYPE_INT_RGB).createGraphics();
   }

   /**
    * Counts reads of the Sales column and can hold the scan at one row.
    */
   private static final class CountingTable extends AttributeTableLens {
      CountingTable(TableLens table) {
         super(table);
      }

      @Override
      public Object getObject(int r, int c) {
         if(c == SALES && r > 0) {
            reads.incrementAndGet();

            CountDownLatch gate = this.gate;

            if(gate != null && r == gateRow) {
               try {
                  assertTrue(gate.await(20, TimeUnit.SECONDS));
               }
               catch(InterruptedException ex) {
                  Thread.currentThread().interrupt();
               }
            }
         }

         return table.getObject(r, c);
      }

      final AtomicInteger reads = new AtomicInteger();
      volatile CountDownLatch gate;
      volatile int gateRow = -1;
   }
}
