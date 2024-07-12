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
package inetsoft.report.composition.execution;

import inetsoft.report.TableDataPath;
import inetsoft.report.TableLens;
import inetsoft.report.script.viewsheet.SelectionTreeVSAScriptable;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.uql.XTable;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.text.Format;
import java.util.*;

/**
 * SelectionTreeVSAQuery2, the selection tree viewsheet assembly query,
 * use to process the specified mode.
 *
 * @version 11.5
 * @author InetSoft Technology Corp
 */
public class SelectionTreeVSAQuery2 extends SelectionTreeVSAQuery {
   /**
    * Create a selection tree viewsheet assembly query.
    * @param box the specified viewsheet sandbox.
    * @param vname the specified viewsheet assembly to be processed.
    */
   public SelectionTreeVSAQuery2(ViewsheetSandbox box, String vname) {
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

      int idIndex = -1;
      int pidIndex = -1;
      int labelIndex = -1;

      for(int i = 0; i < refs.length; i++) {
         String name = refs[i].getName();

         if(Tool.equals(name, assembly.getID())) {
            idIndex = i;
         }
         else if(Tool.equals(name, assembly.getParentID())) {
            pidIndex = i;
         }
         else if(Tool.equals(name, assembly.getLabel())) {
            labelIndex = i;
         }
      }

      if(idIndex == -1) {
         return;
      }

      DataRef ref = refs[idIndex];

      if(pidIndex != -1) {
         DataRef pref = refs[pidIndex];

         if(!Tool.equals(ref.getDataType(), pref.getDataType())) {
            pidIndex = -1;
         }
      }

      CompositeSelectionValue cselection = createNode(ref.getDataType());
      final CompositeSelectionValue cselection2 = createNode(ref.getDataType());
      SelectionList selection = cselection.getSelectionList();
      SelectionList selection2 = cselection2.getSelectionList();

      assembly.setCompositeSelectionValue(cselection);
      boolean first = true;
      final Map<String, Collection<Object>> intersectionAllSelections =
         getColumnMapIntersection(assembly, allSelections);
      // true if there are selections made on other columns
      boolean associationDefined = isAssociationDefined(intersectionAllSelections, ref);

      Set<Object> vset = values.getOrDefault(ref.getName(), new HashSet<>()); // associated values
      // selected values
      Set<Object> sset = (Set<Object>) intersectionAllSelections.getOrDefault(ref.getName(),
                                                                              new HashSet<>());

      // measure range
      final Map<Object, Object> mvalues = measureAggregation.getMeasures();
      final double mmin = measureAggregation.getMin();
      final double mmax = measureAggregation.getMax();

      selection.setMeasureMin(mmin);
      selection2.setMeasureMax(mmax);

      SelectionTreeInfo2 info = SelectionTreeInfo2.build(
         data, ref, mvalues, new double[] {mmin, mmax}, sinfo, vset, sset,
         selection2, idIndex, pidIndex, labelIndex, associationDefined);

      // 1. parent --> row indexs
      Map<String, List<Integer>> p2r = new HashMap<>();

      for(int r = 1; data.moreRows(r); r++) {
         Object obj = data.getObject(r, idIndex);
         Object vobj = pidIndex == -1 ? obj : data.getObject(r, pidIndex);
         String value = vobj == null ? null : Tool.getDataString(vobj);
         List<Integer> rows = p2r.get(value);

         if(rows == null) {
            rows = new ArrayList<>();
            p2r.put(value, rows);
         }

         if(pidIndex != -1) {
            String value0 = obj == null ? null : Tool.getDataString(obj);

            if(Tool.equals(value, value0)) {
               List<Integer> roots = p2r.get(null);

               if(roots == null) {
                  roots = new ArrayList<>();
                  p2r.put(null, roots);
               }

               roots.add(r);
               continue;
            }
         }

         if(first && obj != null) {
            first = false;
            int typeIndex = labelIndex != -1 ? labelIndex : idIndex;
            String dtype = Tool.getDataType(data.getObject(r, typeIndex).getClass());
            selection.setDataType(dtype);
            selection.setComparator(getComparator(data, ref));
            selection2.setDataType(dtype);
            selection2.setComparator(getComparator(data, ref));
         }

         rows.add(r);
      }

      ArrayList<SelectionValue> topLevelValues = new ArrayList<>();
      Map<String, SelectionValue> compMap = new HashMap<>();

      // 2. prepare tree structure
      for(String key : p2r.keySet()) {
         prepareValue(info, p2r.get(key), p2r, compMap, null, key, topLevelValues, 0);
      }

      // For Bug #5976, removed previous loop which only searched for top level
      // items that did not exist in any other selection list.
      // Instead, use the previous logic to also get the top level values of the
      // selection tree and add them to the list.  This will be significantly
      // faster.
      // 3. add top level nodes of the selection tree.
      for(SelectionValue val : topLevelValues) {
         cselection.getSelectionList().addSelectionValue(val);
      }

      // 4. apply selection value state
      applyState(cselection, info);
      boolean openVS = Boolean.TRUE.equals(VSUtil.OPEN_VIEWSHEET.get());

      if(sinfo.isSingleSelection()) {
         // 5. mark selected item if has no selected item.
         markSelected(cselection, cselection2);
      }

      if(openVS && selection2.getSelectionValueCount() == 0 && sinfo.isSelectFirstItem()) {
         if(sinfo.isSelectChildren()) {
            SelectionVSUtil.selectSelectionTreeFirstItem(sinfo, cselection, null);
            SelectionList listClone = (SelectionList) cselection.getSelectionList().clone();
            SelectionValue[] selectionValues = SelectionVSUtil.shrinkSelectionValues(assembly,
               listClone.getSelectionValues());
            cselection2.getSelectionList().setSelectionValues(selectionValues);
         }
         else {
            markSelected(cselection, cselection2);
         }
      }

      selection.sort(getSortType(
         (SelectionBaseVSAssemblyInfo) assembly.getInfo()));
      assembly.setStateCompositeSelectionValue(cselection2);
      syncSelections(assembly, appliedSelections, true);

      if(sinfo.isSingleSelection()) {
         syncSelections(assembly, allSelections, false);
      }
   }

