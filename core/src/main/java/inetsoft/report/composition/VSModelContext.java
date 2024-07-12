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
package inetsoft.report.composition;

import inetsoft.report.Hyperlink;
import inetsoft.report.TableCellBinding;
import inetsoft.report.composition.execution.*;
import inetsoft.report.filter.Highlight;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.binding.BindingTool;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.CompositeColumnHelper;
import inetsoft.uql.erm.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.*;

/**
 * Model context for viewsheet.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class VSModelContext extends AbstractModelContext {
   /**
    * Get table assembly.
    */
   private TableAssembly getTable(XSourceInfo source) {
      if(vs == null || source == null) {
         return null;
      }

      String src = source.getSource();
      TableAssembly table = vs.getBaseWorksheet().getVSTableAssembly(src);

      if(table == null) {
         return null;
      }

      CalculateRef[] calcs = vs.getCalcFields(src);

      if(calcs != null) {
         table = (TableAssembly) table.clone();
         VSUtil.appendCalcFields(table.getColumnSelection(), src, vs);
         table.resetColumnSelection();
      }

      return table;
   }

   /**
    * Constructor.
    */
   public VSModelContext(RuntimeViewsheet rvs) {
      this.rvs = rvs;
      this.vs = rvs.getViewsheet();

      init(vs == null ? null : vs.getBaseEntry(),
         rvs.getViewsheetSandbox() == null ?
         null : rvs.getViewsheetSandbox().getUser());
   }

   /**
    * Initialize.
    */
   protected void init(AssetEntry entry, Principal user) {
      if(entry != null && entry.getType() == AssetEntry.Type.LOGIC_MODEL) {
         DataSourceRegistry dsr = DataSourceRegistry.getRegistry();
         XDataModel xdm = dsr.getDataModel(entry.getProperty("prefix"));
         lm = xdm.getLogicalModel(entry.getProperty("source"), user);
      }
   }

   /**
    * Get all assemblies fields.
    */
   public void getAllAttributes(VSAssemblyInfo cinfo,
                                HashSet<DataRef> all, HashSet<DataRef> aggs)
   {
      if(vs == null) {
         return;
      }

      for(Assembly assembly : vs.getAssemblies()) {
         VSAssembly vsassembly = (VSAssembly) assembly;
         VSAssemblyInfo info = (VSAssemblyInfo) vsassembly.getInfo();
         Boolean current = cinfo != null && info != null &&
            Tool.equals(cinfo.getAbsoluteName(), info.getAbsoluteName());
         info = current ? cinfo : info;
         getAttributes(info, all, aggs);
      }

      getAttributes(cinfo, all, aggs);
   }

   /**
    * Get all attriutes and aggregates attribtes in conditionlist.
    */
   private void getAttributes(HashSet<DataRef> all, ConditionList conds) {
      for(int i = 0; conds != null && i < conds.getSize(); i++) {
         if(conds.isConditionItem(i)) {
            addAttributes(all, conds.getAttribute(i));
            XCondition xcond = conds.getConditionItem(i).getXCondition();

            if(xcond instanceof AssetCondition) {
               DataRef[] dvalues = ((AssetCondition) xcond).getDataRefValues();

               for(DataRef ref : dvalues) {
                  addAttributes(all, ref);
               }
            }
         }
      }
   }

   /**
    * Get all attributes and aggregate attributes in binding.
    */
   private void getAttributes(VSAssemblyInfo info, HashSet<DataRef> all,
                              HashSet<DataRef> aggs) {
      if(info instanceof TableVSAssemblyInfo) {
         getAttributes((TableVSAssemblyInfo) info, all, aggs);
      }
      else if(info instanceof ChartVSAssemblyInfo) {
         ChartVSAssemblyInfo cinfo = (ChartVSAssemblyInfo) info;
         getAttributes(cinfo.getVSChartInfo(), getTable(cinfo.getSourceInfo()),
            all, aggs);
      }
      else if(info instanceof CrosstabVSAssemblyInfo) {
         CrosstabVSAssemblyInfo cinfo = (CrosstabVSAssemblyInfo) info;
         getAttributes(cinfo.getVSCrosstabInfo(),
            getTable(cinfo.getSourceInfo()), all, aggs);
      }
      else if(info instanceof CalcTableVSAssemblyInfo) {
         getAttributes((CalcTableVSAssemblyInfo) info, all, aggs);
      }
      else if(info instanceof SelectionListVSAssemblyInfo) {
         getAttributes((SelectionListVSAssemblyInfo) info, all, aggs);
      }
      else if(info instanceof SelectionTreeVSAssemblyInfo) {
         getAttributes((SelectionTreeVSAssemblyInfo) info, all, aggs);
      }
      else if(info instanceof TimeSliderVSAssemblyInfo) {
         getAttributes((TimeSliderVSAssemblyInfo) info, all, aggs);
      }
      else if(info instanceof CalendarVSAssemblyInfo) {
         getAttributes((CalendarVSAssemblyInfo) info, all, aggs);
      }
      else if(info instanceof OutputVSAssemblyInfo) {
         getAttributes((OutputVSAssemblyInfo) info, all, aggs);
      }
      else if(info instanceof ListBindableVSAssemblyInfo) {
         getAttributes((ListBindableVSAssemblyInfo) info, all, aggs);
      }

      if(info instanceof DataVSAssemblyInfo) {
         getAttributes(all, ((DataVSAssemblyInfo) info).getPreConditionList());
      }

      fixAggregates(all, aggs);
   }

   /**
    * Get all attributes and aggregate attributes in binding.
    */
   public void getAttributes(SelectionListVSAssemblyInfo info,
                             HashSet<DataRef> all, HashSet<DataRef> aggs)
   {
      addAttributes(all, info.getDataRef());
      addMeasureAttribute(info, all, aggs);
   }

   /**
    * Get all attributes and aggregate attributes in binding.
    */
   public void getAttributes(SelectionTreeVSAssemblyInfo info,
                             HashSet<DataRef> all, HashSet<DataRef> aggs)
   {
      addAttributes(all, info.getDataRefs());
      addMeasureAttribute(info, all, aggs);
   }

   /**
    * Get measure value.
    */
   private void addMeasureAttribute(SelectionBaseVSAssemblyInfo info,
                             HashSet<DataRef> all, HashSet<DataRef> aggs)
   {
      String measureValue = info.getMeasureValue();
      String formula = info.getFormula();

      if(measureValue == null || measureValue.length() == 0) {
         return;
      }

      Viewsheet vs = rvs.getViewsheet();
      SelectionVSAssembly assembly = (SelectionVSAssembly)
         vs.getAssembly(info.getAbsoluteName());
      String tname = assembly.getSelectionTableName();

      if(tname == null || tname.length() == 0) {
         return;
      }

      Worksheet ws = vs.getBaseWorksheet();
      TableAssembly table = (TableAssembly) ws.getAssembly(tname);
      ColumnSelection selection = table == null ? new ColumnSelection() :
            (ColumnSelection) table.getColumnSelection(true).clone();

      for(int i = 0; i < selection.getAttributeCount(); i++) {
         DataRef ref = selection.getAttribute(i);

         if(Tool.equals(ref.getAttribute(), measureValue)) {
            addAttributes(all, ref);
            AggregateRef agg = new AggregateRef(ref,
               AggregateFormula.getFormula(formula));
            addAttributes(aggs, agg);
         }
      }
   }

   /**
    * Get all attributes and aggregate attributes in binding.
    */
   public void getAttributes(TimeSliderVSAssemblyInfo info,
                             HashSet<DataRef> all, HashSet<DataRef> aggs)
   {
      addAttributes(all, info.getDataRefs());
   }

   /**
    * Get all attributes and aggregate attributes in binding.
    */
   public void getAttributes(CalendarVSAssemblyInfo info,
                             HashSet<DataRef> all, HashSet<DataRef> aggs)
   {
      addAttributes(all, info.getDataRef());
   }

   /**
    * Get all attributes and aggregate attributes in binding.
    */
   public void getAttributes(ListBindableVSAssemblyInfo info,
                             HashSet<DataRef> all, HashSet<DataRef> aggs)
   {
      ListBindingInfo binfo = info.getListBindingInfo();

      if(binfo == null || binfo.isEmpty()) {
         return;
      }

      addAttributes(all, binfo.getLabelColumn());
      addAttributes(all, binfo.getValueColumn());
   }

   /**
    * Get all attributes and aggregate attributes in binding.
    */
   public void getAttributes(OutputVSAssemblyInfo info,
                             HashSet<DataRef> all, HashSet<DataRef> aggs)
   {
      ScalarBindingInfo sbinfo = info.getScalarBindingInfo();

      if(vs == null || sbinfo == null) {
         return;
      }

      String name = info.getAbsoluteName();
      VSAssembly assembly = (VSAssembly) vs.getAssembly(name);

      if(assembly == null) {
         return;
      }

      VSAssemblyInfo cinfo = (VSAssemblyInfo)
         assembly.getVSAssemblyInfo().clone();

      if(cinfo != info) {
         try {
            ViewsheetSandbox box = rvs.getViewsheetSandbox();

            if(box == null) {
               return;
            }

            assembly.setVSAssemblyInfo(info);

            // make sure original info is roll back
            try {
               box.updateAssembly(name);
            }
            finally {
               assembly.setVSAssemblyInfo(cinfo);
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to update assembly: " + name, ex);
         }
      }

      DataRef[] refs = new DataRef[] {sbinfo.getColumn(),
         sbinfo.getSecondaryColumn()};
      addAttributes(all, refs);
      getAttributes(all, info.getPreConditionList());
      aggs.add(sbinfo.getColumn());
   }

   /**
    * Get fake column.
    */
   private DataRef getColumn(String name) {
      if(name == null) {
         return null;
      }

      int idx = name.indexOf(".");
      idx = idx == -1 ? name.indexOf(":") : idx;

      if(idx == -1) {
         return null;
      }

      String entity = name.substring(0, idx);
      String attribute = name.substring(idx + 1, name.length());

      return new AttributeRef(entity, attribute);
   }

   /**
    * Add attributes.
    */
   private void addAttributes(HashSet<DataRef> all, DataRef[] refs) {
      for(DataRef ref : refs) {
         addAttributes(all, ref);
      }
   }

   /**
    * Get all attributes and aggregate attributes in binding.
    */
   public void getAttributes(TableVSAssemblyInfo info, HashSet<DataRef> all,
                             HashSet<DataRef> aggs) {
      ColumnSelection cols = info.getColumnSelection();

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         addAttributes(all, cols.getAttribute(i));
      }

      Map<String, DataRef> allFields = getAllFields(info);
      TableHighlightAttr thattr = info.getHighlightAttr();
      Enumeration<DataRef> flds = getFields(thattr, allFields);

      while(flds.hasMoreElements()) {
         DataRef fld = flds.nextElement();
         addAttributes(all, fld);
      }

      TableHyperlinkAttr hattr = info.getHyperlinkAttr();
      flds = getFields(hattr, allFields);

      while(flds.hasMoreElements()) {
         DataRef fld = flds.nextElement();
         addAttributes(all, fld);
      }
   }

   /**
    * Get all attributes and aggregate attributes in binding.
    */
   private void getAttributes(CalcTableVSAssemblyInfo info, HashSet<DataRef> all,
                             HashSet<DataRef> aggs) {
      Viewsheet vs = rvs.getViewsheet();
      CalcTableVSAssembly table = (CalcTableVSAssembly)
         vs.getAssembly(info.getAbsoluteName());
      ColumnSelection cols = table == null ? new ColumnSelection() :
         table.getColumnSelection(info);

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         addAttributes(all, cols.getAttribute(i));
      }

      List<TableCellBinding> cells = VSLayoutTool.getTableCellBindings(
         info.getTableLayout(), TableCellBinding.SUMMARY);

      for(int i = 0; i < cells.size(); i++) {
         AggregateRef agg = createAggregateRef(cells.get(i), cols);

         if(agg == null) {
            continue;
         }

         addAttributes(aggs, agg);

         DataRef ref2 = agg.getSecondaryColumn();

         if(ref2 == null) {
            continue;
         }

         addAttributes(aggs, ref2);
      }
   }

   private static AggregateRef createAggregateRef(TableCellBinding bind,
      ColumnSelection columns)
   {
      DataRef ref = findAttribute(columns, bind.getValue());

      if(ref == null) {
         return null;
      }

      DataRef ref2 = null;

      String fname = BindingTool.getFormulaString(bind.getFormula());
      String sformula = BindingTool.getSecondFormula(bind.getFormula());

      if(sformula != null) {
         ref2 = findAttribute(columns, sformula);
      }

      return new AggregateRef(ref, ref2, AggregateFormula.getFormula(fname));
   }

   private static DataRef findAttribute(ColumnSelection cols, String val) {
      if(cols != null) {
         for(int i = 0; i < cols.getAttributeCount(); i++) {
            DataRef ref = cols.getAttribute(i);

            if(Tool.equals(val, ref.getAttribute())) {
               return ref;
            }
         }
      }

      return null;
   }

   /**
    * Get al fields.
    */
   private Map<String, DataRef> getAllFields(TableVSAssemblyInfo info) {
      String name = info.getAbsoluteName();
      Map<String, DataRef> map = new HashMap<>();

      if(vs == null || name == null) {
         return map;
      }

      VSAssembly assembly = (VSAssembly) vs.getAssembly(name);

      if(assembly == null || assembly.getTableName() == null) {
         return map;
      }

      final ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return map;
      }

      Worksheet ws = box.getWorksheet();
      AbstractTableAssembly tassembly =
         (AbstractTableAssembly) ws.getAssembly(assembly.getTableName());

      if(tassembly == null) {
         return map;
      }

      boolean isAggregate = tassembly.isAggregate();

      if(isAggregate) {
         ColumnSelection selection =
            ((TableVSAssembly) assembly).getColumnSelection();

         for(int i = 0; i < selection.getAttributeCount(); i++) {
            DataRef ref = selection.getAttribute(i);
            map.put(ref.getName(), ref);
         }
      }
      else {
         box.lockRead();

         try {
            // disable mv, useless, fix bug1312875633380-FromModel case
            box.setMVDisabled(true);
            DataVSAQuery query = (DataVSAQuery) VSAQuery.
               createVSAQuery(box, assembly, DataMap.NORMAL);
            ColumnSelection columns = null;
            columns = query.getDefaultColumnSelection();
            columns = VSUtil.getVSColumnSelection(columns);

            for(int i = 0; i < columns.getAttributeCount(); i++) {
               DataRef ref = columns.getAttribute(i);
               map.put(ref.getName(), ref);
            }
         }
         catch(Exception e) {
            // do nothing
         }
         finally {
            box.setMVDisabled(false);
            box.unlockRead();
         }
      }

      return map;
   }

   /**
    * Get all Fields used in hyperlink.
    */
   private static Enumeration<DataRef> getFields(TableHighlightAttr hattr,
      Map<String, DataRef> allFields)
   {
      Enumeration hgroups = hattr == null ? null : hattr.getAllHighlights();
      hgroups = hgroups == null ? Collections.emptyEnumeration() : hgroups;
      final ColumnSelection selection = new ColumnSelection();

      while(hgroups.hasMoreElements()) {
         HighlightGroup hgroup = (HighlightGroup) hgroups.nextElement();
         String[] levels = hgroup.getLevels();

         for(int i = 0; i < levels.length; i++) {
            String[] names = hgroup.getNames(levels[i]);

            for(int j = 0; j < names.length; j++) {
               Highlight highlight = hgroup.getHighlight(levels[i], names[j]);

               if(highlight == null) {
                  continue;
               }

               ConditionList clist = highlight.getConditionGroup();

               for(int k = 0; k < clist.getSize(); k++) {
                  DataRef attr = clist.getAttribute(k);

                  if(attr == null) {
                     continue;
                  }

                  attr = allFields.get(attr.getName());

                  if(attr != null && !selection.containsAttribute(attr)) {
                     selection.addAttribute(attr);
                  }
               }
            }
         }
      }

      return new Enumeration() {
         @Override
         public boolean hasMoreElements() {
            return i < selection.getAttributeCount();
         }

         @Override
         public Object nextElement() {
            return selection.getAttribute(i++);
         }

         private int i = 0;
      };
   }

   /**
    * Get all Fields used in hyperlink.
    */
   private static Enumeration<DataRef> getFields(TableHyperlinkAttr hattr,
      Map<String, DataRef> allFields)
   {
      Enumeration links = hattr == null ? null : hattr.getAllHyperlinks();
      links = links == null ? Collections.emptyEnumeration() : links;
      final ColumnSelection selection = new ColumnSelection();

      while(links.hasMoreElements()) {
         Hyperlink link = (Hyperlink) links.nextElement();

         for(String pname : link.getParameterNames()) {
            String field = link.getParameterField(pname);
            DataRef attribute = allFields.get(field);

            if(attribute != null && !selection.containsAttribute(attribute)) {
               selection.addAttribute(attribute);
            }
         }
      }

      return new Enumeration() {
         @Override
         public boolean hasMoreElements() {
            return i < selection.getAttributeCount();
         }

         @Override
         public Object nextElement() {
            return selection.getAttribute(i++);
         }

         private int i = 0;
      };
   }

   /**
    * Get all attributes and aggregate attributes in binding.
    */
   private void getAttributes(VSChartInfo info, TableAssembly table,
                              HashSet<DataRef> all, HashSet<DataRef> aggs)
   {
      VSDataRef[] refs = info.getRTFields();
      getAttributes(refs, table, all, aggs);
      all.addAll(aggs);
   }

   /**
    * Get all attributes and aggregate attributes in binding.
    */
   private void getAttributes(VSCrosstabInfo info, TableAssembly table,
                              HashSet<DataRef> all, HashSet<DataRef> aggs)
   {
      if(info == null) {
         return;
      }

      ArrayList<DataRef> list = new ArrayList();
      list.addAll(Arrays.asList(info.getRuntimeAggregates()));
      list.addAll(Arrays.asList(info.getRuntimeColHeaders()));
      list.addAll(Arrays.asList(info.getRuntimeRowHeaders()));
      VSDataRef[] refs = new VSDataRef[list.size()];
      list.toArray(refs);
      getAttributes(refs, table, all, aggs);
      all.addAll(aggs);
   }

   /*
    * Get all attributes and aggregate attributes in binding.
    */
   private void getAttributes(VSDataRef[] refs, TableAssembly table,
                              HashSet<DataRef> all, HashSet<DataRef> aggs)
   {
      if(table == null) {
         return;
      }

      helper = new CompositeColumnHelper(table);
      ColumnSelection cols = table.getColumnSelection();

      for(VSDataRef ref : refs) {
         if(ref instanceof VSDimensionRef) {
            GroupRef group = ((VSDimensionRef) ref).createGroupRef(cols);
            addAttributes(all, group);
         }
         else if(ref instanceof VSAggregateRef) {
            VSAggregateRef aref = (VSAggregateRef) ref;
            AggregateRef aggr = aref.createAggregateRef(cols);

            if(aggr == null) {
               continue;
            }

            addAttributes(aggs, aggr);

            DataRef ref2 = aggr.getSecondaryColumn();

            if(ref2 != null) {
               addAttributes(aggs, ref2);
            }
         }
      }

      helper = null;
   }

   private static final Logger LOG = LoggerFactory.getLogger(VSModelContext.class);
   protected RuntimeViewsheet rvs;
   private Viewsheet vs;
}
