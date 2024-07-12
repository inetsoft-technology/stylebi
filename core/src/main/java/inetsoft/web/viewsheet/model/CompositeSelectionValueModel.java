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
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.uql.viewsheet.CompositeSelectionValue;
import inetsoft.uql.viewsheet.internal.SelectionBaseVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;

import java.awt.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CompositeSelectionValueModel extends SelectionValueModel {
   public CompositeSelectionValueModel(CompositeSelectionValue svalue,
                                       SelectionListModel parentList,
                                       VSAssemblyInfo assemblyInfo,
                                       Font font, boolean wrapping)
   {
      super(svalue, parentList, (SelectionBaseVSAssemblyInfo) assemblyInfo, font, wrapping);
      // each branch is limited to 1000 nodes
      slist = new SelectionListModel(
         svalue == null ? null : svalue.getSelectionList(), assemblyInfo, 1000);
   }

   public SelectionListModel getSelectionList() {
      return slist;
   }

   @Override
   public String toString() {
      return "{" + super.toString() + " " +
         "slist:" + slist + "} ";
   }

   private SelectionListModel slist;
}
