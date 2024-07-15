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

import com.inetsoft.build.tern.*;
import inetsoft.graph.data.DataSet;
import inetsoft.util.CoreTool;

/**
 * Static texture frame defines a static texture for visual objects.
 * If a column is bound to this frame, and the value of the column is a
 * GTexture, the value is used as the texture for the row instead of the
 * static texture.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=StaticTextureFrame")
public class StaticTextureFrame extends TextureFrame {
   /**
    * Default constructor.
    */
   public StaticTextureFrame() {
      super();
   }

   /**
    * Create a static texture frame with the specified texture.
    */
   @TernConstructor
   public StaticTextureFrame(GTexture texture) {
      setTexture(texture);
   }

   /**
    * Create a texture frame.
    * @param field field to get value to map to textures.
    */
   public StaticTextureFrame(String field) {
      this();
      setField(field);
   }

   /**
    * Get the texture of the Static texture frame.
    */
   @TernMethod
   public GTexture getTexture() {
      return this.texture;
   }

   /**
    * Set the texture of static texture frame.
    */
   @TernMethod
   public void setTexture(GTexture texture) {
      this.texture = texture;
   }

   /**
    * Get the texture for negative values.
    */
   @TernMethod
   public GTexture getNegativeTexture() {
      return negtexture;
   }

   /**
    * Set the texture for negative values. If this texture is not set,
    * the regular texture is used for all values.
    */
   @TernMethod
   public void setNegativeTexture(GTexture negtexture) {
      this.negtexture = negtexture;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      return CoreTool.equals(((StaticTextureFrame) obj).texture, texture) &&
         CoreTool.equals(((StaticTextureFrame) obj).negtexture, negtexture);
   }

   /**
    * Get the texture for the specified cell.
    * @param data the specified dataset.
    * @param col the name of the specified column.
    * @param row the specified row index.
    */
   @Override
   public GTexture getTexture(DataSet data, String col, int row) {
      Object val = (getField() != null) ? data.getData(getField(), row) : col;
      return getTexture(val);
   }

   /**
    * Get the texture for the specified value.
    */
   @Override
   @TernMethod
   public GTexture getTexture(Object val) {
      if(negtexture != null && val instanceof Number) {
         if(((Number) val).doubleValue() < 0) {
            return negtexture;
         }
      }
      else {
         String field = getField();

         if(field != null) {
            if(val instanceof Number) {
               try {
                  String pattern = "PATTERN_" + ((Number) val).intValue();
                  return (GTexture) GTexture.class.getField(pattern).get(null);
               }
               catch(Exception ex) {
                  // ignore
               }
            }
            else if(val instanceof GTexture) {
               return (GTexture) val;
            }
         }
      }

      return texture;
   }

   /**
    * Get the values mapped by this frame.
    */
   @Override
   @TernMethod
   public Object[] getValues() {
      return null;
   }

   /**
    * Get the title to show on the legend.
    */
   @Override
   @TernMethod
   public String getTitle() {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Static frame never shows legend.
    * @return false
    */
   @Override
   @TernMethod
   public boolean isVisible() {
      return false;
   }

   @Override
   @TernMethod
   public String getUniqueId() {
      return texture != null ? texture.toString() : super.getUniqueId();
   }

   private GTexture texture = null;
   private GTexture negtexture = null;
   private static final long serialVersionUID = 1L;
}
