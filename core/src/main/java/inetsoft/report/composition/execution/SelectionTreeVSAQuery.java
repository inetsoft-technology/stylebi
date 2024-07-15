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
package inetsoft.report.composition.execution;

import inetsoft.report.*;
import inetsoft.report.internal.Util;
import inetsoft.report.script.viewsheet.SelectionTreeVSAScriptable;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.xmla.MemberObject;
import inetsoft.util.Tool;

import java.text.Format;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SelectionTreeVSAQuery, the selection tree viewsheet assembly query.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class SelectionTreeVSAQuery extends AbstractSelectionVSAQuery {
   /**
    * Create a selection tree viewsheet assembly query.
    * @param box the specified viewsheet sandbox.
    * @param vname the specified viewsheet assembly to be processed.
    */
   public static SelectionTreeVSAQuery createVSAQuery(ViewsheetSandbox box, String vname) {
      Viewsheet vs = box.getViewsheet();
      SelectionTreeVSAssembly vassembly = (SelectionTreeVSAssembly) vs.getAssembly(vname);
      return vassembly.isIDMode() ? new SelectionTreeVSAQuery2(box, vname)
         : new SelectionTreeVSAQuery(box, vname);
   }

   /**
    * Create a selection tree viewsheet assembly query.
    * @param box the specified viewsheet sandbox.
    * @param vname the specified viewsheet assembly to be processed.
    */
   public SelectionTreeVSAQuery(ViewsheetSandbox box, String vname) {
      super(box, vname);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected void refreshSelectionValue0(
      XTable data, Map<String, Map<String, Collection<Object>>> allSelections,
      Map<String, Map<String, Collection<Object>>> appliedSelections,
      Map<String, Set<Object>> values,
      SelectionMeasureAggregation measureAggregation) throws Exception
   {
      SelectionTreeVSAssembly assembly = (SelectionTreeVSAssembly) getAssembly();
      SelectionTreeVSAssemblyInfo sinfo = (SelectionTreeVSAssemblyInfo) assembly.getInfo();
      final DataRef[] refs = assembly.getDataRefs();

      if(data.getColCount() != refs.length || !data.moreRows(1) || refs.length == 0) {
         assembly.setCompositeSelectionValue(null);
         assembly.setStateCompositeSelectionValue(null);
         return;
      }

      boolean associationDefined = box.isAssociationEnabled() &&
         isAssociationDefined(assembly, allSelections);
      final Map<String, Collection<Object>> intersectionAllSelections =
         getColumnMapIntersection(assembly, allSelections);
      final Map<String, Collection<Object>> intersectionAppliedSelections =
         getColumnMapIntersection(assembly, appliedSelections);

      CompositeSelectionValue cselection = createNode(refs[0].getDataType());
      final CompositeSelectionValue cselection2 = createNode(refs[0].getDataType());
      boolean single = sinfo.isSingleSelection();
      boolean[] firsts = new boolean[refs.length];
      Arrays.fill(firsts, true);

      String selkey = VSUtil.getSelectionKey(refs);
      Set<Object> vset = values.get(selkey);
      Object[] larr = null;

      final List<Set<Object>> allSelPathsList = findCompatibleSelectionPaths(assembly, allSelections);
      final boolean allSelPathsEmpty =
         allSelPathsList.stream().allMatch((selpaths) -> selpaths.size() == 0);
      vset = vset == null ? new HashSet<>() : vset;

      // parent selection lists (SelectionList)
      Vector<SelectionList> plists = new Vector<>();
      // nodes from root (String)
      Vector<String> pnodes = new Vector<>();
      // nodes from root (MemberObject)
      Vector<Object> mnodes = new Vector<>();
      // parent node state (Integer)
      Vector<Integer> pstates = new Vector<>();
      plists.add(cselection.getSelectionList());
      pstates.add(0);

      boolean suppressBlank = sinfo.isSuppressBlankValue();
      boolean hasBlank = false;
      // measure range
      final Map<Object, Object> mvalues = measureAggregation.getMeasures();
      final double mmin = measureAggregation.getMin();
      final double mmax = measureAggregation.getMax();

      cselection.getSelectionList().setMeasureMin(mmin);
      cselection.getSelectionList().setMeasureMax(mmax);
      Set<Object> sset =
         (Set<Object>) intersectionAllSelections.getOrDefault(refs[0].getName(), new HashSet<>());

      // column may not always be in the order of refs (48852).
      ColumnIndexMap columnIndexMap = new ColumnIndexMap(data, true);
      int[] refCols = Arrays.stream(refs)
                            .mapToInt(ref -> Util.findColumn(columnIndexMap, ref))
                            .toArray();
      boolean findSelectedRoot = false;
      boolean rootSingle = sinfo.isSingleSelectionLevel(0);
      boolean emptySelection = true;

      // main loop, mark association status
      for(int rowIdx = 1; data.moreRows(rowIdx); rowIdx++) {
         Object[] row = new Object[refs.length];
         Format[] fmt = new Format[refs.length];
         // optimization, avoid checking if not suppressing
         // boolean blank = !suppressBlank;

         for(int refIdx = 0; refIdx < row.length; refIdx++) {
            Object obj = data.getObject(rowIdx, refCols[refIdx]);

            if(suppressBlank) {
               hasBlank = hasBlank || obj == null || obj.equals("");
            }

            // blank = blank || obj == null || obj.equals("");
            row[refIdx] = SelectionSet.normalize(obj);

            if(data instanceof TableLens) {
               fmt[refIdx] = ((TableLens) data).getDefaultFormat(rowIdx, refIdx);
            }
         }

         /*
         if(blank && suppressBlank) {
            continue;
         }
         */

         int level = -1; // the new level this row starts on

         for(int refIdx = 0; refIdx < row.length; refIdx++) {
            boolean eq = larr != null && Tool.equals(larr[refIdx], row[refIdx]);

            if(!eq) {
               level = refIdx;
               break;
            }
         }

         // the level is the parent position
         if(level == -1) {
            continue;
         }

         plists.setSize(level + 1);
         pstates.setSize(level + 1);
         pnodes.setSize(level);
         mnodes.setSize(level);

         // create nodes for the levels at and below this level
         for(int refIdx = level; refIdx < row.length; refIdx++) {
            Object obj = row[refIdx];
            // association is done with a tuple (path from root) so nodes under
            // different branches would not be confused
            Object tuple = new SelectionSet.Tuple(row, refIdx + 1);
            int refType = refs[refIdx].getRefType();
            refType = (refType & DataRef.CUBE_TIME_DIMENSION) ==
               DataRef.CUBE_TIME_DIMENSION ? DataRef.CUBE_DIMENSION : refType;
            String label = VSCubeTableLens.getDisplayValue(obj, refType);
            String value = obj == null ? null : Tool.getDataString(obj);
            SelectionValue svalue;
            String dtype = obj == null ? null : Tool.getDataType(obj.getClass());
            SelectionList slist = new SelectionList();

            if(refIdx == refs.length - 1) {
               svalue = new SelectionValue(label, value);
            }
            else {
               slist.setDataType(refs[refIdx + 1].getDataType());
               slist.setComparator(getComparator(data, refs[refIdx + 1]));
               svalue = new CompositeSelectionValue(label, value);
               ((CompositeSelectionValue) svalue).setSelectionList(slist);
            }

            Object mvalue = mvalues.get(SelectionSet.normalize(tuple));

            if(mvalue == null && obj instanceof MemberObject) {
               mvalue = mvalues.get(((MemberObject) obj).getCaption());

               if(mvalue == null && tuple instanceof SelectionSet.Tuple) {
                  SelectionSet.Tuple t = (SelectionSet.Tuple) tuple;
                  Object[] nvalues = new Object[t.size()];

                  for(int i = 0; i < t.size(); i++) {
                     nvalues[i] = t.get(i) instanceof MemberObject ?
                        ((MemberObject) t.get(i)).getCaption() : t.get(i);
                  }

                  SelectionSet.Tuple nt = new SelectionSet.Tuple(nvalues, t.size());
                  mvalue = mvalues.get(nt);
               }
            }

            if(mvalue != null) {
               svalue.setMeasureValue(SelectionListVSAQuery.getMValue(mvalue, mmin, mmax));
               svalue.setMeasureLabel(SelectionListVSAQuery.getMeasureLabel(
                  mvalue, assembly.getFormatInfo(), refIdx, locale));
            }

            svalue.setDefaultFormat(fmt[refIdx]);

            // parent selection list
            SelectionList plist = plists.get(refIdx);
            // parent selection state
            int pstate = pstates.get(refIdx);
            // nodes from root to this value
            String[] nodes = new String[refIdx + 1];

            pnodes.copyInto(nodes);
            nodes[nodes.length - 1] = value;

            // nodes from root to this value (MemberObject)
            Object[] nodes0 = new Object[refIdx + 1];

            mnodes.copyInto(nodes0);
            nodes0[nodes0.length - 1] = obj;

            final String selpath = Tool.arrayToString(nodes, "/", true);
            int state = 0;

            // A node can be selected if it is a selected member of the first column of the tree,
            // or if the selection paths contains a path to this node.
            final boolean nodeSelected = refIdx == 0 && sset.contains(obj) ||
               refIdx > 0 && (allSelPathsList.stream()
                  .anyMatch((selPaths) -> selPaths.contains(selpath) || selPaths.contains(nodes0)));

            if(nodeSelected && (refIdx == 0 && (!rootSingle || !findSelectedRoot) ||
               refIdx != 0 && ancestorsSingleLevelSelected(sinfo, refIdx, pstates)))
            {
               state = state | SelectionValue.STATE_SELECTED;
               findSelectedRoot = true;
            }

            if((pstate & SelectionValue.STATE_EXCLUDED) != 0) {
               state = setExcluded(sinfo.isSingleSelectionLevel(refIdx), state);
            }
            else if(vset.contains(tuple)) {
               state = state | SelectionValue.STATE_COMPATIBLE;

               if(allSelPathsEmpty && associationDefined && !sinfo.isSingleSelectionLevel(refIdx)) {
                  state = state | SelectionValue.STATE_INCLUDED;
               }
            }
            else if(associationDefined) {
               state = setExcluded(sinfo.isSingleSelectionLevel(refIdx), state);
            }

            final String[] ppaths = new String[pnodes.size()];
            pnodes.copyInto(ppaths);
            final String ppath = pnodes.size() == 0 ? "" :
               Tool.arrayToString(ppaths, "/") + "/CHILD_SELECTION_EXISTS";
            final boolean siblingSelected = allSelPathsList.stream()
               .anyMatch((selPaths) -> selPaths.contains(ppath));

            // if parent is included and this item is not included, this
            // item should be shown as included (feature1301061387546)
            if(!siblingSelected && !sinfo.isSingleSelectionLevel(refIdx) &&
               (state & SelectionValue.STATE_EXCLUDED) == 0 &&
               (pstate & SelectionValue.STATE_EXCLUDED) == 0 &&
               ((pstate & SelectionValue.STATE_SELECTED) != 0 ||
                (pstate & SelectionValue.STATE_INCLUDED) != 0))
            {
               state = state | SelectionValue.STATE_INCLUDED;
            }

            svalue.setState(state);
            svalue.setLevel(refIdx);

            if(refIdx < row.length - 1) {
               plists.add(slist);
               pnodes.add(value);
               mnodes.add(obj);
               pstates.add(state);
            }

            // if selected, add to the state selection list
            if(svalue.isSelected()) {
               SelectionList selection = cselection2.getSelectionList();
               int slevel = -1;

               for(int k = 0; k < refIdx; k++) {
                  int count = selection.getSelectionValueCount();

                  if(count > 0) {
                     SelectionValue val2 = selection.getSelectionValue(count - 1);
                     selection = ((CompositeSelectionValue) val2).getSelectionList();
                     slevel++;
                  }
               }

               if(slevel == refIdx - 1) {
                  if(firsts[refIdx] && dtype != null) {
                     selection.setDataType(dtype);
                     selection.setComparator(getComparator(data, refs[refIdx]));
                  }

                  selection.addSelectionValue((SelectionValue) svalue.clone());
               }
            }

            if(emptySelection && (svalue.isSelected() || svalue.isExcluded() || svalue.isIncluded())) {
               emptySelection = false;
            }

            plist.addSelectionValue(svalue);

            if(firsts[refIdx] && dtype != null) {
               firsts[refIdx] = false;
               plist.setDataType(dtype);
               plist.setComparator(getComparator(data, refs[refIdx]));
            }
         }

         larr = row;
      }

      XTable table = data;

      if(data instanceof RealtimeTableMetaData.ColumnTable) {
         table = ((RealtimeTableMetaData.ColumnTable) data).getTable();
      }
      else if(data instanceof RealtimeTableMetaData.ColumnsTable) {
         table = ((RealtimeTableMetaData.ColumnsTable) data).getTable();
      }

      checkMaxRowLimit(table);
      boolean newSingleSelection = false;
      boolean openVS = Boolean.TRUE.equals(VSUtil.OPEN_VIEWSHEET.get());

      if(!emptySelection && single && !allSelections.isEmpty()) {
         newSingleSelection = true;

         SelectionTreeStateProcessor processor = new SelectionTreeStateProcessor(
            (SelectionTreeVSAssembly) getAssembly());
         processor.refreshSingleSelectionState(cselection, cselection2, associationDefined, false);
      }

      // this may be cause performance problem, but in most cases,
      // use will not add a selection tree which contains so many selections
      processComplete(cselection.getSelectionList(), suppressBlank && hasBlank);
      int sortType = getSortType((SelectionBaseVSAssemblyInfo) assembly.getInfo());
      cselection.sort(sortType);

      if(openVS && sinfo.isSelectFirstItem() && cselection2.getValueCount() <= 0) {
         if(SelectionVSUtil.selectSelectionTreeFirstItem(sinfo, cselection, cselection2)) {
            newSingleSelection = true;
         }
      }

      if(box.isRuntime()) {
         sinfo.setUsingMetaData(false);
      }

      // force included flag on compatible children if parent is selected.
      // this must be done after the states have been set so the selection
      // paths reflect the new states.
      String selectionPathKey = SelectionVSAssembly.SELECTION_PATH + selkey;
      Set<Object> selPaths = (Set<Object>) intersectionAppliedSelections.
         getOrDefault(selectionPathKey, new HashSet<>());
      selPaths = assembly.getSelectedPaths(cselection2, refs, selPaths, selectionPathKey, true);
      markIncluded(cselection, cselection2, cselection,
         selPaths, 0, "", associationDefined);
      assembly.setCompositeSelectionValue(cselection);
      assembly.setStateCompositeSelectionValue(cselection2);

      syncSelections(assembly, appliedSelections, true);

      if(newSingleSelection) {
         syncSelections(assembly, allSelections, false);
      }
   }

   private boolean ancestorsSingleLevelSelected(SelectionTreeVSAssemblyInfo sinfo, int refIdx, Vector<Integer> pstates)
   {
      for(int i = 0; i < refIdx; i++) {
         if(sinfo.isSingleSelectionLevel(i) && pstates.get(i + 1) != null &&
            (pstates.get(i + 1) & SelectionValue.STATE_SELECTED) != SelectionValue.STATE_SELECTED)
         {
            return false;
         }
      }

      return true;
   }

   private void processComplete(SelectionList list, boolean noBlank) {
      for(int i = list.getSelectionValueCount() - 1; i >= 0; i--) {
         SelectionValue value = list.getSelectionValue(i);

         if(noBlank) {
            value = syncBlank(value);

            if(value == null) {
               list.removeSelectionValue(i);
            }
            else {
               list.setSelectionValue(i, value);
            }
         }

         if(value instanceof CompositeSelectionValue) {
            processComplete(((CompositeSelectionValue) value).getSelectionList(), noBlank);
         }
      }

      list.complete();
   }

   private int setExcluded(boolean singleLevel, int state) {
      return singleLevel ? SelectionValue.STATE_EXCLUDED : state | SelectionValue.STATE_EXCLUDED;
   }

   private SelectionValue syncBlank(SelectionValue value) {
      if(isBlank(value)) {
         return null;
      }

      if(value instanceof CompositeSelectionValue) {
         SelectionList list = ((CompositeSelectionValue) value).getSelectionList();

         for(int i = list.getSelectionValueCount() - 1; i >= 0; i--) {
            SelectionValue child = list.getSelectionValue(i);

            if(isBlank(child)) {
               list.removeSelectionValue(i);
            }
         }

         if(list.getSelectionValueCount() <= 0) {
            SelectionValue ovalue = value;
            value = new SelectionValue(ovalue.getLabel(), ovalue.getValue());
            value.setState(ovalue.getState());
            value.setLevel(ovalue.getLevel());
            value.setFormat(ovalue.getFormat());
            value.setDefaultFormat(ovalue.getDefaultFormat());
            value.setMeasureLabel(ovalue.getMeasureLabel());
            value.setMeasureValue(ovalue.getMeasureValue());
         }
      }

      return value;
   }

   private boolean isBlank(SelectionValue value) {
      return value.isNull() || value.getValue() == null ||
             "".equals(value.getValue());
   }

   /**
    * Mark a child as included if the parent is selected or included, and no
    * sibling is selected (and applied).
    */
   private void markIncluded(CompositeSelectionValue root,
                             CompositeSelectionValue stateSelection,
                             SelectionValue cval, Set<Object> selPaths,
                             int pstate,
                             String ppath,
                             boolean associationDefined)
   {
      SelectionTreeVSAssembly assembly =
         (SelectionTreeVSAssembly) getAssembly();
      SelectionTreeVSAssemblyInfo sinfo =
         (SelectionTreeVSAssemblyInfo) assembly.getInfo();
      int state = cval.getState();

      if((state & SelectionValue.STATE_SELECTED) == 0 && associationDefined) {
         if((state & SelectionValue.STATE_COMPATIBLE) != 0 &&
            ((pstate & SelectionValue.STATE_SELECTED) != 0 ||
             (pstate & SelectionValue.STATE_INCLUDED) != 0 ||
             ppath.length() == 0) && !existChild(root, cval, selPaths, ppath, sinfo))
         {
            if(sinfo.isSingleSelectionLevel(cval.getLevel())) {
               state = state | SelectionValue.STATE_SELECTED;
               selPaths.add(ppath + "/CHILD_SELECTION_EXISTS");
            }
            else {
               state = state | SelectionValue.STATE_INCLUDED;
            }

            cval.setState(state);

            // add selected node to state selection.
            if(cval.isSelected()) {
               selectedValue(root, stateSelection, ppath, cval);
            }
         }
      }

      if(cval instanceof CompositeSelectionValue) {
         SelectionList slist = ((CompositeSelectionValue) cval).getSelectionList();
         String val = cval.getValue();
         ppath = (ppath.length() == 0) ? (val == null ? "" : val) : ppath + "/" + val;

         for(int i = 0; i < slist.getSelectionValueCount(); i++) {
            markIncluded(root, stateSelection, slist.getSelectionValue(i), selPaths, state, ppath,
               associationDefined);
         }
      }
   }

   /**
    * Check if the target selection value exist selected child.
    * @param root       the root CompositeSelectionValue.
    * @param cval       the target selectoin value.
    * @param selPaths   the current selectoin paths.
    * @param ppath      the parent path of the target value.
    * @param sinfo      the current tree info.
    * @return
    */
   private boolean existChild(CompositeSelectionValue root, SelectionValue cval,
                              Set<Object> selPaths, String ppath,
                              SelectionTreeVSAssemblyInfo sinfo)
   {
      boolean existChild = selPaths.contains(ppath + "/CHILD_SELECTION_EXISTS");
      boolean single = sinfo.isSingleSelectionLevel(cval.getLevel());

      if(!existChild && single) {
         SelectionValue pvalue = "".equals(ppath) ? root : getSelectionValue(root, ppath);

         if(pvalue instanceof CompositeSelectionValue) {
            List<SelectionValue> list = ((CompositeSelectionValue) pvalue).getSelectionValues(cval.getLevel(),
                  SelectionValue.STATE_SELECTED, SelectionValue.STATE_EXCLUDED);
            existChild = list.size() > 0;
         }
      }

      return existChild;
   }

   /**
    * Get selection value by path.
    * @param root  the root CompositeSelectionValue.
    * @param path  the target path.
    * @return
    */
   private SelectionValue getSelectionValue(CompositeSelectionValue root, String path) {
      SelectionList list = root.getSelectionList();
      SelectionValue value = null;
      String[] paths = path.split("/");

      for(int i = 0; i < paths.length; i++) {
         value = list.findValue(paths[i], false);

         if(value == null) {
            break;
         }

         if(value instanceof CompositeSelectionValue) {
            list = ((CompositeSelectionValue) value).getSelectionList();
         }
      }

      return value;
   }

   /**
    * Add the target selection value to state selection.
    * @param root             the composite selection value of the tree.
    * @param stateSelection   the state composite selection value of the tree.
    * @param ppath            the parent path of the target selection value
    * @param cval             the target selection value.
    */
   private void selectedValue(CompositeSelectionValue root,
                              CompositeSelectionValue stateSelection,
                              String ppath, SelectionValue cval)
   {
      SelectionList list = root.getSelectionList();
      SelectionList stateList = stateSelection.getSelectionList();
      SelectionValue parent = null;
      String[] paths = ppath.split("/");

      for(int i = 0; i < paths.length; i++) {
         parent = list.findValue(paths[i]);

         if(parent == null) {
            break;
         }

         SelectionValue val = stateList.findValue(paths[i]);

         if(val == null) {
            val = (SelectionValue) parent.clone();
            stateList.addSelectionValue(val);

            if(val instanceof CompositeSelectionValue) {
               ((CompositeSelectionValue) val).getSelectionList().clear();
            }
         }

         if(parent instanceof CompositeSelectionValue) {
            list = ((CompositeSelectionValue) parent).getSelectionList();
            stateList = ((CompositeSelectionValue) val).getSelectionList();
         }
      }

      if(parent != null) {
         stateList.addSelectionValue((SelectionValue) cval.clone());
      }
   }

   /**
    * Refresh the view selection value.
    */
   @Override
   protected void refreshViewSelectionValue0() throws Exception {
      super.refreshViewSelectionValue0();

      SelectionTreeVSAssembly assembly =
         (SelectionTreeVSAssembly) getAssembly();
      SelectionTreeVSAssemblyInfo sinfo =
         (SelectionTreeVSAssemblyInfo) assembly.getInfo();

      if(sinfo.getMode() == SelectionTreeVSAssemblyInfo.ID) {
         return;
      }

      CompositeSelectionValue cval = assembly.getCompositeSelectionValue();
      ViewsheetScope scope = box.getScope();

      final SelectionTreeVSAScriptable scriptable =
         (SelectionTreeVSAScriptable) scope.getVSAScriptable(vname);
      final DataRef[] refs = assembly.getDataRefs();
      final int[] rtypes = new int[refs.length];
      final VSCompositeFormat[] vfmts = new VSCompositeFormat[refs.length];
      final boolean[] dynamics = new boolean[refs.length];
      final FormatInfo finfo = assembly.getFormatInfo();

      for(int i = 0; i < refs.length; i++) {
         rtypes[i] = refs[i] == null ? 0 : refs[i].getRefType();
         int flevel = i < refs.length - 1 ? i : -1;
         int ftype = i < refs.length - 1 ? TableDataPath.GROUP_HEADER :
            TableDataPath.DETAIL;
         TableDataPath path = new TableDataPath(flevel, ftype);
         vfmts[i] = finfo.getFormat(path, false);

         if(vfmts[i] != null) {
            dynamics[i] = isDynamic(vfmts[i].getUserDefinedFormat());
         }
      }

      if(cval != null) {
         cval.getSelectionList().sort(getSortType(sinfo));
      }

      if(finfo == null || cval == null) {
         return;
      }

      TableDataPath objPath = new TableDataPath(-1, TableDataPath.OBJECT);
      final VSCompositeFormat pfmt = finfo.getFormat(objPath, false);

      refreshMeasureFormat();
      SelectionValueIterator iterator = new SelectionValueIterator(cval) {
         @Override
         protected void visit(SelectionValue sval, List<SelectionValue> pvals)
               throws Exception
         {
            int level = sval.getLevel();

            if(level >= 0 && vfmts[level] != null) {
               copyUserDefinedFormat(vfmts[level].getUserDefinedFormat(), pfmt);
               refreshFormat(sval, refs[level], vfmts[level], scriptable,
                             dynamics[level], rtypes[level]);
            }
         }
      };

      iterator.iterate();
   }

   /**
    * Check if selections outside of tree exist.
    */
   private boolean isAssociationDefined(SelectionTreeVSAssembly assembly,
                                         Map<String, Map<String, Collection<Object>>> allSelections)
   {
      final DataRef[] refs = assembly.getDataRefs();

      if(allSelections.size() > 0 || refs.length == 0) {
         for(String tableName : assembly.getTableNames()) {
            final Map<String, Collection<Object>> tableSelections = allSelections.get(tableName);

            if(tableSelections == null) {
               continue;
            }

            Set<String> selectionKeys = new HashSet<>(tableSelections.keySet());

            if(selectionKeys.stream().anyMatch((key) -> key.startsWith(SelectionVSAssembly.RANGE)))
            {
               return true;
            }

            selectionKeys.remove(refs[0].getName());

            final String[] refNames = Arrays.stream(refs)
               .map(DataRef::getName)
               .toArray(String[]::new);

            final Set<String[]> selectionPaths = selectionKeys.stream()
               .filter((key) -> key.startsWith(SelectionVSAssembly.SELECTION_PATH))
               .map((key) -> key.substring(SelectionVSAssembly.SELECTION_PATH.length()))
               .map(VSUtil::parseSelectionKey)
               .collect(Collectors.toSet());

            for(String[] selectionPath : selectionPaths) {
               for(int i = 0; i < selectionPath.length && i < refNames.length; i++) {
                  if(selectionKeys.contains(selectionPath[i]) &&
                     !selectionPath[i].equals(refNames[i]))
                  {
                     return true;
                  }
               }
            }

            selectionKeys.removeIf(key -> key.startsWith(SelectionVSAssembly.SELECTION_PATH) ||
                                          key.startsWith(SelectionVSAssembly.RANGE));

            if(selectionKeys.size() > 0) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Create a composite value.
    */
   public static CompositeSelectionValue createNode(String type) {
      CompositeSelectionValue cselection = new CompositeSelectionValue();
      SelectionList slist = new SelectionList();

      cselection.setLevel(-1);
      slist.setDataType(type);
      cselection.setSelectionList(slist);

      return cselection;
   }

   /**
    * Get comparator.
    */
   protected Comparator getComparator(XTable data, DataRef ref) {
      XTable table0 = data;
      Comparator comp = null;

      while(table0 instanceof TableFilter) {
         table0 = ((TableFilter) table0).getTable();
      }

      if(table0 instanceof RealtimeTableMetaData.ColumnTable) {
         comp = ((RealtimeTableMetaData.ColumnTable) table0).getComparator();
      }
      else if(table0 instanceof RealtimeTableMetaData.ColumnsTable) {
         comp = ((RealtimeTableMetaData.ColumnsTable) table0).
            getComparator(ref.getName());
      }

      return comp;
   }

   /**
    * Find all selected values.
    */
   protected List<SelectionValue> getSelectedValues(
      CompositeSelectionValue cval)
   {
      SelectionTreeVSAssembly assembly = (SelectionTreeVSAssembly) getAssembly();
      DataRef[] refs = assembly.getDataRefs();
      int leaf = (refs != null) ? refs.length - 1 : -1;
      return cval.getSelectionValues(leaf, SelectionValue.STATE_SELECTED, 0);
   }

   /**
    * Find all selection paths which are compatible with this assembly.
    *
    * @param assembly   the assembly to find the compatible selection paths of
    * @param selections the selections to search the compatible selection paths in
    *
    * @return all compatible selection paths
    */
   private List<Set<Object>> findCompatibleSelectionPaths(
      SelectionTreeVSAssembly assembly,
      Map<String, Map<String, Collection<Object>>> selections)
   {
      final DataRef[] refs = assembly.getDataRefs();
      final String subSelkey = VSUtil.getSelectionKey(Arrays.copyOfRange(refs, 0, 1));
      final String pathPrefix = SelectionTreeVSAssembly.SELECTION_PATH + subSelkey;
      final List<Set<Object>> allSelPathsList = new ArrayList<>();

      for(String tableName : assembly.getTableNames()) {
         final Map<String, Collection<Object>> tableSelections = selections.get(tableName);

         if(tableSelections != null) {
            tableSelections.entrySet().stream()
               .filter((e) -> e.getKey().startsWith(pathPrefix))
               .map(Map.Entry::getValue)
               .map((c) -> (Set<Object>) c)
               .forEach(allSelPathsList::add);
         }
      }

      return allSelPathsList;
   }
}
