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
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.FormTableLens;
import inetsoft.report.composition.FormTableRow;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import inetsoft.util.script.graal.ScriptValueConverter;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression for #81904: the form row passed to a table's form script must expose
 * rowState and rowIndex to GraalJS ({@code fr.rowState}, {@code 'rowState' in fr}), while
 * column names, length and the out-of-range index behavior are unchanged.
 * <p>
 * TableFormRowScriptable is a private inner class, so it is built reflectively (with a
 * real TableVSAScriptable as the outer instance, whose table the row reads) rather than widening its
 * visibility.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableFormRowScriptableTest {
   @BeforeEach
   void setup() throws Exception {
      ctx = Context.create("js");

      DefaultTableLens table = new DefaultTableLens(new Object[][] {
         { "name", "qty", "note" },
         { "a", 1, "x" }
      });

      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      TableVSAssembly assembly = new TableVSAssembly();
      assembly.getVSAssemblyInfo().setName("Table1");
      vs.addAssembly(assembly);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getID()).thenReturn("vs1");
      when(box.getViewsheet()).thenReturn(vs);
      when(box.getVSTableLens("Table1", false)).thenReturn(new VSTableLens(table));

      TableVSAScriptable outer = new TableVSAScriptable(box);
      outer.setAssembly("Table1");
      // disabled form: getFormRow only initializes the outer's table array, no form lens
      outer.setDisableForm(true);
      outer.getFormRow(0);
      Map<String, Integer> map = new HashMap<>();
      map.put("name", 0);
      map.put("qty", 1);
      map.put("note", 2);

      FormTableRow formRow = new FormTableRow(new Object[] { "a", 7, null }, 5);
      formRow.setRowState(FormTableRow.CHANGED);

      Class<?> cls = Class.forName(
         TableDataVSAScriptable.class.getName() + "$TableFormRowScriptable");
      Constructor<?> ctor = cls.getDeclaredConstructors()[0];
      ctor.setAccessible(true);
      row = ctor.newInstance(outer, table, map, formRow);
   }

   @AfterEach
   void teardown() {
      ctx.close();
   }

   private Value eval(String expr) {
      ctx.getBindings("js").putMember("fr", ScriptValueConverter.toGuest(row));
      return ctx.eval("js", expr);
   }

   @Test
   void rowStateAndRowIndexAreReadable() {
      assertEquals(FormTableRow.CHANGED, eval("fr.rowState").asInt());
      assertEquals(5, eval("fr.rowIndex").asInt());
      assertTrue(eval("'rowState' in fr").asBoolean());
      assertTrue(eval("'rowIndex' in fr").asBoolean());
   }

   @Test
   void existingBehaviorIsPreserved() {
      assertEquals("a", eval("fr.name").asString());
      assertEquals(7, eval("fr.qty").asInt());
      assertEquals(3, eval("fr.length").asInt());
      assertEquals("a", eval("fr[0]").asString());
      assertTrue(eval("fr.note === null").asBoolean(), "empty cell is null");
      assertTrue(eval("fr[2] === null").asBoolean(), "empty cell by index is null");
      assertTrue(eval("fr[3] === undefined").asBoolean());
      assertTrue(eval("fr[-1] === undefined").asBoolean());
      assertFalse(eval("'nope' in fr").asBoolean());
      assertTrue(eval("fr.nope === undefined").asBoolean());
   }

   /**
    * The rows that a script gets from {@code Table1.getFormRows()} and
    * {@code Table1.getFormRows(state)} must be script rows like the one getFormRow(i) returns:
    * rowState, rowIndex, length and the column names are readable, not the raw Java object's
    * methods. The call goes through the table's script function, as in a viewsheet script.
    */
   @Test
   void getFormRowsElementsAreScriptRows() throws Exception {
      DefaultTableLens table = new DefaultTableLens(new Object[][] {
         { "name", "qty", "note" },
         { "a", 1, "x" },
         { "b", 2, "y" }
      });
      FormTableLens ftable = new FormTableLens(table);
      ftable.rows()[2].setRowState(FormTableRow.CHANGED);

      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      TableVSAssembly assembly = new TableVSAssembly();
      assembly.getVSAssemblyInfo().setName("Table1");
      vs.addAssembly(assembly);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getID()).thenReturn("vs1");
      when(box.getViewsheet()).thenReturn(vs);
      when(box.getVSTableLens("Table1", false)).thenReturn(new VSTableLens(table));
      when(box.getFormTableLens("Table1")).thenReturn(ftable);

      TableVSAScriptable scriptable = new TableVSAScriptable(box);
      scriptable.setAssembly("Table1");
      // the script engine a viewsheet script runs in, with its host access policy
      GraalJavaScriptEngine engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());

      try {
         engine.put("Table1", scriptable);
         assertEquals(3.0, run(engine, "Table1.getFormRows().length"));
         assertEquals(true, run(engine, "'rowState' in Table1.getFormRows()[1]"));
         assertEquals(true, run(engine, "'rowIndex' in Table1.getFormRows()[1]"));
         assertEquals((double) FormTableRow.OLD, run(engine, "Table1.getFormRows()[1].rowState"));
         assertEquals(1.0, run(engine, "Table1.getFormRows()[1].rowIndex"));
         assertEquals((double) FormTableRow.CHANGED, run(engine, "Table1.getFormRows()[2].rowState"));
         assertEquals(2.0, run(engine, "Table1.getFormRows()[2].rowIndex"));
         assertEquals(3.0, run(engine, "Table1.getFormRows()[1].length"));
         assertEquals("a", run(engine, "Table1.getFormRows()[1].name"));
         assertEquals(2.0, run(engine, "Table1.getFormRows()[2].qty"));
         assertEquals("y", run(engine, "Table1.getFormRows()[2][2]"));

         // getFormRows(state), by number and by name
         assertEquals(1.0, run(engine, "Table1.getFormRows(2).length"));
         assertEquals((double) FormTableRow.CHANGED, run(engine, "Table1.getFormRows(2)[0].rowState"));
         assertEquals(2.0, run(engine, "Table1.getFormRows(2)[0].rowIndex"));
         assertEquals("b", run(engine, "Table1.getFormRows(2)[0].name"));
         assertEquals(true, run(engine, "'rowState' in Table1.getFormRows(2)[0]"));
         assertEquals(2.0, run(engine, "Table1.getFormRows('old').length"));
         assertEquals((double) FormTableRow.OLD, run(engine, "Table1.getFormRows('old')[0].rowState"));
         assertEquals(0.0, run(engine, "Table1.getFormRows(3).length"));
      }
      finally {
         engine.close();
      }

      // the Java caller gets the rows wrapped in an array-shaped scope (Bug #80033)
      Object javaRows = scriptable.getFormRows(null);
      assertEquals(3L, ((inetsoft.util.script.graal.ScriptArrayScope) javaRows).getArraySize());
   }

   private static Object run(GraalJavaScriptEngine engine, String script) throws Exception {
      return engine.exec(engine.compile(script), null, null);
   }

   private Context ctx;
   private Object row;
}
