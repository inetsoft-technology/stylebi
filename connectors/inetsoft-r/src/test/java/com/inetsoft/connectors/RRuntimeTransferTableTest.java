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
package com.inetsoft.connectors;

import inetsoft.test.*;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPInteger;
import org.rosuda.REngine.REXPString;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RFileInputStream;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #82391: a frame returned by name is moved through write.csv, whose quotes are the only
 * trace of R's character/factor class and are stripped before the CSV reader guesses the
 * column types. Character columns of numeric-looking text ("00123") must stay text, as they
 * do when the frame is returned by value. R is mocked with the bytes write.csv emits; the
 * read is the real {@link RRuntime#transferTable(RConnection, String)}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RRuntimeTransferTableTest {
   /**
    * The reporter's frame: data.frame(zip = c("00123", "007", "42"), d = as.Date(...),
    * f = c("x", "y", "x"), b = c(TRUE, FALSE, NA)), with NA in d and b on the last row.
    */
   @Test
   void characterColumnOfDigitsStaysText() throws Exception {
      String csv = """
         "zip","d","f","b"
         "00123",2026-01-15,"x",TRUE
         "007",2026-02-01,"y",FALSE
         "42",,"x",
         """;
      XTable table = transfer(csv, new REXPInteger(new int[] { 4, 0, 2 }));

      assertEquals(String.class, table.getColType(0));
      assertEquals(List.of("00123", "007", "42"), column(table, 0));
      assertEquals(java.sql.Date.class, table.getColType(1));
      assertEquals("2026-01-15", String.valueOf(table.getObject(1, 1)));
      assertNull(table.getObject(3, 1));
      assertEquals(String.class, table.getColType(2));
      assertEquals(List.of("x", "y", "x"), column(table, 2));
      assertEquals(Boolean.class, table.getColType(3));
      assertEquals(Boolean.TRUE, table.getObject(1, 3));
      assertNull(table.getObject(3, 3));
   }

   /**
    * Factor levels and character text that look like numbers, booleans or dates stay text;
    * numeric, POSIXct and all-NA columns keep their types and nulls.
    */
   @Test
   void otherTextColumnsStayTextAndOtherColumnsKeepTheirTypes() throws Exception {
      // columns: lvl (factor), tf (chr), num (double), ts (POSIXct), cdate (chr), allna (chr)
      String csv = """
         "lvl","tf","num","ts","cdate","allna"
         "01","TRUE",2.5,2026-01-15 10:30:00,"2026-01-15",
         "02","FALSE",3,2026-02-01 08:00:00,"2026-02-01",
         """;
      XTable table = transfer(csv, new REXPInteger(new int[] { 6, 0, 1, 4, 5 }));

      assertEquals(List.of("01", "02"), column(table, 0));
      assertEquals(List.of("TRUE", "FALSE"), column(table, 1));
      assertEquals(Double.class, table.getColType(2));
      assertEquals(2.5, table.getObject(1, 2));
      assertEquals(java.sql.Timestamp.class, table.getColType(3));
      assertEquals(List.of("2026-01-15", "2026-02-01"), column(table, 4));
      assertEquals(String.class, table.getColType(5));
      assertNull(table.getObject(1, 5));
      assertNull(table.getObject(2, 5));
   }

   /** A 0-row frame still types its character column as text. */
   @Test
   void emptyFrameKeepsTextColumn() throws Exception {
      XTable table = transfer("\"zip\",\"n\"\n", new REXPInteger(new int[] { 2, 0 }));

      assertTrue(table.moreRows(0));
      assertFalse(table.moreRows(1));
      assertEquals(String.class, table.getColType(0));
   }

   /**
    * A character matrix returned by name gets no indexes from R (integer(0)), so every column
    * is detected as before; an index outside the file is dropped instead of failing the read.
    */
   @Test
   void noOrOutOfRangeIndexesDetectTypesAsBefore() throws Exception {
      String matrix = "\"V1\",\"V2\"\n\"1\",\"2\"\n\"3\",\"4\"\n";
      XTable table = transfer(matrix, new REXPInteger(new int[0]));

      assertEquals(Integer.class, table.getColType(0));
      assertEquals(List.of(1, 3), column(table, 0));

      table = transfer(matrix, new REXPInteger(new int[] { 2, 0, 2, 5, -1 }));

      assertEquals(List.of("1", "3"), column(table, 0));
      assertEquals(List.of(2, 4), column(table, 1));
   }

   /** If R does not return the indexes, the frame is read with type detection, as before. */
   @Test
   void failedIndexQueryFallsBackToTypeDetection() throws Exception {
      String csv = "\"zip\"\n\"00123\"\n\"007\"\n";

      XTable table = transfer(csv, null);
      assertEquals(List.of(123, 7), column(table, 0));

      table = transfer(csv, new REXPString("not indexes"));
      assertEquals(List.of(123, 7), column(table, 0));
   }

   private static XTable transfer(String csv, REXP textColumns) throws Exception {
      RConnection connection = mock(RConnection.class);
      when(connection.eval(anyString())).thenReturn(textColumns);
      ByteArrayInputStream bytes = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
      RFileInputStream input = mock(RFileInputStream.class, delegatesTo(bytes));
      when(connection.openFile(anyString())).thenReturn(input);

      return RRuntime.transferTable(connection, "p");
   }

   private static List<Object> column(XTable table, int col) {
      List<Object> values = new ArrayList<>();

      for(int r = table.getHeaderRowCount(); table.moreRows(r); r++) {
         values.add(table.getObject(r, col));
      }

      return values;
   }
}
