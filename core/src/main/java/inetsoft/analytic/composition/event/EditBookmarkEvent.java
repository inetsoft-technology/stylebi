/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.analytic.composition.event;

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.analytic.composition.ViewsheetEvent;
import inetsoft.analytic.composition.command.GetBookmarksCommand;
import inetsoft.report.composition.*;
import inetsoft.report.composition.command.MessageCommand;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.AnnotationVSUtil;
import inetsoft.util.*;
import inetsoft.util.audit.*;
import inetsoft.util.log.LogUtil;
import inetsoft.web.viewsheet.service.CommandDispatcher;

import java.sql.Date;
import java.util.*;

/**
 * Edit bookmark event.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class EditBookmarkEvent extends ViewsheetEvent {
   /**
    * Constructor.
    */
   public EditBookmarkEvent() {
      super();
      catalog = Catalog.getCatalog();
   }

   /**
    * Constructor.
    * @param name the specified bookmark name.
    * @param action the specified bookmark action type.
    * @param oldName the specified bookmark old name for rename action.
    */
   public EditBookmarkEvent(String action, String name, String oldName,
                            int type, IdentityID owner, boolean readOnly, GetBookmarksEvent event)
   {
      this();
      put("action", action);
      put("name", name);
      put("oldName", oldName);
      put("type", type + "");
      put("owner", owner.convertToKey());
      put("event", event);
      put("readOnly", readOnly + "");
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Edit bookmark");
   }

   /**
    * Check if is undoable/redoable.
    * @return <tt>true</tt> if undoable/redoable.
    */
   @Override
   public boolean isUndoable() {
      return false;
   }

   /**
    * Get the influenced assemblies.
    * @return the influenced assemblies, <tt>null</tt> means all.
    */
   @Override
   public String[] getAssemblies() {
      return new String[0];
   }

   /**
    * Process event.
    */
   @Override
   public void process(RuntimeViewsheet rvs, AssetCommand command) throws Exception {
      String name = (String) get("name");
      String oldName = (String) get("oldName");
      String action;

      if(get("action_bookmark") != null) {
         action = (String) get("action_bookmark");
      }
      else {
         action = (String) get("action");
      }

      String originalAction = action;
      IdentityID owner = IdentityID.getIdentityIDFromKey((String) get("owner")) ;
      int type = Integer.parseInt((String) get("type"));
      boolean readOnly = "true".equals(get("readOnly") + "");
      Viewsheet vsheet = rvs.getViewsheet();

      if(vsheet == null) {
         return;
      }

      if("save".equals(action)) {
         if(vsheet.getRuntimeEntry().getScope() ==
            AssetRepository.TEMPORARY_SCOPE) {
            String error =
               catalog.getString("common.viewsheet.saveViewsheetDependence");
            command.addCommand(new MessageCommand(error, MessageCommand.ERROR));
            return;
         }

         if(VSBookmark.HOME_BOOKMARK.equals(name)) {
            SaveViewsheetEvent sevent = new SaveViewsheetEvent(
               null, null, false, false, null, null);
            sevent.setID(getID());
            sevent.setUser(getUser());
            ViewsheetEngine engine =
               (ViewsheetEngine) ViewsheetEngine.getViewsheetEngine();
            String id = rvs.getOriginalID();
            RuntimeViewsheet rvs0 = id == null ? rvs :
               engine.getViewsheet(id, getUser());
            Viewsheet vs = rvs0.getViewsheet();
            Viewsheet ovs = (Viewsheet) rvs.getViewsheet().clone();
            vsheet.getRuntimeEntry().setProperty("noAnnotation", "true");
            // use the preview runtime viewsheet to update the edit viewsheet
            VSEventUtil.updateViewsheet(ovs, true, vs);
            vsheet.getRuntimeEntry().setProperty("noAnnotation", null);
            sevent.setWorksheetEngine(getWorksheetEngine());
            rvs0.getEntry().setProperty("homeBookmarkSaved", "true");
            rvs0.getEntry().setProperty("bookmarkIndex", null);

            try {
               sevent.process(rvs0, command);
            }
            finally {
               if(rvs.isViewer()) {
                  vsheet.getRuntimeEntry().setProperty("keepAnnoVis", "true");
                  Viewsheet nvs = rvs.getViewsheet();
                  VSEventUtil.updateViewsheet(ovs, true, nvs);
                  vsheet.getRuntimeEntry().setProperty("keepAnnoVis", null);
               }
            }

            if(rvs.isPreview()) {
               if(command.isSuccessful()) {
                  rvs0.setSavePoint(rvs0.getCurrent());
               }

               AssetCommand assetCommand = null;

               if(id != null) {
                  assetCommand = new AssetCommand();
                  assetCommand.setID(id);
               }

               ChangedAssemblyList list = createList(true, this, id == null ?
                  command : assetCommand, rvs0, getLinkURI());
               VSEventUtil.refreshViewsheet(
                  rvs0, this, id, getLinkURI(),
                  id == null ? command : assetCommand, false, true, true, list,
                  null, null);

               if(assetCommand != null) {
                  for(int i = 0; i < assetCommand.getCommandCount(); i++) {
                     command.addCommand(assetCommand.getCommand(i));
                  }
               }
            }
         }

         if(!isConfirmed()) {
            String error = null;
            int level = MessageCommand.OK;

            if(rvs.bookmarkUpdated(name, owner)) {
               error =
                  catalog.getString("viewer.viewsheet.bookmark.updated", name);
               level = MessageCommand.OVERRIDE;
            }
            else if(!rvs.checkBookmark(name, owner)) {
               error =
                  catalog.getString("viewer.viewsheet.bookmark.readd", name);
               level = MessageCommand.CONFIRM;
            }

            if(error != null) {
               MessageCommand msgCmd = new MessageCommand(error, level);
               this.remove("action");
               this.put("action_bookmark", action);
               msgCmd.addEvent(this);
               command.addCommand(msgCmd);

               return;
            }
         }

         String confirmed = (String) get("bookmarkConfirmed");

         if(confirmed != null) {
            if("Override".equals(confirmed)) {
               if(!rvs.bookmarkWritable(name, owner)) {
                  String error =
                     catalog.getString("viewer.viewsheet.bookmark.notWritable");
                  command.addCommand(new MessageCommand(error,
                                                        MessageCommand.ERROR));
                  return;
               }
            }

            action = "Override".equals(confirmed) ? "add" : "refresh";
         }
         else {
            action = "add";
         }

         if("add".equals(action)) {
            rvs.removeBookmark(name, owner, false);
            AuditRecordUtils.executeBookmarkRecord(rvs.getViewsheet(),
               rvs.getBookmarkInfo(name, owner), BookmarkRecord.ACTION_TYPE_MODIFY);
         }
      }

      if("add".equals(action)) {
         if(vsheet.getRuntimeEntry().getScope() ==
            AssetRepository.TEMPORARY_SCOPE) {
            String error =
               catalog.getString("common.viewsheet.saveViewsheetDependence");
            command.addCommand(
               new MessageCommand(error, MessageCommand.ERROR));
            return;
         }

         if("add".equals(originalAction) && !isConfirmed()) {
            if(rvs.containsBookmark(name, owner)) {
               String confirmMsg = catalog.getString(
                  "viewer.viewsheet.bookmark.replaceWarning", name);
               MessageCommand msgCmd =
                  new MessageCommand(confirmMsg, MessageCommand.CONFIRM);
               msgCmd.addEvent(this);
               command.addCommand(msgCmd);

               return;
            }
         }

         vsheet.getRuntimeEntry().setProperty("keepAnnoVis", "true");
         rvs.addBookmark(name, type, owner, readOnly);
         vsheet.getRuntimeEntry().setProperty("keepAnnoVis", null);
         AuditRecordUtils.executeBookmarkRecord(rvs.getViewsheet(),
            rvs.getBookmarkInfo(name, owner), BookmarkRecord.ACTION_TYPE_CREATE);
      }
      else if("goto".equals(action)) {
         processBookmark(name, owner, type, readOnly, rvs,
                         (XPrincipal) getUser(), this, getLinkURI(), getID(),
                         command);
      }
      else if("delete".equals(action)) {
         try {
            VSBookmarkInfo bookmarkInfo = rvs.getBookmarkInfo(name, owner);
            rvs.removeBookmark(name, owner);
            AuditRecordUtils.executeBookmarkRecord(
               rvs.getViewsheet(), bookmarkInfo, BookmarkRecord.ACTION_TYPE_DELETE);

            // After remove bookmark, go to home bookmark.
            if(get("homeBkmkInfo") != null) {
               VSBookmarkInfo homeBkmkInfo =
                  (VSBookmarkInfo) get("homeBkmkInfo");
               processBookmark(homeBkmkInfo.getName(),
                  homeBkmkInfo.getOwner(), homeBkmkInfo.getType(),
                  homeBkmkInfo.isReadOnly(), rvs, (XPrincipal) getUser(),
                  this, getLinkURI(), getID(), command);
            }
         }
         catch(MessageException ex) {
            String message = ex.getMessage();
            command.addCommand(new MessageCommand(message,
                                                  MessageCommand.ERROR));
         }
      }
      else if("edit".equals(action)) {
         if(!Tool.equals(name, oldName) && rvs.containsBookmark(name, owner)) {
            throw new RuntimeException(Catalog.getCatalog().getString(
               "viewer.viewsheet.bookmark.duplicateWarning"));
         }

         IdentityID user = getUser() != null ? IdentityID.getIdentityIDFromKey(getUser().getName()) : null;
         boolean bookmarkRenamedInSchedule = !Tool.equals(name, oldName) &&
            isBookmarkUsedInSchedule(oldName, user);

            if(bookmarkRenamedInSchedule) {
               if(!isConfirmed()) {
                  MessageCommand msgCmd = new MessageCommand(
                     catalog.getString("viewer.viewsheet.bookmarkUsedInSchedule",
                                       oldName), MessageCommand.CONFIRM);
                  msgCmd.addEvent(this);
                  command.addCommand(msgCmd);
                  return;
               }
               else {
                  ScheduleManager manager = ScheduleManager.getScheduleManager();
                  manager.bookmarkRenamed(oldName, name, vsheet.getEntry().toIdentifier(), user);
               }
            }

         rvs.editBookmark(name, oldName, type, readOnly);
      }
      else if("refresh".equals(action)) {
         Assembly[] oldArr = getAssemblies(rvs.getViewsheet());
         boolean result = rvs.refreshBookmark(name, owner, 0);

         if(!result) {
            throw new RuntimeException(Catalog.getCatalog().getString(
               "viewer.viewsheet.bookmark.notFound", name));
         }
         else {
            ChangedAssemblyList list =
               createList(true, this, command, rvs, getLinkURI());
            removeUselessAssemblies(rvs, oldArr, command);
            VSEventUtil.refreshViewsheet(rvs, this, getID(), getLinkURI(),
                                         command, false, true, true, list,
                                         null, null);
         }
      }
      else if("setDefault".equals(action)) {
         if(vsheet.getRuntimeEntry().getScope() ==
            AssetRepository.TEMPORARY_SCOPE) {
            String error =
               catalog.getString("common.viewsheet.saveViewsheetDependence");
            command.addCommand(
               new MessageCommand(error, MessageCommand.ERROR));
            return;
         }

         rvs.setDefaultBookmark(
            new VSBookmark().new DefaultBookmark(name, owner));
      }
      else {
         throw new RuntimeException(Catalog.getCatalog().getString(
            "viewer.viewsheet.bookmark.invalidOperation", action));
      }

      GetBookmarksEvent event = (GetBookmarksEvent) get("event");

      if(event != null) {
         AssetCommand cmd = new AssetCommand(this);
         event.setWorksheetEngine(getViewsheetEngine());
         event.setUser(getUser());
         event.setID(getID());
         event.process(rvs, cmd);

         for(int i = 0; i < cmd.getCommandCount(); i++) {
            if(cmd.getCommand(i) instanceof GetBookmarksCommand) {
               command.addCommand(cmd.getCommand(i));
            }
         }
      }

      if("save".equals(originalAction) || "goto".equals(originalAction) ||
         "add".equals(originalAction))
      {
         command.addCommand(new MessageCommand("", MessageCommand.OK));
      }
      else if("edit".equals(originalAction)) {
         command.addCommand(new MessageCommand("", MessageCommand.INFO));
      }
   }

   public static void processBookmark(String name, IdentityID owner, int type,
      boolean readOnly, RuntimeViewsheet rvs, XPrincipal user, AssetEvent event,
      String url, String vsId, AssetCommand command) throws Exception
   {
      Assembly[] oldArr = getAssemblies(rvs.getViewsheet());
      boolean result = rvs.gotoBookmark(name, owner);

      if(!result) {
         throw new RuntimeException(Catalog.getCatalog().getString(
            "viewer.viewsheet.bookmark.notFound", name));
      }
      else {
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
         ExecutionRecord executionRecord = new ExecutionRecord(execSessionID,
            userSessionID, objectName, objectType, execType, execTimestamp,
            ExecutionRecord.EXEC_STATUS_SUCCESS, null);
         Audit.getInstance().auditExecution(executionRecord, user);
         executionRecord = new ExecutionRecord(execSessionID, userSessionID, objectName, objectType,
                                               ExecutionRecord.EXEC_TYPE_FINISH, execTimestamp,
                                               ExecutionRecord.EXEC_STATUS_SUCCESS, null);

         try {
            ChangedAssemblyList list =
               ViewsheetEvent.createList(true, event, command, rvs, url);
            removeUselessAssemblies(rvs, oldArr, command);
            VSEventUtil.refreshViewsheet(rvs, event, vsId, url, command, false,
                                         true, true, list, null, null);
            execTimestamp = new Date(System.currentTimeMillis());
            executionRecord.setExecTimestamp(execTimestamp);
            executionRecord.setExecStatus(
               ExecutionRecord.EXEC_STATUS_SUCCESS);
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
   }

   /**
    * Get all assemblies in current viewsheet.
    */
   private static Assembly[] getAssemblies(Viewsheet vs) {
      List<Assembly> assemblies = new ArrayList<>();
      addAssemblies(vs, assemblies);
      return assemblies.toArray(new Assembly[assemblies.size()]);
   }

   private static void addAssemblies(Viewsheet vs, List<Assembly> assemblies) {
      Assembly[] all = vs.getAssemblies();

      for(Assembly anAll : all) {
         assemblies.add(anAll);

         if(anAll instanceof Viewsheet) {
            addAssemblies((Viewsheet) anAll, assemblies);
         }
      }
   }

   /**
    * Remove use less assemblies.
    */
   private static void removeUselessAssemblies(RuntimeViewsheet rvs, Assembly[] oldAss,
                                               AssetCommand command)
   {
      Assembly[] newAss = getAssemblies(rvs.getViewsheet());
      AnnotationVSUtil.removeUselessAssemblies(oldAss, newAss, command);
   }

   private static void removeUselessAssemblies(RuntimeViewsheet rvs, Assembly[] oldAss,
                                               CommandDispatcher dispatcher)
   {
      Assembly[] newAss = getAssemblies(rvs.getViewsheet());
      AnnotationVSUtil.removeUselessAssemblies(oldAss, newAss, dispatcher);
   }
   
   /**
    * Check whether the bookmark is used in schedule.
    */
   private static boolean isBookmarkUsedInSchedule(String bookmark, IdentityID user)
   {
      ScheduleManager manager = ScheduleManager.getScheduleManager();
      Vector<ScheduleTask> tasks = manager.getScheduleTasks();

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

                  if(Tool.equals(name, bookmark) && Tool.equals(user, userName)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   Catalog catalog;
}
