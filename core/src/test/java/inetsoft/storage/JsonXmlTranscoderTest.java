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
package inetsoft.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class JsonXmlTranscoderTest {

   private JsonXmlTranscoder transcoder;
   private ObjectMapper mapper;

   @BeforeEach
   void setUp() {
      transcoder = new JsonXmlTranscoder();
      mapper = new ObjectMapper();
   }

   // ---- Helper: parse XML string into Document ----

   private Document parseXml(String xml) throws Exception {
      DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      return builder.parse(new InputSource(new StringReader(xml)));
   }

   // ---- transcodeToJson ----

   @Test
   void transcodeToJson_simpleElement_producesObjectNode() throws Exception {
      Document doc = parseXml("<root/>");
      ObjectNode result = transcoder.transcodeToJson(doc);
      assertNotNull(result);
      assertTrue(result.isObject());
   }

   @Test
   void transcodeToJson_elementWithAttribute_prefixedWithAt() throws Exception {
      Document doc = parseXml("<root id=\"42\"/>");
      ObjectNode result = transcoder.transcodeToJson(doc);
      assertTrue(result.has("@id"));
      assertEquals("42", result.get("@id").asText());
   }

   @Test
   void transcodeToJson_elementWithMultipleAttributes_allPrefixed() throws Exception {
      Document doc = parseXml("<root name=\"test\" version=\"1.0\"/>");
      ObjectNode result = transcoder.transcodeToJson(doc);
      assertEquals("test", result.get("@name").asText());
      assertEquals("1.0", result.get("@version").asText());
   }

   @Test
   void transcodeToJson_elementWithTextContent_storedAsHashText() throws Exception {
      Document doc = parseXml("<root>hello world</root>");
      ObjectNode result = transcoder.transcodeToJson(doc);
      assertTrue(result.has("#text"));
      assertEquals("hello world", result.get("#text").asText());
   }

   @Test
   void transcodeToJson_nestedElement_producedAsNestedObject() throws Exception {
      Document doc = parseXml("<root><child/></root>");
      ObjectNode result = transcoder.transcodeToJson(doc);
      assertTrue(result.has("child"));
      assertTrue(result.get("child").isObject());
   }

   @Test
   void transcodeToJson_multipleSameNameChildren_producedAsArray() throws Exception {
      Document doc = parseXml("<root><item/><item/><item/></root>");
      ObjectNode result = transcoder.transcodeToJson(doc);
      assertTrue(result.has("item"));
      assertTrue(result.get("item").isArray());
      assertEquals(3, result.get("item").size());
   }

   @Test
   void transcodeToJson_twoSameNameChildren_producedAsArray() throws Exception {
      Document doc = parseXml("<root><item id=\"1\"/><item id=\"2\"/></root>");
      ObjectNode result = transcoder.transcodeToJson(doc);
      assertTrue(result.get("item").isArray());
      ArrayNode items = (ArrayNode) result.get("item");
      assertEquals("1", items.get(0).get("@id").asText());
      assertEquals("2", items.get(1).get("@id").asText());
   }

   @Test
   void transcodeToJson_nestedWithAttributeAndText_preservedCorrectly() throws Exception {
      Document doc = parseXml("<root><child type=\"main\">content</child></root>");
      ObjectNode result = transcoder.transcodeToJson(doc);
      ObjectNode child = (ObjectNode) result.get("child");
      assertEquals("main", child.get("@type").asText());
      assertEquals("content", child.get("#text").asText());
   }

   @Test
   void transcodeToJson_cdataSection_capturedAsText() throws Exception {
      Document doc = parseXml("<root><![CDATA[raw data]]></root>");
      ObjectNode result = transcoder.transcodeToJson(doc);
      assertTrue(result.has("#text"));
      assertEquals("raw data", result.get("#text").asText());
   }

   // ---- transcodeToXml ----

   @Test
   void transcodeToXml_emptyObject_createsRootElementOnly() throws Exception {
      ObjectNode json = mapper.createObjectNode();
      Document doc = transcoder.transcodeToXml(json, "root");
      assertEquals("root", doc.getDocumentElement().getTagName());
   }

   @Test
   void transcodeToXml_attributeField_setsXmlAttribute() throws Exception {
      ObjectNode json = mapper.createObjectNode();
      json.put("@id", "99");
      Document doc = transcoder.transcodeToXml(json, "element");
      Element root = doc.getDocumentElement();
      assertEquals("99", root.getAttribute("id"));
   }

   @Test
   void transcodeToXml_textField_createsCdataSection() throws Exception {
      ObjectNode json = mapper.createObjectNode();
      json.put("#text", "some text");
      Document doc = transcoder.transcodeToXml(json, "element");
      Element root = doc.getDocumentElement();
      assertEquals("some text", root.getTextContent());
   }

   @Test
   void transcodeToXml_nestedObject_createsChildElement() throws Exception {
      ObjectNode json = mapper.createObjectNode();
      json.set("child", mapper.createObjectNode());
      Document doc = transcoder.transcodeToXml(json, "root");
      Element root = doc.getDocumentElement();
      NodeList children = root.getChildNodes();
      boolean hasChildElement = false;
      for(int i = 0; i < children.getLength(); i++) {
         if(children.item(i).getNodeType() == Node.ELEMENT_NODE &&
            "child".equals(children.item(i).getNodeName()))
         {
            hasChildElement = true;
         }
      }
      assertTrue(hasChildElement);
   }

   @Test
   void transcodeToXml_arrayField_createsRepeatedElements() throws Exception {
      ObjectNode json = mapper.createObjectNode();
      ArrayNode array = mapper.createArrayNode();
      array.add(mapper.createObjectNode());
      array.add(mapper.createObjectNode());
      json.set("item", array);
      Document doc = transcoder.transcodeToXml(json, "root");
      Element root = doc.getDocumentElement();
      NodeList items = root.getElementsByTagName("item");
      assertEquals(2, items.getLength());
   }

   @Test
   void transcodeToXml_rootTagName_setCorrectly() throws Exception {
      ObjectNode json = mapper.createObjectNode();
      Document doc = transcoder.transcodeToXml(json, "myRoot");
      assertEquals("myRoot", doc.getDocumentElement().getTagName());
   }

   // ---- round-trip ----

   @Test
   void roundTrip_simpleElement_preservesStructure() throws Exception {
      String xml = "<config version=\"2.0\"><setting name=\"timeout\">30</setting></config>";
      Document originalDoc = parseXml(xml);

      // XML -> JSON
      ObjectNode json = transcoder.transcodeToJson(originalDoc);

      // JSON -> XML
      Document roundTripped = transcoder.transcodeToXml(json, "config");

      Element root = roundTripped.getDocumentElement();
      assertEquals("config", root.getTagName());
      assertEquals("2.0", root.getAttribute("version"));

      NodeList settings = root.getElementsByTagName("setting");
      assertEquals(1, settings.getLength());
      Element setting = (Element) settings.item(0);
      assertEquals("timeout", setting.getAttribute("name"));
      assertEquals("30", setting.getTextContent());
   }

   @Test
   void roundTrip_multipleChildrenWithSameTag_preservedAsArray() throws Exception {
      String xml = "<list><entry>a</entry><entry>b</entry></list>";
      Document originalDoc = parseXml(xml);
      ObjectNode json = transcoder.transcodeToJson(originalDoc);
      Document roundTripped = transcoder.transcodeToXml(json, "list");

      NodeList entries = roundTripped.getDocumentElement().getElementsByTagName("entry");
      assertEquals(2, entries.getLength());
   }

   @Test
   void roundTrip_attributesPreserved() throws Exception {
      String xml = "<root attr1=\"val1\" attr2=\"val2\"/>";
      Document originalDoc = parseXml(xml);
      ObjectNode json = transcoder.transcodeToJson(originalDoc);
      Document roundTripped = transcoder.transcodeToXml(json, "root");

      Element root = roundTripped.getDocumentElement();
      assertEquals("val1", root.getAttribute("attr1"));
      assertEquals("val2", root.getAttribute("attr2"));
   }
}
