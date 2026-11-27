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

package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.test.*;
import inetsoft.test.dc.DcInMemoryViewsheetFixture;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.Collectors;

import static inetsoft.report.composition.execution.CrosstabVSAQuerySupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82460: a Concat in a crosstab was aggregated in two stages (group by push-down, then the
 * crosstab over the group results). The empty group gave "" that was added to the total as a
 * value (stray comma) and the total listed the groups instead of the rows. The Concat is now
 * aggregated once over the detail rows, so every cell and total is the row order concatenation of
 * the non-null values of its rows.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  DcInMemoryViewsheetFixture.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("slow")
class CrosstabConcatVSAQueryTest {
   private final DcInMemoryViewsheetFixture fixture = new DcInMemoryViewsheetFixture();

   @BeforeEach
   void setUp() {
      fixture.setUp();
   }

   @AfterEach
   void tearDown() {
      fixture.tearDown();
   }

   private static final String[] NAMES = { "Region", "Product", "Grade", "Label", "Qty" };
   private static final String[] TYPES = { XSchema.STRING, XSchema.STRING, XSchema.STRING,
                                           XSchema.STRING, XSchema.INTEGER };

   private static Object[] r(String region, String product, String grade, String label,
                             int qty)
   {
      return new Object[] { region, product, grade, label, qty };
   }

   /** The cells of the crosstab by row, header included, as text ("" for null). */
   private static List<List<String>> cells(TableLens lens) {
      List<List<String>> rows = new ArrayList<>();

      for(int i = 0; i < lens.getRowCount(); i++) {
         List<String> row = new ArrayList<>();

         for(int j = 0; j < lens.getColCount(); j++) {
            Object o = lens.getObject(i, j);
            row.add(o == null ? "" : o.toString());
         }

         rows.add(row);
      }

      return rows;
   }

   private static String concat(List<Object[]> rows) {
      return rows.stream().map(row -> (String) row[3]).filter(Objects::nonNull)
         .collect(Collectors.joining(","));
   }

   private List<List<String>> region(List<Object[]> rows) throws Exception {
      var sheet = fixture.openRuntime(NAMES, TYPES, rows);
      add(sheet, "C1", new VSDimensionRef[] { dim("Region") }, new VSDimensionRef[0],
          agg("Label", AggregateFormula.CONCAT));
      return cells(data(sheet, "C1"));
   }

   @Test
   void concatIsNotAggregatedOnAggregate() {
      VSAggregateRef concat = agg("Label", AggregateFormula.CONCAT);
      VSAggregateRef sum = agg("Qty", AggregateFormula.SUM);

      assertFalse(VSUtil.supportsAOA(concat, new inetsoft.uql.asset.AggregateRef[0]));
      assertTrue(VSUtil.supportsAOA(sum, new inetsoft.uql.asset.AggregateRef[0]));
   }

   @Test
   void nullValuesGiveNoStrayCommaInTheTotal() throws Exception {
      List<Object[]> rows = List.of(r("East", "p", "g", "a", 1), r("East", "p", "g", null, 1),
                                    r("East", "p", "g", "a", 1), r("North", "p", "g", "c", 1),
                                    r("West", "p", "g", null, 1), r("West", "p", "g", null, 1));
      List<List<String>> cells = region(rows);
      List<String> all = new ArrayList<>();
      cells.forEach(row -> all.add(row.get(row.size() - 1)));

      assertTrue(all.contains("a,a,c"), "grand total a,a,c: " + cells);
      assertTrue(all.contains("a,a"), "East: " + cells);
      assertTrue(all.contains("c"), "North: " + cells);
      assertTrue(all.stream().noneMatch(s -> s.endsWith(",") || s.startsWith(",")),
                 "no stray comma: " + cells);
   }

   @Test
   void totalIsInRowOrderNotInGroupOrder() throws Exception {
      List<Object[]> rows = List.of(r("East", "p", "g", "10", 1), r("West", "p", "g", "20", 1),
                                    r("East", "p", "g", "30", 1), r("North", "p", "g", "5", 1),
                                    r("West", "p", "g", "15", 1), r("East", "p", "g", "20", 1));
      List<List<String>> cells = region(rows);
      List<String> all = new ArrayList<>();
      cells.forEach(row -> all.add(row.get(row.size() - 1)));

      assertTrue(all.contains("10,20,30,5,15,20"), "grand total in row order: " + cells);
      assertTrue(all.contains("10,30,20"), "East: " + cells);
      assertTrue(all.contains("20,15"), "West: " + cells);
   }

