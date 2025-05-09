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

package inetsoft.web.viewsheet.controller.table;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.report.filter.*;
import inetsoft.report.internal.Util;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.XCube;
import inetsoft.uql.XDimension;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.xmla.MemberObject;
import inetsoft.util.Tool;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.handler.ClearTableHeaderAliasHandler;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.event.table.*;
import inetsoft.web.viewsheet.handler.crosstab.CrosstabDrillHandler;
import inetsoft.web.viewsheet.service.*;

import java.awt.*;
import java.security.Principal;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public abstract class BaseTableDrillService <T extends BaseTableEvent> extends BaseTableService<T> {
   public BaseTableDrillService(CoreLifecycleService coreLifecycleService,
                                ViewsheetService viewsheetService,
                                CrosstabDrillHandler crosstabDrillHandler,
                                VSBindingService bindingFactory)
   {
      super(coreLifecycleService, viewsheetService);

      this.crosstabDrillHandler = crosstabDrillHandler;
      this.bindingFactory = bindingFactory;
   }


   protected void processDrill(String runtimeId, DrillEvent event, Principal principal,
                               CommandDispatcher dispatcher, @LinkUri String linkUri,
                               boolean refreshPage)
      throws Exception
   {
      processDrill(runtimeId, event, principal, dispatcher, linkUri, refreshPage, null,
                   DrillCellsEvent.DrillTarget.NONE, false, null, false);
   }

   protected void processDrill(String runtimeId, DrillEvent event, Principal principal,
                               CommandDispatcher dispatcher, @LinkUri String linkUri,
                               boolean refreshPage, String assemblyName,
                               DrillCellsEvent.DrillTarget drillTarget, boolean drillUp,
                               String field, boolean replace)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      long startTime = System.currentTimeMillis();
      Viewsheet vs = rvs.getViewsheet();
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();
      String name = assemblyName != null ? assemblyName : event.getAssemblyName();

      if(vs == null || box == null) {
         return;
      }

      int index = name.lastIndexOf(".");
      String sname = name;

      if(index >= 0) {
         vs = (Viewsheet) vs.getAssembly(name.substring(0, index));
         sname = name.substring(index + 1);
      }

      box.lockRead();

      try {
         // @by davidd, Stop all queries related to this Viewsheet to make way for
         // this Drill down request.
         box.cancelAllQueries();

         VSAssembly aobj = vs.getAssembly(sname);
         CrosstabVSAssembly oldTable = null;
         CrosstabVSAssemblyInfo oinfo = null;

         if(aobj instanceof CrosstabVSAssembly) {
            CrosstabVSAssembly table = (CrosstabVSAssembly) aobj;
            oldTable = table.clone();
            oinfo = oldTable.getCrosstabInfo();
            table.setLastDrillDownRequest(startTime);
            VSCrosstabInfo crosstabInfo = table.getVSCrosstabInfo();
            table.getCrosstabInfo().clearHiddenColumns();
            VSTableLens lens = getVsTableLens(box, name, table);
            XCube cube = crosstabDrillHandler.getCube(table);

            if(DrillCellsEvent.DrillTarget.CROSSTAB == drillTarget) {
               drillAllCrosstab(table, cube, drillUp);
            }
            else if(DrillCellsEvent.DrillTarget.FIELD == drillTarget) {
               crosstabDrillHandler.drillAllField(replace, drillUp, field, table,
                                                  this::allowExpandLevel, false, lens);
            }
            else {
               drillAllCells(event, rvs, name, table, oinfo, lens, dispatcher, linkUri);
            }

            crosstabInfo.fixAggregateRefs();
         }

         crosstabDrillHandler.processChange(rvs, name, oinfo, dispatcher, linkUri, refreshPage);

         if(refreshPage) {
            crosstabDrillHandler.refreshDependAssemblies(
               dispatcher, linkUri, rvs, vs, sname, aobj, oinfo);

            BindingModel binding = bindingFactory.createModel(aobj);
            SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
            dispatcher.sendCommand(bcommand);
         }
      }
      finally {
         box.unlockRead();
      }
   }

   private VSTableLens getVsTableLens(ViewsheetSandbox box, String name, CrosstabVSAssembly table)
      throws Exception
   {
      VSTableLens lens = table.getLastTableLens();

      // If no previous TableLens, then request the data directly.
      if(lens == null) {
         lens = box.getVSTableLens(name, false);
      }

      return lens;
   }

   private void drillAllCells(DrillEvent event, RuntimeViewsheet rvs, String name,
                              CrosstabVSAssembly assembly, CrosstabVSAssemblyInfo oinfo,
                              VSTableLens lens, CommandDispatcher dispatcher,
                              String linkUri)
      throws Exception
   {
      if(event.drillAll() && ChartConstants.DRILL_DOWN_OP.equals(event.getDrillOp())) {
         drillDownAllCells(name, event, rvs, assembly, oinfo, lens, linkUri, dispatcher);
         VSCrosstabInfo cinfo = assembly.getVSCrosstabInfo();
         boolean isCol = ChartConstants.DRILL_DIRECTION_X.equals(event.getDirection());
         String drillOp = event.getDrillOp();
         DataRef[] refs = isCol ? cinfo.getRuntimeColHeaders() : cinfo.getRuntimeRowHeaders();
         DataRef[] refs2 = isCol ? cinfo.getRuntimeRowHeaders() : cinfo.getRuntimeColHeaders();
         final boolean isDrillDown = ChartConstants.DRILL_DOWN_OP.equals(drillOp);

         if(isDrillDown) {
            List<DataRef> ref2List = new ArrayList<>(Arrays.asList(refs2));

            for (DataRef ref : refs) {
               if (ref2List.contains(ref)) {
                  ref2List.remove(ref);
               }
            }

            refs2 = ref2List.toArray(new DataRef[0]);

            if(isCol) {
               cinfo.setDesignRowHeaders(refs2);
            }
            else {
               cinfo.setDesignColHeaders(refs2);
            }
         }
      }
      else {
         processCrosstab(assembly, lens, event, false);
      }
   }

   private void drillDownAllCells(String name,
                                  DrillEvent event,
                                  RuntimeViewsheet rvs,
                                  CrosstabVSAssembly assembly,
                                  CrosstabVSAssemblyInfo oinfo,
                                  VSTableLens lens,
                                  String linkUri,
                                  CommandDispatcher dispatcher)
      throws Exception
   {
      int row = event.getRow();
      int col = event.getCol();
      CrosstabTree crosstabTree = assembly.getCrosstabTree();
      String path = crosstabTree.getPath(lens, row, col, true);
      Object value = lens.getObject(row, col);

      crosstabDrillHandler.drillAllField(false, false, event.getField(),
                                         assembly, this::allowExpandLevel, true, true, lens);
      Map<String, Set<String>> expandedPaths = crosstabTree.getExpandedPaths();
      Map<String, Set<String>> backupExpandedPaths = new HashMap<>();

      if(expandedPaths != null) {
         backupExpandedPaths.putAll(expandedPaths);
      }

      crosstabTree.clearDrills();
      // Optimize performance, use expanded lens to get expand status insteadof recursive do drill.
      VSTableLens expandedLens = getVsTableLens(rvs.getViewsheetSandbox(), name, assembly);

      if(expandedPaths != null) {
         expandedPaths.putAll(backupExpandedPaths);
      }

      boolean drillX = ChartConstants.DRILL_DIRECTION_X.equals(event.getDirection());
      row = drillX ? row : getExpandedRowOrCol(expandedLens, path, drillX, row, col, crosstabTree);
      col = drillX ? getExpandedRowOrCol(expandedLens, path, drillX, row, col, crosstabTree) : col;
      setExpandCell(expandedLens, assembly, drillX, row, col, path);
   }

   private void setExpandCell(VSTableLens expandedLens, CrosstabVSAssembly assembly,
                              boolean drillX, int row, int col, String path)
   {
      CrosstabTree crosstabTree = assembly.getCrosstabTree();
      VSCrosstabInfo cinfo = assembly.getVSCrosstabInfo();
      CrossTabFilter cross = Util.getCrosstab(expandedLens);
      boolean cubeType = VSUtil.getCubeType(assembly) != null &&
         cross instanceof CrossTabCubeFilter;
      Dimension span = null;

      if(cross != null) {
         span = cross.getSpan(row, col, false);
      }
      else {
         span = expandedLens.getSpan(row, col);
      }

      // drill in the x direction
      if(drillX) {
         DataRef[] colRefs = cinfo.getRuntimeColHeaders();
         int rowCount = colRefs == null ? 0 : colRefs.length;
         int colCount = col + (span == null ? 1 : (int) span.getWidth());

         for(int j = col; j < colCount; j++) {
            String matchedParentField = null;

            for(int i = 0; i < rowCount; i++) {
               String p = crosstabTree.getPath(expandedLens, i, j, true);
               String fieldName = crosstabTree.getFieldName(colRefs[i]);
               String parentField = crosstabTree.getParentField(fieldName);

               if(cubeType && !(((CrossTabCubeFilter) cross).getData0(i, j) instanceof MemberObject)) {
                  continue;
               }

               if(matchedParentField == null && p != null && p.equals(path) ||
                  parentField != null && parentField.equals(matchedParentField))
               {
                  matchedParentField = fieldName;
                  crosstabTree.setExpanded(expandedLens, i, j, true);
               }
            }
         }
      }
      // drill in the y direction
      else {
         DataRef[] rowRefs = cinfo.getPeriodRuntimeRowHeaders();
         int rowCount = row + (span == null ? 1 : (int) span.getHeight());
         int colCount = rowRefs == null ? 0 : rowRefs.length;

         for(int i = row; i < rowCount; i++) {
            String matchedParentField = null;

            for(int j = 0; j < colCount; j++) {
               String p = crosstabTree.getPath(expandedLens, i, j, true);
               String fieldName = crosstabTree.getFieldName(rowRefs[j]);
               String parentField = crosstabTree.getParentField(fieldName);

               if(cubeType && !(((CrossTabCubeFilter) cross).getData0(i, j) instanceof MemberObject)) {
                  continue;
               }

               if(matchedParentField == null && p != null && p.equals(path) ||
                  parentField != null && parentField.equals(matchedParentField))
               {
                  matchedParentField = fieldName;
                  crosstabTree.setExpanded(expandedLens, i, j, true);
               }
            }
         }
      }
   }

   private void drillAllCrosstab(CrosstabVSAssembly crosstab, XCube cube,
                                 boolean drillUp)
   {
      DataRef[] newRows;
      DataRef[] newCols;
      VSCrosstabInfo vsCrosstabInfo = crosstab.getVSCrosstabInfo();
      CrosstabTree crosstabTree = crosstab.getCrosstabTree();
      DataRef[] rows = vsCrosstabInfo.getRuntimeRowHeaders();
      DataRef[] cols = vsCrosstabInfo.getRuntimeColHeaders();

      if(drillUp) {
         newRows = removeChildRefs(crosstabTree, new ArrayList<>(Arrays.asList(rows)));
         newCols = removeChildRefs(crosstabTree, new ArrayList<>(Arrays.asList(cols)));
      }
      else {
         newRows = addChildRefs(crosstabTree, crosstab, cube, new ArrayList<>(Arrays.asList(rows)));
         newCols = addChildRefs(crosstabTree, crosstab, cube, new ArrayList<>(Arrays.asList(cols)));
      }

      if(!Tool.equalsContent(newRows, vsCrosstabInfo.getDesignRowHeaders())) {
         newRows = crosstabDrillHandler.fixDesignHeaders(newRows);
         vsCrosstabInfo.setDesignRowHeaders(newRows);
      }

      if(!Tool.equalsContent(newCols, vsCrosstabInfo.getDesignColHeaders())) {
         newCols = crosstabDrillHandler.fixDesignHeaders(newCols);
         vsCrosstabInfo.setDesignColHeaders(newCols);
      }

      crosstab.setLastStartRow(0);
      crosstabTree.clearDrills();
   }

   private DataRef[] addChildRefs(CrosstabTree crosstabTree, CrosstabVSAssembly assembly,
                                  XCube cube, ArrayList<DataRef> refs)
   {
      List<DataRef> cloneRefs = new ArrayList<>(refs);
      Set<String> childFields = refs.stream().filter(ref -> ref instanceof VSDimensionRef)
         .map(crosstabTree::getFieldName)
         .collect(Collectors.toSet());
      DataRef ref;

      for(DataRef cloneRef : cloneRefs) {
         ref = cloneRef;

         if(ref instanceof VSDimensionRef) {
            crosstabDrillHandler.drillDownChild(
               crosstabTree, assembly, cube, refs, childFields, (VSDimensionRef) ref, true, false,
               this::allowExpandLevel, false);
         }
      }

      removeDuplicates(refs, false);

      return refs.toArray(new DataRef[0]);
   }

   private DataRef[] removeChildRefs(CrosstabTree crosstabTree, List<DataRef> refs) {
      DataRef ref;

      for(int i = refs.size() - 1; i >= 0; i--) {
         ref = refs.get(i);

         if(!(ref instanceof VSDimensionRef)) {
            continue;
         }

         if(crosstabTree.isDrilled(crosstabTree.getParentField(crosstabTree.getFieldName(ref)))) {
            refs.remove(i);
         }
      }

      return refs.toArray(new DataRef[0]);
   }

   /**
    * Process expand/collapse in crosstab.
    */
   private VSDimensionRef processCrosstab(CrosstabVSAssembly table, VSTableLens lens,
                                          DrillEvent event, boolean drillAll)
   {
      CrosstabVSAssemblyInfo assemblyInfo = (CrosstabVSAssemblyInfo) table.getInfo();
      VSCrosstabInfo cinfo = table.getVSCrosstabInfo();
      CrosstabTree ctree = table.getCrosstabTree();
      String field = event.getField();
      String drillOp = event.getDrillOp();
      int row = event.getRow();
      int col = event.getCol();
      boolean isCol = ChartConstants.DRILL_DIRECTION_X.equals(event.getDirection());
      DataRef[] refs = isCol ? cinfo.getRuntimeColHeaders() : cinfo.getRuntimeRowHeaders();
      DataRef[] refs2 = isCol ? cinfo.getRuntimeRowHeaders() : cinfo.getRuntimeColHeaders();
      List<DataRef> reflist = new ArrayList<>(Arrays.asList(refs));
      VSDataRef ref = null;
      int idx = -1;
      final boolean isDrillDown = ChartConstants.DRILL_DOWN_OP.equals(drillOp);

      for(DataRef obj : reflist) {
         idx++;

         if(((VSDataRef) obj).getFullName().equals(field)) {
            ref = (VSDataRef) obj;
            break;
         }
      }

      if(!(ref instanceof VSDimensionRef)) {
         return null;
      }

      XCube cube = crosstabDrillHandler.getCube(table);
      int max0 = getMaxRanking(refs, cube, (VSDimensionRef) ref);
      int max = Math.max(max0, getMaxRanking(refs2, cube, (VSDimensionRef) ref));
      ArrayList<String> changed = new ArrayList<>();
      boolean clearAlias = false;

      if(isDrillDown) {
         VSDimensionRef nref = ctree.getChildRef(ref.getFullName());

         if(nref == null) {
            nref = getDrillDownRef((VSDimensionRef) ref, cube);
            ctree.updateChildRef(ref.getFullName(), nref);
            clearAlias = true;
         }

         if(drillAll && !allowExpandLevel(cube, nref)) {
            return null;
         }

         if(nref != null) {
            int rank0 = getDimRanking(nref, cube, null);
            int index = VSUtil.findIndex(refs, nref);

            // if the descendant of ref is not exist or is its next level,
            // only drill up the node
            if(index >= 0 || rank0 >= max || cube == null) {
               ctree.setExpanded(lens, row, col, true);
            }

            // if the descendant of ref is not its next level,
            // drill up all the node in ref
            if(index < 0 && (cube == null || rank0 != max || max0 != max)) {
               reflist.add(idx + 1, nref);
               changed.add(nref.getFullName());
               int idx2 = VSUtil.findIndex(refs2, nref);

               // make sure the next dim is not on the other direction
               if(idx2 >= 0) {
                  List<DataRef> reflist2 = new ArrayList<>(Arrays.asList(refs2));
                  reflist2.remove(idx2);
                  removeDuplicates(reflist2, false);
                  refs2 = reflist2.toArray(new DataRef[0]);

                  if(isCol) {
                     cinfo.setDesignRowHeaders(refs2);
                  }
                  else {
                     cinfo.setDesignColHeaders(refs2);
                  }
               }
            }
         }
      }
      else if(ChartConstants.DRILL_UP_OP.equals(drillOp)) {
         if(!removeAll(ctree, reflist, (VSDimensionRef) ref, cube, max)) {
            if(ctree.setExpanded(lens, row, col, false)) {
               int rank = getDimRanking((VSDimensionRef) ref, cube, null);

               for(int i = reflist.size() - 1; i >= 0; i--) {
                  if(reflist.get(i) instanceof VSDimensionRef) {
                     int rank2 = getDimRanking((VSDimensionRef) reflist.get(i),
                                               cube, (VSDimensionRef) ref);

                     if(rank2 > rank) {
                        changed.add(((VSDimensionRef) reflist.get(i)).getFullName());
                        reflist.remove(i);
                     }
                  }
               }
            }
            else {
               removeCollapseDimension(ctree, reflist, (VSDimensionRef) ref, cube, changed);
            }
         }
      }

      removeDuplicates(reflist, true);
      DataRef[] colHeaders0 = cinfo.getDesignColHeaders();
      DataRef[] rowHeaders0 = cinfo.getDesignRowHeaders();

      if(isCol) {
         cinfo.setDesignColHeaders(reflist.toArray(new DataRef[0]));
      }
      else {
         cinfo.setDesignRowHeaders(reflist.toArray(new DataRef[0]));
      }

      saveColumnInfo(assemblyInfo, lens);

      DrillInfo info = getDrillInfo(rowHeaders0, colHeaders0, field, isCol,
                                    cinfo, changed);

      if(info == null) {
         return (VSDimensionRef) ref;
      }

      syncColumnInfo(assemblyInfo, info);

      if(!isCol && clearAlias) {
         ClearTableHeaderAliasHandler.clearAlias(ref, table.getFormatInfo(), col + 1);
      }

      return (VSDimensionRef) ref;
   }

   private void syncColumnInfo(CrosstabVSAssemblyInfo assemblyInfo, DrillInfo info) {
      if(assemblyInfo.getFormatInfo() != null) {
         Map<TableDataPath, VSCompositeFormat> map = assemblyInfo.getFormatInfo().getFormatMap();
         syncPath(map, info);
         // replaced by save/restoreSemanticHeaderInfo. (60379)
         //updateMap(map, info);
      }

      if(assemblyInfo.getHyperlinkAttr() != null) {
         Map<TableDataPath, Hyperlink> map = assemblyInfo.getHyperlinkAttr().getHyperlinkMap();
         syncPath(map, info);
         //updateMap(map, info);
      }

      if(assemblyInfo.getHighlightAttr() != null) {
         Map<TableDataPath, HighlightGroup> map = assemblyInfo.getHighlightAttr().getHighlightMap();
         syncPath(map, info);
         //updateMap(map, info);
      }
   }

   // save the positional column header format/link/highlight by its correspondent field name.
   public static void saveColumnInfo(CrosstabVSAssemblyInfo assemblyInfo, TableLens lens) {
      if(assemblyInfo.getFormatInfo() != null) {
         Map<TableDataPath, VSCompositeFormat> map = assemblyInfo.getFormatInfo().getFormatMap();
         saveSemanticHeaderInfo(map, lens);
      }

      if(assemblyInfo.getHyperlinkAttr() != null) {
         Map<TableDataPath, Hyperlink> map = assemblyInfo.getHyperlinkAttr().getHyperlinkMap();
         saveSemanticHeaderInfo(map, lens);
      }

      if(assemblyInfo.getHighlightAttr() != null) {
         Map<TableDataPath, HighlightGroup> map = assemblyInfo.getHighlightAttr().getHighlightMap();
         saveSemanticHeaderInfo(map, lens);
      }
   }

   // restore the position column header format/link/highlight from its correspondent field name.
   public static boolean restoreColumnInfo(CrosstabVSAssemblyInfo assemblyInfo, TableLens lens) {
      boolean changed = false;

      if(assemblyInfo.getFormatInfo() != null) {
         Map<TableDataPath, VSCompositeFormat> map = assemblyInfo.getFormatInfo().getFormatMap();
         changed = restoreSemanticHeaderInfo(map, lens) || changed;
      }

      if(assemblyInfo.getHyperlinkAttr() != null) {
         Map<TableDataPath, Hyperlink> map = assemblyInfo.getHyperlinkAttr().getHyperlinkMap();
         changed = restoreSemanticHeaderInfo(map, lens) || changed;
      }

      if(assemblyInfo.getHighlightAttr() != null) {
         Map<TableDataPath, HighlightGroup> map = assemblyInfo.getHighlightAttr().getHighlightMap();
         changed = restoreSemanticHeaderInfo(map, lens) || changed;
      }

      return changed;
   }

   /**
    * Remove the duplicate dim (with variable) from the list.
    */
   private void removeDuplicates(List<?> reflist, boolean includeDate) {
      Set<String> groups = new HashSet<>();

      for(int i = 0; i < reflist.size(); i++) {
         VSDimensionRef ref = (VSDimensionRef) reflist.get(i);

         if(ref.isDynamic()) {
            String group = ref.isDateTime() && !includeDate ? ref.getFullName() :
               ref.getGroupColumnValue();

            if(groups.contains(group)) {
               reflist.remove(i--);
            }
            else {
               groups.add(group);
            }
         }
      }
   }

   /**
    * Remove the collapsed DataRef from headers.
    */
   private void removeCollapseDimension(CrosstabTree ctree, List<DataRef> reflist,
                                        VSDimensionRef ref, XCube cube,
                                        List<String> changed)
   {
      if(!ctree.isDrilled()) {
         return;
      }

      DataRef[] refs = reflist.toArray(new DataRef[0]);
      VSDimensionRef cref = getNextLevelRef0(ref, refs, cube);
      int nextLevelIndex = VSUtil.findIndex(refs, cref);

      if(nextLevelIndex < 0) {
         int rank = getDimRanking(ref, cube, null);
         String name = ctree.getFieldName(ref);
         boolean expand = ctree.isDrilled(name);

         for(int i = reflist.size() - 1; i >= 0; i--) {
            if(reflist.get(i) instanceof VSDimensionRef) {
               int rank2 = getDimRanking((VSDimensionRef) reflist.get(i),
                                         cube, cref);

               if(!expand && rank2 >= rank) {
                  changed.add(((VSDimensionRef) reflist.get(i)).getFullName());
                  reflist.remove(i);
               }
            }
         }
      }
      else {
         while(cref != null) {
            int idx2 = VSUtil.findIndex(refs, cref);

            if(idx2 < 0) {
               break;
            }

            String name = ctree.getFieldName(refs[idx2]);
            boolean expand = ctree.isDrilled(name);

            if(expand) {
               cref = getNextLevelRef0(cref, refs, cube);
            }
            else {
               int rank = getDimRanking(cref, cube, null);

               for(int i = reflist.size() - 1; i >= 0; i--) {
                  if(reflist.get(i) instanceof VSDimensionRef) {
                     int rank2 = getDimRanking((VSDimensionRef) reflist.get(i),
                                               cube, cref);

                     if(rank2 > rank) {
                        changed.add(((VSDimensionRef) reflist.get(i)).getFullName());
                        reflist.remove(i);
                     }
                  }
               }

               break;
            }
         }
      }
   }

   /**
    * Remove all the expandpaths of ref, and remove the next level of the ref.
    */
   private boolean removeAll(CrosstabTree ctree, List<DataRef> reflist, VSDimensionRef ref,
                             XCube cube, int max)
   {
      if(!ctree.isDrilled()) {
         return false;
      }

      DataRef[] refs = reflist.toArray(new DataRef[0]);
      VSDimensionRef cref = CrosstabTree.findChild(ref, refs, cube);
      List<DataRef> list = new ArrayList<>();
      boolean successive = true;

      // if the descendants of the ref is not successive,
      // remove the next level of the ref and all the expandpaths of ref
      while(cref != null) {
         int idx2 = VSUtil.findIndex(refs, cref);

         if(idx2 >= 0) {
            list.add(refs[idx2]);

            if(max == getDimRanking(cref, cube, null)) {
               break;
            }

            cref = getNextLevelRef0(cref, refs, cube);
         }
         else {
            successive = false;
            break;
         }
      }

      if(!successive) {
         ctree.removeDrill(ctree.getFieldName(ref));

         for(DataRef ref0 : list) {
            int idx = VSUtil.findIndex(reflist.toArray(new DataRef[0]), (VSDataRef) ref0);
            ctree.removeDrill(ctree.getFieldName(ref0));
            reflist.remove(idx);
         }

         return true;
      }

      return false;
   }

   /**
    * gets the expanded row or col
    */
   private int getExpandedRowOrCol(VSTableLens expandedLens, String path, boolean drillX,
                                   int row, int col, CrosstabTree crosstabTree)
   {
      int index = drillX ? col : row;

      if(drillX) {
         for(int i = 0; i < expandedLens.getColCount(); i++) {
            String lensPath = crosstabTree.getPath(expandedLens, row, i, true);

            if(Tool.equals(lensPath, path)) {
               index = i;
               break;
            }
         }
      }
      else {
         for(int i = 0; i < expandedLens.getRowCount(); i++) {
            String lensPath = crosstabTree.getPath(expandedLens, i, col, true);

            if(Tool.equals(lensPath, path)) {
               index = i;
               break;
            }
         }
      }

      return index;
   }

   /**
    * Get the max ranking in refs.
    */
   private int getMaxRanking(DataRef[] refs, XCube cube, VSDimensionRef ref) {
      int rank = -1;
      XDimension dim = findDimension(cube, ref);

      for(DataRef ref0 : refs) {
         if(!(ref0 instanceof VSDimensionRef)) {
            continue;
         }

         XDimension dim0 = findDimension(cube, (VSDimensionRef) ref0);

         if(!Tool.equals(dim, dim0)) {
            continue;
         }
         else if(ref.isDateTime() && ((VSDimensionRef) ref0).isDateTime() &&
            !ref.getName().equals(ref0.getName()))
         {
            continue;
         }

         rank = Math.max(rank, getDimRanking((VSDimensionRef) ref0, cube, null));
      }

      return rank;
   }

   /**
    * Get the ranking of the dimension member in the hierarchy.
    *
    * @param pref parent ref in the dimension, ignore if null.
    */
   private int getDimRanking(VSDimensionRef ref, XCube cube, VSDimensionRef pref) {
      if(cube != null) {
         String name = ref.getAttribute();
         XDimension xdim = findDimension(cube, ref);

         if(xdim != null) {
            if(pref != null) {
               XDimension pdim = findDimension(cube, pref);

               // not in the same dimension, meaningless to rank
               if(pdim != xdim) {
                  return 0;
               }
            }

            int dlevel = ref.getDateLevel();
            String mbrname = VSUtil.getDimMemberName(name, xdim);

            return VSUtil.getScope(mbrname, xdim, dlevel);
         }
      }

      if(pref == null || pref.equals(ref)) {
         int level = ref.getDateLevel();
         return GraphUtil.getDateLevelRanking(level);
      }

      return 0;
   }

   /**
    * Find the dimension.
    */
   private XDimension findDimension(XCube cube, VSDimensionRef ref) {
      if(cube == null) {
         return null;
      }

      String name = ref.getAttribute();
      int dot = name.lastIndexOf('.');
      String dimname = (dot > 0) ? name.substring(0, dot) : "";
      String mbrname = name.substring(dot + 1);
      XDimension xdim = cube.getDimension(dimname);

      if(xdim == null) {
         xdim = VSUtil.findDimension(cube, mbrname);
      }

      if(xdim == null) {
         xdim = VSUtil.findDimension(cube, name);
      }

      return xdim;
   }

   /**
    * Get next ref fro drill down.
    */
   private VSDimensionRef getDrillDownRef(VSDimensionRef ref, XCube cube) {
      return VSUtil.getNextLevelRef(ref, cube, true);
   }

   /**
    * Find the next level ref. If a child (direct or indirect) exists on the
    * ref list, use it instead of the next direct level.
    */
   private VSDimensionRef getNextLevelRef0(VSDimensionRef ref,
                                           DataRef[] refs, XCube cube)
   {
      VSDimensionRef nref = CrosstabTree.findChild(ref, refs, cube);

      if(nref == null) {
         nref = VSUtil.getNextLevelRef(ref, cube, true);
      }

      // inserted ref shouldn't be dynamic
      if(nref != null && nref.isDynamic()) {
         nref.setGroupColumnValue(nref.getDataRef().getName());
      }

      return nref;
   }

   /**
    * Synchronize the table data path in this map.
    */
   private <V> void syncPath(Map<TableDataPath, V> map, DrillInfo dinfo) {
      if(dinfo == null) {
         return;
      }

      List<TableDataPath> list = new ArrayList<>(map.keySet());

      for(TableDataPath tp : list) {
         String[] arr = tp.getPath();

         if(tp.getType() == TableDataPath.HEADER ||
            (tp.getType() == TableDataPath.GROUP_HEADER &&
               shouldSync(arr, dinfo)) ||
            (tp.getType() == TableDataPath.GRAND_TOTAL &&
               shouldSync(arr, dinfo)))
         {
            String[] arr0 = updatePath(arr, dinfo);
            TableDataPath ntp = (TableDataPath) tp.clone(arr0);

            V obj = map.get(ntp);

            // @by stephenwebster, Fix bug1393541393078
            // Existence in the map is predicated on if the user
            // has defined something.  If a user has set a format to
            // None, or removes a Hyperlink or a Highlight, then it
            // is safe to copy the value from/to the drilled level.
            // Therefore, if it does exist, then it must be user defined
            // and it should not be changed.
            if(obj == null) {
               // reverting fix for bug1420337798505, seems it is already fixed
               // by additional changes in VSUtil.pruneTableDataPaths().
               obj = map.get(tp);
               map.put(ntp, obj);
               // don't delete format for old data path, otherwise drill up and down
               // will cause cell format lost. since drilling is mostly an interactive
               // (instead of design time) action, leaving unused format should be ok. (43502)
               //map.remove(tp);
            }
         }
      }
   }

   /**
    * Sync group header cell which is not the drill date.
    */
   private boolean shouldSync(String[] arr, DrillInfo dinfo) {
      if(arr.length == 0) {
         return false;
      }

      String fld = arr[arr.length - 1];

      if(fld == null) {
         return false;
      }

      if(dinfo.isDrillDown) {
         if(fld.equals(dinfo.drilled) || dinfo.changed != null &&
            dinfo.changed.length > 0 && fld.equals(dinfo.changed[0]))
         {
            return false;
         }
      }
      else {
         if(fld.equals(dinfo.drilled)) {
            return false;
         }

         String[] changed = dinfo.changed;

         if(changed != null && changed.length > 0) {
            for(String s : changed) {
               if(fld.equals(s)) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   /**
    * Produce DrillInfo by comparing col/row headers.
    */
   private DrillInfo getDrillInfo(DataRef[] orows, DataRef[] ocols,
                                  String drill, boolean isCol, VSCrosstabInfo cinfo,
                                  ArrayList<String> changed)
   {
      DataRef[] headers = isCol ? cinfo.getDesignColHeaders() :
         cinfo.getDesignRowHeaders();
      DataRef[] headers0 = isCol ? ocols : orows;

      if(headers0 == null || headers == null ||
         headers0.length == headers.length || drill == null)
      {
         return null;
      }

      DrillInfo dinfo = new DrillInfo();
      dinfo.isDrillDown = headers0.length < headers.length;
      int changeLen = dinfo.isDrillDown ? headers.length - headers0.length :
         headers0.length - headers.length;
      DataRef[] nrows = cinfo.getDesignRowHeaders();
      DataRef[] ncols = cinfo.getDesignColHeaders();
      String[] dimNames = dinfo.isDrillDown ? getDesignDimNames(nrows, ncols) :
         getDesignDimNames(orows, ocols);
      ArrayList<String> list = new ArrayList<>();
      DataRef[] change = dinfo.isDrillDown ? (isCol ? ncols : nrows) :
         (isCol ? ocols : orows);
      int rowLen = dinfo.isDrillDown ? nrows.length : orows.length;
      // @by ChrisSpagnoli bug1407188995034 2015-1-29
      dinfo.rowLen = rowLen;

      for(int i = 0; i < change.length; i++) {
         if(VSUtil.isSameCol(drill, change[i])) {
            int idx = isCol ? i + rowLen : i;
            dinfo.drilled = dimNames[idx];

            for(int j = 0; j < change.length; j++) {
               String changeName = change[j] instanceof VSDimensionRef ?
                  ((VSDimensionRef) change[j]).getFullName() :
                  change[j].getName();

               if(changed.contains(changeName)) {
                  idx = isCol ? j + rowLen : j;

                  if(list.size() < changeLen) {
                     list.add(dimNames[idx]);
                     // @by ChrisSpagnoli bug1407188995034 2015-1-29
                     dinfo.changedIdx.add(idx);
                  }
               }
            }

            dinfo.changed = list.toArray(new String[0]);
            break;
         }
      }

      return dinfo;
   }

   private boolean allowExpandLevel(XCube cube, VSDimensionRef childRef) {
      if(crosstabDrillHandler.drillChildEnabled(cube, childRef)
         && (XSchema.DATE.equals(childRef.getDataType()) || XSchema.TIME_INSTANT.equals(childRef.getDataType())))
      {
         int expandAllLevel = DateRangeRef.getDateRangeOption(
            SreeEnv.getProperty("crosstab.dateTime.expandAll.level"));
         int level = childRef.getDateLevel();

         if(expandAllLevel != -1 && level > expandAllLevel) {
            return false;
         }
      }

      return true;
   }

   /**
    * Save the dime names in the path for every dimension.
    */
   private String[] getDesignDimNames(DataRef[] row, DataRef[] col) {
      DataRef[] refs = new DataRef[row.length + col.length];
      System.arraycopy(row, 0, refs, 0, row.length);
      System.arraycopy(col, 0, refs, row.length, col.length);

      return VSUtil.buildName(refs, false);
   }

   /**
    * Update table data path by DrillInfo.
    */
   private String[] updatePath(String[] arr, DrillInfo dinfo) {
      String drill = dinfo.drilled;

      if(drill == null) {
         return arr;
      }

      List<String> list = new ArrayList<>(Arrays.asList(arr));
      int idx = -1;

      for(int i = 0; i < list.size(); i++) {
         if(drill.equals(list.get(i))) {
            idx = i;
            break;
         }
      }

      if(idx >= 0) {
         String[] name = dinfo.changed;

         if(dinfo.isDrillDown && name.length != 0) {
            boolean found = false;

            for(int i = idx + 1; i < list.size(); i++) {
               if(name[0].equals(list.get(i))) {
                  found = true;
                  break;
               }
            }

            // already in path? no update it
            if(!found) {
               list.add(idx + 1, name[0]);
            }
         }
         else {
            for(String s : name) {
               list.remove(s);
            }
         }
      }

      return list.toArray(new String[0]);
   }

   // @by ChrisSpagnoli bug1407188995034 2015-1-29

   /**
    * Update the map of formats, which is indexed by TableDataPath
    * Do this by swapping the "collapsed" cells formats to the end,
    * and sliding the remaining cell formats up.
    *
    * @param map   The Map of <TableDataPath, VSCompositeFormat>.
    * @param dinfo The DrillInfo containing List changedIdx<int> and rowLen.
    */
   private <V> void updateMap(Map<TableDataPath, V> map, DrillInfo dinfo) {
      if(map.isEmpty()) {
         return;
      }

      if(dinfo.isDrillDown) {
         for(int i = dinfo.changedIdx.size() - 1; i >= 0; i--) {
            for(int j = dinfo.rowLen - 1; j > dinfo.changedIdx.get(0); j--) {
               swapMapFormats(map, j, j - 1);
            }
         }
      }
      else {
         for(int i = 0; i < dinfo.changedIdx.size(); i++) {
            for(int j = dinfo.changedIdx.get(0); j < dinfo.rowLen - 1; j++) {
               swapMapFormats(map, j, j + 1);
            }
         }
      }
   }

   /**
    * Swap two formats in the map of formats.
    *
    * @param map  The Map of <TableDataPath, VSCompositeFormat>.
    * @param from The column number of one end of the swap.
    * @param to   The column number of the other end of the swap.
    */
   private <V> void swapMapFormats(Map<TableDataPath, V> map, final int from, final int to) {
      final String pathFrom = "Cell [0," + from + "]";
      final String pathTo = "Cell [0," + to + "]";
      swapMapFormats(map, pathFrom, pathTo);
   }

   /**
    * Swap two formats in the map of formats.
    *
    * @param map      The Map of <TableDataPath, VSCompositeFormat>.
    * @param pathFrom The String containing the path of one end of the swap.
    * @param pathTo   The String containing the path of the other end of the swap.
    */
   private <V> void swapMapFormats(Map<TableDataPath, V> map,
                                   final String pathFrom, final String pathTo)
   {
      TableDataPath[] keyObjs = map.keySet().toArray(new TableDataPath[0]);

      for(TableDataPath keyTdp : keyObjs) {
         if(keyTdp.getPath().length > 0) {
            String[] pathsFrom = { pathFrom };
            String[] pathsTo = { pathTo };
            TableDataPath tdpFrom = new TableDataPath(
               keyTdp.getLevel(), keyTdp.getType(), keyTdp.getDataType(), pathsFrom,
               keyTdp.isRow(), keyTdp.isCol());
            TableDataPath tdpTo = new TableDataPath(
               keyTdp.getLevel(), keyTdp.getType(), keyTdp.getDataType(), pathsTo,
               keyTdp.isRow(), keyTdp.isCol());
            swapMapFormats(map, tdpFrom, tdpTo);
            break;
         }
      }
   }

   // when drill is changed, we copy the current positional setting (format/hyperlink/highlight)
   // to the corresponding field, to be restored when the drill changes again. (60379)
   private static <V> void saveSemanticHeaderInfo(Map<TableDataPath, V> map, TableLens lens) {
      for(int i = 0; i < lens.getColCount(); i++) {
         TableDataPath path = lens.getDescriptor().getCellDataPath(0, i);
         TableDataPath spath = getSemanticHeaderPath(i, lens);

         if(spath != null) {
            map.put(spath, map.get(path));
         }
      }
   }

   // restore the column header settings saved in saveSemanticHeaderInfo to corresponding
   // positional header cells.
   private static <V> boolean restoreSemanticHeaderInfo(Map<TableDataPath, V> map, TableLens lens) {
      if(lens == null || map == null || lens.getDescriptor() == null) {
         return false;
      }

      boolean changed = false;

      for(int i = 0; i < lens.getColCount(); i++) {
         TableDataPath path = lens.getDescriptor().getCellDataPath(0, i);
         TableDataPath spath = getSemanticHeaderPath(i, lens);

         if(spath != null && map.containsKey(spath) && !Objects.equals(map.get(path), map.get(spath))) {
            map.put(path, map.get(spath));
            changed = true;
         }
      }

      return changed;
   }

   // get a column header path that is tied to the corresponding field (instead of the
   // header cell position, Cell[0, 1]). this is used to save the info so when the
   // column shifts during drill up/down, the information is tied to the 'same'
   // cell instead of shifted to an unrelated cell.
   private static TableDataPath getSemanticHeaderPath(int col, TableLens table) {
      TableDataPath path = table.getDescriptor().getColDataPath(col);

      if(path == null) {
         return null;
      }

      switch(path.getType()) {
      case TableDataPath.GROUP_HEADER:
         if(path.getPath().length > 0) {
            return new TableDataPath(0, TableDataPath.HEADER, XSchema.STRING,
                                     new String[] { "_CROSSTAB_",
                                                    path.getPath()[path.getPath().length - 1]});
         }
         break;
      case TableDataPath.SUMMARY_HEADER:
         return new TableDataPath(0, TableDataPath.HEADER, XSchema.STRING,
                                  new String[] { "_CROSSTAB_", "_SUMMARY_HEADER_" });
      }

      return null;
   }

   // @by ChrisSpagnoli bug1407188995034 2015-1-29

   /**
    * Swap two formats in the map of formats.
    *
    * @param map     The Map of <TableDataPath, VSCompositeFormat>.
    * @param tdpFrom The TableDataPath of one end of the swap.
    * @param tdpTo   The TableDataPath of the other end of the swap.
    */
   private <V> void swapMapFormats(Map<TableDataPath, V> map, final TableDataPath tdpFrom,
                                   final TableDataPath tdpTo)
   {
      V vscfFrom = null;
      V vscfTo = null;

      if(map.get(tdpFrom) instanceof VSCompositeFormat) {
         vscfFrom = map.get(tdpFrom);
      }

      if(map.get(tdpTo) instanceof VSCompositeFormat) {
         vscfTo = map.get(tdpTo);
      }

      if(vscfFrom == null) {
         map.remove(tdpTo);
      }
      else {
         map.put(tdpTo, vscfFrom);
      }

      if(vscfTo == null) {
         map.remove(tdpFrom);
      }
      else {
         map.put(tdpFrom, vscfTo);
      }
   }

   private static class DrillInfo {
      private boolean isDrillDown;
      private String drilled;
      private String[] changed;
      private final ArrayList<Integer> changedIdx = new ArrayList<>();
      private int rowLen;

      @Override
      public String toString() {
         return "DrillInfo[" + isDrillDown + "," + drilled +
            ",[" + Arrays.toString(changed) + "],[" + changedIdx + "]," + rowLen;
      }
   }

   protected final VSBindingService bindingFactory;
   protected final CrosstabDrillHandler crosstabDrillHandler;
}
