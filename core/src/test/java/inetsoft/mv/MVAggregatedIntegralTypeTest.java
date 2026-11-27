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
package inetsoft.mv;

import inetsoft.mv.MVDef.MVContainer;
import inetsoft.mv.data.*;
import inetsoft.mv.fs.BlockFile;
import inetsoft.mv.fs.internal.CacheBlockFile;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #82319: a Sum over an integer column of an aggregated (sub) MV table is declared double by
 * the formula, but the live query returns the integer the database gives it. The MV stored and
 * returned it as a double, so a rotated table named its columns "12101.00" instead of "12101" and
 * the viewsheet columns bound to the live names were missing.
 *
 * The test builds an aggregated MV whose aggregate column (declared double) holds the values of
 * the build query, writes and reads the blocks, and re-reads the column with MAX through
 * SubMVQuery and MVQuery, as a sub MV does.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVAggregatedIntegralTypeTest {
   @Test
   void integerSumOfAggregatedMVStaysInteger() throws Exception {
      Map<String, Object> result = maxByGroup(true, 12101, 12024, 20000001);

      assertEquals(Integer.class, storedType);
      assertEquals(12101, result.get("Y"));
      assertEquals(12024, result.get("N"));
      assertEquals(20000001, result.get("X"));
      // the name a rotated table gives the value is the one of the live query
      assertEquals("12101", AssetUtil.format(result.get("Y")));
   }

   @Test
   void longSumOfAggregatedMVStaysLong() throws Exception {
      Map<String, Object> result = maxByGroup(true, 12101L, 12024L, 5000000000L);

      assertEquals(Long.class, storedType);
      assertEquals(12101L, result.get("Y"));
      assertEquals(5000000000L, result.get("X"));
      assertEquals("12101", AssetUtil.format(result.get("Y")));
   }

   /**
    * A sum computed in memory is a double in the live query too, it stays double.
    */
   @Test
   void doubleSumOfAggregatedMVStaysDouble() throws Exception {
      Map<String, Object> result = maxByGroup(true, 12101.0, 12024.5, 3.0);

      assertEquals(Double.class, storedType);
      assertEquals(12101.0, result.get("Y"));
      assertEquals(12024.5, result.get("N"));
      assertEquals("12101.00", AssetUtil.format(result.get("Y")));
   }

   /**
    * A detail (not aggregated) MV keeps the integral type of the data of a measure column
    * declared double, as the live query returns it (Bug #82257).
    */
   @Test
   void detailMVKeepsTheDeclaredType() throws Exception {
      Map<String, Object> result = maxByGroup(false, 12101, 12024, 3);

      assertEquals(Integer.class, storedType);
      assertEquals(12101, result.get("Y"));
   }

   /**
    * A group column of the aggregated MV table is not an aggregate output.
    */
   @Test
   void groupColumnIsNotAnAggregateOutput() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "Query1");
      ws.addAssembly(table);
      AggregateInfo ainfo = new AggregateInfo();
      ainfo.addGroups(new GroupRef[] { new GroupRef(column("ORDERS1", "DISCOUNT", "double")) });
      ainfo.addAggregate(new AggregateRef(column("ORDERS1", "ORDER_ID", "integer"),
                                          AggregateFormula.SUM));
      table.setAggregateInfo(ainfo);

      MVDef def = mock(MVDef.class);
      when(def.getWorksheet()).thenReturn(ws);
      when(def.getMVTable()).thenReturn("Query1");

      assertFalse(MVCreatorUtil.isAggregateOutput(
         def, new MVColumn(column(null, "DISCOUNT", "double"), false)));
      assertTrue(MVCreatorUtil.isAggregateOutput(
         def, new MVColumn(column(null, "ORDER_ID", "double"), false)));
      assertFalse(MVCreatorUtil.isAggregateOutput(
         def, new MVColumn(column(null, "ORDER_ID", "double"), true)));
   }

   // build the mv (PAID dimension, ORDER_ID measure declared double like Sum(ORDER_ID)) and
   // return MAX(ORDER_ID) by PAID read through the stored blocks
   private Map<String, Object> maxByGroup(boolean aggregated, Object... sums) throws Exception {
      List<MVColumn> cols = new ArrayList<>();
      cols.add(new MVColumn(column(null, "PAID", "string"), true));
      cols.add(new MVColumn(column(null, "ORDER_ID", "double"), false));
      MVDef def = new MVDef();
      def.container = new MVContainer(cols, null);

      String[] paids = { "Y", "N", "X" };
      Object[][] rows = new Object[sums.length + 1][];
      rows[0] = new Object[] { "PAID", "ORDER_ID" };

      for(int i = 0; i < sums.length; i++) {
         rows[i + 1] = new Object[] { paids[i], sums[i] };
      }

      MVBuilder builder = new MVBuilder(new DefaultTableLens(rows), def, aggregated, null);
      List<SubMV> subs = new ArrayList<>();
      List<BlockFile> files = new ArrayList<>();
      Iterator<SubMV> it = builder.getSubMVs();

      try {
         while(it.hasNext()) {
            SubMV sub = it.next();
            BlockFile file = new CacheBlockFile(
               "MVAggregatedIntegralTypeTest-" + UUID.randomUUID() + ".dat");
            files.add(file);
            sub.write(file);
            subs.add(SubMV.get(file));
         }

         MV mv = builder.getMV();
         storedType = mv.getTypes()[1];

         GroupRef[] groups = { new GroupRef(column(null, "PAID", "string")) };
         boolean[] order = { true };
         AggregateRef[] aggrs = {
            new AggregateRef(column(null, "ORDER_ID", "double"), AggregateFormula.MAX) };
         MVQuery query = new MVQuery(mv, groups, order, aggrs, aggrs, null);

         for(int i = 0; i < subs.size(); i++) {
            SubMVQuery sq = new SubMVQuery(groups, order, aggrs, null);
            sq.setBlockIndex(i);
            query.add("b" + i, sq.execute(subs.get(i)));
         }

         query.complete(true);
         XTable data = query.getData();
         Map<String, Object> result = new HashMap<>();

         for(int r = 1; data.moreRows(r); r++) {
            result.put((String) data.getObject(r, 0), data.getObject(r, 1));
         }

         return result;
      }
      finally {
         for(BlockFile file : files) {
            SubMV.removeMap(file);
            file.delete();
         }
      }
   }

   private static ColumnRef column(String entity, String name, String type) {
      ColumnRef ref = new ColumnRef(new AttributeRef(entity, name));
      ref.setDataType(type);
      return ref;
   }

   private Class<?> storedType;
}
