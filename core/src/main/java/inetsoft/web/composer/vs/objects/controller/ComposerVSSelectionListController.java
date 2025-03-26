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
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.binding.dnd.TableTransfer;
import inetsoft.web.binding.event.VSDndEvent;
import inetsoft.web.composer.vs.objects.event.*;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Controller that processes VS Selection List events.
 */
@Controller
public class ComposerVSSelectionListController {
   /**
    * Creates a new instance of <tt>ComposerVSSelectionListController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated
    *                            with the WebSocket session.
    */
   @Autowired
   public ComposerVSSelectionListController(RuntimeViewsheetRef runtimeViewsheetRef,
                                            ComposerVSSelectionListServiceProxy composerVSSelectionListServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.composerVSSelectionListServiceProxy = composerVSSelectionListServiceProxy;
   }

   /**
    * Change the number of columns in the selection list.
    *
    * @param count      the number of columns.
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/selectionList/changeColCount/{count}")
   public void changeColCount(@DestinationVariable("count") int count,
                              VSObjectEvent event, Principal principal,
                              CommandDispatcher dispatcher) throws Exception
   {
      composerVSSelectionListServiceProxy.changeColCount(runtimeViewsheetRef.getRuntimeId(), count,
                                                         event, principal, dispatcher);
   }

   /**
    * Change the height of the cells  in the selection
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/selectionList/updateCellHeight/")
   public void updateCellHeight(VSSetCellHeightEvent event,
                                Principal principal,
                                CommandDispatcher dispatcher)
      throws Exception
   {
      composerVSSelectionListServiceProxy.updateCellHeight(runtimeViewsheetRef.getRuntimeId(),
                                                           event, principal, dispatcher);
   }

   /**
    * Change the size of the cell measures in the selection
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/selectionList/updateMeasureSize/")
   public void updateMeasureSize(VSSetMeasuresEvent event,
                                 Principal principal,
                                 CommandDispatcher dispatcher)
      throws Exception
   {
      composerVSSelectionListServiceProxy.updateMeasureSize(runtimeViewsheetRef.getRuntimeId(),
                                                            event, principal, dispatcher);
   }

   /**
    * Remove column from selection list or tree by dragging a selection list cell with that
    * column's data to the asset tree
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping(value="/vsselection/dnd/removeColumns")
   public void removeColumnByDrag(@Payload VSDndEvent event, Principal principal,
                         @LinkUri String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      composerVSSelectionListServiceProxy.removeColumnByDrag(runtimeViewsheetRef.getRuntimeId(),
                                                              event, principal, linkUri, dispatcher);
   }

   /**
    * Change selection list to a range slider. Mimic of ConvertCSComponentEvent.java
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/selectionList/convertToRangeSlider")
   public void convertToRangeSlider(@Payload ConvertToRangeSliderEvent event,
                                    Principal principal,
                                    CommandDispatcher dispatcher,
                                    @LinkUri String linkUri) throws Exception
   {
      composerVSSelectionListServiceProxy.convertToRangeSlider(runtimeViewsheetRef.getRuntimeId(),
                                                               event, principal, dispatcher, linkUri);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private ComposerVSSelectionListServiceProxy composerVSSelectionListServiceProxy;

   private static final Logger LOG =
      LoggerFactory.getLogger(ComposerVSSelectionListController.class);
}
