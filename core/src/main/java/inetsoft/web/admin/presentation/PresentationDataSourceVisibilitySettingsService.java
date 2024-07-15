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
import inetsoft.uql.DataSourceListingService;
import inetsoft.util.data.CommonKVModel;
import inetsoft.web.admin.presentation.model.PresentationDataSourceVisibilitySettingsModel;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class PresentationDataSourceVisibilitySettingsService {

   public PresentationDataSourceVisibilitySettingsModel getModel(boolean globalProperty) {
      String visibleDataSources = SreeEnv.getProperty("visible.datasource.types", false, !globalProperty);
      String[] visibleArray = visibleDataSources == null || visibleDataSources.isEmpty() ?
          new String[0] : visibleDataSources.split(",");
      String hiddenDataSources = SreeEnv.getProperty("hidden.datasource.types", false, !globalProperty);
      String[] hiddenArray = hiddenDataSources == null || hiddenDataSources.isEmpty() ?
         new String[0] : hiddenDataSources.split(",");
      CommonKVModel<String, String>[] listingNames =
         DataSourceListingService.getAllDataSourceListings(false).stream()
         .map(s -> new CommonKVModel(s.getName(), s.getDisplayName()))
         .sorted((o1, o2) -> ((String) o1.getValue()).compareTo(((String) o2.getValue())))
         .toArray(CommonKVModel[]::new);

      return PresentationDataSourceVisibilitySettingsModel.builder()
         .visibleDataSources(visibleArray)
         .hiddenDataSources(hiddenArray)
         .dataSourceListings(listingNames)
         .build();
   }

   public void setModel(PresentationDataSourceVisibilitySettingsModel model,
                        boolean globalSettings) throws IOException
   {
      String visibleDataSources = model.visibleDataSources()  == null ||
         model.visibleDataSources().length == 0  ?
         null : String.join(",", model.visibleDataSources());
      String hiddenDataSources = model.hiddenDataSources() == null  ||
         model.hiddenDataSources().length == 0  ?
         null : String.join(",", model.hiddenDataSources());

      SreeEnv.setProperty("visible.datasource.types", visibleDataSources, !globalSettings);
      SreeEnv.setProperty("hidden.datasource.types", hiddenDataSources, !globalSettings);
      SreeEnv.save();
   }

   public void resetSettings(boolean globalSettings) throws IOException {
      SreeEnv.resetProperty("visible.datasource.types", !globalSettings);
      SreeEnv.resetProperty("hidden.datasource.types", !globalSettings);
      SreeEnv.save();
   }
}
