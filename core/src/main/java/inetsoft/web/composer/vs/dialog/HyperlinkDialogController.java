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

import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.portal.controller.RepositoryTreeService;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;

/**
 * Controller that provides the endpoints for the hyperlink dialog.
 *
 * @since 12.3
 */
@Controller
public class HyperlinkDialogController {
   /**
    * Creates a new instance of <tt>HyperlinkDialogController</tt>.
    *
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    */
   @Autowired
   public HyperlinkDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                    AssetRepository assetRepository,
                                    RepositoryTreeService repositoryTreeService,
                                    HyperlinkDialogServiceProxy hyperlinkDialogServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.assetRepository = assetRepository;
      this.repositoryTreeService = repositoryTreeService;
      this.hyperlinkDialogServiceProxy = hyperlinkDialogServiceProxy;
   }

   /**
    * Gets the model for the hyperlink dialog.
    *
    * @param objectId  the object identifier.
    * @param row       the row of the selected cell.
    * @param col       the column of the selected cell.
    * @param runtimeId the runtime identifier of the viewsheet.
    * @param principal the user information.
    *
    * @return the model.
    */
   @RequestMapping(
      value = "/api/composer/vs/hyperlink-dialog-model",
      method = RequestMethod.GET
   )
   @ResponseBody
   public HyperlinkDialogModel getHyperlinkDialogModel(
      @RequestParam("objectId") String objectId,
      @RequestParam(value = "row", required = false, defaultValue = "0") Integer row,
      @RequestParam(value = "col", required = false, defaultValue = "0") Integer col,
      @RequestParam(value = "colName", required = false) String colName,
      @RequestParam(value = "isAxis", required = false) boolean isAxis,
      @RequestParam(value = "isText", required = false) boolean isText,
      @RequestParam("runtimeId") String runtimeId, Principal principal)
      throws Exception
   {
      return hyperlinkDialogServiceProxy.getHyperlinkDialogModel(runtimeId, objectId, row, col,
                                                          colName, isAxis, isText, principal);
   }

   @RequestMapping(
      value = "/api/composer/vs/hyperlink-parameters",
      method = RequestMethod.GET
   )
   @ResponseBody
   public String[] getViewsheetParameters(
      @RequestParam("assetId") String assetId, Principal principal)
      throws Exception
   {
      AssetEntry entry = AssetEntry.createAssetEntry(assetId);
      Viewsheet vs = (Viewsheet)
         assetRepository.getSheet(entry, principal, false, AssetContent.NO_DATA);

      if(vs == null) {
         return new String[0];
      }

      return Arrays.stream(vs.getAllVariables())
         .map(v -> v.getName())
         .toArray(String[]::new);
   }

   @RequestMapping(
      value = "/api/composer/vs/hyperlink-dialog-model/tree",
      method = RequestMethod.GET
   )
   @ResponseBody
   public TreeNodeModel getFolder(
      @RequestParam(value = "path", defaultValue = "/") String path,
      @RequestParam(value = "isOnPortal", defaultValue = "false") String isOnPortal,
      Principal principal) throws Exception
   {
      int selector = RepositoryEntry.VIEWSHEET | RepositoryEntry.FOLDER;
      TreeNodeModel root = repositoryTreeService.getRootFolder("/", ResourceAction.READ, selector,
         "", principal, "true".equals(isOnPortal));
      return root;
   }

   /**
    * Get the bookmarks of a specific sheet.
    *
    * @param id        the id of the viewsheet.
    * @param principal the user information.
    *
    * @return the list of bookmarks.
    */
   @GetMapping("/api/composer/vs/hyperlink-dialog-model/bookmarks/**")
   @ResponseBody
   public List<String> getBookmarks(@RemainingPath String id, Principal principal) throws Exception
   {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      VSBookmarkInfo[] bookmarks = VSUtil.getBookmarks(id, pId);
      List<String> bookmarkNames = new ArrayList<>();

      for(VSBookmarkInfo bookmark: bookmarks) {
         bookmarkNames.add(bookmark.getName() + "(" + VSUtil.getUserAlias(bookmark.getOwner()) + ")");
      }

      return bookmarkNames;
   }

   /**
    * Sets information gathered from the hyperlink dialog.
    *
    * @param objectId   the object id
    * @param model      the hyperlink dialog model.
    * @param principal  the user information.
    * @param dispatcher the command dispatcher.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/hyperlink-dialog-model/{objectId}")
   public void setHyperlinkDialogModel(@DestinationVariable("objectId") String objectId,
                                       @Payload HyperlinkDialogModel model,
                                       @LinkUri String linkUri,
                                       Principal principal,
                                       CommandDispatcher dispatcher)
      throws Exception
   {
      hyperlinkDialogServiceProxy.setHyperlinkDialogModel(runtimeViewsheetRef.getRuntimeId(),
                                                          objectId, model, linkUri, principal, dispatcher);
   }

   /**
    * Check whether the parameters set for the table hyperlink will cause a trap.
    *
    * @param model     the model containing the hyperlink model
    * @param objectId  the object id
    * @param runtimeId the runtime id
    * @param principal the user principal
    *
    * @return the table trap model stating whether or not here is a trap.
    */
   @PostMapping("/api/composer/viewsheet/check-hyperlink-dialog-trap/{objectId}/**")
   @ResponseBody
   public VSTableTrapModel checkVSTableTrap(
      @RequestBody() HyperlinkDialogModel model,
      @PathVariable("objectId") String objectId,
      @RemainingPath String runtimeId,
      Principal principal) throws Exception
   {
      return hyperlinkDialogServiceProxy.checkVSTableTrap(runtimeId, model, objectId, principal);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final HyperlinkDialogServiceProxy hyperlinkDialogServiceProxy;
   private final RepositoryTreeService repositoryTreeService;
   private final AssetRepository assetRepository;
}
