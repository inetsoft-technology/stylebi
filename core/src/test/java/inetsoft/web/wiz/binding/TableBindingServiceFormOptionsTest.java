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
package inetsoft.web.wiz.binding;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78823: a details re-set on a Form table left every column a plain {@code ColumnRef}
 * (ColumnSelection.setAttribute is a silent no-op for a same-named ref), so a later
 * set_column_options was reported ok but never stored, and any option already set was lost.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableBindingServiceFormOptionsTest {
   private static ColumnRef plain(String name) {
      return new ColumnRef(new AttributeRef(null, name));
   }

   private static TableVSAssembly formTable(ColumnRef... refs) {
      TableVSAssembly table = new TableVSAssembly(new Viewsheet(), "Table1");
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      ColumnSelection cols = new ColumnSelection();

      for(ColumnRef ref : refs) {
         cols.addAttribute(ref);
      }

      info.setColumnSelection(cols);
      info.setForm(true);
      return table;
   }

   @Test
   void plainColumnsBecomeFormRefsAfterADetailsWrite() {
      TableVSAssembly table = formTable(plain("A"), plain("B"));

      TableBindingService.restoreFormRefInvariant(table, "details", Map.of());

      ColumnSelection cols =
         ((TableVSAssemblyInfo) table.getVSAssemblyInfo()).getColumnSelection();
      assertEquals(2, cols.getAttributeCount());
      assertInstanceOf(FormRef.class, cols.getAttribute(0));
      assertInstanceOf(FormRef.class, cols.getAttribute(1));
      assertEquals("A", cols.getAttribute(0).getName());
      assertEquals("B", cols.getAttribute(1).getName());
   }

   @Test
   void existingColumnOptionsSurviveTheReSetByColumnName() {
      FormRef a = FormRef.toFormRef(plain("A"));
      IntegerColumnOption integer = new IntegerColumnOption(100, 0, "bad", true);
      a.setOption(integer);
      TableVSAssembly table = formTable(a, FormRef.toFormRef(plain("B")));
      Map<String, ColumnOption> captured = TableBindingService.captureFormOptions(table);

      // what applyBinding leaves behind: the same names as plain refs, plus a new column
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      ColumnSelection rebuilt = new ColumnSelection();
      rebuilt.addAttribute(plain("A"));
      rebuilt.addAttribute(plain("C"));
      info.setColumnSelection(rebuilt);

      TableBindingService.restoreFormRefInvariant(table, "details", captured);

      ColumnSelection cols = info.getColumnSelection();
      assertSame(integer, ((FormRef) cols.getAttribute(0)).getOption());
      assertInstanceOf(FormRef.class, cols.getAttribute(1));
      assertNotSame(integer, ((FormRef) cols.getAttribute(1)).getOption());
   }

   @Test
   void nonDetailsShelfAndNonFormTableAreLeftAlone() {
      TableVSAssembly table = formTable(plain("A"));
      TableBindingService.restoreFormRefInvariant(table, "rows", Map.of());
      assertFalse(((TableVSAssemblyInfo) table.getVSAssemblyInfo()).getColumnSelection()
                     .getAttribute(0) instanceof FormRef);

      ((TableVSAssemblyInfo) table.getVSAssemblyInfo()).setForm(false);
      TableBindingService.restoreFormRefInvariant(table, "details", Map.of());
      assertFalse(((TableVSAssemblyInfo) table.getVSAssemblyInfo()).getColumnSelection()
                     .getAttribute(0) instanceof FormRef);
   }
}
