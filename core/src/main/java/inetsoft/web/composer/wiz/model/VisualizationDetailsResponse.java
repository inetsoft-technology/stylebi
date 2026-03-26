/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.composer.wiz.model;

import java.util.List;

public class VisualizationDetailsResponse {
   public VisualizationDetailsResponse(List<VisualizationDetailModel> bindingDetails,
                                       List<VisualizationDetailModel> worksheetDetails)
   {
      this.bindingDetails = bindingDetails;
      this.worksheetDetails = worksheetDetails;
   }

   public List<VisualizationDetailModel> getBindingDetails() {
      return bindingDetails;
   }

   public List<VisualizationDetailModel> getWorksheetDetails() {
      return worksheetDetails;
   }

   private final List<VisualizationDetailModel> bindingDetails;
   private final List<VisualizationDetailModel> worksheetDetails;
}
