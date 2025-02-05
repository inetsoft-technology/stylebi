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
package inetsoft.analytic.composition.event;

import inetsoft.analytic.composition.*;
import inetsoft.analytic.composition.command.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.command.MessageCommand;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.*;
import inetsoft.util.audit.Audit;
import inetsoft.util.audit.ExecutionRecord;
import inetsoft.util.log.*;
import inetsoft.web.AutoSaveUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.security.Principal;
import java.sql.Date;
import java.util.*;

/**
 * Open viewsheet event.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class OpenViewsheetEvent extends AssetEvent {
   /**
    * Constructor.
    */
   public OpenViewsheetEvent() {
      super();
   }

   /**
    * Constructor.
    */
   public OpenViewsheetEvent(AssetEntry entry) {
      this();
      put("entry", entry);
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Open Viewsheet");
   }

   /**
    * Process this asset event.
    */
   @Override
   public void process(AssetCommand command) throws Exception {
      try {
         AssetDataCache.monitor(true);
         process0(command);
      }
      finally {
         AssetDataCache.monitor(false);
      }
   }

   private void process0(AssetCommand command) throws Exception {
      AssetEntry entry = (AssetEntry) get("entry");

      if(entry == null || !entry.isViewsheet()) {
         return;
      }

      if(Thread.currentThread() instanceof GroupedThread) {
         ((GroupedThread) Thread.currentThread())
            .addRecord(LogContext.DASHBOARD, entry.getPath());
      }

      boolean openAutoSaved = "true".equals(get("openAutoSaved"));
      entry.setProperty("openAutoSaved", openAutoSaved + "");

      String rvid = (String) get("rvid");

      // if the runtime viewsheet id already exist, just refresh it
      if(rvid != null && !"".equals(rvid) && !rvid.isEmpty()) {
         openReturnedViewsheet(rvid, command);
         return;
      }

      String drillfrom = (String) get("drillfrom");
      boolean viewer = "true".equals(get("viewer"));

      // from EA drill? it is preview
      if(!viewer && drillfrom != null) {
         entry.setProperty("preview", "true");
      }

      if(viewer) {
         applyViewsheetQuota(entry, getUser());
      }

      Object sync = get("sync");
      ItemMap params = (ItemMap) get("parameters");
      String fullScreenId = (String) get("fullScreenId");
      String eid = (String) get("eid");
      VariableTable vars = VSEventUtil.decodeParameters(params);
      boolean auditFinish = true;

      viewer = viewer || drillfrom != null || params != null;
      entry.setProperty("sync", sync + "");
      vars = (vars == null) ? new VariableTable() : vars;

      // log viewsheet excecution
      String userSessionID = getUser() == null ?
         XSessionService.createSessionID(XSessionService.USER, null) :
         ((XPrincipal) getUser()).getSessionID();
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
      Audit.getInstance().auditExecution(executionRecord, getUser());
      executionRecord = new ExecutionRecord(execSessionID,
                                            userSessionID, objectName, objectType,
                                            ExecutionRecord.EXEC_TYPE_FINISH, execTimestamp,
                                            ExecutionRecord.EXEC_STATUS_SUCCESS, null);

      try {
         ViewsheetService engine = (ViewsheetService) getWorksheetEngine();

         if(!isConfirmed() && !viewer) {
            if(AutoSaveUtils.exists(entry, getUser())) {
               MessageCommand msgCmd = new MessageCommand(
                  Catalog.getCatalog().getString(
                  "designer.designer.autosavedFileExists"),
                  MessageCommand.CONFIRM);
               msgCmd.addEvent(this);
               command.addCommand(msgCmd);
               return;
            }
         }

         String displayWidth = (String) get("_device_display_width");
         String pixelDensity = (String) get("_device_pixel_density");
         String mobile = (String) get("_device_mobile");
         String serverString = (String) get("_device_server_string");
         String userAgent = (String) get("_device_user_agent");

         if(displayWidth != null) {
            entry.setProperty("_device_display_width", displayWidth);
            entry.setProperty("_device_pixel_density", pixelDensity);
            entry.setProperty("_device_mobile", mobile);
            entry.setProperty("_device_server_string", serverString);
            entry.setProperty("_device_user_agent", userAgent);

            if(LogManager.getInstance().isDebugEnabled(LOG.getName())) {
               String msg;

               if(serverString != null) {
                  msg = "Mobile app: serverString=" + serverString +
                     ", userAgent=" + userAgent +
                     ", pixelDensity=" + pixelDensity;
               }
               else {
                  msg = "Browser: userAgent=" + userAgent;
               }

               msg += ", displayWidth=" + displayWidth + ", mobile=" + mobile;
               LOG.debug(msg);
            }
         }

         AssetEvent.MAIN.set(this);
         String id = VSEventUtil.openViewsheet(engine, this, getUser(),
            getLinkURI(), eid, entry, command, viewer, drillfrom, vars,
            fullScreenId);

         setOption(command);

         RuntimeViewsheet rvs = engine.getViewsheet(id, getUser());
         processBookmark(id, rvs, command);

         if(rvs != null) {
            auditFinish = shouldAuditFinish(rvs);
            rvs.setExecSessionID(execSessionID);

            if(get("previousURL") != null) {
               rvs.setPreviousURL((String) get("previousURL"));
            }
            // drill from exist? it is the previous viewsheet
            else if(drillfrom != null) {
               RuntimeViewsheet drvs = engine.getViewsheet(drillfrom, getUser());
               AssetEntry dentry = drvs.getEntry();
               String didentifier = dentry.toIdentifier();
               String purl = getLinkURI() + "?op=vs_html&identifier=" +
                  Tool.encodeWebURL(didentifier) +
                  "&rvid=" + Tool.encodeWebURL(drillfrom);
               rvs.setPreviousURL(purl);
            }

            String url = rvs.getPreviousURL();

            if(url != null) {
               command.addCommand(new GetPreviousURLCommand(url));
            }

            VSModelTrapContext context = new VSModelTrapContext(rvs, true);

            if(context.isCheckTrap()) {
               context.checkTrap(null, null);
               DataRef[] refs = context.getGrayedFields();

               if(refs.length > 0) {
                  command.addCommand(new SetVSTreeGrayFieldsCommand(refs));
               }
            }

            Viewsheet vs = rvs.getViewsheet();
            Assembly[] assemblies = vs.getAssemblies();

            // fix bug1309250160380, fix AggregateInfo for CrosstabVSAssembly
            for(Assembly assembly : assemblies) {
               if(assembly instanceof CrosstabVSAssembly) {
                  VSEventUtil.fixAggregateInfo((CrosstabVSAssembly) assembly,
                                               rvs, getWorksheetEngine().getAssetRepository(), getUser());
               }
            }
         }

         execTimestamp = new Date(System.currentTimeMillis());
         executionRecord.setExecTimestamp(execTimestamp);
         executionRecord.setExecStatus(ExecutionRecord.EXEC_STATUS_SUCCESS);
         command.put("openAutoSaved", openAutoSaved + "");
         VSEventUtil.deleteAutoSavedFile(entry, getUser());
      }
      catch(ConfirmException ex) {
         throw ex;
      }
      catch(Exception e) {
         execTimestamp = new Date(System.currentTimeMillis());
         executionRecord.setExecTimestamp(execTimestamp);
         executionRecord.setExecStatus(ExecutionRecord.EXEC_STATUS_FAILURE);
         executionRecord.setExecError(e.getMessage());

         throw e;
      }
      finally {
         // @by ankitmathur fix bug1415397231289, Only log the "finish" record
         // in this method if the Viewsheet does not contain Variables. If it
         // does CollectParametersOverEvent will be called and we should log the
         // record in that event instead.
         if(auditFinish) {
            Audit.getInstance().auditExecution(executionRecord, getUser());
         }

         if(executionRecord != null && executionRecord.getExecTimestamp() != null) {
            logEntry.setFinishTime(executionRecord.getExecTimestamp().getTime());
            LogUtil.logPerformance(logEntry);
         }
      }
   }

   private void openReturnedViewsheet(String rid, AssetCommand command)
      throws Exception
   {
      ViewsheetService engine = (ViewsheetService) getWorksheetEngine();
      RuntimeViewsheet rvs = engine.getViewsheet(rid, getUser());

      if(rvs == null) {
         command.addCommand(new MessageCommand("Viewsheet " + rid + " was expired."));
         return;
      }

      VSEventUtil.setExportType(command);
      VSEventUtil.setPermission(rvs, getUser(), command);
      processBookmark(rid, rvs, command);
      ChangedAssemblyList clist = ViewsheetEvent.createList(
         true, this, command, null, getLinkURI());
      VSEventUtil.refreshViewsheet(rvs, this, rid, getLinkURI(), command,
         true, false, false, clist);
      String url = rvs.getPreviousURL();

      if(url != null) {
         command.addCommand(new GetPreviousURLCommand(url));
      }
   }

   /**
    * process viewsheet bookmark.
    */
   private void processBookmark(String id, RuntimeViewsheet rvs,
      AssetCommand command) throws Exception
   {
      if(getUser() == null) {
         return;
      }

      IdentityID currUser = IdentityID.getIdentityIDFromKey(getUser().getName());
      VSBookmarkInfo info = null;

      if(get("bookmarkName") == null || get("bookmarkUser") == null ) {
         return;
      }

      //anonymous should not apply bookmark.
      if(XPrincipal.ANONYMOUS.equals(currUser.name)) {
         return;
      }

      String bookmarkName = (String) get("bookmarkName");
      IdentityID bookmarkUser = IdentityID.getIdentityIDFromKey((String) get("bookmarkUser"));

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
      boolean readOnly = info.isReadOnly();

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

      EditBookmarkEvent.processBookmark(bookmarkName, bookmarkUser,
         bookmarkType, readOnly, rvs, (XPrincipal) getUser(), this,
         getLinkURI(), id, command);
   }

   /**
    * Apply the per instance viewsheet limit.
    *
    * @param entry the asset entry for the viewsheet.
    * @param user  the user for which to apply the quota.
    */
   private void applyViewsheetQuota(AssetEntry entry, Principal user) {
      int limit = -1;
      String prop = SreeEnv.getProperty("viewsheet.user.instance.limit");

      if(prop != null) {
         try {
            limit = Integer.parseInt(prop);
         }
         catch(Exception ignore) {
         }
      }

      if(limit <= 0) {
         return;
      }

      ViewsheetService service = ViewsheetEngine.getViewsheetEngine();

      Set<RuntimeViewsheet> list = new TreeSet<>(
         new Comparator<RuntimeViewsheet>() {
            @Override
            public int compare(RuntimeViewsheet o1, RuntimeViewsheet o2) {
               return Long.compare(o1.getLastAccessed(), o2.getLastAccessed());
            }
         });

      for(RuntimeViewsheet sheet : service.getRuntimeViewsheets(user)) {
         if(sheet.getEntry().equals(entry) && sheet.isViewer()) {
            list.add(sheet);
         }
      }

      // a viewsheet is being opened, so we want limit - 1 instances left
      while(list.size() >= limit) {
         Iterator<RuntimeViewsheet> iterator = list.iterator();
         RuntimeViewsheet sheet = iterator.next();
         iterator.remove();

         LOG.debug(
            "Closing viewsheet " + sheet.getID() + " due to quota for " + user);

         CloseViewsheetEvent event = new CloseViewsheetEvent();
         event.put("__ID__", sheet.getID());
         event.put("_isEV_", "true");

         service.process(event, user);
      }
   }

   /**
    * Determine if the "finish" record for Auditing should be logged in this
    * Event
    * @param rvs the RuntimeViewsheet for which we need to check the variables
    * for.
    * @return the boolean representing if the "finish" record should be logged.
    */
   private boolean shouldAuditFinish(RuntimeViewsheet rvs) {
      VariableTable vars = new VariableTable();

      try {
         AssetQuerySandbox abox =
            rvs.getViewsheetSandbox().getAssetQuerySandbox();

         if(abox.getAllVariables(vars) != null &&
            abox.getAllVariables(vars).length > 0)
         {
            return false;
         }
      }
      catch (Exception e) {
         // In case there are any issues/errors in checking the Variables for
         // this Viewsheet, just swallow the exception and continue on with the
         // previous logic. There is no reason to display this error to the end-user.
      }

      return true;
   }

   private void setOption(AssetCommand command) {
      for(int i = command.getCommandCount() - 1; i >= 0; i--) {
         AssetCommand cmd = command.getCommand(i);

         if(cmd instanceof CollectParametersCommand) {
            cmd.put("close", "true");
            break;
         }
      }
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(OpenViewsheetEvent.class);
}
