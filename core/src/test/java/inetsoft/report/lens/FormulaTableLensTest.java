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

import inetsoft.report.LibManagerProvider;
import inetsoft.report.TabularSheet;
import inetsoft.report.internal.binding.FormulaHeaderInfo;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
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
      LibManagerProvider libManagerProvider = Mockito.mock(LibManagerProvider.class);
      Cluster cluster = Mockito.mock(Cluster.class);
      TabularSheet report = new TabularSheet(libManagerProvider, cluster);
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

   // Bug #77181: an initializer-less top-level let in a formula column starts
   // undefined on every row (a native var keeps its #75596 persistence)
   @Test
   void initializerlessLetStartsUndefinedEveryRow() {
      DefaultTableLens tbl = new DefaultTableLens(new Object[][] {
         {"x"}, {10}, {1}, {1}, {7}, {2}
      });
      FormulaTableLens lens = new FormulaTableLens(tbl, new String[] { "f1", "f2", "f3" },
         new String[] {
            "let r; field['x'] > 5 && (r = 'High'); r",
            "let n; n = (n || 0) + 1; n",
            "var v; field['x'] > 5 && (v = 'High'); v"
         }, new GraalJavaScriptEnv(), null);
      Object[][] expected = {
         {"x", "f1", "f2", "f3"},
         {10, "High", 1.0, "High"},
         {1, null, 1.0, "High"},
         {1, null, 1.0, "High"},
         {7, "High", 1.0, "High"},
         {2, null, 1.0, "High"},
      };

      XTableUtil.assertEquals(lens, expected);
   }

   // Bug #77321: the reporter's columns; `value` and `count` are lower-case CALC
   // functions, i.e. engine-owned globals the #77181 reset skips
   @Test
   void initializerlessLetNamedLikeCalcFunctionStartsUndefinedEveryRow() {
      DefaultTableLens tbl = new DefaultTableLens(new Object[][] {
         {"col0"}, {10}, {1}, {1}
      });
      FormulaTableLens lens = new FormulaTableLens(tbl,
         new String[] { "r_value", "r_count", "r_total" },
         new String[] {
            "let value; field['col0'] > 5 && (value = 'High'); value",
            "let count; field['col0'] > 5 && (count = 'High'); count",
            "let total; field['col0'] > 5 && (total = 'High'); total"
         }, new GraalJavaScriptEnv(), null);
      Object[][] expected = {
         {"col0", "r_value", "r_count", "r_total"},
         {10, "High", "High", "High"},
         {1, null, null, null},
         {1, null, null, null},
      };

      XTableUtil.assertEquals(lens, expected);
   }

   @Test
   public void testSerialize() throws Exception {
      String[] headers = { "f1" };
      String[] formulas = { "field['col2'] + field['col3']" };
      List<FormulaHeaderInfo> formulaHeaderInfoList = new ArrayList<>();
      FormulaHeaderInfo formulaHeaderInfo = new FormulaHeaderInfo("f1", "f1", false,
                                                                  null);
      formulaHeaderInfoList.add(formulaHeaderInfo);
      FormulaTableLens originalTable = new FormulaTableLens(XTableUtil.getDefaultTableLens(),
                                                            headers, formulas, new GraalJavaScriptEnv(),
                                                            null);
      originalTable.setFormulaHeaderInfo(formulaHeaderInfoList);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(FormulaTableLens.class, deserializedTable.getClass());
   }
}
