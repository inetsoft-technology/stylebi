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
package inetsoft.graph.aesthetic;

/**
 * This is a texture frame that produces patterns with right tilting lines.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class RightTiltTextureFrame extends LinearTextureFrame {
   /**
    * Create a texture frame.
    */
   public RightTiltTextureFrame() {
   }

   /**
    * Create a texture frame.
    * @param field field to get value to map to textures.
    */
   public RightTiltTextureFrame(String field) {
      this();
      setField(field);
   }

   /**
    * Get the texture at the ratio.
    * @param ratio the value between 0 and 1,
    * and is the position of the value in a linear scale.
    */
   @Override
   protected GTexture getTexture(double ratio) {
      return new GTexture((int) (2 + 10 * (1 - ratio)), Math.PI / 4, 1);
   }

   private static final long serialVersionUID = 1L;
}
