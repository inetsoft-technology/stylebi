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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

   // Bug #78597: an aggregate tag is resolved case-insensitively at runtime, so a case-different
   // column name is a reference too, unless it is another field's exact name.
   @Test
   void matchesACaseDifferentAggregateName() {
      assertEquals("field['Sum(Net Revenue)']", rename("field['Sum(net sales)']"));
      assertTrue(VSUtil.referencesCalcField("field['Sum(gst)']", "Gst", List.of()));
   }

   @Test
   void leavesACaseDifferentAggregateNameThatIsAnotherExistingField() {
      String expression = "field['Sum(gst)']";
      assertSame(expression,
         VSUtil.renameCalcFieldReference(expression, "Gst", "Tax", List.of("gst")));
      assertFalse(VSUtil.referencesCalcField(expression, "Gst", List.of("gst")));
   }

   @Test
   void treatsAnAmbiguousCaseDifferentAggregateNameAsAReference() {
      String expression = "field['Sum(GST)']";
      assertSame(expression,
         VSUtil.renameCalcFieldReference(expression, "Gst", "Tax", List.of("gst")));
      assertTrue(VSUtil.referencesCalcField(expression, "Gst", List.of("gst")));
   }

   // Bug #78597: the row scope ignores a table prefix (text up to the last '.') in field['...'].
   @Test
   void rewritesATablePrefixedReferenceKeepingThePrefix() {
      assertEquals("field['Order Model.VatR'] * 2",
         VSUtil.renameCalcFieldReference("field['Order Model.Vat'] * 2", "Vat", "VatR"));
      assertEquals("field[\"Order Model.VatR\"]",
         VSUtil.renameCalcFieldReference("field[\"Order Model.Vat\"]", "Vat", "VatR"));
      assertTrue(VSUtil.referencesCalcField("field['Order Model.Vat']", "Vat", List.of()));
   }

   @Test
   void rewritesATablePrefixedAggregateKeepingThePrefix() {
      assertEquals("field['Order Model.Sum(VatR)']",
         VSUtil.renameCalcFieldReference("field['Order Model.Sum(Vat)']", "Vat", "VatR"));
   }

   @Test
   void doesNotHijackADottedBaseColumn() {
      String expression = "field['X.Vat']";
      assertSame(expression,
         VSUtil.renameCalcFieldReference(expression, "Vat", "VatR", List.of("X.Vat")));
      assertFalse(VSUtil.referencesCalcField(expression, "Vat", List.of("X.Vat")));
   }

   @Test
   void doesNotMatchATablePrefixedCaseDifferentName() {
      String expression = "field['Order Model.vat']";
      assertSame(expression, VSUtil.renameCalcFieldReference(expression, "Vat", "VatR"));
      assertFalse(VSUtil.referencesCalcField(expression, "Vat", List.of()));
   }

   @Test
   void doesNotMatchAPrefixedLongerName() {
      assertFalse(VSUtil.referencesCalcField("field['Order Model.Vat2']", "Vat", List.of()));
      assertFalse(VSUtil.referencesCalcField("field['Order Model.xVat']", "Vat", List.of()));
   }

   // Bug #78441: the dot form and a case-different name are references too.
   private static String renameTax(String expression, String... others) {
      return VSUtil.renameCalcFieldReference(expression, "Tax", "Duty", List.of(others));
   }

   @Test
   void rewritesTheDotForm() {
      assertEquals("field.Duty * 2", renameTax("field.Tax * 2"));
      assertEquals("field.Duty.toFixed(2)", renameTax("field . Tax.toFixed(2)").replace(" ", ""));
   }

   @Test
   void turnsTheDotFormIntoABracketWhenTheNewNameIsNotAnIdentifier() {
      assertEquals("field['Net Revenue'] * 2",
         VSUtil.renameCalcFieldReference("field.Tax * 2", "Tax", "Net Revenue"));
      assertEquals("field['default'] * 2",
         VSUtil.renameCalcFieldReference("field.Tax * 2", "Tax", "default"));
      assertEquals("field[\"O'Brien\"]",
         VSUtil.renameCalcFieldReference("field.Tax", "Tax", "O'Brien"));
   }

   @Test
   void dotFormDoesNotMatchLongerNamesOtherObjectsOrLiterals() {
      String expression = "field.Taxes + myfield.Tax + 'field.Tax' + \"field.Tax\" // field.Tax";
      assertSame(expression, renameTax(expression));
   }

   @Test
   void rewritesAWhitespaceBracketAndTheDotFormTogether() {
      assertEquals("field[ 'Duty' ] + field[\"Duty\"] + field.Duty",
         renameTax("field[ 'Tax' ] + field[\"Tax\"] + field.Tax"));
   }

   @Test
   void matchesACaseDifferentNameWhenNothingElseDoes() {
      assertEquals("field['Fee'] + field.Fee",
         VSUtil.renameCalcFieldReference("field['duty'] + field.duty", "Duty", "Fee"));
   }

   @Test
   void leavesACaseDifferentNameThatIsAnotherExistingFieldExactly() {
      String expression = "field['duty'] + field.duty";
      assertSame(expression,
         VSUtil.renameCalcFieldReference(expression, "Duty", "Fee", List.of("duty")));
   }

   @Test
   void leavesAnAmbiguousCaseDifferentName() {
      String expression = "field['DUTY']";
      assertSame(expression,
         VSUtil.renameCalcFieldReference(expression, "Duty", "Fee", List.of("duty")));
      assertTrue(VSUtil.referencesCalcField(expression, "Duty", List.of("duty")));
   }

   @Test
   void referencesCalcFieldSeesEveryFormOfAReference() {
      assertTrue(VSUtil.referencesCalcField("field.Tax * 2", "Tax", List.of()));
      assertTrue(VSUtil.referencesCalcField("field['tax']", "Tax", List.of()));
      assertTrue(VSUtil.referencesCalcField("field['Sum(Tax)']", "Tax", List.of()));
      assertFalse(VSUtil.referencesCalcField("field.Taxes", "Tax", List.of()));
      assertFalse(VSUtil.referencesCalcField("field['tax']", "Tax", List.of("tax")));
   }

   // Bug #78649: references inside literals and comments are not references.
   private static String renameVat(String expression) {
      return VSUtil.renameCalcFieldReference(expression, "Vat", "VatR", List.of());
   }

   private static boolean refsVat(String expression) {
      return VSUtil.referencesCalcField(expression, "Vat", List.of());
   }

   private static void assertIgnored(String expression) {
      assertFalse(refsVat(expression), expression);
      assertSame(expression, renameVat(expression), expression);
   }

   private static void assertRenamed(String expected, String expression) {
      assertTrue(refsVat(expression), expression);
      assertEquals(expected, renameVat(expression));
   }

   @Test
   void ignoresAccessorsInStringLiteralsAndComments() {
      assertIgnored("field['ORDER_ID'] + \" field['Vat'] \"");
      assertIgnored("field['ORDER_ID'] + ' field[\"Vat\"] '");
      assertIgnored("field['ORDER_ID'] + \" field['Sum(Vat)'] \"");
      assertIgnored("/* field['Vat'] */ field['ORDER_ID']");
      assertIgnored("// field['Vat']\nfield['ORDER_ID']");
      assertIgnored("field['ORDER_ID'] + \"a\\\" field['Vat']\"");
      assertIgnored("field['ORDER_ID'] + `field['Vat']`");
      assertIgnored("field['ORDER_ID'] + `field.Vat`");
   }

   @Test
   void onlyRewritesTheRealReferenceNextToALiteral() {
      assertRenamed("field['VatR'] + \" field['Vat'] \"", "field['Vat'] + \" field['Vat'] \"");
      assertRenamed("\" field['Vat'] \" + field['VatR']", "\" field['Vat'] \" + field['Vat']");
   }

   @Test
   void stillSeesReferencesAroundTemplateLiterals() {
      assertRenamed("`don't` + field['VatR']", "`don't` + field['Vat']");
      assertRenamed("`it's ${field['VatR']}`", "`it's ${field['Vat']}`");
      assertRenamed("`a\"b` + field['VatR'] + \"c\"", "`a\"b` + field['Vat'] + \"c\"");
      assertRenamed("`${`don't`}` + field['VatR']", "`${`don't`}` + field['Vat']");
      assertRenamed("`${'}'}` + field['VatR']", "`${'}'}` + field['Vat']");
      assertRenamed("`x ${field['VatR']}`", "`x ${field['Vat']}`");
      assertRenamed("`a\\`b' ${field['VatR']}`", "`a\\`b' ${field['Vat']}`");
      assertRenamed("`${ {a: 1}.a }don't` + field['VatR']", "`${ {a: 1}.a }don't` + field['Vat']");
      assertRenamed("`don't ${field['VatR']} it's` + field['VatR']",
                    "`don't ${field['Vat']} it's` + field['Vat']");
   }

   @Test
   void seesTheDotFormAfterATemplateLiteral() {
      assertTrue(refsVat("`don't` + field.Vat"));
      assertTrue(refsVat("`it's ${field.Vat}`"));
   }

   @Test
   void unterminatedLiteralFallsBackToUnmaskedMatching() {
      // the regex literal's quote desynchronizes the scan; a real reference must not be dropped
      assertTrue(refsVat("/'/.test(field['ORDER_ID']) ? field['Vat'] : 0"));
      assertTrue(refsVat("`unclosed ${field['Vat']}"));
   }
}
