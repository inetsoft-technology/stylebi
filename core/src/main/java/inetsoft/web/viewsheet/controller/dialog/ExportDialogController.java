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
package inetsoft.web.viewsheet.controller.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.io.csv.CSVConfig;
import inetsoft.report.io.viewsheet.excel.CSVUtil;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.portal.model.CSVConfigModel;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.dialog.ExportDialogModel;
import inetsoft.web.viewsheet.model.dialog.FileFormatPaneModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@Controller
public class ExportDialogController {
   /**
    * Creates a new instance of ExportDialogController
    * @param runtimeViewsheetRef    RuntimeViewsheetRef instance
    * @param viewsheetService
    */
   @Autowired
   public ExportDialogController(
      RuntimeViewsheetRef runtimeViewsheetRef,
      AssetRepository assetRepository,
      AnalyticRepository analyticRepository,
      ViewsheetService viewsheetService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.assetRepository = assetRepository;
      this.analyticRepository = analyticRepository;
      this.viewsheetService = viewsheetService;
      catalog =  Catalog.getCatalog();
   }

   /**
    * Gets the export dialog model.
    * @param runtimeId  the runetime id
    * @param principal  the principal user
    * @return  the export dialog model
    * @throws Exception if could not create the export dialog model
    */
   @RequestMapping(value="/api/vs/export-dialog-model/**", method = RequestMethod.GET)
   @ResponseBody
   public ExportDialogModel getExportDialogModel(@RemainingPath String runtimeId,
                                                 Principal principal)
      throws Exception
   {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      runtimeId = Tool.byteDecode(runtimeId);
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      VSBookmarkInfo[] bookmarks = VSUtil.getBookmarks(rvs.getEntry(), pId);
      List<String> allBookmarks = new ArrayList<>();

      for(VSBookmarkInfo vsBookmarkInfo : bookmarks) {
         if(vsBookmarkInfo.getName().equals(VSBookmark.HOME_BOOKMARK)) {
            allBookmarks.add(Catalog.getCatalog().getString(vsBookmarkInfo.getName()));
         }
         else if(vsBookmarkInfo.getOwner() == null ||
            vsBookmarkInfo.getOwner().equals(pId))
         {
            allBookmarks.add(vsBookmarkInfo.getName());
         }
         else {
            allBookmarks.add(vsBookmarkInfo.getName() + "(" +
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

   /**
    * Check if exporting a viewsheet is valid.
    * @param runtimeId     the runtime id of the vs
    * @param folderPath    the folder to export to
    * @param principal     the principal user
    * @return  A Message command with type OK for valid export params
    * @throws Exception if could not check if the export is valid
    */
   @RequestMapping(value="/api/vs/check-export-valid/**", method = RequestMethod.GET)
   @ResponseBody
   public MessageCommand checkExportValid(@RemainingPath String runtimeId,
                                          @RequestParam("folderPath") String folderPath,
                                          Principal principal)
      throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
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

   /**
    * Copy of isCube() from ExportVSEvent.java
    * Check if base cube data source.
    */
   public static boolean isCube(Viewsheet viewsheet) {
      Assembly[] assemblies = viewsheet.getAssemblies(true);

      for(Assembly assembly : assemblies) {
         VSAssemblyInfo info = ((VSAssembly) assembly).getVSAssemblyInfo();

         if(info instanceof SelectionVSAssemblyInfo) {
            for(String tableName : ((SelectionVSAssemblyInfo) info).getTableNames()) {
               if(AssetUtil.getCubeType(null, tableName) != null) {
                  return true;
               }
            }
         }
         else if(info instanceof DataVSAssemblyInfo) {
            DataVSAssemblyInfo dinfo = (DataVSAssemblyInfo) info;
            SourceInfo sinfo = dinfo.getSourceInfo();
            String prefix = sinfo == null ? null : sinfo.getPrefix();
            String source = sinfo == null ? null : sinfo.getSource();

            if(source != null && source.length() > 0) {
               String cubeType = AssetUtil.getCubeType(prefix, source);

               if(cubeType != null) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final AssetRepository assetRepository;
   private final ViewsheetService viewsheetService;
   private final AnalyticRepository analyticRepository;
   private Catalog catalog;
}
