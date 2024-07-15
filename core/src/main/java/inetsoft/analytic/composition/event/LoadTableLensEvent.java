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

import inetsoft.analytic.composition.ViewsheetEvent;
import inetsoft.analytic.composition.command.LoadTableLensCommand;
import inetsoft.report.composition.*;
import inetsoft.report.composition.command.MessageCommand;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.TableHighlightAttr.HighlightTableLens;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.CancelledException;
import inetsoft.util.Catalog;
import inetsoft.util.script.ScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Load table lens event.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class LoadTableLensEvent extends ViewsheetEvent implements BinaryEvent {
   /**
    * Table row counts per loading process.
    */
   public static final int BLOCK = 100;

   /**
    * Table small row counts per loading process.
    */
   public static final int SMALL_BLOCK = 50;

   /**
    * Process event.
    * @param rvs the specified runtime viewsheet.
    * @param name the specified table assembly name.
    * @param uri the specified service request uri.
    * @param mode the specified mode.
    * @param start the start point.
    * @param num the data row count.
    * @param command the command container.
    */
   public static void processEvent(RuntimeViewsheet rvs, AssetEvent evt,
      String name, String uri, int mode, int start, int num,
      AssetCommand command)
      throws Exception
   {
      long startTime = System.currentTimeMillis();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return;
      }

      try {
         String oname = name;
         boolean detail = oname.startsWith(Assembly.DETAIL);

         if(detail) {
            oname = oname.substring(Assembly.DETAIL.length());
         }

         // @by stephenwebster, For bug1434058776599
         // It doesn't seem reasonable to let the event occur, if later on
         // in VSEventUtil we remove the LoadTableCommand.  This will prevent
         // unnecessary work from occurring if the assembly generating the
         // command is not visible in the tab at design time.
         try {
            VSAssembly assembly = rvs.getViewsheet().getAssembly(oname);

            if(!VSEventUtil.isVisibleTabVS(assembly, rvs.isRuntime())) {
               return;
            }
         }
         catch(Exception ex) {
            //ignore, not expecting any exception here.
         }

         VSTableLens lens = box.getVSTableLens(oname, detail);

         if(lens == null) {
            return;
         }

         if(Util.isTimeoutTable(lens)) {
            AssetCommand scmd =
               new MessageCommand(Catalog.getCatalog().getString(
                                  "common.timeout", "" + name + ""),
                                  MessageCommand.ERROR);
            command.addCommand(scmd);
            return;
         }

         int ccount = lens.getColCount();

         if(ccount > 500) {
            throw new Exception("Column count is out of bounds:" + ccount);
         }

         // check more rows to init table, fix bug1243742452824
         lens.moreRows(start + num - 1);
         lens.setLinkURI(uri);
         int count = lens.getRowCount();
         boolean more = count < 0;
         count = count < 0 ? -count - 1 : count;
         more = more || count > start + num;

         if(!more) {
            LOG.debug(
               "Table " + name + " finished processing: " + lens.getRowCount());
         }

         int end = Math.min(count, start + num);
         int block = Math.max(0, end - start);

         if(!more && (rvs.isPreview() || rvs.isViewer())) {
            //VSEventUtil.addWarningTextCommand(lens, rvs, command);
         }

         Viewsheet vs = rvs.getViewsheet();

         if(vs == null) {
            return;
         }

         VSAssembly vsassembly = (VSAssembly) vs.getAssembly(name);

         // @by davidd, 2011-11-11 Update Crosstab cached table lens, used
         // for improving performance of Drilldowns..
         if(vsassembly instanceof CrosstabVSAssembly) {
            CrosstabVSAssembly cvsa = (CrosstabVSAssembly) vsassembly;

            // Only save the lens, if this request is the more recent.
            if(startTime > cvsa.getLastDrillDownRequest()) {
               cvsa.setLastTableLens(lens);
            }
         }

         VSAssemblyInfo info = VSEventUtil.getAssemblyInfo(rvs, vsassembly);
         addScriptables(lens, vsassembly, box);
         AssetCommand lcmd = new LoadTableLensCommand(name, lens, mode,
                                                      start, block, !more, info);
         command.addCommand(lcmd);
      }
      catch(ConfirmException cex) {
         if(!(cex.getEvent() instanceof CheckMVEvent)) {
            RefreshAssemblyEvent revent = new RefreshAssemblyEvent();
            revent.put("name", name);
            cex.setEvent(revent);
         }

         // if this exception is thrown, the main event could not continue,
         // so here we do not throw it but just remember it for engine to
         // handle after the main event is executed over
         evt.addConfirmException(cex);
      }
      catch(CancelledException e) {
         throw e;
      }
      catch(ScriptException e) {
         MessageCommand cmd =
            new MessageCommand(e.getMessage(), MessageCommand.INFO);
         command.addCommand(cmd);
      }
      catch(Exception ex) {
         String id = evt instanceof GridEvent ? ((GridEvent) evt).getID() : null;
         WorksheetEngine.ExceptionKey key = new WorksheetEngine.ExceptionKey(ex, id);
         WorksheetEngine.ExceptionKey key2 = WorksheetEngine.exceptionMap.get(key);

         if(key2 != null && !key2.isTimeout()) {
            return;
         }
         else {
            WorksheetEngine.exceptionMap.put(key, key);
         }

         LOG.error("Failed to load the table data", ex);
         AssetCommand scmd = new MessageCommand(Catalog.getCatalog().getString(
            "common.nodata", "" + ex.getMessage() + ""),
            MessageCommand.ERROR);
         command.addCommand(scmd);
      }
   }

   /**
    * Constructor.
    */
   public LoadTableLensEvent() {
      super();
   }

   /**
    * Constructor.
    */
   public LoadTableLensEvent(String name, int mode, int start, int num) {
      this();

      put("name", name);
      put("mode", "" + mode);
      put("start", "" + start);
      put("num", "" + num);
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Load Lens");
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
   public void process(RuntimeViewsheet rvs, AssetCommand command)
      throws Exception
   {
      String name = (String) get("name");
      int mode = Integer.parseInt((String) get("mode"));
      int start = Integer.parseInt((String) get("start"));
      int num = Integer.parseInt((String) get("num"));
      String uri = getLinkURI();

      processEvent(rvs, this, name, uri, mode, start, num, command);
   }

   /**
    * Add scriptables to asset query sandbox.
    */
   private static void addScriptables(VSTableLens lens,
                                      VSAssembly vsassembly,
                                      ViewsheetSandbox vsbox)
   {
      HighlightTableLens table = (HighlightTableLens) Util.getNestedTable(
         lens, HighlightTableLens.class);

      if(table == null || vsassembly == null) {
         return;
      }

      table.setQuerySandbox(vsbox.getConditionAssetQuerySandbox(
         vsassembly.getViewsheet()));
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(LoadTableLensEvent.class);
}