   /**
    * If has no selected node, then select the first included item, or the first item.
    */
   public void markSelected(CompositeSelectionValue cselection,
                            CompositeSelectionValue cselection2)
   {
      SelectionValue[] values = cselection.getSelectionList().getAllSelectionValues();
      SelectionList list = cselection2.getSelectionList();
      boolean hasIncluded = false;

      for(SelectionValue value : values) {
         if(value.isSelected()) {
            return;
         }

         if(value.isIncluded()) {
            hasIncluded = true;
            break;
         }
      }

      for(SelectionValue value : values) {
         if(!hasIncluded || hasIncluded && value.isIncluded()) {
            value.setSelected(true);
            list.addSelectionValue((SelectionValue) value.clone());
            break;
         }
      }
   }

   /**
    * @param intersectionAllSelections the intersection of all selections
    * @param IDRef                     the ID column ref
    *
    * @return true if association is defined, otherwise false
    */
   private boolean isAssociationDefined(Map<String, Collection<Object>> intersectionAllSelections,
                                        DataRef IDRef)
   {
      return box.isAssociationEnabled() &&
         (intersectionAllSelections.size() > 1 ||
            intersectionAllSelections.size() > 0 &&
               intersectionAllSelections.get(IDRef.getName()) == null);
   }

   /**
    * @param values row indexes.
    * @param p2r parent (value) to children row indexes.
    * @param compMap parent (value) to parent node map.
    * @param parent parent node to add children to.
    * @param key parent value.
    */
   private void prepareValue(final SelectionTreeInfo2 info, final List<Integer> values,
                             final Map<String, List<Integer>> p2r,
                             final Map<String, SelectionValue> compMap,
                             final SelectionValue parent, final String key,
                             final ArrayList<SelectionValue> topLevelValues, final int level)
   {
      // prevent infinite recursion
      if(level > 100) {
         LOG.warn("Selection tree reached maximum number of nesting (100).");
         return;
      }

      for(int row : values) {
         Object obj = info.data.getObject(row, info.idIndex);

         if(info.suppressBlank && (obj == null || obj.equals(""))) {
            continue;
         }

         String value = obj == null ? null :Tool.getDataString(obj);
         Object lbValue = info.labelIndex == -1 ? value : info.data.getObject(row, info.labelIndex);
         String label = lbValue == null ? "" : Tool.getDataString(lbValue);
         List<Integer> rows = p2r.get(value);
         int typeIndex = info.labelIndex != -1 ? info.labelIndex : info.idIndex;
         String dtype = obj == null ? null : Tool.getDataType(info.data.getObject(row, typeIndex));
         Comparator comp = getComparator(info.data, info.ref);
         Format fmt = (info.data instanceof TableLens) ?
            ((TableLens) info.data).getDefaultFormat(row, info.idIndex) : null;
         Object mvalue = getMeasureValue(info, row);
         SelectionValue svalue = null;
         int state = 0;

         if(parent != null && compMap.containsKey(value)) {
            svalue = (SelectionValue) compMap.get(value).clone();
            svalue.setLevel(parent.getLevel() + 1);
            svalue.setLabel(label);
            setMeasureInfo(svalue, info, mvalue);

            compMap.put(value, svalue);
            ((CompositeSelectionValue) parent).getSelectionList().
               addSelectionValue(compMap.get(value));
            continue;
         }

         if(rows == null || info.pidIndex == -1) {
            svalue = new SelectionValue(label, value);
         }
         else if(rows.size() > 0 && info.pidIndex > -1) {
            svalue = new CompositeSelectionValue(label, value);
            SelectionList slist = new SelectionList();
            slist.setDataType(dtype);
            slist.setComparator(comp);
            ((CompositeSelectionValue) svalue).setSelectionList(slist);
         }

         if(svalue == null) {
            continue;
         }

         if(parent == null && !compMap.containsKey(value)) {
            // @by stephenwebster, the keepBlank key is "null" string.
            if(svalue != null && (key == null || key.isEmpty())) {
               topLevelValues.add(svalue);
            }

            compMap.put(value, svalue);
         }

         svalue.setLevel(parent == null ? 0 : parent.getLevel() + 1);
         svalue.setDefaultFormat(fmt);
         setMeasureInfo(svalue, info, mvalue);

         if(info.sset.contains(obj) &&
            // single selection, only allow one item selected
            (!info.single || info.selection2.getSelectionValueCount() == 0))
         {
            state = state | SelectionValue.STATE_SELECTED;

            if(info.single) {
               info.selection2.addSelectionValue((SelectionValue) svalue.clone());
            }
         }

         if(info.vset.contains(obj)) {
            state = state | SelectionValue.STATE_COMPATIBLE;

            if(info.sset.size() == 0 && info.association_defined) {
               state = state | SelectionValue.STATE_INCLUDED;
            }
         }
         else if(info.association_defined) {
            state = state | SelectionValue.STATE_EXCLUDED;
         }

         svalue.setState(state);

         if(parent instanceof CompositeSelectionValue) {
            ((CompositeSelectionValue) parent).getSelectionList().
               addSelectionValue(svalue);
         }

         if(rows != null && rows.size() > 0 && info.pidIndex > -1) {
            prepareValue(info, rows, p2r, compMap, svalue, key, topLevelValues, level + 1);
         }
      }
   }

