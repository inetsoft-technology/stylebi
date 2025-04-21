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

import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.AnnotationVSUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.audit.*;
import inetsoft.util.log.LogUtil;
import inetsoft.web.viewsheet.command.AnnotationChangedCommand;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.sql.Date;
import java.util.*;

@Service
public class VSBookmarkService {
   @Autowired
   public VSBookmarkService(VSObjectService service) {
      this.service = service;
   }

   public void addBookmark(RuntimeViewsheet rvs, String bookmarkName, int type,
                           boolean readOnly, boolean confirmed, Principal principal,
                           CommandDispatcher commandDispatcher, VSEditBookmarkEvent value,
                           String bookmarkActionType, VSBookmarkInfo origBookmarkInfo)
      throws Exception {
      MessageCommand messageCommand = addBookmarkToViewSheet(rvs, bookmarkName, type, readOnly,
                                                             confirmed, principal);
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
            rvs.getBookmarkInfo(bookmarkName, pId), bookmarkActionType);
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
      Viewsheet vs = rvs.getViewsheet();
      MessageCommand messageCommand = checkAddBookmark(rvs, bookmarkName, confirmed, principal);

      if(messageCommand.getType() != MessageCommand.Type.OK) {
         return messageCommand;
      }

      vs.getRuntimeEntry().setProperty("keepAnnonVis", "true");
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
      SecurityEngine engine = SecurityEngine.getSecurity();
      MessageCommand messageCommand = new MessageCommand();

      boolean isGlobalVSPermDenied = SUtil.isDefaultVSGloballyVisible(principal) &&
                                     !Tool.equals(rvs.getEntry() == null ? "" :
                                     rvs.getEntry().getOrgID(),((XPrincipal)principal).getOrgId());

