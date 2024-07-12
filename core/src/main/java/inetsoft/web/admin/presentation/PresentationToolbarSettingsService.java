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
package inetsoft.web.admin.presentation;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.presentation.model.PresentationViewsheetToolbarOptionsModel;
import inetsoft.web.admin.presentation.model.ToolbarOption;
import inetsoft.web.viewsheet.Audited;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PresentationToolbarSettingsService {
   public PresentationViewsheetToolbarOptionsModel getViewsheetOptions(boolean globalProperty) {
      List<SUtil.ToolBarElement> array = SUtil.getVSToolBarElements(globalProperty);
      List<ToolbarOption> toolbarOptions = new ArrayList<>();
      Catalog catalog = Catalog.getCatalog();

      for(SUtil.ToolBarElement elem : array) {
         ToolbarOption toolbarOption = ToolbarOption.builder()
            .id(elem.id)
            .alias(catalog.getString(elem.id))
            .visible(Boolean.valueOf(elem.visible))
            .enabled(true)
            .build();
         toolbarOptions.add(toolbarOption);
      }

      return PresentationViewsheetToolbarOptionsModel.builder().options(toolbarOptions).build();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-Viewsheet-Toolbar Options",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void setViewsheetOptions(PresentationViewsheetToolbarOptionsModel model, boolean globalSettings)
      throws Exception
   {
      List<SUtil.ToolBarElement> array = SUtil.getVSToolBarElements(globalSettings);
      int permissionsDenied = 0;

      for(ToolbarOption option : model.options()) {
         String elemName = this.getToolBarElementName(array, option.id());

         if(elemName != null) {
            SreeEnv.setProperty(elemName, String.valueOf(option.visible()), !globalSettings);

            if(!option.visible()) {
               permissionsDenied++;
            }
         }
      }

      SreeEnv.setProperty("Viewsheet Toolbar Hidden",
                          String.valueOf(permissionsDenied == model.options().size()), !globalSettings);
      SreeEnv.save();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-Viewsheet-Toolbar Options",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void resetSettings(boolean globalSettings)
      throws Exception
   {
      List<SUtil.ToolBarElement> array = SUtil.getVSToolBarElements(globalSettings);

      for(SUtil.ToolBarElement element : array) {
         if(globalSettings) {
            SreeEnv.setProperty(element.name, "true", !globalSettings);
         }
         else {
            SreeEnv.remove(element.name);
         }
      }

      SreeEnv.setProperty("Viewsheet Toolbar Hidden", "false", !globalSettings);
      SreeEnv.save();
   }

   /**
    * Get the element name by element id.
    */
   private String getToolBarElementName(List<SUtil.ToolBarElement> array, String id) {
      return array.stream()
         .filter((elem) -> Tool.equals(elem.id, id))
         .map((elem) -> elem.name)
         .findAny()
         .orElse(null);
   }
}
