/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.filter;

import inetsoft.report.TableLens;
import inetsoft.report.lens.*;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A filter keeps receiving its base table's change events after a gc, and a base table does not
 * keep a dropped filter alive (bug #77398). Each test of event delivery runs a gc between building
 * the chain and changing the base: without it, the test would pass even when the listener is lost.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class TableChangeListenerLifetimeTest {
   /**
    * A crosstab over a base table shows the base change made after a gc.
    */
   @Test
   public void crosstabFollowsBaseChangeAfterGc() throws Exception {
      DefaultTableLens base = new DefaultTableLens(data());
      CrossTabFilter filter = crosstab(base);
      List<List<Object>> before = cells(filter);

      gc();
      base.setObject(1, VALUE_COL, CHANGED_VALUE);

      assertEquals(cells(crosstab(new DefaultTableLens(changedData()))), cells(filter),
                   "the crosstab still shows the value before the base change " + before);
   }

   /**
    * A base change reaches a crosstab through a sort filter between them, after a gc.
    */
   @Test
   public void cascadeFollowsBaseChangeAfterGc() throws Exception {
      DefaultTableLens base = new DefaultTableLens(data());
      SortFilter sort = sort(base);
      CrossTabFilter filter = crosstab(sort);
      cells(filter);
      assertEquals(5, firstValue(sort));

      gc();
      base.setObject(1, VALUE_COL, CHANGED_VALUE);

      assertEquals(CHANGED_VALUE, firstValue(sort), "the sort order is stale");
      assertEquals(cells(crosstab(sort(new DefaultTableLens(changedData())))), cells(filter),
                   "the crosstab over the sort is stale");
   }

   /**
    * A filter that is no longer used is collected although its base still has its listener,
    * and the base drops the listener when it fires the next event.
    */
   @Test
   public void droppedFilterIsCollectedAndPruned() throws Exception {
      DefaultTableLens base = new DefaultTableLens(data());
      WeakReference<SortFilter> ref = droppedSort(base);

      for(int i = 0; i < MAX_GC_ROUNDS && ref.get() != null; i++) {
         gc();
      }

      assertNull(ref.get(), "the base table keeps the dropped filter alive");
      base.setObject(1, VALUE_COL, CHANGED_VALUE);
      assertEquals(0, listenerCount(base), "the listener of the collected filter is kept");
   }

   /**
    * A filter registers once on its base, however many times it is set on it.
    */
   @Test
   public void sameBaseRegistersOneListener() throws Exception {
      DefaultTableLens base = new DefaultTableLens(data());
      SortFilter sort = sort(base);
      sort.setTable(base);
      assertEquals(1, listenerCount(base), "setTable() twice on the same base");

      DefaultTableLens both = new DefaultTableLens(data());
      CompositeTableLens composite = new CompositeTableLens(both, both);
      assertEquals(1, listenerCount(both), "the same base on both sides");
      assertNotNull(composite);
   }

   /**
    * Removing a filter's listener from the base stops its events, even when the listener that
    * is removed is not the instance the filter registered (as FormulaTableLens does while its
    * scripts run), and adding it again restores them.
    */
   @Test
   public void removeChangeListenerSuppressesEvents() throws Exception {
      DefaultTableLens base = new DefaultTableLens(data());
      SortFilter sort = sort(base);
      assertEquals(5, firstValue(sort));

      DefaultTableChangeListener listener = new DefaultTableChangeListener(sort);
      base.removeChangeListener(listener);
      assertEquals(0, listenerCount(base), "the filter's listener is still registered");
      base.setObject(1, VALUE_COL, CHANGED_VALUE);
      assertEquals(5, firstValue(sort), "the removed listener still invalidated the filter");

      base.addChangeListener(listener);
      gc();
      base.setObject(1, VALUE_COL, CHANGED_VALUE - 1);
      assertEquals(CHANGED_VALUE - 1, firstValue(sort), "the listener added again got no event");
   }

   /**
    * A listener serialized with its filter still invalidates that filter after it is read back,
    * and a listener read back without its filter holds that filter only weakly.
    */
   @Test
   public void listenerKeepsItsFilterAcrossSerialization() throws Exception {
      SortFilter sort = sort(new DefaultTableLens(data()));
      assertEquals(5, firstValue(sort));
      Object[] copy = (Object[]) roundTrip(new Object[] { new DefaultTableChangeListener(sort), sort });
      DefaultTableChangeListener listener = (DefaultTableChangeListener) copy[0];
      SortFilter sortCopy = (SortFilter) copy[1];

      assertSame(sortCopy, listener.getTarget(), "the listener lost its filter after it was read");

      // stop the base's own events, so only the deserialized listener can invalidate the filter
      DefaultTableLens baseCopy = (DefaultTableLens) sortCopy.getTable();
      assertEquals(5, firstValue(sortCopy));
      baseCopy.removeChangeListener(new DefaultTableChangeListener(sortCopy));
      baseCopy.setObject(1, VALUE_COL, CHANGED_VALUE);
      assertEquals(5, firstValue(sortCopy));
      listener.tableChanged(null);
      assertEquals(CHANGED_VALUE, firstValue(sortCopy), "the deserialized listener did not invalidate");

      // the listener stays reachable through the gc, so only a weak hold on its filter lets the
      // filter be collected
      DefaultTableChangeListener alone =
         (DefaultTableChangeListener) roundTrip(new DefaultTableChangeListener(sort));
      WeakReference<Object> target = droppedTarget(alone);

      for(int i = 0; i < MAX_GC_ROUNDS && target.get() != null; i++) {
         gc();
      }

      assertNull(target.get(), "a listener read back without its filter keeps the filter alive");
      assertNull(alone.getTarget(), "the listener still returns the collected filter");
   }

   /**
    * A filter over a set table, which keeps its own listener list and serializes it, still gets
    * the set table's change events through the listener read back from that list.
    */
   @Test
   public void filterOverSetTableFollowsChangeAfterSerialization() throws Exception {
      DefaultTableLens left = new DefaultTableLens(data());
      SortFilter sort = sort(new UnionTableLens(left, new DefaultTableLens(data())));
      assertEquals(5, firstValue(sort));
      SortFilter sortCopy = (SortFilter) roundTrip(sort);
      UnionTableLens unionCopy = (UnionTableLens) sortCopy.getTable();
      DefaultTableLens leftCopy = (DefaultTableLens) unionCopy.getTables()[0];
      assertEquals(5, firstValue(sortCopy));

      // the first listener was read back from the set table's list. the others were added when
      // the filter was read back (SortFilter.readObject calls setTable): remove them, so only the
      // deserialized listener can invalidate the filter
      List<?> listeners = setTableListeners(unionCopy);
      assertFalse(listeners.isEmpty(), "the set table read back has no listener");
      DefaultTableChangeListener listener = (DefaultTableChangeListener) listeners.get(0);
      assertSame(sortCopy, listener.getTarget(), "the deserialized listener lost its filter");

      for(Object added : new ArrayList<>(listeners.subList(1, listeners.size()))) {
         unionCopy.removeChangeListener((DefaultTableChangeListener) added);
      }

      assertEquals(1, setTableListeners(unionCopy).size());
      leftCopy.setObject(1, VALUE_COL, CHANGED_VALUE);
      unionCopy.invalidate();

      assertEquals(CHANGED_VALUE, firstValue(sortCopy), "the deserialized filter got no event");
   }

   private static Object roundTrip(Object value) throws Exception {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(value);
      }

      try(ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
      {
         return in.readObject();
      }
   }

   // in its own frame, so no local variable keeps the filter reachable
   private static WeakReference<Object> droppedTarget(DefaultTableChangeListener listener) {
      return new WeakReference<>(listener.getTarget());
   }

   // in its own frame, so no local variable keeps the filter reachable
   private static WeakReference<SortFilter> droppedSort(DefaultTableLens base) {
      SortFilter sort = sort(base);
      firstValue(sort);
      return new WeakReference<>(sort);
   }

   // run full gcs until a fresh weakly reachable object is cleared, a few times over
   private static void gc() throws InterruptedException {
      for(int round = 0; round < 5; round++) {
         WeakReference<Object> sentinel = new WeakReference<>(new Object());

         for(int i = 0; i < MAX_GC_ROUNDS && sentinel.get() != null; i++) {
            System.gc();
            Thread.sleep(10);
         }

         assertNull(sentinel.get(), "System.gc() did not collect");
      }
   }

   private static List<?> setTableListeners(SetTableLens table) throws Exception {
      Field field = SetTableLens.class.getDeclaredField("clisteners");
      field.setAccessible(true);
      return (List<?>) field.get(table);
   }

   private static int listenerCount(AbstractTableLens table) throws Exception {
      Field field = AbstractTableLens.class.getDeclaredField("clisteners");
      field.setAccessible(true);
      return ((List<?>) field.get(table)).size();
   }

   private static Object[][] data() {
      return new Object[][] {
         { "row", "col", "value" },
         { "a", "x", 10 },
         { "a", "x", 10 },
         { "b", "x", 5 },
         { "b", "y", 7 }
      };
   }

   private static Object[][] changedData() {
      Object[][] data = data();
      data[1][VALUE_COL] = CHANGED_VALUE;
      return data;
   }

   private static SortFilter sort(TableLens base) {
      return new SortFilter(base, new int[] { VALUE_COL }, true);
   }

   private static CrossTabFilter crosstab(TableLens base) {
      return new CrossTabFilter(base, 0, 1, VALUE_COL, new SumFormula());
   }

   private static Object firstValue(SortFilter sort) {
      sort.moreRows(TableLens.EOT);
      return sort.getObject(1, VALUE_COL);
   }

   private static List<List<Object>> cells(CrossTabFilter filter) {
      List<List<Object>> cells = new ArrayList<>();

      for(int r = 0; filter.moreRows(r); r++) {
         List<Object> row = new ArrayList<>();

         for(int c = 0; c < filter.getColCount(); c++) {
            row.add(filter.getObject(r, c));
         }

         cells.add(row);
      }

      return cells;
   }

   private static final int VALUE_COL = 2;
   private static final int CHANGED_VALUE = -100000;
   private static final int MAX_GC_ROUNDS = 100;
}