      if(!engine.checkPermission(principal, ResourceType.VIEWSHEET_ACTION, "Bookmark",
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
      else if(!confirmed && rvs.containsBookmark(bookmarkName, IdentityID.getIdentityIDFromKey(principal.getName()))) {
         messageCommand.setMessage(
                 catalog.getString("viewer.viewsheet.bookmark.replaceWarning", bookmarkName));
         messageCommand.setType(MessageCommand.Type.CONFIRM);
      }

      return messageCommand;
   }

   public void processBookmark(String name, IdentityID owner, RuntimeViewsheet rvs, XPrincipal user,
                               inetsoft.web.viewsheet.event.OpenViewsheetEvent event,
                               String url, String vsId,  CommandDispatcher dispatcher)
      throws Exception
   {
      ChangedAssemblyList clist = service.createList(true, event, dispatcher, rvs, url);
      processBookmark(
         name, owner, rvs, user, url, vsId, event.getWidth(), event.getHeight(), event.isMobile(),
         event.getUserAgent(), clist, false, dispatcher);
   }

   public void processBookmark(String name, IdentityID owner, RuntimeViewsheet rvs, XPrincipal user,
                               VSEditBookmarkEvent event, String url, String vsId,
                               CommandDispatcher dispatcher)
      throws Exception
   {
      ChangedAssemblyList clist = service.createList(true, dispatcher, rvs, url);
      processBookmark(
         name, owner, rvs, user, url, vsId, event.windowWidth(),
         event.windowHeight(), event.mobile(),
         event.userAgent(), clist, false, dispatcher);
   }

   public void processBookmark(String name, IdentityID owner, RuntimeViewsheet rvs, XPrincipal user,
                               VSDeletedMatchedBookmarksEvent event, String url, String vsId,
                               CommandDispatcher dispatcher)
           throws Exception
   {
      ChangedAssemblyList clist = service.createList(true, dispatcher, rvs, url);
      processBookmark(
              name, owner, rvs, user, url, vsId, event.windowWidth(),
              event.windowHeight(), event.mobile(),
              event.userAgent(), clist, false, dispatcher);
   }

   public void processBookmark(String name, IdentityID owner, RuntimeViewsheet rvs, XPrincipal user,
                               String url, String vsId, Integer width0, Integer height0,
                               Boolean mobile0, String userAgent, ChangedAssemblyList clist,
                               boolean annotationChanged, CommandDispatcher dispatcher)
      throws Exception
   {
      int width = width0 != null ? width0 : 0;
      int height = height0 != null ? height0 : 0;
      boolean mobile = mobile0 != null && mobile0;
      Assembly[] oldArr = getAssemblies(rvs.getViewsheet());
      boolean result = rvs.gotoBookmark(name, owner);

      if(!result) {
         // reload vs bookmark from the storage and try again
         rvs.reloadVSBookmark(owner);
         result = rvs.gotoBookmark(name, owner);

         if(!result) {
            throw new RuntimeException(catalog.getString(
               "viewer.viewsheet.bookmark.notFound", name));
         }
      }

      // to init vs to apply the shared filter when switch to home bookmark,
      // because the home bookmark created before initViewsheet in the runtime viewsheet.
      if(VSBookmark.HOME_BOOKMARK.equals(name)) {
         rvs.initViewsheet(rvs.getViewsheet(), false);
      }

      // log viewsheet excecution
      String userSessionID = user == null ?
         XSessionService.createSessionID(XSessionService.USER, null) :
         user.getSessionID();
      AssetEntry entry = rvs.getEntry();
      String objectName = entry.getDescription();
      LogUtil.PerformanceLogEntry logEntry = new LogUtil.PerformanceLogEntry(objectName);
      String execSessionID = XSessionService.createSessionID(
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

      //@temp get type and readonly from bookmark info by name and user.
      for(VSBookmarkInfo bminfo : rvs.getBookmarks(bookmarkUser)) {
         if(bminfo != null && bookmarkName.equals(bminfo.getName())) {
            info = bminfo;
         }
      }

      if(info == null) {
         return;
      }

      int bookmarkType = info.getType();

      if(!currUser.equals(bookmarkUser)) {
         if(VSBookmarkInfo.PRIVATE == bookmarkType) {
            return;
         }
         else if(VSBookmarkInfo.GROUPSHARE == bookmarkType &&
            !rvs.isSameGroup(bookmarkUser, currUser))
         {
            return;
         }
      }

      processBookmark(bookmarkName, bookmarkUser, rvs, (XPrincipal) principal,
                      event, linkUri, id, dispatcher);
   }

   public final void removeAssemblyAndRefreshViewSheet(CommandDispatcher dispatcher,
                                                       RuntimeViewsheet rvs, String uri,
                                                       Assembly[] oldAss,
                                                       String id, int width, int height,
                                                       boolean mobile, String userAgent,
                                                       ChangedAssemblyList clist)
      throws Exception
   {
      removeUselessAssemblies(rvs, oldAss, dispatcher);
      service.refreshViewsheet(
         rvs, id, uri, width, height, mobile, userAgent, dispatcher, false, true, true, clist,
         null, null);
   }

   /**
    * Get all assemblies in current viewsheet.
    */
   public Assembly[] getAssemblies(Viewsheet vs) {
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
   public void removeUselessAssemblies(RuntimeViewsheet rvs, Assembly[] oldAss,
                                               CommandDispatcher dispatcher)
   {
      Assembly[] newAss = getAssemblies(rvs.getViewsheet());
      AnnotationVSUtil.removeUselessAssemblies(oldAss, newAss, dispatcher);
   }

   /**
    * Check whether the bookmark is used in schedule.
    */
   public boolean isBookmarkUsedInSchedule(AssetEntry vs, String bookmark, IdentityID user) {
      if(vs == null) {
         return false;
      }

      ScheduleManager manager = ScheduleManager.getScheduleManager();
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
   public List<ScheduleTask> getScheduledTasksUsingBookmark(String bookmark, IdentityID user, String vname) {
      ScheduleManager manager = ScheduleManager.getScheduleManager();
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

   private final VSObjectService service;

   private static final Catalog catalog = Catalog.getCatalog();
}
