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

import inetsoft.uql.XNode;
import inetsoft.uql.asset.AssetEntry;

import java.security.Principal;

/**
 * AnnotationListener represents a listener for annotations
 *
 * @version 11.5b
 * @author InetSoft Technology Corp
 */
public interface AnnotationListener {
   /**
    * Called when the annotation is added to the viewsheet
    *
    * @param user the specified user
    * @param viewsheetEntry the specified viewsheet asset entry
    * @param assemblyName base assembly name
    * @param column column name
    * @param annotationText the content of the annotation
    * @param annotationData underlying tabular data that can be cast to XTableNode
    * (pivoted data for Crosstab)
    */
   public void annotationAdded(Principal user, AssetEntry viewsheetEntry, String assemblyName,
      String column, String annotationText, XNode annotationData);

   /**
    * Called when the annotation has been edited
    *
    * @param user the specified user
    * @param viewsheetEntry the specified viewsheet asset entry
    * @param assemblyName base assembly name
    * @param column column name
    * @param annotationText the content of the annotation
    * @param annotationData underlying tabular data that can be cast to XTableNode
    * (pivoted data for Crosstab)
    */
   public void annotationEdited(Principal user, AssetEntry viewsheetEntry, String assemblyName,
      String column, String annotationText, XNode annotationData);

   /**
    * Called when the annotation has been removed from the viewsheet
    *
    * @param user the specified user
    * @param viewsheetEntry the specified viewsheet asset entry
    * @param assemblyName base assembly name
    * @param column column name
    * @param annotationText the content of the annotation
    * @param annotationData underlying tabular data that can be cast to XTableNode
    * (pivoted data for Crosstab)
    */
   public void annotationRemoved(Principal user, AssetEntry viewsheetEntry, String assemblyName,
      String column, String annotationText, XNode annotationData);
}
