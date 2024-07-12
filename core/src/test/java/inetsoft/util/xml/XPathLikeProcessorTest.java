/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util.xml;

import org.junit.jupiter.api.Test;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XPathLikeProcessorTest {
   @Test
   public void simplePathMatching() throws ParserConfigurationException {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      final Document doc = factory.newDocumentBuilder().newDocument();
      final Element root = doc.createElement("root");
      final Node childL1 = root.appendChild(doc.createElement("childL1"));
      final Node childL2 = childL1.appendChild(doc.createElement("childL2"));
      final Node childL3 = childL2.appendChild(doc.createElement("childL3"));
      final Node childL4 = childL3.appendChild(doc.createElement("childL4"));
      final Node childL5 = childL4.appendChild(doc.createElement("childL5"));

      NodeList nodes =
         processor.getChildNodesByTagNamePath(root, "childL1", "childL2",
                                              "childL3", "childL4", "childL5");

      assertEquals(1, nodes.getLength());
      assertEquals(childL5, nodes.item(0));
   }

   @Test
   public void wildcardElementMatchesSingleElement() throws ParserConfigurationException {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      final Document doc = factory.newDocumentBuilder().newDocument();
      final Element root = doc.createElement("root");
      final Node childL1 = root.appendChild(doc.createElement("childL1"));
      final Node childL2 = childL1.appendChild(doc.createElement("childL2"));
      final Node childL3 = childL2.appendChild(doc.createElement("childL3"));
      final Node childL4 = childL3.appendChild(doc.createElement("childL4"));

      NodeList nodes = processor.getChildNodesByTagNamePath(root, "*",
                                                            "childL2",
                                                            "*",
                                                            "childL4");

      assertEquals(1, nodes.getLength());
      assertEquals(childL4, nodes.item(0));

      nodes = processor.getChildNodesByTagNamePath(root, "*", "childL3");
      assertEquals(0, nodes.getLength());
   }

   @Test
   public void wildcardSequenceMatchesOneOrManyElements() throws ParserConfigurationException {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      final Document doc = factory.newDocumentBuilder().newDocument();
      final Element root = doc.createElement("root");
      final Node childL1 = root.appendChild(doc.createElement("childL1"));
      final Node childL2 = childL1.appendChild(doc.createElement("childL2"));
      final Node childL3 = childL2.appendChild(doc.createElement("childL3"));
      final Node childL4 = childL3.appendChild(doc.createElement("childL4"));
      final Node childL5 = childL4.appendChild(doc.createElement("childL5"));

      NodeList nodes = processor.getChildNodesByTagNamePath(root, "**",
                                                            "childL2",
                                                            "**",
                                                            "childL5");
      assertEquals(1, nodes.getLength());
      assertEquals(childL5, nodes.item(0));
   }

   @Test
   public void wildcardsMustMatchAnElement() throws ParserConfigurationException {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      final Document doc = factory.newDocumentBuilder().newDocument();
      final Element root = doc.createElement("root");

      NodeList nodes = processor.getChildNodesByTagNamePath(root, "*", "root");
      assertEquals(0, nodes.getLength());

      nodes = processor.getChildNodesByTagNamePath(root, "**", "root");
      assertEquals(0, nodes.getLength());
   }

   @Test
   public void nestedWildcardSelection() throws ParserConfigurationException {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      final Document doc = factory.newDocumentBuilder().newDocument();
      final Element root = doc.createElement("root");
      final Node childL1 = root.appendChild(doc.createElement("childL1"));
      final Node childL2 = childL1.appendChild(doc.createElement("childL2"));
      final Node childL3 = childL2.appendChild(doc.createElement("childL3"));
      final Node childL22 = childL3.appendChild(doc.createElement("childL2"));

      NodeListImpl nodes =
         (NodeListImpl) processor.getChildNodesByTagNamePath(root, "**", "childL2");

      assertEquals(2, nodes.getLength());
      assertEquals(childL2, nodes.item(0));
      assertEquals(childL22, nodes.item(1));
   }

   final XPathLikeProcessor processor = new XPathLikeProcessor();
}