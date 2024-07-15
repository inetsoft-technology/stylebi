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
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.GTexture;

/**
 * This class defines the common API for all texture frames.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public abstract class TextureFrameWrapper extends VisualFrameWrapper {
   /**
    * Get the shape according to shape id.
    */
   public static final GTexture getGTexture(int id) {
      return (id < 0) ? null: TEXTURES[id];
   }

   /**
    * Find the ID of a shape.
    */
   public static int getID(GTexture shape) {
      for(int i = 0; i < TEXTURES.length; i++) {
         if(TEXTURES[i].equals(shape)) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Get the TEXTURES constants' count.
    * @return
    */
   public static int getTextureCount() {
      return TEXTURES.length;
   }

   private static final GTexture[] TEXTURES = {
      GTexture.PATTERN_0, GTexture.PATTERN_1, GTexture.PATTERN_2,
      GTexture.PATTERN_3, GTexture.PATTERN_4, GTexture.PATTERN_5,
      GTexture.PATTERN_6, GTexture.PATTERN_7, GTexture.PATTERN_8,
      GTexture.PATTERN_9, GTexture.PATTERN_10, GTexture.PATTERN_11,
      GTexture.PATTERN_12, GTexture.PATTERN_13, GTexture.PATTERN_14,
      GTexture.PATTERN_15, GTexture.PATTERN_16, GTexture.PATTERN_17,
      GTexture.PATTERN_18, GTexture.PATTERN_19};
}
