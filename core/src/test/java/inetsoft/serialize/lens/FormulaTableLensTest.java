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

package inetsoft.serialize.lens;

import inetsoft.report.TabularSheet;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.test.SreeHome;
import inetsoft.test.XTableUtil;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@SreeHome()
public class FormulaTableLensTest {
   @Test
   void testSerialize() throws Exception {
      DefaultTableLens tbl1 = new DefaultTableLens(new Object[][]{
         { "col1", "col2", "col3" },
         { "a", 1, 5.0 },
         { "b", 3, 10.0 },
         { "b", 1, 2.5 },
         { "c", 1, 3.0 }
      });
      String[] headers = { "f1" };
      String[] formulas = { "field['col2'] + field['col3']" };
      TabularSheet report = new TabularSheet();
      FormulaTableLens originalTable = new FormulaTableLens(tbl1, headers, formulas, report);
      originalTable.moreRows(XTable.EOT); // need XSwappableTable.complete() to be called, otherwise it will hang
      Object[][] expected = {
         { "col1", "col2", "col3", "f1" },
         { "a", 1, 5.0, 6.0 },
         { "b", 3, 10.0, 13.0 },
         { "b", 1, 2.5, 3.5 },
         { "c", 1, 3.0, 4.0 },
         };

      // Serialize to a byte array
      byte[] serializedData;
      try(ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
          ObjectOutputStream out = new ObjectOutputStream(byteOut))
      {
         out.writeObject(originalTable);
         serializedData = byteOut.toByteArray();
      }

      assertNotNull(serializedData);
      assertTrue(serializedData.length > 0);

      // Deserialize from byte array
      FormulaTableLens deserializedTable;

      try(ByteArrayInputStream byteIn = new ByteArrayInputStream(serializedData);
          ObjectInputStream in = new ObjectInputStream(byteIn))
      {

         deserializedTable = (FormulaTableLens) in.readObject();
      }

      assertNotNull(deserializedTable);

      // Ensure the deserialized object has the expected data
      XTableUtil.assertEquals(deserializedTable, expected);
   }
}
