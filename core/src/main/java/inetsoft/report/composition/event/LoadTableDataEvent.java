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
package inetsoft.report.composition.event;

import inetsoft.report.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.command.LoadTableDataCommand;
import inetsoft.report.composition.command.MessageCommand;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.FormatTableLens;
import inetsoft.report.internal.table.TableTool;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.report.lens.SubTableLens;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Load table data event.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class LoadTableDataEvent extends WorksheetEvent implements BinaryEvent {
   /**
    * Table row counts per loading process.
    */
   public static final int BLOCK = 100;

   /**
    * Get XEmbeddedTable by table lens.
    * @param lens the specified table lens from query.
    * @param block number of rows affected.
    * @param start start point.
    */
   public static XEmbeddedTable getEmbeddedTable(TableLens lens, int block,
                                                 int start)
   {
      String[][] data = new String[block][lens.getColCount()];
      Object[][] links = new Object[block][lens.getColCount()];
      String[] types = new String[lens.getColCount()];

      for(int i = 0; i < block; i++) {
         for(int j = 0; j < lens.getColCount(); j++) {
            Object val = lens.getObject(i + start, j);
            data[i][j] = AssetUtil.format(val);

            // set hyperlinks
            XDrillInfo dinfo = lens.getXDrillInfo(i + start, j);

            if(dinfo == null) {
               continue;
            }

            TableLens dataTable = lens instanceof TableFilter ?
               getAssetDataTable((TableFilter) lens) : lens;
            ColumnIndexMap columnIndexMap = new ColumnIndexMap(dataTable, true);
            int brow = TableTool.getBaseRowIndex(lens, dataTable, i + start);
            int bcol = TableTool.getBaseColIndex(lens, dataTable, j);
            Vector vec = new Vector();
            DataRef dcol = dinfo.getColumn();

            for(int k = 0; k < dinfo.getDrillPathCount() && brow >= 0; k++) {
               DrillPath dpath = dinfo.getDrillPath(k);
               DrillSubQuery query = dpath.getQuery();
               Hyperlink.Ref ref =
                  new Hyperlink.Ref(dpath, dataTable, brow, bcol);

               if(query != null) {
                  ref.setParameter(StyleConstants.SUB_QUERY_PARAM, dataTable.getObject(brow, bcol));
                  String queryParam = null;

                  if(dcol != null) {
                     queryParam = Util.findSubqueryVariable(query, dcol.getName());
                  }

                  if(queryParam == null) {
                     String tableHeader = dataTable.getColumnIdentifier(bcol);
                     tableHeader = tableHeader == null ?
                        (String) Util.getHeader(dataTable, bcol) : tableHeader;
                     queryParam = Util.findSubqueryVariable(query, tableHeader);
                  }

                  if(queryParam != null) {
                     ref.setParameter(
                        Tool.encodeWebURL(StyleConstants.SUB_QUERY_PARAM_PREFIX + queryParam),
                        dataTable.getObject(brow, bcol));
                  }

                  Iterator<String> it = query.getParameterNames();

                  while(it.hasNext()) {
                     String qvar = it.next();

                     if(Tool.equals(qvar, queryParam)) {
                        continue;
                     }

                     String header = query.getParameter(qvar);
                     int col = Util.findColumn(columnIndexMap, header);

                     if(col < 0) {
                        continue;
                     }

                     ref.setParameter(
                        Tool.encodeWebURL(StyleConstants.SUB_QUERY_PARAM_PREFIX + qvar),
                        dataTable.getObject(brow, col));
                  }
               }

               vec.add(ref);
            }

            Hyperlink.Ref[] refs = new Hyperlink.Ref[vec.size()];
            links[i][j] = vec.toArray(refs);
         }
      }

      return new XEmbeddedTable(types, data, links);
   }

   /**
    * Process event.
    * @param rws the specified runtime worksheet.
    * @param name the specified table assembly name.
    * @param mode the specified mode.
    * @param start the start point.
    * @param num the data row count.
    * @param command the command container.
    */
   public static void processEvent(RuntimeWorksheet rws, String name, int mode,
                                   int start, int num, AssetCommand command)
      throws Exception
   {
      Worksheet ws = rws.getWorksheet();
      AssetQuerySandbox box = rws.getAssetQuerySandbox();

      try {
         List<ColumnInfo> cinfos = box.getColumnInfos(name, mode);
         TableLens lens = box.getTableLens(name, mode);

         // check if the lens is disposed
         try {
            if(lens != null && lens.moreRows(1)) {
               for(int i = 0; i < lens.getColCount(); i++) {
                  lens.getObject(0, i);
               }
            }
         }
         catch(DisposedException ex) {
            box.resetTableLens(name, mode);
            lens = box.getTableLens(name, mode);
         }

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
            lens = new SubTableLens(lens, -1, 0, -1, 500, true, true);
            AssetCommand scmd =
               new MessageCommand(Catalog.getCatalog().getString(
                                  "common.columnexceed",
                                  "" + name + "", "" + ccount),
                                  MessageCommand.ERROR);
            command.addCommand(scmd);
         }

         lens.moreRows(start + num - 1);
         int count = lens.getRowCount();
         boolean more = count < 0;

         count = count < 0 ? -count - 1 : count;
         more = more || count > start + num;

         int end = Math.min(count, start + num);
         int block = Math.max(0, end - start);

         XEmbeddedTable embedded = getEmbeddedTable(lens, block, start);

         // if show details from viewsheet
         String linkUri = (String) command.get("linkUri");

         if(linkUri != null) {
            embedded.setLinkURI(linkUri);
            command.remove("linkUri");
         }

         AssetCommand lcmd = new LoadTableDataCommand(name, cinfos, embedded,
            mode, start, block, !more);

         // set exceeded information if completed
         if(!more) {
            TableAssembly table = (TableAssembly) ws.getAssembly(name);
            String exceededMsg = AssetEventUtil.getExceededMsg(table, block);

            if(exceededMsg != null) {
               lcmd.put("exceeded", exceededMsg);
            }
         }

         command.addCommand(lcmd);
      }
      catch(ConfirmException mex) {
         throw mex;
      }
      catch(CancelledException cex) {
         throw cex;
      }
      catch(Exception ex) {
         LOG.error(
            "Failed to process load table data event for assembly: " + name, ex);
         AssetCommand scmd = new MessageCommand(ex, MessageCommand.ERROR);
         scmd.put("load_table_data_error", name);
         command.addCommand(scmd);
      }
   }

   /**
    * Constructor.
    */
   public LoadTableDataEvent() {
      super();
   }

   /**
    * Constructor.
    */
   public LoadTableDataEvent(String name) {
      this();

      put("name", name);
   }

   /**
    * Constructor.
    */
   public LoadTableDataEvent(String name, int mode, int start, int num) {
      this(name);

      put("mode", "" + mode);
      put("start", "" + start);
      put("num", "" + num);
   }

   /**
    * Get a table lens to get pure data.
    */
   private static TableLens getAssetDataTable(TableFilter filter) {
      TableLens table = filter;

      while(table instanceof TableFilter) {
         if(table instanceof FormatTableLens) {
            return ((FormatTableLens) table).getTable();
         }

         if(table instanceof AttributeTableLens) {
            return ((AttributeTableLens) table).getTable();
         }

         if(table instanceof FormulaTableLens) {
            return table;
         }

         table = ((TableFilter) table).getTable();
      }

      return filter;
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Load Data");
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
    * Process this worksheet event.
    * @param rws the specified runtime worksheet as the context.
    */
   @Override
   public void process(RuntimeWorksheet rws, AssetCommand command)
      throws Exception
   {
      String name = (String) get("name");
      String linkUri = getLinkURI();
      int mode = -1;

      if(linkUri != null) {
         command.put("linkUri", linkUri);
      }

      try {
         mode = Integer.parseInt((String) get("mode"));
      }
      catch(Exception e) {
      }

      if(mode < 0) {
         Worksheet ws = rws.getWorksheet();
         WSAssembly table = (WSAssembly) ws.getAssembly(name);

         if(table == null) {
            return;
         }

         if(table instanceof TableAssembly) {
            mode = AssetEventUtil.getMode((TableAssembly) table);

            if(!table.isVisible()) {
               return;
            }

            if(AssetUtil.isHierarchical((TableAssembly) table)) {
               TableAssembly[] stables =
                  ((ComposedTableAssembly) table).getTableAssemblies(false);

               int mode2 = mode | AssetQuerySandbox.EMBEDDED_MODE;

               for(int i = 0; i < stables.length; i++) {
                  String sname = name + "/" + stables[i].getName();
                  processEvent(rws, sname, mode2, 1, LoadTableDataEvent.BLOCK,
                               command);
               }
            }

            processEvent(rws, name, mode, 1, LoadTableDataEvent.BLOCK, command);
         }
      }
      else {
         int start = Integer.parseInt((String) get("start"));
         int num = Integer.parseInt((String) get("num"));

         AssetQuerySandbox box = rws.getAssetQuerySandbox();

         if(start == 1 && isConfirmed()) {
            box.setTimeLimited(name, !isConfirmed());
         }

         processEvent(rws, name, mode, start, num, command);
      }
   }

   private static ThreadPool pool = new ThreadPool(2, 2, "LoadTableData");
   private static final Logger LOG =
      LoggerFactory.getLogger(LoadTableDataEvent.class);
}
