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

import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.DataSet;

/**
 * This class create texture frame gradating with left tilting.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class LeftTiltTextureFrameWrapper extends LinearTextureFrameWrapper {
   /**
    * Create the corresponding frame.
    */
   @Override
   protected VisualFrame createVisualFrame() {
      return new LeftTiltTextureFrame() {
         @Override
         public GTexture getTexture(DataSet data, String col, int row) {
            Object obj = data.getData(getBrushField(getField(), col), row);
            return getTexture(obj);
         }
      };
   }
}
