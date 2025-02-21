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
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.presentation.model.PresentationTimeSettingsModel;
import inetsoft.web.viewsheet.Audited;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class PresentationTimeSettingsService {

   public PresentationTimeSettingsModel getModel(boolean globalProperty) {
      return PresentationTimeSettingsModel.builder()
         .weekStart(SreeEnv.getProperty("week.start", false, !globalProperty))
         .scheduleTime12Hours(Boolean.parseBoolean(SreeEnv.getProperty("schedule.time.12hours", false, !globalProperty)))
         .build();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-Time Setting",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void setModel(PresentationTimeSettingsModel model, boolean globalSettings) throws IOException {
      SreeEnv.setProperty("week.start", model.weekStart(), !globalSettings);
      SreeEnv.setProperty("schedule.time.12hours", model.scheduleTime12Hours()+"", !globalSettings);
      SreeEnv.save();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-Time Setting",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void resetSettings(boolean globalSettings) throws IOException {
      SreeEnv.resetProperty("week.start", !globalSettings);
      SreeEnv.resetProperty("schedule.time.12hours", !globalSettings);
      SreeEnv.save();
   }
}
