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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.ViewsheetBookmarkChangedEvent;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.AnnotationVSUtil;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;
import inetsoft.util.audit.*;
import inetsoft.util.log.LogUtil;
import inetsoft.web.viewsheet.command.AnnotationChangedCommand;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.*;
import inetsoft.web.viewsheet.model.RemoveAnnotationsCondition;
import inetsoft.web.viewsheet.model.VSBookmarkInfoModel;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.sql.Date;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

@Service
@ClusterProxy
public class VSBookmarkService implements ApplicationListener<ProcessBookmarkEvent> {
   @Autowired
   public VSBookmarkService(VSObjectService vsObjectService,
                            ViewsheetService viewsheetService,
                            SecurityEngine securityEngine,
                            ScheduleManager scheduleManager,
                            Cluster cluster, XSessionService sessionService)
   {
      this.vsObjectService = vsObjectService;
      this.viewsheetService = viewsheetService;
      this.securityEngine = securityEngine;
      this.scheduleManager = scheduleManager;
      this.cluster = cluster;
      this.sessionService = sessionService;
   }

   @Override
   public void onApplicationEvent(ProcessBookmarkEvent event) {
      try {
         processBookmark(
            event.getId(), event.getRvs(), event.getLinkUri(), event.getPrincipal(),
            event.getBookmarkName(), event.getBookmarkUser(), event.getEvent(),
            event.getDispatcher());
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to process bookmark", e);
      }
   }

   /**
    * The kind of access a bookmark request needs on the bookmark it names.
    */
   enum BookmarkAccess {
      /** Load or reference the bookmark: own, or shared with the caller. */
      READ,
      /** Overwrite the bookmark: own, or shared with the caller and not read-only. */
      WRITE,
      /** Delete or edit the bookmark: own only. */
      OWNER
   }

   private static IdentityID getCaller(Principal principal) {
      return principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
   }

   /**
    * Get the effective bookmark owner of a request. An empty owner (older clients) means the
    * caller.
    */
   static IdentityID resolveBookmarkOwner(IdentityID owner, IdentityID caller) {
      return owner == null || Tool.isEmptyString(owner.name) ? caller : owner;
   }

   /**
    * Decide whether a caller may access a bookmark of the given owner. The owner of a bookmark
    * event is supplied by the client, so it must always be validated against the caller.
    *
    * @param caller    the principal that sent the request.
    * @param owner     the owner named in the request.
    * @param info      the bookmark as visible to the caller, or null if the caller can not see
    *                  a bookmark of that name and owner.
    * @param sameGroup tells whether caller and owner share a group.
    * @param access    the access needed.
    */
   static boolean isBookmarkAccessAllowed(IdentityID caller, IdentityID owner,
                                          VSBookmarkInfo info, BooleanSupplier sameGroup,
                                          BookmarkAccess access)
   {
      if(caller == null || owner == null) {
         return false;
      }

      // IdentityID.equals compares both the name and the organization
      if(caller.equals(owner)) {
         return true;
      }

      if(access == BookmarkAccess.OWNER || info == null) {
         return false;
      }

      int type = info.getType();

      if(type != VSBookmarkInfo.ALLSHARE &&
         !(type == VSBookmarkInfo.GROUPSHARE && sameGroup.getAsBoolean()))
      {
         return false;
      }

      return access != BookmarkAccess.WRITE || !info.isReadOnly();
   }

   /**
    * Find the bookmark of the given name and owner among the bookmarks visible to the caller.
    * The same name can appear for several owners, so the owner must be compared too.
    */
   private static VSBookmarkInfo findVisibleBookmark(RuntimeViewsheet rvs, String name,
                                                     IdentityID owner, IdentityID caller)
   {
      for(VSBookmarkInfo bminfo : rvs.getBookmarks(caller)) {
         if(bminfo != null && Tool.equals(name, bminfo.getName()) &&
            owner.equals(bminfo.getOwner()))
         {
            return bminfo;
         }
      }

      return null;
   }

   /**
    * Tell whether the caller may access the bookmark of the given name and owner, without
    * reporting anything to the client.
    */
   private boolean isBookmarkAllowed(RuntimeViewsheet rvs, String name, IdentityID owner,
                                     IdentityID caller, BookmarkAccess access)
   {
      if(caller != null && owner != null && !owner.equals(caller) &&
         !securityEngine.isSecurityEnabled() && Tool.equals(owner.name, caller.name))
      {
         // security is off: every user is the shared anonymous user, not a boundary
         return true;
      }

      VSBookmarkInfo info = null;

      if(caller != null && owner != null && !owner.equals(caller) &&
         access != BookmarkAccess.OWNER)
      {
         info = findVisibleBookmark(rvs, name, owner, caller);
      }

      return isBookmarkAccessAllowed(
         caller, owner, info, () -> rvs.isSameGroup(owner, caller), access);
   }

   private boolean checkBookmarkAccess(RuntimeViewsheet rvs, String name, IdentityID owner,
                                       IdentityID caller, BookmarkAccess access,
                                       CommandDispatcher commandDispatcher)
   {
      boolean allowed = isBookmarkAllowed(rvs, name, owner, caller, access);

      if(!allowed) {
         LOG.warn("Rejected {} access to bookmark '{}' of user {} for user {}", access, name,
                  owner, caller);
         MessageCommand command = new MessageCommand();
         command.setMessage(catalog.getString("common.noPermission"));
         command.setType(MessageCommand.Type.ERROR);
         commandDispatcher.sendCommand(command);
      }

      return allowed;
   }

