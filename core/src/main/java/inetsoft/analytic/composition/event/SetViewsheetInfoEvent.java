/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.analytic.composition.event;

import inetsoft.analytic.composition.ViewsheetEvent;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.command.SetViewsheetInfoCommand;
import inetsoft.report.composition.*;
import inetsoft.report.composition.command.*;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.event.RefreshTreeEvent;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssemblyInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TipVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.script.ScriptEnv;

import java.util.*;

/**
 * Set viewsheet info event.
 *
 * @version 8.5, 07/26/2006
 * @author InetSoft Technology Corp
 */
public class SetViewsheetInfoEvent extends ViewsheetEvent {
   /**
    * Reset the old base work sheet table column selection.
    */
   public static void resetDefaultColumnSelection(RuntimeViewsheet rvs) {
      Viewsheet vs = rvs.getViewsheet();
      Worksheet ws = vs == null ? null : vs.getBaseWorksheet();

      if(ws == null) {
         return;
      }

      Assembly[] arr = ws.getAssemblies();

      if(arr == null || arr.length <= 0) {
         return;
      }

      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return;
      }

      AssetQuerySandbox wbox = box.getAssetQuerySandbox();

      if(wbox == null) {
         return;
      }

      for(int i = 0; i < arr.length; i++) {
         wbox.resetDefaultColumnSelection(arr[i].getName());
      }
   }

   /**
    * Constructor.
    */
   public SetViewsheetInfoEvent() {
      super();
   }

   /**
    * Constructor.
    */
   public SetViewsheetInfoEvent(ViewsheetInfo info) {
      this();
      put("info", info);
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Set Viewsheet Info");
   }

   /**
    * Check if is undoable/redoable.
    * @return <tt>true</tt> if undoable/redoable.
    */
   @Override
   public boolean isUndoable() {
      return true;
   }

   /**
    * Check if requires reset when undo.
    */
   @Override
   public boolean requiresReset() {
      return false;
   }

   /**
    * Get the influenced assemblies.
    * @return the influenced assemblies, <tt>null</tt> means all.
    */
   @Override
   public String[] getAssemblies() {
      return null;
   }

   /**
    * Process event.
    */
   @Override
   public void process(RuntimeViewsheet rvs, AssetCommand command)
      throws Exception
   {
      ViewsheetInfo info = (ViewsheetInfo) get("info");
      AssetEntry wentry = (AssetEntry) get("wentry");
      wentry = VSUtil.fixEntry(wentry);
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null || info == null) {
         return;
      }

      Set<String> oflyovers = getFlyoverViews(vs);
      boolean refresh = info.isMetadata() != vs.getViewsheetInfo().isMetadata();
      boolean reset = Boolean.valueOf((String) get("reset"));
      ScriptEnv env = rvs.getViewsheetSandbox().getScope().getScriptEnv();

      // check script.
      if(env != null) {
         try {
            if(!isConfirmed() && info.getOnInit() != null) {
               env.compile(info.getOnInit());
            }

            if(!isConfirmed() && info.getOnLoad() != null) {
               env.compile(info.getOnLoad());
            }
         }
         catch(Exception ex) {
            MessageCommand msgCmd = new MessageCommand(
               Catalog.getCatalog().getString("viewer.viewsheet.scriptFailed",
               ex.getMessage()), MessageCommand.CONFIRM);
            msgCmd.addEvent(this);
            command.addCommand(msgCmd);
            return;
         }
      }

      vs.setViewsheetInfo(info);

      if(!Tool.equals(wentry, vs.getBaseEntry())) {
         // reset runtime
         vs.setBaseEntry(wentry);
         // reset column selection
         resetDefaultColumnSelection(rvs);
         refresh = true;
      }

      command.addCommand(new SetViewsheetInfoCommand(rvs));
      command.addCommand(new MessageCommand("", MessageCommand.OK));

      if(reset) {
         VSUtil.resetRuntimeValues(vs, false);
      }

      if(refresh || reset) {
         // refresh content
         VSRefreshEvent evt = new VSRefreshEvent();
         evt.setID(getID());
         evt.setLinkURI(getLinkURI());
         evt.put("initing", "false");
         evt.process(rvs, command);
      }

      RefreshTreeEvent event = (RefreshTreeEvent) get("event");
      ViewsheetService vengine = getViewsheetEngine();
      AssetRepository engine = vengine.getAssetRepository();
      String alias = (String) get("alias");
      AssetEntry entry = rvs.getEntry();

      if(engine.containsEntry(entry) && event != null) {
         entry.setAlias(alias != null ? alias : "");
         String desc = entry.getDescription();
         desc = desc.substring(0, desc.indexOf("/") + 1);
         desc += vengine.localizeAssetEntry(entry.getPath(), getUser(), true,
            entry, entry.getScope() == AssetRepository.USER_SCOPE);
         entry.setProperty("_description_", desc);
         entry.setProperty("localStr",
            desc.substring(desc.lastIndexOf("/") + 1));
         engine.changeSheet(entry, entry, getUser(), true);
         AssetTreeModel model =
            AssetEventUtil.refreshTree(engine, getUser(), event, isServer());
         command.addCommand(new RefreshTreeCommand(model));

         rvs.setEntry(entry);
         rvs.setEditable(true);
         ResetSheetCommand rcmd = new ResetSheetCommand();
         rcmd.put("entry", entry);
         rcmd.put("editable", rvs.isEditable() + "");
         command.addCommand(rcmd);
      }

      Set<String> nflyovers = getFlyoverViews(vs);
      oflyovers.removeAll(nflyovers);

      // clear flyover views that are removed in onLoad/onInit script
      for(String vname : oflyovers) {
         int hint = VSAssembly.INPUT_DATA_CHANGED;
         VSAssembly tip = (VSAssembly) vs.getAssembly(vname);

         if(tip != null && tip.getTipConditionList() != null) {
            tip.setTipConditionList(null);
            VSEventUtil.execute(rvs, this, vname, getLinkURI(), hint, command);
            VSEventUtil.refreshVSAssembly(rvs, vname, command);
         }
      }
   }

   /**
    * Get the flyover views in the viewsheet.
    */
   private Set<String> getFlyoverViews(Viewsheet vs) {
      Set<String> tipviews = new HashSet<>();

      for(Assembly obj : vs.getAssemblies()) {
         AssemblyInfo vsinfo = obj.getInfo();

         if(vsinfo instanceof TipVSAssemblyInfo) {
            String[] arr = ((TipVSAssemblyInfo) vsinfo).getFlyoverViews();

            if(arr != null) {
               tipviews.addAll(Arrays.asList(arr));
            }
         }
      }

      return tipviews;
   }
}
