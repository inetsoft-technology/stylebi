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
package inetsoft.graph.data;

import org.junit.jupiter.api.*;

import java.text.DecimalFormat;
import java.text.Format;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for hyperlinks, drill links and formats through {@link AttributeDataSet} and
 * {@link AbstractDataSetFilter}.
 */
@Tag("core")
class AttributeDataSetTest {
   private static final Comparator<Object> NAT = (a, b) -> ((Comparable) a).compareTo(b);

   // ---------------------------------------------------------------- helpers

   private static final Object[][] K5 = {
      {"k", "v"}, {"b", 2.0}, {"a", 1.0}, {"c", 3.0}, {"a", 5.0}, {"b", 4.0}};

   /** Base data set with links, drill links and formats kept per (col, row). */
   private static class AB extends DefaultDataSet implements AttributeDataSet {
      AB() {
         super(K5);
      }

      @Override
      public HRef getHyperlink(int col, int row) {
         return links.get(col + ":" + row);
      }

      @Override
      public void setHyperlink(int col, int row, HRef link) {
         links.put(col + ":" + row, link);
      }

      @Override
      public HRef[] getDrillHyperlinks(int col, int row) {
         HRef[] res = drills.get(col + ":" + row);
         return res == null ? new HRef[0] : res;
      }

      @Override
      public Format getFormat(int col, int row) {
         return formats.get(col + ":" + row);
      }

      @Override
      public Format getFormat(String col, int row) {
         formatNames.add(col);
         return AttributeDataSet.super.getFormat(col, row);
      }

      final List<String> formatNames = new ArrayList<>();
      final Map<String, HRef> links = new HashMap<>();
      final Map<String, HRef[]> drills = new HashMap<>();
      final Map<String, Format> formats = new HashMap<>();
   }

   /** Base data set that keeps only hyperlinks, relying on the interface defaults. */
   private static class LinksOnly extends DefaultDataSet implements AttributeDataSet {
      LinksOnly() {
         super(K5);
      }

      @Override
      public HRef getHyperlink(int col, int row) {
         return links.get(col + ":" + row);
      }

      @Override
      public void setHyperlink(int col, int row, HRef link) {
         links.put(col + ":" + row, link);
      }

      final Map<String, HRef> links = new HashMap<>();
   }

   /** Calc column with a header and field that does not read any data. */
   private static class FieldCalc implements CalcColumn {
      FieldCalc(String header, String field) {
         this.header = header;
         this.field = field;
      }

      @Override
      public Object calculate(DataSet data, int row, boolean first, boolean last) {
         return 1.0;
      }

      @Override
      public String getHeader() {
         return header;
      }

      @Override
      public Class getType() {
         return Double.class;
      }

      @Override
      public boolean isMeasure() {
         return true;
      }

      @Override
      public String getField() {
         return field;
      }

      private final String header;
      private final String field;
   }

   private static SubDataSet sub(DataSet base, int... mapping) {
      return new SubDataSet(base, mapping);
   }

   private static AbstractDataSetFilter idf(DataSet base) {
      return new AbstractDataSetFilter(base) {
      };
   }

   // ---------------------------------------------------------- base defaults

   @Test
   void defaultMethodsByNameFollowTheIndexMethods() {
      AB base = new AB();
      HRef link = new HRef("L");
      HRef[] drill = {new HRef("D")};
      Format format = new DecimalFormat("0.0");
      base.setHyperlink(1, 0, link);
      base.formats.put("1:0", format);
      base.drills.put("1:0", drill);

      assertSame(link, base.getHyperlink("v", 0));
      assertSame(format, base.getFormat("v", 0));
      assertSame(drill, base.getDrillHyperlinks("v", 0));

      HRef link2 = new HRef("L2");
      base.setHyperlink("v", 2, link2);
      assertSame(link2, base.getHyperlink(1, 2));
   }

   @Test
   void interfaceDefaultsHaveNoDrillLinksAndNoFormat() {
      LinksOnly base = new LinksOnly();

      assertNotNull(base.getDrillHyperlinks(1, 0));
      assertEquals(0, base.getDrillHyperlinks(1, 0).length);
      assertNull(base.getFormat(1, 0));
   }

   // ------------------------------------------------------- filter mappings

   @Test
   void hyperlinkIsReadFromTheMappedBaseRow() {
      AB base = new AB();
      HRef l = new HRef("L");
      HRef m = new HRef("M");
      base.setHyperlink(1, 2, l);
      base.setHyperlink(1, 0, m);
      SubDataSet sub = sub(base, 2, 0);

      assertSame(l, sub.getHyperlink(1, 0));
      assertSame(m, sub.getHyperlink(1, 1));
      assertSame(l, sub.getHyperlink("v", 0));
      assertSame(m, sub.getHyperlink("v", 1));
   }

   @Test
   void filterOwnHyperlinkOverridesTheBase() {
      AB base = new AB();
      HRef l = new HRef("L");
      HRef m = new HRef("M");
      HRef n = new HRef("N");
      base.setHyperlink(1, 2, l);
      base.setHyperlink(1, 0, m);
      SubDataSet sub = sub(base, 2, 0);

      sub.setHyperlink(1, 0, n);

      assertSame(n, sub.getHyperlink(1, 0));
      assertSame(m, sub.getHyperlink(1, 1));
      assertSame(l, base.getHyperlink(1, 2));
   }

