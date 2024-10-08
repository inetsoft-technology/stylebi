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
package inetsoft.web.binding.model.graph.aesthetic;

import inetsoft.graph.aesthetic.StaticTextureFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.report.StyleConstants;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticTextureFrameWrapper;

public class StaticTextureModel extends TextureFrameModel {
   public StaticTextureModel() {
   }

   public StaticTextureModel(StaticTextureFrameWrapper wrapper) {
      super(wrapper);
      setTexture(wrapper.getTexture());
   }

   /**
    * Set the current using texture.
    */
   public void setTexture(int texture) {
      this.texture = texture;
   }

   /**
    * Set the current using texture.
    */
   public int getTexture() {
      return texture;
   }

   @Override
   public VisualFrame createVisualFrame() {
      return new StaticTextureFrame();
   }

   private int texture = StyleConstants.PATTERN_NONE;
}