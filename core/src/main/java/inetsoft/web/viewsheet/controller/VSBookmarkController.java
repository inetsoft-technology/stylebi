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

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.sync.ViewsheetBookmarkChangedEvent;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;
import inetsoft.util.audit.AuditRecordUtils;
import inetsoft.util.audit.BookmarkRecord;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.VSDeletedMatchedBookmarksEvent;
import inetsoft.web.viewsheet.event.VSEditBookmarkEvent;
import inetsoft.web.viewsheet.model.*;
import inetsoft.web.viewsheet.service.*;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class VSBookmarkController {
   @Autowired
   public VSBookmarkController(VSObjectService service, VSBookmarkService bookmarkService,
                               ViewsheetService viewsheetService,
                               RuntimeViewsheetRef runtimeViewsheetRef,
                               SecurityEngine securityEngine)
   {
      this.service = service;
      this.bookmarkService = bookmarkService;
      this.viewsheetService = viewsheetService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.securityEngine = securityEngine;
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
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs =
         service.getRuntimeViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet vs = rvs.getViewsheet();
      VSBookmarkInfoModel vsBookmarkInfoModel = value.vsBookmarkInfoModel();
      String name = Tool.defaultIfNull(vsBookmarkInfoModel.name(), "");
      IdentityID ownerId = Tool.defaultIfNull(vsBookmarkInfoModel.owner(),new IdentityID("",""));
      boolean readOnly = vsBookmarkInfoModel.readOnly();
      int type = vsBookmarkInfoModel.type();
      String originalAction = value.instruction();
      AssetEntry vsEntry = vs.getRuntimeEntry();

      if(vsEntry == null) {
         return;
      }

      if(vsEntry != null && vsEntry.getScope() == AssetRepository.TEMPORARY_SCOPE) {
         MessageCommand command = new MessageCommand();
         command.setMessage(catalog.getString("common.viewsheet.saveViewsheetDependence"));
         command.setType(MessageCommand.Type.ERROR);
         commandDispatcher.sendCommand(command);
         return;
      }

      if(VSBookmark.HOME_BOOKMARK.equals(name)) {
         String id = rvs.getOriginalID();
         RuntimeViewsheet rvs0 = id == null ? rvs : engine.getViewsheet(id, principal);
         Viewsheet vs0 = rvs0.getViewsheet();
         Viewsheet ovs = vs.clone();
         AssetEntry vsEntry0 = rvs0.getEntry();

         // could be disposed
         if(vs0 == null || vsEntry0 == null) {
            return;
         }

         vsEntry.setProperty("noAnnotation", "true");
         // use the preview runtime viewsheet to update the edit viewsheet
         VSEventUtil.updateViewsheet(ovs, true, vs0);
         vsEntry.setProperty("noAnnotation", null);
         vsEntry0.setProperty("homeBookmarkSaved", "true");
         vsEntry0.setProperty("bookmarkIndex", null);

         try {
            engine.setViewsheet(vs0, vsEntry0, principal, true, true);
         }
         finally {
            if(rvs.isViewer()) {
               vs.getRuntimeEntry().setProperty("keepAnnoVis", "true");
               Viewsheet nvs = rvs.getViewsheet();
               VSEventUtil.updateViewsheet(ovs, true, nvs);
               vs.getRuntimeEntry().setProperty("keepAnnoVis", null);
            }
         }

         if(rvs.isPreview()) {
            rvs0.setSavePoint(rvs0.getCurrent());
            service.refreshViewsheet(rvs0, linkUri, new CommandDispatcher(commandDispatcher, value.clientId()));
         }
         else if(rvs.isViewer()) {
            if(vs.getViewsheetInfo().isScaleToScreen()) {
               Object scaleSize = rvs.getProperty("viewsheet.appliedScale");

               if(scaleSize instanceof Dimension && ((Dimension) scaleSize).width > 0 &&
                  ((Dimension) scaleSize).height > 0)
               {
                  service.refreshViewsheet(rvs, ((Dimension) scaleSize).width,
                     ((Dimension) scaleSize).height, linkUri, commandDispatcher);
               }
            }
         }
         else {
            rvs.getViewsheetSandbox().updateAssemblies();
         }
      }

      if(!value.confirmed()) {
         if(rvs.bookmarkUpdated(name, ownerId)) {
            MessageCommand messageCommand = new MessageCommand();
            messageCommand.setMessage("The bookmark has been changed by others but front end did not check whether to override it.");
            messageCommand.setType(MessageCommand.Type.ERROR);
            commandDispatcher.sendCommand(messageCommand);
            return;
         }
         else if(!rvs.checkBookmark(name, ownerId)) {
            MessageCommand messageCommand = new MessageCommand();
            messageCommand.setMessage("The bookmark has been deleted by owner but front end did not check whether to readd it.");
            messageCommand.setType(MessageCommand.Type.ERROR);
            commandDispatcher.sendCommand(messageCommand);
            return;
         }
      }

      String bookmarkConfirmed = value.bookmarkConfirmed();

      String action;

      if(bookmarkConfirmed != null) {
         if("Override".equals(bookmarkConfirmed)) {
            if(!rvs.bookmarkWritable(name, ownerId)) {
               MessageCommand messageCommand = new MessageCommand();
               messageCommand.setMessage(
                  catalog.getString("viewer.viewsheet.bookmark.notWritable"));
               messageCommand.setType(MessageCommand.Type.ERROR);
               commandDispatcher.sendCommand(messageCommand);
               return;
            }
         }

         action = "Override".equals(bookmarkConfirmed) ? "add" : "readd";
      }
      else {
         action = "add";
      }

      SRPrincipal owner = new SRPrincipal(ownerId);

      if(principal instanceof SRPrincipal &&
         !SUtil.isInternalUser((SRPrincipal) principal) &&
         Tool.equals(((SRPrincipal) principal).getIdentityID(), ownerId))
      {
         owner = (SRPrincipal) principal;
      }

      VSBookmarkInfo origBookmarkInfo = new VSBookmarkInfo();

      if(rvs.getBookmarkInfo(name, ownerId) != null) {
         BeanUtils.copyProperties(rvs.getBookmarkInfo(name, ownerId), origBookmarkInfo);
      }

      if("add".equals(action)) {
         rvs.removeBookmark(name, ownerId, false);
         //add the bookmark
         bookmarkService.addBookmark(rvs, name, type, readOnly,
                                     !"add".equals(originalAction) || value.confirmed(), owner,
                                     commandDispatcher, value, BookmarkRecord.ACTION_TYPE_MODIFY,
                                     origBookmarkInfo);
      }
      else if("readd".equals(action)) {
         //The bookmark has been deleted by other user, add it back.
         rvs.updateVSBookmark();
         bookmarkService.addBookmark(rvs, name, type, readOnly,
                                     !"add".equals(originalAction) || value.confirmed(), owner,
                                     commandDispatcher, value, BookmarkRecord.ACTION_TYPE_MODIFY,
                                     origBookmarkInfo);
      }
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
      RuntimeViewsheet rvs =
         service.getRuntimeViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      String originalAction = value.instruction();
      VSBookmarkInfoModel vsBookmarkInfoModel = value.vsBookmarkInfoModel();
      String name = Tool.defaultIfNull(vsBookmarkInfoModel.name(),"");
      int type = vsBookmarkInfoModel.type();
      boolean readOnly = vsBookmarkInfoModel.readOnly();

      bookmarkService.addBookmark(rvs, name, type, readOnly,
                                  !"add".equals(originalAction) || value.confirmed(), principal,
                                  commandDispatcher, value, BookmarkRecord.ACTION_TYPE_CREATE, null);
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
      RuntimeViewsheet rvs =
         service.getRuntimeViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      VSBookmarkInfoModel vsBookmarkInfoModel = value.vsBookmarkInfoModel();
      assert vsBookmarkInfoModel != null;
      String name = Tool.defaultIfNull(vsBookmarkInfoModel.name(), "");
      IdentityID owner = Tool.defaultIfNull(vsBookmarkInfoModel.owner(), new IdentityID("", ""));

      IdentityID user = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

      if(!value.confirmed()) {
         final List<ScheduleTask> tasksUsingBookmark =
            bookmarkService.getScheduledTasksUsingBookmark(name, user, rvs.getEntry().toIdentifier());

         if(!tasksUsingBookmark.isEmpty()) {
            MessageCommand messageCommand = new MessageCommand();
            final String tasksStr = tasksUsingBookmark.stream()
               .map(scheduleTask -> scheduleTask.toView(true))
               .collect(Collectors.joining(", "));
            messageCommand.setMessage(
               catalog.getString("viewer.viewsheet.deleteBookmarkInSchedule", name, tasksStr));
            messageCommand.setType(MessageCommand.Type.ERROR);
            commandDispatcher.sendCommand(messageCommand);
            return;
         }
      }

      try {
         VSBookmarkInfo currBookmark = rvs.getOpenedBookmark();
         VSBookmarkInfo bookmarkInfo = rvs.getBookmarkInfo(name, IdentityID.getIdentityIDFromKey(principal.getName()));
         rvs.removeBookmark(name, owner);
         AuditRecordUtils.executeBookmarkRecord(
            rvs.getViewsheet(), bookmarkInfo, BookmarkRecord.ACTION_TYPE_DELETE);

         // After remove bookmark, go to home bookmark.
         if(currBookmark != null && currBookmark.getName().equals(name)) {
            bookmarkService.processBookmark(VSBookmark.HOME_BOOKMARK, IdentityID.getIdentityIDFromKey(principal.getName()),
                                            rvs, (XPrincipal) principal, value, linkUri,
                                            rvs.getID(), commandDispatcher);
         }
      }
      catch(MessageException ex) {
         MessageCommand command = new MessageCommand();
         command.setMessage(ex.getMessage());
         command.setType(MessageCommand.Type.ERROR);
         commandDispatcher.sendCommand(command);
      }
   }

   /**
    * Delete the matched condition bookmarks.
    */
   @MessageMapping("/vs/bookmark/delete-matched-bookmarks")
   public void deleteMatchedBookmarks(@Payload VSDeletedMatchedBookmarksEvent event, Principal principal,
                                      CommandDispatcher commandDispatcher, @LinkUri String linkUri)
           throws Exception
   {
      RuntimeViewsheet rvs = service.getRuntimeViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      IdentityID user = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      RemoveAnnotationsCondition condition = event.condition();
      List<VSBookmarkInfo> bookmarks = rvs.getBookmarks(user);
      Date filterTime = Tool.parseDate(condition.getFilterTime());

      if(filterTime == null) {
         return;
      }

      List<String> removeBookmarks = new ArrayList<>();
      StringBuilder deleteConfirmMessage = new StringBuilder();

      for(VSBookmarkInfo bookmark : bookmarks) {
         if(!user.equals(bookmark.getOwner()) || VSBookmark.HOME_BOOKMARK.equals(bookmark.getName()) ||
            VSBookmark.INITIAL_STATE.equals(bookmark.getName()))
         {
            continue;
         }

         if(condition.getFilterOption() == RemoveAnnotationsCondition.AnnotationFilterOption.NOT_ACCESSED &&
            bookmark.getLastAccessed() < filterTime.getTime() ||
            condition.getFilterOption() == RemoveAnnotationsCondition.AnnotationFilterOption.OLDER_THAN &&
            bookmark.getCreateTime() < filterTime.getTime())
         {
            final List<ScheduleTask> tasksUsingBookmark =
                    bookmarkService.getScheduledTasksUsingBookmark(bookmark.getName(), user, rvs.getEntry().toIdentifier());

            if(!tasksUsingBookmark.isEmpty()) {
               final String tasksStr = tasksUsingBookmark.stream().map(ScheduleTask::getTaskId)
                       .collect(Collectors.joining(", "));
               String message = catalog.getString("viewer.viewsheet.deleteBookmarkInSchedule",
                                                  bookmark.getName(), tasksStr);

               if(deleteConfirmMessage.indexOf(message) < 0) {
                  deleteConfirmMessage.append(message);
                  deleteConfirmMessage.append("\n");
               }
            }
            else {
               removeBookmarks.add(bookmark.getName());
            }
         }
      }

      if(removeBookmarks.isEmpty() && deleteConfirmMessage.length() == 0) {
         MessageCommand messageCommand = new MessageCommand();
         messageCommand.setMessage(Catalog.getCatalog().getString(
            "viewer.viewsheet.bookmark.deleteNoMatch"));
         messageCommand.setType(MessageCommand.Type.INFO);
         commandDispatcher.sendCommand(messageCommand);
         return;
      }

      if(deleteConfirmMessage.length() > 0) {
         MessageCommand messageCommand = new MessageCommand();
         messageCommand.setMessage(deleteConfirmMessage.toString());
         messageCommand.setType(MessageCommand.Type.ERROR);
         commandDispatcher.sendCommand(messageCommand);
      }

      try {
         VSBookmarkInfo currBookmark = rvs.getOpenedBookmark();
         rvs.removeBookmarks(removeBookmarks, user);

         // After remove bookmark, go to home bookmark.
         if(currBookmark != null && removeBookmarks.contains(currBookmark.getName())) {
            bookmarkService.processBookmark(VSBookmark.HOME_BOOKMARK, IdentityID.getIdentityIDFromKey(principal.getName()),
                                            rvs, (XPrincipal) principal, event, linkUri,
                                            rvs.getID(), commandDispatcher);
         }
      }
      catch(MessageException ex) {
         MessageCommand command = new MessageCommand();
         command.setMessage(ex.getMessage());
         command.setType(MessageCommand.Type.ERROR);
         commandDispatcher.sendCommand(command);
      }
   }

   /**
    * Edit the properties of bookmark.
    */
   @MessageMapping("/vs/bookmark/edit-bookmark")
   public void editBookmark(@Payload VSEditBookmarkEvent value, Principal principal,
                            CommandDispatcher commandDispatcher, @LinkUri String linkUri)
      throws Exception
   {
      RuntimeViewsheet rvs =
         service.getRuntimeViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      VSBookmarkInfoModel vsBookmarkInfoModel = value.vsBookmarkInfoModel();
      String name = Tool.defaultIfNull(vsBookmarkInfoModel.name(), "");
      IdentityID owner = Tool.defaultIfNull(vsBookmarkInfoModel.owner(), new IdentityID("",""));
      int type = vsBookmarkInfoModel.type();
      boolean readOnly = vsBookmarkInfoModel.readOnly();
      String oldName = value.oldName();

      if(!Tool.equals(name, oldName) && rvs.containsBookmark(name, owner)) {
         throw new RuntimeException(Catalog.getCatalog().getString(
            "viewer.viewsheet.bookmark.duplicateWarning"));
      }

      IdentityID user = IdentityID.getIdentityIDFromKey(principal.getName());
      boolean bookmarkRenamedInSchedule = !Tool.equals(name, oldName) &&
         bookmarkService.isBookmarkUsedInSchedule(oldName, user);

      if(bookmarkRenamedInSchedule) {
         if(!value.confirmed()) {
            MessageCommand messageCommand = new MessageCommand();
            messageCommand.setMessage(catalog.getString("viewer.viewsheet.bookmarkUsedInSchedule", oldName));
            messageCommand.setType(MessageCommand.Type.CONFIRM);
            messageCommand.addEvent("/events/vs/bookmark/edit-bookmark", value);
            commandDispatcher.sendCommand(messageCommand);
            return;
         }
         else {
            ScheduleManager manager = ScheduleManager.getScheduleManager();
            manager.bookmarkRenamed(oldName, name, rvs.getEntry().toIdentifier(), user);
         }
      }

      VSBookmarkInfo origBookmarkInfo = new VSBookmarkInfo();
      BeanUtils.copyProperties(rvs.getBookmarkInfo(oldName, owner), origBookmarkInfo);
      rvs.editBookmark(name, oldName, type, readOnly);
      AuditRecordUtils.executeEditBookmarkRecord(rvs, origBookmarkInfo, name, owner);

      if(!Tool.equals(name, oldName)) {
         Cluster.getInstance().sendMessage(new ViewsheetBookmarkChangedEvent(rvs.getEntry()));
      }
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
                                             Principal principal)
      throws Exception
   {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      runtimeId = Tool.byteDecode(runtimeId);
      ViewsheetService vsService = viewsheetService;
      RuntimeViewsheet rvs = vsService.getViewsheet(runtimeId, principal);
      List<VSBookmarkInfoModel> allBookmarks = new ArrayList<>();

      VSBookmark.DefaultBookmark defaultBookmark = rvs.getDefaultBookmark();
      VSBookmarkInfo currentBookmark = rvs.getOpenedBookmark();
      // when security is not enabled, all users share the anonymous bookmarks
      VSBookmarkInfo[] bookmarks =
         VSUtil.getBookmarks(rvs.getEntry(), pId);
      boolean security = securityEngine.isSecurityEnabled();

      boolean readOnly = !SUtil.isDefaultVSGloballyVisible(principal) ||
                         !Tool.equals(rvs.getEntry() == null ? "" : rvs.getEntry().getOrgID(),Organization.getDefaultOrganizationID());

      for(VSBookmarkInfo vsBookmarkInfo : bookmarks) {
         if(vsBookmarkInfo.getName().equals(VSBookmark.HOME_BOOKMARK)) {
            allBookmarks.add(VSBookmarkInfoModel.builder()
                                .name(vsBookmarkInfo.getName())
                                .type(VSBookmarkInfo.ALLSHARE)
                                .owner(IdentityID.getIdentityIDFromKey(principal.getName()))
                                .readOnly(readOnly)
                                .label(catalog.getString(vsBookmarkInfo.getName()))
                                .defaultBookmark(defaultBookmark != null &&
                                                    defaultBookmark.getName().equals(vsBookmarkInfo.getName()) &&
                                                    defaultBookmark.getOwner().equals(vsBookmarkInfo.getOwner()))
                                .currentBookmark(currentBookmark == null ||
                                                    (currentBookmark.getName().equals(vsBookmarkInfo.getName()) &&
                                                       currentBookmark.getOwner().equals(vsBookmarkInfo.getOwner())))
                                .build()
            );
         }
         else {
            IdentityID owner = vsBookmarkInfo.getOwner();
            String ownerName = security ? VSUtil.getUserAlias(owner): "";

            allBookmarks.add(
               VSBookmarkInfoModel.builder()
                  .name(vsBookmarkInfo.getName())
                  .owner(vsBookmarkInfo.getOwner())
                  .type(vsBookmarkInfo.getType())
                  .readOnly(vsBookmarkInfo.isReadOnly())
                  .label(StringUtils.isEmpty(ownerName) || Tool.equals(vsBookmarkInfo.getOwner().convertToKey(), principal.getName()) ?
                            vsBookmarkInfo.getName() : (vsBookmarkInfo.getName() + "(" + ownerName + ")"))
                  .defaultBookmark(defaultBookmark != null &&
                                      defaultBookmark.getName().equals(vsBookmarkInfo.getName()) &&
                                      defaultBookmark.getOwner().equals(vsBookmarkInfo.getOwner()))
                  .currentBookmark(currentBookmark != null &&
                                      currentBookmark.getName().equals(vsBookmarkInfo.getName()) &&
                                      currentBookmark.getOwner().equals(vsBookmarkInfo.getOwner()))
                  .tooltip(VSUtil.getBookmarkTooltip(vsBookmarkInfo))
                  .build()
            );
         }
      }

      return allBookmarks.toArray(new VSBookmarkInfoModel[0]);
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
      RuntimeViewsheet rvs =
         service.getRuntimeViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      VSBookmarkInfoModel vsBookmarkInfoModel = value.vsBookmarkInfoModel();
      assert vsBookmarkInfoModel != null;
      String name = Tool.defaultIfNull(vsBookmarkInfoModel.name(),"");
      IdentityID owner = Tool.defaultIfNull(vsBookmarkInfoModel.owner(), new IdentityID("",""));

      bookmarkService.processBookmark(
         name, owner, rvs, (XPrincipal) principal, value, linkUri, rvs.getID(), commandDispatcher);
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
      RuntimeViewsheet rvs =
         service.getRuntimeViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet vs = rvs.getViewsheet();
      VSBookmarkInfoModel vsBookmarkInfoModel = value.vsBookmarkInfoModel();
      String name = Tool.defaultIfNull(vsBookmarkInfoModel.name(), "");
      IdentityID owner = Tool.defaultIfNull(vsBookmarkInfoModel.owner(), new IdentityID("",""));

      if(vs.getRuntimeEntry().getScope() == AssetRepository.TEMPORARY_SCOPE) {
         MessageCommand command = new MessageCommand();
         command.setMessage(catalog.getString("common.viewsheet.saveViewsheetDependence"));
         command.setType(MessageCommand.Type.ERROR);
         commandDispatcher.sendCommand(command);
         return;
      }

      rvs.setDefaultBookmark(
         new VSBookmark().new DefaultBookmark(name, owner));
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
      IdentityID ownerId = IdentityID.getIdentityIDFromKey(owner);
      runtimeId = Tool.byteDecode(runtimeId);
      ViewsheetService vsService = viewsheetService;
      RuntimeViewsheet rvs = vsService.getViewsheet(runtimeId, principal);
      return rvs.bookmarkUpdated(name, ownerId) ? Boolean.TRUE.toString() : Boolean.FALSE.toString();
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
      IdentityID ownerId = IdentityID.getIdentityIDFromKey(owner);
      runtimeId = Tool.byteDecode(runtimeId);
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      return rvs.checkBookmark(name, ownerId) ? Boolean.FALSE.toString() : Boolean.TRUE.toString();
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
      RuntimeViewsheet rvs = ViewsheetEngine.getViewsheetEngine().getViewsheet(assetID, principal);
      return SUtil.isDefaultVSGloballyVisible(principal) && !Tool.equals(((XPrincipal)principal).getOrgId(),rvs.getEntry().getOrgID()) &&
         Tool.equals(rvs.getEntry().getOrgID(), Organization.getDefaultOrganizationID());

   }



   private final VSObjectService service;
   private final VSBookmarkService bookmarkService;
   private final ViewsheetService viewsheetService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final SecurityEngine securityEngine;
   private static final Catalog catalog = Catalog.getCatalog();
}
