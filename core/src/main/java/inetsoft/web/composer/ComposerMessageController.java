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
package inetsoft.web.composer;

import inetsoft.sree.SreeEnv;
import inetsoft.web.composer.model.ComposerCustomMessageModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ComposerMessageController {
   @GetMapping("/api/composer/customMessage")
   public ComposerCustomMessageModel getCustomMessage() {
      return ComposerCustomMessageModel.builder()
         .viewsheetCreateMessage(SreeEnv.getProperty("composer.vs.create.messsage"))
         .viewsheetEditMessage(SreeEnv.getProperty("composer.vs.edit.messsage"))
         .worksheetCreateMessage(SreeEnv.getProperty("composer.ws.create.messsage"))
         .worksheetEditMessage(SreeEnv.getProperty("composer.ws.edit.messsage"))
         .build();
   }
}
