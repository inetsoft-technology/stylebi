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
import inetsoft.uql.VariableTable;
import inetsoft.uql.XConstants;
import inetsoft.uql.XNode;
import inetsoft.uql.jdbc.util.VarSQL;
import inetsoft.uql.util.XUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77619, a parameter holding NULL_VALUE, EMPTY_STRING or NULL_STRING was only rewritten
 * when it was the right operand of a comparison. As the left operand ($(p) = a.col), in an
 * IN list (a.col IN ($(p))) or as a BETWEEN bound it was bound as the text of the sentinel.
 * <p>
 * A parameter that isn't rewritten is bound here the way VarSQL binds it, as the text of its
 * value, so the rows show a missed rewrite. The table has a row holding the text NULL_VALUE.
 * <p>
 * Bug #77707, a right operand that only starts with a parameter, e.g. $(p) || 'x', was
 * replaced as if it were the parameter. Bug #77709, a parameter in a string literal,
 * '$(p)', was bound as the text of the sentinel, and so was the subject of a BETWEEN.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 SQLHelperNotEqualJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XUtilSentinelOperandTest {
   private static final String NULL_VALUE = XConstants.CONDITION_NULL_VALUE;
   private static final String EMPTY_STRING = XConstants.CONDITION_EMPTY_STRING;
   private static final String NULL_STRING = XConstants.CONDITION_NULL_STRING;
   private static final String SELECT = "select a.id, a.k, a.name from a ";
   private static final String GROUP = "select a.k, count(*) from a group by a.k ";
   // SQLHelper and OracleSQLHelper, with and without ANSI joins
   private static final String[] TYPES = { "default", "derby", "derby-ansi", "oracle",
                                           "oracle-ansi" };

   // sql with $(p), the value of p, hand-written sql with the expected rows
   static Stream<Arguments> rowCases() {
      Object[] nullArray = { NULL_VALUE };
      Object[] emptyArray = { EMPTY_STRING };

      return Stream.of(
         // reversed comparison
         Arguments.of(SELECT + "where $(p) = a.name", NULL_VALUE,
                      SELECT + "where a.name IS NULL"),
         Arguments.of(SELECT + "where a.k = 1 and $(p) = a.id", NULL_VALUE,
                      SELECT + "where a.k = 1 and a.id IS NULL"),
         Arguments.of(SELECT + "where a.k = 1 and $(p) <> a.name", NULL_VALUE,
                      SELECT + "where a.k = 1 and a.name IS NOT NULL"),
         Arguments.of(SELECT + "where $(p) != a.id", NULL_VALUE,
                      SELECT + "where a.id IS NOT NULL"),
         Arguments.of(SELECT + "where a.k = 1 and not ($(p) <> a.id)", NULL_VALUE,
                      SELECT + "where a.k = 1 and a.id IS NULL"),
         Arguments.of(SELECT + "where a.k = 2 or $(p) < a.id", NULL_VALUE,
                      SELECT + "where a.k = 2 or a.id IS NULL"),
         Arguments.of(SELECT + "where $(p) = a.name", EMPTY_STRING,
                      SELECT + "where '' = a.name"),
         Arguments.of(SELECT + "where a.k = 1 and $(p) <> a.name", EMPTY_STRING,
                      SELECT + "where a.k = 1 and '' <> a.name"),
         Arguments.of(SELECT + "where $(p) = a.name", NULL_STRING,
                      SELECT + "where 'null' = a.name"),
         Arguments.of(GROUP + "having $(p) = max(a.name)", NULL_VALUE,
                      GROUP + "having max(a.name) IS NULL"),
         // IN list
         Arguments.of(SELECT + "where a.name in ($(p))", NULL_VALUE,
                      SELECT + "where a.name IS NULL"),
         Arguments.of(SELECT + "where a.k = 1 and a.id in ($(p))", NULL_VALUE,
                      SELECT + "where a.k = 1 and a.id IS NULL"),
         Arguments.of(SELECT + "where a.name not in ($(p))", NULL_VALUE,
                      SELECT + "where a.name IS NOT NULL"),
         Arguments.of(SELECT + "where a.name in ($(p))", EMPTY_STRING,
                      SELECT + "where a.name IN ('')"),
         Arguments.of(SELECT + "where a.name not in ($(p))", EMPTY_STRING,
                      SELECT + "where a.name not IN ('')"),
         Arguments.of(SELECT + "where a.name in ($(p))", NULL_STRING,
                      SELECT + "where a.name in ('null')"),
         Arguments.of(GROUP + "having count(*) > 1 and max(a.name) in ($(p))", NULL_VALUE,
                      GROUP + "having count(*) > 1 and max(a.name) IS NULL"),
         // BETWEEN
         Arguments.of(SELECT + "where a.name between $(p) and 'z'", NULL_VALUE,
                      SELECT + "where a.name IS NULL"),
         Arguments.of(SELECT + "where a.k = 1 and a.id between 1 and $(p)", NULL_VALUE,
                      SELECT + "where a.k = 1 and a.id IS NULL"),
         Arguments.of(SELECT + "where a.k = 2 or a.id not between $(p) and 5", NULL_VALUE,
                      SELECT + "where a.k = 2 or a.id IS NOT NULL"),
         Arguments.of(SELECT + "where a.name between $(p) and 'm'", EMPTY_STRING,
                      SELECT + "where a.name between '' and 'm'"),
         Arguments.of(SELECT + "where a.name between 'a' and $(p)", NULL_STRING,
                      SELECT + "where a.name between 'a' and 'null'"),
         Arguments.of(GROUP + "having count(*) > 1 and max(a.id) between $(p) and 10",
                      NULL_VALUE, GROUP + "having count(*) > 1 and max(a.id) IS NULL"),
         // a parameter with one value is the same as a scalar
         Arguments.of(SELECT + "where a.name in ($(p))", nullArray,
                      SELECT + "where a.name IS NULL"),
         Arguments.of(SELECT + "where a.name not in ($(p))", emptyArray,
                      SELECT + "where a.name not IN ('')"),
         Arguments.of(SELECT + "where $(p) = a.name", nullArray,
                      SELECT + "where a.name IS NULL"),
         Arguments.of(SELECT + "where a.name between $(p) and 'z'", nullArray,
                      SELECT + "where a.name IS NULL"),
         // the right operand, already rewritten for a scalar
         Arguments.of(SELECT + "where a.name = $(p)", nullArray,
                      SELECT + "where a.name IS NULL"),
         Arguments.of(SELECT + "where a.name = $(p)", emptyArray,
                      SELECT + "where a.name = ''"),
         // Bug #77707, the escape clause of a LIKE is part of the right operand
         Arguments.of(SELECT + "where a.name like $(p) escape '!'", NULL_VALUE,
                      SELECT + "where a.name IS NULL"),
         Arguments.of(SELECT + "where a.name like $(p) escape '!'", EMPTY_STRING,
                      SELECT + "where a.name like ''"),
         Arguments.of(SELECT + "where a.name like $(p) escape '!'", NULL_STRING,
                      SELECT + "where a.name like 'null'"),
         Arguments.of(SELECT + "where a.k = 1 and a.name not like $(p) escape '!'", NULL_VALUE,
                      SELECT + "where a.k = 1 and a.name IS NOT NULL"),
         Arguments.of(SELECT + "where a.name like $(p) ESCAPE '\\'", EMPTY_STRING,
                      SELECT + "where a.name like ''"),
         // Bug #77709, a string literal that is only the parameter
         Arguments.of(SELECT + "where a.name = '$(p)'", NULL_VALUE,
                      SELECT + "where a.name IS NULL"),
         Arguments.of(SELECT + "where a.name = '$(p)'", EMPTY_STRING,
                      SELECT + "where a.name = ''"),
         Arguments.of(SELECT + "where a.name = '$(p)'", NULL_STRING,
                      SELECT + "where a.name = 'null'"),
         Arguments.of(SELECT + "where a.name = '$(p)'", emptyArray,
                      SELECT + "where a.name = ''"),
         Arguments.of(SELECT + "where a.k = 1 and a.name <> '$(p)'", NULL_VALUE,
                      SELECT + "where a.k = 1 and a.name IS NOT NULL"),
         Arguments.of(SELECT + "where a.name <> '$(p)'", EMPTY_STRING,
                      SELECT + "where a.name <> ''"),
         Arguments.of(SELECT + "where a.name like '$(p)'", EMPTY_STRING,
                      SELECT + "where a.name like ''"),
         Arguments.of(SELECT + "where a.name like '$(p)' escape '!'", NULL_VALUE,
                      SELECT + "where a.name IS NULL"),
         Arguments.of(SELECT + "where '$(p)' = a.name", NULL_VALUE,
                      SELECT + "where a.name IS NULL"),
         Arguments.of(SELECT + "where '$(p)' = a.name", EMPTY_STRING,
                      SELECT + "where '' = a.name"),
         Arguments.of(SELECT + "where a.k = 1 and '$(p)' <> a.name", NULL_VALUE,
                      SELECT + "where a.k = 1 and a.name IS NOT NULL"),
         Arguments.of(SELECT + "where a.name in ('$(p)')", NULL_VALUE,
                      SELECT + "where a.name IS NULL"),
         Arguments.of(SELECT + "where a.name in ( '$(p)' )", EMPTY_STRING,
                      SELECT + "where a.name in ('')"),
         Arguments.of(SELECT + "where a.name not in ('$(p)')", NULL_STRING,
                      SELECT + "where a.name not in ('null')"),
         Arguments.of(SELECT + "where a.name = '$(@p)'", NULL_VALUE,
                      SELECT + "where a.name IS NULL"),
         Arguments.of(SELECT + "where a.name = '$(@p)'", EMPTY_STRING,
                      SELECT + "where a.name = ''"),
         Arguments.of(GROUP + "having count(*) > 1 and max(a.name) = '$(p)'", NULL_VALUE,
                      GROUP + "having count(*) > 1 and max(a.name) IS NULL"),
         // a string literal bound, the subject of a BETWEEN
         Arguments.of(SELECT + "where a.name between '$(p)' and 'm'", NULL_VALUE,
                      SELECT + "where a.name IS NULL"),
         Arguments.of(SELECT + "where a.name between '$(p)' and 'm'", EMPTY_STRING,
                      SELECT + "where a.name between '' and 'm'"),
         Arguments.of(SELECT + "where a.name between 'a' and '$(p)'", NULL_STRING,
                      SELECT + "where a.name between 'a' and 'null'"),
         Arguments.of(SELECT + "where $(p) between a.name and 'zzz'", EMPTY_STRING,
                      SELECT + "where '' between a.name and 'zzz'"),
         Arguments.of(SELECT + "where $(p) between a.name and 'zzz'", NULL_STRING,
                      SELECT + "where 'null' between a.name and 'zzz'"),
         Arguments.of(SELECT + "where a.k = 1 and $(p) not between a.name and 'zzz'",
                      EMPTY_STRING, SELECT + "where a.k = 1 and '' not between a.name and 'zzz'"),
         Arguments.of(SELECT + "where '$(p)' between a.name and 'zzz'", EMPTY_STRING,
                      SELECT + "where '' between a.name and 'zzz'"),
         Arguments.of(SELECT + "where $(p) between a.name and $(p)", NULL_STRING,
                      SELECT + "where 'null' between a.name and 'null'"),
         Arguments.of(SELECT + "where $(p) between a.name and 'zzz'", emptyArray,
                      SELECT + "where '' between a.name and 'zzz'"));
   }

   @ParameterizedTest
   @MethodSource("rowCases")
   void sameRows(String sql, Object p, String expected) throws Exception {
      try(Connection conn = connect()) {
         List<String> expectedRows = rows(conn, expected);

         for(String type : TYPES) {
            for(boolean forVpm : new boolean[] { false, true }) {
               String generated = validate(parse(sql, type), p, forVpm);
               assertEquals(expectedRows, rows(conn, bind(generated, p)),
                            type + " forVpm=" + forVpm + "\ngenerated: " + generated);
               assertFalse(generated.contains("$(p)"),
                           type + " forVpm=" + forVpm + " not rewritten: " + generated);
            }
         }
      }
   }

   // sql, the value of p, the expected clause tail on the default helper
   static Stream<Arguments> textCases() {
      return Stream.of(
         Arguments.of(SELECT + "where $(p) <> a.name", NULL_VALUE,
                      "where not (a.name IS NULL)"),
         Arguments.of(SELECT + "where a.name not in ($(p))", NULL_VALUE,
                      "where not (a.name IS NULL)"),
         Arguments.of(SELECT + "where $(p) < a.name", EMPTY_STRING, "where '' < a.name"),
         Arguments.of(SELECT + "where a.name in ($(p))", EMPTY_STRING, "where a.name IN ('')"),
         Arguments.of(SELECT + "where a.name not between $(p) and 'z'", NULL_VALUE,
                      "where not (a.name IS NULL)"),
         Arguments.of(SELECT + "where a.name between $(p) and 'z'", EMPTY_STRING,
                      "where a.name BETWEEN '' and 'z'"),
         // Bug #77707, the escape clause goes with the parameter
         Arguments.of(SELECT + "where a.name like $(p) escape '!'", EMPTY_STRING,
                      "where a.name LIKE ''"),
         Arguments.of(SELECT + "where a.name not like $(p) escape '!'", NULL_VALUE,
                      "where not (a.name IS NULL)"),
         // Bug #77709
         Arguments.of(SELECT + "where a.name <> '$(p)'", NULL_VALUE,
                      "where not (a.name IS NULL)"),
         Arguments.of(SELECT + "where a.name = '$(p)'", EMPTY_STRING, "where a.name = ''"),
         Arguments.of(SELECT + "where $(p) between a.name and 'z'", EMPTY_STRING,
                      "where '' BETWEEN a.name and 'z'"));
   }

   @ParameterizedTest
   @MethodSource("textCases")
   void generatedText(String sql, String p, String expected) throws Exception {
      for(boolean forVpm : new boolean[] { false, true }) {
         assertEndsWith(expected, validate(parse(sql, "default"), p, forVpm));
      }
   }

   // shapes that keep the parameter: no single condition means the same thing, or the
   // operand is more than the parameter
   static Stream<Arguments> unchangedCases() {
      return Stream.of(
         // IS NULL on the other operand isn't valid sql, or means something else
         Arguments.of(SELECT + "where $(p) in ('x', 'y')", NULL_VALUE),
         Arguments.of(SELECT + "where $(p) not in ('x', 'y')", NULL_VALUE),
         Arguments.of(SELECT + "where $(p) in (select b.x from b)", NULL_VALUE),
         Arguments.of(SELECT + "where $(p) = any (select b.x from b)", NULL_VALUE),
         Arguments.of(SELECT + "where $(p) <> all (select b.x from b)", NULL_VALUE),
         Arguments.of(SELECT + "where $(p) like a.name", NULL_VALUE),
         Arguments.of(SELECT + "where $(p) like a.name escape '!'", NULL_VALUE),
         // the operand isn't exactly the parameter
         Arguments.of(SELECT + "where $(p) || 'x' = a.name", NULL_VALUE),
         Arguments.of(SELECT + "where $(p) || 'x' = a.name", EMPTY_STRING),
         Arguments.of(SELECT + "where a.name in ($(p), 'x')", NULL_VALUE),
         Arguments.of(SELECT + "where a.name in ($(p), 'x')", EMPTY_STRING),
         Arguments.of(SELECT + "where a.name in ('x', $(p))", NULL_VALUE),
         // a NULL_VALUE subject of BETWEEN
         Arguments.of(SELECT + "where $(p) between a.name and 'z'", NULL_VALUE),
         Arguments.of(SELECT + "where '$(p)' between a.name and 'z'", NULL_VALUE),
         // Bug #77707, a right operand that only starts with the parameter
         Arguments.of(SELECT + "where a.name = $(p) || 'x'", NULL_VALUE),
         Arguments.of(SELECT + "where a.name = $(p) || 'x'", EMPTY_STRING),
         Arguments.of(SELECT + "where a.name = $(p) || 'x'", NULL_STRING),
         Arguments.of(SELECT + "where a.name = $(p)||'x'", EMPTY_STRING),
         Arguments.of(SELECT + "where a.k = 1 and a.name <> $(p) || 'x'", NULL_VALUE),
         Arguments.of(SELECT + "where a.name like $(p) || '%'", EMPTY_STRING),
         Arguments.of(SELECT + "where a.name like $(p) || '%'", NULL_VALUE),
         Arguments.of(SELECT + "where a.name like $(p) || '%' escape '!'", EMPTY_STRING),
         Arguments.of(SELECT + "where a.name like $(p) || '%' escape '!'", NULL_VALUE),
         Arguments.of(SELECT + "where a.id = $(p) + 1", NULL_VALUE),
         // Bug #77709, a string literal that holds more than the parameter
         Arguments.of(SELECT + "where a.name like '%$(p)%'", EMPTY_STRING),
         Arguments.of(SELECT + "where a.name like '%$(p)%'", NULL_VALUE),
         Arguments.of(SELECT + "where a.name = ' $(p)'", EMPTY_STRING),
         Arguments.of(SELECT + "where '$(p)' || 'x' = a.name", EMPTY_STRING),
         Arguments.of(SELECT + "where a.name in ('$(p)', 'x')", EMPTY_STRING),
         Arguments.of(SELECT + "where a.name = '$(p)'", "n1"),
         Arguments.of(SELECT + "where $(p) between a.name and 'z'", "n1"),
         // more than one value
         Arguments.of(SELECT + "where a.name in ($(p))", new Object[] { NULL_VALUE, "n1" }),
         Arguments.of(SELECT + "where a.name = $(p)", new Object[] { NULL_VALUE, "n1" }),
         Arguments.of(SELECT + "where $(p) = a.name", new Object[] { EMPTY_STRING, "n1" }),
         // not a sentinel
         Arguments.of(SELECT + "where $(p) = a.name", "n1"),
         Arguments.of(SELECT + "where a.name in ($(p))", "n1"),
         Arguments.of(SELECT + "where a.name between $(p) and 'z'", "n1"));
   }

   @ParameterizedTest
   @MethodSource("unchangedCases")
   void unchanged(String sql, Object p) throws Exception {
      for(boolean forVpm : new boolean[] { false, true }) {
         UniformSQL expected = parse(sql, "default");
         String generated = validate(parse(sql, "default"), p, forVpm);
         assertEquals(generate(expected), generated, "forVpm=" + forVpm);
         assertTrue(generated.contains("$(p)"), generated);
      }
   }

   // Bug #77707, an operand with a second parameter, which has a value
   @Test
   void secondParameterUnchanged() throws Exception {
      String[] cases = {
         SELECT + "where a.name = $(p) || $(q)",
         SELECT + "where a.name like $(p) escape $(q)",
      };

      for(String sql : cases) {
         for(String p : new String[] { NULL_VALUE, EMPTY_STRING, NULL_STRING }) {
            for(boolean forVpm : new boolean[] { false, true }) {
               VariableTable vars = new VariableTable();
               vars.put("p", p);
               vars.put("q", "!");
               UniformSQL usql = parse(sql, "default");
               XUtil.validateConditions(null, usql, vars, true, forVpm);
               assertEquals(generate(parse(sql, "default")), generate(usql), sql + " " + p);
            }
         }
      }
   }

   // Bug #77709, an embedded parameter outside quotes is sql text and is kept
   @Test
   void embeddedParameterUnchanged() throws Exception {
      String[] cases = {
         SELECT + "where a.name = $(@p)",
         SELECT + "where $(@p) = a.name",
         SELECT + "where a.name in ($(@p))",
      };

      for(String sql : cases) {
         for(Object p : new Object[] { NULL_VALUE, EMPTY_STRING }) {
            for(boolean forVpm : new boolean[] { false, true }) {
               String generated = validate(parse(sql, "default"), p, forVpm);
               assertEquals(generate(parse(sql, "default")), generated, sql + " " + p);
               assertTrue(generated.contains("$(@p)"), generated);
            }
         }
      }
   }

   // a NULL_VALUE bound turns the BETWEEN into IS NULL of the subject. An EMPTY_STRING
   // subject is kept there, as before: '' IS NULL would be true on oracle only.
   @Test
   void nullBoundKeepsSubject() throws Exception {
      String[][] cases = {
         { SELECT + "where a.k = 1 and $(p) between a.name and $(q)", "$(p)" },
         { SELECT + "where a.k = 1 and '$(p)' between $(q) and a.name", "'$(p)'" },
      };

      for(String[] c : cases) {
         for(boolean forVpm : new boolean[] { false, true }) {
            VariableTable vars = new VariableTable();
            vars.put("p", EMPTY_STRING);
            vars.put("q", NULL_VALUE);
            UniformSQL usql = parse(c[0], "default");
            XUtil.validateConditions(null, usql, vars, true, forVpm);
            assertEndsWith("where a.k = 1 and " + c[1] + " IS NULL", generate(usql));
         }
      }
   }

   // the expected rows of the unchanged shapes that derby can run, p bound as its text
   @Test
   void unchangedRows() throws Exception {
      String[] cases = {
         SELECT + "where $(p) in ('x', 'NULL_VALUE')",
         SELECT + "where $(p) like a.name",
         SELECT + "where a.name in ($(p), 'n1')",
      };

      try(Connection conn = connect()) {
         for(String sql : cases) {
            for(boolean forVpm : new boolean[] { false, true }) {
               String generated = validate(parse(sql, "derby"), NULL_VALUE, forVpm);
               assertEquals(rows(conn, bind(sql, NULL_VALUE)), rows(conn, bind(generated, NULL_VALUE)),
                            generated);
            }
         }
      }
   }

   // the parser keeps an IN list as an expression in parentheses and BETWEEN as a trinary
   // condition, which is what the rewrite matches
   @Test
   void parsedShapes() throws Exception {
      UniformSQL usql = parse(SELECT + "where a.k = 1 and a.name in ($(p))", "default");
      XBinaryCondition in = (XBinaryCondition) findLeaf(usql.getWhere(), "a.name");
      assertEquals("($(p))", in.getExpression2().toString().trim());

      usql = parse(SELECT + "where a.k = 1 and a.name between $(p) and 'z'", "default");
      assertInstanceOf(XTrinaryCondition.class, findLeaf(usql.getWhere(), "a.name"));

      usql = parse(SELECT + "where a.k = 1 and $(p) = a.name", "default");
      XFilterNode reversed = findLeaf(usql.getWhere(), "$(p)");
      assertEquals(XBinaryCondition.class, reversed.getClass());
   }

   // a WHERE/HAVING that is a single condition rather than a set, as built by code
   @Test
   void bareConditionRoot() throws Exception {
      for(boolean forVpm : new boolean[] { false, true }) {
         UniformSQL usql = parse(SELECT + "where a.k = 1 and a.name between $(p) and 'z'",
                                 "default");
         usql.setWhere(findLeaf(usql.getWhere(), "a.name"));
         usql.getWhere().setIsNot(true);
         assertEndsWith("from a where not (a.name IS NULL)", validate(usql, NULL_VALUE, forVpm));
         assertInstanceOf(XBinaryCondition.class, usql.getWhere());

         usql = parse(SELECT + "where a.k = 1 and a.name between $(p) and 'z'", "default");
         usql.setWhere(findLeaf(usql.getWhere(), "a.name"));
         assertEndsWith("from a where a.name BETWEEN '' and 'z'",
                        validate(usql, EMPTY_STRING, forVpm));

         usql = parse(GROUP + "having count(*) > 1 and $(p) <> max(a.name)", "default");
         usql.setHaving(findLeaf(usql.getHaving(), "$(p)"));
         assertEndsWith("group by a.k having not (max(a.name) IS NULL)",
                        validate(usql, NULL_VALUE, forVpm));

         usql = parse(GROUP + "having count(*) > 1 and max(a.name) in ($(p))", "default");
         usql.setHaving(findLeaf(usql.getHaving(), "max(a.name)"));
         assertEndsWith("group by a.k having max(a.name) IN ('null')",
                        validate(usql, NULL_STRING, forVpm));
      }
   }

   // the rewritten condition keeps the name of the original condition
   @Test
   void nameKept() throws Exception {
      String[][] cases = {
         { SELECT + "where a.k = 1 and $(p) = a.name", "$(p)", NULL_VALUE },
         { SELECT + "where a.k = 1 and $(p) = a.name", "$(p)", EMPTY_STRING },
         { SELECT + "where a.k = 1 and a.name in ($(p))", "a.name", NULL_VALUE },
         { SELECT + "where a.k = 1 and a.name between $(p) and 'z'", "a.name", NULL_VALUE },
         { SELECT + "where a.k = 1 and a.name between $(p) and 'z'", "a.name", NULL_STRING },
      };

      for(String[] c : cases) {
         UniformSQL usql = parse(c[0], "default");
         XFilterNode leaf = findLeaf(usql.getWhere(), c[1]);
         leaf.setName("cond1");
         XNode parent = leaf.getParent();
         int index = indexOf(parent, leaf);
         assertTrue(index >= 0, c[0]);
         VariableTable vars = new VariableTable();
         vars.put("p", c[2]);
         XUtil.validateConditions(null, usql, vars, true, false);
         XFilterNode rewritten = (XFilterNode) parent.getChild(index);
         assertNotSame(leaf, rewritten, c[0] + " " + c[2]);
         assertEquals("cond1", rewritten.getName(), c[0] + " " + c[2] + " " + rewritten);
      }
   }

   // a NULL_VALUE bound replaces the whole BETWEEN, an unset parameter in the other bound
   // goes with it. With another sentinel the other bound is kept, and an unset parameter
   // there drops the condition on the jdbc path as before.
   @Test
   void otherBoundUnset() throws Exception {
      String sql = SELECT + "where a.k = 1 and a.id between $(p) and $(q)";

      for(boolean forVpm : new boolean[] { false, true }) {
         assertEndsWith("where a.k = 1 and a.id IS NULL",
                        validate(parse(sql, "default"), NULL_VALUE, forVpm));
      }

      sql = SELECT + "where a.k = 1 and a.name between $(p) and $(q)";
      assertEndsWith("from a where a.k = 1", validate(parse(sql, "default"), EMPTY_STRING, false));
      assertEndsWith("where a.k = 1 and a.name BETWEEN '' and $(q)",
                     validate(parse(sql, "default"), EMPTY_STRING, true));
   }

   // both operands are parameters: the right one is checked first, as before
   @Test
   void bothOperandsParameters() throws Exception {
      String sql = SELECT + "where a.k = 1 and $(p) = $(q)";
      VariableTable vars = new VariableTable();
      vars.put("p", NULL_VALUE);
      vars.put("q", EMPTY_STRING);
      UniformSQL usql = parse(sql, "default");
      XUtil.validateConditions(null, usql, vars, true, true);
      assertEndsWith("where a.k = 1 and $(p) = ''", generate(usql));

      vars.put("q", "x");
      usql = parse(sql, "default");
      XUtil.validateConditions(null, usql, vars, true, true);
      assertEndsWith("where a.k = 1 and $(q) IS NULL", generate(usql));
   }

   // the reported shapes, bound by VarSQL as JDBCHandler binds them rather than as text
   static Stream<Arguments> varSqlCases() {
      return Stream.of(
         Arguments.of(SELECT + "where $(p) <> a.name", NULL_VALUE,
                      SELECT + "where a.name IS NOT NULL"),
         Arguments.of(SELECT + "where $(p) <> a.id", NULL_VALUE,
                      SELECT + "where a.id IS NOT NULL"),
         Arguments.of(SELECT + "where $(p) <> a.name", EMPTY_STRING,
                      SELECT + "where '' <> a.name"),
         Arguments.of(SELECT + "where $(p) <> a.name", NULL_STRING,
                      SELECT + "where 'null' <> a.name"),
         Arguments.of(SELECT + "where $(p) = a.name", NULL_VALUE,
                      SELECT + "where a.name IS NULL"),
         Arguments.of(SELECT + "where $(p) = a.id", NULL_VALUE,
                      SELECT + "where a.id IS NULL"),
         Arguments.of(SELECT + "where $(p) = a.name", EMPTY_STRING,
                      SELECT + "where '' = a.name"),
         Arguments.of(SELECT + "where $(p) = a.name", NULL_STRING,
                      SELECT + "where 'null' = a.name"),
         Arguments.of(SELECT + "where a.name between $(p) and 'z'", NULL_VALUE,
                      SELECT + "where a.name IS NULL"),
         Arguments.of(SELECT + "where a.id between $(p) and 10", NULL_VALUE,
                      SELECT + "where a.id IS NULL"),
         Arguments.of(SELECT + "where a.name between $(p) and 'z'", EMPTY_STRING,
                      SELECT + "where a.name between '' and 'z'"),
         Arguments.of(SELECT + "where a.name between $(p) and 'z'", NULL_STRING,
                      SELECT + "where a.name between 'null' and 'z'"),
         Arguments.of(SELECT + "where a.name in ($(p))", NULL_VALUE,
                      SELECT + "where a.name IS NULL"),
         Arguments.of(SELECT + "where a.id in ($(p))", NULL_VALUE,
                      SELECT + "where a.id IS NULL"),
         Arguments.of(SELECT + "where a.name in ($(p))", EMPTY_STRING,
                      SELECT + "where a.name in ('')"),
         Arguments.of(SELECT + "where a.name in ($(p))", NULL_STRING,
                      SELECT + "where a.name in ('null')"));
   }

   @ParameterizedTest
   @MethodSource("varSqlCases")
   void sameRowsThroughVarSQL(String sql, String p, String expected) throws Exception {
      try(Connection conn = connect()) {
         List<String> expectedRows = rows(conn, expected);

         for(boolean forVpm : new boolean[] { false, true }) {
            VariableTable vars = new VariableTable();
            vars.put("p", p);
            UniformSQL usql = parse(sql, "derby");
            XUtil.validateConditions(null, usql, vars, true, forVpm);
            usql.clearSQLString();
            VarSQL varsql = new VarSQL();
            varsql.setSQLType(VarSQL.SQLType.STATEMENT);
            String bound = varsql.replaceVariables(usql.getSQLString(), vars);
            List<String> actual = new ArrayList<>();

            try(PreparedStatement stmt = conn.prepareStatement(bound)) {
               List<Object> values = varsql.getParameterValues();

               for(int i = 0; i < values.size(); i++) {
                  stmt.setObject(i + 1, values.get(i));
               }

               try(ResultSet rs = stmt.executeQuery()) {
                  while(rs.next()) {
                     actual.add(rs.getString(1) + "|" + rs.getString(2) + "|" +
                                   rs.getString(3) + "|");
                  }
               }
            }

            Collections.sort(actual);
            assertEquals(expectedRows, actual, "forVpm=" + forVpm + "\nbound: " + bound);
            assertTrue(varsql.getParameterValues().isEmpty(), bound);
         }
      }
   }

   @AfterAll
   static void dropDatabase() {
      try {
         DriverManager.getConnection("jdbc:derby:memory:bug77619;drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   private static Connection connect() throws SQLException {
      Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77619;create=true");

      try(Statement stmt = conn.createStatement()) {
         for(String table : new String[] { "a", "b" }) {
            try {
               stmt.executeUpdate("drop table " + table);
            }
            catch(SQLException ignore) {
               // first run
            }
         }

         stmt.executeUpdate("create table a (id int, k int, name varchar(20))");
         stmt.executeUpdate("create table b (id int, k int, x varchar(20))");
         stmt.executeUpdate("insert into a values (1, 1, 'n1'), (null, 1, ''), (3, 2, null), " +
                               "(null, 2, 'null'), (5, 1, 'NULL_VALUE'), (6, 1, null), " +
                               "(7, 3, null), (8, 3, null), (9, 2, ''), (10, 3, 'x'), " +
                               "(11, 1, 'EMPTY_STRING')");
         stmt.executeUpdate("insert into b values (1, 2, null), (3, 2, 'x'), (5, 2, 'n1')");
      }

      return conn;
   }

   private static List<String> rows(Connection conn, String sql) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
         int count = rs.getMetaData().getColumnCount();

         while(rs.next()) {
            StringBuilder row = new StringBuilder();

            for(int i = 1; i <= count; i++) {
               row.append(rs.getString(i)).append('|');
            }

            rows.add(row.toString());
         }
      }

      Collections.sort(rows);
      return rows;
   }

   // a parameter left in the sql is bound by VarSQL as the (first) value, as text here
   private static String bind(String sql, Object p) {
      Object value = p instanceof Object[] ? ((Object[]) p)[0] : p;
      return sql.replace("$(p)", "'" + value + "'");
   }

   // the condition whose first operand is the text
   private static XFilterNode findLeaf(XNode node, String operand) {
      XExpression exp1 = node instanceof XBinaryCondition ?
         ((XBinaryCondition) node).getExpression1() : node instanceof XTrinaryCondition ?
         ((XTrinaryCondition) node).getExpression1() : null;

      if(exp1 != null && operand.equals(exp1.toString().trim())) {
         return (XFilterNode) node;
      }

      for(int i = 0; i < node.getChildCount(); i++) {
         XFilterNode leaf = findLeaf(node.getChild(i), operand);

         if(leaf != null) {
            return leaf;
         }
      }

      return null;
   }

   private static int indexOf(XNode parent, XNode child) {
      for(int i = 0; i < parent.getChildCount(); i++) {
         if(parent.getChild(i) == child) {
            return i;
         }
      }

      return -1;
   }

   private static String validate(UniformSQL usql, Object p, boolean forVpm) {
      VariableTable vars = new VariableTable();
      vars.put("p", p);
      XUtil.validateConditions(null, usql, vars, true, forVpm);
      return generate(usql);
   }

   private static String generate(UniformSQL usql) {
      usql.clearSQLString();
      return normalize(usql.getSQLString());
   }

   private static void assertEndsWith(String expected, String sql) {
      assertTrue(sql.endsWith(" " + expected), "expected ..." + expected + "\nactual " + sql);
   }

   // the generated sql is pretty-printed
   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").replace("( ", "(").trim();
   }

   // a new query for every case and run, the rewrite changes the parsed tree in place
   private static UniformSQL parse(String text, String type) throws Exception {
      UniformSQL sql = new UniformSQL();

      if(!"default".equals(type)) {
         sql.setDataSource(SQLHelperNotEqualJoinTest.RowCompare.dataSource(type));
      }

      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      sql.clearSQLString();
      return sql;
   }
}
