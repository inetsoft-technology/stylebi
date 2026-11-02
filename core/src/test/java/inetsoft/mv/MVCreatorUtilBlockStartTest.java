/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.mv.data.MVColumnInfo;
import inetsoft.mv.data.XDimDictionary;
import inetsoft.mv.data.XDynamicTable;
import inetsoft.uql.XTable;
import inetsoft.test.*;
import inetsoft.util.ThreadPool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Date;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * {@link MVCreatorUtil#addRowToDictionaries} is called once per MV block with that block's
 * {@code [start, end)} row range, and every column kind must read only that range. The non-numeric
 * measure and the date-convert loops used to start at row 0 (Bug #79400): the reads grew
 * quadratically with the block count, the block dictionary / date range absorbed the earlier
 * blocks, and an overflown dictionary's raw values (indexed by {@code row - baseRow}) were shifted.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVCreatorUtilBlockStartTest {
   private static final int ROWS = 10;
   private static final int START = 6;

   @Test
   void stringMeasureReadsOnlyTheBlockRows() {
      XTable lens = stringLens();
      XDimDictionary[] dicts = {new XDimDictionary()};
      dicts[0].setDataType(String.class);
      int[] minRowRead = {Integer.MAX_VALUE};
      when(lens.getObject(anyInt(), anyInt())).thenAnswer(inv -> {
         int r = inv.getArgument(0);
         minRowRead[0] = Math.min(minRowRead[0], r);
         return "v" + r;
      });

      run(() -> MVCreatorUtil.addRowToDictionaries(lens, START, ROWS, new int[0], new int[] {0},
         dicts, new MVColumnInfo[] {new MVColumnInfo()}, new boolean[] {false},
         new boolean[] {false}), dicts);
      dicts[0].complete();

      assertEquals(START, minRowRead[0], "rows before the block start must not be read");
      TreeSet<Object> values = new TreeSet<>();

      for(int i = 0; i < dicts[0].size(); i++) {
         values.add(dicts[0].getValue(i));
      }

      assertEquals(ROWS - START, values.size());
      assertTrue(values.stream().allMatch(v -> Integer.parseInt(((String) v).substring(1)) >= START),
                 "block dictionary holds values of earlier blocks: " + values);
   }

   @Test
   void overflownMeasureRawValuesHoldOnlyTheBlockRows() {
      XTable lens = stringLens();
      when(lens.getObject(anyInt(), anyInt())).thenAnswer(inv -> "v" + inv.getArgument(0));
      XDimDictionary dict = new XDimDictionary();
      dict.setDataType(String.class);
      dict.setOverflow(true);
      dict.setBaseRow(START);
      XDimDictionary[] dicts = {dict};

      run(() -> MVCreatorUtil.addRowToDictionaries(lens, START, ROWS, new int[0], new int[] {0},
         dicts, new MVColumnInfo[] {new MVColumnInfo()}, new boolean[] {false},
         new boolean[] {false}), dicts);
      dict.complete();

      // raw values are indexed by row - baseRow, so they must hold exactly this block's rows
      // (in-memory DimValueList.get() is only usable after a write/read round trip)
      assertEquals(ROWS - START, dict.size(), "raw values must start at the block start row");

      for(int row = START; row < ROWS; row++) {
         assertTrue(dict.indexOf("v" + row, row) < dict.size(), "row " + row);
      }
   }

   @Test
   void convertDateRangeCoversOnlyTheBlockRows() {
      XDynamicTable lens = mock(XDynamicTable.class);
      when(lens.moreRows(anyInt())).thenAnswer(inv -> (int) inv.getArgument(0) < ROWS);
      when(lens.getBaseColCount()).thenReturn(5);
      // row r holds the date 1000 * (r + 1)
      when(lens.getBaseObject(anyInt(), anyInt()))
         .thenAnswer(inv -> new Date(1000L * ((int) inv.getArgument(0) + 1)));
      when(lens.getObject(anyInt(), anyInt()))
         .thenAnswer(inv -> new Date(1000L * ((int) inv.getArgument(0) + 1)));
      when(lens.getRowCount()).thenReturn(ROWS);
      MVColumnInfo info = new MVColumnInfo();

      run(() -> MVCreatorUtil.addRowToDictionaries(lens, START, ROWS, new int[0], new int[] {0},
         new XDimDictionary[1], new MVColumnInfo[] {info}, new boolean[] {false},
         new boolean[] {true}), null);

      assertEquals(1000L * (START + 1), ((Date) info.getMin()).getTime());
      assertEquals(1000L * ROWS, ((Date) info.getMax()).getTime());
   }

   private static XTable stringLens() {
      XTable lens = mock(XTable.class);
      when(lens.moreRows(anyInt())).thenAnswer(inv -> (int) inv.getArgument(0) < ROWS);
      when(lens.getRowCount()).thenReturn(ROWS);
      return lens;
   }

   private static void run(Runnable action, XDimDictionary[] ignored) {
      try(MockedStatic<ThreadPool> pool = mockStatic(ThreadPool.class)) {
         pool.when(() -> ThreadPool.addOnDemand(any())).thenAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
         });
         action.run();
      }
   }
}
