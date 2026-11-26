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
package inetsoft.web.composer.ws.dialog;

import inetsoft.web.composer.ws.dialog.ExpressionReferenceChecker.Kind;
import inetsoft.web.composer.ws.dialog.ExpressionReferenceChecker.Problem;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81670: saving a calculated column must warn about a missing column, a self reference
 * and a cycle between calculated columns.
 */
@Tag("core")
class ExpressionReferenceCheckerTest {
   private static final Set<String> COLS = Set.of("id", "b");

   @Test
   void missingColumn() {
      Problem p = ExpressionReferenceChecker.check("a", "field['nope'] + 1", COLS, Map.of());
      assertEquals(Kind.MISSING, p.kind());
      assertEquals("nope", p.first());
   }

   @Test
   void selfReference() {
      Problem p = ExpressionReferenceChecker.check("a1", "field[\"a1\"] + 1", Set.of("id"),
                                                   Map.of());
      assertEquals(Kind.SELF, p.kind());
      assertEquals("a1", p.first());
   }

   @Test
   void cycleBetweenTwoFields() {
      Problem p = ExpressionReferenceChecker.check(
         "a", "field['b'] + 1", COLS, Map.of("b", "field['a'] + 1"));
      assertEquals(Kind.CYCLE, p.kind());
      assertEquals("a", p.first());
      assertEquals("b", p.second());
   }

   @Test
   void indirectCycle() {
      Problem p = ExpressionReferenceChecker.check(
         "a", "field['b']", Set.of("id"), Map.of("b", "field['c']", "c", "field['a']"));
      assertEquals(Kind.CYCLE, p.kind());
   }

   @Test
   void validReferencesAndNoReferencesPass() {
      assertNull(ExpressionReferenceChecker.check("a", "field['id'] + field['b']", COLS,
                                                  Map.of("b", "field['id']")));
      assertNull(ExpressionReferenceChecker.check("a", "1 + 1", COLS, Map.of()));
      assertNull(ExpressionReferenceChecker.check("a", null, COLS, Map.of()));
   }

   // Bug #81656: SQL option expressions use the same field['x'] syntax
   @Test
   void sqlExpressionsAreChecked() {
      assertEquals(Kind.MISSING, ExpressionReferenceChecker.check(
         "a", "field['nope']", COLS, Map.of()).kind());
      assertEquals(Kind.SELF, ExpressionReferenceChecker.check(
         "a1", "field['a1']+1", Set.of("id"), Map.of()).kind());
      assertEquals(Kind.CYCLE, ExpressionReferenceChecker.check(
         "a", "field['b']+1", COLS, Map.of("b", "field['a']+1")).kind());
      assertNull(ExpressionReferenceChecker.check(
         "c", "field['id'] + field['b']", COLS, Map.of("b", "field['id']")));
   }
}
