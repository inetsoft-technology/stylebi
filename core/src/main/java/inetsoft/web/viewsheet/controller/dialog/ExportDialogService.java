/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.viewsheet.controller.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.report.io.csv.CSVConfig;
import inetsoft.report.io.viewsheet.excel.CSVUtil;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import inetsoft.web.portal.model.CSVConfigModel;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.model.dialog.ExportDialogModel;
import inetsoft.web.viewsheet.model.dialog.FileFormatPaneModel;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

@Service
@ClusterProxy
public class ExportDialogService {

   public ExportDialogService(AssetRepository assetRepository,
                              AnalyticRepository analyticRepository,
                              ViewsheetService viewsheetService)
   {
      this.assetRepository = assetRepository;
      this.analyticRepository = analyticRepository;
      this.viewsheetService = viewsheetService;
      catalog =  Catalog.getCatalog();
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ExportDialogModel getExportDialogModel(@ClusterProxyKey String runtimeId,
                                                 Principal principal)
      throws Exception
   {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      VSBookmarkInfo[] bookmarks = VSUtil.getBookmarks(rvs.getEntry(), pId);
      List<String> allBookmarks = new ArrayList<>();
      List<String> allBookmarkLabels = new ArrayList<>();

      for(VSBookmarkInfo vsBookmarkInfo : bookmarks) {
         if(vsBookmarkInfo.getName().equals(VSBookmark.HOME_BOOKMARK)) {
            allBookmarks.add(vsBookmarkInfo.getName());
            allBookmarkLabels.add(Catalog.getCatalog().getString(vsBookmarkInfo.getName()));
         }
         else if(vsBookmarkInfo.getOwner() == null || vsBookmarkInfo.getOwner().equals(pId)) {
            allBookmarks.add(vsBookmarkInfo.getName());
            allBookmarkLabels.add(vsBookmarkInfo.getName());
         }
         else {
            allBookmarks.add(vsBookmarkInfo.getName() + "(" + vsBookmarkInfo.getOwner().getName() + ")");
            allBookmarkLabels.add(vsBookmarkInfo.getName() + "(" +
                                     VSUtil.getUserAlias(vsBookmarkInfo.getOwner()) + ")");
         }
      }

      Viewsheet vs = rvs.getViewsheet();
      boolean hasPrintLayout = false;
      List<String> tableDataAssemblies = new ArrayList<>();

      if(vs != null) {
         hasPrintLayout = vs.getLayoutInfo().getPrintLayout() != null;

         VSUtil.getTableDataAssemblies(vs, true)
            .stream().forEach(assembly -> {
               if(CSVUtil.needExport(assembly)) {
                  tableDataAssemblies.add(assembly.getAbsoluteName());
               }
            });
      }

      boolean expandComponentEnabled = SreeEnv.getBooleanProperty("export.expandComponents");
      boolean expandComponentAllowed = SecurityEngine.getSecurity().checkPermission(principal, ResourceType.VIEWSHEET_TOOLBAR_ACTION,
                                                                                    "ExportExpandComponents", ResourceAction.READ);
      //by nickgovus 2023-10-26, matchLayout = !ExportComponents = false only if (ExportSecurityPermission and setExportComponent = true)
      boolean matchLayout = !(expandComponentEnabled && expandComponentAllowed);

      FileFormatPaneModel fileFormatPaneModel = FileFormatPaneModel.builder()
         .allBookmarks(allBookmarks.toArray(new String[0]))
         .allBookmarkLabels(allBookmarkLabels.toArray(new String[0]))
         .formatType(getDefaultFormat())
         .matchLayout(matchLayout)
         .expandEnabled(expandComponentAllowed)
         .hasPrintLayout(hasPrintLayout)
         .csvConfig(CSVConfigModel.builder().from(new CSVConfig()).build())
         .tableDataAssemblies(tableDataAssemblies.toArray(new String[0]))
         .build();

      return ExportDialogModel.builder()
         .fileFormatPaneModel(fileFormatPaneModel)
         .build();
   }

   /**
    * Get the default format type
    */
   private int getDefaultFormat() {
      String[] types = VSUtil.getExportOptions();

      if(Arrays.stream(types).anyMatch(type ->
                                          FileFormatInfo.EXPORT_NAME_EXCEL.equals(type)))
      {
         return FileFormatInfo.EXPORT_TYPE_EXCEL;
      }
      else if(Arrays.stream(types).anyMatch(
         type -> FileFormatInfo.EXPORT_NAME_POWERPOINT.equals(type)))
      {
         return FileFormatInfo.EXPORT_TYPE_POWERPOINT;
      }
      else if(Arrays.stream(types).anyMatch(
         type -> FileFormatInfo.EXPORT_NAME_PDF.equals(type)))
      {
         return FileFormatInfo.EXPORT_TYPE_PDF;
      }
      else if(Arrays.stream(types).anyMatch(
         type -> FileFormatInfo.EXPORT_NAME_SNAPSHOT.equals(type)))
      {
         return FileFormatInfo.EXPORT_TYPE_SNAPSHOT;
      }
      else if(Arrays.stream(types).anyMatch(
         type -> FileFormatInfo.EXPORT_NAME_HTML.equals(type)))
      {
         return FileFormatInfo.EXPORT_TYPE_HTML;
      }

      return FileFormatInfo.EXPORT_TYPE_EXCEL;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public MessageCommand checkExportValid(@ClusterProxyKey String runtimeId, String folderPath,
                                          Principal principal) throws Exception
   {
      viewsheetService.getViewsheet(runtimeId, principal);
      MessageCommand messageCommand = new MessageCommand();
      messageCommand.setType(MessageCommand.Type.OK);

      if(!analyticRepository.checkPermission(principal, ResourceType.REPORT, folderPath, ResourceAction.WRITE)) {
         messageCommand.setMessage(catalog.getString("common.writeAuthority",
                                                     folderPath));
         messageCommand.setType(MessageCommand.Type.WARNING);
      }

      return messageCommand;
   }

   private final AssetRepository assetRepository;
   private final ViewsheetService viewsheetService;
   private final AnalyticRepository analyticRepository;
   private Catalog catalog;
}


