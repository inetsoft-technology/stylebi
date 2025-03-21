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
package inetsoft.web.vswizard.controller;

import inetsoft.web.composer.vs.objects.event.ChangeVSObjectTextEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.event.SetPreviewPaneSizeEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * vs wizard binding tree controller.
 */
@Controller
public class VSWizardPreviewController {
   @Autowired
   public VSWizardPreviewController(RuntimeViewsheetRef runtimeViewsheetRef,
                                    VSWizardPreviewServiceProxy vsWizardPreviewServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsWizardPreviewServiceProxy = vsWizardPreviewServiceProxy;
   }

   @MessageMapping("/vswizard/preview/changeDescription")
   public void changeDescription(@Payload ChangeVSObjectTextEvent event,
                                  CommandDispatcher dispatcher, Principal principal)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();
      vsWizardPreviewServiceProxy.changeDescription(id, event, dispatcher, principal);
   }

   @MessageMapping("/vswizard/preview/setPaneSize")
   public void setPreviewPaneSize(@Payload SetPreviewPaneSizeEvent event,
                                  CommandDispatcher dispatcher, @LinkUri String linkUri,
                                  Principal principal)
           throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();
      vsWizardPreviewServiceProxy.setPreviewPaneSize(id, event, dispatcher, linkUri, principal);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private VSWizardPreviewServiceProxy vsWizardPreviewServiceProxy;

}
