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
package inetsoft.uql.schema;

import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77891, #77892: the choices of a query variable were written raw into CDATA, so a
 * choice label or value holding {@code ]]>} or a control character made the variable (and the
 * query or worksheet that holds it) unreadable. A choice value is passed to the query, so it
 * comes back exactly; a choice label comes back with each control character replaced by a space.
 */
@Tag("core")
class UserVariableCdataTest {
   @Test
   void choiceLabelsAndValuesRoundTrip() throws Exception {
      UserVariable var = new UserVariable("p");
      var.setSortValue(false);
      var.setChoices(new Object[] { "c]]>1", "c\u00012", "plain" });
      var.setValues(new Object[] { "v]]>1", "v\u00012\\x", "plain" });

      UserVariable back = new UserVariable();
      back.setSortValue(false);
      back.parseXML(parse(xml(var)));

      assertArrayEquals(new Object[] { "c]]>1", "c 2", "plain" }, back.getChoices());
      assertArrayEquals(new Object[] { "v]]>1", "v\u00012\\x", "plain" }, back.getValues());
   }

   @Test
   void ordinaryChoicesAreWrittenAsBefore() throws Exception {
      UserVariable var = new UserVariable("p");
      var.setSortValue(false);
      var.setChoices(new Object[] { "East" });
      var.setValues(new Object[] { "c:\\east \\u0001" });

      String xml = xml(var);

      assertTrue(xml.contains("<choice><item><![CDATA[East]]></item>" +
                              "<value><![CDATA[c:\\east \\u0001]]></value></choice>"), xml);
      UserVariable back = new UserVariable();
      back.setSortValue(false);
      back.parseXML(parse(xml));
      assertArrayEquals(new Object[] { "c:\\east \\u0001" }, back.getValues());
   }

   private static String xml(UserVariable var) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      var.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   private static Element parse(String xml) throws Exception {
      return Tool.parseXML(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
   }
}
