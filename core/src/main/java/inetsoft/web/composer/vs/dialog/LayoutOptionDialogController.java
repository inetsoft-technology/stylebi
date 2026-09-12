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
package inetsoft.web.composer.vs.dialog;

import inetsoft.web.composer.model.vs.LayoutOptionDialogModel;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import java.security.Principal;

/**
 * Controller that provides the REST endpoints for the layout option dialog.
 *
 * @since 12.3
 */
@Controller
public class LayoutOptionDialogController {
   /**
    * Creates a new instance of <tt>LayoutOptionDialogController</tt>.
    *
    * @param runtimeViewsheetRef RuntimeViewsheetRef instance
    */
   @Autowired
   public LayoutOptionDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                       LayoutOptionDialogServiceProxy layoutOptionDialogServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.layoutOptionDialogServiceProxy = layoutOptionDialogServiceProxy;
   }

   /**
    * Sets new positioning of an object being placed inside a container.
    *
    * @param model      the layout option model.
    * @param principal  the principal.
    * @param dispatcher the the command dispatcher.
    */
   @Undoable
   @MessageMapping("/composer/vs/layout-option-dialog-model/")
   public void setLayoutOptionDialogModel(@Payload LayoutOptionDialogModel model,
                                          Principal principal,
                                          @LinkUri String linkUri,
                                          CommandDispatcher dispatcher)
      throws Exception
   {
      layoutOptionDialogServiceProxy.setLayoutOptionDialogModel(runtimeViewsheetRef.getRuntimeId(),
                                                           model, principal, linkUri, dispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private LayoutOptionDialogServiceProxy layoutOptionDialogServiceProxy;
}
