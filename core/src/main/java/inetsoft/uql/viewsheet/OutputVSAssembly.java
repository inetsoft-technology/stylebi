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

import inetsoft.report.Hyperlink;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.uql.ConditionList;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.viewsheet.internal.OutputVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.*;

/**
 * OutputVSAssembly represents one output assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class OutputVSAssembly extends AbstractVSAssembly
   implements DynamicBindableVSAssembly
{
   /**
    * Constructor.
    */
   public OutputVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public OutputVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Get the OutputVSAssemblyInfo.
    * @return the OutputVSAssemblyInfo.
    */
   protected OutputVSAssemblyInfo getOutputVSAssemblyInfo() {
      return (OutputVSAssemblyInfo) getInfo();
   }

   /**
    * Get the worksheet assemblies depended on.
    * @return the worksheet assemblies depended on.
    */
   @Override
   public AssemblyRef[] getDependedWSAssemblies() {
      Worksheet ws = getWorksheet();
      BindingInfo info = getBindingInfo();
      String table = info == null ? null : info.getTableName();
      Assembly assembly = ws == null || table == null ? null : ws.getAssembly(table);

      if(assembly instanceof TableAssembly) {
         return new AssemblyRef[] {new AssemblyRef(AssemblyRef.INPUT_DATA,
            assembly.getAssemblyEntry())};
      }

      return new AssemblyRef[0];
   }

   /**
    * Get the assemblies depended on by its output values.
    * @param set the set stores the assemblies depended on.
    */
   @Override
   public void getDependeds(Set<AssemblyRef> set) {
      super.getDependeds(set);
      VSUtil.getConditionDependeds(getPreConditionList(), set, getViewsheet());

      // maintain brush dependency properly. When chart has brush selection,
      // the data viewsheet assembly should apply the brush selection as
      // a condition list. Therefore some data might be filtered out
      String tname = getTableName();

      if(tname == null || tname.length() == 0) {
         return;
      }

      Viewsheet vs = getViewsheet();
      Assembly[] arr = vs.getAssemblies();
      ChartVSAssembly target = null;

      for(Assembly assembly : arr) {
         if(!(assembly instanceof ChartVSAssembly)) {
            continue;
         }

         ChartVSAssembly chart = (ChartVSAssembly) assembly;

         if(!tname.equals(chart.getTableName())) {
            continue;
         }

         // no selection
         if(!chart.containsBrushSelection()) {
            continue;
         }

         // the source chart contains brush selection?
         if(chart.getName().equals(getName())) {
            continue;
         }

         target = chart;
         break;
      }

      if(target == null) {
         target = (ChartVSAssembly) vs.getBrush(tname);

         if(target != null && Objects.equals(getName(), target.getName())) {
            target = null;
         }
      }

      if(target != null) {
         set.add(new AssemblyRef(AssemblyRef.OUTPUT_DATA, target.getAssemblyEntry()));
      }
   }

   /**
    * Get the depending worksheet assemblies to modify.
    * @return the depending worksheet assemblies to modify.
    */
   @Override
   public AssemblyRef[] getDependingWSAssemblies() {
      return new AssemblyRef[0];
   }

   /**
    * Get the binding info.
    * @return the binding info of this assembly info.
    */
   public BindingInfo getBindingInfo() {
      return getOutputVSAssemblyInfo().getBindingInfo();
   }

   /**
    * Get the table/column binding information.
    * @return the scalar binding info of this assembly info.
    */
   public ScalarBindingInfo getScalarBindingInfo() {
      return getOutputVSAssemblyInfo().getScalarBindingInfo();
   }

   /**
    * Set the table/column binding information.
    * @param binding the specified scalar binding info.
    */
   public void setScalarBindingInfo(ScalarBindingInfo binding) {
      getOutputVSAssemblyInfo().setScalarBindingInfo(binding);
   }

   /**
    * Get the target table.
    * @return the target table.
    */
   @Override
   public String getTableName() {
      BindingInfo source = getBindingInfo();

      return source == null ? null : source.getTableName();
   }

   public Integer getSourceType() {
      BindingInfo source = getBindingInfo();

      return source == null ? null : source.getType();
   }

   /**
    * Set the name of the target table.
    * @param table the specified name of the target table.
    */
   @Override
   public void setTableName(String table) {
      BindingInfo source = getBindingInfo();

      if(source != null) {
         source.setTableName(table);
      }
      else if(table != null) {
         throw new RuntimeException("Binding not defined on assembly: " +
            getName());
      }
   }

   /**
    * Set the uri.
    * @param uri the specified service request uri.
    */
   public void setLinkURI(String uri) {
      getOutputVSAssemblyInfo().setLinkURI(uri);
   }

   /**
    * Get the specified service request uri.
    * @return the uri.
    */
   public String getLinkURI() {
      return getOutputVSAssemblyInfo().getLinkURI();
   }

   /**
    * Check if this data assembly only depends on selection assembly.
    * @return <tt>true</tt> if it is only changed by the selection assembly,
    * <tt>false</tt> otherwise.
    */
   @Override
   public boolean isStandalone() {
      Set<AssemblyRef> set = new HashSet<>();
      getDependeds(set);

      if(!isStandalone(set)) {
         return false;
      }

      set.clear();
      getViewDependeds(set, false);

      if(!isStandalone(set)) {
         return false;
      }

      // @by stephenwebster, For #195
      // It is possible that the assembly is dependent on the result of
      // the values of other elements referenced through script.
      set.clear();
      getScriptReferencedAssets(set);

      return isStandalone(set);
   }

   /**
    * Check if the dependency set is standalone.
    * @param set the specified dependency set.
    * @return <tt>true</tt> if standalone, <tt>false</tt> otherwise.
    */
   private boolean isStandalone(Set<AssemblyRef> set) {
      for(AssemblyRef ref : set) {
         AssemblyEntry entry = ref.getEntry();

         if(!entry.isWSAssembly() && !entry.getName().equals(getName())) {
            return false;
         }
      }

      return true;
   }

   /**
    * Get the view assemblies depended on.
    * @param set the set stores the presentation assemblies depended on.
    * @param self <tt>true</tt> to include self, <tt>false</tt> otherwise.
    */
   @Override
   public void getViewDependeds(Set<AssemblyRef> set, boolean self) {
      super.getViewDependeds(set, self);
      VSUtil.getHighlightDependeds(
         getOutputVSAssemblyInfo().getHighlightGroup(), set, getViewsheet());
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname) {
      super.renameDepended(oname, nname);

      VSUtil.renameHighlightDependeds(oname, nname,
         getOutputVSAssemblyInfo().getHighlightGroup());
   }

   /**
    * Called when the highlight or value is changed.
    */
   public void updateHighlight(VariableTable vars, Object querySandbox) {
      getOutputVSAssemblyInfo().updateHighlight(vars, querySandbox);
   }

   /**
    * Determines if a highlight is applied to the assembly.
    *
    * @param vars         the query variables.
    * @param querySandbox the query sandbox.
    *
    * @return <tt>true</tt> if a highlight is applied; <tt>false</tt> otherwise.
    */
   public boolean isHighlighted(VariableTable vars, Object querySandbox) {
      return getOutputVSAssemblyInfo().isHighlighted(vars, querySandbox);
   }

   @Override
   public DataRef[] getBindingRefs() {
      List<DataRef> datarefs = new ArrayList<>();
      ScalarBindingInfo sbinfo = getScalarBindingInfo();
      DataRef ref = sbinfo == null ? null : sbinfo.getColumn();
      DataRef ref2 = sbinfo == null ? null : sbinfo.getSecondaryColumn();

      if(ref != null) {
         datarefs.add(ref);
      }

      if(ref2 != null) {
         datarefs.add(ref2);
      }

      return datarefs.toArray(new DataRef[] {});
   }

   @Override
   public DataRef[] getAllBindingRefs() {
      List<DataRef> datarefs = getBindingRefList();
      VSUtil.addConditionListRef(getPreConditionList(), datarefs);
      Viewsheet vs = getViewsheet();
      Hyperlink hlink = getOutputVSAssemblyInfo().getHyperlink();
      VSUtil.addHyperlinkRef(hlink, datarefs, vs.getCalcFields(getTableName()));

      return datarefs.toArray(new DataRef[] {});
   }

   /**
    * Set the pre-condition list defined in this output viewsheet assembly.
    */
   @Override
   public int setPreConditionList(ConditionList conds) {
      return getOutputVSAssemblyInfo().setPreConditionList(conds);
   }

   /**
    * Get the pre-condition list.
    */
   @Override
   public ConditionList getPreConditionList() {
      return getOutputVSAssemblyInfo().getPreConditionList();
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      List<UserVariable> list = new ArrayList<>();
      ConditionList preconds = getPreConditionList();
      UserVariable[] vars = preconds == null ? new UserVariable[0] :
         preconds.getAllVariables();
      Viewsheet.mergeVariables(list, vars);

      if(getOutputVSAssemblyInfo().getHighlightGroup() != null) {
         Viewsheet.mergeVariables(list,
            getOutputVSAssemblyInfo().getHighlightGroup().getAllVariables());
      }

      vars = new UserVariable[list.size()];
      list.toArray(vars);
      return vars;
   }

   /**
    * Write the state.
    * @param writer the specified print writer.
    */
   @Override
   protected void writeStateContent(PrintWriter writer, boolean runtime) {
      super.writeStateContent(writer, runtime);

      if(runtime) {
         writeAnnotations(getOutputVSAssemblyInfo(), writer);
         writeConditions(getOutputVSAssemblyInfo(), writer);
         writeHighlights(getOutputVSAssemblyInfo(), writer);
      }
   }

   /**
    * Parse the state.
    * @param elem the specified xml element.
    * @param runtime if is runtime mode, default is true.
    */
   @Override
   protected void parseStateContent(Element elem, boolean runtime)
      throws Exception
   {
      super.parseStateContent(elem, runtime);

      if(runtime) {
         parseAnnotations(elem, getOutputVSAssemblyInfo());
         parseConditions(elem, getOutputVSAssemblyInfo());
         parseHighlights(elem, getOutputVSAssemblyInfo());
      }
   }

   /**
    * Write runtime conditions.
    */
   private void writeConditions(OutputVSAssemblyInfo info, PrintWriter writer) {
      ConditionList conditions = info.getPreConditionList();

      if(conditions != null) {
         writer.println("<state_conditions>");
         conditions.writeXML(writer);
         writer.println("</state_conditions>");
      }
      else {
         writer.println("<state_conditions/>");
      }
   }

   /**
    * Parse runtime conditions.
    */
   private void parseConditions(Element elem, OutputVSAssemblyInfo info)
      throws Exception
   {
      Element conditionsNode =
         Tool.getChildNodeByTagName(elem, "state_conditions");

      if(conditionsNode != null) {
         conditionsNode = Tool.getFirstChildNode(conditionsNode);
         ConditionList conditions = new ConditionList();

         if(conditionsNode != null) {
            conditions.parseXML(conditionsNode);
         }

         info.setPreConditionList(conditions);
      }
   }

   /**
    * Write runtime highlight.
    */
   private void writeHighlights(OutputVSAssemblyInfo info, PrintWriter writer) {
      HighlightGroup highlights = info.getHighlightGroup();

      if(highlights != null) {
         writer.println("<state_highlights>");
         highlights.writeXML(writer);
         writer.println("</state_highlights>");
      }
      else {
         writer.println("<state_highlights/>");
      }
   }

   /**
    * Parse runtime highlight.
    */
   private void parseHighlights(Element elem, OutputVSAssemblyInfo info)
      throws Exception
   {
      Element highlightsNode =
            Tool.getChildNodeByTagName(elem, "state_highlights");

      if(highlightsNode != null) {
         highlightsNode = Tool.getFirstChildNode(highlightsNode);
         HighlightGroup highlights = new HighlightGroup();

         if(highlightsNode != null) {
            highlights.parseXML(highlightsNode);
         }

         info.setHighlightGroup(highlights);
      }
   }

   @Override
   public void removeBindingCol(String ref) {
      // it only show to one col data, clear the col means clear table?
      setTableName(null);
   }

   @Override
   public void renameBindingCol(String oname, String nname) {
      DataRef dref = getScalarBindingInfo().getColumn();
      ScalarBindingInfo sbinfo = getScalarBindingInfo();
      String columnValue = sbinfo.getColumnValue();
      String column2Value = sbinfo.getColumn2Value();

      if(dref != null && Tool.equals(oname, dref.getName())) {
         VSUtil.renameDataRef(dref, nname);
         sbinfo.setColumnValue(nname);
      }
      else if(Tool.equals(oname, columnValue)) {
         sbinfo.setColumnValue(nname);
      }

      DataRef sref = sbinfo.getSecondaryColumn();

      if(sref != null && Tool.equals(oname, sref.getName())) {
         VSUtil.renameDataRef(sref, nname);
         sbinfo.setColumn2Value(nname);
      }
      else if(Tool.equals(oname, column2Value)) {
         sbinfo.setColumn2Value(nname);
      }

      VSUtil.renameConditionListRef(getPreConditionList(), oname, nname);
      Hyperlink hlink = getOutputVSAssemblyInfo().getHyperlink();
      VSUtil.renameHyperlinkRef(hlink, oname, nname);
   }

   /**
    * Change calc type: detail and aggregate.
    */
   @Override
   public void changeCalcType(String refName, CalculateRef ref) {
      // do nothing
   }

   /**
    * Get the value of this output viewsheet assembly.
    * @return the value of this output viewsheet assembly.
    */
   public abstract Object getValue();

   /**
    * Set the value to this output viewsheet assembly.
    * @param val the specified value.
    */
   public abstract void setValue(Object val);
}
