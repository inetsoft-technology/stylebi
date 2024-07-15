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
package inetsoft.web.admin.general;

import inetsoft.graph.geo.service.MapboxService;
import inetsoft.graph.geo.service.MapboxStyle;
import inetsoft.sree.SreeEnv;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.general.model.WebMapSettingsModel;
import inetsoft.web.viewsheet.AuditUser;
import inetsoft.web.viewsheet.Audited;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@Service
public class WebMapSettingsService {
   public WebMapSettingsModel getModel(boolean globalProperty) {
      MapboxService service = new MapboxService(!globalProperty);
      List<MapboxStyle> styles = new ArrayList<>();

      try {
         if(SreeEnv.getProperty("webmap.service") !=null) {
            styles = service.getStyles(false);
         }
      }
      catch(Exception ex) {
         LOG.info("Failed to get mapbox styles: " + ex, ex);
      }

      return WebMapSettingsModel.builder()
         .service(SreeEnv.getProperty("webmap.service", false, !globalProperty))
         .defaultOn("true".equals(SreeEnv.getProperty("webmap.default", false, !globalProperty)))
         .mapboxUser(SreeEnv.getProperty("mapbox.user", false, !globalProperty))
         .mapboxToken(SreeEnv.getProperty("mapbox.token", false, !globalProperty))
         .mapboxStyle(SreeEnv.getProperty("mapbox.style", false, !globalProperty))
         .mapboxStyles(styles)
         .googleKey(SreeEnv.getProperty("google.maps.key", false, !globalProperty))
         .build();
   }

   public List<MapboxStyle> getMapStyles(String mapboxUser, String mapboxToken) {
      try {
         return new MapboxService(mapboxUser, mapboxToken, null).getStyles(false);
      }
      catch(IOException ex) {
         LOG.info("Failed to get mapbox styles: " + ex, ex);
         return new ArrayList<>();
      }
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-WebMap",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void setModel(WebMapSettingsModel model,
                        @SuppressWarnings("unused") @AuditUser Principal principal, boolean globalSettings)
      throws Exception
   {
      SreeEnv.setProperty("webmap.service", model.service(), !globalSettings);
      SreeEnv.setProperty("webmap.default", model.defaultOn() + "", !globalSettings);
      SreeEnv.setProperty("mapbox.user", model.mapboxUser(), !globalSettings);
      SreeEnv.setProperty("mapbox.token", model.mapboxToken(), !globalSettings);
      SreeEnv.setProperty("mapbox.style", model.mapboxStyle(), !globalSettings);
      SreeEnv.setProperty("google.maps.key", model.googleKey(), !globalSettings);
      SreeEnv.save();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-WebMap",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void resetSettings(
                        @SuppressWarnings("unused") @AuditUser Principal principal, boolean globalSettings)
      throws Exception
   {
      SreeEnv.resetProperty("webmap.service", !globalSettings);
      SreeEnv.resetProperty("webmap.default", !globalSettings);
      SreeEnv.resetProperty("mapbox.user", !globalSettings);
      SreeEnv.resetProperty("mapbox.token", !globalSettings);
      SreeEnv.resetProperty("mapbox.style", !globalSettings);
      SreeEnv.resetProperty("google.maps.key", !globalSettings);
      SreeEnv.save();
   }

   private static final Logger LOG = LoggerFactory.getLogger(WebMapSettingsService.class);
}