   /**
    * Set measure information.
    */
   private void setMeasureInfo(SelectionValue svalue, SelectionTreeInfo2 info,
      Object mvalue)
   {
      if(mvalue != null && svalue != null) {
         svalue.setMeasureValue(SelectionListVSAQuery.getMValue(
            mvalue, info.range[0], info.range[1]));
         svalue.setMeasureLabel(SelectionListVSAQuery.getMeasureLabel(
            mvalue, getAssembly().getFormatInfo(), 0, locale));
      }
   }

   private Object getMeasureValue(SelectionTreeInfo2 info, int row) {
      Object obj = info.data.getObject(row, info.idIndex);

      if(info.pidIndex == -1) {
         return info.mvalues.get(SelectionSet.normalize(obj));
      }

      ArrayList<Object> rvalues = new ArrayList<>();
      Object parentObj = info.data.getObject(row, info.pidIndex);

      if(StringUtils.isEmpty(parentObj)) {
         parentObj = null;
      }

      rvalues.add(SelectionSet.normalize(parentObj));
      rvalues.add(SelectionSet.normalize(obj));

      if(info.labelIndex != -1) {
         rvalues.add(SelectionSet.normalize(info.data.getObject(row, info.labelIndex)));
      }

      Object[] arr = rvalues.toArray(new Object[0]);
      Object tuple = new SelectionSet.Tuple(arr, info.idIndex + 1);
      return info.mvalues.get(SelectionSet.normalize(tuple));
   }

