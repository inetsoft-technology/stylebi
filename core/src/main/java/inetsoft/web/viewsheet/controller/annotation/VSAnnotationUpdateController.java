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
package inetsoft.web.viewsheet.controller.annotation;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.RemoveVSObjectCommand;
import inetsoft.web.viewsheet.event.annotation.UpdateAnnotationEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.security.Principal;

@Controller
@MessageMapping("/annotation")
public class VSAnnotationUpdateController {
   @Autowired
   public VSAnnotationUpdateController(RuntimeViewsheetRef runtimeViewsheetRef,
                                       VSAnnotationUpdateServiceProxy vsAnnotationUpdateServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsAnnotationUpdateServiceProxy = vsAnnotationUpdateServiceProxy;
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/update-annotation")
   public void updateAnnotation(@Payload UpdateAnnotationEvent event,
                                Principal principal,
                                @LinkUri String linkUri,
                                CommandDispatcher dispatcher) throws Exception
   {
      vsAnnotationUpdateServiceProxy.updateAnnotation(runtimeViewsheetRef.getRuntimeId(), event,
                                                      principal, linkUri, dispatcher);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/update-annotation-endpoint")
   public void updateAnnotationEndpoint(@Payload UpdateAnnotationEvent event,
                                        Principal principal,
                                        @LinkUri String linkUri,
                                        CommandDispatcher dispatcher) throws Exception
   {
      vsAnnotationUpdateServiceProxy.updateAnnotationEndpoint(runtimeViewsheetRef.getRuntimeId(), event,
                                                              principal, linkUri, dispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private VSAnnotationUpdateServiceProxy vsAnnotationUpdateServiceProxy;
}