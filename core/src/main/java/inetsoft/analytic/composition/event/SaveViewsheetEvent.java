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

import inetsoft.analytic.composition.ViewsheetEvent;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.command.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.command.*;
import inetsoft.report.composition.event.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.uql.viewsheet.vslayout.LayoutInfo;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;

import java.security.Principal;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Vector;

/**
 * Save Viewsheet event.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class SaveViewsheetEvent extends ViewsheetEvent
   implements SaveSheetEvent, BinaryEvent
{
   /**
    * Constructor.
    */
   public SaveViewsheetEvent() {
      super();
      catalog = Catalog.getCatalog();
   }

   /**
    * Constructor.
    * @param name worksheet name.
    * @param parent parent asset folder.
    * @param event refresh tree event.
    */
   public SaveViewsheetEvent(String name, AssetEntry parent, boolean closeVS,
      boolean openVS, ViewsheetInfo info, AssetEvent event)
   {
      this();
      put("name", name);
      put("parent", parent);
      put("event", event);
      put("closeVS", "" + closeVS);
      put("openVS", "" + openVS);
      put("info", info);
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Save Viewsheet");
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
    * Get the assembly which will load data in this event.
    */
   @Override
   protected String getAssembly() {
      return null;
   }

   /**
    * Return true if the event will access storage heavily.
    */
   @Override
   public boolean isStorageEvent() {
      return true;
   }

   /**
    * Process event.
    */
   @Override
   public void process(RuntimeViewsheet rvs, AssetCommand command)
      throws Exception
   {
      if("true".equals(get("mvconfirmed"))) {
         rvs.setProperty("mvconfirmed", "true");
      }

      String name = (String) get("name");
      AssetEntry parent = (AssetEntry) get("parent");
      AssetEntry entry = rvs.getEntry();
      entry.setProperty("_description_", null);
      ViewsheetService engine = getViewsheetEngine();
      ViewsheetInfo info = (ViewsheetInfo) get("info");
      LayoutInfo layoutInfo = (LayoutInfo) get("layoutInfo");

      Viewsheet vs = rvs.getViewsheet();
      boolean close = "true".equals(get("closeVS"));
      boolean openVS = "true".equals(get("openVS"));
      boolean saveAs = close && openVS;
      AssetEntry oldEntry = saveAs ? entry : null;

      if(vs == null) {
         return;
      }

      // is exists adhoc filter in viewsheet, need group the adhoc filter to
      // container first
      VSEventUtil.changeAdhocFilterStatus(rvs, command);

      if(info != null) {
         vs.setViewsheetInfo(info);
         vs.validate();
      }

      if(layoutInfo != null) {
         vs.setLayoutInfo(layoutInfo);
         vs.validate();
      }

      AssetEntry wentry = (AssetEntry) get("wentry");
      boolean baseChanged = false;

      if(info != null && !Tool.equals(wentry, vs.getBaseEntry())) {
         // reset runtime
         vs.setBaseEntry(wentry);
         baseChanged = true;
      }

      // log save viewsheet action
      String userName = SUtil.getUserName(getUser());
      String objectType = AssetEventUtil.getObjectType(entry);
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(userName, null, null, objectType,
         actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE, null);

      try {
         // save as
         if(name != null) {
            if(actionRecord != null) {
               actionRecord.setActionName(ActionRecord.ACTION_NAME_CREATE);
               String objectName = parent.getDescription()  + "/" + name;
               actionRecord.setObjectName(objectName);
            }

            if(parent != null) {
               try {
                  parent.setProperty("_sheetType_", "viewsheet");
                  engine.getAssetRepository()
                     .checkAssetPermission(getUser(), parent, ResourceAction.WRITE);
               }
               catch(Exception ex) {
                  command.addCommand(new MessageCommand(ex));

                  if(ex instanceof ConfirmException) {
                     actionRecord = null;
                  }

                  if(actionRecord != null) {
                     actionRecord.setActionStatus(
                        ActionRecord.ACTION_STATUS_FAILURE);
                     actionRecord.setActionError(ex.getMessage());
                  }

                  return;
               }
            }

            if(!parent.isFolder()) {
               String error =
                  catalog.getString("common.invalidFolder");

               if(actionRecord != null) {
                  actionRecord.setActionStatus(
                     ActionRecord.ACTION_STATUS_FAILURE);
                  actionRecord.setActionError(error);
               }

               command.addCommand(new MessageCommand(error));
               return;
            }

            String nname = parent.isRoot() ?
               name :
               parent.getPath() + "/" + name;
            IdentityID uname = getUser() == null ? null : IdentityID.getIdentityIDFromKey(getUser().getName());
            String alias = (String) get("alias");
            entry = new AssetEntry(parent.getScope(), AssetEntry.Type.VIEWSHEET,
                                   nname, uname);
            entry.copyProperties(parent);

            if(alias != null) {
               entry.setAlias(alias);
            }

            String desc = entry.getDescription();
            desc = desc.substring(0, desc.indexOf("/") + 1);
            desc += engine.localizeAssetEntry(entry.getPath(), getUser(), true,
               entry, entry.getScope() == AssetRepository.USER_SCOPE);
            entry.setProperty("_description_", desc);
            entry.setProperty("localStr",
               desc.substring(desc.lastIndexOf("/") + 1));

            if(!AssetUtil.isInvalidEntry(getAssetRepository(), entry)) {
               actionRecord = null;

               // not allowed to be overwritten replet
               MessageCommand msgCmd =
                  AssetUtil.isDuplicatedEntry(getAssetRepository(), entry) ?
                  new MessageCommand(
                     catalog.getString("common.folderExist", name),
                     MessageCommand.ERROR) :
                  new MessageCommand(catalog.getString("Invalid Name"));
               msgCmd.addEvent(this);
               command.addCommand(msgCmd);
               return;
            }

            if(engine.isDuplicatedEntry(getAssetRepository(), entry)) {
               if(isConfirmed()) {
                  boolean opened = "true".equals(get("opened"));

                  if(opened) {
                     actionRecord = null;
                     MessageCommand msgCmd = new MessageCommand(
                        catalog.getString(
                        "common.overwriteForbidden"),
                        MessageCommand.WARNING);
                     command.addCommand(msgCmd);
                     return;
                  }
               }
               else {
                  actionRecord = null;

                  // do not use itself as viewsheet assemblies
                  AssetEntry[] entries =
                     rvs.getViewsheet().getOuterDependents();
                  Vector vec = Tool.toVector(entries);
                  boolean containsSelf = vec.contains(entry);

                  // not allowed to be overwritten replet
                  MessageCommand msgCmd =
                     getAssetRepository().containsEntry(entry) &&
                     !containsSelf ?
                     new MessageCommand(
                        name + " " + catalog.getString("common.alreadyExists") +
                        ", " + catalog.getString("common.overwriteIt") + "?",
                        MessageCommand.CONFIRM) :
                     new MessageCommand(catalog.getString("replet.duplicated"));
                  msgCmd.addEvent(this);
                  msgCmd.put("dupEntry", entry);
                  command.addCommand(msgCmd);
                  return;
               }
            }

            String mvmsg = checkMVMessage(rvs, entry);

            if(mvmsg != null && mvmsg.length() > 0) {
               MessageCommand msgCmd = new MessageCommand(mvmsg,
                  MessageCommand.CONFIRM);
               msgCmd.addEvent(this);
               msgCmd.put("mvconfirmed", "true");
               command.addCommand(msgCmd);
               return;
            }

            if(actionRecord != null) {
               actionRecord.setObjectName(entry.getDescription());
            }

            try {
               if(inetsoft.mv.MVManager.getManager().containsMV(entry)) {
                  // mv is not enabled? don't warn user the message, but set the
                  // status to warn
                  vs.getViewsheetInfo().setWarningIfNotHitMV(true);
               }
            }
            catch(Exception ex) {
               // ignore it
            }

            engine.setViewsheet(rvs.getViewsheet(), entry, getUser(),
                                isConfirmed(), true);

            if(actionRecord != null) {
               actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
            }

            command.addCommand(new MessageCommand("", MessageCommand.OK));
         }
         else {
            String mvmsg = checkMVMessage(rvs, entry);

            if(mvmsg != null && mvmsg.length() > 0) {
               MessageCommand msgCmd = new MessageCommand(mvmsg,
                  MessageCommand.CONFIRM);
               msgCmd.addEvent(this);
               msgCmd.put("mvconfirmed", "true");
               command.addCommand(msgCmd);
               return;
            }

            if(actionRecord != null) {
               actionRecord.setActionName(ActionRecord.ACTION_NAME_EDIT);
               actionRecord.setObjectName(entry.getDescription());
            }

            try {
               if(inetsoft.mv.MVManager.getManager().
                  containsMV(entry))
               {
                  // mv is not enabled? don't warn user the message, but set the
                  // status to warn
                  vs.getViewsheetInfo().setWarningIfNotHitMV(true);
               }
            }
            catch(Exception ex) {
               // ignore it
            }

            engine.setViewsheet(rvs.getViewsheet(), entry, getUser(),
                                isConfirmed(), true);

            if(actionRecord != null) {
               actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
            }
         }

         VSEventUtil.deleteAutoSavedFile(entry, getUser());

         // @by ankitmathur Related to bug1415292205565, If this action was a
         // "save as", we should also delete the auto-saved version of the
         // original Viewsheet (if one exists).
         if(oldEntry != null && !oldEntry.equals(entry)) {
            VSEventUtil.deleteAutoSavedFile(oldEntry, getUser());
         }
      }
      catch(Exception ex) {
         if(ex instanceof ConfirmException) {
            actionRecord = null;
         }

         if(actionRecord != null) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError(ex.getMessage());
         }

         throw ex;
      }
      finally {
         if(actionRecord != null) {
            Audit.getInstance().auditAction(actionRecord, getUser());
         }
      }

      RefreshTreeEvent event = (RefreshTreeEvent) get("event");

      // @by larryl, should only need to refresh the asset tree if the name
      // has changed, otherwise the tree should remain the same
      if(event != null && saveAs) {
         AssetTreeModel model = AssetEventUtil.refreshTree(
            engine.getAssetRepository(), getUser(), event, isServer());
         command.addCommand(new RefreshTreeCommand(model));
      }

      // base worksheet changed in saveAs?
      if(baseChanged && saveAs) {
         // reset column selection
         SetViewsheetInfoEvent.resetDefaultColumnSelection(rvs);
         // refresh content
         VSRefreshEvent refresh = new VSRefreshEvent();
         refresh.setID(getID());
         refresh.setLinkURI(getLinkURI());
         refresh.process(rvs, command);
         // set viewsheet info to client side
         command.addCommand(new SetViewsheetInfoCommand(rvs));
         // refresh base tree
         AssetRepository rep = getWorksheetEngine().getAssetRepository();
         AssetTreeModel model =
            VSEventUtil.refreshBaseWSTree(rep, getUser(), vs);
         command.addCommand(new SetWSTreeModelCommand(model));
      }

      if(saveAs) {
         rvs.setEntry(entry);
         rvs.setEditable(true);

         Principal user0 = ThreadContext.getContextPrincipal();
         Catalog ucata = Catalog.getCatalog(user0, Catalog.REPORT);
         String str = entry.getAlias() != null ?
            ucata.getString(entry.getAlias()) :
            ucata.getString(entry.getName());
         entry.setProperty("localStr", str);

         ResetSheetCommand rcmd = new ResetSheetCommand();
         rcmd.put("entry", entry);
         rcmd.put("editable", rvs.isEditable() + "");
         command.addCommand(rcmd);
      }
      else if(close) {
         command.addCommand(new CloseViewsheetCommand());
      }

      // the viewsheet might be closed
      if(rvs.getViewsheet() != null) {
         long modified = rvs.getViewsheet().getLastModified();
         Date date = new Date(modified);
         command.put("lastModified",
                     AssetUtil.getDateTimeFormat().format(date));
      }
   }

   private String checkMVMessage(RuntimeViewsheet rvs, AssetEntry entry) {
      if("true".equals(rvs.getProperty("mvconfirmed"))) {
         return "";
      }

      Viewsheet vs = rvs.getViewsheet();

      try {
         if(inetsoft.mv.MVManager.getManager().
            containsMV(entry))
         {
            // mv is not enabled? don't warn user the message, but set the
            // status to warn
            return catalog.getString("vs.mv.exist");
         }
      }
      catch(Exception ex) {
         // ignore it
      }

      return "";
   }

   private Catalog catalog;
}
