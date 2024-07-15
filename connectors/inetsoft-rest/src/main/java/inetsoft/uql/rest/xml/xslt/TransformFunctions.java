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
package inetsoft.uql.rest.xml.xslt;

import inetsoft.uql.rest.xml.XMLIterationStreamTransformer;
import inetsoft.uql.rest.xml.XMLPagedStreamTransformer;
import inetsoft.uql.rest.xml.parse.DocumentParser;
import org.w3c.dom.Node;

/**
 * Contains functions meant to be called by xalan while iterating over an xslt template.
 */
@SuppressWarnings("unused")
public class TransformFunctions {
   private TransformFunctions() {
   }

   /**
    * Adds a node to the document parser.
    *
    * @param node   the node to add.
    * @param parser the document parser to add the node to.
    */
   public static void addNode(Node node, Object parser) {
      ((DocumentParser) parser).append(node);
   }

   /**
    * @param node         the node read the page count from.
    * @param transformer  the transformer to set the page count of.
    */
   public static void setPageCount(Node node, Object transformer)
   {
      ((XMLPagedStreamTransformer) transformer).setPageCount(node);
   }

   /**
    * @param node the node to read the page offset from.
    * @param transformer  the transformer to set the page offset of.
    */
   public static void setPageOffsetParam(Node node, Object transformer) {
      ((XMLIterationStreamTransformer) transformer).setPageOffsetParam(node.getTextContent());
   }

   /**
    * @param node the node to check hasNext from.
    * @param transformer  the transformer to set the hasNext of.
    */
   public static void setHasNext(Node node, Object transformer) {
      ((XMLIterationStreamTransformer) transformer).setHasNext(node.hasChildNodes());
   }

   public static void log(Node node) {
      System.out.println(node.getTextContent());
   }
}