   @ClusterWriteMethod
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void saveBookmark(@ClusterProxyKey String runtimeId, VSEditBookmarkEvent value,
                            Principal principal, CommandDispatcher commandDispatcher, String linkUri) throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = vsObjectService.getRuntimeViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();
      VSBookmarkInfoModel vsBookmarkInfoModel = value.vsBookmarkInfoModel();
      String name = Tool.defaultIfNull(vsBookmarkInfoModel.name(), "");
      IdentityID caller = getCaller(principal);
      IdentityID ownerId = resolveBookmarkOwner(vsBookmarkInfoModel.owner(), caller);
      boolean readOnly = vsBookmarkInfoModel.readOnly();
      int type = vsBookmarkInfoModel.type();
      String originalAction = value.instruction();
      AssetEntry vsEntry = vs.getRuntimeEntry();

      // the owner comes from the request, never trust it (Bug #79436)
      if(!checkBookmarkAccess(rvs, name, ownerId, caller, BookmarkAccess.WRITE,
                              commandDispatcher))
      {
         return null;
      }

      if(!ownerId.equals(caller)) {
         // saving over another user's shared bookmark replaces its content only; its sharing
         // type and read-only flag belong to the owner (Bug #79737)
         VSBookmarkInfo stored = rvs.getBookmarkInfo(name, ownerId);

         if(stored != null) {
            type = stored.getType();
            readOnly = stored.isReadOnly();
         }
      }

      if(vsEntry == null) {
         return null;
      }

      if(vsEntry != null && vsEntry.getScope() == AssetRepository.TEMPORARY_SCOPE) {
         MessageCommand command = new MessageCommand();
         command.setMessage(catalog.getString("common.viewsheet.saveViewsheetDependence"));
         command.setType(MessageCommand.Type.ERROR);
         commandDispatcher.sendCommand(command);
         return null;
      }

      if(VSBookmark.HOME_BOOKMARK.equals(name)) {
         String id = rvs.getOriginalID();
         RuntimeViewsheet rvs0 = id == null ? rvs : engine.getViewsheet(id, principal);
         Viewsheet vs0 = rvs0.getViewsheet();
         Viewsheet ovs = vs.clone();
         AssetEntry vsEntry0 = rvs0.getEntry();

         // could be disposed
         if(vs0 == null || vsEntry0 == null) {
            return null;
         }

         vsEntry.setProperty("noAnnotation", "true");
         // use the preview runtime viewsheet to update the edit viewsheet
         VSEventUtil.updateViewsheet(ovs, true, vs0);
         vsEntry.setProperty("noAnnotation", null);
         vsEntry0.setProperty("homeBookmarkSaved", "true");
         vsEntry0.setProperty("bookmarkIndex", null);

         try {
            engine.setViewsheet(vs0, vsEntry0, principal, true, true);
            saveEmbeddedViewsheets(vs0, engine, principal);
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
            vsObjectService.refreshViewsheet(rvs0, linkUri, new CommandDispatcher(commandDispatcher, value.clientId()));
         }
         else if(rvs.isViewer()) {
            if(vs.getViewsheetInfo().isScaleToScreen()) {
               Object scaleSize = rvs.getProperty("viewsheet.appliedScale");

               if(scaleSize instanceof Dimension && ((Dimension) scaleSize).width > 0 &&
                  ((Dimension) scaleSize).height > 0)
               {
                  vsObjectService.refreshViewsheet(rvs, ((Dimension) scaleSize).width,
                                           ((Dimension) scaleSize).height, linkUri, commandDispatcher);
               }
            }
         }
         else {
            Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

            if(box.isPresent()) {
               box.get().updateAssemblies();
            }
         }
      }

      if(!value.confirmed()) {
         if(rvs.bookmarkUpdated(name, ownerId)) {
            MessageCommand messageCommand = new MessageCommand();
            messageCommand.setMessage("The bookmark has been changed by others but front end did not check whether to override it.");
            messageCommand.setType(MessageCommand.Type.ERROR);
            commandDispatcher.sendCommand(messageCommand);
            return null;
         }
         else if(!rvs.checkBookmark(name, ownerId)) {
            MessageCommand messageCommand = new MessageCommand();
            messageCommand.setMessage("The bookmark has been deleted by owner but front end did not check whether to readd it.");
            messageCommand.setType(MessageCommand.Type.ERROR);
            commandDispatcher.sendCommand(messageCommand);
            return null;
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
               return null;
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
         //add the bookmark
         addBookmark(rvs, name, type, readOnly,
                                     !"add".equals(originalAction) || value.confirmed(), owner,
                                     principal, commandDispatcher, value, BookmarkRecord.ACTION_TYPE_MODIFY,
                                     origBookmarkInfo);
      }
      else if("readd".equals(action)) {
         //The bookmark has been deleted by other user, add it back.
         rvs.updateVSBookmark();
         addBookmark(rvs, name, type, readOnly,
                                     !"add".equals(originalAction) || value.confirmed(), owner,
                                     principal, commandDispatcher, value, BookmarkRecord.ACTION_TYPE_MODIFY,
                                     origBookmarkInfo);
      }

      return null;
   }

