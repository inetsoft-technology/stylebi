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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.Hyperlink;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.graph.calc.ChangeCalc;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79449: {@code VSUtil.buildMap}'s runtime-ID fallback carried a path-keyed setting
 * (format, highlight, hyperlink) from a measure that left the aggregates shelf onto a different
 * measure that took over its slot. The carry now requires the same design column. The runtime IDs
 * are assigned by {@code setRuntimeAggregates}, as a real render does, not hard-coded.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSUtilSyncCrosstabAggregateCarryTest {
   @Test
   void replacingAMeasureDoesNotCarryItsFormatToTheNewMeasure() {
      Map<TableDataPath, Object> map = sync(aggs(sum("Total"), sum("Discount")),
                                            aggs(sum("Total"), sum("Price")), false,
                                            "Discount", "D-FMT");

      assertFalse(map.containsKey(path("Price")), "Price must not inherit Discount's setting");
      assertEquals("D-FMT", map.get(path("Discount")), "retention (43331): old key stays");
   }

   @Test
   void swappingTwoSlotsCarriesNeitherMeasure() {
      Map<TableDataPath, Object> map = sync(aggs(sum("A"), sum("B")), aggs(sum("C"), sum("D")),
                                            false, "B", "B-FMT");

      assertFalse(map.containsKey(path("C")));
      assertFalse(map.containsKey(path("D")), "B must not be carried to D");
   }

   @Test
   void aFormatAlreadyOnTheIncomingMeasureIsNotOverwritten() {
      Map<TableDataPath, Object> map = new HashMap<>();
      map.put(path("Discount"), "D-FMT");
      map.put(path("Price"), "P-FMT");

      sync(aggs(sum("Total"), sum("Discount")), aggs(sum("Total"), sum("Price")), false, map);

      assertEquals("P-FMT", map.get(path("Price")));
      assertEquals("D-FMT", map.get(path("Discount")));
   }

   @Test
   void reAddingTheOldMeasureRestoresItsFormat() {
      Map<TableDataPath, Object> map = sync(aggs(sum("Total"), sum("Discount")),
                                            aggs(sum("Total"), sum("Price")), false,
                                            "Discount", "D-FMT");
      sync(aggs(sum("Total"), sum("Price")), aggs(sum("Total"), sum("Discount")), false, map);

      assertEquals("D-FMT", map.get(path("Discount")));
   }

   @Test
   void theSameMeasureKeepsItsFormat() {
      Map<TableDataPath, Object> map = sync(aggs(sum("Total"), sum("Discount")),
                                            aggs(sum("Total"), sum("Discount")), false,
                                            "Discount", "D-FMT");

      assertEquals("D-FMT", map.get(path("Discount")));
      assertEquals(1, map.size());
   }

   @Test
   void aFormulaChangeInPlaceKeepsTheFormat() {
      Map<TableDataPath, Object> map = sync(aggs(sum("Total"), sum("Discount")),
                                            aggs(sum("Total"), agg("Discount", "Average")), false,
                                            "Discount", "D-FMT");

      assertEquals("D-FMT", map.get(path("Discount")));
      assertEquals(1, map.size());
   }

   @Test
   void addingACalculatorToTheSameColumnCarriesTheFormatToThePrefixedKey() {
      VSAggregateRef calc = sum("Discount");
      calc.setCalculator(new ChangeCalc());
      // a one-column formula: the secondary column is null on one side and "" on the other
      calc.setSecondaryColumnValue("");
      String key = VSUtil.buildName(aggs(calc), true)[0];
      assertNotEquals("Discount", key, "fixture: the calculator prefixes the key");

      Map<TableDataPath, Object> map = sync(aggs(sum("Total"), sum("Discount")),
                                            aggs(sum("Total"), calc), false, "Discount", "D-FMT");

      assertEquals("D-FMT", map.get(path(key)));
   }

   @Test
   void aCalculatorChangeWithADifferentColumnDoesNotCarry() {
      VSAggregateRef calc = sum("Price");
      calc.setCalculator(new ChangeCalc());
      String key = VSUtil.buildName(aggs(calc), true)[0];

      Map<TableDataPath, Object> map = sync(aggs(sum("Total"), sum("Discount")),
                                            aggs(sum("Total"), calc), false, "Discount", "D-FMT");

      assertFalse(map.containsKey(path(key)));
   }

   @Test
   void aSecondaryColumnChangeOnACalculatorDoesNotCarry() {
      VSAggregateRef old = agg("A", "Correlation");
      old.setSecondaryColumnValue("B");
      VSAggregateRef next = agg("A", "Correlation");
      next.setSecondaryColumnValue("C");
      next.setCalculator(new ChangeCalc());
      String key = VSUtil.buildName(aggs(next), true)[0];

      Map<TableDataPath, Object> map = sync(aggs(old), aggs(next), false, "A", "A-FMT");

      assertFalse(map.containsKey(path(key)));
   }

   @Test
   void aVariableDrivenColumnWithTheSameDesignValueStillCarries() {
      VSAggregateRef old = sum("Discount");
      old.setColumnValue("$(measure)");
      VSAggregateRef next = sum("Price");
      next.setColumnValue("$(measure)");

      Map<TableDataPath, Object> map = sync(aggs(sum("Total"), old), aggs(sum("Total"), next),
                                            false, "Discount", "D-FMT");

      assertEquals("D-FMT", map.get(path("Price")), "design value unchanged: same measure");
   }

   @Test
   void reorderAddAndRemoveBehaveAsBefore() {
      Map<TableDataPath, Object> map = sync(aggs(sum("A"), sum("B")), aggs(sum("B"), sum("A")),
                                            false, "B", "B-FMT");
      assertEquals("B-FMT", map.get(path("B")));
      assertEquals(1, map.size());

      map = sync(aggs(sum("A"), sum("B")), aggs(sum("A"), sum("B"), sum("C")), false, "B",
                 "B-FMT");
      assertEquals("B-FMT", map.get(path("B")));
      assertFalse(map.containsKey(path("C")));

      map = sync(aggs(sum("A"), sum("B")), aggs(sum("A")), false, "B", "B-FMT");
      assertEquals("B-FMT", map.get(path("B")), "retention: old key stays");
   }

   /** Highlight and hyperlink maps ride the same call (generic V). */
   @Test
   void anyPathKeyedMapFollowsTheSameRule() {
      Map<TableDataPath, Object> hl = new HashMap<>();
      hl.put(path("Discount"), new HighlightGroup());
      sync(aggs(sum("Total"), sum("Discount")), aggs(sum("Total"), sum("Price")), false, hl);
      assertFalse(hl.containsKey(path("Price")));

      Map<TableDataPath, Object> links = new HashMap<>();
      links.put(path("Discount"), new Hyperlink("http://x", Hyperlink.WEB_LINK));
      sync(aggs(sum("Total"), sum("Discount")), aggs(sum("Total"), sum("Price")), false, links);
      assertFalse(links.containsKey(path("Price")));
   }

   /**
    * VSInputService's caller (fromSetInput): an un-carried entry is marked removed and
    * pruneTableDataPaths drops the unreferenced path, so a different-column swap loses the
    * format there instead of retaining it. Recorded, not endorsed beyond the owner's decision.
    */
   @Test
   void fromSetInputPrunesTheUncarriedEntry() {
      Map<TableDataPath, Object> map = sync(aggs(sum("Total"), sum("Discount")),
                                            aggs(sum("Total"), sum("Price")), true,
                                            "Discount", "D-FMT");

      assertFalse(map.containsKey(path("Price")));
      assertFalse(map.containsKey(path("Discount")), "pruned: Discount is no longer bound");
   }

   /** The rule is aggregate-only: a row dimension replaced in the same slot still carries. */
   @Test
   void replacingADimensionInTheSameSlotStillCarriesItsSetting() {
      CrosstabVSAssembly old = crosstab(aggs(sum("Total")));
      CrosstabVSAssembly next = crosstab(aggs(sum("Total")));
      VSCrosstabInfo ninfo = next.getVSCrosstabInfo();
      ninfo.setDesignRowHeaders(new DataRef[] { dim("Country") });
      ninfo.setRuntimeRowHeaders(new DataRef[] { dim("Country") });
      ((VSDimensionRef) ninfo.getRuntimeRowHeaders()[0]).setRuntimeID(1);

      Map<TableDataPath, Object> map = new HashMap<>();
      map.put(new TableDataPath(-1, TableDataPath.SUMMARY, XSchema.DOUBLE,
                                new String[] { "Region", "Category", "Total" }), "T-FMT");
      VSUtil.syncCrosstabPath(next, (CrosstabVSAssemblyInfo) old.getInfo(), false, map, false,
                              false, null);

      assertEquals("T-FMT",
                   map.get(new TableDataPath(-1, TableDataPath.SUMMARY, XSchema.DOUBLE,
                                             new String[] { "Country", "Category", "Total" })),
                   "dimension carry is unchanged by the aggregate rule");
   }

   // -- fixture --

   private Map<TableDataPath, Object> sync(DataRef[] oldAggs, DataRef[] newAggs,
                                           boolean fromSetInput, String measure, Object value)
   {
      Map<TableDataPath, Object> map = new HashMap<>();
      map.put(path(measure), value);
      sync(oldAggs, newAggs, fromSetInput, map);
      return map;
   }

   private void sync(DataRef[] oldAggs, DataRef[] newAggs, boolean fromSetInput,
                     Map<TableDataPath, Object> map)
   {
      CrosstabVSAssembly old = crosstab(oldAggs);
      CrosstabVSAssembly next = crosstab(newAggs);
      VSUtil.syncCrosstabPath(next, (CrosstabVSAssemblyInfo) old.getInfo(), false, map,
                              fromSetInput, false, null);
   }

   /** rows Region, cols Category; the runtime IDs come from setRuntimeAggregates. */
   private static CrosstabVSAssembly crosstab(DataRef[] aggs) {
      CrosstabVSAssembly cross = new CrosstabVSAssembly(new Viewsheet(), "Crosstab1");
      VSCrosstabInfo info = new VSCrosstabInfo();
      info.setDesignRowHeaders(new DataRef[] { dim("Region") });
      info.setDesignColHeaders(new DataRef[] { dim("Category") });
      info.setRuntimeRowHeaders(new DataRef[] { dim("Region") });
      info.setRuntimeColHeaders(new DataRef[] { dim("Category") });
      info.setDesignAggregates(aggs);
      DataRef[] runtime = new DataRef[aggs.length];

      for(int i = 0; i < aggs.length; i++) {
         runtime[i] = (DataRef) aggs[i].clone();
      }

      info.setRuntimeAggregates(runtime);
      ((VSDimensionRef) info.getRuntimeColHeaders()[0]).setRuntimeID(0);
      ((VSDimensionRef) info.getRuntimeRowHeaders()[0]).setRuntimeID(1);
      cross.setVSCrosstabInfo(info);
      return cross;
   }

   private static DataRef[] aggs(DataRef... aggs) {
      return aggs;
   }

   private static VSDimensionRef dim(String column) {
      VSDimensionRef dim = new VSDimensionRef(new ColumnRef(new AttributeRef(null, column)));
      dim.setGroupColumnValue(column);
      return dim;
   }

   private static VSAggregateRef sum(String column) {
      return agg(column, "Sum");
   }

   private static VSAggregateRef agg(String column, String formula) {
      VSAggregateRef agg = new VSAggregateRef();
      agg.setDataRef(new ColumnRef(new AttributeRef(null, column)));
      agg.setColumnValue(column);
      agg.setFormulaValue(formula);
      return agg;
   }

   /** The full-dimension SUMMARY path [row dims, col dims, measure key]. */
   private static TableDataPath path(String measureKey) {
      return new TableDataPath(-1, TableDataPath.SUMMARY, XSchema.DOUBLE,
                               new String[] { "Region", "Category", measureKey });
   }
}
