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
package inetsoft.graph.aesthetic;

import com.inetsoft.build.tern.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.Scale;
import inetsoft.util.CoreTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This class defines a texture frame for categorical values.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=CategoricalTextureFrame")
public class CategoricalTextureFrame extends TextureFrame implements CategoricalFrame {
   /**
    * Create a texture frame for categorical values.
    */
   public CategoricalTextureFrame() {
      txtrs = new GTexture[] {GTexture.PATTERN_0, GTexture.PATTERN_1,
         GTexture.PATTERN_2, GTexture.PATTERN_3, GTexture.PATTERN_4,
         GTexture.PATTERN_5, GTexture.PATTERN_6, GTexture.PATTERN_7,
         GTexture.PATTERN_8, GTexture.PATTERN_9, GTexture.PATTERN_10,
         GTexture.PATTERN_11, GTexture.PATTERN_12, GTexture.PATTERN_13,
         GTexture.PATTERN_14, GTexture.PATTERN_15, GTexture.PATTERN_16,
         GTexture.PATTERN_17, GTexture.PATTERN_18, GTexture.PATTERN_19,
         GTexture.PATTERN_0, GTexture.PATTERN_1, GTexture.PATTERN_2,
         GTexture.PATTERN_3, GTexture.PATTERN_4, GTexture.PATTERN_5,
         GTexture.PATTERN_6, GTexture.PATTERN_7, GTexture.PATTERN_8,
         GTexture.PATTERN_9};
   }

   /**
    * Create a texture frame.
    * @param field field to get value to map to textures.
    */
   @TernConstructor
   public CategoricalTextureFrame(String field) {
      this();
      setField(field);
   }

   /**
    * Initialize the categorical texture frame with categorical values.
    */
   public void init(Object... vals) {
      CategoricalScale scale = new CategoricalScale();

      scale.init(vals);
      setScale(scale);
   }

   /**
    * Initialize the categorical texture frame with categorical values and
    * textures.
    * The value and texture array must have identical length. Each value in the
    * value array is assigned a texture from the texture array at the same
    * position.
    */
   public void init(Object[] vals, GTexture[] txtrs) {
      this.txtrs = txtrs;
      CategoricalScale scale = new CategoricalScale();
      scale.init(vals);
      setScale(scale);
   }

   /**
    * Initialize the categorical texture frame with categorical values from the
    * dimension column.
    */
   @Override
   public void init(DataSet data) {
      if(getField() == null) {
         init(getAllHeaders(data), txtrs);
         return;
      }

      createScale(data);
   }

   /**
    * Get the texture for the chart object.
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
    * Get a texture for the specified value.
    */
   @Override
   @TernMethod
   public GTexture getTexture(Object val) {
      if(cmap.size() > 0) {
         GTexture texture = cmap.get(GTool.toString(val));

         if(texture != null) {
            return texture;
         }
      }

      Scale scale = getScale();

      if(scale == null) {
         return defaultTexture;
      }

      double idx = scale.map(val);

      if(Double.isNaN(idx)) {
         idx = scale.map(GTool.toString(val));
      }

      return Double.isNaN(idx) ? defaultTexture : txtrs[(int) idx % txtrs.length];
   }

   /**
    * Set the texture to be used if the value is not found in the categorical values.
    */
   @TernMethod
   public void setDefaultTexture(GTexture texture) {
      this.defaultTexture = texture;
   }

   /**
    * Get the texture to be used if the value is not found in the categorical values.
    */
   @TernMethod
   public GTexture getDefaultTexture() {
      return defaultTexture;
   }

   /**
    * Set the texture for the specified value.
    */
   @TernMethod
   public void setTexture(Object val, GTexture texture) {
      if(texture != null) {
         cmap.put(GTool.toString(val), texture);
      }
      else {
         cmap.remove(GTool.toString(val));
      }
   }

   /**
    * Check if the value is assigned a static aesthetic value.
    */
   @Override
   @TernMethod
   public boolean isStatic(Object val) {
      return cmap.get(GTool.toString(val)) != null;
   }

   @Override
   @TernMethod
   public Set<Object> getStaticValues() {
      return cmap.keySet();
   }

   @Override
   @TernMethod
   public void clearStatic() {
      cmap.clear();
   }

   /**
    * Get the shape at the specified index.
    */
   @TernMethod
   public GTexture getTexture(int index) {
      return txtrs[index % txtrs.length];
   }

   /**
    * Set the texture at the specified index.
    */
   @TernMethod
   public void setTexture(int index, GTexture texture) {
      if(txtrs != null) {
         txtrs[index % txtrs.length] = texture;
      }
   }

   /**
    * Get the texture count.
    */
   @TernMethod
   public int getTextureCount() {
      return txtrs.length;
   }

   @Override
   @TernMethod
   public String getUniqueId() {
      return super.getUniqueId() + new TreeMap(cmap);
   }

   /**
    * Check if equals another object.
    */
   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      GTexture[] txtrs2 = ((CategoricalTextureFrame) obj).txtrs;

      if(txtrs.length != txtrs2.length) {
         return false;
      }

      for(int i = 0; i < txtrs.length; i++) {
         if(!CoreTool.equals(txtrs[i], txtrs2[i])) {
            return false;
         }
      }

      return cmap.equals(((CategoricalTextureFrame) obj).cmap);
   }

   /**
    * Create a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         CategoricalTextureFrame frame =
            (CategoricalTextureFrame) super.clone();
         frame.txtrs = txtrs.clone();
         frame.cmap = new HashMap<>(cmap);

         return frame;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone texture frame", ex);
         return null;
      }
   }

   private GTexture[] txtrs;
   private Map<Object, GTexture> cmap = new HashMap<>();
   private GTexture defaultTexture = null;

   private static final long serialVersionUID = 1L;
   private static final Logger LOG = LoggerFactory.getLogger(CategoricalTextureFrame.class);
}
