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

import inetsoft.report.*;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.script.viewsheet.SelectionListVSAScriptable;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.XTable;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.xmla.MemberObject;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Format;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SelectionListVSAQuery, the selection list viewsheet assembly query.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class SelectionListVSAQuery extends AbstractSelectionVSAQuery {
   /**
    * Create a selection list viewsheet assembly query.
    * @param box the specified viewsheet sandbox.
    * @param vname the specified viewsheet assembly to be processed.
    */
   public SelectionListVSAQuery(ViewsheetSandbox box, String vname) {
      super(box, vname);

      String prop = SreeEnv.getProperty("selectionList.maxrow");
      long maxRow = 50000;

      if(prop.length() > 0) {
         try {
            maxRow = Long.parseLong(prop);

            if(maxRow == 0) {
               maxRow = 50000;
            }
         }
         catch(Exception ex) {
            LOG.error("Invalid long value for the maximum " +
                         "number of selection list rows property (asset.sample.maxrows): " + prop,
                      ex);
         }
      }

      this.maxRow = maxRow;
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
      SelectionList selection = null;
      SelectionList selection2 = null;
      SelectionListVSAssembly assembly = (SelectionListVSAssembly) getAssembly();
      SelectionListVSAssemblyInfo sinfo = (SelectionListVSAssemblyInfo) assembly.getInfo();

      if(data.getColCount() != 1 || !data.moreRows(1)) {
         // view selection list
         assembly.setSelectionList(selection);
         // state selection list
         assembly.setStateSelectionList(selection2);
         return;
      }

      data.moreRows(Integer.MAX_VALUE);
      XTable table = data;

      if(data instanceof RealtimeTableMetaData.ColumnTable) {
         table = ((RealtimeTableMetaData.ColumnTable) data).getTable();
      }
      else if(data instanceof RealtimeTableMetaData.ColumnsTable) {
         table = ((RealtimeTableMetaData.ColumnsTable) data).getTable();
      }

      checkMaxRowLimit(table);

      final Map<String, Collection<Object>> intersectionAllSelections =
         getColumnMapIntersection(assembly, allSelections);

      DataRef ref = assembly.getDataRef();
      Set<Object> vset = values.get(ref.getName()); // associated values
      Set<Object> sset = (Set<Object>) intersectionAllSelections.get(ref.getName()); // selected values

      boolean associationDefined = box.isAssociationEnabled() &&
         isAssociationDefined(assembly, allSelections);
      XTable table0 = data;

      while(table0 instanceof TableFilter) {
         table0 = ((TableFilter) table0).getTable();
      }

      Comparator comp = null;

      if(table0 instanceof RealtimeTableMetaData.ColumnTable) {
         comp = ((RealtimeTableMetaData.ColumnTable) table0).getComparator();
      }
      else if(table0 instanceof RealtimeTableMetaData.ColumnsTable) {
         comp = ((RealtimeTableMetaData.ColumnsTable) table0).getComparator(ref.getName());
      }

      selection = new SelectionList();
      selection.setDataType(ref.getDataType());
      selection.setComparator(comp);
      selection2 = new SelectionList();
      selection2.setDataType(ref.getDataType());
      selection2.setComparator(comp);

      vset = vset == null ? new HashSet<>() : vset;
      sset = sset == null ? new HashSet<>() : sset;

      TableLens table2 = data instanceof TableLens ? (TableLens) data : null;
      List<SelectionValue> svalues = new ArrayList<>();
      boolean single = sinfo.isSingleSelection();
      boolean suppressBlank = sinfo.isSuppressBlankValue();
      boolean first = true;
      boolean hasSelection = false;
      int rtype = ref.getRefType();
      final Map<Object, Object> mvalues = measureAggregation.getMeasures();
      double mmin = measureAggregation.getMin();
      double mmax = measureAggregation.getMax();

      selection.setMeasureMin(mmin);
      selection.setMeasureMax(mmax);

      refreshMeasureFormat(); // make sure format is up-to-date

      final List<Set<Object>> appliedSelectionValuesList =
         assembly.getTableNames().stream()
            .map(appliedSelections::get)
            .filter(Objects::nonNull)
            .map((s) -> s.get(ref.getName()))
            .filter(Objects::nonNull)
            .map((c) -> (Set<Object>) c)
            .collect(Collectors.toList());

      boolean findSelected = false;

      for(int r = 1; data.moreRows(r); r++) {
         Object obj0 = data.getObject(r, 0);

         if("".equals(obj0)) {
            obj0 = null;
         }

         Object obj = obj0;

         if(suppressBlank && obj == null) {
            continue;
         }

         if(r > maxRow) {
            LOG.warn("Selection list truncated to " + maxRow + " items: " + assembly.getAbsoluteName());
            break;
         }

         String value = obj == null ? null : Tool.getDataString(obj);
         String label = VSCubeTableLens.getDisplayValue(
            obj == null ? "" : obj instanceof MemberObject ? obj : value, rtype);

         SelectionValue svalue = new SelectionValue(label, value);

         if(first && obj != null) {
            first = false;
            String dtype = Tool.getDataType(obj.getClass());
            selection.setDataType(dtype);
            selection.setComparator(comp);
            selection2.setDataType(dtype);
            selection2.setComparator(comp);
         }

         Format fmt = table2 == null ? null : table2.getDefaultFormat(r, 0);
         Object mvalue = mvalues.get(SelectionSet.normalize(obj));

         if(mvalue == null && obj instanceof MemberObject) {
            mvalue = mvalues.get(((MemberObject) obj).getCaption());
         }

         int state = 0;

         svalue.setDefaultFormat(fmt);

         if(mvalue != null) {
            svalue.setMeasureValue(getMValue(mvalue, mmin, mmax));
            svalue.setMeasureLabel(getMeasureLabel(
               mvalue, assembly.getFormatInfo(), 0, locale));
         }

         if((!findSelected || !single) && sset.contains(obj)) {
            state = state | SelectionValue.STATE_SELECTED;
            findSelected = true;
         }

         if(vset.contains(obj)) {
            state = state | SelectionValue.STATE_COMPATIBLE;

            if(sset.size() == 0 && associationDefined && !single) {
               state = state | SelectionValue.STATE_INCLUDED;
            }
            else if((state & SelectionValue.STATE_SELECTED) != 0) {
               hasSelection = true;
            }
         }
         else if(associationDefined) {
            state = state | SelectionValue.STATE_EXCLUDED;

            if((state & SelectionValue.STATE_SELECTED) == SelectionValue.STATE_SELECTED) {
               appliedSelectionValuesList.forEach((v) -> v.remove(obj));
            }
         }

         svalue.setState(state);
         svalue.setLevel(0);
         svalues.add(svalue);

         if(svalue.isSelected()) {
            selection2.addSelectionValue(svalue);
         }
      }

      SelectionValue[] arr = new SelectionValue[svalues.size()];
      selection.setSelectionValues(svalues.toArray(arr));

      //fix bug #3983
      if(box.isRuntime()) {
         sinfo.setUsingMetaData(false);
      }

      selection.sort(getSortType((SelectionBaseVSAssemblyInfo) assembly.getInfo()));
      boolean autoSelected = false;
      boolean openVS = Boolean.TRUE.equals(VSUtil.OPEN_VIEWSHEET.get());
      boolean autoSelectedFirst = false;

      // force an item to be selected for single selection, like radio button
      if(single && selection2.getSelectionValueCount() == 0) {
         for(int i = 0; i < selection.getSelectionValueCount(); i++) {
            SelectionValue sval = selection.getSelectionValue(i);

            if(!sval.isExcluded()) {
               sval.setState(sval.getState() | SelectionValue.STATE_SELECTED);
               selection2.addSelectionValue(sval);
               hasSelection = true;
               autoSelected = true;
               break;
            }
         }
      }
      else if(openVS && sinfo.isSelectFirstItem() && selection2.getSelectionValueCount() == 0) {
         if(SelectionVSUtil.selectSelectionFirstItem(selection, selection2)) {
            hasSelection = true;
            autoSelected = true;
            autoSelectedFirst = true;
         }
      }

      // view selection list
      assembly.setSelectionList(selection);
      // state selection list
      assembly.setStateSelectionList(selection2);
      selection.complete();
      selection2.complete();

      // re-populate the selected values as it may be changed by this method
      syncSelections(assembly, appliedSelections, true);

      if(autoSelected) {
         syncSelections(assembly, allSelections, false);
      }

      // we have a little sequence problem. When we are setting the INCLUDED
      // status, we really don't have the information that whether any
      // selected values on this list would be applied. It's only accurate
      // after the entire list has been processed. So we re-populate the status
      // here.
      for(int i = 0; i < selection.getSelectionValueCount(); i++) {
         SelectionValue sval = selection.getSelectionValue(i);
         int state = sval.getState();

         if(!sval.isSelected() && (state & SelectionValue.STATE_COMPATIBLE) != 0) {
            if(hasSelection) {
               state &= ~SelectionValue.STATE_INCLUDED;
            }
            else if(associationDefined) {
               state |= SelectionValue.STATE_INCLUDED;
            }

            sval.setState(state);
         }
      }
   }

   /**
    * Refresh the view selection value.
    */
   @Override
   protected void refreshViewSelectionValue0() throws Exception {
      super.refreshViewSelectionValue0();

      SelectionListVSAssembly assembly = (SelectionListVSAssembly) getAssembly();

      if(assembly == null) {
         return;
      }

      SelectionListVSAssemblyInfo sinfo = (SelectionListVSAssemblyInfo) assembly.getInfo();
      SelectionList slist = assembly.getSelectionList();
      DataRef ref = assembly.getDataRef();

      if(ref == null) {
         return;
      }

      if(slist != null) {
         slist.sort(getSortType(sinfo));
      }

      FormatInfo finfo = assembly.getFormatInfo();
      TableDataPath path = new TableDataPath(-1, TableDataPath.DETAIL);
      VSCompositeFormat vfmt = (finfo == null) ? null : finfo.getFormat(path, false);
      TableDataPath objPath = new TableDataPath(-1, TableDataPath.OBJECT);
      VSCompositeFormat pfmt = (finfo == null) ? null : finfo.getFormat(objPath, false);

      if(vfmt == null || slist == null) {
         return;
      }

      vfmt = (VSCompositeFormat) vfmt.clone();
      copyUserDefinedFormat(vfmt.getUserDefinedFormat(), pfmt);

      refreshMeasureFormat();
      ViewsheetScope scope = box.getScope();
      SelectionListVSAScriptable scriptable =
         (SelectionListVSAScriptable) scope.getVSAScriptable(vname);
      boolean dynamic = isDynamic(vfmt.getUserDefinedFormat());
      int rtype = ref.getRefType();

      for(int i = 0; i < slist.getSelectionValueCount(); i++) {
         SelectionValue svalue = slist.getSelectionValue(i);

         if(scriptable != null) {
            scriptable.setCellValue(svalue.getValue());
         }

         refreshFormat(svalue, ref, vfmt, scriptable, dynamic, rtype);
      }
   }

   /**
    * Get the measure value ratio.
    */
   static double getMValue(Object mvalue, double mmin, double mmax) {
      if(mvalue instanceof Number) {
         double mval = ((Number) mvalue).doubleValue();

         if(mval < 0 && mmin < 0) {
            mval = -mval / mmin;
         }
         else {
            mval = mval / mmax;
         }

         return mval;
      }

      return 0;
   }

   /**
    * Return formatted measure label.
    */
   static String getMeasureLabel(Object mvalue, FormatInfo finfo,
                                 int level, Locale locale)
   {
      VSCompositeFormat vfmt = finfo.getFormat(
         SelectionListVSAssemblyInfo.getMeasureTextPath(level));

      if(vfmt != null) {
         Format fmt = TableFormat.getFormat(vfmt.getFormat(), vfmt.getFormatExtent(), locale);

         if(fmt != null) {
            return fmt.format(mvalue);
         }
      }

      if(mvalue instanceof Number) {
         Number num = (Number) mvalue;

         return (num.doubleValue() == num.intValue())
            ? Integer.toString(num.intValue())
            : Tool.toString(mvalue);
      }

      return Tool.toString(mvalue);
   }

   private boolean isAssociationDefined(
      SelectionListVSAssembly assembly,
      Map<String, Map<String, Collection<Object>>> allSelections)
   {
      final String refName = assembly.getDataRef().getName();

      for(String tableName : assembly.getTableNames()) {
         final Map<String, Collection<Object>> tableSelections = allSelections.get(tableName);

         if(tableSelections == null) {
            continue;
         }

         final Set<String> selectionKeys = tableSelections.keySet();
         final boolean hasRangeSelection = selectionKeys.stream()
            .anyMatch((key) -> key.startsWith(SelectionVSAssembly.RANGE));

         if(hasRangeSelection) {
            return true;
         }

         final long numColSels = selectionKeys.stream()
            .filter((key) -> !key.startsWith(SelectionVSAssembly.SELECTION_PATH))
            .filter((key) -> !key.startsWith(SelectionVSAssembly.RANGE))
            .count();

         if(numColSels > 1 || numColSels > 0 && tableSelections.get(refName) == null) {
            return true;
         }
      }

      return false;
   }

   private final long maxRow;
   private static final Logger LOG = LoggerFactory.getLogger(SelectionListVSAQuery.class);
}
