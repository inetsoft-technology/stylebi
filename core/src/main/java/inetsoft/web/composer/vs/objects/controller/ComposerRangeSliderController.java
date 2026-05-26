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
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.web.composer.vs.objects.event.ConvertToRangeSliderEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import java.security.Principal;

/**
 * Controller that processes VS Selection List events.
 */
@Controller
public class ComposerRangeSliderController {
   /**
    * Creates a new instance of <tt>ComposerRangeSliderController</tt>.
    *  @param runtimeViewsheetRef the runtime viewsheet reference
    */
   @Autowired
   public ComposerRangeSliderController(
      RuntimeViewsheetRef runtimeViewsheetRef,
      ComposerRangeSliderServiceProxy composerRangeSliderServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.composerRangeSliderServiceProxy = composerRangeSliderServiceProxy;

   }

   /**
    * Change range slider to a selection list. Mimic of ConvertCSComponentEvent.java
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/rangeSlider/convertToSelectionList")
   public void convertCSComponent(@Payload ConvertToRangeSliderEvent event,
                                  Principal principal,
                                  CommandDispatcher dispatcher,
                                  @LinkUri String linkUri) throws Exception
   {
      composerRangeSliderServiceProxy.convertCSComponent(runtimeViewsheetRef.getRuntimeId(), event,
                                                         principal, dispatcher, linkUri);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private ComposerRangeSliderServiceProxy composerRangeSliderServiceProxy;
}
