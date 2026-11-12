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
package inetsoft.uql.erm;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The static helpers that split a SQL expression from the script expression embedded as a
 * {@code /*script ... script*}{@code /} comment (Test Case #80957, vscalc matrix row X1). Both are pure
 * string functions, but ExpressionRef initializes from the configuration, so the class has
 * a Spring context.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ExpressionRefScriptExpressionTest {
   @Test
   @DisplayName("X1a: a script expression is only read from a sql expression")
   void x1a_notSql_returnsTheInput() {
      assertEquals("a /*script x script*/", ExpressionRef.getScriptExpression(false,
         "a /*script x script*/"));
      assertEquals("a /*script x script*/", ExpressionRef.getSQLExpression(false,
         "a /*script x script*/"));
      assertNull(ExpressionRef.getScriptExpression(true, null));
      assertNull(ExpressionRef.getSQLExpression(true, null));
   }

   @Test
   @DisplayName("X1b: without a marker the sql expression is the whole text")
   void x1b_noMarker_returnsTheInput() {
      assertEquals("a + b", ExpressionRef.getScriptExpression(true, "a + b"));
      assertEquals("a + b", ExpressionRef.getSQLExpression(true, "a + b"));
   }

   @Test
   @DisplayName("X1c: the script between the markers is trimmed")
   void x1c_script_isTheTextBetweenTheMarkers() {
      assertEquals("x", ExpressionRef.getScriptExpression(true, "a /*script x script*/"));
      assertEquals("a + 1", ExpressionRef.getSQLExpression(true, "a + 1 /*script x script*/"));
   }

   @Test
   @DisplayName("X1d: the markers are matched in any case")
   void x1d_upperCaseMarkers() {
      assertEquals("x", ExpressionRef.getScriptExpression(true, "a /*SCRIPT x SCRIPT*/"));
   }

   @Test
   @DisplayName("X1e: a prefix without a suffix, or a suffix before the prefix, is not a script")
   void x1e_incompleteMarkers_returnTheInput() {
      assertEquals("a /*script x", ExpressionRef.getScriptExpression(true, "a /*script x"));
      assertEquals("a script*/ b", ExpressionRef.getScriptExpression(true, "a script*/ b"));
      assertEquals("a /*script x", ExpressionRef.getSQLExpression(true, "a /*script x"));
   }

   @Disabled("Bug #81214, mismatch X1f")
   @Test
   @DisplayName("X1f: overlapping markers do not throw")
   void x1f_overlappingMarkers_doNotThrow() {
      assertDoesNotThrow(() -> ExpressionRef.getScriptExpression(true, "/*script*/"));
      assertDoesNotThrow(() -> ExpressionRef.getScriptExpression(true, "a /*script*/ b"));
   }
}
