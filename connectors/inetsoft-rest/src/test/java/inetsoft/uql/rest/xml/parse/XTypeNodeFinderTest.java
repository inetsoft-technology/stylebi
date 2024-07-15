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

import inetsoft.uql.rest.xml.schema.XSDParserWrapper;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.util.CoreTool;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.xpath.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled
class XTypeNodeFinderTest {
   @Test
   void testXPathWithTypes() throws Exception {
      final XSDParserWrapper xsdParser = new XSDParserWrapper();
      final XTypeNode root = xsdParser.parse(loader.getResourceAsStream("company.xsd"));

      final Document document = CoreTool.parseXML(loader.getResourceAsStream("company.xml"));
      final XPath xPath = XPathFactory.newInstance().newXPath();
      final XPathExpression expr = xPath.compile("SpringAhead/Company/Id");
      final Node node = (Node) expr.evaluate(document, XPathConstants.NODE);

      final XTypeNodeFinder matcher = new XTypeNodeFinder();
      final XTypeNode xTypeNode = matcher.find(node, root);
      assertEquals(XSchema.INTEGER, xTypeNode.getType());
   }

   private final ClassLoader loader = getClass().getClassLoader();
}
