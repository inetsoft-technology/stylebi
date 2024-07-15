/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.admin.general;

import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.sree.SreeEnv;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.general.model.PerformanceSettingsModel;
import inetsoft.web.viewsheet.AuditUser;
import inetsoft.web.viewsheet.Audited;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
public class PerformanceSettingsService {
   public PerformanceSettingsModel getModel() {
      return PerformanceSettingsModel.builder()
         .queryTimeout(Long.parseLong(SreeEnv.getProperty("query.runtime.timeout")))
         .maxQueryRowCount(Integer.parseInt(SreeEnv.getProperty("query.runtime.maxrow")))
         .queryPreviewTimeout(Long.parseLong(SreeEnv.getProperty("query.preview.timeout")))
         .maxQueryPreviewRowCount(Integer.parseInt(SreeEnv.getProperty("query.preview.maxrow")))
         .maxTableRowCount(Integer.parseInt(SreeEnv.getProperty("table.output.maxrow", "0")))
         .dataSetCaching(Boolean.valueOf(SreeEnv.getProperty("query.cache.data")))
         .dataCacheSize(Long.parseLong(SreeEnv.getProperty("query.cache.limit")))
         .dataCacheTimeout(Long.parseLong(SreeEnv.getProperty("query.cache.timeout")))
         .build();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "General-Performance",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void setModel(PerformanceSettingsModel model,
                        @SuppressWarnings("unused") @AuditUser Principal principal)
      throws Exception
   {
      SreeEnv.setProperty("query.runtime.timeout", model.queryTimeout() + "");
      SreeEnv.setProperty("query.runtime.maxrow", model.maxQueryRowCount() + "");
      SreeEnv.setProperty("table.output.maxrow", model.maxTableRowCount() + "");
      SreeEnv.setProperty("query.preview.timeout", model.queryPreviewTimeout() + "");

      String oPreviewMaxRow = SreeEnv.getProperty("query.preview.maxrow");
      SreeEnv.setProperty("query.preview.maxrow", model.maxQueryPreviewRowCount() + "");
      flushAssetDataCache(oPreviewMaxRow, model.maxQueryPreviewRowCount());

      SreeEnv.setProperty("query.cache.data", model.dataSetCaching() + "");
      SreeEnv.setProperty("query.cache.limit", model.dataCacheSize() + "");
      SreeEnv.setProperty("query.cache.timeout", model.dataCacheTimeout() + "");
      SreeEnv.save();
   }

   /**
    * Flush asset data cache if query.preview.maxrow changed.
    */
   private void flushAssetDataCache(String oPreviewMaxRow, int npreviewMaxRow) {
      int oldMaxRow = 0;

      if(oPreviewMaxRow != null) {
         try {
            oldMaxRow = Integer.parseInt(oPreviewMaxRow);
         }
         catch(NumberFormatException ignore) {
         }
      }

      if(oldMaxRow != npreviewMaxRow) {
         AssetDataCache.getCache().clear();
      }
   }
}
