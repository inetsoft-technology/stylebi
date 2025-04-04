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
package inetsoft.web.composer.ws.assembly;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.TableAssemblyInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.composer.ws.TableModeController;
import inetsoft.web.composer.ws.TableModeService;
import inetsoft.web.composer.ws.event.WSDragColumnsEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;

import java.awt.*;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * Class to process ws column dnd from asset tree..
 */
public class WSColumnDndProcessor {
   // Drag columns from detail pane.
   public WSColumnDndProcessor(RuntimeWorksheet rws,
                               TableAssembly table,
                               WSDragColumnsEvent evt,
                               Principal user,
                               CommandDispatcher commandDispatcher)
   {
      this.event = evt;
      this.user = user;
      this.rws = rws;
      this.table = table;
      this.commandDispatcher = commandDispatcher;
   }

   // check if not a ws, do nothing.
   public void process() throws Exception {
      double top = event.top();
      double left = event.left();

      boolean eventContainsInvisibleColumn = containsInvisibleColumns(table, event.columnIndices());

      if(eventContainsInvisibleColumn) {
         createNewTable(left, top);
      }
      else {
         createMirrorTable(left, top);
      }
   }

   private void createNewTable(double left, double top) throws Exception {
      if(!(table instanceof BoundTableAssembly) || table instanceof SQLBoundTableAssembly ||
         table instanceof TabularTableAssembly)
      {
         return;
      }

      if(event.columnIndices().length == 0) {
         return;
      }

      final Worksheet ws = rws.getWorksheet();
      ColumnSelection columns = table.getColumnSelection();
      BoundTableAssembly ntable;
      ColumnSelection ncolumns = new ColumnSelection();
      ColumnRef[] columnRefs = new ColumnRef[event.columnIndices().length];

      for(int i = 0; i < event.columnIndices().length; i++) {
         int index = event.columnIndices()[i];
         ColumnRef ref = (ColumnRef) columns.getAttribute(index);
         DataRef column = columns.getAttribute(columns.indexOfAttribute(ref));
         ncolumns.addAttribute((DataRef) column.clone());
         columnRefs[i] = ref;
      }

      SourceInfo sinfo = ((BoundTableAssembly) table).getSourceInfo();
      String source = sinfo.getSource();
      final String nname;

      if(table instanceof PhysicalBoundTableAssembly) {
         nname = AssetUtil.getNextName(ws, AssetUtil.normalizeTable(source));
      }
      else {
         nname = AssetUtil.getNextName(ws, AbstractSheet.TABLE_ASSET);
      }

      if(table instanceof QueryBoundTableAssembly) {
         ntable = new QueryBoundTableAssembly(ws, nname);
      }
      else if(table instanceof PhysicalBoundTableAssembly) {
         ntable = new PhysicalBoundTableAssembly(ws, nname);
      }
      else {
         ntable = new BoundTableAssembly(ws, nname);
      }

      if(!isExpressionOnly(columnRefs)) {
         Point point = new Point();
         point.setLocation(left, top);

         ntable.setColumnSelection(ncolumns);
         ntable.setSourceInfo((SourceInfo) sinfo.clone());
         ntable.setPixelOffset(point);
         ws.addAssembly(ntable);
         WorksheetEventUtil.createAssembly(rws, ntable, commandDispatcher, user);
         WorksheetEventUtil.loadTableData(rws, nname, false, false);
         WorksheetEventUtil.refreshAssembly(rws, nname, false, commandDispatcher, user);
         WorksheetEventUtil.layout(rws, commandDispatcher);
      }
   }

   /**
    * Return true if dragging columns are all expression columns.
    */
   private boolean isExpressionOnly(ColumnRef[] columns) {
      int count = 0;

      for(ColumnRef column : columns) {
         if(!column.isExpression()) {
            count++;
         }
      }

      return count == 0;
   }

   private void createMirrorTable(double x, double y)
      throws Exception
   {
      Worksheet ws = rws.getWorksheet();
      String name = table.getName();
      AbstractWSAssembly assembly = (AbstractWSAssembly) ws.getAssembly(name);
      int[] cols = event.columnIndices();

      // For mirror table, create its mirror can't using hidden columns.
      if(assembly instanceof MirrorTableAssembly) {
         cols = fixMirrorHiddenColumns((MirrorTableAssembly) assembly, event.columnIndices());

         if(cols.length == 0) {
            return;
         }
      }
      else if(assembly instanceof QueryBoundTableAssembly) {
         cols = fixQueryHiddenColumns((QueryBoundTableAssembly) assembly, event.columnIndices());

         if(cols.length == 0) {
            return;
         }
      }

      String nname = AssetUtil.getNextName(ws, AbstractSheet.TABLE_ASSET);
      MirrorTableAssembly ntable = new MirrorTableAssembly(ws, nname, null, false, assembly);
      AssetEventUtil.initColumnSelection(rws, ntable);

      // don't create is has no visible columns.
      if(!hideColumnNoDrag(ntable, cols)) {
         return;
      }

      TableModeService.setDefaultTableMode(ntable, rws.getAssetQuerySandbox());

      ntable.setPixelOffset(new Point((int) x, (int) y));
      ws.addAssembly(ntable);

      WorksheetEventUtil.createAssembly(rws, ntable, commandDispatcher, user);
      WorksheetEventUtil.loadTableData(rws, nname, false, false);
      WorksheetEventUtil.refreshAssembly(rws, nname, false, commandDispatcher, user);
      WorksheetEventUtil.layout(rws, commandDispatcher);
   }

