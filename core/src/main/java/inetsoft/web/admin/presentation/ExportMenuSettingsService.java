/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.presentation;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.presentation.model.ExportMenuOption;
import inetsoft.web.admin.presentation.model.PresentationExportMenuSettingsModel;
import inetsoft.web.viewsheet.Audited;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExportMenuSettingsService {
   public PresentationExportMenuSettingsModel getExportMenuSettings(boolean globalProperty) {
      List<ExportMenuOption> vsOptions = getVSOptions(globalProperty);

      return PresentationExportMenuSettingsModel.builder()
         .vsOptions(vsOptions)
         .vsEnabled(true)
         .build();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-Export Menu Options",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void setExportMenuSettings(PresentationExportMenuSettingsModel model, boolean globalSettings)
      throws IOException
   {
      saveVSExportMenuSettings(model.vsOptions(), globalSettings);
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-Viewsheet-Export Menu Options",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   private void saveVSExportMenuSettings(List<ExportMenuOption> reportOptions, boolean globalSettings) throws IOException {
      saveExportMenuSettings(reportOptions, "vsexport.menu.options", globalSettings);
   }

   private List<ExportMenuOption> getVSOptions(boolean globalProperty) {
      List<ExportMenuOption> vsOptions = new ArrayList<>();
      List<String> props = Arrays.asList(VSUtil.getExportOptions(globalProperty));
      final String[] exportTypes = FileFormatInfo.EXPORT_ALL_NAMES;

      for(String type : exportTypes) {
         ExportMenuOption option =
            ExportMenuOption.builder()
               .name(type)
               .description(catalog.getString(type))
               .value(props.size() == 0 || props.contains(type))
               .build();
         vsOptions.add(option);
      }

      Collections.sort(vsOptions, Comparator.comparing(ExportMenuOption::description));

      return vsOptions;
   }

   private void saveExportMenuSettings(List<ExportMenuOption> options, String optionsName, boolean globalSettings)
      throws IOException
   {
      String menuOptions =
         options.stream()
            .filter(ExportMenuOption::value)
            .map(ExportMenuOption::name)
            .collect(Collectors.joining(","));

      if(menuOptions.length() == 0) {
         menuOptions =
            options.stream()
               .map(ExportMenuOption::name)
               .collect(Collectors.joining(","));
      }

      SreeEnv.setProperty(optionsName, menuOptions, !globalSettings);
      SreeEnv.save();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-Export Menu Options",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void resetSettings(boolean globalSettings) throws IOException {
      SreeEnv.resetProperty("vsexport.menu.options", !globalSettings);
   }

   private final Catalog catalog = Catalog.getCatalog();
}
