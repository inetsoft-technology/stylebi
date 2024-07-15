/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
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
public class YlOrRdColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "ffeda0feb24cf03b20",
         "ffffb2fecc5cfd8d3ce31a1c",
         "ffffb2fecc5cfd8d3cf03b20bd0026",
         "ffffb2fed976feb24cfd8d3cf03b20bd0026",
         "ffffb2fed976feb24cfd8d3cfc4e2ae31a1cb10026",
         "ffffccffeda0fed976feb24cfd8d3cfc4e2ae31a1cb10026",
         "ffffccffeda0fed976feb24cfd8d3cfc4e2ae31a1cbd0026800026"};
   }

   private static final long serialVersionUID = 1L;
}
