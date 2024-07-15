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
 * This class defines a sequential diverging color frame for numeric values
 * using ColorBrewer scale.
 *
 * @version 12.3
 * @author InetSoft Technology
 */
public class RdYlBuColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "fc8d59ffffbf91bfdb",
         "d7191cfdae61abd9e92c7bb6",
         "d7191cfdae61ffffbfabd9e92c7bb6",
         "d73027fc8d59fee090e0f3f891bfdb4575b4",
         "d73027fc8d59fee090ffffbfe0f3f891bfdb4575b4",
         "d73027f46d43fdae61fee090e0f3f8abd9e974add14575b4",
         "d73027f46d43fdae61fee090ffffbfe0f3f8abd9e974add14575b4",
         "a50026d73027f46d43fdae61fee090e0f3f8abd9e974add14575b4313695",
         "a50026d73027f46d43fdae61fee090ffffbfe0f3f8abd9e974add14575b4313695"};
   }

   private static final long serialVersionUID = 1L;
}
