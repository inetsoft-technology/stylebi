/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.cluster.*;
import inetsoft.report.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.viewsheet.vslayout.AbstractLayout;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.vs.objects.event.*;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.util.*;
import java.util.List;

@Service
@ClusterProxy
public class ComposerAdhocFilterService {

   public ComposerAdhocFilterService(ViewsheetService viewsheetService,
                                     CoreLifecycleService coreLifecycleService,
                                     VSTableService vsTableService,
                                     GroupingService groupingService)
   {
      this.viewsheetService = viewsheetService;
      this.coreLifecycleService = coreLifecycleService;
      this.vsTableService = vsTableService;
      this.groupingService = groupingService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void addTableFilter(@ClusterProxyKey String runtimeId, FilterTableEvent event,
                              Principal principal, String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly assembly = (VSAssembly) vs.getAssembly(event.getName());

      assert assembly instanceof TableDataVSAssembly;

      DataRef column = null;
      TableDataVSAssembly table = (TableDataVSAssembly) assembly;
      String title = null;

      if(table instanceof TableVSAssembly) {
         TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getInfo();
         ColumnSelection cols = info.getColumnSelection();
         int index = ComposerVSTableController.getActualColIndex(cols, event.getCol());
         DataRef col = cols.getAttribute(index);
         ColumnRef colRef = (ColumnRef) col.clone();
         title = colRef.getAlias();
         colRef.setAlias(null);

         column = colRef;
      }
      else if(table instanceof CrosstabVSAssembly) {
         int row = event.getRow();
         int col = event.getCol();

         column = getCellDataRef(rvs, (CrosstabVSAssembly) table, row, col);
      }
      else if(table instanceof CalcTableVSAssembly) {
         CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) table.getInfo();
         TableLayout layout = info.getTableLayout();
         ViewsheetSandbox box = rvs.getViewsheetSandbox();
         String oname = table.getAbsoluteName();
         String refName = "";
         boolean detail = oname.startsWith(Assembly.DETAIL);

         if(detail) {
            oname = oname.substring(Assembly.DETAIL.length());
         }

         VSTableLens lens = box.getVSTableLens(oname, detail);
         TableDataPath tpath = lens.getTableDataPath(event.getRow(), event.getCol());
         String[] path = tpath.getPath();

         if(path != null) {
            String path0 = path[0];
            int idx1 = path0.lastIndexOf("[");
            int idx2 = path0.indexOf("]");

            if(idx1 > 0 && idx2 > idx1) {
               String[] pair = path0.substring(idx1 + 1, idx2).split(",");

               if(pair.length == 2) {
                  int row = Integer.parseInt(pair[0]);
                  int col = Integer.parseInt(pair[1]);

                  CellBinding binding = layout.getCellBinding(row, col);

                  if(binding == null ||
                     binding.getType() != TableCellBinding.BIND_COLUMN)
                  {
                     return null;
                  }

                  refName = binding.getValue();
                  Worksheet ws = vs.getBaseWorksheet();
                  String tableName = info.getSourceInfo().getSource();
                  String columnName = refName;

                  CalculateRef ref = getCalcRef(vs, tableName, columnName);

                  if(ref != null && !ref.isBaseOnDetail()) {
                     MessageCommand command = new MessageCommand();
                     command.setMessage(
                        Catalog.getCatalog().getString("adhocfilter.calcfield.notsupport"));
                     command.setType(MessageCommand.Type.ERROR);
                     dispatcher.sendCommand(command);

                     return null;
                  }

                  if(findColumnType(vs, ws, tableName, refName) == null) {
                     refName = VSLayoutTool.getOriginalColumn(refName);
                  }
               }
            }
         }

         AttributeRef attr = new AttributeRef(null, refName);
         ColumnRef ref = new ColumnRef(attr);
         column = ref;
      }

      if(column != null) {
         addFilter(rvs, table, column, event.getTop(), event.getLeft(), false, linkUri,
                   dispatcher, title);
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void addChartFilter(@ClusterProxyKey String runtimeId, FilterChartEvent event,
                              Principal principal, String linkUri,
                              CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();
      ChartVSAssembly chart = (ChartVSAssembly) vs.getAssembly(event.getName());
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      VSChartInfo chartInfo = info.getVSChartInfo();
      DataRef chartRef;

      if(event.isLegend()) {
         chartRef = Arrays.stream(chartInfo.getAestheticRefs(true))
            .filter(ref -> ref.getFullName().equals(event.getColumnName()))
            .findFirst()
            .get();
      }
      else {
         chartRef = chartInfo.getFieldByName(event.getColumnName(), true);
      }

      if(chartRef == null) {
         return null;
      }

      addFilter(rvs, chart, chartRef, event.getTop(), event.getLeft(),
                event.isDimension(), linkUri, dispatcher, null);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void moveAdhocFilter(@ClusterProxyKey String runtimeId, VSObjectEvent event, Principal principal,
                               String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      final RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      final Viewsheet vs = rvs.getViewsheet();
      final Assembly assembly = vs.getAssembly(event.getName());
      final String containerName;

      if(assembly instanceof SelectionListVSAssembly) {
         final SelectionListVSAssembly list = (SelectionListVSAssembly) assembly;
         final SelectionListVSAssemblyInfo info =
            (SelectionListVSAssemblyInfo) list.getVSAssemblyInfo();
         Map<String, Object> map = list.getAhFilterProperty();
         containerName = map != null ? (String) map.get("_container") : null;

         if(list.getSelectedObjects().isEmpty() && info.isCreatedByAdhoc()) {
            VSEventUtil.removeVSObject(rvs, event.getName(), dispatcher);
            return null;
         }
         else {
            info.setShowType(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
            info.setAdhocFilter(false);
         }
      }
      else if(assembly instanceof TimeSliderVSAssembly) {
         final TimeSliderVSAssembly slider = (TimeSliderVSAssembly) assembly;
         final SelectionList slist = slider.getSelectionList();
         final TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) slider.getInfo();
         Map<String, Object> map = slider.getAhFilterProperty();
         containerName = map != null ? (String) map.get("_container") : null;

         if(slist != null) {
            final SelectionValue start = slist.getSelectionValue(0);
            final SelectionValue end = slist.getSelectionValue(slist.getSelectionValueCount() - 1);

            if(start.isSelected() && end.isSelected() && info.isCreatedByAdhoc()) {
               VSEventUtil.removeVSObject(rvs, event.getName(), dispatcher);
               return null;
            }
            else {
               info.setAdhocFilter(false);
            }
         }
      }
      else {
         // Error
         return null;
      }

      final VSAssembly selection = (VSAssembly) assembly;
      final List<CurrentSelectionVSAssembly> containers = findContainers(vs, selection);
      CurrentSelectionVSAssembly container = containers.stream()
         .filter(c -> containerName == null || containerName.equals(c.getAbsoluteName()))
         .findFirst().get();
      restoreFormats(selection);
      groupingService.groupComponents(rvs, container, selection, true, linkUri, dispatcher, true);

      return null;
   }

   /**
    * Get binding data caption.
    */
   private String getColumnCaption(DataRef dataRef) {
      boolean isCube = dataRef instanceof VSDataRef &&
         (dataRef.getRefType() & AbstractDataRef.CUBE) != 0;

      if(isCube) {
         return ((VSDataRef) dataRef).getFullName();
      }

      return null;
   }

   /**
    * Get the ref associated with this cell.
    */
   private DataRef getCellDataRef(RuntimeViewsheet rvs, CrosstabVSAssembly table, int row, int col)
      throws Exception
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      String oname = table.getAbsoluteName();
      boolean detail = oname.startsWith(Assembly.DETAIL);

      if(detail) {
         oname = oname.substring(Assembly.DETAIL.length());
      }

      VSTableLens lens = box.getVSTableLens(oname, detail);
      TableDataPath tpath = lens.getTableDataPath(row, col);

      return VSTableService.getCrosstabCellDataRef(table.getVSCrosstabInfo(), tpath, row,
                                                   col, true);
   }

   /**
    * Find the data ref from the specified aggregate info.
    */
   private GroupRef findGroupRef(AggregateInfo ainfo, String name) {
      for(int i = 0; ainfo != null && i < ainfo.getGroupCount(); i++) {
         GroupRef group = ainfo.getGroup(i);

         if(group.getDataRef() != null &&
            Tool.equals(group.getDataRef().getName(), name))
         {
            return group;
         }
      }

      return null;
   }

   /**
    * Find adhoc filter list/slider from container.
    */
   private SelectionVSAssembly getFilterFromContainers(DataRef dataref, VSAssembly assembly,
                                                       List<CurrentSelectionVSAssembly> containers,
                                                       String sourceName, int sourceType)
   {
      for(CurrentSelectionVSAssembly container : containers) {
         SelectionVSAssembly filter = getFilterFromContainer(dataref, assembly, container,
                                                             sourceName, sourceType);

         if(filter != null) {
            return filter;
         }
      }

      return null;
   }

   private SelectionVSAssembly getFilterFromContainer(DataRef dataref, VSAssembly assembly,
                                                      CurrentSelectionVSAssembly container,
                                                      String sourceName, int sourceType)
   {
      Viewsheet viewsheet = container.getViewsheet();
      String[] names = container.getAssemblies();

      for(String name: names) {
         Assembly object = viewsheet.getAssembly(name);

         if(!(object instanceof SelectionListVSAssembly) &&
            !(object instanceof TimeSliderVSAssembly))
         {
            continue;
         }

         SelectionVSAssemblyInfo info = (SelectionVSAssemblyInfo) object.getInfo();
         String tableName = info.getTableName();

         if(!Tool.equals(sourceName, tableName)) {
            continue;
         }

         if(sourceType == SourceInfo.VS_ASSEMBLY && sourceType != info.getSourceType()) {
            continue;
         }

         DataRef[] dataRefs = info instanceof TimeSliderVSAssemblyInfo
            ? ((TimeSliderVSAssemblyInfo)info).getDataRefs()
            : new DataRef[] {((SelectionListVSAssemblyInfo) info).getDataRef()};

         if(dataRefs.length == 1 &&
            (Tool.equals(vsTableService.getColumnName(dataref, assembly), dataRefs[0].getName()) ||
               dataref instanceof CalculateAggregate &&
                  Tool.equals(dataref.getName(), dataRefs[0].getName())))
         {
            return (SelectionVSAssembly) object;
         }
      }

      return null;
   }

   // find the adhoc filter selection
   private SelectionVSAssembly getAdhocFilter(Viewsheet viewsheet, DataRef dataref,
                                              VSAssembly assembly,
                                              String sourceName, int sourceType)
   {
      for(Assembly object : viewsheet.getAssemblies()) {
         if(!(object instanceof SelectionListVSAssembly) &&
            !(object instanceof TimeSliderVSAssembly))
         {
            continue;
         }

         SelectionVSAssemblyInfo info = (SelectionVSAssemblyInfo) object.getInfo();

         if(!info.isAdhocFilter()) {
            continue;
         }

         String tableName = info.getTableName();

         if(!Tool.equals(sourceName, tableName)) {
            continue;
         }

         if(sourceType == SourceInfo.VS_ASSEMBLY && sourceType != info.getSourceType()) {
            continue;
         }

         DataRef[] dataRefs = info instanceof TimeSliderVSAssemblyInfo
            ? ((TimeSliderVSAssemblyInfo)info).getDataRefs()
            : new DataRef[] {((SelectionListVSAssemblyInfo) info).getDataRef()};

         if(dataRefs.length == 1 &&
            Tool.equals(vsTableService.getColumnName(dataref, assembly), dataRefs[0].getName()))
         {
            return (SelectionVSAssembly) object;
         }
      }

      return null;
   }

   /**
    * Find a container for the adhoc filter, if it exists.
    */
   public static List<CurrentSelectionVSAssembly> findContainers(Viewsheet vs, VSAssembly assembly)
   {
      boolean embedded = assembly.isEmbedded();
      List<CurrentSelectionVSAssembly> containers = new ArrayList<>();
      Assembly[] objects = vs.getAssemblies(true);

      for(Assembly object: objects) {
         if(!(object instanceof CurrentSelectionVSAssembly) ||
            !(((CurrentSelectionVSAssembly)object).isEnabled()))
         {
            continue;
         }

         CurrentSelectionVSAssembly obj = (CurrentSelectionVSAssembly) object;

         if(embedded && obj.isEmbedded() && Tool.equals(obj.getViewsheet(), assembly.getViewsheet())
            || !embedded && !obj.isEmbedded())
         {
            containers.add(obj);
         }
      }

      return containers;
   }

   /**
    * Change the assembly to an adhoc filter.
    */
   private void changeAssemblyToAdhocFilter(VSAssembly moved, VSAssembly object,
                                            VSAssembly target, Point offsetPixel)
   {
      VSAssemblyInfo info = moved.getVSAssemblyInfo();
      VSAssembly objectContainer = object != null ? object.getContainer(): null;
      VSAssembly targetContainer = target != null ? target.getContainer(): null;
      AbstractContainerVSAssembly container = targetContainer == null
         || target instanceof CurrentSelectionVSAssembly
         && targetContainer instanceof TabVSAssembly ?
         (AbstractContainerVSAssembly) target :
         (AbstractContainerVSAssembly) targetContainer;
      VSAssemblyInfo containerInfo = (VSAssemblyInfo) container.getInfo();

      if(info instanceof SelectionVSAssemblyInfo) {
         ((SelectionVSAssemblyInfo) info).setAdhocFilter(true);

         if(offsetPixel != null) {
            ((SelectionVSAssemblyInfo) info).setPixelOffset(offsetPixel);
         }
      }

      info.setZIndex(object.getVSAssemblyInfo().getZIndex() + 1);
      int width =
         containerInfo.getLayoutSize() != null && containerInfo.isVisible() ?
            containerInfo.getLayoutSize().width : 2 * AssetUtil.defw;

      if(moved instanceof TimeSliderVSAssembly) {
         moved.setPixelSize(new Dimension(width, 3 * AssetUtil.defh));
      }
      else {
         moved.setPixelSize(new Dimension(width, 6 * AssetUtil.defh));
      }

      fixFilterPosition(object, moved);
   }

   private int getFilterZIndex(VSAssembly object) {
      if(object == null) {
         return -1;
      }

      Viewsheet vs = object.getViewsheet();
      int zindex = object.getVSAssemblyInfo().getZIndex();

      if(vs.isMaxMode()) {
         if(object instanceof TableDataVSAssembly) {
            return ((TableDataVSAssembly) object).getTableDataVSAssemblyInfo().getMaxModeZIndex() + 1;
         }
         else if(object instanceof ChartVSAssembly) {
            return ((ChartVSAssemblyInfo) object.getVSAssemblyInfo()).getMaxModeZIndex() + 1;
         }

         if(vs.isEmbedded()) {
            return vs.getZIndex() + zindex + 1;
         }
      }

      return zindex + 1000;
   }

   private void saveFormats(VSAssembly moved) {
      VSAssemblyInfo info = moved.getVSAssemblyInfo();
      FormatInfo fmtInfo = info.getFormatInfo();
      VSCompositeFormat titleFormat = fmtInfo.getFormat(VSAssemblyInfo.TITLEPATH);
      VSCompositeFormat objFormat = fmtInfo.getFormat(VSAssemblyInfo.OBJECTPATH);
      VSCompositeFormat detailformat =
         fmtInfo.getFormat(new TableDataPath(-1, TableDataPath.DETAIL));

      Map<String, Object> prop = new HashMap<>();
      prop.put("_title", titleFormat.clone());
      prop.put("_object", objFormat.clone());
      prop.put("_zindex", info.getZIndex());

      if(moved.getContainer() != null) {
         prop.put("_container", moved.getContainer().getAbsoluteName());
      }

      if(detailformat != null) {
         prop.put("_detailUser", detailformat.clone());
      }

      titleFormat.getDefaultFormat().setBackgroundValue(Color.WHITE.getRGB() + "");
      objFormat.getDefaultFormat().setBackgroundValue(Color.WHITE.getRGB() + "");

      if(detailformat != null) {
         detailformat.getDefaultFormat().setBackgroundValue(Color.WHITE.getRGB() + "");
      }

      ((AbstractSelectionVSAssembly) moved).setAhFilterProperty(prop);
   }

   private void restoreFormats(VSAssembly moved) {
      Map<String, Object> prop = ((AbstractSelectionVSAssembly) moved).getAhFilterProperty();

      if(prop == null) {
         return;
      }

      VSAssemblyInfo info = moved.getVSAssemblyInfo();
      FormatInfo fmtInfo = info.getFormatInfo();
      Object zindexObj = prop.get("_zindex");

      fmtInfo.setFormat(VSAssemblyInfo.TITLEPATH, (VSCompositeFormat) prop.get("_title"));
      fmtInfo.setFormat(VSAssemblyInfo.OBJECTPATH, (VSCompositeFormat) prop.get("_object"));

      if(prop.containsKey("_detailUser")) {
         fmtInfo.setFormat(new TableDataPath(-1, TableDataPath.DETAIL),
                           (VSCompositeFormat) prop.get("_detailUser"));
      }

      if(zindexObj != null) {
         info.setZIndex((Integer) zindexObj);
      }
   }

   /**
    * Apply layout size for assembly in container.
    */
   private void applyLayoutSizeInContainer(Viewsheet vs, VSAssembly assembly,
                                           VSAssemblyInfo cinfo)
   {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      Dimension layoutsize = info.getLayoutSize();
      Dimension clayoutsize = cinfo.getLayoutSize();

      if(layoutsize == null) {
         Dimension size = vs.getPixelSize(info);
         Dimension csize = vs.getPixelSize(cinfo);
         double radioH = (double) clayoutsize.height / (double) csize.height;
         layoutsize = new Dimension(clayoutsize.width,
                                    (int) (size.height * radioH));

         if(size.height <= AssetUtil.defh) {
            int height = 1;

            if(info instanceof SelectionBaseVSAssemblyInfo) {
               height = ((SelectionBaseVSAssemblyInfo) info).getListHeight() + 1;
            }
            else if(info instanceof TimeSliderVSAssemblyInfo) {
               height = ((TimeSliderVSAssemblyInfo) info).getListHeight() + 1;
            }

            layoutsize.height = height * AssetUtil.defh;
         }

         Dimension nlayoutsize =
            new Dimension(size.width, layoutsize.height);
         info.setLayoutSize(nlayoutsize);
      }
   }

   /**
    * Get calc ref by the column name.
    */
   private CalculateRef getCalcRef(Viewsheet vs, String btableName, String columnName) {
      CalculateRef[] crefs = vs.getCalcFields(btableName);

      if(crefs != null) {
         for(CalculateRef ref: crefs) {
            if(ref.getName().equals(columnName)) {
               return ref;
            }
         }
      }

      return null;
   }

   /**
    * Find column type.
    */
   private String findColumnType(Viewsheet vs, Worksheet ws, String btableName,
                                 String columnName)
   {
      for(Assembly assembly : ws.getAssemblies()) {
         if(!(assembly instanceof TableAssembly)) {
            continue;
         }

         TableAssembly tassembly = (TableAssembly) assembly;

         if(tassembly.getName().equals(btableName)) {
            String dtype = getColumnDataType(tassembly , columnName);

            if(dtype != null) {
               return dtype;
            }
         }
      }

      CalculateRef ref = getCalcRef(vs, btableName, columnName);

      if(ref != null && ref.isBaseOnDetail()) {
         return ref.getDataType();
      }

      return null;
   }

   /**
    * Get binding column data type.
    */
   private String getColumnDataType(TableAssembly assembly, String cname) {
      ColumnSelection columns = assembly.getColumnSelection(true);
      boolean iscube = assembly instanceof CubeTableAssembly;

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef cref = (ColumnRef) columns.getAttribute(i);
         String refName = cref.getName();

         if(!iscube && refName.indexOf(".") != -1) {
            refName = refName.substring(refName.lastIndexOf(".") + 1);
         }

         if(refName.equals(cname)) {
            return cref.getDataType();
         }
      }

      return null;
   }

   /**
    * Keep the new assembly position in containers.
    */
   public static int convert(RuntimeViewsheet rvs, VSAssembly assembly, String oname,
                             String nname, CommandDispatcher dispatcher)
      throws Exception
   {
      int hint = 0;
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly cassembly = assembly.getContainer();

      //TODO container logic
      // keep the convert assembly in group
      if(cassembly instanceof GroupContainerVSAssembly) {

      }
      else if(cassembly instanceof TabVSAssembly) {

      }

      VSEventUtil.removeVSObject(rvs, oname, dispatcher);

      return hint;
   }

   private void addFilter(RuntimeViewsheet rvs, DataVSAssembly assembly, DataRef column,
                          int top, int left, boolean forceDimension, String linkUri,
                          CommandDispatcher dispatcher, String title)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      List<CurrentSelectionVSAssembly> containers = findContainers(vs, assembly);
      AssetEntry entry = vs.getRuntimeEntry();

      if(containers.isEmpty()) {
         if(entry != null) {
            String name = entry.getAlias() != null ? entry.getAlias(): entry.getName();
            MessageCommand command = new MessageCommand();
            command.setMessage(
               Catalog.getCatalog().getString("fl.action.adhocfilter.createContainer", name));
            command.setType(MessageCommand.Type.INFO);
            dispatcher.sendCommand(command);
         }

         return;
      }

      DataVSAssemblyInfo info = (DataVSAssemblyInfo) assembly.getVSAssemblyInfo();
      Point offset = null;

      if(assembly.isEmbedded()) {
         // assembly inside embedded vs is relative to the embedded vs
         Viewsheet parentVS = assembly.getViewsheet();
         Point position = info.getLayoutPosition() != null ?
            info.getLayoutPosition() : parentVS.getPixelPosition(info);
         VSAssemblyInfo vsInfo = parentVS.getVSAssemblyInfo();
         Point vsPos = vsInfo.getLayoutPosition() != null ?
            vsInfo.getLayoutPosition() : vs.getPixelPosition(vsInfo);
         offset = new Point(position.x - vsPos.x + left,
                            position.y - vsPos.y + top);
      }
      else {
         Point position = info.getLayoutPosition() != null ?
            info.getLayoutPosition() : vs.getPixelPosition(info);
         offset = new Point(position.x + left, position.y + top);
      }

      boolean aggr = !forceDimension && column instanceof XAggregateRef;
      String dtype = column.getDataType();
      String btableName = aggr ? assembly.getName() : info.getSourceInfo().getSource();
      int sourceType = column instanceof XAggregateRef ? XSourceInfo.VS_ASSEMBLY : -1;

      if(getAdhocFilter(vs, column, assembly, btableName, sourceType) != null) {
         return;
      }

      SelectionVSAssembly filter = getFilterFromContainers(column, assembly, containers,
                                                           btableName, sourceType);
      CurrentSelectionVSAssembly container = containers.get(0);

      if(filter != null) {
         VSAssemblyInfo filterInfo = filter.getVSAssemblyInfo();

         if(filterInfo instanceof SelectionVSAssemblyInfo) {
            ((SelectionVSAssemblyInfo) filterInfo).setAdhocFilter(true);
         }

         if(filterInfo instanceof SelectionBaseVSAssemblyInfo) {
            SelectionBaseVSAssemblyInfo baseVSAssemblyInfo = (SelectionBaseVSAssemblyInfo) filterInfo;
            int showType = baseVSAssemblyInfo.getShowType();

            if(showType == SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE) {
               baseVSAssemblyInfo.setShowType(SelectionVSAssemblyInfo.LIST_SHOW_TYPE);
               Dimension layoutSize = baseVSAssemblyInfo.getLayoutSize();

               if(layoutSize != null) {
                  layoutSize.height = baseVSAssemblyInfo.getListHeight() * AssetUtil.defh +
                     baseVSAssemblyInfo.getTitleHeight();
               }
            }
         }

         saveFormats(filter);
         container = containers.stream()
            .filter(c -> c.removeAssembly(filter.getName()))
            .findFirst().get();
         filterInfo.setPixelOffset(offset);

         if(assembly.isEmbedded()) {
            Viewsheet embeddedVS = assembly.getViewsheet();

            if(embeddedVS != null) {
               embeddedVS = embeddedVS.clone();
            }

            Point realPos = embeddedVS.getPixelPosition(filterInfo);
            // in vs-viewsheet.component, the bounds is subtracted from offset
            embeddedVS.removeAssembly(filterInfo.getName());
            Rectangle bounds = embeddedVS.getPreferredBounds(false, false);
            // make sure the final offset actually equals to the offset we want
            offset.x += offset.x - realPos.x + bounds.x;
            offset.y += offset.y - realPos.y + bounds.y;
            filterInfo.setPixelOffset(offset);
         }

         if(filterInfo.getLayoutPosition() != null) {
            filterInfo.setLayoutPosition(offset);
         }

         Dimension filterSize = filterInfo.getLayoutSize();

         if(filterSize == null || !container.isVisible()) {
            changeAssemblyToAdhocFilter(filter, assembly, container, offset);
            filterSize = vs.getPixelSize(filterInfo);
         }

         filter.getVSAssemblyInfo().setPixelSize(filterSize);
         filter.setZIndex(assembly.getZIndex() + 1000);
         coreLifecycleService.execute(rvs, container.getAbsoluteName(), linkUri,
                                      VSAssembly.VIEW_CHANGED, dispatcher);
         coreLifecycleService.execute(rvs, filter.getAbsoluteName(), linkUri,
                                      VSAssembly.VIEW_CHANGED, dispatcher);
         return;
      }

      String columnName = vsTableService.getColumnName(column, assembly);
      CalculateRef ref = null;

      if(sourceType != XSourceInfo.VS_ASSEMBLY) {
         ref = getCalcRef(vs, btableName, columnName);
      }

      if(ref != null && !ref.isBaseOnDetail()) {
         // TODO ERROR
         return;
      }

      Worksheet ws = vs.getBaseWorksheet();

      if(assembly.isEmbedded()) {
         vs = assembly.getViewsheet();
         ws = vs.getBaseWorksheet();
      }

      if(assembly instanceof CalcTableVSAssembly) {
         dtype = sourceType == XSourceInfo.VS_ASSEMBLY ? XSchema.DOUBLE :
            findColumnType(vs, ws, btableName, columnName);
      }

      int type = AbstractSheet.SELECTION_LIST_ASSET;

      if(XSchema.isDateType(dtype) || XSchema.isNumericType(dtype)) {
         type = AbstractSheet.TIME_SLIDER_ASSET;
      }

      String name = AssetUtil.getNextName(vs, type);
      ColumnSelection columns = new ColumnSelection();

      // Create new Ref
      AttributeRef attr = new AttributeRef(
         column.getRefType() == XSourceInfo.MODEL ? null : column.getEntity(),
         (column instanceof VSChartAggregateRef)
            ? VSUtil.normalizeAggregateName(((VSChartAggregateRef) column).getFullName(false))
            : column.getAttribute());

      attr.setRefType(column.getRefType());
      attr.setCaption(getColumnCaption(column));

      ColumnRef colref = new ColumnRef(attr);
      colref.setDataType(dtype);
      colref.setAlias(null);

      columns.addAttribute(colref);

      final List<String> btables = Collections.singletonList(btableName);
      VSAssembly vsassembly = vsTableService.createSelectionVSAssembly(vs, type, dtype,
                                                                       name, btables,
                                                                       columns, title);

      if(vsassembly instanceof TimeSliderVSAssembly) {
         ((TimeSliderVSAssembly) vsassembly).setSourceType(sourceType);

         if(sourceType == XSourceInfo.VS_ASSEMBLY) {
            ((TimeSliderVSAssembly) vsassembly).setTableName(assembly.getName());
         }
      }

      if(vsassembly == null) {
         return;
      }

      VSAssemblyInfo selectionInfo = vsassembly.getVSAssemblyInfo();
      vsassembly.initDefaultFormat();
      ((SelectionVSAssemblyInfo) selectionInfo).setCreatedByAdhoc(true);
      changeAssemblyToAdhocFilter(vsassembly, assembly, container, offset);
      saveFormats(vsassembly);

      if(assembly.isEmbedded()) {
         Point realPos = vs.getPixelPosition(selectionInfo);
         // in vs-viewsheet.component, the bounds is subtracted from offset
         Rectangle bounds = vs.getPreferredBounds(false, false);
         // make sure the final offset actually equals to the offset we want
         offset.x += offset.x - realPos.x + bounds.x;
         offset.y += offset.y - realPos.y + bounds.y;
         selectionInfo.setPixelOffset(offset);
      }

      String[] path = new String[]{column.getName()};
      TableDataPath dpath = new TableDataPath(-1, TableDataPath.DETAIL, null, path, false, false);
      FormatInfo finfo = selectionInfo.getFormatInfo();
      VSCompositeFormat fmt = finfo.getFormat(dpath);
      String format = null;
      String format_spec = null;

      if(fmt != null) {
         format = fmt.getFormat();
         format_spec = fmt.getFormatExtent();
      }

      if(format != null) {
         VSCompositeFormat cfmt = finfo.getFormat(dpath);

         if(cfmt != null) {
            cfmt.getUserDefinedFormat().setFormatValue(format);
            cfmt.getUserDefinedFormat().setFormatExtentValue(format_spec);
            finfo.setFormat(dpath, cfmt);
         }
      }

      // Bug #2972, when adhoc filter, it will not apply layout and apply scale,
      // so should apply layout size manually for new adding vsassembly.
      if(assembly.getContainer() != null) {
         VSAssemblyInfo cinfo = assembly.getContainer().getVSAssemblyInfo();

         if(!vs.getViewsheetInfo().isScaleToScreen() && cinfo.getLayoutSize() != null) {
            applyLayoutSizeInContainer(vs, vsassembly, cinfo);
            AbstractLayout.applyScaleFont(vsassembly, vs.getRScaleFont());
         }
      }

      vs.addAssembly(vsassembly, false);
      coreLifecycleService.addDeleteVSObject(rvs, vsassembly, dispatcher);
   }

   private void fixFilterPosition(VSAssembly assembly, VSAssembly filter) {
      if(assembly instanceof ChartVSAssembly &&
         ((ChartVSAssemblyInfo) assembly.getVSAssemblyInfo()).getMaxSize() != null)
      {
         Dimension maxSize = ((ChartVSAssemblyInfo) assembly.getVSAssemblyInfo()).getMaxSize();
         Point offset = filter.getPixelOffset();
         Dimension size = filter.getPixelSize();

         if(offset.x + size.width > maxSize.width) {
            offset.x = Math.max(0, maxSize.width - size.width);
         }

         if(offset.y + size.height > maxSize.height) {
            offset.y = Math.max(0, maxSize.height - size.height);
         }
      }
   }

   private final ViewsheetService viewsheetService;
   private final CoreLifecycleService coreLifecycleService;
   private final VSTableService vsTableService;
   private final GroupingService groupingService;
}
