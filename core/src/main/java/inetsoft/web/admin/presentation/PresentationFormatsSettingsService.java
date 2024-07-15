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
import inetsoft.web.admin.presentation.model.PresentationFormatsSettingsModel;
import inetsoft.web.viewsheet.Audited;

import java.text.SimpleDateFormat;

import org.springframework.stereotype.Service;

@Service
public class PresentationFormatsSettingsService {
   public PresentationFormatsSettingsModel getModel(boolean globalProperty) {
      return PresentationFormatsSettingsModel.builder()
         .dateFormat(SreeEnv.getProperty("format.date", false, !globalProperty))
         .timeFormat(SreeEnv.getProperty("format.time", false, !globalProperty))
         .dateTimeFormat(SreeEnv.getProperty("format.date.time", false, !globalProperty))
         .build();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-General",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void setModel(PresentationFormatsSettingsModel model, boolean globalSettings) throws Exception {
      String formatDate = model.dateFormat();
      formatDate = formatDate == null ? "" : formatDate.trim();
      checkDateFormatPattern(formatDate);

      String formatTime = model.timeFormat();
      formatTime = formatTime == null ? "" : formatTime.trim();
      checkDateFormatPattern(formatTime);

      String formatDateTime = model.dateTimeFormat();
      formatDateTime = formatDateTime == null ? "" : formatDateTime;
      checkDateFormatPattern(formatDateTime);

      if("".equals(formatDate)) {
         SreeEnv.remove("format.date", !globalSettings);
      }
      else {
         SreeEnv.setProperty("format.date", formatDate, !globalSettings);
      }

      if("".equals(formatTime)) {
         SreeEnv.remove("format.time", !globalSettings);
      }
      else {
         SreeEnv.setProperty("format.time", formatTime, !globalSettings);
      }

      if("".equals(formatDateTime)) {
         SreeEnv.remove("format.date.time", !globalSettings);
      }
      else {
         SreeEnv.setProperty("format.date.time", formatDateTime, !globalSettings);
      }

      SreeEnv.save();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-General",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void resetSettings(boolean globalSettings) throws Exception {
      SreeEnv.remove("format.date", !globalSettings);
      SreeEnv.remove("format.time", !globalSettings);
      SreeEnv.remove("format.date.time", !globalSettings);

      SreeEnv.save();
   }

   private void checkDateFormatPattern(String pattern) throws IllegalArgumentException {
      if("".equals(pattern) ||
         "full".equalsIgnoreCase(pattern) ||
         "long".equalsIgnoreCase(pattern) ||
         "medium".equalsIgnoreCase(pattern) ||
         "short".equalsIgnoreCase(pattern)) {
         return;
      }

      SimpleDateFormat sformat = new SimpleDateFormat();
      sformat.applyPattern(pattern);
   }
}
