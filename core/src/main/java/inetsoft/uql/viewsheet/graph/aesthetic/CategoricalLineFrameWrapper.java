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
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.DataSet;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;

/**
 * This class defines a line frame for categorical values.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class CategoricalLineFrameWrapper extends LineFrameWrapper {
   /**
    * Create the corresponding frame.
    */
   @Override
   protected VisualFrame createVisualFrame() {
      return new CategoricalLineFrame() {
         @Override
         public GLine getLine(DataSet data, String col, int row) {
            col = getField() == null ? GraphUtil.getOriginalCol(col) : col;
            return super.getLine(data, col, row);
         }
      };
   }

   /**
    * Default constructor.
    */
   public CategoricalLineFrameWrapper() {
   }

   /**
    * Get the line at the specified index.
    */
   public int getLine(int index) {
      CategoricalLineFrame frame = (CategoricalLineFrame) getVisualFrame();

      if(index < frame.getLineCount()) {
         return frame.getLine(index).getStyle();
      }

      return GLine.THIN_LINE.getStyle();
   }

   /**
    * Set the line at the specified index.
    */
   public void setLine(int index, int line) {
      CategoricalLineFrame frame = (CategoricalLineFrame) getVisualFrame();

      if(index < frame.getLineCount()) {
         setChanged(isChanged() || frame.getLine(index).getStyle() != line);
         frame.setLine(index, new GLine(line));
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      CategoricalLineFrame frame = (CategoricalLineFrame) getVisualFrame();

      writer.println("<lines>");

      for(int i = 0; i < frame.getLineCount(); i++) {
         writer.print("<line index=\"" + i + "\" ");
         writer.print("value=\"" + frame.getLine(i).getStyle() + "\" ");
         writer.println("></line>");
      }

      writer.println("</lines>");
   }

   /**
    * Parse contents.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);

      Element linesNode = Tool.getChildNodeByTagName(tag, "lines");
      CategoricalLineFrame frame = (CategoricalLineFrame) this.frame;

      if(linesNode != null) {
         NodeList list = Tool.getChildNodesByTagName(linesNode, "line");

         for(int i = 0; i < list.getLength(); i++) {
            Element node = (Element) list.item(i);
            String val = node.getAttribute("value");

            if(val != null) {
               frame.setLine(i, new GLine(Integer.parseInt(val)));
            }
         }
      }
   }
}
