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
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.command.ResizeDetailPaneCommand;
import inetsoft.graph.data.*;
import inetsoft.graph.geo.GeoDataSet;
import inetsoft.graph.geo.MappedDataSet;
import inetsoft.report.TableLens;
import inetsoft.report.composition.*;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.*;
import inetsoft.report.internal.table.FormatTableLens2;
import inetsoft.report.lens.DataSetTable;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ColumnInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.Format;

/**
 * Show data event.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class ShowDataEvent extends ViewsheetEvent {
   /**
    * Constructor.
    */
   public ShowDataEvent() {
      super();

      put("dataEvent", "true"); // mark as data event
   }

   /**
    * Constructor.
    */
   public ShowDataEvent(String name) {
      super();

      put("dataEvent", "true"); // mark as data event
      put("name", name);
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Show Data");
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
      String name = (String) get("name");
      return name != null ? new String[] {name} : new String[0];
   }

   /**
    * Process event.
    */
   @Override
   public void process(final RuntimeViewsheet rvs, AssetCommand command)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null || box == null) {
         return;
      }

      AssetEntry entry = rvs.getEntry();
      String name = (String) get("name");
      String vsid = (String) get("vsid");
      String detailPaneID = (String) get("detailPaneID");
      put("zoomScale", vs.getRScaleFont() + "");

      if(name == null) {
         return;
      }

      VGraphPair pair = box.getVGraphPair(name);
      DataSet dset = pair == null ? null : pair.getData();

      if(dset == null) {
         return;
      }

      VSDataSet vset = null;
      DataSet filter = dset;

      while(true) {
         if(filter instanceof VSDataSet) {
            vset = (VSDataSet) filter;
            break;
         }
         else if(filter instanceof DataSetFilter) {
            boolean useBase = filter instanceof BrushDataSet ||
               filter instanceof GeoDataSet ||
               filter instanceof MappedDataSet ||
               filter instanceof PairsDataSet;
            filter = ((DataSetFilter) filter).getDataSet();

            if(useBase) {
               dset = filter;
            }
         }
         else {
            break;
         }
      }

      TableLens table = null;
      final DataVSAssembly data = (DataVSAssembly) vs.getAssembly(name);
      table = new DataSetTable(dset);

      if(vset != null) {
         table = new FormatTableLens2(table);
         table = setDefaultFormats(vset, data, (FormatTableLens2) table);
      }

      ViewsheetService engine = getViewsheetEngine();
      String id = getID();
      String tname = "Data";
      String wid = detailPaneID != null ? detailPaneID :
         engine.openPreviewWorksheet(id, tname, getUser());
      RuntimeWorksheet rws = engine.getWorksheet(wid, getUser());
      AssetQuerySandbox box2 = rws.getAssetQuerySandbox();
      box2.resetTableLens(tname);
      rws.setParentID(id);
      Worksheet ws = rws.getWorksheet();

      if(data.getDataColumnWidths() != null) {
         String[] widths = Tool.split(data.getDataColumnWidths(), ',');

         for(int i = 0; i < widths.length; i++) {
            // @damianwysocki, Bug #9543
            // Removed grid, this is an old event, should not be used/removed
//            ws.setColWidth(i, Integer.parseInt(widths[i]));
         }
      }
      else {
         // clear widths to default
//         for(int i = 0; i < ws.getSize().width; i++) {
            // @damianwysocki, Bug #9543
            // Removed grid, this is an old event, should not be used/removed
//            ws.setColWidth(i, AssetUtil.defw);
//         }
      }

      DataTableAssembly assembly = (DataTableAssembly) ws.getAssembly(tname);
      assembly.setListener(null);
      assembly.setData(table);
      assembly.setListener(new PropertyChangeListener() {
         @Override
         public void propertyChange(PropertyChangeEvent evt) {
            data.setDataColumns((ColumnSelection) evt.getNewValue());
         }
      });

      // set columns visibility and format
      ColumnSelection infos = data.getDataColumns();
      ColumnSelection columns = assembly.getColumnSelection();
      ColumnSelection columns2 = new ColumnSelection();
      ColumnInfo info = (ColumnInfo) get("info");
      VSCompositeFormat format = (VSCompositeFormat) get("fmt");

      if(table instanceof FormatTableLens2) {
         ((FormatTableLens2) table).addTableFormat(data, assembly,
            columns, info, format);
      }

      // set sort ref
      SortInfo sortInfo = assembly.getSortInfo();
      sortInfo.clear();

      sortInfo.setListener(new PropertyChangeListener() {
         @Override
         public void propertyChange(PropertyChangeEvent evt) {
            data.setDataSortRef((SortRef) evt.getNewValue());
         }
      });

      SortRef sortRef = data.getDataSortRef();
      sortInfo.addSort(sortRef);

      if(infos == null) {
         data.setDataColumns(columns);
      }
      else {
         for(int i = 0; i < infos.getAttributeCount(); i++) {
            DataRef col = infos.getAttribute(i);

            if(columns.containsAttribute(col)) {
               columns2.addAttribute(col);
            }
         }

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            DataRef col = columns.getAttribute(i);

            if(!columns2.containsAttribute(col)) {
               columns2.addAttribute(col);
            }
         }

         assembly.setColumnSelection(columns2);
      }

      AssetEventUtil.previewTable(wid, tname, detailPaneID, getUser(), engine,
                                  this, command, entry, vsid);

      if(detailPaneID != null) {
         int[] cols = new int[0];

         for(int i = 0; i < cols.length; i++) {
            // @damianwysocki, Bug #9543
            // Removed grid, so always use default value for now,
            // deprecated event should be removed
            cols[i] = AssetUtil.defw;
         }

         command.addCommand(
            new ResizeDetailPaneCommand(AssetEvent.GRID_COL_RESIZE, cols));
      }
   }

   /**
    * Set the default date formats.
    */
   private TableLens setDefaultFormats(VSDataSet vset, DataVSAssembly data,
                                       FormatTableLens2 table)
   {
      for(int i = 0; i < table.getColCount(); i++) {
         DataRef ref = vset.getDataRef((String) table.getObject(0, i));

         if(ref instanceof VSDimensionRef) {
            VSDimensionRef dref = (VSDimensionRef) ref;
            int dlevel = dref.isDateRange() ? dref.getDateLevel() : XConstants.NONE_DATE_GROUP;

            if(dlevel != XConstants.NONE_DATE_GROUP) {
               Format fmt = XUtil.getDefaultDateFormat(dlevel, ref.getDataType());
               table.setFormat(i, fmt);
            }
         }
      }

      SourceInfo sinfo = data.getSourceInfo();
      String ctype = sinfo == null ? null :
         AssetUtil.getCubeType(sinfo.getPrefix(), sinfo.getSource());

      for(int i = 0; i < table.getColCount(); i++) {
         String header = (String) table.getObject(0, i);
         DataRef ref = vset.getDataRef(header);
         String caption = null;

         if(ref instanceof VSDimensionRef) {
            caption = ((VSDimensionRef) ref).getCaption();
         }

         if(ref instanceof VSAggregateRef) {
            if(!XCube.SQLSERVER.equals(ctype)) {
               caption = ((VSAggregateRef) ref).getCaption();
            }
         }

         header = caption != null ? caption : header;

         ((FormatTableLens2) table).setHeaderCaption(i, Tool.localize(header));
      }

      return table;
   }
}
