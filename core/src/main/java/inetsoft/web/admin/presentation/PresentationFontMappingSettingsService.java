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
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.presentation.model.PresentationFontMappingModel;
import inetsoft.web.admin.presentation.model.PresentationFontMappingSettingsModel;
import inetsoft.web.viewsheet.Audited;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PresentationFontMappingSettingsService {
   public PresentationFontMappingSettingsModel getModel() {
      String fontMappingString = SreeEnv.getProperty("pdf.font.mapping");
      List<PresentationFontMappingModel> fontMappingModels = new ArrayList<>();

      if(fontMappingString != null && !fontMappingString.isEmpty()) {
         String[] fontMappings = fontMappingString.split(";");

         for(String fontMapping : fontMappings) {
            int index = fontMapping.indexOf(":");
            fontMappingModels.add(PresentationFontMappingModel.builder()
                                     .trueTypeFont(fontMapping.substring(0, index))
                                     .cidFont(fontMapping.substring(index + 1))
                                     .build());
         }
      }

      return PresentationFontMappingSettingsModel.builder()
         .fontMappings(fontMappingModels)
         .build();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-Font Mapping",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void setModel(PresentationFontMappingSettingsModel model) throws Exception {
      String fontMappingString = model.fontMappings().stream()
         .map((mapping) -> mapping.trueTypeFont() + ":" + mapping.cidFont())
         .collect(Collectors.joining(";"));
      SreeEnv.setProperty("pdf.font.mapping", fontMappingString);
      SreeEnv.save();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-Font Mapping",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void resetSettings() throws Exception {
      SreeEnv.setProperty("pdf.font.mapping", "");
      SreeEnv.save();
   }
}
