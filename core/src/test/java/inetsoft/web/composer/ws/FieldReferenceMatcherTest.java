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
package inetsoft.web.composer.ws;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.erm.AttributeRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78234 (WBS-104/105): the one token matcher shared by the rename rewrite and the remove
 * pre-check, across every syntax the script engine accepts for a table's own column.
 */
@Tag("core")
class FieldReferenceMatcherTest {
   private static final Set<String> AMOUNT = Set.of("Amount", "T.Amount");

   @ParameterizedTest
   @ValueSource(strings = {
      "field['Amount'] * 2",
      "field[\"Amount\"] * 2",
      "field.Amount * 2",
      "field.Amount",
      "field['T.Amount'] > 10 ? 'x' : 'y'",
      "Math.round(field.Amount.valueOf())",
   })
   void recognisesEveryFieldReferenceSyntax(String expr) {
      assertTrue(FieldReferenceMatcher.references(expr, AMOUNT), expr);
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "field.Amount2 * 2",
      "field['AmountRate']",
      "Other.Amount * 2",
      "Other['Amount']",
      "'field.Amount' + 1",
      "\"field['Amount']\"",
      "myfield.Amount",
   })
   void ignoresLookalikes(String expr) {
      assertFalse(FieldReferenceMatcher.references(expr, AMOUNT), expr);
   }

   @Test
   void renameKeepsEachQuotingStyle() {
      Map<String, String> renames = Map.of("Amount", "Amt", "T.Amount", "T.Amt");

      assertEquals("field['Amt'] * 2",
         FieldReferenceMatcher.rename("field['Amount'] * 2", renames));
      assertEquals("field[\"Amt\"] * 2",
         FieldReferenceMatcher.rename("field[\"Amount\"] * 2", renames));
      assertEquals("field.Amt * 2", FieldReferenceMatcher.rename("field.Amount * 2", renames));
      assertEquals("field['T.Amt'] + 1",
         FieldReferenceMatcher.rename("field['T.Amount'] + 1", renames));
   }

   @Test
   void renameLeavesStringLiteralsAndLookalikesAlone() {
      Map<String, String> renames = Map.of("Amount", "Amt");
      String expr = "'field.Amount: ' + field.Amount + field.Amount2 + Other.Amount";

      assertEquals("'field.Amount: ' + field.Amt + field.Amount2 + Other.Amount",
         FieldReferenceMatcher.rename(expr, renames));
   }

   @Test
   void renameTurnsADotReferenceIntoABracketWhenTheNewNameIsNotAnIdentifier() {
      assertEquals("field['Net Amount'] * 2",
         FieldReferenceMatcher.rename("field.Amount * 2", Map.of("Amount", "Net Amount")));
   }

   @Test
   void renameReturnsTheSameInstanceWhenNothingMatches() {
      String expr = "field [ 'Other' ] * 2";
      assertSame(expr, FieldReferenceMatcher.rename(expr, Map.of("Amount", "Amt")));
   }

   /**
    * Join where both sides have "Amount" and the right side's is aliased "Amount_1":
    * field['Amount'] reads the LEFT column, so it must not count as a reference to the right one
    * -- the right one is addressed as Amount_1 or R.Amount. The left one keeps its bare name.
    */
   @Test
   void removeScanKeysAnAliasedJoinColumnOnItsOutputName() {
      ColumnRef left = new ColumnRef(new AttributeRef("L", "Amount"));
      ColumnRef right = new ColumnRef(new AttributeRef("R", "Amount"));
      right.setAlias("Amount_1");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(left);
      cs.addAttribute(right);
      TableAssembly join = mock(TableAssembly.class);
      when(join.getColumnSelection()).thenReturn(cs);

      Set<String> rightNames = WorksheetControllerService.fieldNames(join, right);
      Set<String> leftNames = WorksheetControllerService.fieldNames(join, left);

      assertEquals(Set.of("Amount_1", "R.Amount"), rightNames);
      assertFalse(FieldReferenceMatcher.references("field['Amount'] * 2", rightNames));
      assertTrue(FieldReferenceMatcher.references("field['Amount_1'] * 2", rightNames));
      assertEquals(Set.of("Amount", "L.Amount"), leftNames);
      assertTrue(FieldReferenceMatcher.references("field['Amount'] * 2", leftNames));
   }

   /** An aliased column whose attribute nobody else outputs still matches the attribute. */
   @Test
   void removeScanKeepsAnAliasedColumnsAttributeWhenUnambiguous() {
      ColumnRef col = new ColumnRef(new AttributeRef("T", "Amount"));
      col.setAlias("Revenue");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(col);
      TableAssembly table = mock(TableAssembly.class);
      when(table.getColumnSelection()).thenReturn(cs);

      assertEquals(Set.of("Revenue", "T.Amount", "Amount"),
         WorksheetControllerService.fieldNames(table, col));
   }
}
