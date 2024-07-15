/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest.xml.parse;

import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.ExpandedJsonTable;
import org.junit.jupiter.api.Test;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

   private final ClassLoader loader = getClass().getClassLoader();
}
