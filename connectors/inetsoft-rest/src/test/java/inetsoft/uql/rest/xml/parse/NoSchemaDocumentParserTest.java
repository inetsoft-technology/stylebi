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
package inetsoft.uql.rest.xml.parse;

import inetsoft.test.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.ExpandedJsonTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, CredentialTestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class NoSchemaDocumentParserTest {
   @Test
   void parse() throws Exception {
      final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
      docFactory.setNamespaceAware(true);
      final DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
      final Document document = docBuilder.parse(loader.getResourceAsStream("company.xml"));

      final XPath xPath = XPathFactory.newInstance().newXPath();
      final XPathExpression expr = xPath.compile("SpringAhead/Company");
      final NodeList nodes = (NodeList) expr.evaluate(document, XPathConstants.NODESET);

      final NoSchemaDocumentParser parser = new NoSchemaDocumentParser();

      for(int i = 0; i < nodes.getLength(); i++) {
         final Node node = nodes.item(i);
         parser.append(node);
      }

      final Object rootObj = parser.getRoot();
      final ExpandedJsonTable table = new ExpandedJsonTable();

      table.load(rootObj);
      assertEquals(XSchema.INTEGER, table.getColumnType("Company.Id"));
      assertEquals(XSchema.STRING, table.getColumnType("Company.Name"));
   }

   @Test
   void attributesBecomeEntries() throws Exception {
      final Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new org.xml.sax.InputSource(new java.io.StringReader(
            "<c id=\"1\" xmlns:x=\"urn:x\"><name>A</name><r k=\"v\">t</r></c>")));
      final NoSchemaDocumentParser parser = new NoSchemaDocumentParser();
      parser.append(document.getDocumentElement());

      final MapNode c = (MapNode) ((MapNode) parser.getRoot()).get("c");
      assertEquals(java.util.Arrays.asList("name", "r", "r@k", "@id"),
         new java.util.ArrayList<>(c.keySet()));
      assertEquals(new ValueNode(1), c.get("@id"));
      assertEquals(new ValueNode("v"), c.get("r@k"));
      assertEquals(new ValueNode("t"), c.get("r"));
   }

   @Test
   void matchedAttributeNodeYieldsValue() throws Exception {
      final Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new org.xml.sax.InputSource(new java.io.StringReader("<c total=\"296\"/>")));
      final NoSchemaDocumentParser parser = new NoSchemaDocumentParser();
      parser.append(document.getDocumentElement().getAttributeNode("total"));

      assertEquals(new ValueNode(296), ((MapNode) parser.getRoot()).get("@total"));
   }

   @Test
   void optionalAttributeOnRepeatedLeafStaysIndexAligned() throws Exception {
      final MapNode x = parseRoot(
         "<x><y a=\"1\">p</y><y>q</y><y a=\"3\">r</y><y>s</y></x>", "x");

      assertEquals(java.util.Arrays.asList(new ValueNode("p"), new ValueNode("q"),
         new ValueNode("r"), new ValueNode("s")), x.get("y"));
      assertEquals(java.util.Arrays.asList(new ValueNode(1), new ValueNode(null),
         new ValueNode(3), new ValueNode(null)), x.get("y@a"));
   }

   @Test
   void attributeFirstSeenOnLaterRepeatedLeafIsLeftPadded() throws Exception {
      final MapNode x = parseRoot("<x><y>p</y><y a=\"2\">q</y></x>", "x");

      assertEquals(java.util.Arrays.asList(new ValueNode(null), new ValueNode(2)), x.get("y@a"));
   }

   @Test
   void optionalAttributeOnRepeatedMapElementsKeepsOwnMaps() throws Exception {
      final MapNode x = parseRoot(
         "<x><c><n>1</n></c><c b=\"B\"><n>2</n></c></x>", "x");
      final java.util.List<?> cs = (java.util.List<?>) x.get("c");

      assertEquals(false, ((MapNode) cs.get(0)).containsKey("@b"));
      assertEquals(new ValueNode("B"), ((MapNode) cs.get(1)).get("@b"));
   }

   private MapNode parseRoot(String xml, String name) throws Exception {
      final Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
      final NoSchemaDocumentParser parser = new NoSchemaDocumentParser();
      parser.append(document.getDocumentElement());
      return (MapNode) ((MapNode) parser.getRoot()).get(name);
   }

   private final ClassLoader loader = getClass().getClassLoader();
}
