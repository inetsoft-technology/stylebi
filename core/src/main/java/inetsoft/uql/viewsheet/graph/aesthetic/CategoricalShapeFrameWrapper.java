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
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.DataSet;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;

/**
 * This class defines a shape frame for categorical values.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class CategoricalShapeFrameWrapper extends ShapeFrameWrapper {
   /**
    * Create the corresponding frame.
    */
   @Override
   protected VisualFrame createVisualFrame() {
      return new CategoricalShapeFrame() {
         @Override
         public GShape getShape(DataSet data, String col, int row) {
            col = getField() == null ? GraphUtil.getOriginalCol(col) : col;
            return super.getShape(data, col, row);
         }
      };
   }

   /**
    * Default constructor.
    */
   public CategoricalShapeFrameWrapper() {
   }

   /**
    * Get the shape option value of the categorical shape Frame.
    * @return the shape option value of categorical shape Frame.
    */
   public String getShape(int index) {
      CategoricalShapeFrame frame = (CategoricalShapeFrame) getVisualFrame();

      if(index < frame.getShapeCount()) {
         return getID(frame.getShape(index));
      }

      return getID(GShape.CIRCLE);
   }

   /**
    * Set the shape option value of categorical shape Frame.
    * @param the static option value of categorical shape Frame.
    */
   public void setShape(int index, String shape) {
      CategoricalShapeFrame frame = (CategoricalShapeFrame) getVisualFrame();

      if(index < frame.getShapeCount()) {
         setChanged(isChanged() || !Tool.equals(getID(frame.getShape(index)), shape));
         frame.setShape(index, getGShape(shape));
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      CategoricalShapeFrame frame = (CategoricalShapeFrame) getVisualFrame();

      writer.println("<shapes>");

      for(int i = 0; i < frame.getShapeCount(); i++) {
         writer.print("<shape index=\"" + i + "\" ");
         writer.print("value=\"" + getID(frame.getShape(i)) + "\" ");
         writer.println("></shape>");
      }

      writer.println("</shapes>");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);

      Element shapesNode = Tool.getChildNodeByTagName(tag, "shapes");
      CategoricalShapeFrame frame = (CategoricalShapeFrame) this.frame;

      if(shapesNode != null) {
         NodeList list = Tool.getChildNodesByTagName(shapesNode, "shape");

         for(int i = 0; i < list.getLength(); i++) {
            Element node = (Element) list.item(i);
            String val = node.getAttribute("value");

            if(val != null) {
               frame.setShape(i, getGShape(val));
            }
         }
      }
   }
}
