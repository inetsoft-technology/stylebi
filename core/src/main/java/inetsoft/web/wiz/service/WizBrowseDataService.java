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
package inetsoft.web.wiz.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.BrowseDataController;
import inetsoft.web.composer.model.BrowseDataModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
public class WizBrowseDataService {
   public WizBrowseDataService(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   /**
    * Browse distinct values for a worksheet table column, accessed via a VS runtimeId.
    *
    * @param runtimeId    the VS runtime ID (vsId)
    * @param assemblyName the worksheet table/assembly name that owns the column
    * @param dataRefModel the column ref describing which column to browse
    * @param principal    the current user
    */
   public BrowseDataModel browseData(String runtimeId, String assemblyName,
                                     DataRefModel dataRefModel, Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      if(rvs == null) {
         return null;
      }

      RuntimeWorksheet rws = rvs.getRuntimeWorksheet();

      if(rws == null) {
         LOG.warn("No RuntimeWorksheet found for VS runtimeId={}", runtimeId);
         return null;
      }

      BrowseDataController browseDataController = new BrowseDataController();
      DataRef dataRef = dataRefModel.createDataRef();

      if(!(dataRef instanceof ColumnRef)) {
         dataRef = new ColumnRef(dataRef);
      }

      browseDataController.setColumn((ColumnRef) dataRef);
      browseDataController.setName(assemblyName);

      try {
         return browseDataController.process(rws.getAssetQuerySandbox());
      }
      catch(Exception ex) {
         LOG.warn("Failed to browse data for assembly={}, column={}", assemblyName,
                  dataRefModel.getName(), ex);
         return null;
      }
   }

   private final ViewsheetService viewsheetService;
   private static final Logger LOG = LoggerFactory.getLogger(WizBrowseDataService.class);
}
