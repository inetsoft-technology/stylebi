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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.asset.AssetObject;

import java.util.List;

/**
 * BaseAnnotationVSAssemblyInfo, the assembly info which contains annotations.
 *
 * @version 11.4
 * @author InetSoft Technology Corp
 */
public interface BaseAnnotationVSAssemblyInfo extends AssetObject {
   /**
    * Get the annotations.
    * @return the annotations of this assembly info.
    */
   public List<String> getAnnotations();

   /**
    * Add the annotation.
    * @param name the specified annotation name.
    */
   public void addAnnotation(String name);

   /**
    * Remove the annotation.
    * @param name the specified annotation name.
    */
   public void removeAnnotation(String name);

   /**
    * Remove all annotations.
    */
   public void clearAnnotations();
}
