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
package inetsoft.report.composition.event;

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.mv.MVManager;
import inetsoft.report.composition.*;
import inetsoft.report.composition.command.*;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;

import java.security.Principal;
import java.sql.Timestamp;
import java.util.Date;

/**
 * Save worksheet event.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class SaveWorksheetEvent extends WorksheetEvent
   implements SaveSheetEvent
{
   /**
    * Constructor.
    */
   public SaveWorksheetEvent() {
      super();
   }

   /**
    * Constructor.
    * @param name worksheet name.
    * @param parent parent asset folder.
    * @param event refresh tree event.
    */
   public SaveWorksheetEvent(String name, AssetEntry parent, boolean closeWS,
      boolean openWS, boolean reportSource, AssetEvent event)
   {
      this();
      put("name", name);
      put("parent", parent);
      put("reportSource", "" + reportSource);
      put("event", event);
      put("closeWS", "" + closeWS);
      put("openWS", "" + openWS);
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
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return catalog.getString("Save Worksheet");
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
    * Return true if the event will access storage heavily.
    */
   @Override
   public boolean isStorageEvent() {
      return true;
   }

   /**
    * Process save worksheet event.
    */
   @Override
   public void process(RuntimeWorksheet rws, AssetCommand command) throws Exception {
      if("true".equals(get("mvconfirmed"))) {
         rws.setProperty("mvconfirmed", "true");
      }

      final String fullName = (String) get("name");
      String name = (fullName == null) ? null
         : fullName.substring(fullName.lastIndexOf("/") + 1);
      // rename datablock
      boolean renameDatablock = "true".equals((String) get("renameDatablock"));
      Assembly[] assemblies = rws.getWorksheet().getAssemblies();

      if(renameDatablock && name != null && assemblies.length == 1
         && !name.equals(assemblies[0].getName()))
      {
         WorksheetEvent event = new RenameAssemblyEvent(
         assemblies[0].getName(), name);
         event.process(rws, command);
      }

      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      String wsName = box.getWSName();
      boolean reportSource = !"false".equals(get("reportSource"));
      boolean deploy = "true".equals((String) get("deploy"));
      UserVariable[] variables = null;
      Principal user = box.getUser();
      variables = rws.getWorksheet().getAllVariables();
      boolean close = "true".equals((String) get("closeWS"));
      boolean openWS = "true".equals((String) get("openWS"));
      boolean saveAs = close && openWS;
      AssetEntry parent = (AssetEntry) get("parent");
      AssetEntry oldEntry;

      AssetEntry entry = rws.getEntry();
      WorksheetService engine = getWorksheetEngine();
      AssetRepository assetRep = engine.getAssetRepository();
      oldEntry = saveAs ? entry : null;

      // log save worksheet action
      String userName = SUtil.getUserName(getUser());
      String objectType = AssetEventUtil.getObjectType(entry);
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(userName, null, null, objectType,
         actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE, null);

      try {
         if(name != null) {
            if(actionRecord != null) {
               actionRecord.setActionName(ActionRecord.ACTION_NAME_CREATE);
               String objectName = parent.getDescription() + "/" + name;
               actionRecord.setObjectName(objectName);
            }

            if(parent != null) {
               try {
                  parent.setProperty("_sheetType_", "worksheet");
                  assetRep.checkAssetPermission(getUser(), parent, ResourceAction.WRITE);
               }
               catch(Exception ex) {
                  MessageCommand msgCmd = new MessageCommand(ex);
                  msgCmd.put("restricted", entry);
                  msgCmd.addEvent(this);
                  command.addCommand(msgCmd);

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
               String error = catalog.getString(
                  "common.invalidFolder");

               if(actionRecord != null) {
                  actionRecord.setActionStatus(
                     ActionRecord.ACTION_STATUS_FAILURE);
                  actionRecord.setActionError(error);
               }

               command.addCommand(new MessageCommand(error));
               return;
            }

            String nname = parent.isRoot() ? name :
               parent.getPath() + "/" + name;
            IdentityID uname = getUser() == null ? null : IdentityID.getIdentityIDFromKey(getUser().getName());
            String alias = (String) get("alias");
            String description = (String) get("desc");
            entry = new AssetEntry(parent.getScope(), AssetEntry.Type.WORKSHEET,
                                   nname, uname);
            entry.copyProperties(parent);
            entry.setReportDataSource(reportSource);
            entry.setProperty("description", description);
            String newName = entry.getSheetName();

            for(int i = 0; i < variables.length; i++) {
               String vname = variables[i].getName();

               if(user != null) {
                  Object varValue = engine.getCachedProperty(user,
                     wsName + " variable : " + vname);
                  engine.setCachedProperty(user,
                     newName + " variable : " + vname, varValue);
               }
            }

            if(alias != null) {
               entry.setAlias(alias);
            }

            String desc = entry.getDescription();
            desc = desc.substring(0, desc.indexOf("/") + 1);
            desc += engine.localizeAssetEntry(entry.getPath(), getUser(),
               true, entry, entry.getScope() == AssetRepository.USER_SCOPE);
            entry.setProperty("_description_", desc);
            entry.setProperty("localStr",
               desc.substring(desc.lastIndexOf("/") + 1));

            if(actionRecord != null) {
               actionRecord.setObjectName(entry.getDescription());
            }

            String isLeaf = (String) get("isLeaf");

            if(isLeaf != null) {
               RuntimeSheet[] opened =
                  getWorksheetEngine().getRuntimeSheets(user);

               for(int i = 0; opened != null && i < opened.length; i++) {
                  AssetEntry tentry = opened[i].getEntry();

                  if(!assetRep.containsEntry(tentry)) {
                     continue;
                  }

                  if(Tool.equals(entry, tentry)) {
                     MessageCommand msgCmd = new MessageCommand(
                        catalog.getString(
                        "common.overwriteForbidden"),
                        MessageCommand.WARNING);
                     command.addCommand(msgCmd);
                     return;
                  }
               }
            }

            // if select a leaf node, the user wants to overwrite the selected
            // worksheet, so do not check duplicate in this case.
            // fix bug1285570783088, consistent with others, check duplicate also
            if(getWorksheetEngine().isDuplicatedEntry(assetRep, entry)
               && isLeaf != null)
            {
               if(!isConfirmed()) {
                  actionRecord = null;
                  MessageCommand msgCmd = assetRep.containsEntry(entry) ?
                     new MessageCommand(
                        name + " " + catalog.getString("common.alreadyExists") +
                        ", " + catalog.getString("common.overwriteIt") + "?",
                        MessageCommand.CONFIRM) :
                     new MessageCommand(catalog.getString("Duplicate Name"));
                  msgCmd.addEvent(this);
                  msgCmd.put("dupEntry", entry);
                  command.addCommand(msgCmd);
                  return;
               }
            }

            String mvmsg = checkMVMessage(rws, entry);

            if(mvmsg.length() > 0) {
               MessageCommand msgCmd = new MessageCommand(mvmsg,
                  MessageCommand.CONFIRM);
               msgCmd.addEvent(this);
               msgCmd.put("mvconfirmed", "true");
               command.addCommand(msgCmd);
               return;
            }

            engine.setWorksheet(rws.getWorksheet(), entry, getUser(),
                                isConfirmed(), true);

            if(actionRecord != null) {
               actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
            }

            command.addCommand(new MessageCommand("", MessageCommand.OK));
         }
         else {
            if(actionRecord != null) {
               actionRecord.setActionName(ActionRecord.ACTION_NAME_EDIT);
               actionRecord.setObjectName(entry.getDescription());
            }

            String mvmsg = checkMVMessage(rws, entry);

            if(mvmsg.length() > 0) {
               MessageCommand msgCmd = new MessageCommand(mvmsg,
                  MessageCommand.CONFIRM);
               msgCmd.addEvent(this);
               msgCmd.put("mvconfirmed", "true");
               command.addCommand(msgCmd);
               return;
            }

            engine.setWorksheet(rws.getWorksheet(), entry, getUser(),
                                isConfirmed(), true);

            if(actionRecord != null) {
               actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
            }
         }

         // @by ankitmathur Fix bug1415292205565 If saving the Worksheet was
         // successful, check and remove any auto-saved versions of the
         // Worksheet. If this action was a "save as", we should also delete
         // the auto-saved version of the original Worksheet (if one exists).
         VSEventUtil.deleteAutoSavedFile(entry, getUser());

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

      if(event != null) {
         AssetTreeModel model = AssetEventUtil.refreshTree(
            assetRep, getUser(), event, isServer());
         AssetCommand cmd = new RefreshTreeCommand(model);
         cmd.put("createdWS", entry.toIdentifier());
         command.addCommand(cmd);
      }

      if(close) {
         if(openWS) {
            rws.setEntry(entry);
            rws.setEditable(true);
            ResetSheetCommand rcmd = new ResetSheetCommand();
            rcmd.put("entry", entry);
            rcmd.put("editable", rws.isEditable() + "");
            rcmd.put("reportSource", reportSource + "");
            command.addCommand(rcmd);
         }
         else {
            command.addCommand(new CloseWorksheetCommand());
         }
      }

      long modified = rws.getWorksheet().getLastModified();
      Date date = new Date(modified);
      command.put("lastModified", AssetUtil.getDateTimeFormat().format(date));
   }

   private String checkMVMessage(RuntimeWorksheet rws, AssetEntry entry) {
      if("true".equals(rws.getProperty("mvconfirmed"))) {
         return "";
      }

      String mvmsg = "";
      String[] wpaths = MVManager.getManager().findSheets(entry, true);

      if(wpaths.length > 0) {
         mvmsg += catalog.getString("Worksheets") + ":\n";
      }

      for(int i = 0; i < wpaths.length; i++) {
         mvmsg += (i > 0 ? ",\n" : "") + "   " + wpaths[i];
      }

      String[] vpaths = MVManager.getManager().findSheets(entry, false);

      if(vpaths.length > 0) {
         if(wpaths.length > 0) {
            mvmsg += "\n\n";
         }

         mvmsg += catalog.getString("Viewsheets") + ":\n";
      }

      for(int i = 0; i < vpaths.length; i++) {
         mvmsg += (i > 0 ? ",\n" : "") + "   " + vpaths[i];
      }

      if(mvmsg.isEmpty()) {
         return "";
      }

      return catalog.getString("mv.dependmessage", mvmsg);
   }

   private Catalog catalog = Catalog.getCatalog(getUser());
}
