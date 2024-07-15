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
package inetsoft.report;

/**
 * ScaledPainter is a painter that knows it's precise size in inches.
 * The pixel size returned by getPreferredSize() is treated as the 
 * area used for painting, but the end result is scaled to fit the
 * size specified with the getSize() method.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface ScaledPainter extends Painter {
   /**
    * Return the preferred actual size of this painter. This size is 
    * different from the preferred size in that it is specified as 
    * inches, which is independent of the resolution of the output
    * media. The ReportSheet automatically scales the image to fit the
    * exact size returned by this method.
    * @return size in inches.
    */
   public Size getSize();
}

