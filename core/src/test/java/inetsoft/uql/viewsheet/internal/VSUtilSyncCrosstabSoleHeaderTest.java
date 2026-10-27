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
 * Bug #79452: a crosstab with row dimensions and no column dimension renders its only measure's
 * header at the one-segment path [agg]; with a second measure the header paths are
 * [row dims..., agg] and [ROW_GRAND_TOTAL, agg]. {@code VSUtil.syncCrosstabPath} never re-keyed the
 * one-segment path, so a label (or header format, highlight, hyperlink) vanished when a second
 * measure was added. Only 1 to 2+ is migrated; 2 to 1 stays as it was.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSUtilSyncCrosstabSoleHeaderTest {
   @Test
   void theSoleMeasureLabelFollowsAnAddedSecondMeasure() {
      Map<TableDataPath, Object> map = soleLabelled("Sales");

      sync(rows("Region"), aggs(sum("Total")), rows("Region"), aggs(sum("Total"), sum("Num")), map);

      for(int type : new int[] { TableDataPath.HEADER, TableDataPath.GROUP_HEADER }) {
         assertEquals("Sales", map.get(path(type, "Region", "Total")), "detail, type " + type);
         assertEquals("Sales", map.get(path(type, "ROW_GRAND_TOTAL", "Total")),
                      "grand total, type " + type);
         assertNull(map.get(path(type, "Region", "Num")));
         assertNull(map.get(path(type, "ROW_GRAND_TOTAL", "Num")));
      }

      assertEquals("Sales", map.get(path(TableDataPath.GROUP_HEADER, "Total")), "old key stays");
      assertEquals("Sales", map.get(path(TableDataPath.HEADER, "Total")), "old key stays");
   }

   @Test
   void eachTwinKeyMigratesIndependently() {
      Map<TableDataPath, Object> map = new HashMap<>();
      map.put(path(TableDataPath.HEADER, "Total"), "H-ONLY");

      sync(rows("Region"), aggs(sum("Total")), rows("Region"), aggs(sum("Total"), sum("Num")), map);

      assertEquals("H-ONLY", map.get(path(TableDataPath.HEADER, "Region", "Total")));
      assertNull(map.get(path(TableDataPath.GROUP_HEADER, "Region", "Total")));
   }

   @Test
   void everyNestingLevelOfTheNewShapeIsCovered() {
      Map<TableDataPath, Object> map = soleLabelled("Sales");

      sync(rows("Region", "Country"), aggs(sum("Total")), rows("Region", "Country"),
           aggs(sum("Total"), sum("Num")), map);

      assertEquals("Sales", map.get(path(TableDataPath.HEADER, "Region", "Country", "Total")));
      assertEquals("Sales", map.get(path(TableDataPath.HEADER, "Region", "Total")), "subtotal");
      assertEquals("Sales", map.get(path(TableDataPath.HEADER, "ROW_GRAND_TOTAL", "Total")));
   }

   @Test
   void theLabelFollowsTheMeasureWhenItMovesSlot() {
      Map<TableDataPath, Object> map = soleLabelled("Sales");

      sync(rows("Region"), aggs(sum("Total")), rows("Region"), aggs(sum("Num"), sum("Total")), map);

      assertEquals("Sales", map.get(path(TableDataPath.HEADER, "Region", "Total")));
      assertNull(map.get(path(TableDataPath.HEADER, "Region", "Num")));
   }

   @Test
   void aReplacedMeasureDoesNotInheritTheLabel() {
      Map<TableDataPath, Object> map = new HashMap<>();
      map.put(path(TableDataPath.HEADER, "A"), "A-LABEL");
      map.put(path(TableDataPath.GROUP_HEADER, "A"), "A-LABEL");

      sync(rows("Region"), aggs(sum("A")), rows("Region"), aggs(sum("C"), sum("D")), map);

      assertEquals(2, map.size(), "A's label is on neither C nor D");
   }

   @Test
   void withAColumnDimensionNothingMigrates() {
      Map<TableDataPath, Object> map = soleLabelled("Sales");

      sync(rows("Region"), aggs(sum("Total")), rows("Region"), cols("Category"),
           aggs(sum("Total"), sum("Num")), map);

      assertEquals(2, map.size());
   }

   @Test
   void aCrosstabWithoutRowDimensionsIsUntouched() {
      Map<TableDataPath, Object> map = soleLabelled("Sales");

      sync(rows(), aggs(sum("Total")), rows(), aggs(sum("Total"), sum("Num")), map);

      assertEquals(2, map.size());
   }

   /** 2 to 1 is deliberately unchanged: the label stays at the 2-aggregate keys. */
   @Test
   void twoToOneIsNotMigrated() {
      Map<TableDataPath, Object> map = new HashMap<>();
      map.put(path(TableDataPath.HEADER, "Region", "Total"), "Sales");

      sync(rows("Region"), aggs(sum("Total"), sum("Num")), rows("Region"), aggs(sum("Total")),
           map);

      assertNull(map.get(path(TableDataPath.HEADER, "Total")));
      assertNull(map.get(path(TableDataPath.GROUP_HEADER, "Total")));
      assertEquals(1, map.size());
   }

   @Test
   void twoToThreeAndOneToOneAreUnchanged() {
      Map<TableDataPath, Object> map = new HashMap<>();
      map.put(path(TableDataPath.HEADER, "Region", "Total"), "Sales");
      sync(rows("Region"), aggs(sum("Total"), sum("Num")), rows("Region"),
           aggs(sum("Total"), sum("Num"), sum("Qty")), map);
      assertEquals(1, map.size());
      assertEquals("Sales", map.get(path(TableDataPath.HEADER, "Region", "Total")));

      map = soleLabelled("Sales");
      sync(rows("Region"), aggs(sum("Total")), rows("Region"), aggs(sum("Total")), map);
      assertEquals(2, map.size());
   }

   @Test
   void highlightAndHyperlinkMapsFollowToo() {
      Map<TableDataPath, Object> hl = new HashMap<>();
      HighlightGroup group = new HighlightGroup();
      hl.put(path(TableDataPath.HEADER, "Total"), group);
      sync(rows("Region"), aggs(sum("Total")), rows("Region"), aggs(sum("Total"), sum("Num")), hl);
      assertSame(group, hl.get(path(TableDataPath.HEADER, "Region", "Total")));
      assertSame(group, hl.get(path(TableDataPath.HEADER, "ROW_GRAND_TOTAL", "Total")));

      Map<TableDataPath, Object> links = new HashMap<>();
      Hyperlink link = new Hyperlink("http://x", Hyperlink.WEB_LINK);
      links.put(path(TableDataPath.HEADER, "Total"), link);
      sync(rows("Region"), aggs(sum("Total")), rows("Region"), aggs(sum("Total"), sum("Num")),
           links);
      assertSame(link, links.get(path(TableDataPath.HEADER, "Region", "Total")));
   }

   @Test
   void aFormatObjectIsNotSharedBetweenTheNewKeys() {
      Map<TableDataPath, Object> map = new HashMap<>();
      VSCompositeFormat format = new VSCompositeFormat();
      map.put(path(TableDataPath.HEADER, "Total"), format);

      sync(rows("Region"), aggs(sum("Total")), rows("Region"), aggs(sum("Total"), sum("Num")), map);

      Object detail = map.get(path(TableDataPath.HEADER, "Region", "Total"));
      Object grand = map.get(path(TableDataPath.HEADER, "ROW_GRAND_TOTAL", "Total"));
      assertNotNull(detail);
      assertNotSame(detail, grand);
      assertNotSame(format, detail);
   }

   @Test
   void aSettingAlreadyOnTheNewKeyIsKeptOnlyForSetInput() {
      Map<TableDataPath, Object> map = soleLabelled("Sales");
      map.put(path(TableDataPath.HEADER, "Region", "Total"), "USER");
      sync(rows("Region"), aggs(sum("Total")), rows("Region"), aggs(sum("Total"), sum("Num")),
           map, true, false, false);
      assertEquals("USER", map.get(path(TableDataPath.HEADER, "Region", "Total")));

      map = soleLabelled("Sales");
      map.put(path(TableDataPath.HEADER, "Region", "Total"), "USER");
      sync(rows("Region"), aggs(sum("Total")), rows("Region"), aggs(sum("Total"), sum("Num")),
           map, false, false, false);
      assertEquals("Sales", map.get(path(TableDataPath.HEADER, "Region", "Total")));
   }

   /** Drill and property change keep the aggregates, so the count gate is closed. */
   @Test
   void drillAndPropertyChangeWithUnchangedAggregatesLeaveTheMapAlone() {
      for(boolean[] flags : new boolean[][] { { false, true, false }, { false, false, true } }) {
         Map<TableDataPath, Object> map = soleLabelled("Sales");
         sync(rows("Region"), aggs(sum("Total")), rows("Region"), aggs(sum("Total")), map,
              flags[0], flags[1], flags[2]);
         assertEquals(2, map.size());
      }
   }

   // -- fixture --

   private static Map<TableDataPath, Object> soleLabelled(String label) {
      Map<TableDataPath, Object> map = new HashMap<>();
      map.put(path(TableDataPath.GROUP_HEADER, "Total"), label);
      map.put(path(TableDataPath.HEADER, "Total"), label);
      return map;
   }

   private static void sync(DataRef[] orows, DataRef[] oaggs, DataRef[] nrows, DataRef[] naggs,
                            Map<TableDataPath, Object> map)
   {
      sync(orows, oaggs, nrows, new DataRef[0], naggs, map);
   }

   private static void sync(DataRef[] orows, DataRef[] oaggs, DataRef[] nrows, DataRef[] ncols,
                            DataRef[] naggs, Map<TableDataPath, Object> map)
   {
      VSUtil.syncCrosstabPath(crosstab(nrows, ncols, naggs),
                              (CrosstabVSAssemblyInfo) crosstab(orows, new DataRef[0], oaggs)
                                 .getInfo(), false, map, false, false, null);
   }

   private static void sync(DataRef[] orows, DataRef[] oaggs, DataRef[] nrows, DataRef[] naggs,
                            Map<TableDataPath, Object> map, boolean fromSetInput,
                            boolean fromDrill, boolean isPropertyChange)
   {
      VSUtil.syncCrosstabPath(crosstab(nrows, new DataRef[0], naggs),
                              (CrosstabVSAssemblyInfo) crosstab(orows, new DataRef[0], oaggs)
                                 .getInfo(), isPropertyChange, map, fromSetInput, fromDrill, null);
   }

   private static CrosstabVSAssembly crosstab(DataRef[] rows, DataRef[] cols, DataRef[] aggs) {
      CrosstabVSAssembly cross = new CrosstabVSAssembly(new Viewsheet(), "Crosstab1");
      VSCrosstabInfo info = new VSCrosstabInfo();
      info.setDesignRowHeaders(rows);
      info.setDesignColHeaders(cols);
      info.setRuntimeRowHeaders(rows);
      info.setRuntimeColHeaders(cols);
      info.setDesignAggregates(aggs);
      DataRef[] runtime = new DataRef[aggs.length];

      for(int i = 0; i < aggs.length; i++) {
         runtime[i] = (DataRef) aggs[i].clone();
      }

      info.setRuntimeAggregates(runtime);
      cross.setVSCrosstabInfo(info);
      return cross;
   }

   private static DataRef[] aggs(DataRef... aggs) {
      return aggs;
   }

   private static DataRef[] rows(String... columns) {
      return dims(columns);
   }

   private static DataRef[] cols(String... columns) {
      return dims(columns);
   }

   private static DataRef[] dims(String... columns) {
      DataRef[] dims = new DataRef[columns.length];

      for(int i = 0; i < columns.length; i++) {
         VSDimensionRef dim = new VSDimensionRef(new ColumnRef(new AttributeRef(null, columns[i])));
         dim.setGroupColumnValue(columns[i]);
         dims[i] = dim;
      }

      return dims;
   }

   private static VSAggregateRef sum(String column) {
      VSAggregateRef agg = new VSAggregateRef();
      agg.setDataRef(new ColumnRef(new AttributeRef(null, column)));
      agg.setColumnValue(column);
      agg.setFormulaValue("Sum");
      return agg;
   }

   private static TableDataPath path(int type, String... segments) {
      return new TableDataPath(-1, type, XSchema.STRING, segments);
   }
}
