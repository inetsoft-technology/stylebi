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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.asset.AssetObject;

import java.awt.*;

/**
 * FlotableVSAssemblyInfo, the assembly info of a floatable assembly.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public interface FloatableVSAssemblyInfo extends AssetObject {
   /**
    * Set the offset from the grid position.
    */
   public void setPixelOffset(Point poff);

   /**
    * Get the offset from the grid position.
    */
   public Point getPixelOffset();
   
   /**
    * Set the pixel size of this object. If set, it overrides the
    * size determined by the grid.
    */
   public void setPixelSize(Dimension pixelsize);

   /**
    * Get the pixel size of this object.
    * @return pixel size or null if the position is not explicitly set.
    */
   public Dimension getPixelSize();
}
