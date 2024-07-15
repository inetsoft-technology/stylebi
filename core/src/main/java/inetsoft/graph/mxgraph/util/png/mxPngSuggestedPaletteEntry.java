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
package inetsoft.graph.mxgraph.util.png;

import java.io.Serializable;

/**
 * A class representing the fields of a PNG suggested palette entry.
 *
 * <p><b> This class is not a committed part of the JAI API.  It may
 * be removed or changed in future releases of JAI.</b>
 */
public class mxPngSuggestedPaletteEntry implements Serializable {

   /**
    *
    */
   private static final long serialVersionUID = 1L;

   /**
    * The name of the entry.
    */
   public String name;

   /**
    * The depth of the color samples.
    */
   public int sampleDepth;

   /**
    * The red color value of the entry.
    */
   public int red;

   /**
    * The green color value of the entry.
    */
   public int green;

   /**
    * The blue color value of the entry.
    */
   public int blue;

   /**
    * The alpha opacity value of the entry.
    */
   public int alpha;

   /**
    * The probable frequency of the color in the image.
    */
   public int frequency;

}
