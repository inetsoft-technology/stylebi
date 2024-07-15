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
import inetsoft.web.admin.presentation.model.PresentationTimeSettingsModel;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class PresentationTimeSettingsService {

   public PresentationTimeSettingsModel getModel(boolean globalProperty) {
      return PresentationTimeSettingsModel.builder()
         .weekStart(SreeEnv.getProperty("week.start", false, !globalProperty))
         .localTimezone(SreeEnv.getProperty("local.timezone",  false, !globalProperty))
         .scheduleTime12Hours(Boolean.parseBoolean(SreeEnv.getProperty("schedule.time.12-hours", false, !globalProperty)))
         .build();
   }

   public void setModel(PresentationTimeSettingsModel model, boolean globalSettings) throws IOException {
      SreeEnv.setProperty("week.start", model.weekStart(), !globalSettings);
      SreeEnv.setProperty("schedule.time.12-hours", model.scheduleTime12Hours()+"", !globalSettings);
      SreeEnv.setProperty("local.timezone", model.localTimezone(), !globalSettings);
      SreeEnv.save();
   }

   public void resetSettings(boolean globalSettings) throws IOException {
      SreeEnv.resetProperty("week.start", !globalSettings);
      SreeEnv.resetProperty("schedule.time.12-hours", !globalSettings);
      SreeEnv.resetProperty("local.timezone", !globalSettings);
      SreeEnv.save();
   }
}
