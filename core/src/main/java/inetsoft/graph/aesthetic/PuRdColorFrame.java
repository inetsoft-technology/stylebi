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

import inetsoft.graph.rgb.AbstractSplineColorFrame;

/**
 * This class defines a sequential multi-hue color frame for numeric values
 * using ColorBrewer scale.
 *
 * @version 12.3
 * @author InetSoft Technology
 */
public class PuRdColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "e7e1efc994c7dd1c77",
         "f1eef6d7b5d8df65b0ce1256",
         "f1eef6d7b5d8df65b0dd1c77980043",
         "f1eef6d4b9dac994c7df65b0dd1c77980043",
         "f1eef6d4b9dac994c7df65b0e7298ace125691003f",
         "f7f4f9e7e1efd4b9dac994c7df65b0e7298ace125691003f",
         "f7f4f9e7e1efd4b9dac994c7df65b0e7298ace125698004367001f"};
   }

   private static final long serialVersionUID = 1L;
}
