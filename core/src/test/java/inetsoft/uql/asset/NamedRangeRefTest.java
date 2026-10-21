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

import inetsoft.report.internal.binding.ExpertNamedGroupInfo;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.JunctionOperator;
import inetsoft.uql.XCondition;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78933: NamedRangeRef rendered only EQUAL_TO/LESS_THAN/GREATER_THAN named-group
 * conditions; any other operator produced invalid script ({@code "N"(...)}) or a silently
 * wrong SQL {@code IN (...)}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class NamedRangeRefTest {
   private static final String COL = "Customer:State";

   private static Condition cond(String type, int op, Object... values) {
      Condition c = new Condition(type);
      c.setOperation(op);

      for(Object v : values) {
         c.addValue(v);
      }

      return c;
   }

   private static Condition neg(Condition c) {
      c.setNegated(true);
      return c;
   }

   private static ConditionList list(Condition... conds) {
      ConditionList list = new ConditionList();

      for(int i = 0; i < conds.length; i++) {
         if(i > 0) {
            list.append(new JunctionOperator(JunctionOperator.OR, 0));
         }

         list.append(new ConditionItem(new AttributeRef(COL), conds[i], 0));
      }

      return list;
   }

   private static NamedRangeRef ref(String type, String dbType, ConditionList conds) {
      ExpertNamedGroupInfo info = new ExpertNamedGroupInfo();
      info.setGroupCondition("G", conds);
      AttributeRef attr = new AttributeRef(COL);
      attr.setDataType(type);
      NamedRangeRef ref = new NamedRangeRef(COL, attr);
      ref.setNamedGroupInfo(info);
      ref.setBaseDataType(type);
      ref.setDBType(dbType);
      return ref;
   }

   /** Evaluate the generated script for the given field value; returns the result. */
   private static Object eval(NamedRangeRef ref, Object fieldValue) {
      String lit = fieldValue == null ? "null" :
         fieldValue instanceof String ? "'" + ((String) fieldValue).replace("\\", "\\\\")
            .replace("'", "\\'") + "'" : "" + fieldValue;
      String script = "var field = {'" + COL + "': " + lit + "};\n" + ref.getScriptExpression();

      try(Context ctx = Context.newBuilder("js").build()) {
         Value v = ctx.eval("js", script);
         return v.isNull() ? null : v.isNumber() ? v.asDouble() : v.asString();
      }
   }

   private static String script(String type, Condition... conds) {
      return ref(type, "derby", list(conds)).getScriptExpression();
   }

   private static String sql(String type, String db, Condition... conds) {
      return ref(type, db, list(conds)).getExpression();
   }

   // ---------------------------------------------------------------- unchanged output

   @Test
   void legacyScriptOutputUnchanged() {
      assertEquals(
         "if((\"NJ\" == (field['Customer:State'] == null ? field['Customer:State'] : " +
         "field['Customer:State'].replace(/\\\\/g, \"\\\\\\\\\").replace(/\\n/g, \"\\\\n\")))) {\n" +
         "   \"G\";\n}else {\n   field['Customer:State'] == null ? field['Customer:State'] : " +
         "field['Customer:State'];\n}",
         script(XSchema.STRING, cond(XSchema.STRING, XCondition.EQUAL_TO, "NJ")));
   }

   @Test
   void legacySqlOutputUnchanged() {
      assertEquals(
         "(CASE \n WHEN field['Customer:State'] IN ('NJ')THEN 'G'\n" +
         " ELSE field['Customer:State'] END)",
         sql(XSchema.STRING, "postgresql", cond(XSchema.STRING, XCondition.EQUAL_TO, "NJ")));
   }

   // ---------------------------------------------------------------- script path

   @Test
   void scriptStartingWithIsValidAndMatchesPrefix() {
      NamedRangeRef ref = ref(XSchema.STRING, "derby",
         list(cond(XSchema.STRING, XCondition.STARTING_WITH, "N")));
      assertFalse(ref.getScriptExpression().contains("\"N\"("));
      assertEquals("G", eval(ref, "NY"));
      assertEquals("MA", eval(ref, "MA"));
      assertEquals("AN", eval(ref, "AN"));
      assertNull(eval(ref, null));
   }

   @Test
   void scriptContainsLikeOneOfBetween() {
      assertEquals("G", eval(ref(XSchema.STRING, "derby",
         list(cond(XSchema.STRING, XCondition.CONTAINS, "ew"))), "New York"));
      assertEquals("x", eval(ref(XSchema.STRING, "derby",
         list(cond(XSchema.STRING, XCondition.CONTAINS, "ew"))), "x"));
      NamedRangeRef like = ref(XSchema.STRING, "derby",
         list(cond(XSchema.STRING, XCondition.LIKE, "N%")));
      assertEquals("G", eval(like, "NJ"));
      assertEquals("MN", eval(like, "MN"));
      NamedRangeRef oneOf = ref(XSchema.STRING, "derby",
         list(cond(XSchema.STRING, XCondition.ONE_OF, "NJ", "NV", "NY")));
      assertEquals("G", eval(oneOf, "NV"));
      assertEquals("MA", eval(oneOf, "MA"));
      NamedRangeRef between = ref(XSchema.STRING, "derby",
         list(cond(XSchema.STRING, XCondition.BETWEEN, "B", "D")));
      assertEquals("G", eval(between, "C"));
      assertEquals("E", eval(between, "E"));
   }

   @Test
   void scriptNegation() {
      Condition c = cond(XSchema.STRING, XCondition.STARTING_WITH, "N");
      c.setNegated(true);
      NamedRangeRef ref = ref(XSchema.STRING, "derby", list(c));
      assertEquals("G", eval(ref, "MA"));
      assertEquals("NY", eval(ref, "NY"));
      Condition ne = cond(XSchema.STRING, XCondition.EQUAL_TO, "MA");
      ne.setNegated(true);
      assertEquals("G", eval(ref(XSchema.STRING, "derby", list(ne)), "NY"));
      Condition nn = cond(XSchema.STRING, XCondition.NULL);
      nn.setNegated(true);
      NamedRangeRef notNull = ref(XSchema.STRING, "derby", list(nn));
      assertEquals("G", eval(notNull, "NY"));
   }

   @Test
   void scriptNumericOperators() {
      NamedRangeRef ge = ref(XSchema.INTEGER, "derby", list(
         cond(XSchema.INTEGER, XCondition.BETWEEN, 10, 20)));
      assertEquals("G", eval(ge, 15));
      assertEquals(25d, eval(ge, 25));
      NamedRangeRef oneOf = ref(XSchema.INTEGER, "derby", list(
         cond(XSchema.INTEGER, XCondition.ONE_OF, 1, 3)));
      assertEquals("G", eval(oneOf, 3));
      assertEquals(2d, eval(oneOf, 2));
   }

   @Test
   void scriptEscapesLiteralValues() {
      String evil = "x\"); throw new Error('boom'); (\"";
      NamedRangeRef ref = ref(XSchema.STRING, "derby",
         list(cond(XSchema.STRING, XCondition.STARTING_WITH, evil)));
      assertDoesNotThrow(() -> eval(ref, "abc"));
      assertEquals("G", eval(ref, evil + "tail"));
      NamedRangeRef bs = ref(XSchema.STRING, "derby",
         list(cond(XSchema.STRING, XCondition.CONTAINS, "a\\b'c")));
      assertEquals("G", eval(bs, "xa\\b'cx"));
   }

   // ---------------------------------------------------------------- SQL path

   @Test
   void sqlStartingWithIsLikeNotIn() {
      String sql = sql(XSchema.STRING, "postgresql",
         cond(XSchema.STRING, XCondition.STARTING_WITH, "N"));
      assertTrue(sql.contains("field['Customer:State'] LIKE 'N%' ESCAPE '!'"), sql);
      assertFalse(sql.contains("IN ("), sql);
   }

   @Test
   void sqlOperatorsRendered() {
      assertTrue(sql(XSchema.STRING, "postgresql",
         cond(XSchema.STRING, XCondition.CONTAINS, "a%b_c!"))
         .contains("LIKE '%a!%b!_c!!%' ESCAPE '!'"));
      assertTrue(sql(XSchema.STRING, "postgresql",
         cond(XSchema.STRING, XCondition.LIKE, "N_%"))
         .contains("LIKE 'N_%'"));
      assertTrue(sql(XSchema.STRING, "postgresql",
         cond(XSchema.STRING, XCondition.ONE_OF, "NJ", "NV"))
         .contains("IN ('NJ', 'NV')"));
      assertTrue(sql(XSchema.STRING, "postgresql",
         cond(XSchema.STRING, XCondition.BETWEEN, "B", "D"))
         .contains("BETWEEN 'B' AND 'D'"));
      assertTrue(sql(XSchema.STRING, "postgresql",
         neg(cond(XSchema.STRING, XCondition.EQUAL_TO, "O'Neil")))
         .contains("NOT"));
   }

   @Test
   void sqlEscapesQuotes() {
      String sql = sql(XSchema.STRING, "postgresql",
         cond(XSchema.STRING, XCondition.ONE_OF, "O'Neil"));
      assertTrue(sql.contains("'O''Neil'"), sql);
   }

   @Test
   void sqlBackslashEscapedOnMysqlOnly() {
      String evil = "a\\' OR 1=1 --";
      Condition[] conds = {
         cond(XSchema.STRING, XCondition.ONE_OF, evil),
         cond(XSchema.STRING, XCondition.STARTING_WITH, evil),
         cond(XSchema.STRING, XCondition.LIKE, evil)
      };

      for(Condition c : conds) {
         String my = sql(XSchema.STRING, "mysql", c);
         assertTrue(my.contains("a\\\\''"), my);
         assertFalse(my.contains("a\\'' OR"), my);
         String pg = sql(XSchema.STRING, "postgresql", c);
         assertTrue(pg.contains("a\\'' OR 1=1 --"), pg);
         assertFalse(pg.contains("a\\\\"), pg);
      }
   }

   @Test
   void sqlGroupNameEscaped() {
      ExpertNamedGroupInfo info = new ExpertNamedGroupInfo();
      info.setGroupCondition("it's\\x",
         list(cond(XSchema.STRING, XCondition.STARTING_WITH, "N")));

      for(String db : new String[] { "mysql", "mariadb", "postgresql" }) {
         NamedRangeRef r = ref(XSchema.STRING, db, list());
         r.setNamedGroupInfo(info);
         String expected = db.equals("postgresql") ? "THEN 'it''s\\x'" : "THEN 'it''s\\\\x'";
         assertTrue(r.getExpression().contains(expected), r.getExpression());
      }
   }

   @Test
   void legacySqlPlainValuesUnchangedOnAllDialects() {
      String expected = "(CASE \n WHEN field['Customer:State'] IN ('NJ')THEN 'G'\n" +
         " ELSE field['Customer:State'] END)";

      for(String db : new String[] { "mysql", "mariadb", "postgresql", "derby" }) {
         assertEquals(expected,
            sql(XSchema.STRING, db, cond(XSchema.STRING, XCondition.EQUAL_TO, "NJ")), db);
      }
   }

   @Test
   void legacySqlEscapesQuoteAndBackslash() {
      Condition c = cond(XSchema.STRING, XCondition.EQUAL_TO, "O'Neil", "a\\' OR 1=1 --");
      String pg = sql(XSchema.STRING, "postgresql", c);
      assertTrue(pg.contains("IN ('O''Neil', 'a\\'' OR 1=1 --')"), pg);
      String my = sql(XSchema.STRING, "mysql", c);
      assertTrue(my.contains("IN ('O''Neil', 'a\\\\'' OR 1=1 --')"), my);
   }

   @Test
   void legacySqlGroupNameEscaped() {
      for(String db : new String[] { "mysql", "postgresql" }) {
         for(int nullCase = 0; nullCase < 2; nullCase++) {
            ExpertNamedGroupInfo info = new ExpertNamedGroupInfo();
            info.setGroupCondition("it's\\x", list(nullCase == 0 ?
               cond(XSchema.STRING, XCondition.EQUAL_TO, "A") :
               cond(XSchema.STRING, XCondition.NULL)));
            NamedRangeRef r = ref(XSchema.STRING, db, list());
            r.setNamedGroupInfo(info);
            String expected = db.equals("postgresql") ? "THEN 'it''s\\x'" : "THEN 'it''s\\\\x'";
            assertTrue(r.getExpression().contains(expected), r.getExpression());
         }
      }
   }

   @Test
   void sqlNumericDerbyUnquoted() {
      assertTrue(sql(XSchema.INTEGER, "derby",
         cond(XSchema.INTEGER, XCondition.BETWEEN, 1, 5)).contains("BETWEEN 1 AND 5"));
   }

   // ---------------------------------------------------------------- Bug #79129 FLOAT / DOUBLE

   private static final String[] FP_TYPES = { XSchema.DOUBLE, XSchema.FLOAT };

   /** Evaluate with a raw JS literal as the field value (e.g. Math.fround(999.99)). */
   private static Object evalRaw(NamedRangeRef ref, String jsLiteral) {
      String script = "var abs = Math.abs; function numberToString(x) { return String(x); }\n" +
         "var field = {'" + COL + "': " + jsLiteral + "};\n" + ref.getScriptExpression();

      try(Context ctx = Context.newBuilder("js").build()) {
         Value v = ctx.eval("js", script);
         return v.isNull() ? null : v.isNumber() ? v.asDouble() : v.asString();
      }
   }

   /** Group "G" for a single-operator condition; rows grouped are those evaluating to "G". */
   private static boolean grouped(String type, Condition c, String jsValue) {
      return "G".equals(evalRaw(ref(type, "derby", list(c)), jsValue));
   }

   private static ConditionList andList(Condition a, Condition b) {
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new AttributeRef(COL), a, 0));
      list.append(new JunctionOperator(JunctionOperator.AND, 0));
      list.append(new ConditionItem(new AttributeRef(COL), b, 0));
      return list;
   }

   private static Condition ge(String type, Object v) {
      Condition c = cond(type, XCondition.GREATER_THAN, v);
      c.setEqual(true);
      return c;
   }

   private static Condition le(String type, Object v) {
      Condition c = cond(type, XCondition.LESS_THAN, v);
      c.setEqual(true);
      return c;
   }

   private static final String[] ROWS = {
      "-15.5", "-5.25", "0", "5.5", "10", "15.75", "19.99", "20", "25.5", "999.99" };

   private static String rows(String type, Condition c) {
      NamedRangeRef r = ref(type, "derby", list(c));
      return rows(r);
   }

   private static String rows(NamedRangeRef r) {
      StringBuilder sb = new StringBuilder();

      for(String row : ROWS) {
         // a FLOAT column holds the float nearest the decimal, simulate with fround
         String js = XSchema.FLOAT.equals(r.getBaseDataType()) ?
            "Math.fround(" + row + ")" : row;

         if("G".equals(evalRaw(r, js))) {
            sb.append(sb.length() > 0 ? "," : "").append(row);
         }
      }

      return sb.toString();
   }

   @Test
   void floatingLessThanIsExclusiveOfTheBound() {
      for(String t : FP_TYPES) {
         assertEquals("-15.5,-5.25,0,5.5", rows(t, cond(t, XCondition.LESS_THAN, "10")), t);
         assertEquals("-15.5,-5.25,0,5.5", rows(t, cond(t, XCondition.LESS_THAN, 10.0)), t);
         assertEquals("-15.5,-5.25,0,5.5,10", rows(t, le(t, "10")), t);
      }
   }

   @Test
   void floatingGreaterThanIsExclusiveOfTheBound() {
      for(String t : FP_TYPES) {
         assertEquals("25.5,999.99", rows(t, cond(t, XCondition.GREATER_THAN, "20")), t);
         assertEquals("20,25.5,999.99", rows(t, ge(t, "20")), t);
      }
   }

   @Test
   void floatingRangePairs() {
      for(String t : FP_TYPES) {
         // the Named Group dialog range shape: >= a AND < b
         assertEquals("10,15.75,19.99", rows(ref(t, "derby",
            andList(ge(t, "10"), cond(t, XCondition.LESS_THAN, "20")))), t);
         assertEquals("-5.25", rows(ref(t, "derby",
            andList(ge(t, "-10"), cond(t, XCondition.LESS_THAN, "0")))), t);
         // bound that is not exactly representable as a float
         assertEquals("19.99", rows(ref(t, "derby",
            andList(ge(t, "19.99"), cond(t, XCondition.LESS_THAN, "20")))), t);
      }
   }

   @Test
   void floatingBetweenNotAndEqualToUnchanged() {
      for(String t : FP_TYPES) {
         assertEquals("10,15.75,19.99,20",
            rows(t, cond(t, XCondition.BETWEEN, "10", "20")), t);
         assertEquals("10,15.75,19.99,20,25.5,999.99",
            rows(t, neg(cond(t, XCondition.LESS_THAN, "10"))), t);
         assertEquals("10", rows(t, cond(t, XCondition.EQUAL_TO, "10")), t);
         assertEquals("5.5", rows(t, cond(t, XCondition.EQUAL_TO, 5.5)), t);
      }
   }

   @Test
   void floatEqualToLargeValue() {
      // float 999.99f differs from the double 999.99 by about 1e-5
      assertEquals("999.99", rows(XSchema.FLOAT, cond(XSchema.FLOAT, XCondition.EQUAL_TO, "999.99")));
      assertEquals("19.99", rows(XSchema.FLOAT, cond(XSchema.FLOAT, XCondition.EQUAL_TO, "19.99")));
      assertEquals("999.99", rows(XSchema.DOUBLE, cond(XSchema.DOUBLE, XCondition.EQUAL_TO, "999.99")));
   }

   @Test
   void floatingNullAndNonNumericNeverMatch() {
      for(String t : FP_TYPES) {
         assertFalse(grouped(t, cond(t, XCondition.LESS_THAN, "10"), "null"), t);
         assertFalse(grouped(t, cond(t, XCondition.GREATER_THAN, "10"), "null"), t);
         assertFalse(grouped(t, cond(t, XCondition.EQUAL_TO, "10"), "null"), t);
         assertFalse(grouped(t, cond(t, XCondition.LESS_THAN, "abc"), "5"), t);
         assertFalse(grouped(t, cond(t, XCondition.LESS_THAN, "10"), "NaN"), t);
      }
   }

   @Test
   void doubleEqualToScriptUnchanged() {
      assertTrue(script(XSchema.DOUBLE, cond(XSchema.DOUBLE, XCondition.EQUAL_TO, "10"))
         .contains("field['Customer:State'] != null && abs(10.0 - field['Customer:State']) "
            + "< 0.000001 )"), script(XSchema.DOUBLE,
               cond(XSchema.DOUBLE, XCondition.EQUAL_TO, "10")));
   }
}
