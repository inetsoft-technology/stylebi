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
package inetsoft.report.lens;

import inetsoft.report.TabularSheet;
import inetsoft.test.TestSerializeUtils;
import inetsoft.test.XTableUtil;
import inetsoft.uql.XTable;
import inetsoft.util.script.JavaScriptEnv;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
class FormulaTableLensTest {
   @Test
   void testFormula() {
      DefaultTableLens tbl1 = new DefaultTableLens(new Object[][] {
         {"col1", "col2", "col3"},
         {"a", 1, 5.0},
         {"b", 3, 10.0},
         {"b", 1, 2.5},
         {"c", 1, 3.0}
      });
      String[] headers = { "f1" };
      String[] formulas = { "field['col2'] + field['col3']" };
//      SreeEnv.setProperty("license.key", "D000-6B0-DER-0000C30010AF-F2F31FE87256");
      TabularSheet report = new TabularSheet();
      FormulaTableLens joined = new FormulaTableLens(tbl1, headers, formulas, report);
      //XTableUtil.printTableAsJava(joined);
      Object[][] expected = {
         {"col1", "col2", "col3", "f1"},
         {"a", 1, 5.0, 6.0},
         {"b", 3, 10.0, 13.0},
         {"b", 1, 2.5, 3.5},
         {"c", 1, 3.0, 4.0},
      };

      XTableUtil.assertEquals(joined, expected);
   }

   @Test
   public void testSerialize() throws Exception {
      String[] headers = { "f1" };
      String[] formulas = { "field['col2'] + field['col3']" };
      FormulaTableLens originalTable = new FormulaTableLens(XTableUtil.getDefaultTableLens(),
                                                            headers, formulas, new JavaScriptEnv(),
                                                            null);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);

      Assertions.assertEquals(FormulaTableLens.class, deserializedTable.getClass());
   }
}
