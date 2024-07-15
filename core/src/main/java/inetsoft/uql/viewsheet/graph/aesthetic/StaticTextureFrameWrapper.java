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

import inetsoft.graph.aesthetic.StaticTextureFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * Static texture frame defines a static texture for visual objects.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class StaticTextureFrameWrapper extends TextureFrameWrapper {
   /**
    * Create the corresponding frame.
    */
   @Override
   protected VisualFrame createVisualFrame() {
      return new StaticTextureFrame();
   }

   /**
    * Get the texture value of the Static texture frame.
    * @return the texture value value of Static texture frame.
    */
   public int getTexture() {
      StaticTextureFrame frame = (StaticTextureFrame) getVisualFrame();

      return getID(frame.getTexture());
   }

   /**
    * Set the texture value of static texture frame.
    * @param texture the texture value of static texture frame.
    */
   public void setTexture(int texture) {
      StaticTextureFrame frame = (StaticTextureFrame) getVisualFrame();

      setChanged(isChanged() || getID(frame.getTexture()) != texture);
      frame.setTexture(getGTexture(texture));
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      StaticTextureFrame frame = (StaticTextureFrame) getVisualFrame();

      writer.print("texture=\"" + getID(frame.getTexture()) + "\"");
   }

   /**
    * Parse attributes.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      String val;
      StaticTextureFrame frame = (StaticTextureFrame) getVisualFrame();

      if((val = Tool.getAttribute(tag, "texture")) != null) {
         frame.setTexture(getGTexture(Integer.parseInt(val)));
      }
   }
}
