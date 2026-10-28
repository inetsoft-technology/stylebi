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
package inetsoft.mv.data;

import inetsoft.mv.MVDef;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.util.Tool;
import inetsoft.util.swap.XSwappableObjectList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79507: sub blocks arrive in dictionary index order (always case sensitive). With the
 * default case insensitive compare, {@link MVQuery#add} must re-sort the converted rows so that
 * the merge (which assumes {@link MVRow2.RowComparator} order) produces case insensitive
 * ordered, non-duplicated groups across several blocks.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MVQueryConvertRowCaseTest {
   @Test
   void mergedGroupsAreCaseInsensitiveOrdered() throws Exception {
      withCaseSensitive(false, () -> {
         // blocks overlap on "Fast Transportation" and "NY"; "a"/"A" are separate groups (A first)
         List<String> merged = run(
            new String[][] {
               { "NY", "Northwest", "FL", "Fast Transportation", "a" },
               { "NV", "NY", "Fast Transportation", "A", "FISGA" } });
         assertEquals(Arrays.asList("A", "a", "Fast Transportation", "FISGA", "FL", "Northwest",
                                    "NV", "NY"), merged);
      });
   }

   @Test
   void nullGroupStaysFirstAndOnlyOnce() throws Exception {
      withCaseSensitive(false, () -> {
         List<String> merged = run(
            new String[][] { { null, "b", "B", "C" }, { "c", null, "a" } });
         assertEquals(Arrays.asList(null, "a", "B", "b", "C", "c"), merged);
      });
   }

   @Test
   void caseSensitiveConfigurationKeepsDictionaryOrder() throws Exception {
      withCaseSensitive(true, () -> {
         List<String> merged = run(
            new String[][] {
               { "NY", "Northwest", "FL", "Fast Transportation" },
               { "NV", "NY", "FISGA" } });
         List<String> expected = new ArrayList<>(
            Arrays.asList("FISGA", "FL", "Fast Transportation", "NV", "NY", "Northwest"));
         assertEquals(expected, merged);
      });
   }

   @Test
   void detailQueryKeepsStoredRowOrder() throws Exception {
      withCaseSensitive(false, () -> {
         // stored (dictionary index) order of the sub block is the case sensitive order
         List<String> rows = runDetail(new String[] { "NY", "Northwest", "FL", "Fast Transportation" });
         assertEquals(Arrays.asList("FL", "Fast Transportation", "NY", "Northwest"), rows);
      });
   }

   /**
    * Run one block through a detail MVQuery and return the converted rows of the block, which
    * must stay in the stored order.
    */
   private static List<String> runDetail(String[] values) throws Exception {
      GroupRef group = new GroupRef(new ColumnRef(new AttributeRef(null, "City")));
      MV mv = mock(MV.class);
      mv.types = new Class[] { String.class };
      when(mv.getDef()).thenReturn(mock(MVDef.class));
      when(mv.indexOfHeader(anyString(), anyInt())).thenAnswer(
         inv -> MVDef.getMVHeader(group.getDataRef()).equals(inv.getArgument(0)) ? 0 : -1);
      XDimDictionary dict = new XDimDictionary();
      dict.setDataType(String.class);

      for(String v : values) {
         dict.addValue(v);
      }

      dict.complete();
      when(mv.getDictionary(0, 0)).thenReturn(dict);
      MVQuery query = new MVQuery(mv, new GroupRef[] { group }, new boolean[] { true },
                                  new AggregateRef[0], new AggregateRef[0], null);
      query.setDetail(true);
      XSwappableObjectList<MVRow> rows = new XSwappableObjectList<>(MVRow.class);

      for(int i = 0; i < dict.size(); i++) {
         rows.add(new MVRow(new long[] { i }, new double[0]));
      }

      rows.complete();
      SubTableBlock block = mock(SubTableBlock.class);
      XSwappableObjectList<MVRow>[] holder = new XSwappableObjectList[] { rows };
      when(block.getBlockIndex()).thenReturn(0);
      when(block.getRowCount()).thenAnswer(inv -> holder[0].size());
      when(block.getRow(anyInt())).thenAnswer(inv -> holder[0].get(inv.getArgument(0)));
      org.mockito.Mockito.doAnswer(inv -> {
         holder[0] = inv.getArgument(0);
         return null;
      }).when(block).setRows(org.mockito.ArgumentMatchers.any());
      // the detail merge itself is not under test, convertRow runs before it
      query.add("block0", block);
      List<String> result = new ArrayList<>();

      for(int r = 0; r < holder[0].size(); r++) {
         result.add((String) ((MVRow2) holder[0].get(r)).groups2[0]);
      }

      return result;
   }

   /**
    * Run the given blocks (each one the distinct values of one sub block) through a group only
    * MVQuery and return the merged group values in order.
    */
   private static List<String> run(String[][] blocks) throws Exception {
      GroupRef group = new GroupRef(new ColumnRef(new AttributeRef(null, "City")));
      MV mv = mock(MV.class);
      mv.types = new Class[] { String.class };
      when(mv.getDef()).thenReturn(mock(MVDef.class));
      when(mv.indexOfHeader(anyString(), anyInt())).thenAnswer(
         inv -> MVDef.getMVHeader(group.getDataRef()).equals(inv.getArgument(0)) ? 0 : -1);

      List<XDimDictionary> dicts = new ArrayList<>();

      for(int b = 0; b < blocks.length; b++) {
         // dictionary (case sensitive sorted, null first) like XDimDictionary.complete
         XDimDictionary dict = new XDimDictionary();
         dict.setDataType(String.class);

         for(String v : blocks[b]) {
            dict.addValue(v);
         }

         dict.complete();
         dicts.add(dict);
         when(mv.getDictionary(0, b)).thenReturn(dict);
      }

      MVQuery query = new MVQuery(mv, new GroupRef[] { group }, new boolean[] { true },
                                  new AggregateRef[0], new AggregateRef[0], null);

      for(int b = 0; b < blocks.length; b++) {
         XDimDictionary dict = dicts.get(b);

         // sub block rows in dictionary index order
         XSwappableObjectList<MVRow> rows = new XSwappableObjectList<>(MVRow.class);
         int size = dict.size();

         for(int i = 0; i < size; i++) {
            rows.add(new MVRow(new long[] { i }, new double[0]));
         }

         rows.complete();
         SubTableBlock block = mock(SubTableBlock.class);
         XSwappableObjectList<MVRow>[] holder = new XSwappableObjectList[] { rows };
         when(block.getBlockIndex()).thenReturn(b);
         when(block.getRowCount()).thenAnswer(inv -> holder[0].size());
         when(block.getRow(anyInt())).thenAnswer(inv -> holder[0].get(inv.getArgument(0)));
         org.mockito.Mockito.doAnswer(inv -> {
            holder[0] = inv.getArgument(0);
            return null;
         }).when(block).setRows(org.mockito.ArgumentMatchers.any());
         query.add("block" + b, block);
      }

      java.lang.reflect.Field field = MVQuery.class.getDeclaredField("table");
      field.setAccessible(true);
      MergedTableBlock table = (MergedTableBlock) field.get(query);
      table.complete();
      List<String> result = new ArrayList<>();

      for(int r = 0; r < table.getRowCount(); r++) {
         result.add((String) ((MVRow2) table.getRow(r)).groups2[0]);
      }

      return result;
   }

   private interface ThrowingRunnable {
      void run() throws Exception;
   }

   private static void withCaseSensitive(boolean cs, ThrowingRunnable run) throws Exception {
      String old = SreeEnv.getProperty("string.compare.casesensitive");
      SreeEnv.setProperty("string.compare.casesensitive", "" + cs);
      Tool.invalidateCaseSensitive();

      try {
         run.run();
      }
      finally {
         if(old == null) {
            SreeEnv.remove("string.compare.casesensitive");
         }
         else {
            SreeEnv.setProperty("string.compare.casesensitive", old);
         }

         Tool.invalidateCaseSensitive();
      }
   }
}
