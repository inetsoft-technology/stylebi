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
package inetsoft.uql.util;

import inetsoft.test.*;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.XQueryRepository;
import inetsoft.uql.XTableNode;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.web.composer.model.BrowseDataModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #79613: browsing a column with many distinct values was quadratic because the
 * de-duplication in ColumnCache.addXNode and ColumnCache.format used List.contains. These tests
 * pin the de-duplication semantics (which must stay) and guard the scaling.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ColumnCacheBrowseDataTest {
   private ColumnCache cache;
   private Method addXNode;

   @BeforeEach
   void setUp() throws Exception {
      cache = new ColumnCache(mock(DataSourceRegistry.class), null);
      addXNode = ColumnCache.class.getDeclaredMethod(
         "addXNode", String.class, XQueryRepository.class, String.class, String.class,
         inetsoft.uql.XNode.class, inetsoft.uql.VariableTable.class);
      addXNode.setAccessible(true);
   }

   private BrowseDataModel run(int ncol, Object[]... rows) throws Exception {
      return (BrowseDataModel) addXNode.invoke(
         cache, "k", null, "q", "c", new ListTable(ncol, Arrays.asList(rows)), null);
   }

   @Test
   void addXNodeFirstOccurrenceWinsWithDescription() throws Exception {
      BrowseDataModel m = run(2, new Object[] { "b", "d2" }, new Object[] { "a", "d1" },
                              new Object[] { "b", "dX" });

      // sorted by description; the second "b" row (dX) is dropped
      assertArrayEquals(new Object[] { "a", "b" }, m.values());
      assertArrayEquals(new Object[] { "d1", "d2" }, m.labels());
   }

   @Test
   void addXNodeCollapsesNullsAndIsCaseSensitive() throws Exception {
      BrowseDataModel m = run(1, new Object[] { null }, new Object[] { "a" },
                              new Object[] { null }, new Object[] { "A" },
                              new Object[] { "a" });

      assertEquals(3, m.values().length);
      assertEquals(1, Arrays.stream(m.values()).filter(Objects::isNull).count());
      assertTrue(Arrays.asList(m.values()).containsAll(Arrays.asList("a", "A")));
      assertFalse(m.existLabels());
   }

   @Test
   void addXNodeDedupesNumbersByEquals() throws Exception {
      // equality is Object.equals as before (a column holds one class, so Integer vs Long never
      // meets here; the later sort could not compare them anyway)
      BrowseDataModel m = run(1, new Object[] { 2 }, new Object[] { 1 }, new Object[] { 2 },
                              new Object[] { 1 });

      assertArrayEquals(new Object[] { 1, 2 }, m.values());
   }

   @Test
   void addXNodeSortsByValue() throws Exception {
      BrowseDataModel m = run(1, new Object[] { "c" }, new Object[] { "a" },
                              new Object[] { "b" }, new Object[] { "a" });

      assertArrayEquals(new Object[] { "a", "b", "c" }, m.values());
   }

   @Test
   void formatCollapsesEqualLabelsFirstWins() {
      BrowseDataModel m = BrowseDataModel.builder()
         .values(new Object[] { "x", "y", null, "", "z" })
         .labels(new Object[] { "L", "L", null, "", null })
         .build();

      String[][] res = ColumnCache.format(m, "string", false, null, DateRangeRef.NONE_DATE_GROUP);

      assertArrayEquals(new String[] { "x", null }, res[0]);
      assertArrayEquals(new String[] { "L", "" }, res[1]);
   }

   @Test
   void formatKeepsOrderAndCaseSensitivity() {
      BrowseDataModel m = BrowseDataModel.builder()
         .values(new Object[] { "b", "a", "B", "a", "c" }).build();

      String[][] res = ColumnCache.format(m, "string", false, null, DateRangeRef.NONE_DATE_GROUP);

      assertArrayEquals(new String[] { "b", "a", "B", "c" }, res[0]);
      assertArrayEquals(new String[] { "b", "a", "B", "c" }, res[1]);
   }

   /**
    * The cost varies with the number of distinct values. The old List.contains code needed about
    * 10 s (addXNode) plus 3 s (format) for 50k values and about 27 s for 100k on a development
    * machine; the hash-based code needs well under a second. The bound is generous for slow CI.
    */
   @Test
   void manyDistinctValuesScaleLinearly() {
      final int n = 100_000;
      List<Object[]> rows = new ArrayList<>(n);

      for(int i = 0; i < n; i++) {
         rows.add(new Object[] { "value-" + i });
      }

      Collections.shuffle(rows, new Random(1));

      assertTimeoutPreemptively(Duration.ofSeconds(8), () -> {
         BrowseDataModel m = (BrowseDataModel) addXNode.invoke(
            cache, "k", null, "q", "c", new ListTable(1, rows), null);
         assertEquals(n, m.values().length);

         String[][] res = ColumnCache.format(
            m, "string", false, null, DateRangeRef.NONE_DATE_GROUP);
         assertEquals(n, res[0].length);
         assertEquals(n, res[1].length);
      });
   }

   private static final class ListTable extends XTableNode {
      ListTable(int ncol, List<Object[]> rows) {
         this.ncol = ncol;
         this.rows = rows;
      }

      @Override
      public boolean next() {
         return ++pos < rows.size();
      }

      @Override
      public int getColCount() {
         return ncol;
      }

      @Override
      public String getName(int col) {
         return "c" + col;
      }

      @Override
      public Class getType(int col) {
         return Object.class;
      }

      @Override
      public Object getObject(int col) {
         return rows.get(pos)[col];
      }

      @Override
      public XMetaInfo getXMetaInfo(int col) {
         return null;
      }

      @Override
      public boolean rewind() {
         pos = -1;
         return true;
      }

      @Override
      public boolean isRewindable() {
         return true;
      }

      private final int ncol;
      private final List<Object[]> rows;
      private int pos = -1;
   }
}
