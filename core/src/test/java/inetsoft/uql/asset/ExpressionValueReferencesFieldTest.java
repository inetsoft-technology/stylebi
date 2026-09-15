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
package inetsoft.uql.asset;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ExpressionValue#referencesField()} (WBS-042) -- the detector that both
 * {@code PreAssetQuery}'s SQL-mergeability gate and {@code AssetConditionGroup}'s per-row
 * evaluation use to recognize a JAVASCRIPT-typed condition value that needs a real row instead of
 * a once-per-query scalar resolution.
 */
@Tag("core")
class ExpressionValueReferencesFieldTest {
   private static ExpressionValue expr(String type, String expression) {
      ExpressionValue eval = new ExpressionValue();
      eval.setType(type);
      eval.setExpression(expression);
      return eval;
   }

   @Test
   void javascriptWithBracketFieldReference_true() {
      assertTrue(expr(ExpressionValue.JAVASCRIPT, "field['REGION_ID']").referencesField());
   }

   @Test
   void javascriptWithDotFieldReference_true() {
      assertTrue(expr(ExpressionValue.JAVASCRIPT, "field.REGION_ID").referencesField());
   }

   @Test
   void javascriptWithFieldReferenceInsideLargerExpression_true() {
      assertTrue(expr(ExpressionValue.JAVASCRIPT,
         "field['REGION_ID'] + '-' + parameter.suffix").referencesField());
   }

   @Test
   void javascriptWithoutFieldReference_false() {
      assertFalse(expr(ExpressionValue.JAVASCRIPT, "parameter.minRevenue * 1.1").referencesField());
   }

   @Test
   void javascriptWithFieldAsUnrelatedSubstring_false() {
      // "myfield[...]" is not the `field` identifier -- must not false-positive on a substring.
      assertFalse(expr(ExpressionValue.JAVASCRIPT, "myfield['x']").referencesField());
   }

   @Test
   void sqlTypeWithFieldBracketSyntax_false() {
      // The SQL branch's own textual substitution (PreAssetQuery.parseFieldExpression) already
      // handles field[...] correctly and must stay completely unaffected by this detector --
      // referencesField() is gated to JAVASCRIPT only.
      assertFalse(expr(ExpressionValue.SQL, "field['REGION_ID']").referencesField());
   }

   @Test
   void nullExpression_false() {
      assertFalse(expr(ExpressionValue.JAVASCRIPT, null).referencesField());
   }

   @Test
   void nullType_false() {
      assertFalse(expr(null, "field['REGION_ID']").referencesField());
   }
}