   @Test
   void twoRowGroupsAndAColumnGroupWithTotals() throws Exception {
      List<Object[]> rows = List.of(r("E", "p1", "g1", "a", 1), r("W", "p1", "g2", null, 2),
                                    r("E", "p2", "g2", "b", 3), r("E", "p1", "g2", "c", 4),
                                    r("W", "p2", "g1", "d", 5), r("E", "p1", "g1", null, 6),
                                    r("W", "p1", "g1", "e", 7), r("E", "p1", "g1", "f", 8),
                                    r("W", "p2", "g2", null, 9));
      var sheet = fixture.openRuntime(NAMES, TYPES, rows);
      add(sheet, "C2", new VSDimensionRef[] { dim("Region"), dim("Product") },
          new VSDimensionRef[] { dim("Grade") }, agg("Label", AggregateFormula.CONCAT));
      List<List<String>> cells = cells(data(sheet, "C2"));

      // every value is the row order concat of the non-null labels of some subset of the rows
      Set<String> valid = new HashSet<>();
      valid.add("");

      for(String region : new String[] { null, "E", "W" }) {
         for(String product : new String[] { null, "p1", "p2" }) {
            for(String grade : new String[] { null, "g1", "g2" }) {
               valid.add(concat(rows.stream().filter(row ->
                  (region == null || region.equals(row[0])) &&
                  (product == null || product.equals(row[1])) &&
                  (grade == null || grade.equals(row[2]))).collect(Collectors.toList())));
            }
         }
      }

      Set<String> found = new HashSet<>();

      for(int i = 0; i < cells.size(); i++) {
         for(int j = 0; j < cells.get(i).size(); j++) {
            String s = cells.get(i).get(j);

            if(i < 1 || j < 2) {
               continue; // header rows and columns
            }

            assertTrue(valid.contains(s), "cell " + i + "," + j + " '" + s + "': " + cells);
            found.add(s);
         }
      }

      // the grand total, a column total (g1) and the row total of E/p1 (a,c,f; group order a,f,c)
      assertTrue(found.contains(concat(rows)), "grand: " + cells);
      assertTrue(found.contains(concat(rows.stream().filter(row -> "g1".equals(row[2]))
                                         .collect(Collectors.toList()))), "g1: " + cells);
      assertTrue(found.contains(concat(rows.stream().filter(row ->
         "E".equals(row[0]) && "p1".equals(row[1])).collect(Collectors.toList()))),
         "E/p1: " + cells);
   }

   @Test
   void topNOthersHasNoStrayComma() throws Exception {
      List<Object[]> rows = List.of(r("A", "p", "g", "x", 100), r("B", "p", "g", "y", 90),
                                    r("C", "p", "g", null, 80), r("D", "p", "g", null, 70),
                                    r("E", "p", "g", "z", 60), r("F", "p", "g", null, 50),
                                    r("A", "p", "g", "w", 1));
      var sheet = fixture.openRuntime(NAMES, TYPES, rows);
      VSDimensionRef region = dim("Region");
      region.setRankingOptionValue(XCondition.TOP_N + "");
      region.setRankingNValue("2");
      region.setRankingColValue("Qty");
      region.setGroupOthersValue("true");
      add(sheet, "C3", new VSDimensionRef[] { region }, new VSDimensionRef[0],
          agg("Qty", AggregateFormula.SUM), agg("Label", AggregateFormula.CONCAT));
      List<List<String>> cells = cells(data(sheet, "C3"));
      List<String> labels = new ArrayList<>();

      for(List<String> row : cells) {
         labels.add(row.get(row.size() - 1));
      }

      assertTrue(labels.stream().noneMatch(s -> s.startsWith(",") || s.endsWith(",") ||
                                               s.contains(",,")),
                 "no stray comma: " + cells);
      // Others (C, D, E, F) holds the labels of E only; the grand total is all rows in row order
      assertTrue(labels.contains("z"), "Others: " + cells);
      // the Top N rebuilds the grand total from the rows of all groups, in row order
      assertTrue(labels.contains("x,y,z,w"), "grand total: " + cells);
   }

   @Test
   void topNOthersWithAColumnGroupHasNoStrayComma() throws Exception {
      List<Object[]> rows = List.of(r("A", "p", "g1", "x", 100), r("B", "p", "g1", "y", 90),
                                    r("C", "p", "g1", null, 80), r("D", "p", "g2", null, 70),
                                    r("E", "p", "g2", "z", 60), r("F", "p", "g1", "v", 50),
                                    r("A", "p", "g2", "w", 1));
      var sheet = fixture.openRuntime(NAMES, TYPES, rows);
      VSDimensionRef region = dim("Region");
      region.setRankingOptionValue(XCondition.TOP_N + "");
      region.setRankingNValue("2");
      region.setRankingColValue("Qty");
      region.setGroupOthersValue("true");
      add(sheet, "C4", new VSDimensionRef[] { region }, new VSDimensionRef[] { dim("Grade") },
          agg("Qty", AggregateFormula.SUM), agg("Label", AggregateFormula.CONCAT));
      List<List<String>> cells = cells(data(sheet, "C4"));

      for(List<String> row : cells) {
         for(String s : row) {
            assertFalse(s.startsWith(",") || s.endsWith(",") || s.contains(",,"),
                        "stray comma: " + cells);
         }
      }

      // Others = C, D, E, F: g1 has v, g2 has z, total has z,v in row order
      assertTrue(cells.toString().contains("Others"), "Others group: " + cells);
      assertTrue(cells.toString().contains("z,v"), "Others total: " + cells);
   }
}
