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
import inetsoft.uql.XNode;
import inetsoft.uql.schema.XValueNode;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77903: the embedded input parameter values of a stored procedure query are written by
 * {@link XValueNode#writeXML} and read back by {@code XMLUtil.createTree}, which does not use
 * the {@code XValueNode} reader. A value holding {@code ]]>} or a control character must come
 * back exactly, and an ordinary value must be written and read as before.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ProcedureSQLInputCdataTest {
   @Test
   void inputParameterValueRoundTrips() throws Exception {
      String[] values = {
         "x ]]> y", "]]>", "x]]]>>y", "a\u0001b\\x", "a\\u0001]]>b\u001F", "lit\\u0041z \\ c:\\x"
      };

      for(String p : values) {
         ProcedureSQL back = roundTrip(p);

         assertEquals(p, back.getEmbededInParameterValue("p1"), p);
         XNode node = back.getInputValue().getNode("this.p1");
         assertNull(node.getAttribute(Tool.XML_ILLEGAL_CHARS_ATTR), p);
      }
   }

   @Test
   void ordinaryInputParameterValueIsWrittenAsBefore() throws Exception {
      String p = "lit\\u0041z \\ c:\\x";
      String xml = xml(procedure(p));

      assertTrue(xml.contains("<valuenode _node_name=\"p1\" type=\"string\" null=\"false\" " +
                              "isExpression=\"false\"><![CDATA[" + p + "]]></valuenode>"), xml);
      assertFalse(xml.contains(Tool.XML_ILLEGAL_CHARS_ATTR), xml);
   }

   private static ProcedureSQL procedure(String value) {
      ProcedureSQL sql = new ProcedureSQL();
      sql.setName("proc1");
      sql.addParameterDefinition("p1", ProcedureSQL.IN, Types.VARCHAR, "VARCHAR");
      XNode input = sql.getInputType().newInstance();
      ((XValueNode) input.getChild("p1")).setValue(value);
      sql.setInputValue(input);
      return sql;
   }

   private static ProcedureSQL roundTrip(String value) throws Exception {
      String xml = xml(procedure(value));
      ProcedureSQL back = new ProcedureSQL();
      back.parseXML(Tool.parseXML(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                       .getDocumentElement());
      return back;
   }

   private static String xml(ProcedureSQL sql) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      sql.writeXML(writer);
      writer.flush();
      return buf.toString();
   }
}
