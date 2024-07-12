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
package inetsoft.uql.viewsheet;

import inetsoft.report.*;
import inetsoft.report.TableLayout.TableCellBindingInfo;
import inetsoft.report.internal.binding.*;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.util.script.ScriptEnv;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.*;

/**
 * CalcTableVSAssembly represents one formula table assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 11.3
 * @author InetSoft Technology Corp
 */
public class CalcTableVSAssembly extends TableDataVSAssembly implements FormulaTable {
   /**
    * Constructor.
    */
   public CalcTableVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public CalcTableVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.FORMULA_TABLE_ASSET;
   }

   /**
    * Get table layout.
    */
   @Override
   public TableLayout getTableLayout() {
      return getCalcTableVSAssemblyInfo().getTableLayout();
   }

   /**
    * Set table layout.
    */
   @Override
   public void setTableLayout(TableLayout layout) {
      getCalcTableVSAssemblyInfo().setTableLayout(layout);
   }

   /**
    * Get the calc table lens in this assembly.
    */
   @Override
   public TableLens getBaseTable() {
      return lens;
   }

   /**
    * Set the calc table lens.
    */
   @Override
   public void setTable(TableLens table) {
      this.lens = table;

      // calc table lens needs report for script
      if(table instanceof CalcTableLens) {
         ((CalcTableLens) table).setReport(null);
         ((CalcTableLens) table).setElement(this);
      }
   }

   /**
    * Get the source attr.
    */
   @Override
   public XSourceInfo getXSourceInfo() {
      return getSourceInfo();
   }

   /**
    * Get the id of this element.
    */
   @Override
   public String getID() {
      return getAbsoluteName();
   }

   /**
    * Set the script environment.
    */
   public void setScriptEnv(ScriptEnv senv) {
      this.senv = senv;
   }

   /**
    * Get the script environment.
    * @return the script enrironment.
    */
   @Override
   public ScriptEnv getScriptEnv() {
      return senv;
   }

   /**
    * Set script base table.
    */
   public void setScriptTable(TableLens table) {
      this.scriptTable = table;
   }

   /**
    * Get script base table.
    */
   @Override
   public TableLens getScriptTable() {
      return scriptTable;
   }

   /**
    * Get calc table assembly info.
    * @return calc table assembly info.
    */
   protected CalcTableVSAssemblyInfo getCalcTableVSAssemblyInfo() {
      return (CalcTableVSAssemblyInfo) getInfo();
   }

   /**
    * Get the highlight attr.
    */
   @Override
   protected TableHighlightAttr getHighlightAttr() {
      return getCalcTableVSAssemblyInfo().getHighlightAttr();
   }

   /**
    * Get the hyperlink attr.
    */
   @Override
   protected TableHyperlinkAttr getHyperlinkAttr() {
      return getCalcTableVSAssemblyInfo().getHyperlinkAttr();
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new CalcTableVSAssemblyInfo();
   }

   /**
    * Get the binding datarefs.
    */
   @Override
   public DataRef[] getBindingRefs() {
      return getBindingRefs0(getCalcTableVSAssemblyInfo());
   }

   @Override
   public DataRef[] getBindingRefs(ColumnSelection sourceColumnSelection) {
      return getBindingRefs0(getCalcTableVSAssemblyInfo(), sourceColumnSelection);
   }

   /**
    * Get the column selection used in calc table.
    */
   public ColumnSelection getColumnSelection(CalcTableVSAssemblyInfo cinfo) {
      ColumnSelection cols = new ColumnSelection();
      DataRef[] refs = getBindingRefs0(cinfo);

      for(int i = 0; i < refs.length; i++) {
         cols.addAttribute(refs[i]);
      }

      return cols;
   }

   /**
    * Get the binding datarefs.
    */
   private DataRef[] getBindingRefs0(CalcTableVSAssemblyInfo calcInfo) {
      return getBindingRefs0(calcInfo, null);
   }

   /**
    * Get the binding datarefs.
    */
   private DataRef[] getBindingRefs0(CalcTableVSAssemblyInfo calcInfo,
                                     ColumnSelection sourceColumnSelection)
   {
      TableLayout layout = calcInfo.getTableLayout();

      if(layout == null) {
         return new DataRef[0];
      }

      List<CellBindingInfo> cinfos = layout.getCellInfos(true);
      List<DataRef> datarefs = new ArrayList<>();

      for(int i = 0; i < cinfos.size(); i++) {
         TableCellBindingInfo cinfo = (TableCellBindingInfo) cinfos.get(i);

         if(cinfo == null) {
            continue;
         }

         String value = cinfo.getValue();

         if(value == null) {
            continue;
         }

         if(cinfo.getType() == TableCellBinding.BIND_COLUMN) {
            TableCellBinding cell = cinfo.getCellBinding();

            if(cell.getExpression() != null) {
               ExpressionRef eref = new ExpressionRef();
               eref.setExpression(cell.getExpression());

               Enumeration e = eref.getAttributes();

               while(e.hasMoreElements()) {
                  AttributeRef ref = (AttributeRef) e.nextElement();
                  String nattr = VSLayoutTool.getOriginalColumn(
                     ref.getAttribute());

                  ColumnRef column = new ColumnRef(
                     new AttributeRef(ref.getEntity(), nattr));

                  if(!datarefs.contains(column)) {
                     datarefs.add(column);
                  }
               }

               continue;
            }

            String formula = BindingTool.getFormulaString(cinfo.getFormula());

            if(formula != null && !"none".equalsIgnoreCase(formula) &&
               value.indexOf(formula) >= 0)
            {
               value = value.substring(formula.length() + 1,
                       value.length() - 1);
            }
            else if(sourceColumnSelection == null ||
               sourceColumnSelection.getAttribute(value)  == null)
            {
               value = VSLayoutTool.getOriginalColumn(value);
            }

            AttributeRef ref = new AttributeRef(value);
            ColumnRef column = new ColumnRef(ref);

            if(!datarefs.contains(column)) {
               datarefs.add(column);
            }

            String second = BindingTool.getSecondFormula(cinfo.getFormula());

            if(second != null) {
               ref = new AttributeRef(second);
               column = new ColumnRef(ref);

               if(!datarefs.contains(column)) {
                  datarefs.add(column);
               }
            }
         }
         else if(cinfo.getType() == TableCellBinding.BIND_FORMULA) {
            String[] fields = RealtimeGenerator.parseFieldFromScript(value);

            for(int j = 0; j < fields.length; j++) {
               AttributeRef ref = new AttributeRef(fields[j]);
               ColumnRef column = new ColumnRef(ref);

               if(!datarefs.contains(column)) {
                  datarefs.add(column);
               }
            }
         }
      }

      if(calcInfo.getSortInfo() != null) {
         SortInfo sinfo = calcInfo.getSortInfo();
         SortRef[] sorts = sinfo.getSorts();

         for(int i = 0; i < sorts.length; i++) {
            ColumnRef column = (ColumnRef) sorts[i].getDataRef();

            if(column != null && !datarefs.contains(column)) {
               datarefs.add(column);
            }
         }
      }

      return datarefs.toArray(new DataRef[] {});
   }

   /**
    * For calc tables, remove cells referencing deleted references
    */
   @Override
   public void removeBindingCol(String ref) {
      super.removeBindingCol(ref);

      if(ref != null) {
         TableLayout tlayout = this.getTableLayout();
         BaseLayout.Region[] regions = tlayout.getRegions();

         for(int i = 0; i < regions.length; i++) {
            BaseLayout.Region region = regions[i];

            for(int r = 0; r < region.getRowCount(); r++) {
               for(int c = 0; c < region.getColCount(); c++) {
                  CellBinding cellBinding = region.getCellBinding(r, c);

                  if(cellBinding != null && ref.equals(cellBinding.getValue())) {
                     region.setCellBinding(r, c, new TableCellBinding());
                  }
               }
            }
         }
      }
   }

   /**
    * Get the sort info.
    * @return the sort info.
    */
   @Override
   public SortInfo getSortInfo() {
      return getCalcTableVSAssemblyInfo().getSortInfo();
   }

   /**
    * Set the sort info.
    * @param info the specified sort info.
    */
   @Override
   public void setSortInfo(SortInfo info) {
      getCalcTableVSAssemblyInfo().setSortInfo(info);
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      UserVariable[] vars = super.getAllVariables();
      TableLayout layout = getTableLayout();

      if(layout == null) {
         return vars;
      }

      Enumeration e = layout.getAllVariables();
      List<UserVariable> nvars = new ArrayList<>();

      while(e.hasMoreElements()) {
         UserVariable variable = (UserVariable) e.nextElement();

         if(!nvars.contains(variable)) {
            nvars.add(variable);
         }
      }

      for(UserVariable variable : vars) {
         if(!nvars.contains(variable)) {
            nvars.add(variable);
         }
      }

      vars = nvars.toArray(new UserVariable[0]);
      return vars;
   }

   /**
    * Get the assemblies depended on by its output values.
    * @param set the set stores the assemblies depended on.
    */
   @Override
   public void getDependeds(Set<AssemblyRef> set) {
      super.getDependeds(set);

      //Consider the conditions defined in the name group
      Viewsheet vs = getViewsheet();
      CalcTableVSAssemblyInfo calcTableVSAssemblyInfo = getCalcTableVSAssemblyInfo();
      TableLayout layout = calcTableVSAssemblyInfo.getTableLayout();

      if(lens == null) {
         return;
      }

      int row = lens.getRowCount();
      int col = lens.getColCount();

      for(int i = 0; i < row; i++) {
         for(int j = 0; j < col; j++) {
            TableCellBinding bind = (TableCellBinding) layout.getCellBinding(i, j);

            if(bind == null) {
               continue;
            }

            OrderInfo orderInfo = bind.getOrderInfo(false);

            if(orderInfo == null) {
               continue;
            }

            XNamedGroupInfo namedGroupInfo = orderInfo.getNamedGroupInfo();

            if(namedGroupInfo instanceof ExpertNamedGroupInfo) {
               ExpertNamedGroupInfo expertNamedGroupInfo = (ExpertNamedGroupInfo) namedGroupInfo;
               Set<String> conditionKeys = expertNamedGroupInfo.getGroupConditionKeys();

               for(String key : conditionKeys) {
                  ConditionList conditionList = expertNamedGroupInfo.getGroupCondition(key);
                  VSUtil.getConditionDependeds(conditionList, set, vs);
               }
            }
         }
      }
   }

   /**
    * Get assemblies depended on by the javascript attached to this assembly.
    * @param set the set stores the assemblies depended on.
    */
   @Override
   public void getScriptReferencedAssets(Set<AssemblyRef> set) {
      super.getScriptReferencedAssets(set);
      TableLayout layout = getTableLayout();

      for(int i = 0; i < layout.getRegionCount(); i ++) { // Iterate through formula cells
         BaseLayout.Region tregion = layout.getRegion(i);

         for(int row = 0; row < tregion.getRowCount(); row ++) {
            for(int col = 0; col < tregion.getColCount(); col ++) {
               TableCellBinding bind = (TableCellBinding) tregion.getCellBinding(row, col);

               if(bind != null && (bind.getType() == CellBinding.BIND_COLUMN ||
                  bind.getType() == CellBinding.BIND_FORMULA))
               {
                  VSUtil.getReferencedAssets(bind.getValue(), set, getViewsheet(), this);
               }
            }
         }
      }
   }

   /**
    * Write the state.
    * @param writer the specified print writer.
    */
   @Override
   protected void writeStateContent(PrintWriter writer, boolean runtime) {
      super.writeStateContent(writer, runtime);
      CalcTableVSAssemblyInfo ainfo = getCalcTableVSAssemblyInfo();

      if(ainfo != null) {
         writer.println("<state_calctable>");
         ainfo.writeXML(writer);
         writer.println("</state_calctable>");
      }
   }

   /**
    * Parse the state.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseStateContent(Element elem, boolean runtime)
      throws Exception
   {
      super.parseStateContent(elem, runtime);
      Element cnode = Tool.getChildNodeByTagName(elem, "state_calctable");

      if(cnode != null) {
         cnode = Tool.getFirstChildNode(cnode);
         CalcTableVSAssemblyInfo info = new CalcTableVSAssemblyInfo();
         info.parseXML(cnode);
         setVSAssemblyInfo(info);
      }
   }

   private ScriptEnv senv;
   private TableLens lens;
   private TableLens scriptTable;
}