   @ClusterWriteMethod
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void addBookmark(@ClusterProxyKey String runtimeId, VSEditBookmarkEvent value,
                           Principal principal, CommandDispatcher commandDispatcher, String linkUri) throws Exception
   {
      RuntimeViewsheet rvs = vsObjectService.getRuntimeViewsheet(runtimeId, principal);
      String originalAction = value.instruction();
      VSBookmarkInfoModel vsBookmarkInfoModel = value.vsBookmarkInfoModel();
      String name = Tool.defaultIfNull(vsBookmarkInfoModel.name(),"");
      int type = vsBookmarkInfoModel.type();
      boolean readOnly = vsBookmarkInfoModel.readOnly();

      addBookmark(rvs, name, type, readOnly,
                                  !"add".equals(originalAction) || value.confirmed(), principal,
                                  commandDispatcher, value, BookmarkRecord.ACTION_TYPE_CREATE, null);

      return null;
   }

   @ClusterWriteMethod
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void deleteBookmark(@ClusterProxyKey String runtimeId, VSEditBookmarkEvent value,
                              Principal principal, CommandDispatcher commandDispatcher, String linkUri) throws Exception
   {
      RuntimeViewsheet rvs = vsObjectService.getRuntimeViewsheet(runtimeId, principal);
      VSBookmarkInfoModel vsBookmarkInfoModel = value.vsBookmarkInfoModel();
      assert vsBookmarkInfoModel != null;
      String name = Tool.defaultIfNull(vsBookmarkInfoModel.name(), "");
      IdentityID user = getCaller(principal);
      IdentityID owner = resolveBookmarkOwner(vsBookmarkInfoModel.owner(), user);

      // only the owner can delete a bookmark, and it must be validated before the scheduled
      // task check below, which is keyed by the caller (Bug #79436)
      if(!checkBookmarkAccess(rvs, name, owner, user, BookmarkAccess.OWNER, commandDispatcher)) {
         return null;
      }

      if(!value.confirmed()) {
         final List<ScheduleTask> tasksUsingBookmark =
            getScheduledTasksUsingBookmark(name, user, rvs.getEntry().toIdentifier());

         if(!tasksUsingBookmark.isEmpty()) {
            MessageCommand messageCommand = new MessageCommand();
            final String tasksStr = tasksUsingBookmark.stream()
               .map(scheduleTask -> scheduleTask.toView(true, true))
               .collect(Collectors.joining(", "));
            messageCommand.setMessage(
               catalog.getString("viewer.viewsheet.deleteBookmarkInSchedule", name, tasksStr));
            messageCommand.setType(MessageCommand.Type.ERROR);
            commandDispatcher.sendCommand(messageCommand);
            return null;
         }
      }

      try {
         VSBookmarkInfo currBookmark = rvs.getOpenedBookmark();
         VSBookmarkInfo bookmarkInfo = rvs.getBookmarkInfo(name, IdentityID.getIdentityIDFromKey(principal.getName()));
         rvs.removeBookmark(name, owner);
         AuditRecordUtils.executeBookmarkRecord(
            rvs.getViewsheet(), bookmarkInfo, BookmarkRecord.ACTION_TYPE_DELETE, null);

         // After remove bookmark, go to home bookmark.
         if(currBookmark != null && currBookmark.getName().equals(name)) {
            processBookmark(VSBookmark.HOME_BOOKMARK, IdentityID.getIdentityIDFromKey(principal.getName()),
                                            rvs, (XPrincipal) principal, value, linkUri,
                                            rvs.getID(), commandDispatcher);
         }

         if(currBookmark != null) {
            cluster.sendMessage(new ViewsheetBookmarkChangedEvent(rvs,
                                                                                true, currBookmark.getName()));
         }
      }
      catch(MessageException ex) {
         MessageCommand command = new MessageCommand();
         command.setMessage(ex.getMessage());
         command.setType(MessageCommand.Type.ERROR);
         commandDispatcher.sendCommand(command);
      }

      return null;
   }

