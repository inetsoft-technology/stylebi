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
package inetsoft.web.admin.presentation;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.DataSourceListingService;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.data.CommonKVModel;
import inetsoft.web.admin.presentation.model.PresentationDataSourceVisibilitySettingsModel;
import inetsoft.web.viewsheet.Audited;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class PresentationDataSourceVisibilitySettingsService {

   public PresentationDataSourceVisibilitySettingsModel getModel(boolean globalProperty) {
      Map<String, String> visibleMap = new HashMap<>();
      Map<String, String> hiddenMap = new HashMap<>();

      String visibleDataSources = SreeEnv.getProperty("visible.datasource.types", false, !globalProperty);
      String[] visibleArray = visibleDataSources == null || visibleDataSources.isEmpty() ?
          new String[0] : visibleDataSources.split(",");
      String hiddenDataSources = SreeEnv.getProperty("hidden.datasource.types", false, !globalProperty);
      String[] hiddenArray = hiddenDataSources == null || hiddenDataSources.isEmpty() ?
         new String[0] : hiddenDataSources.split(",");

      Arrays.stream(visibleArray).forEach(k -> visibleMap.put(k, k));
      Arrays.stream(hiddenArray).forEach(k -> hiddenMap.put(k, k));

      CommonKVModel<String, String>[] listingNames =
         DataSourceListingService.getAllDataSourceListings(false).stream()
         .map(s -> {
            String key = s.getName();
            String label = s.getDisplayName();

            if(visibleMap.containsKey(key)) {
               visibleMap.put(key, label);
            }

            if(hiddenMap.containsKey(key)) {
               hiddenMap.put(key, label);
            }

            return new CommonKVModel(key, label);
         })
         .sorted((o1, o2) -> ((String) o1.getValue()).compareTo(((String) o2.getValue())))
         .toArray(CommonKVModel[]::new);

      return PresentationDataSourceVisibilitySettingsModel.builder()
         .visibleDataSources(getCommonKVModels(visibleMap))
         .hiddenDataSources(getCommonKVModels(hiddenMap))
         .dataSourceListings(listingNames)
         .build();
   }

   private CommonKVModel<String, String>[] getCommonKVModels(Map<String, String> map) {
      List<CommonKVModel<String, String>> list = new ArrayList<>();

      for(Map.Entry<String, String> entry : map.entrySet()) {
         list.add(new CommonKVModel(entry.getKey(), entry.getValue()));
      }

      return list.toArray(new CommonKVModel[list.size()]);
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-DataSource Visibility",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void setModel(PresentationDataSourceVisibilitySettingsModel model,
                        boolean globalSettings) throws IOException
   {
      String visibleDataSources = getVisibilityItems(model.visibleDataSources());
      String hiddenDataSources = getVisibilityItems(model.hiddenDataSources());

      SreeEnv.setProperty("visible.datasource.types", visibleDataSources, !globalSettings);
      SreeEnv.setProperty("hidden.datasource.types", hiddenDataSources, !globalSettings);
      SreeEnv.save();
   }

   private String getVisibilityItems(CommonKVModel<String, String>[] arr) {
      if(arr == null || arr.length == 0) {
         return null;
      }

      StringBuilder builder = new StringBuilder();

      for(int i = 0; i < arr.length; i++) {
         if(i > 0) {
            builder.append(",");
         }

         builder.append(arr[i].getKey());
      }

      return builder.toString();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-DataSource Visibility",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void resetSettings(boolean globalSettings) throws IOException {
      SreeEnv.resetProperty("visible.datasource.types", !globalSettings);
      SreeEnv.resetProperty("hidden.datasource.types", !globalSettings);
      SreeEnv.save();
   }
}
