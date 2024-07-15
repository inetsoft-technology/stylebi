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
package inetsoft.report.composition.execution;

import inetsoft.analytic.AnalyticAssistant;
import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.report.*;
import inetsoft.report.composition.*;
import inetsoft.report.internal.RuntimeAssetEngine;
import inetsoft.report.internal.binding.*;
import inetsoft.sree.AnalyticRepository;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.util.QueryManager;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.security.Principal;

/**
 * This class provides API for processing and executing worksheets.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class ReportWorksheetProcessor implements WorksheetProcessor {
   /**
    * Create a processor.
    */
   public ReportWorksheetProcessor() {
   }

   /**
    * Execute a worksheet and return the data for the primary table assembly.
    * If the primary assembly is not a table, a null is returned.
    * @param entry worksheet asset entry.
    * @param vars execution parameters.
    * @param user current user.
    */
   @Override
   public XTable execute(AssetEntry entry, VariableTable vars, Principal user) throws Exception {
      AssetRepository asset = AssetUtil.getAssetRepository(false);

      if(asset == null) {
         LOG.warn("Asset repository not available");
         return null;
      }

      asset = new RuntimeAssetEngine(asset, null);
      Worksheet ws = null;
      RuntimeWorksheet rws = null;
      boolean useRuntimeWorksheet = false;
      WorksheetService service = WorksheetEngine.getWorksheetService();
      String drillFrom = (String) vars.get("drillfrom");

      // @by stephenwebster, For Bug #7815, When drilling from viewsheet
      // use the runtime worksheet from the runtime viewsheet such that the
      // runtime structure of the worksheet used to run the report query has
      // the same structure as the viewsheet runtime.  This ensures selections
      // made on the viewsheet would have the same effect on the report.
      if(drillFrom != null && service instanceof ViewsheetEngine) {
         RuntimeViewsheet rvs = null;

         try {
            rvs = ((ViewsheetEngine) service).getViewsheet(drillFrom, user);
         }
         catch(Exception e) {
            // The drill may be coming from a report, in which case the report cannot be found in
            // the viewsheet engine, so just ignore.
         }

         rws = rvs != null ? rvs.getRuntimeWorksheet() : null;
      }

      if(rws != null) {
         AssetEntry entryFromViewsheet = rws.getEntry();

         // @by stephenwebster, For Bug #10013
         // We cannot use the runtime worksheet from the viewsheet unless it
         // is the same one that is used in the report.
         if(entryFromViewsheet != null &&
            entryFromViewsheet.toIdentifier().equals(entry.toIdentifier()))
         {
            ws = rws.getWorksheet();

            // AssetQuerySandbox is not expecting a WorksheetWrapper since
            // some of the processing methods are not implemented.
            if(ws instanceof WorksheetWrapper) {
               ws = ((WorksheetWrapper) ws).getWorksheet();
            }

            useRuntimeWorksheet = true;
         }
      }

      if(!useRuntimeWorksheet) {
         ws = (Worksheet) asset.getSheet(entry, user, false, AssetContent.ALL);
         ws = ws == null ? null : (Worksheet) ws.clone();

         if(ws != null) {
            rws = new RuntimeWorksheet(entry, ws, user, false);
         }
      }

      if(ws == null) {
         if(LOG.isWarnEnabled()) {
            LOG.warn("Worksheet not found: {}", entry.toIdentifier());
         }

         return null;
      }

      if(drillFrom != null && service != null) {
         service.applyRuntimeCondition(drillFrom, rws);
      }

      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      String aname = entry.getProperty("table.name");

      if(aname == null) {
         aname = ws.getPrimaryAssemblyName();
      }

      box.setWSName(entry.getSheetName());
      box.setWSEntry(entry);
      box.setBaseUser(user);
      box.refreshVariableTable(vars);

      try {
         box.refreshColumnSelection(aname, true);
      }
      catch(Exception ex) {
         LOG.warn("Failed to refresh column selection: " + aname, ex);
      }

      TableAssembly assembly0 = (TableAssembly) ws.getAssembly(aname);
      TableAssembly assembly = (TableAssembly) assembly0.clone();
      String cid = (String) vars.get("__column__");

      // Bug #60782, clear cache on refresh
      if("true".equals(vars.get("__refresh_report__"))) {
         DataKey key = AssetDataCache.getCacheKey(
            assembly, box, null, AssetQuerySandbox.RUNTIME_MODE, true);
         AssetDataCache.removeCachedData(key);
      }

      // @by billh, fix customer bug bug1299280932445
      // hide the other columns and apply distinct, so that users could browse
      // data properly
      if(vars.get("__browsed__") != null && cid != null) {
         // fix Bug #46077, use mirror table to avoid lose the maxrow limit of the ws table.
         String nname = aname + Viewsheet.OUTER_TABLE_SUFFIX;
         assembly.getInfo().setName(nname);
         ws.addAssembly(assembly);
         MirrorTableAssembly mirror = new MirrorTableAssembly(ws, aname, assembly);
         mirror.setVisibleTable(assembly.isVisibleTable());
         mirror.setProperty(Viewsheet.VS_MIRROR_TABLE, "true");
         ws.addAssembly(mirror);
         assembly = mirror;

         ColumnSelection cols = assembly.getColumnSelection(false);
         ColumnRef col = (ColumnRef) cols.getAttribute(cid);

         if(col != null && assembly.isPlain()) {
            if(!(assembly instanceof CubeTableAssembly)) {
               for(int i = 0; i < cols.getAttributeCount(); i++) {
                  ColumnRef tcol = (ColumnRef) cols.getAttribute(i);
                  tcol.setVisible(tcol == col);
               }
            }

            assembly.setDistinct(true);
            assembly.resetColumnSelection();
         }
      }

      box.setTimeLimited(assembly.getName(), false);
      assembly.setProperty("assetName", "");
      QueryManager qmgr = null;
      TableLens table = null;

      // if query failed, need to use the original table assembly and bindingAttr
      // since the merged table/bindingAttr is no longer valid (49077).
      table = AssetDataCache.getData(null, assembly0, box, qmgr);

      if(table == null || !table.moreRows(0)) {
         return table;
      }

      // maintain header & table data path
      AssetTableLens table2 = new AssetTableLens(table);

      for(int i = 0; i < table.getColCount(); i++) {
         String oheader = XUtil.getHeader(table, i).toString();
         String nheader = AssetUtil.format(XUtil.getHeader(table, i));

         if(!oheader.equals(nheader)) {
            table2.setObject(0, i, nheader);
         }
      }

      return table2;
   }

   private static final Logger LOG = LoggerFactory.getLogger(ReportWorksheetProcessor.class);
}
