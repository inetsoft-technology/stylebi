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
import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.DataVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.BrowseDataController;
import inetsoft.web.composer.model.BrowseDataModel;
import inetsoft.web.wiz.WizUtil;
import inetsoft.web.wiz.model.WizBrowseDataResponse;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class WizBrowseDataService {
   public WizBrowseDataService(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   /**
    * Browse distinct values for a worksheet table column, accessed via a VS runtimeId.
    *
    * @param runtimeId           the VS runtime ID (vsId)
    * @param assemblyName        the VS chart/table assembly's own presentation name (e.g.
    *                            "Chart1") — the underlying worksheet table it's bound to is
    *                            resolved internally from the assembly's {@code SourceInfo}
    * @param viewsheetIdentifier the durable asset id, used to restore a reaped runtime; may be null
    * @param dataRefModel        the column ref describing which column to browse
    * @param principal           the current user
    */
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public WizBrowseDataResponse browseData(@ClusterProxyKey String runtimeId, String assemblyName,
                                           String viewsheetIdentifier, DataRefModel dataRefModel,
                                           Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs =
         WizUtil.getViewsheetOrRestore(viewsheetService, runtimeId, viewsheetIdentifier, principal);

      if(rvs == null) {
         throw new IllegalArgumentException("No viewsheet found for runtimeId=" + runtimeId);
      }

      RuntimeWorksheet rws = rvs.getRuntimeWorksheet();

      if(rws == null) {
         throw new IllegalStateException("No RuntimeWorksheet found for VS runtimeId=" + runtimeId);
      }

      String wsTableName = resolveWorksheetTableName(rvs.getViewsheet(), assemblyName);

      BrowseDataController browseDataController = new BrowseDataController();
      DataRef dataRef = dataRefModel.createDataRef();

      if(dataRef == null) {
         throw new IllegalArgumentException("DataRefModel produced a null DataRef");
      }

      ColumnRef columnRef = dataRef instanceof ColumnRef ? (ColumnRef) dataRef : new ColumnRef(dataRef);

      browseDataController.setColumn(columnRef);
      browseDataController.setName(wsTableName);

      BrowseDataModel model = browseDataController.process(rws.getAssetQuerySandbox());

      // process() returns null for several distinct reasons (an unresolved column, the table lens
      // not being ready yet, or an exception swallowed and only logged inside
      // executeByDataSource()) — none of which we can tell apart here, so the message stays
      // deliberately generic rather than guessing "unknown column" for every case.
      if(model == null) {
         throw new IllegalArgumentException(
            "Browse data returned no result for column '" + columnRef.getAttribute() +
            "' on worksheet table '" + wsTableName + "'");
      }

      WizBrowseDataResponse response = new WizBrowseDataResponse();
      response.setValues(model.values());

      // Echo the runtimeId only when a reaped runtime was restored (id changed) so the client adopts
      // the live runtime for its next edit instead of triggering a second restore.
      if(!runtimeId.equals(rvs.getID())) {
         response.setRuntimeId(rvs.getID());
      }

      return response;
   }

   /**
    * Resolves the worksheet table name that {@code assemblyName} (the VS chart's own presentation
    * name, e.g. "Chart1") is bound to. {@link BrowseDataController#process} looks its {@code name}
    * up against the base WORKSHEET's own assemblies — a different namespace — so passing the chart
    * name through unresolved always misses (silently: it returns null instead of erroring). Mirrors
    * the same {@code DataVSAssembly} → {@code SourceInfo} → {@code getSource()} resolution
    * {@code WizVsService} does for aggregate-condition handling. Package-private for unit testing.
    */
   String resolveWorksheetTableName(Viewsheet vs, String assemblyName) {
      VSAssembly chartAssembly = vs != null ? vs.getAssembly(assemblyName) : null;

      if(!(chartAssembly instanceof DataVSAssembly dataAssembly)) {
         throw new IllegalArgumentException(
            "No chart assembly named '" + assemblyName + "' found in the viewsheet");
      }

      SourceInfo sourceInfo = dataAssembly.getSourceInfo();
      String wsTableName = sourceInfo != null ? sourceInfo.getSource() : null;

      if(wsTableName == null) {
         throw new IllegalStateException(
            "Chart assembly '" + assemblyName + "' has no bound worksheet table");
      }

      return wsTableName;
   }

   private final ViewsheetService viewsheetService;
}
