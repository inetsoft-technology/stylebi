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

package inetsoft.uql.viewsheet.internal;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

// Bug #76951: renaming a calc field must rewrite the other calc fields that reference it.
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSUtilRenameCalcFieldReferenceTest {
   private static String rename(String expression) {
      return VSUtil.renameCalcFieldReference(expression, "Net Sales", "Net Revenue");
   }

   @Test
   void rewritesAPerRowReference() {
      assertEquals("field['Net Revenue'] * 2", rename("field['Net Sales'] * 2"));
   }

   @Test
   void rewritesAnAggregateReference() {
      assertEquals(
         "(field['Sum(Product:Total)'] - field['Sum(Net Revenue)']) / field['Sum(Product:Total)']",
         rename("(field['Sum(Product:Total)'] - field['Sum(Net Sales)']) / field['Sum(Product:Total)']"));
   }

   @Test
   void matchesTheFormulaNameCaseInsensitively() {
      assertEquals("field['sum(Net Revenue)']", rename("field['sum(Net Sales)']"));
   }

   @Test
   void keepsTheBracketedSpelling() {
      assertEquals("field['Max([Net Revenue])']", rename("field['Max([Net Sales])']"));
   }

   @Test
   void rewritesEitherColumnOfATwoColumnFormula() {
      assertEquals("field['Correlation(Total, Net Revenue)']",
         rename("field['Correlation(Total, Net Sales)']"));
      assertEquals("field['Correlation([Net Revenue], [Total])']",
         rename("field['Correlation([Net Sales], [Total])']"));
   }

   @Test
   void leavesTheNOfAnNFormulaAlone() {
      assertEquals("field['NthLargest(Net Revenue, 2)']",
         rename("field['NthLargest(Net Sales, 2)']"));
   }

   @Test
   void rewritesADoubleQuotedAccessor() {
      assertEquals("field[\"Sum(Net Revenue)\"]", rename("field[\"Sum(Net Sales)\"]"));
   }

   @Test
   void rewritesAnOldNameThatContainsAComma() {
      assertEquals("field['Sum(Net Revenue)']",
         VSUtil.renameCalcFieldReference("field['Sum(Sales, Net)']", "Sales, Net", "Net Revenue"));
   }

   @Test
   void leavesAFieldWhoseNameOnlyStartsWithTheOldName() {
      String expression = "field['Net Sales 2'] + field['Sum(Net Sales 2)']";
      assertSame(expression, rename(expression));
   }

   @Test
   void leavesAStringLiteralThatIsNotAFieldAccessor() {
      String expression = "field['Total'] > 0 ? 'Net Sales' : 'Sum(Net Sales)'";
      assertSame(expression, rename(expression));
   }

   @Test
   void leavesAFunctionCallThatIsNotAnAggregateFormula() {
      String expression = "field['Foo(Net Sales)']";
      assertSame(expression, rename(expression));
   }

   @Test
   void matchesTheOldNameCaseSensitively() {
      String expression = "field['Sum(net sales)']";
      assertSame(expression, rename(expression));
   }
}
