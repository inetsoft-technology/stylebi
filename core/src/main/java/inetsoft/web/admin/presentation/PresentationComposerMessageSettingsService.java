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
import inetsoft.web.admin.presentation.model.PresentationComposerMessageSettingsModel;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class PresentationComposerMessageSettingsService {

   public PresentationComposerMessageSettingsModel getModel(boolean globalProperty) {
      return PresentationComposerMessageSettingsModel.builder()
         .viewsheetCreateMessage(SreeEnv.getProperty("composer.vs.create.messsage", false, !globalProperty))
         .viewsheetEditMessage(SreeEnv.getProperty("composer.vs.edit.messsage", false, !globalProperty))
         .worksheetCreateMessage(SreeEnv.getProperty("composer.ws.create.messsage", false, !globalProperty))
         .worksheetEditMessage(SreeEnv.getProperty("composer.ws.edit.messsage", false, !globalProperty))
         .build();
   }

   public void setModel(PresentationComposerMessageSettingsModel model, boolean globalSettings) throws IOException {
      SreeEnv.setProperty("composer.vs.create.messsage", model.viewsheetCreateMessage(), !globalSettings);
      SreeEnv.setProperty("composer.vs.edit.messsage", model.viewsheetEditMessage(), !globalSettings);
      SreeEnv.setProperty("composer.ws.create.messsage", model.worksheetCreateMessage(), !globalSettings);
      SreeEnv.setProperty("composer.ws.edit.messsage", model.worksheetEditMessage(), !globalSettings);
      SreeEnv.save();
   }

   public void resetSettings(boolean globalSettings) throws IOException {
      SreeEnv.resetProperty("composer.vs.create.messsage", !globalSettings);
      SreeEnv.resetProperty("composer.vs.edit.messsage", !globalSettings);
      SreeEnv.resetProperty("composer.ws.create.messsage", !globalSettings);
      SreeEnv.resetProperty("composer.ws.edit.messsage", !globalSettings);

      SreeEnv.save();
   }
}