   @ClusterWriteMethod
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void deleteMatchedBookmarks(@ClusterProxyKey String runtimeId, VSDeletedMatchedBookmarksEvent event,
                                      Principal principal, CommandDispatcher commandDispatcher, @LinkUri String linkUri) throws Exception
   {
      RuntimeViewsheet rvs = vsObjectService.getRuntimeViewsheet(runtimeId, principal);
      IdentityID user = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      RemoveAnnotationsCondition condition = event.condition();
      List<VSBookmarkInfo> bookmarks = rvs.getBookmarks(user);
      java.util.Date filterTime = Tool.parseDate(condition.getFilterTime());

      if(filterTime == null) {
         return null;
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
               getScheduledTasksUsingBookmark(bookmark.getName(), user, rvs.getEntry().toIdentifier());

            if(!tasksUsingBookmark.isEmpty()) {
               final String tasksStr = tasksUsingBookmark.stream()
                  .map(scheduleTask -> scheduleTask.toView(true, true))
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
         return null;
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
            processBookmark(VSBookmark.HOME_BOOKMARK, IdentityID.getIdentityIDFromKey(principal.getName()),
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

      return null;
   }

   @ClusterWriteMethod
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void editBookmark(@ClusterProxyKey String runtimeId, VSEditBookmarkEvent value, Principal principal,
                            CommandDispatcher commandDispatcher, @LinkUri String linkUri) throws Exception
   {
      RuntimeViewsheet rvs = vsObjectService.getRuntimeViewsheet(runtimeId, principal);
      VSBookmarkInfoModel vsBookmarkInfoModel = value.vsBookmarkInfoModel();
      String name = Tool.defaultIfNull(vsBookmarkInfoModel.name(), "");
      IdentityID caller = getCaller(principal);
      IdentityID owner = resolveBookmarkOwner(vsBookmarkInfoModel.owner(), caller);
      int type = vsBookmarkInfoModel.type();
      boolean readOnly = vsBookmarkInfoModel.readOnly();
      String oldName = value.oldName();

      // only the owner can edit a bookmark (Bug #79436)
      if(!checkBookmarkAccess(rvs, oldName, owner, caller, BookmarkAccess.OWNER,
                              commandDispatcher))
      {
         return null;
      }

      if(!Tool.equals(name, oldName) && rvs.containsBookmark(name, owner)) {
         throw new RuntimeException(Catalog.getCatalog().getString(
            "viewer.viewsheet.bookmark.duplicateWarning"));
      }

      IdentityID user = IdentityID.getIdentityIDFromKey(principal.getName());
      boolean bookmarkRenamedInSchedule = !Tool.equals(name, oldName) &&
         isBookmarkUsedInSchedule(rvs.getEntry(), oldName, user);

      if(bookmarkRenamedInSchedule) {
         if(!value.confirmed()) {
            MessageCommand messageCommand = new MessageCommand();
            messageCommand.setMessage(catalog.getString("viewer.viewsheet.bookmarkUsedInSchedule", oldName));
            messageCommand.setType(MessageCommand.Type.CONFIRM);
            messageCommand.addEvent("/events/vs/bookmark/edit-bookmark", value);
            commandDispatcher.sendCommand(messageCommand);
            return null;
         }
         else {
            ScheduleManager manager = scheduleManager;
            manager.bookmarkRenamed(oldName, name, rvs.getEntry().toIdentifier(), user);
         }
      }

      VSBookmarkInfo origBookmarkInfo = new VSBookmarkInfo();
      BeanUtils.copyProperties(rvs.getBookmarkInfo(oldName, owner), origBookmarkInfo);
      rvs.editBookmark(name, oldName, type, readOnly);
      AuditRecordUtils.executeEditBookmarkRecord(rvs, origBookmarkInfo, name, owner);

      if(!Tool.equals(name, oldName)) {
         cluster.sendMessage(new ViewsheetBookmarkChangedEvent(rvs.getEntry()));
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public VSBookmarkInfoModel[] getBookmarks(@ClusterProxyKey String runtimeId, String localTimeZone,
                                             Principal principal) throws Exception
   {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
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
            allBookmarks.add(0, VSBookmarkInfoModel.builder()
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
                  .tooltip(VSUtil.getBookmarkTooltip(vsBookmarkInfo, localTimeZone))
                  .build()
            );
         }
      }

      return allBookmarks.toArray(new VSBookmarkInfoModel[0]);
   }

   @ClusterWriteMethod
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void gotoBookmark(@ClusterProxyKey String runtimeId, VSEditBookmarkEvent value,
                            Principal principal, CommandDispatcher commandDispatcher, String linkUri) throws Exception
   {
      RuntimeViewsheet rvs = vsObjectService.getRuntimeViewsheet(runtimeId, principal);
      VSBookmarkInfoModel vsBookmarkInfoModel = value.vsBookmarkInfoModel();
      assert vsBookmarkInfoModel != null;
      String name = Tool.defaultIfNull(vsBookmarkInfoModel.name(),"");
      IdentityID caller = getCaller(principal);
      IdentityID owner = resolveBookmarkOwner(vsBookmarkInfoModel.owner(), caller);

      if(!checkBookmarkAccess(rvs, name, owner, caller, BookmarkAccess.READ, commandDispatcher)) {
         return null;
      }

      processBookmark(
         name, owner, rvs, (XPrincipal) principal, value, linkUri, rvs.getID(), commandDispatcher);

      return null;
   }

   @ClusterWriteMethod
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setDefaultBookmark(@ClusterProxyKey String runtimeId, VSEditBookmarkEvent value,
                                  Principal principal, CommandDispatcher commandDispatcher, String linkUri) throws Exception
   {
      RuntimeViewsheet rvs = vsObjectService.getRuntimeViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();
      VSBookmarkInfoModel vsBookmarkInfoModel = value.vsBookmarkInfoModel();
      String name = Tool.defaultIfNull(vsBookmarkInfoModel.name(), "");
      IdentityID caller = getCaller(principal);
      IdentityID owner = resolveBookmarkOwner(vsBookmarkInfoModel.owner(), caller);

      if(!checkBookmarkAccess(rvs, name, owner, caller, BookmarkAccess.READ, commandDispatcher)) {
         return null;
      }

      if(vs.getRuntimeEntry().getScope() == AssetRepository.TEMPORARY_SCOPE) {
         MessageCommand command = new MessageCommand();
         command.setMessage(catalog.getString("common.viewsheet.saveViewsheetDependence"));
         command.setType(MessageCommand.Type.ERROR);
         commandDispatcher.sendCommand(command);
         return null;
      }

      rvs.setDefaultBookmark(
         new VSBookmark.DefaultBookmark(name, owner));

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public String checkBookmarkChanged(@ClusterProxyKey String runtimeId, String name, String owner,
                                      Principal principal) throws Exception
   {
      IdentityID ownerId = IdentityID.getIdentityIDFromKey(owner);
      ViewsheetService vsService = viewsheetService;
      RuntimeViewsheet rvs = vsService.getViewsheet(runtimeId, principal);

      // a bookmark the caller can not see answers like one that does not exist (Bug #79737)
      if(!isBookmarkAllowed(rvs, name, ownerId, getCaller(principal), BookmarkAccess.READ)) {
         return Boolean.FALSE.toString();
      }

      return rvs.bookmarkUpdated(name, ownerId) ? Boolean.TRUE.toString() : Boolean.FALSE.toString();
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public String checkBookmarkDeleted(@ClusterProxyKey String runtimeId, String name,
                                      String owner, Principal principal) throws Exception
   {
      IdentityID ownerId = IdentityID.getIdentityIDFromKey(owner);
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      // a bookmark the caller can not see answers like one that does not exist (Bug #79737)
      if(!isBookmarkAllowed(rvs, name, ownerId, getCaller(principal), BookmarkAccess.READ)) {
         return Boolean.TRUE.toString();
      }

      return rvs.checkBookmark(name, ownerId) ? Boolean.FALSE.toString() : Boolean.TRUE.toString();
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Boolean isDefaultOrgAsset(@ClusterProxyKey String assetID, Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = ViewsheetEngine.getViewsheetEngine().getViewsheet(assetID, principal);
      return SUtil.isDefaultVSGloballyVisible(principal) && !Tool.equals(((XPrincipal)principal).getOrgId(),rvs.getEntry().getOrgID()) &&
         Tool.equals(rvs.getEntry().getOrgID(), Organization.getDefaultOrganizationID());

   }

   private void addBookmark(RuntimeViewsheet rvs, String bookmarkName, int type,
                           boolean readOnly, boolean confirmed, Principal principal,
                           CommandDispatcher commandDispatcher, VSEditBookmarkEvent value,
                           String bookmarkActionType, VSBookmarkInfo origBookmarkInfo)
      throws Exception {
      addBookmark(rvs, bookmarkName, type, readOnly, confirmed, principal, principal,
                  commandDispatcher, value, bookmarkActionType, origBookmarkInfo);
   }

   private void addBookmark(RuntimeViewsheet rvs, String bookmarkName, int type,
                           boolean readOnly, boolean confirmed, Principal principal,
                           Principal currentPrincipal,
                           CommandDispatcher commandDispatcher, VSEditBookmarkEvent value,
                           String bookmarkActionType, VSBookmarkInfo origBookmarkInfo)
      throws Exception {
      MessageCommand messageCommand = addBookmarkToViewSheet(rvs, bookmarkName, type, readOnly,
                                                             confirmed, principal, currentPrincipal);
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

      if(messageCommand.getType() != MessageCommand.Type.OK) {
         if(messageCommand.getType() == MessageCommand.Type.CONFIRM) {
            messageCommand.addEvent("/events/vs/bookmark/save-bookmark", value);
         }

         commandDispatcher.sendCommand(messageCommand);
         return;
      }
      else {
         if(origBookmarkInfo != null && origBookmarkInfo.getName() != null &&
            rvs.getBookmarkInfo(bookmarkName, pId) != null)
         {
            rvs.getBookmarkInfo(bookmarkName, pId).setCreateTime(
               origBookmarkInfo.getCreateTime());
         }

         AuditRecordUtils.executeBookmarkRecord(rvs.getViewsheet(),
            rvs.getBookmarkInfo(bookmarkName, pId), bookmarkActionType, null);
         commandDispatcher.sendCommand(AnnotationChangedCommand.of(false));
      }
   }

   /**
    * Adds a bookmark to the viewsheet
    *
    * @param rvs          the Runtime Viewsheet Instance
    * @param bookmarkName the added bookmarks name
    *
    * @return MessageCommand with type OK if succeeded otherwise appropiate message to show
    */
   public MessageCommand addBookmarkToViewSheet(RuntimeViewsheet rvs, String bookmarkName, int type,
                                                boolean readOnly, boolean confirmed,
                                                Principal principal)
      throws Exception
   {
      return addBookmarkToViewSheet(rvs, bookmarkName, type, readOnly, confirmed, principal, principal);
   }

   public MessageCommand addBookmarkToViewSheet(RuntimeViewsheet rvs, String bookmarkName, int type,
                                                boolean readOnly, boolean confirmed,
                                                Principal principal, Principal currentPrincipal)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      MessageCommand messageCommand = checkAddBookmark(rvs, bookmarkName, confirmed, principal, currentPrincipal);

      if(messageCommand.getType() != MessageCommand.Type.OK) {
         return messageCommand;
      }

      vs.getRuntimeEntry().setProperty("keepAnnoVis", "true");
      rvs.addBookmark(bookmarkName, type,
                      principal.getName() == null ? new IdentityID("admin", OrganizationManager.getInstance().getCurrentOrgID()) :
                      IdentityID.getIdentityIDFromKey(principal.getName()), readOnly);
      vs.getRuntimeEntry().setProperty("keepAnnoVis", null);
      return messageCommand;
   }

   public MessageCommand checkAddBookmark(RuntimeViewsheet rvs, String bookmarkName,
                                          boolean confirmed, Principal principal)
      throws SecurityException
   {
      return checkAddBookmark(rvs, bookmarkName, confirmed, principal, principal);
   }

   public MessageCommand checkAddBookmark(RuntimeViewsheet rvs, String bookmarkName,
                                          boolean confirmed, Principal principal,
                                          Principal currentPrincipal)
      throws SecurityException
   {
      SecurityEngine engine = securityEngine;
      MessageCommand messageCommand = new MessageCommand();

      boolean isGlobalVSPermDenied = currentPrincipal instanceof XPrincipal &&
                                     SUtil.isDefaultVSGloballyVisible(currentPrincipal) &&
                                     !Tool.equals(rvs.getEntry() == null ? "" :
                                     rvs.getEntry().getOrgID(), ((XPrincipal) currentPrincipal).getOrgId());

      if(!engine.checkPermission(currentPrincipal, ResourceType.VIEWSHEET_ACTION, "Bookmark",
                                 ResourceAction.READ))
      {
         messageCommand.setMessage(catalog.getString("viewer.viewsheet.security.addbookmark"));
         messageCommand.setType(MessageCommand.Type.WARNING);
         return messageCommand;
      }

      if(isGlobalVSPermDenied) {
         messageCommand.setMessage(catalog.getString("Can't create simple scheduler for default organization"));
         messageCommand.setType(MessageCommand.Type.WARNING);
         return messageCommand;
      }

      Viewsheet vs = rvs.getViewsheet();
      messageCommand.setType(MessageCommand.Type.OK);

      if(vs.getRuntimeEntry().getScope() == AssetRepository.TEMPORARY_SCOPE) {
         messageCommand.setMessage(catalog.getString("common.viewsheet.saveViewsheetDependence"));
         messageCommand.setType(MessageCommand.Type.ERROR);
      }
      // Use principal (bookmark owner), not currentPrincipal — the "replace?" prompt must check
      // whether the owner already has a bookmark by this name, not whether the current user does.
      else if(!confirmed && rvs.containsBookmark(bookmarkName, IdentityID.getIdentityIDFromKey(principal.getName()))) {
         messageCommand.setMessage(
                 catalog.getString("viewer.viewsheet.bookmark.replaceWarning", bookmarkName));
         messageCommand.setType(MessageCommand.Type.CONFIRM);
      }

      return messageCommand;
   }

   private void processBookmark(String name, IdentityID owner, RuntimeViewsheet rvs, XPrincipal user,
                               inetsoft.web.viewsheet.event.OpenViewsheetEvent event,
                               String url, String vsId,  CommandDispatcher dispatcher)
      throws Exception
   {
      ChangedAssemblyList clist = vsObjectService.createList(true, event, dispatcher, rvs, url);
      processBookmark(
         name, owner, rvs, user, url, vsId, event.getWidth(), event.getHeight(), event.isMobile(),
         event.getUserAgent(), clist, false, dispatcher);
   }

   private void processBookmark(String name, IdentityID owner, RuntimeViewsheet rvs, XPrincipal user,
                               VSEditBookmarkEvent event, String url, String vsId,
                               CommandDispatcher dispatcher)
      throws Exception
   {
      ChangedAssemblyList clist = vsObjectService.createList(true, dispatcher, rvs, url);
      processBookmark(
         name, owner, rvs, user, url, vsId, event.windowWidth(),
         event.windowHeight(), event.mobile(),
         event.userAgent(), clist, false, dispatcher);
   }

   private void processBookmark(String name, IdentityID owner, RuntimeViewsheet rvs, XPrincipal user,
                               VSDeletedMatchedBookmarksEvent event, String url, String vsId,
                               CommandDispatcher dispatcher)
           throws Exception
   {
      ChangedAssemblyList clist = vsObjectService.createList(true, dispatcher, rvs, url);
      processBookmark(
              name, owner, rvs, user, url, vsId, event.windowWidth(),
              event.windowHeight(), event.mobile(),
              event.userAgent(), clist, false, dispatcher);
   }

   private void processBookmark(String name, IdentityID owner, RuntimeViewsheet rvs, XPrincipal user,
                               String url, String vsId, Integer width0, Integer height0,
                               Boolean mobile0, String userAgent, ChangedAssemblyList clist,
                               boolean annotationChanged, CommandDispatcher dispatcher)
      throws Exception
   {
      int width = width0 != null ? width0 : 0;
      int height = height0 != null ? height0 : 0;
      boolean mobile = mobile0 != null && mobile0;
      Assembly[] oldArr = getAssemblies(rvs.getViewsheet());
      rvs.resetUserBookmark(owner.convertToKey());
      boolean result = rvs.gotoBookmark(name, owner, user);

      if(!result) {
         // reload vs bookmark from the storage and try again
         rvs.reloadVSBookmark(owner);
         result = rvs.gotoBookmark(name, owner, user);

         if(!result) {
            throw new RuntimeException(catalog.getString(
               "viewer.viewsheet.bookmark.notFound", name));
         }
      }

      // Navigating to HOME resets assemblies to design-time state (the HOME bookmark was
      // created before initViewsheet in the runtime viewsheet) and applies the shared
      // filter. Clear the onInit guard first so processOnInit is allowed to re-run on the
      // following refreshViewsheet(), restoring any rValues set by onInit (e.g. bottomTabs).
      // Note: clearInit affects the whole sandbox — every onInit script in the viewsheet
      // re-runs, which matches the semantics of "HOME = same as a fresh open".
      if(VSBookmark.HOME_BOOKMARK.equals(name)) {
         rvs.getViewsheetSandbox().ifPresent(box -> box.clearInit());
         rvs.initViewsheet(rvs.getViewsheet(), false);
      }

      // log viewsheet excecution
      String userSessionID = user == null ?
         sessionService.createSessionID(XSessionService.USER, null) :
         user.getSessionID();
      AssetEntry entry = rvs.getEntry();
      String objectName = entry.getDescription();
      LogUtil.PerformanceLogEntry logEntry = new LogUtil.PerformanceLogEntry(objectName);
      String execSessionID = sessionService.createSessionID(
         XSessionService.EXPORE_VIEW, entry.getName());
      String objectType = ExecutionRecord.OBJECT_TYPE_VIEW;
      String execType = ExecutionRecord.EXEC_TYPE_START;
      Date execTimestamp = new Date(System.currentTimeMillis());
      logEntry.setStartTime(execTimestamp.getTime());

      ExecutionRecord executionRecord = new ExecutionRecord(
         execSessionID, userSessionID, objectName, objectType, execType, execTimestamp,
         ExecutionRecord.EXEC_STATUS_SUCCESS, null);
      Audit.getInstance().auditExecution(executionRecord, user);
      executionRecord = new ExecutionRecord(
         execSessionID, userSessionID, objectName, objectType, ExecutionRecord.EXEC_TYPE_FINISH,
         execTimestamp, ExecutionRecord.EXEC_STATUS_SUCCESS, null);

      // Check structural compatibility between the bookmark state and the current dashboard.
      // Dispatch a non-blocking WARNING toast if assemblies are missing or types have changed.
      try {
         AssetRepository bookmarkRep =
            inetsoft.uql.asset.internal.AssetUtil.getAssetRepository(false);
         VSBookmark vsBookmark = bookmarkRep.getVSBookmark(
            rvs.getEntry(), new XPrincipal(owner));

         if(vsBookmark != null) {
            VSBookmark.BookmarkIncompatibility incompatibility =
               vsBookmark.getIncompatibilities(name, rvs.getViewsheet());

            if(!incompatibility.isEmpty()) {
               StringBuilder msg = new StringBuilder();
               msg.append(catalog.getString(
                  "viewer.viewsheet.bookmark.mayBeOutOfDate", name));

               if(incompatibility.isParseError()) {
                  msg.append(" ").append(
                     catalog.getString("viewer.viewsheet.bookmark.parseError"));
               }
               else {
                  if(!incompatibility.getMissingAssemblies().isEmpty()) {
                     msg.append(" ").append(catalog.getString(
                        "viewer.viewsheet.bookmark.missingComponents",
                        String.join(", ", incompatibility.getMissingAssemblies())));
                  }

                  if(!incompatibility.getTypeChanges().isEmpty()) {
                     msg.append(" ").append(catalog.getString(
                        "viewer.viewsheet.bookmark.typeChanges",
                        String.join(", ", incompatibility.getTypeChanges())));
                  }
               }

               msg.append(" ").append(
                  catalog.getString("viewer.viewsheet.bookmark.considerUpdating"));

               MessageCommand warnCommand = new MessageCommand();
               warnCommand.setMessage(msg.toString());
               warnCommand.setType(MessageCommand.Type.WARNING);
               dispatcher.sendCommand(warnCommand);
            }
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to check bookmark compatibility for bookmark '{}'",
            name, ex);
      }

      try {
         removeAssemblyAndRefreshViewSheet(
            dispatcher, rvs, url, oldArr, vsId, width, height, mobile, userAgent, clist);

         if(annotationChanged) {
            dispatcher.sendCommand(AnnotationChangedCommand.of(false));
         }

         execTimestamp = new Date(System.currentTimeMillis());
         executionRecord.setExecTimestamp(execTimestamp);
         executionRecord.setExecStatus(ExecutionRecord.EXEC_STATUS_SUCCESS);
         executionRecord.setExecError(name);
      }
      catch(Exception e) {
         execTimestamp = new Date(System.currentTimeMillis());
         executionRecord.setExecTimestamp(execTimestamp);
         executionRecord.setExecStatus(
            ExecutionRecord.EXEC_STATUS_FAILURE);
         executionRecord.setExecError(e.getMessage());

         throw e;
      }
      finally {
         Audit.getInstance().auditExecution(executionRecord, user);

         if(executionRecord != null && executionRecord.getExecTimestamp() != null) {
            logEntry.setFinishTime(executionRecord.getExecTimestamp().getTime());
            LogUtil.logPerformance(logEntry);
         }
      }
   }

   public void processBookmark(String id, RuntimeViewsheet rvs, String linkUri,
                               Principal principal, String bookmarkName,
                               IdentityID bookmarkUser, OpenViewsheetEvent event,
                               CommandDispatcher dispatcher)
      throws Exception
   {
      if(principal == null) {
         return;
      }

      IdentityID currUser = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      VSBookmarkInfo info = null;

      if(bookmarkName == null || bookmarkUser == null) {
         return;
      }

      //anonymous should not apply bookmark.
      if(XPrincipal.ANONYMOUS.equals(currUser.name)) {
         return;
      }

      // resolve the bookmark by name AND owner among those visible to the caller; the name
      // alone can match another owner's shared bookmark (Bug #79737)
      info = findVisibleBookmark(rvs, bookmarkName, bookmarkUser, currUser);

      if(info == null ||
         !isBookmarkAccessAllowed(currUser, bookmarkUser, info,
                                  () -> rvs.isSameGroup(bookmarkUser, currUser),
                                  BookmarkAccess.READ))
      {
         return;
      }

      processBookmark(bookmarkName, bookmarkUser, rvs, (XPrincipal) principal,
                      event, linkUri, id, dispatcher);
   }

   private void removeAssemblyAndRefreshViewSheet(CommandDispatcher dispatcher,
                                                       RuntimeViewsheet rvs, String uri,
                                                       Assembly[] oldAss,
                                                       String id, int width, int height,
                                                       boolean mobile, String userAgent,
                                                       ChangedAssemblyList clist)
      throws Exception
   {
      removeUselessAssemblies(rvs, oldAss, dispatcher);
      vsObjectService.refreshViewsheet(
         rvs, id, uri, width, height, mobile, userAgent, dispatcher, false, true, true, clist,
         null, null);
   }

   /**
    * Get all assemblies in current viewsheet.
    */
   private Assembly[] getAssemblies(Viewsheet vs) {
      List<Assembly> assemblies = new ArrayList<>();
      addAssemblies(vs, assemblies);
      return assemblies.toArray(new Assembly[assemblies.size()]);
   }

   private void addAssemblies(Viewsheet vs, List<Assembly> assemblies) {
      Assembly[] all = vs.getAssemblies();

      for(Assembly anAll : all) {
         assemblies.add(anAll);

         if(anAll instanceof Viewsheet) {
            addAssemblies((Viewsheet) anAll, assemblies);
         }
      }
   }

   /**
    * Remove the assemblies that don't exist in the new bookmark
    *
    * @param rvs        the runtime viewsheet
    * @param oldAss     the list of assemblies for the old bookmark
    * @param dispatcher the command dispatcher
    */
   private void removeUselessAssemblies(RuntimeViewsheet rvs, Assembly[] oldAss,
                                               CommandDispatcher dispatcher)
   {
      Assembly[] newAss = getAssemblies(rvs.getViewsheet());
      AnnotationVSUtil.removeUselessAssemblies(oldAss, newAss, dispatcher);
   }

   /**
    * Check whether the bookmark is used in schedule.
    */
   private boolean isBookmarkUsedInSchedule(AssetEntry vs, String bookmark, IdentityID user) {
      if(vs == null) {
         return false;
      }

      ScheduleManager manager = scheduleManager;
      Vector<ScheduleTask> tasks = manager.getScheduleTasks();

      for(int i = 0; i < tasks.size(); i++) {
         ScheduleTask currtask = tasks.elementAt(i);

         for(int j = 0; j < currtask.getActionCount(); j++) {
            if(currtask.getAction(j) instanceof ViewsheetAction) {
               ViewsheetAction vact = (ViewsheetAction) currtask.getAction(j);
               String actionVs = vact.getViewsheet();
               String[] names = vact.getBookmarks();
               IdentityID[] userNames = vact.getBookmarkUsers();

               for(int k = 0; k < names.length; k ++) {
                  String name = names[k];
                  IdentityID userName = userNames[k];

                  if(Tool.equals(actionVs, vs.toIdentifier()) && Tool.equals(name, bookmark) &&
                     Tool.equals(user, userName))
                  {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   /**
    * @return the scheduled tasks that use the bookmark.
    */
   private List<ScheduleTask> getScheduledTasksUsingBookmark(String bookmark, IdentityID user, String vname) {
      ScheduleManager manager = scheduleManager;
      Vector<ScheduleTask> tasks = manager.getScheduleTasks();
      final List<ScheduleTask> matchingTasks = new ArrayList<>();

      for(int i = 0; i < tasks.size(); i++) {
         ScheduleTask currtask = tasks.elementAt(i);

         for(int j = 0; j < currtask.getActionCount(); j++) {
            if(currtask.getAction(j) instanceof ViewsheetAction) {
               ViewsheetAction vact = (ViewsheetAction) currtask.getAction(j);
               String[] names = vact.getBookmarks();
               IdentityID[] userNames = vact.getBookmarkUsers();

               for(int k = 0; k < names.length; k ++) {
                  String name = names[k];
                  IdentityID userName = userNames[k];

                  if(Tool.equals(name, bookmark) && Tool.equals(user, userName) &&
                     Tool.equals(vname, vact.getViewsheetName()))
                  {
                     matchingTasks.add(currtask);
                  }
               }
            }
         }
      }

      return matchingTasks;
   }

   private void saveEmbeddedViewsheets(Viewsheet vs, ViewsheetService engine,
                                       Principal principal)
   {
      for(Assembly assembly : vs.getAssemblies()) {
         if(assembly instanceof Viewsheet embeddedVs) {
            AssetEntry embeddedEntry = embeddedVs.getEntry();

            if(embeddedEntry != null) {
               Viewsheet cloned = embeddedVs.clone();

               if(cloned != null) {
                  // Clear parent so isEmbedded()=false and writeXML persists inner assemblies
                  cloned.setViewsheet(null);

                  try {
                     // updateDependency=false: saving embedded VS runtime state does not change the dependency graph
                     engine.setViewsheet(cloned, embeddedEntry, principal, true, false);
                  }
                  catch(Exception ex) {
                     LOG.warn("Failed to save embedded viewsheet for HOME bookmark: {}", embeddedEntry, ex);
                  }
               }

               // Always recurse so deeply-nested embedded VSes are visited even if cloning this level failed
               saveEmbeddedViewsheets(embeddedVs, engine, principal);
            }
         }
      }
   }

   private final ViewsheetService viewsheetService;
   private final VSObjectService vsObjectService;
   private final SecurityEngine securityEngine;
   private final ScheduleManager scheduleManager;
   private final Cluster cluster;
   private final XSessionService sessionService;
   private static final Catalog catalog = Catalog.getCatalog();
   private static final Logger LOG = LoggerFactory.getLogger(VSBookmarkService.class);
}
