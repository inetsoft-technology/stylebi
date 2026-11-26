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
package inetsoft.report.script;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.script.XTableArray;
import inetsoft.util.script.graal.ScriptValueConverter;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for #81786: reading a row or cell index outside a table from a script
 * must give JS {@code undefined} (as in 13.8 / Rhino), not {@code null}, so
 * {@code while(t[i] !== undefined)} terminates. A real null cell stays null.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableArrayOutOfRangeScriptTest {
   // header row + 2 data rows, 3 columns, one real null cell at [1][1]
   private static XTable table() {
      return new DefaultTableLens(new Object[][] {
         { "col1", "col2", "col3" },
         { "a", null, 5.0 },
         { "b", 3, 10.0 }
      });
   }

   @BeforeEach
   void setup() {
      ctx = Context.create("js");
   }

   @AfterEach
   void teardown() {
      ctx.close();
   }

   private Value eval(Object array, String expr) {
      ctx.getBindings("js").putMember("t", ScriptValueConverter.toGuest(array));
      return ctx.eval("js", expr);
   }

   private void check(Object array) {
      assertTrue(eval(array, "t[4] === undefined").asBoolean(), "row past the end");
      assertTrue(eval(array, "t[3] === undefined").asBoolean(), "row == size");
      assertTrue(eval(array, "t[-1] === undefined").asBoolean(), "negative row");
      assertTrue(eval(array, "t[1][5] === undefined").asBoolean(), "column past the end");
      assertTrue(eval(array, "t[1][9] === undefined").asBoolean(), "column far past the end");
      assertTrue(eval(array, "t[1][1] === null").asBoolean(), "in-range null cell stays null");
      assertEquals("a", eval(array, "t[1][0]").asString());
      assertEquals(
         3, eval(array, "var i = 0; while(t[i] !== undefined && i < 20) i++; i").asInt(),
         "scan loop ends at the row count");
   }

   @Test
   void tableArrayOutOfRangeIsUndefined() {
      check(new TableArray(table()));
   }

   @Test
   void xTableArrayOutOfRangeIsUndefined() {
      check(new XTableArray(table()));
   }

   @Test
   void tableRowPreviousRowStillWorks() {
      TableArray arr = new TableArray(table());
      // field[-1] on a row is the previous row, not undefined
      assertFalse(eval(arr, "t[2][-1] === undefined").asBoolean());
      assertEquals("a", eval(arr, "t[2][-1][0]").asString());
   }

   @Test
   void javaContractStillReturnsNull() {
      assertNull(new TableArray(table()).getArrayElement(7));
      assertNull(new XTableArray(table()).getArrayElement(7));
   }

   private Context ctx;
}
