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
package inetsoft.uql.viewsheet.graph;

import inetsoft.report.filter.HighlightGroup;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.stream.Stream;

/**
 * HighlightRef, a data ref that supports defining a highlight.
 *
 * @author InetSoft Technology Corp.
 * @version 13.1
 */
public interface HighlightRef extends AssetObject, DataRef {
   /**
    * Get the highlight group of this ref.
    */
   HighlightGroup getHighlightGroup();

   /**
    * Set the highlight group.
    */
   void setHighlightGroup(HighlightGroup group);

   /**
    * Get the highlight for the text label of this ref.
    */
   HighlightGroup getTextHighlightGroup();

   /**
    * Set the highlight for the text label of this ref.
    */
   void setTextHighlightGroup(HighlightGroup group);

   /**
    * Returh the highlights defined in this ref.
    */
   default Stream<HighlightGroup> highlights() {
      return Stream.of(getHighlightGroup(), getTextHighlightGroup()).filter(a -> a != null);
   }

   default void writeHighlightGroup(PrintWriter writer) {
      HighlightGroup hl = getHighlightGroup();

      if(hl != null) {
         hl.writeXML(writer);
      }

      hl = getTextHighlightGroup();

      if(hl != null) {
         writer.println("<textHighlightGroup>");
         hl.writeXML(writer);
         writer.println("</textHighlightGroup>");
      }
   }

   default void parseHighlightGroup(Element elem) throws Exception {
      Element node = Tool.getChildNodeByTagName(elem, "HighlightGroup");

      if(node != null) {
         HighlightGroup hlGroup = new HighlightGroup();
         hlGroup.parseXML(node);
         setHighlightGroup(hlGroup);
      }

      node = Tool.getChildNodeByTagName(elem, "textHighlightGroup");

      if(node != null) {
         HighlightGroup hlGroup = new HighlightGroup();
         hlGroup.parseXML(Tool.getChildNodeByTagName(node, "HighlightGroup"));
         setTextHighlightGroup(hlGroup);
      }
   }
}
