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

import inetsoft.graph.rgb.AbstractSplineColorFrame;

/**
 * This class defines a sequential diverging color frame for numeric values
 * using ColorBrewer scale.
 *
 * @version 12.3
 * @author InetSoft Technology
 */
public class BrBGColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "d8b365f5f5f55ab4ac",
         "a6611adfc27d80cdc1018571",
         "a6611adfc27df5f5f580cdc1018571",
         "8c510ad8b365f6e8c3c7eae55ab4ac01665e",
         "8c510ad8b365f6e8c3f5f5f5c7eae55ab4ac01665e",
         "8c510abf812ddfc27df6e8c3c7eae580cdc135978f01665e",
         "8c510abf812ddfc27df6e8c3f5f5f5c7eae580cdc135978f01665e",
         "5430058c510abf812ddfc27df6e8c3c7eae580cdc135978f01665e003c30",
         "5430058c510abf812ddfc27df6e8c3f5f5f5c7eae580cdc135978f01665e003c30"};
   }

   private static final long serialVersionUID = 1L;
}