   /**
    * Copy selected nodes from tree nodes to the state nodes.
    */
   private void applyState(CompositeSelectionValue cselection, SelectionTreeInfo2 info) {
      SelectionList list = cselection.getSelectionList();

      for(int i = 0; i < list.getSelectionValueCount(); i++) {
         SelectionValue svalue = list.getSelectionValue(i);

         if(svalue.isSelected()) {
            info.selection2.addSelectionValue((SelectionValue) svalue.clone());
         }

         if(svalue instanceof CompositeSelectionValue) {
            applyState((CompositeSelectionValue) svalue, info);
         }
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
      final DataRef ref = getRefByName(assembly, sinfo.getID());

      if(ref == null) {
         return;
      }

      CompositeSelectionValue cval = assembly.getCompositeSelectionValue();
      final FormatInfo finfo = assembly.getFormatInfo();

      if(cval != null) {
         cval.getSelectionList().sort(getSortType(sinfo));
      }

      if(finfo == null || cval == null) {
         return;
      }

      TableDataPath path = new TableDataPath(-1, TableDataPath.DETAIL);
      final VSCompositeFormat vfmt = finfo.getFormat(path, false);
      TableDataPath objPath = new TableDataPath(-1, TableDataPath.OBJECT);
      final VSCompositeFormat pfmt = finfo.getFormat(objPath, false);

      if(vfmt == null) {
         return;
      }

      copyUserDefinedFormat(vfmt.getUserDefinedFormat(), pfmt);

      refreshMeasureFormat();
      ViewsheetScope scope = box.getScope();
      final SelectionTreeVSAScriptable scriptable =
         (SelectionTreeVSAScriptable) scope.getVSAScriptable(vname);
      final boolean dynamic = isDynamic(vfmt.getUserDefinedFormat());
      final int rtype = ref.getRefType();
      SelectionValueIterator iterator = new SelectionValueIterator(cval) {
         @Override
         protected void visit(SelectionValue sval, List<SelectionValue> pvals)
               throws Exception
         {
            int level = sval.getLevel();

            if(level >= 0) {
               scriptable.setCellValue(sval.getValue());
               refreshFormat(sval, ref, vfmt, scriptable, dynamic, rtype);
            }
         }
      };

      iterator.iterate();
   }

   /**
    * Get selection value data by the data type.
    */
   @Override
   protected Object getSelectionValueData(SelectionValue svalue, DataRef ref) {
      SelectionTreeVSAssembly assembly =
         (SelectionTreeVSAssembly) getAssembly();
      SelectionTreeVSAssemblyInfo sinfo =
         (SelectionTreeVSAssemblyInfo) assembly.getInfo();
      String name = sinfo.getLabel();

      DataRef dref = getRefByName(assembly, name);
      ref = dref != null ? dref : ref;
      String dtype = ref.getDataType();
      String label = svalue.getOriginalLabel();

      return Tool.getData(dtype, dref != null ? label : svalue.getValue(), true);
   }

   /**
    * Get data ref by specified name.
    */
   private DataRef getRefByName(SelectionVSAssembly assembly, String name) {
      DataRef[] refs = assembly.getDataRefs();

      for(DataRef ref : refs) {
         if(ref != null && Tool.equals(ref.getName(), name)) {
            return ref;
         }
      }

      return null;
   }

   private static class SelectionTreeInfo2 {
      public static SelectionTreeInfo2 build(XTable data, DataRef ref,
                                             Map mvalues, double[] range,
                                             SelectionTreeVSAssemblyInfo sinfo,
                                             Set vset, Set sset,
                                             SelectionList selection2,
                                             int idIndex, int pidIndex,
                                             int labelIndex,
                                             boolean association_defined)
      {
         SelectionTreeInfo2 info = new SelectionTreeInfo2();

         info.data = data;
         info.ref = ref;
         info.mvalues = mvalues;
         info.vset = vset;
         info.sset = sset;
         info.selection2 = selection2;
         info.range = range;
         info.idIndex = idIndex;
         info.pidIndex = pidIndex;
         info.labelIndex = labelIndex;
         info.association_defined = association_defined;
         info.single = sinfo.isSingleSelection();
         info.suppressBlank = sinfo.isSuppressBlankValue();

         return info;
      }

      private XTable data;
      private DataRef ref;
      private Map mvalues;
      private Set vset, sset;
      private SelectionList selection2;
      private int idIndex, pidIndex, labelIndex;
      private double[] range;
      private boolean association_defined;
      private boolean single;
      private boolean suppressBlank;
   }

   private static final Logger LOG = LoggerFactory.getLogger(SelectionTreeVSAQuery2.class);
}
