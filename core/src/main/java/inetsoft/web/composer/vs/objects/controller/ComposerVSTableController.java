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

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.composer.vs.objects.event.*;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.table.ChangeVSTableCellsTextEvent;
import inetsoft.web.viewsheet.model.*;
import inetsoft.web.viewsheet.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import java.security.Principal;

/**
 * Controller that processes vs table events in composer.
 */
@Controller
public class ComposerVSTableController {
   /**
    * Creates a new instance of <tt>ComposerVSTableController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated
    *                            with the WebSocket session.
    */
   @Autowired
   public ComposerVSTableController(RuntimeViewsheetRef runtimeViewsheetRef,
                                    ComposerVSTableServiceProxy composerVSTableServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.composerVSTableServiceProxy = composerVSTableServiceProxy;
   }

    /**
      * Change table header text.
      *
      * @param event      the event parameters.
      * @param principal  a principal identifying the current user.
      * @param dispatcher the command dispatcher.
      *
      * @throws Exception if unable to retrieve/edit object.
      */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/table/changeColumnTitle/{row}/{col}")
   public void changeColumnTitle(@DestinationVariable("row") int row,
                                 @DestinationVariable("col") int col,
                                 @Payload ChangeVSObjectTextEvent event, Principal principal,
                                 CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      composerVSTableServiceProxy.changeColumnTitle(runtimeViewsheetRef.getRuntimeId(), row, col,
                                                    event, principal, dispatcher, linkUri);
   }

   /**
    * Change text of embedded table cell.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/table/changeCellText")
   public void changeCellText(@Payload ChangeVSTableCellsTextEvent event,
                              Principal principal,
                              @LinkUri String linkUri,
                              CommandDispatcher dispatcher)
      throws Exception
   {
      composerVSTableServiceProxy.changeCellText(runtimeViewsheetRef.getRuntimeId(), event,
                                                 principal, linkUri, dispatcher);
   }

   /**
    * Remove table columns.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/table/deleteColumns")
   public void deleteColumns(@Payload RemoveTableColumnsEvent event, Principal principal,
                             CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      composerVSTableServiceProxy.deleteColumns(runtimeViewsheetRef.getRuntimeId(), event,
                                                principal, dispatcher, linkUri);
   }

   /**
    * Show and hide table columns.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/table/showHideColumns")
   public void hideColumns(@Payload ShowHideCrosstabColumnsEvent event, Principal principal,
                           CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      composerVSTableServiceProxy.hideColumns(runtimeViewsheetRef.getRuntimeId(), event,
                                              principal, dispatcher, linkUri);
   }

   /**
    * get actual index (on the displayed table) according to display index.
    * it could be different from column list index because column can been hidden.
    * @param columns all columnSelection
    * @param displayIndex display column index.
    */
   public static int getActualColIndex(ColumnSelection columns, int displayIndex) {
      if(columns != null) {
         // Starting from -1 is to prevent the actualIndex from being fixed to 1 when
         // displayIndex is 0.
         int actualIndex = -1, visibleIndex = -1;

         do {
            DataRef dataRef = columns.getAttribute(++actualIndex);

            // Just only accumulate the index for the visible column.
            if(dataRef instanceof ColumnRef && ((ColumnRef) dataRef).isVisible()) {
               visibleIndex++;
            }
         } while(visibleIndex < displayIndex);

         return actualIndex;
      }

      return displayIndex;
   }

   /**
    * Get the column index in ColumnSelection, taking into account of the hidden columns.
    * @param col the index in the displayed table.
    */
   public static int getBindingColIndex(ColumnSelection columns, int col) {
      int cidx = 0;

      for(; cidx < columns.getAttributeCount() && col >= 0; cidx++) {
         ColumnRef columnRef = (ColumnRef) columns.getAttribute(cidx);

         if(columnRef.isVisible()) {
            col--;
         }
      }

      return cidx - 1;
   }

   /**
    * Offset the column index according to the invisible columns.
    */
   public static int getOffsetColumnIndex(ColumnSelection columns, int dropIndex) {
      int actualIndex = dropIndex;

      for(int i = 0; i <= actualIndex && i < columns.getAttributeCount(); i++) {
         final DataRef ref = columns.getAttribute(i);

         if(ref instanceof ColumnRef && !((ColumnRef) ref).isVisible()) {
            actualIndex++;
         }
      }

      return actualIndex;
   }

   /**
    * Reset table layout.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/table/resetTableLayout")
   public void resetTableLayout(@Payload RemoveTableColumnsEvent event, Principal principal,
                                CommandDispatcher dispatcher) throws Exception
   {
      composerVSTableServiceProxy.resetTableLayout(runtimeViewsheetRef.getRuntimeId(), event,
                                                   principal, dispatcher);
   }

   /**
    * Resize table cell.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/table/changeCellSize")
   public void resizeTableCell(@Payload ResizeTableCellEvent event, Principal principal,
                               @LinkUri String linkUri, CommandDispatcher dispatcher)
      throws Exception
   {
      composerVSTableServiceProxy.resizeTableCell(runtimeViewsheetRef.getRuntimeId(), event,
                                                  principal, linkUri, dispatcher);
   }

   /**
    * Resize table columns.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/table/changeColumnWidth")
   public void changeColumnWidth(@Payload ResizeTableColumnEvent event, Principal principal,
                                 @LinkUri String linkUri,
                                 CommandDispatcher dispatcher) throws Exception
   {
      composerVSTableServiceProxy.changeColumnWidth(runtimeViewsheetRef.getRuntimeId(), event,
                                                    principal, linkUri, dispatcher);
   }

   /**
    * Resize the row heights.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/table/changeRowHeight")
   public void changeRowHeight(@Payload ResizeTableRowEvent event, Principal principal,
                               @LinkUri String linkUri,
                               CommandDispatcher dispatcher)
      throws Exception
   {
      composerVSTableServiceProxy.changeRowHeight(runtimeViewsheetRef.getRuntimeId(), event,
                                                  principal, linkUri, dispatcher);
   }

   /**
    * Change table to a freehand table. Mimic of ConvertToCalcTableEvent.java
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/table/convertToFreehand")
   public void convertToFreehandTable(@Payload ConvertToFreehandTableEvent event,
                                      Principal principal,
                                      @LinkUri String linkUri,
                                      CommandDispatcher dispatcher) throws Exception
   {
      composerVSTableServiceProxy.convertToFreehandTable(runtimeViewsheetRef.getRuntimeId(), event,
                                                         principal, linkUri, dispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private ComposerVSTableServiceProxy composerVSTableServiceProxy;


   private static final Logger LOG =
      LoggerFactory.getLogger(ComposerVSTableController.class);
}
