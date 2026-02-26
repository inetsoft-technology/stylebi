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
import inetsoft.web.admin.presentation.model.PresentationAISettingsModel;
import inetsoft.web.viewsheet.Audited;
import org.springframework.stereotype.Service;

@Service
public class AISettingsService {
   public PresentationAISettingsModel getModel() {
      String aiAssistantVisibleProp = SreeEnv.getProperty("portal.ai.assistant.visible", "true");
      boolean aiAssistantVisible = "true".equalsIgnoreCase(aiAssistantVisibleProp);
      String chatAppServerUrl = SreeEnv.getProperty("chat.app.server.url");

      return PresentationAISettingsModel.builder()
         .aiAssistantVisible(aiAssistantVisible)
         .chatAppServerUrl(chatAppServerUrl)
         .build();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-AI Integration",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void setModel(PresentationAISettingsModel model) throws Exception {
      SreeEnv.setProperty("portal.ai.assistant.visible", model.aiAssistantVisible() ? "true" : "false");
      SreeEnv.setProperty("chat.app.server.url", model.chatAppServerUrl());
      SreeEnv.save();
   }

   public void resetSettings() throws Exception {
      SreeEnv.remove("portal.ai.assistant.visible");
      SreeEnv.save();
   }
}