   private int[] fixMirrorHiddenColumns(MirrorTableAssembly mirror, int[] cols) {
      List<Integer> ncols = new ArrayList<>();
      TableAssemblyInfo oinfo = (TableAssemblyInfo) mirror.getInfo();
      ColumnSelection columns = oinfo.getPrivateColumnSelection();

      for(int index : cols) {
         ColumnRef col = (ColumnRef) columns.getAttribute(index);

         if(col.isVisible()) {
            ncols.add(index);
         }
      }

      int[] visibleCols = new int[ncols.size()];

      for(int i = 0; i < ncols.size(); i++) {
         visibleCols[i] = ncols.get(i);
      }

      return visibleCols;
   }

   private int[] fixQueryHiddenColumns(QueryBoundTableAssembly mirror, int[] cols) {
      List<Integer> ncols = new ArrayList<>();
      TableAssemblyInfo oinfo = (TableAssemblyInfo) mirror.getInfo();
      ColumnSelection icolumns = oinfo.getPrivateColumnSelection();
      ColumnSelection ocolumns = oinfo.getPublicColumnSelection();

      for(int index : cols) {
         ColumnRef col = (ColumnRef) icolumns.getAttribute(index);
         int idx = ocolumns.indexOfAttribute(col);

         if(idx != -1) {
            ncols.add(idx);
         }
      }

      int[] visibleCols = new int[ncols.size()];

      for(int i = 0; i < ncols.size(); i++) {
         visibleCols[i] = ncols.get(i);
      }

      return visibleCols;
   }

   private boolean containsInvisibleColumns(TableAssembly table, int[] cols) {
      if(table == null || table.isLiveData() || cols == null || cols.length == 0) {
         return false;
      }

      ColumnSelection columns = table.getColumnSelection(false);

      for(int i : cols) {
         final ColumnRef columnRef = (ColumnRef) columns.getAttribute(i);

         if(!columnRef.isVisible()) {
            return true;
         }
      }

      return false;
   }

   private boolean hideColumnNoDrag(TableAssembly table, int[] cols) {
      if(table == null) {
         return false;
      }

      ColumnSelection sourceColumns = this.table instanceof QueryBoundTableAssembly ?
         this.table.getColumnSelection(true) : this.table.getColumnSelection(false);
      ColumnSelection dndColumns = new ColumnSelection();

      for(int i = 0; i < cols.length; i++) {
         if(cols[i] < sourceColumns.getAttributeCount()) {
            ColumnRef column = (ColumnRef) sourceColumns.getAttribute(cols[i]);

            if(column.isVisible()) {
               dndColumns.addAttribute(column);
            }
         }
      }

      TableAssemblyInfo tinfo = (TableAssemblyInfo) table.getInfo();
      // The column index in drag is from private column selection.
      ColumnSelection icolumns = tinfo.getPrivateColumnSelection();
      // hide some columns not dragged in public column selection.
      ColumnSelection ocolumns = tinfo.getPublicColumnSelection();
      boolean isAggregate = table.isAggregate();

      for(int i = 0; i < icolumns.getAttributeCount(); i++) {
         ColumnRef col = (ColumnRef) icolumns.getAttribute(i);
         col.setVisible(false);
      }

      boolean hasVisibleColumns = false;
      ColumnSelection tcolumn = isAggregate ? ocolumns : icolumns;

      for(int i = 0; i < dndColumns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) ocolumns.findAttribute(dndColumns.getAttribute(i));
         DataRef ref = icolumns.getAttribute(dndColumns.getAttribute(i).getAttribute());

         if(ref instanceof ColumnRef) {
            ((ColumnRef) ref).setVisible(true);
            hasVisibleColumns = true;
         }
      }

      return hasVisibleColumns;
   }

   private final WSDragColumnsEvent event;
   private final Principal user;
   private final RuntimeWorksheet rws;
   private final TableAssembly table;

   private final CommandDispatcher commandDispatcher;
}
