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
import inetsoft.uql.XNode;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77735, the truth value of x IS [NOT] TRUE/FALSE/UNKNOWN is an XUnaryCondition with an
 * empty op, which is written as an empty CDATA and was read back as null, so generating the
 * loaded query failed with a NullPointerException. A statement with a truth test fails to
 * parse now, but a query saved before that keeps its parsed tree (#77477), so the op is read
 * back as "" again. The XML here was written by the parser before the refusal.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XUnaryConditionTruthTestXmlTest {
   // select a.x from a where (a.k = 1) is not true, without its sql string
   private static final String SAVED_IS_NOT_TRUE = """
      <uniform_sql parse="true">
      <table>
      <alias><![CDATA[a]]></alias>
      <name><![CDATA[a]]></name>
      <issql><![CDATA[false]]></issql>
      <xlocation><![CDATA[-1]]></xlocation>
      <ylocation><![CDATA[-1]]></ylocation>
      <xScroll><![CDATA[0]]></xScroll>
      <yScroll><![CDATA[0]]></yScroll>
      </table>
      <column>
      <![CDATA[a.x]]>
      <alias><![CDATA[]]></alias>
      <type><![CDATA[]]></type>
      <table><![CDATA[]]></table>
      <description><![CDATA[]]></description>
      <isExp><![CDATA[false]]></isExp>
      </column>
      <where>
      <XSet relation="and" isnot="false" group="false" clause="where">
      <XSet relation="and" isnot="false" group="false" clause="">
      <XSet relation="and" isnot="false" group="false" clause="">
      <XSet relation="is not" isnot="false" group="false" clause="where">
      <XBinaryCondition isnot="false" clause="" containsNull="false">
      <expression1>
      <expression type="Field">
      <![CDATA[a.k]]>
      </expression>
      </expression1>
      <expression2>
      <expression type="Value">
      <![CDATA[1]]>
      </expression>
      </expression2>
      <op><![CDATA[=]]></op>
      </XBinaryCondition>
      <XUnaryCondition isnot="false" clause= "">
      <expression1>
      <expression type="Expression">
      <![CDATA[true]]>
      </expression>
      </expression1>
      <op><![CDATA[]]></op>
      </XUnaryCondition>
      </XSet>
      </XSet>
      <XSet relation="or" isnot="false" group="false" clause="">
      </XSet>
      </XSet>
      </XSet>
      </where>
      <sortby></sortby>
      <groupby></groupby>
      <having>
      </having>
      </uniform_sql>
      """;

   // select a.x from a where a.k = 1 and (a.id = $(p)) is false, without its sql string
   private static final String SAVED_PARAM_IS_FALSE = """
      <uniform_sql parse="true">
      <table>
      <alias><![CDATA[a]]></alias>
      <name><![CDATA[a]]></name>
      <issql><![CDATA[false]]></issql>
      <xlocation><![CDATA[-1]]></xlocation>
      <ylocation><![CDATA[-1]]></ylocation>
      <xScroll><![CDATA[0]]></xScroll>
      <yScroll><![CDATA[0]]></yScroll>
      </table>
      <column>
      <![CDATA[a.x]]>
      <alias><![CDATA[]]></alias>
      <type><![CDATA[]]></type>
      <table><![CDATA[]]></table>
      <description><![CDATA[]]></description>
      <isExp><![CDATA[false]]></isExp>
      </column>
      <where>
      <XSet relation="and" isnot="false" group="false" clause="where">
      <XSet relation="and" isnot="false" group="false" clause="">
      <XSet relation="and" isnot="false" group="false" clause="">
      <XSet relation="and" isnot="false" group="false" clause="where">
      <XBinaryCondition isnot="false" clause="" containsNull="false">
      <expression1>
      <expression type="Field">
      <![CDATA[a.k]]>
      </expression>
      </expression1>
      <expression2>
      <expression type="Value">
      <![CDATA[1]]>
      </expression>
      </expression2>
      <op><![CDATA[=]]></op>
      </XBinaryCondition>
      <XSet relation="is" isnot="false" group="false" clause="">
      <XBinaryCondition isnot="false" clause="" containsNull="false">
      <expression1>
      <expression type="Field">
      <![CDATA[a.id]]>
      </expression>
      </expression1>
      <expression2>
      <expression type="Expression">
      <![CDATA[$(p)]]>
      </expression>
      </expression2>
      <op><![CDATA[=]]></op>
      </XBinaryCondition>
      <XUnaryCondition isnot="false" clause= "">
      <expression1>
      <expression type="Expression">
      <![CDATA[false]]>
      </expression>
      </expression1>
      <op><![CDATA[]]></op>
      </XUnaryCondition>
      </XSet>
      </XSet>
      </XSet>
      <XSet relation="or" isnot="false" group="false" clause="">
      </XSet>
      </XSet>
      </XSet>
      </where>
      <sortby></sortby>
      <groupby></groupby>
      <having>
      </having>
      </uniform_sql>
      """;

   @Test
   void savedTruthTestGenerates() throws Exception {
      UniformSQL sql = load(SAVED_IS_NOT_TRUE);
      XSet test = findTruthTest(sql.getWhere());
      assertNotNull(test);
      assertEquals("", ((XUnaryCondition) test.getChild(1)).getOp());
      assertFalse(sql.hasSQLString());

      assertEquals("select a.x from a where ((a.k = 1) is not true)", generate(sql));
      // the parameter check walks the conditions too
      VariableTable vars = new VariableTable();
      XUtil.validateConditions(null, sql, vars, true, false);
      assertEquals("select a.x from a where ((a.k = 1) is not true)", generate(sql));
   }

   // saved again, and loaded again
   @Test
   void savedTruthTestRoundTrips() throws Exception {
      UniformSQL sql = load(save(load(SAVED_IS_NOT_TRUE)));
      assertEquals("", ((XUnaryCondition) findTruthTest(sql.getWhere()).getChild(1)).getOp());
      assertEquals("select a.x from a where ((a.k = 1) is not true)", generate(sql));
   }

   // an unset parameter removes the whole truth test (#77738), a set one keeps it
   @Test
   void savedTruthTestWithParameter() throws Exception {
      UniformSQL sql = load(SAVED_PARAM_IS_FALSE);
      assertEquals("select a.x from a where a.k = 1 and ((a.id = $(p)) is false)",
                   generate(sql));

      XUtil.validateConditions(null, sql, new VariableTable(), true, false);
      assertEquals("select a.x from a where a.k = 1", generate(sql));

      sql = load(SAVED_PARAM_IS_FALSE);
      VariableTable vars = new VariableTable();
      vars.put("p", 2);
      XUtil.validateConditions(null, sql, vars, true, false);
      assertEquals("select a.x from a where a.k = 1 and ((a.id = $(p)) is false)",
                   generate(sql));
   }

   // a query saved with its sql string is lossy now, so the sql string runs
   @Test
   void savedTruthTestWithSqlString() throws Exception {
      String text = "select a.x from a where (a.k = 1) is not true";
      UniformSQL sql = load(SAVED_IS_NOT_TRUE.replace("</uniform_sql>",
         "<sqlstring parseResult=\"0\"><![CDATA[" + text + "]]></sqlstring>\n</uniform_sql>"));
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertTrue(sql.isLossy());
      assertEquals(text, sql.getSQLString());
   }

   @Test
   void opOfCondition() throws Exception {
      assertEquals("", op("<op><![CDATA[]]></op>"));
      assertEquals("", op("<op></op>"));
      // a missing op defaults to ""
      assertEquals("", op(""));
      // the stored name of IS NULL
      assertEquals("IS NULL", op("<op><![CDATA[null]]></op>"));
   }

   private static String op(String op) throws Exception {
      String xml = "<XUnaryCondition isnot=\"false\" clause=\"\"><expression1>" +
         "<expression type=\"Field\"><![CDATA[a.k]]></expression></expression1>" + op +
         "</XUnaryCondition>";
      XUnaryCondition condition = new XUnaryCondition();
      condition.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      return condition.getOp();
   }

   private static UniformSQL load(String xml) throws Exception {
      Element root = Tool.parseXML(new StringReader(xml)).getDocumentElement();
      UniformSQL sql = new UniformSQL();
      sql.parseXML(root);
      return sql;
   }

   private static String save(UniformSQL sql) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      String xml = buffer.toString();
      assertTrue(xml.contains("<op><![CDATA[]]></op>"), xml);
      return xml;
   }

   private static String generate(UniformSQL sql) {
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static XSet findTruthTest(XNode node) {
      if(node instanceof XSet set && SQLHelper.isTruthTest(set)) {
         return set;
      }

      for(int i = 0; node != null && i < node.getChildCount(); i++) {
         XSet test = findTruthTest(node.getChild(i));

         if(test != null) {
            return test;
         }
      }

      return null;
   }
}
