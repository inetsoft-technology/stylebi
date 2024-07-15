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
package inetsoft.graph.aesthetic;

import com.inetsoft.build.tern.TernClass;
import com.inetsoft.build.tern.TernConstructor;

/**
 * This is a texture frame that produces patterns with left tilting lines.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass
public class LeftTiltTextureFrame extends LinearTextureFrame {
   /**
    * Create a texture frame.
    */
   public LeftTiltTextureFrame() {
   }

   /**
    * Create a texture frame.
    * @param field field to get value to map to textures.
    */
   @TernConstructor
   public LeftTiltTextureFrame(String field) {
      this();
      setField(field);
   }

   /**
    * Get the texture.
    * @param ratio the value between 0 and 1,
    * and is the position of the value in a linear scale.
    */
   @Override
   protected GTexture getTexture(double ratio) {
      return new GTexture((int) (2 + 10 * (1 - ratio)), 3 * Math.PI / 4, 1);
   }

   private static final long serialVersionUID = 1L;
}
