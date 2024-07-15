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

import com.inetsoft.build.tern.TernMethod;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.scale.Scale;

import java.lang.reflect.Method;

/**
 * This class defines a texture frame for numeric values.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public abstract class LinearTextureFrame extends TextureFrame {
   /**
    * Get a texture for the specified value.
    */
   @Override
   @TernMethod
   public GTexture getTexture(Object val) {
      Scale scale = getScale();

      if(scale == null) {
         return getTexture(1);
      }
      
      double v = scale.map(val);

      if(Double.isNaN(v)) {
         return null;
      }

      double min = scale.getMin();
      double max = scale.getMax();

      return getTexture(Math.min(1, (v - min) / (max - min)));
   }

   /**
    * Get the texture for the specified cell.
    * @param data the specified dataset.
    * @param col the name of the specified column.
    * @param row the specified row index.
    */
   @Override
   @TernMethod
   public GTexture getTexture(DataSet data, String col, int row) {
      Object obj = data.getData(getField(), row);
      return getTexture(obj);
   }

   /**
    * Get a texture at the relative scale.
    * @param ratio a value from 0 to 1.
    */
   protected abstract GTexture getTexture(double ratio);

   /**
    * Legend is always visible.
    */
   @Override
   boolean isMultiItem(Method getter) throws Exception {
      return true;
   }
}
