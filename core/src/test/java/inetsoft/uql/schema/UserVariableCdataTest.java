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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.asset.ExpressionValue;
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
 * <p>
 * Bug #77903: the default value of a variable ({@code <valuenode>}, {@code <valueString>} and
 * {@code <valueString2>}) was written raw into CDATA too. A default is passed to the query, so it
 * comes back exactly, from the variable's XML and from its JSON (which embeds the XML).
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

   private static final String[] DEFAULTS = {
      "x ]]> y", "arr[i[0]]>5", "a\u0001b\\x", "a\\u0001]]>b\u001F", "]]>"
   };

   @Test
   void defaultValueRoundTripsInXmlAndJson() throws Exception {
      for(String p : DEFAULTS) {
         UserVariable var = new UserVariable("p");
         // (Object) selects createValueNode(value, name), as the variable dialog does
         var.setValueNode(XValueNode.createValueNode((Object) p, "default"));

         assertEquals(p, xmlRoundTrip(var).getValueNode().getValue(), p);
         assertEquals(p, jsonRoundTrip(var).getValueNode().getValue(), p);
      }
   }

   @Test
   void expressionDefaultRoundTripsInXmlAndJson() throws Exception {
      for(String p : DEFAULTS) {
         UserVariable var = new UserVariable("p");
         ExpressionValue ev = new ExpressionValue();
         ev.setType(ExpressionValue.JAVASCRIPT);
         ev.setExpression(p);
         var.setValueNode(XValueNode.createValueNode(ev, "default", XSchema.STRING));

         for(UserVariable back : new UserVariable[] { xmlRoundTrip(var), jsonRoundTrip(var) }) {
            Object value = back.getValueNode().getValue();
            assertInstanceOf(ExpressionValue.class, value, p);
            assertEquals(p, ((ExpressionValue) value).getExpression(), p);
         }
      }
   }

   /**
    * A default with {@code ]]>} but no control character is marked too, so its backslashes
    * (including backslash-u-hex text) must come back unchanged.
    */
   @Test
   void cdataEndDefaultWithBackslashesRoundTripsInXmlAndJson() throws Exception {
      String p = "c:\\x \\u0041]]>b\\";
      UserVariable var = new UserVariable("p");
      var.setValueNode(XValueNode.createValueNode((Object) p, "default"));

      assertEquals(p, xmlRoundTrip(var).getValueNode().getValue());
      assertEquals(p, jsonRoundTrip(var).getValueNode().getValue());
   }

   @Test
   void characterDefaultWithControlCharacterRoundTrips() throws Exception {
      UserVariable var = new UserVariable("p");
      var.setValueNode(XValueNode.createValueNode((Object) Character.valueOf('\u0001'), "default"));

      assertEquals(Character.valueOf('\u0001'), xmlRoundTrip(var).getValueNode().getValue());
      assertEquals(Character.valueOf('\u0001'), jsonRoundTrip(var).getValueNode().getValue());
   }

   /**
    * A multi-value default reaches the file through {@code <valueString>} and
    * {@code <valueString2>}, which join the values.
    */
   @Test
   void multiValueDefaultWithCdataEndOrControlCharacterIsReadable() throws Exception {
      for(String p : DEFAULTS) {
         UserVariable var = new UserVariable("p");
         Object[] values = { "m" + p, "m2" };
         var.setValueNode(XValueNode.createValueNode((Object) values, "default"));

         Element elem = parse(xml(var));
         UserVariable back = new UserVariable();
         back.parseXML(elem);
         assertNotNull(back.getValueNode(), p);
         assertEquals(Tool.getDataString(values),
                      Tool.getCDATAData(elem.getElementsByTagName("valueString").item(0)), p);
         assertNotNull(jsonRoundTrip(var).getValueNode(), p);
      }
   }

   /**
    * A variable with only {@code <valueString>} (no {@code <valuenode>}) is read from it.
    */
   @Test
   void valueStringIsReadWhenThereIsNoValueNode() throws Exception {
      for(String p : DEFAULTS) {
         UserVariable var = new UserVariable("p");
         var.setValueNode(XValueNode.createValueNode((Object) p, "default"));
         String xml = xml(var).replaceAll("(?s)<valuenode.*</valuenode>", "");
         assertFalse(xml.contains("<valuenode"), xml);

         UserVariable back = new UserVariable();
         back.parseXML(parse(xml));
         assertEquals(p, back.getValueNode().getValue(), p);
      }
   }

   /**
    * An ordinary default, including backslash text that looks like an encoded character, is
    * written exactly as before (so files written before the fix read the same) and read back
    * unchanged.
    */
   @Test
   void ordinaryDefaultIsWrittenAndReadAsBefore() throws Exception {
      String p = "lit\\u0041z \\ c:\\x";
      UserVariable var = new UserVariable("p");
      var.setValueNode(XValueNode.createValueNode((Object) p, "default"));
      String xml = xml(var);

      assertTrue(xml.contains("<valuenode _node_name=\"default\" type=\"string\" null=\"false\" " +
                              "isExpression=\"false\"><![CDATA[" + p + "]]></valuenode>"), xml);
      assertTrue(xml.contains("<valueString><![CDATA[" + p + "]]></valueString>"), xml);
      assertTrue(xml.contains("<valueString2><![CDATA[" + p + "]]></valueString2>"), xml);
      assertFalse(xml.contains(Tool.XML_ILLEGAL_CHARS_ATTR), xml);
      assertEquals(p, xmlRoundTrip(var).getValueNode().getValue());
      assertEquals(p, jsonRoundTrip(var).getValueNode().getValue());

      UserVariable back = new UserVariable();
      back.parseXML(parse(xml.replaceAll("(?s)<valuenode.*</valuenode>", "")));
      assertEquals(p, back.getValueNode().getValue());
   }

   private static UserVariable xmlRoundTrip(UserVariable var) throws Exception {
      UserVariable back = new UserVariable();
      back.parseXML(parse(xml(var)));
      return back;
   }

   private static UserVariable jsonRoundTrip(UserVariable var) throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      return mapper.readValue(mapper.writeValueAsString(var), UserVariable.class);
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
