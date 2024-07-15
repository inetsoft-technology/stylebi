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
 * This class defines a texture frame for categorical values.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class CategoricalTextureFrameWrapper extends TextureFrameWrapper {
   /**
    * Create the corresponding frame.
    */
   @Override
   protected VisualFrame createVisualFrame() {
      return new CategoricalTextureFrame() {
         @Override
         public GTexture getTexture(DataSet data, String col, int row) {
            col = getField() == null ? GraphUtil.getOriginalCol(col) : col;
            return super.getTexture(data, col, row);
         }
      };
   }

   /**
    * Default constructor.
    */
   public CategoricalTextureFrameWrapper() {
   }

   /**
    * Get the texture option value of the static texture frame.
    * @return the texture option value of static texture frame.
    */
   public int getTexture(int index) {
      CategoricalTextureFrame frame = (CategoricalTextureFrame)getVisualFrame();

      if(index < frame.getTextureCount()) {
         return getID(frame.getTexture(index));
      }

      return getID(GTexture.PATTERN_0);
   }

   /**
    * Set the static option value of static texture frame.
    * @param texture the static option value of static texture frame.
    */
   public void setTexture(int index, int texture) {
      CategoricalTextureFrame frame = (CategoricalTextureFrame)getVisualFrame();

      if(index < frame.getTextureCount()) {
         setChanged(isChanged() || getID(frame.getTexture(index)) != texture);
         frame.setTexture(index, getGTexture(texture));
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      CategoricalTextureFrame frame = (CategoricalTextureFrame)getVisualFrame();

      writer.println("<textures>");

      for(int i = 0; i < frame.getTextureCount(); i++) {
         writer.print("<texture index=\"" + i + "\" ");
         writer.print("value=\"" + getID(frame.getTexture(i)) + "\" ");
         writer.println("></texture>");
      }

      writer.println("</textures>");
   }

   /**
    * Parse contents.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);
      Element txtrsNode = Tool.getChildNodeByTagName(tag, "textures");
      CategoricalTextureFrame frame = (CategoricalTextureFrame) this.frame;

      if(txtrsNode != null) {
         NodeList list = Tool.getChildNodesByTagName(txtrsNode, "texture");

         for(int i = 0; i < list.getLength(); i++) {
            Element node = (Element) list.item(i);
            String val = node.getAttribute("value");

            if(val != null) {
               frame.setTexture(i, getGTexture(Integer.parseInt(val)));
            }
         }
      }
   }
}