   @Test
   void baseWithoutAttributesGivesNothingAndNoError() {
      SubDataSet sub = sub(new DefaultDataSet(K5), 2, 0);

      assertNull(sub.getHyperlink(1, 0));
      assertEquals(0, sub.getDrillHyperlinks(1, 0).length);
      assertNull(sub.getFormat(1, 0));
   }

   @Test
   void rowWithoutBaseRowGivesNothingAndNoError() {
      AB base = new AB();
      base.setHyperlink(1, 2, new HRef("L"));
      base.formats.put("1:2", new DecimalFormat("0.0"));
      SubDataSet sub = sub(base, 2, 0);

      assertEquals(-1, sub.getBaseRow(3));
      assertNull(sub.getHyperlink(1, 3));
      assertEquals(0, sub.getDrillHyperlinks(1, 3).length);
      assertNull(sub.getFormat(1, 3));
      assertNull(sub.getFormat("v", 3));
   }

   @Test
   void drillLinksAreReadThroughTheMappingAndMissForCalcColumns() {
      AB base = new AB();
      HRef[] drill = {new HRef("D")};
      base.drills.put("1:2", drill);
      SubDataSet sub = sub(base, 2, 0);
      sub.addCalcColumn(new FieldCalc("rt", "v"));
      sub.prepareCalc(null, null, true);

      assertSame(drill, sub.getDrillHyperlinks(1, 0));
      assertEquals(0, sub.getDrillHyperlinks(2, 0).length);
   }

   @Test
   void calcColumnFormatFollowsTheField() {
      AB base = new AB();
      Format format = new DecimalFormat("0.0");
      base.formats.put("1:2", format);
      SubDataSet sub = sub(base, 2, 0);
      sub.addCalcColumn(new FieldCalc("rt", "v"));
      sub.prepareCalc(null, null, true);

      assertSame(format, sub.getFormat(sub.indexOfHeader("rt"), 0));
   }

   @Test
   @DisplayName("format by name forwards the column name unchanged to the base")
   void formatByNameForwardsTheName() {
      AB base = new AB();
      Format format = new DecimalFormat("0.0");
      base.formats.put("1:2", format);
      SubDataSet sub = sub(base, 2, 0);
      sub.addCalcColumn(new FieldCalc("rt", "v"));
      sub.prepareCalc(null, null, true);

      assertSame(format, sub.getFormat("v", 0));
      assertSame(sub.getFormat(1, 0), sub.getFormat("v", 0));
      sub.getFormat("rt", 0);
      // the base decides by name (a calc column of the filter is unknown there)
      assertEquals(List.of("v", "v", "rt"), base.formatNames);
   }

   @Test
   void calcColumnFieldLoopAndUnknownFieldGiveNoFormat() {
      AB base = new AB();
      base.formats.put("1:0", new DecimalFormat("0.0"));
      SubDataSet loop = sub(base, 0, 1);
      loop.addCalcColumn(new FieldCalc("self", "self"));
      loop.prepareCalc(null, null, true);
      assertNull(loop.getFormat(2, 0));

      SubDataSet unknown = sub(base, 0, 1);
      unknown.addCalcColumn(new FieldCalc("calc", "nofield"));
      unknown.prepareCalc(null, null, true);
      assertNull(unknown.getFormat(2, 0));
   }

   @Test
   @Disabled("Bug #82095 (dead code): AttributeDataSet.setHyperlink has no caller; clone(false) shares the links matrix with the original, so setHyperlink on the clone replaces the original link, expected the original link kept")
   @DisplayName("a deep clone keeps its own hyperlinks")
   void cloneDoesNotShareLinks() {
      AB base = new AB();
      HRef l = new HRef("L");
      AbstractDataSetFilter filter = idf(base);
      filter.setHyperlink(1, 0, l);
      AttributeDataSet copy = (AttributeDataSet) filter.clone(false);

      copy.setHyperlink(1, 0, new HRef("M"));

      assertSame(l, filter.getHyperlink(1, 0));
   }

   @Test
   void sortedFilterMapsFormatThroughTheSortedRow() {
      AB base = new AB();
      base.setComparator("k", NAT);
      Format format = new DecimalFormat("0.0");
      base.formats.put("1:1", format);
      SortedDataSet sorted = new SortedDataSet(base, "k");

      // sorted row 0 is base row 1 (the first a)
      assertEquals(1, sorted.getBaseRow(0));
      assertSame(format, sorted.getFormat(1, 0));
   }

   @Test
   @DisplayName("pinned: a link on an extra filter column is asked from the base with the filter column index")
   void linkForExtraFilterColumn() {
      AB base = new AB();
      HRef link = new HRef("L");
      base.setHyperlink(2, 0, link);
      SubDataSet sub = sub(base, 0, 1);
      sub.addCalcColumn(new FieldCalc("rt", "v"));
      sub.prepareCalc(null, null, true);

      assertSame(link, sub.getHyperlink(2, 0));
   }

   @Test
   void linkTableIsKeyedByTheFilterRow() {
      AB base = new AB();
      AbstractDataSetFilter filter = idf(base);
      HRef link = new HRef("L");
      filter.setHyperlink(1, 3, link);

      assertSame(link, filter.getHyperlink(1, 3));
      assertNull(filter.getHyperlink(1, 2));
      assertSame(link, filter.getHyperlink("v", 3));
      assertNull(filter.getHyperlink("v", 2));
   }
}
