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
package inetsoft.web.viewsheet.controller;

import inetsoft.util.*;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.event.VSDeletedMatchedBookmarksEvent;
import inetsoft.web.viewsheet.event.VSEditBookmarkEvent;
import inetsoft.web.viewsheet.model.*;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@Controller
public class VSBookmarkController {
   @Autowired
   public VSBookmarkController(RuntimeViewsheetRef runtimeViewsheetRef,
                               VSBookmarkServiceProxy vsBookmarkServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsBookmarkServiceProxy = vsBookmarkServiceProxy;
   }

   /**
    * Save current bookmark
    *
    * @param value
    * @param principal
    * @param commandDispatcher
    * @param linkUri
    * @throws Exception
    */
   @MessageMapping("/vs/bookmark/save-bookmark")
   public void saveBookmark(@Payload VSEditBookmarkEvent value, Principal principal,
                            CommandDispatcher commandDispatcher, @LinkUri String linkUri)
      throws Exception
   {
      vsBookmarkServiceProxy.saveBookmark(runtimeViewsheetRef.getRuntimeId(), value, principal,
                   commandDispatcher, linkUri);
   }

   /**
    * Add new bookmark
    *
    * @param value
    * @param principal
    * @param commandDispatcher
    * @param linkUri
    * @throws Exception
    */
   @MessageMapping("/vs/bookmark/add-bookmark")
   public void addBookmark(@Payload VSEditBookmarkEvent value, Principal principal,
                           CommandDispatcher commandDispatcher, @LinkUri String linkUri)
      throws Exception
   {
      vsBookmarkServiceProxy.addBookmark(runtimeViewsheetRef.getRuntimeId(), value, principal, commandDispatcher, linkUri);
   }

   /**
    * Delete the specific bookmark
    *
    * @param value
    * @param principal
    * @param commandDispatcher
    * @param linkUri
    * @throws Exception
    */
   @MessageMapping("/vs/bookmark/delete-bookmark")
   public void deleteBookmark(@Payload VSEditBookmarkEvent value, Principal principal,
                              CommandDispatcher commandDispatcher, @LinkUri String linkUri)
      throws Exception
   {
      vsBookmarkServiceProxy.deleteBookmark(runtimeViewsheetRef.getRuntimeId(), value, principal, commandDispatcher, linkUri);
   }

   /**
    * Delete the matched condition bookmarks.
    */
   @MessageMapping("/vs/bookmark/delete-matched-bookmarks")
   public void deleteMatchedBookmarks(@Payload VSDeletedMatchedBookmarksEvent event, Principal principal,
                                      CommandDispatcher commandDispatcher, @LinkUri String linkUri)
           throws Exception
   {
      vsBookmarkServiceProxy.deleteMatchedBookmarks(runtimeViewsheetRef.getRuntimeId(), event, principal, commandDispatcher, linkUri);
   }

   /**
    * Edit the properties of bookmark.
    */
   @MessageMapping("/vs/bookmark/edit-bookmark")
   public void editBookmark(@Payload VSEditBookmarkEvent value, Principal principal,
                            CommandDispatcher commandDispatcher, @LinkUri String linkUri)
      throws Exception
   {
    vsBookmarkServiceProxy.editBookmark(runtimeViewsheetRef.getRuntimeId(), value, principal, commandDispatcher, linkUri);
   }

   /**
    * Gets the viewsheets bookmarks.
    * @param runtimeId  the runtime id
    * @param principal  the principal user
    * @return  the list of bookmarks
    * @throws Exception if could not get the bookmarks
    */
   @RequestMapping(value="/api/vs/bookmark/get-bookmarks/**", method = RequestMethod.GET)
   @ResponseBody
   public VSBookmarkInfoModel[] getBookmarks(@RemainingPath String runtimeId,
      @RequestParam(value = "localTimeZone", defaultValue = "") String localTimeZone,
      Principal principal)
      throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return vsBookmarkServiceProxy.getBookmarks(runtimeId, localTimeZone, principal);
   }

   /**
    * Switch to the specific bookmark
    *
    * @param value
    * @param principal
    * @param commandDispatcher
    * @param linkUri
    * @throws Exception
    */
   @LoadingMask
   @MessageMapping("/vs/bookmark/goto-bookmark")
   public void gotoBookmark(@Payload VSEditBookmarkEvent value, Principal principal,
                            CommandDispatcher commandDispatcher, @LinkUri String linkUri)
      throws Exception
   {
      vsBookmarkServiceProxy.gotoBookmark(runtimeViewsheetRef.getRuntimeId(), value, principal, commandDispatcher, linkUri);
   }

   /**
    * Set the default bookmark
    *
    * @param value
    * @param principal
    * @param commandDispatcher
    * @param linkUri
    * @throws Exception
    */
   @MessageMapping("/vs/bookmark/set-default-bookmark")
   public void setDefaultBookmark(@Payload VSEditBookmarkEvent value, Principal principal,
                                  CommandDispatcher commandDispatcher, @LinkUri String linkUri)
      throws Exception
   {
      vsBookmarkServiceProxy.setDefaultBookmark(runtimeViewsheetRef.getRuntimeId(), value,
                                                principal, commandDispatcher,linkUri);
   }

   @RequestMapping(
      value="/api/vs/bookmark/check-bookmark-changed/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public String checkBookmarkChanged(@RemainingPath String runtimeId,
                                      @RequestParam(value = "name", defaultValue = "") String name,
                                      @RequestParam(value = "owner", defaultValue = "") String owner,
                                      Principal principal)
      throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return vsBookmarkServiceProxy.checkBookmarkChanged(runtimeId, name, owner, principal);
   }

   @RequestMapping(
      value="/api/vs/bookmark/check-bookmark-deleted/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public String checkBookmarkDeleted(@RemainingPath String runtimeId,
                                      @RequestParam(value = "name", defaultValue = "") String name,
                                      @RequestParam(value = "owner", defaultValue = "") String owner,
                                      Principal principal)
      throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return vsBookmarkServiceProxy.checkBookmarkDeleted(runtimeId, name, owner, principal);
     }

   @RequestMapping(
      value="api/vs/bookmark/isDefaultOrgAsset/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public boolean isDefaultOrgAsset(@RemainingPath String assetID, Principal principal)
      throws Exception
   {
      assetID = Tool.byteDecode(assetID);
      return vsBookmarkServiceProxy.isDefaultOrgAsset(assetID, principal);
   }

   private final VSBookmarkServiceProxy vsBookmarkServiceProxy;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
}
