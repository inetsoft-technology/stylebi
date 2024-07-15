/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.util.xml;

import org.w3c.dom.*;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Class which replicates simple XPath functionality, such as looking up an XML Path.
 *
 * The <b>*</b> wildcard matches any element.
 * The <b>**</b> wildcard matches any sequence of elements.
 *
 * Example usage:
 *
 * <ul>
 *    <li>getChildNodesByTagNamePath(elem, "child", "grandChild");</li>
 *    <li>getChildNodesByTagNamePath(elem, "*", "grandChild");</li>
 *    <li>getChildNodesByTagNamePath(elem, "**", "distantDescendant");</li>
 * </ul>
 *
 */
public class XPathLikeProcessor {
   /**
    * Searches through the children of elem looking for tags matching the hierarchy provided by
    * the tag sequence in names.
    *
    * @param elem  the root element to search within
    * @param names the tag sequence to search.
    * @return the matching node list.
    */
   public NodeList getChildNodesByTagNamePath(Element elem, String... names) {
      final NodeListImpl matchingChildren = new NodeListImpl();
      getChildNodesByTagNamePath(matchingChildren::addItem, elem, names);
      return matchingChildren;
   }

   /**
    * Searches through the children of elem looking for tags matching the hierarchy provided by
    * the tag sequence in names.
    *
    * @param consumer the consumer that will be called for every matched element.
    * @param elem     the root element to search within
    * @param names    the tag sequence to search.
    */
   public void getChildNodesByTagNamePath(Consumer<Element> consumer, Element elem,
                                          String... names)
   {
      getChildNodesByTagNamePathDeep(consumer, elem, Arrays.asList(names));
   }

   private void getChildNodesByTagNamePathDeep(Consumer<Element> consumer, Node parent,
                                               List<String> names)
   {
      if(names.size() == 0) {
         return;
      }

      final NodeList childNodes = parent.getChildNodes();
      final boolean last = names.size() == 1;
      final String name = names.get(0);
      final int len = childNodes.getLength();

      for(int i = 0; i < len; i++) {
         if(childNodes.item(i) instanceof Element) {
            // remove instanceof is slightly faster
            Element node = (Element) childNodes.item(i);

            if(ELEMENT_WILDCARD.equals(name) || node.getTagName().equals(name)) {
               if(last) {
                  consumer.accept(node);
               }
               else {
                  getChildNodesByTagNamePathDeep(consumer, node, names.subList(1, names.size()));
               }
            }
            else if(ELEM_SEQUENCE_WILDCARD.equals(name)) {
               if(names.size() >= 2 && node.getTagName().equals(names.get(1))) {
                  if(names.size() == 2) {
                     consumer.accept(node);
                  }

                  getChildNodesByTagNamePathDeep(consumer, node,
                                                 names.subList(2, names.size()));

               }

               getChildNodesByTagNamePathDeep(consumer, node, names);
            }
         }
      }
   }

   private static final String ELEMENT_WILDCARD = "*";
   private static final String ELEM_SEQUENCE_WILDCARD = "**";
}
