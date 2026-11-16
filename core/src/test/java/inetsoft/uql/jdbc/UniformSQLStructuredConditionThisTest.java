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
package inetsoft.uql.jdbc;

import inetsoft.test.*;
import inetsoft.uql.util.expr.TokenMgrError;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80137, UniformSQL.genCondition used a private replace() that advanced past a match by
 * the replacement's length instead of the matched text's length while substituting the legacy
 * StructuredSQL placeholder "this" with the real column name. A shorter column name left a
 * corrupted condition ("chis" instead of the column), a column name containing "this" made the
 * loop never terminate, and a column name longer than the remaining text threw a
 * StringIndexOutOfBoundsException that was swallowed, silently dropping the condition.
 *
 * Fix: genCondition now uses the token-based StructuredSQL.substitute() (now static) instead,
 * and its catch is widened to Exception | TokenMgrError with a logged warning instead of a
 * silent null. substitute() itself is also made line-aware, since routing genCondition through
 * it would otherwise newly expose a pre-existing bug where its token offsets (JavaCC column
 * numbers, which restart at 1 on every line) were used as absolute string offsets.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLStructuredConditionThisTest {
   // ---- StructuredSQL.substitute(), the token-based replacement genCondition now uses ----

   @Test
   void shrinkingNameIsNotCorrupted() throws Exception {
      assertEquals("c > 5 and c < 10",
                   StructuredSQL.substitute("this > 5 and this < 10", "this", "c"));
   }

   @Test
   void nameContainingThisTerminates() throws Exception {
      assertEquals("this_x > 5", StructuredSQL.substitute("this > 5", "this", "this_x"));
   }

   @Test
   void nameLongerThanConditionDoesNotThrow() throws Exception {
      assertEquals("a_very_long_column_name > 5",
                   StructuredSQL.substitute("this > 5", "this", "a_very_long_column_name"));
   }

   @Test
   void multiLineConditionSubstitutesAcrossLineBreak() throws Exception {
      assertEquals("c > 5\nand c < 10",
                   StructuredSQL.substitute("this > 5\nand this < 10", "this", "c"));
   }

   @Test
   void malformedConditionThrowsTokenMgrError() {
      assertThrows(TokenMgrError.class,
                   () -> StructuredSQL.substitute("this > \"unterminated", "this", "c"));
   }

   // ---- end to end, through the actual UniformSQL(StructuredSQL) load path ----

   @Test
   void shrinkingColumnNameConditionIsNotCorrupted() {
      XFilterNode where = convert("c", "this > 5 and this < 10").getWhere();
      String rendered = where.toString();

      assertEquals(1, where.getChildCount(), rendered);
      assertFalse(rendered.contains("chis"), rendered);
      assertTrue(rendered.contains("c > 5"), rendered);
      assertTrue(rendered.contains("c < 10"), rendered);
   }

   @Test
   void columnNameContainingThisConditionTerminatesAndIsCorrect() {
      XFilterNode where = convert("this_x", "this > 5").getWhere();

      assertEquals(1, where.getChildCount(), where.toString());
      assertEquals("this_x > 5", where.getChild(0).toString());
   }

   @Test
   void malformedConditionIsDroppedNotThrown() {
      XFilterNode where = convert("c", "this > \"unterminated").getWhere();

      assertEquals(0, where.getChildCount(), where.toString());
   }

   private static UniformSQL convert(String column, String condition) {
      StructuredSQL structured = new StructuredSQL();
      structured.addTable("t");
      structured.setCondition(column, condition);
      return new UniformSQL(structured);
   }
}
