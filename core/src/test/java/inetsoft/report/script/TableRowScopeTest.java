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
package inetsoft.report.script;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WBS-042 regression: {@link TableRowScope#hasMember(String)} must agree with
 * {@link TableRowScope#getMember(String)} about the {@code basename} identifier (e.g.
 * {@code "field"}) -- {@code getMember} already special-cases it (returns the wrapped
 * {@link TableRow}), but {@code hasMember} did not, so GraalJS's {@code ScopeProxy} (which asks
 * {@code hasMember} first to decide whether an unqualified identifier resolves in this scope,
 * see {@code inetsoft.util.script.graal.ScopeProxy#hasMember}) reported {@code field} as absent
 * and threw {@code ReferenceError: field is not defined} for any script referencing it through
 * this scope as the exec root -- exactly the mechanism {@code AssetConditionGroup}'s new per-row
 * field-expression evaluation (and, previously, {@code FormulaTableLens}) relies on.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableRowScopeTest {
   private static final Object[][] DATA = {
      { "name", "id" },
      { "a", 1 },
      { "b", 2 },
   };

   @Test
   void hasMember_reportsBasenameAsPresent() {
      TableRow row = new TableRow(new DefaultTableLens(DATA), 1);
      TableRowScope scope = new TableRowScope(row, "field");

      assertTrue(scope.hasMember("field"),
         "hasMember must agree with getMember, which already returns `row` for the basename");
      assertSame(row, scope.getMember("field"));
   }

   @Test
   void hasMember_columnNameStillResolves() {
      TableRow row = new TableRow(new DefaultTableLens(DATA), 1);
      TableRowScope scope = new TableRowScope(row, "field");

      assertTrue(scope.hasMember("name"), "a real column name must still be reported present");
   }

   @Test
   void hasMember_unknownIdentifierIsAbsent() {
      TableRow row = new TableRow(new DefaultTableLens(DATA), 1);
      TableRowScope scope = new TableRowScope(row, "field");

      assertFalse(scope.hasMember("notAColumnOrBasename"),
         "an identifier that is neither the basename nor a column must remain absent, " +
         "no false positives introduced by the fix");
   }
}
