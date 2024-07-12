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
package inetsoft.uql.viewsheet;

/**
 * SingleInputVSAssembly represents one single input assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public interface SingleInputVSAssembly extends VSAssembly {
   /**
    * Get the selected object.
    * @return the object which is selected.
    */
   public Object getSelectedObject();

   /**
    * Set the selected object.
    * @param obj the specified object selected.
    * @return the hint to reset output data.
    */
   public int setSelectedObject(Object obj);
}
