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
package inetsoft.report.composition.execution;

import inetsoft.test.*;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #81110: a dynamic value that embeds a multi-select variable inside surrounding text
 * ({@code pre $(Check1) post}) must keep the previously resolved value while it is still
 * selected, as it does for a bare {@code $(Check1)}. The previous runtime value carries the
 * surrounding text, so it has to be stripped before it is matched against the selection.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VariableFormulaMultiSelectTest {
   @Test
   void keepsPreviousValueWithSurroundingText() throws Exception {
      DynamicValue dval = new DynamicValue("pre $(Check1) post");
      dval.setRValue("pre b post");
      assertEquals("pre b post", evaluate(dval, "a", "b"));
   }

   @Test
   void keepsPreviousValueInsideFormula() throws Exception {
      DynamicValue dval = new DynamicValue("Sum($(Check1))");
      dval.setRValue("Sum(B)");
      assertEquals("Sum(B)", evaluate(dval, "A", "B"));
   }

   @Test
   void keepsPreviousNonStringValue() throws Exception {
      DynamicValue dval = new DynamicValue("n=$(Check1)!");
      dval.setRValue("n=2!");
      assertEquals("n=2!", evaluate(dval, 1, 2));
   }

   @Test
   void fallsBackToFirstWhenPreviousNoLongerSelected() throws Exception {
      DynamicValue dval = new DynamicValue("pre $(Check1) post");
      dval.setRValue("pre c post");
      assertEquals("pre a post", evaluate(dval, "a", "b"));
   }

   @Test
   void fallsBackToFirstWithoutPreviousValue() throws Exception {
      assertEquals("pre a post", evaluate(new DynamicValue("pre $(Check1) post"), "a", "b"));
   }

   private static Viewsheet viewsheet(Object... selected) {
      Viewsheet vs = new Viewsheet();
      CheckBoxVSAssembly check = new CheckBoxVSAssembly(vs, "Check1");
      check.setSelectedObjects(selected);
      vs.addAssembly(check);
      return vs;
   }

   private static Object evaluate(DynamicValue dval, Object... selected) throws Exception {
      Viewsheet vs = viewsheet(selected);
      ViewsheetSandbox box =
         new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, null);
      box.executeDynamicValue(dval, null, "Text1", null);
      return dval.getRValue();
   }
}
