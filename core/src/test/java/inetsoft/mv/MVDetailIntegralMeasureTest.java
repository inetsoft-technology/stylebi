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

import java.math.BigDecimal;
import java.util.*;
import inetsoft.report.filter.ConcatFormula;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #82257: Concat over a measure column of a detail (non-aggregated) MV must print the same
 * text as the live query ("20", not "20.0") when the data is integral but the column is declared
 * double.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVDetailIntegralMeasureTest {
   @Test
   void integerDataConcatsWithoutFraction() throws Exception {
      run(20, 9, 7);
      assertEquals(Integer.class, type);
      assertEquals("20,9,7", concat);
   }

   @Test
   void doubleDataKeepsFraction() throws Exception {
      run(26.0, 3.0);
      assertEquals(Double.class, type);
      assertEquals("26.0,3.0", concat);
   }

   @Test
   void mixedDataStaysDouble() throws Exception {
      run(1, 2.5);
      assertEquals(Double.class, type);
      assertEquals("1.0,2.5", concat);
   }

   @Test
   void scaleZeroBigDecimalBecomesIntegral() throws Exception {
      run(new BigDecimal("20"), new BigDecimal("9"));
      assertEquals(Integer.class, type);
      assertEquals("20,9", concat);
   }

   @Test
   void scaleTwoBigDecimalStaysNonIntegral() throws Exception {
      run(new BigDecimal("20.00"), new BigDecimal("9.00"));
      assertEquals(Double.class, type);
   }

   @Test
   void measureColumnHelper() {
      assertTrue(MVCreatorUtil.isMeasureColumn(new MVColumn(column(null, "A", "double"), false)));
      assertFalse(MVCreatorUtil.isMeasureColumn(new MVColumn(column(null, "A", "double"), true)));
      MVColumn base = new MVColumn(column(null, "A", "date"), true);
      assertFalse(MVCreatorUtil.isMeasureColumn(
         new DateMVColumn(base, column(null, "A", "date"), 1)));
   }

   private void run(Object... qtys) throws Exception {
      List<MVColumn> cols = new ArrayList<>();
      cols.add(new MVColumn(column(null, "PAID", "string"), true));
      cols.add(new MVColumn(column(null, "QTY", "double"), false));
      MVDef def = new MVDef();
      def.container = new MVContainer(cols, null);
      Object[][] rows = new Object[qtys.length + 1][];
      rows[0] = new Object[] { "PAID", "QTY" };

      for(int i = 0; i < qtys.length; i++) {
         rows[i + 1] = new Object[] { "P" + i, qtys[i] };
      }

      MVBuilder builder = new MVBuilder(new DefaultTableLens(rows), def, false, null);
      List<SubMV> subs = new ArrayList<>();
      List<BlockFile> files = new ArrayList<>();
      Iterator<SubMV> it = builder.getSubMVs();

      try {
         while(it.hasNext()) {
            SubMV sub = it.next();
            BlockFile file = new CacheBlockFile("MVDetailIntegral-" + UUID.randomUUID() + ".dat");
            files.add(file);
            sub.write(file);
            subs.add(SubMV.get(file));
         }

         MV mv = builder.getMV();
         type = mv.getTypes()[1];
         GroupRef[] groups = { new GroupRef(column(null, "PAID", "string")),
                               new GroupRef(column(null, "QTY", "double")) };
         boolean[] order = { true, true };
         AggregateRef[] aggrs = {};
         MVQuery query = new MVQuery(mv, groups, order, aggrs, aggrs, null);
         query.setDetail(true);
         MVJob job = mock(MVJob.class);
         when(job.getTable()).thenReturn(mock(TableAssembly.class));
         query.setJob(job);

         for(int i = 0; i < subs.size(); i++) {
            SubMVQuery sq = new SubMVQuery(groups, order, aggrs, null);
            sq.setBlockIndex(i);
            query.add("b" + i, sq.execute(subs.get(i)));
         }

         query.complete(true);
         XTable data = query.getData();
         ConcatFormula f = new ConcatFormula();

         for(int r = 1; data.moreRows(r); r++) {
            // the rule of FormulaAgent.getAgent
            if(data.getColType(1) == Double.class) {
               f.addValue(data.getDouble(r, 1));
            }
            else {
               f.addValue(data.getObject(r, 1));
            }
         }

         concat = String.valueOf(f.getResult());
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

   private Class<?> type;
   private String concat;
}
