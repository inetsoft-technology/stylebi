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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.LineVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.LineVSAssemblyInfo;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.vs.objects.event.ResizeVSLineEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Controller that processes vs line events.
 */
@Controller
public class VSLineController {
   /**
    * Creates a new instance of <tt>VSLineController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated
    *                            with the WebSocket session.
    * @param viewsheetService
    */
   @Autowired
   public VSLineController(
      RuntimeViewsheetRef runtimeViewsheetRef,
      ViewsheetService viewsheetService,
      VSAssemblyInfoHandler vsAssemblyInfoHandler)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.viewsheetService = viewsheetService;
      this.vsAssemblyInfoHandler = vsAssemblyInfoHandler;
   }

   /**
    * Resize vs line component.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/vsLine/resize")
   public void resize(@Payload ResizeVSLineEvent event, Principal principal,
                      CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      LineVSAssembly assembly = (LineVSAssembly) viewsheet.getAssembly(event.getName());

      assert assembly != null;

      Point startPosition = new Point(event.getStartLeft(), event.getStartTop());
      Point endPosition = new Point(event.getEndLeft(), event.getEndTop());
      Point offset = new Point(event.getOffsetX(), event.getOffsetY());
      Dimension size = new Dimension(Math.max(1, Math.abs(endPosition.x - startPosition.x)),
                                     Math.max(1, Math.abs(endPosition.y - startPosition.y)));

      assembly.setStartPos(startPosition);
      assembly.setEndPos(endPosition);
      assembly.setPixelOffset(offset);
      assembly.setPixelSize(size);

      LineVSAssemblyInfo lineInfo = (LineVSAssemblyInfo) assembly.getInfo();

      if(event.getStartAnchorId() != null) {
         lineInfo.setStartAnchorID(event.getStartAnchorId());
         lineInfo.setStartAnchorPos(event.getStartAnchorPos());
      }
      else {
         lineInfo.setStartAnchorID(null);
      }

      if(event.getEndAnchorId() != null) {
         lineInfo.setEndAnchorID(event.getEndAnchorId());
         lineInfo.setEndAnchorPos(event.getEndAnchorPos());
      }
      else {
         lineInfo.setEndAnchorID(null);
      }

      List<String> assemblyName = Stream.of(event.getName()).collect(Collectors.toList());
      vsAssemblyInfoHandler.updateAnchoredLines(rvs, assemblyName, dispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ViewsheetService viewsheetService;
   private final VSAssemblyInfoHandler vsAssemblyInfoHandler;

   private static final Logger LOG =
      LoggerFactory.getLogger(VSLineController.class);
}
