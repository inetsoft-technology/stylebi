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
package inetsoft.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77891, #77892: the CDATA helpers for user text. {@link Tool#cdataText} is lossy
 * (XML-illegal characters become a space, {@code ]]>} is split); {@link Tool#cdataDataAttr},
 * {@link Tool#cdataData} and {@link Tool#getCDATAData} are the lossless marker pair.
 */
@Tag("core")
class ToolCdataTextTest {
   @Test
   void cdataTextReplacesOnlyXmlIllegalCharsWithOneSpaceEach() {
      assertEquals("a b c d e", Tool.cdataText("a\u0001b\u001Fc\uFFFEd\uFFFFe"));
      assertEquals("a  b", Tool.cdataText("a\u0000\u0002b"));
   }

   @Test
   void cdataTextKeepsTabNewlinesC1AndDel() {
      String text = "\tif(a) {\r\n\t\tb = '\u0085\u007F';\n}";
      assertSame(text, Tool.cdataText(text));
   }

   @Test
   void cdataTextSplitsCdataEnd() {
      assertEquals("x ]]]]><![CDATA[> y", Tool.cdataText("x ]]> y"));
      assertEquals("x ]]]]><![CDATA[> y ", Tool.cdataText("x ]]> y\u0001"));
   }

   @Test
   void cdataTextReturnsPlainTextAndNullUnchanged() {
      String text = "Sales [Q1] > 0 \\u0001 c:\\new";
      assertSame(text, Tool.cdataText(text));
      assertNull(Tool.cdataText(null));
      assertNull(Tool.replaceXMLIllegalChars(null));
   }

   @Test
   void cdataDataLeavesPlainTextUnmarked() {
      String text = "a\\b \\u0001 [x] > y";
      assertEquals("", Tool.cdataDataAttr(text));
      assertSame(text, Tool.cdataData(text));
      assertEquals("", Tool.cdataDataAttr(null));
      assertNull(Tool.cdataData(null));
      assertEquals("x]]]]><![CDATA[>y", Tool.cdataData("x]]>y"));
      assertEquals("", Tool.cdataDataAttr("x]]>y"));
   }

   @Test
   void cdataDataEncodesAndMarksXmlIllegalChars() {
      String value = "a\u0001b\\c]]>d";
      assertEquals(" ctrlEncoded=\"true\"", Tool.cdataDataAttr(value));
      assertEquals("a\\u0001b\\\\c]]]]><![CDATA[>d", Tool.cdataData(value));
   }

   @Test
   void getCDATADataRoundTripsMarkedValuesExactly() throws Exception {
      String[] values = {
         "a\u0001b", "a\u0001b\\c \\u0001 ]]> \uFFFF", "\uD83D\uDE00\u0002", "plain\\u0001",
         "x ]]> y", "\t\u0085\u007F"
      };

      for(String value : values) {
         String xml = "<value" + Tool.cdataDataAttr(value) + "><![CDATA[" +
            Tool.cdataData(value) + "]]></value>";
         assertEquals(value, Tool.getCDATAData(parse(xml)), xml);
      }
   }

   @Test
   void getCDATADataDoesNotDecodeUnmarkedValues() throws Exception {
      assertEquals("a\\u0001b \\\\ c:\\new",
                   Tool.getCDATAData(parse("<value><![CDATA[a\\u0001b \\\\ c:\\new]]></value>")));
      assertNull(Tool.getCDATAData(parse("<value></value>")));
   }

   private static Element parse(String xml) throws Exception {
      return Tool.parseXML(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
   }
}
