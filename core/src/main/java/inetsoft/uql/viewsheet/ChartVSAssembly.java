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
package inetsoft.uql.viewsheet;

import inetsoft.report.Hyperlink;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.*;

/**
 * ChartVSAssembly represents one chart assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class ChartVSAssembly extends DataVSAssembly
   implements CubeVSAssembly, TitledVSAssembly, DrillFilterVSAssembly, DateCompareAbleAssembly
{
   /**
    * Constructor.
    */
   public ChartVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public ChartVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new ChartVSAssemblyInfo();
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.CHART_ASSET;
   }

   /**
    * Set brushing selections.
    * @param bselection the specified brushing selections.
    */
   public int setBrushSelection(VSSelection bselection) {
      return getChartInfo().setBrushSelection(bselection);
   }

   /**
    * Get brushing selections.
    * @return the brushing selections.
    */
   public VSSelection getBrushSelection() {
      return getChartInfo().getBrushSelection();
   }

   /**
    * Test if contains brush selections.
    * @return true if contains brush selection, false otherwise.
    */
   public boolean containsBrushSelection() {
      VSSelection selection = getChartInfo().getBrushSelection();
      return selection != null && !selection.isEmpty();
   }

   /**
    * Get the brush condition list.
    * @param expothers true to expand 'Others' into individual items.
    */
   public ConditionList getBrushConditionList(ColumnSelection cols, boolean expothers) {
      return getChartInfo().getBrushConditionList(cols, expothers);
   }

   /**
    * Get the detail selection.
    */
   public VSSelection getDetailSelection() {
      return dselection;
   }

   /**
    * Get share from assembly for date comparison.
    * @return share assembly name
    */
   @Override
   public String getComparisonShareFromAssembly() {
      return getChartInfo().getComparisonShareFrom();
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      List<UserVariable> list = new ArrayList<>();
      Viewsheet.mergeVariables(list, super.getAllVariables());
      VSChartInfo chartInfo = getVSChartInfo();

      if(chartInfo.getHighlightGroup() != null) {
         Viewsheet.mergeVariables(list, chartInfo.getHighlightGroup().getAllVariables());
      }

      VSDataRef[] dataRefs = chartInfo.getFields();

      if(dataRefs.length > 0) {
         collectHLVariables(list, dataRefs);
      }
      else {
         //if runtime datarefs are empty
         collectHLVariables(list, chartInfo.getXFields());
         collectHLVariables(list, chartInfo.getYFields());
         collectHLVariables(list, chartInfo.getGroupFields());
      }

      collectHLVariables(list, chartInfo.getRuntimeDateComparisonRefs());

      UserVariable[] vars = new UserVariable[list.size()];
      list.toArray(vars);
      return vars;
   }

   private void collectHLVariables(List<UserVariable> list, VSDataRef[] refs) {
      for(VSDataRef dataRef:refs) {
         if(dataRef instanceof HighlightRef) {
            ((HighlightRef) dataRef).highlights()
               .forEach(hg -> Viewsheet.mergeVariables(list, hg.getAllVariables()));
         }
      }
   }

   /**
    * Set the detail selection.
    */
   public void setDetailSelection(VSSelection selection) {
      this.dselection = selection;
   }

   /**
    * Get the detail condition list.
    */
   public ConditionList getDetailConditionList(ColumnSelection cols) {
      VSSelection selection = getDetailSelection();
      return getChartInfo().getConditionList(selection, cols);
   }

   /**
    * Check if selection is valid in the given column selection.
    */
   public boolean isSelectionValid(VSSelection selection, ColumnSelection cols)
   {
      return getChartInfo().isSelectionValid(selection, cols);
   }

   /**
    * Set zoom selections.
    */
   public int setZoomSelection(VSSelection zselection) {
      return getChartInfo().setZoomSelection(zselection);
   }

   /**
    * Get zoom selections.
    */
   public VSSelection getZoomSelection() {
      return getChartInfo().getZoomSelection();
   }

   /**
    * Set exclude selections.
    */
   public int setExcludeSelection(VSSelection zselection) {
      return getChartInfo().setExcludeSelection(zselection);
   }

   /**
    * Get exclude selections.
    */
   public VSSelection getExcludeSelection() {
      return getChartInfo().getExcludeSelection();
   }

   /**
    * Get the zoom condition list.
    */
   public ConditionList getZoomConditionList(ColumnSelection cols) {
      return getChartInfo().getZoomConditionList(cols);
   }

   /**
    * Get the condition list from a selection.
    * @param selection the specified selection.
    * @param cols the specified column selection.
    */
   public ConditionList getConditionList(VSSelection selection, ColumnSelection cols) {
      return getChartInfo().getConditionList(selection, cols);
   }

   /**
    * Get the chart descriptor.
    * @return the chart desciptor.
    */
   public ChartDescriptor getChartDescriptor() {
      return getChartInfo().getChartDescriptor();
   }

   /**
    * Set the chart descriptor.
    * @param desc the specified chart descriptor.
    */
   public void setChartDescriptor(ChartDescriptor desc) {
      getChartInfo().setChartDescriptor(desc);
   }

   public void setTitleVisible(boolean titleVis) {
      getChartInfo().setTitleVisibleValue(titleVis);
   }

   /**
    * Set the assembly info.
    * @param info the specified viewsheet assembly info.
    * @return the hint to reset view, input data or output data.
    */
   @Override
   public int setVSAssemblyInfo(VSAssemblyInfo info) {
      int hint = super.setVSAssemblyInfo(info);

      if((hint & VSAssembly.INPUT_DATA_CHANGED) ==
         VSAssembly.INPUT_DATA_CHANGED)
      {
         hint = hint | setBrushSelection(null);
         hint = hint | setZoomSelection(null);
         hint = hint | setExcludeSelection(null);
      }

      return hint;
   }

   /**
    * Get the xcube.
    * @return the xcube.
    */
   @Override
   public XCube getXCube() {
      return getChartInfo().getXCube();
   }

   /**
    * Set the xcube.
    * @param cube the specified xcube.
    */
   @Override
   public void setXCube(XCube cube) {
      getChartInfo().setXCube(cube);
   }

   /**
    * Get the chart info.
    * @return the chart info.
    */
   public VSChartInfo getVSChartInfo() {
      return getChartInfo().getVSChartInfo();
   }

   /**
    * Set the chart info.
    * @param info the chart info.
    */
   public void setVSChartInfo(VSChartInfo info) {
      getChartInfo().setVSChartInfo(info);
   }

   /**
    * Get chart assembly info.
    * @return the chart assembly info.
    */
   public ChartVSAssemblyInfo getChartInfo() {
      return (ChartVSAssemblyInfo) getInfo();
   }

   /**
    * Get the chart hierarchical tree.
    */
   public ChartTree getChartTree() {
      return getChartInfo().getChartTree();
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
      VSChartInfo cinfo = getVSChartInfo();

      if(cinfo != null) {
         cinfo.renameDepended(oname, nname, getViewsheet());
      }

      renameHighlightDependeds(oname, nname);
   }

   private void renameHighlightDependeds(String oname, String nname) {
      VSChartInfo cinfo = getVSChartInfo();

      if(cinfo == null) {
         return;
      }

      VSUtil.renameHighlightDependeds(oname, nname, cinfo.getHighlightGroup());

      ChartRef[] refs = cinfo.getBindingRefs(false);

      for(int i = 0; i < refs.length; i++) {
         if(refs[i] instanceof HighlightRef) {
            ((HighlightRef) refs[i]).highlights()
               .forEach(hl -> VSUtil.renameHighlightDependeds(oname, nname, hl));
         }
      }
   }

   /**
    * Check if this viewsheet assembly has a cube.
    * @return <tt>true</tt> if has a cube, <tt>false</tt> otherwise.
    */
   public boolean hasCube() {
      XSourceInfo source = getSourceInfo();

      if(source == null || source.isEmpty()) {
         return false;
      }

      if(source.getType() == XSourceInfo.CUBE) {
         return true;
      }

      XCube cube = getXCube();

      if(cube instanceof VSCube) {
         return !((VSCube) cube).isEmpty();
      }

      return false;
   }

   /**
    * Write the state.
    * @param writer the specified print writer.
    */
   @Override
   protected void writeStateContent(PrintWriter writer, boolean runtime) {
      super.writeStateContent(writer, runtime);
      writeDrillState(writer);

      // write brush
      VSSelection selection = getBrushSelection();

      if(selection != null) {
         writer.println("<state_brush>");
         selection.writeXML(writer);
         writer.println("</state_brush>");
      }

      // write zoom
      selection = getZoomSelection();

      if(selection != null) {
         writer.println("<state_zoom>");
         selection.writeXML(writer);
         writer.println("</state_zoom>");
      }

      selection = getExcludeSelection();

      if(selection != null) {
         writer.println("<state_exclude>");
         selection.writeXML(writer);
         writer.println("</state_exclude>");
      }

      ChartVSAssemblyInfo chartInfo = getChartInfo();

      if(chartInfo != null && chartInfo.getTitle()!=null) {
         writer.format("<titleVisible>%s</titleVisible>%n", chartInfo.getTitleVisibleValue());
      }

      if(chartInfo != null && (chartInfo.getDateComparisonInfo() != null ||
         !Tool.isEmptyString(chartInfo.getComparisonShareFrom())))
      {
         writer.println("<state_dateComparison>");

         if(chartInfo.getDateComparisonInfo() != null) {
            chartInfo.getDateComparisonInfo().writeXML(writer);
         }

         if(chartInfo.getComparisonShareFrom() != null) {
            writer.println("<shareFrom>");
            writer.print("<![CDATA[" + getChartInfo().getComparisonShareFrom() + "]]>");
            writer.println("</shareFrom>");
         }

         writer.println("</state_dateComparison>");
      }

      if(runtime) {
         // write chart info
         VSChartInfo cinfo = getVSChartInfo();

         if(cinfo != null) {
            String table = getSourceName();

            if(table != null) {
               writer.println("<state_table>");
               writer.print("<![CDATA[" + table + "]]>");
               writer.println("</state_table>");
            }

            writer.println("<state_info>");
            cinfo.writeXML(writer);
            writer.println("</state_info>");
         }

         // write descriptor
         ChartDescriptor descriptor = getChartDescriptor();

         if(descriptor != null) {
            writer.println("<state_descriptor>");
            descriptor.writeXML(writer);
            writer.println("</state_descriptor>");
         }

         // write the chart object's format
         VSCompositeFormat fmt = getChartInfo().getFormat();

         if(fmt!= null) {
            writer.println("<state_format>");
            fmt.writeXML(writer);
            writer.println("</state_format>");
         }

         if(chartInfo != null && chartInfo.getTitle()!=null) {
            writer.format("<titleVisible>%s</titleVisible>%n", chartInfo.getTitleVisibleValue());
         }
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
      parseDrillState(elem);

      Element znode = Tool.getChildNodeByTagName(elem, "state_zoom");

      if(znode != null) {
         VSSelection zselection = new VSSelection();
         znode = Tool.getFirstChildNode(znode);
         zselection.parseXML(znode);
         setZoomSelection(zselection);
      }
      else {
         setZoomSelection(null);
      }

      Element xnode = Tool.getChildNodeByTagName(elem, "state_exclude");

      if(xnode != null) {
         VSSelection xselection = new VSSelection();
         xnode = Tool.getFirstChildNode(xnode);
         xselection.parseXML(xnode);
         setExcludeSelection(xselection);
      }
      else {
         setExcludeSelection(null);
      }

      Element bnode = Tool.getChildNodeByTagName(elem, "state_brush");

      if(bnode != null) {
         VSSelection bselection = new VSSelection();
         bnode = Tool.getFirstChildNode(bnode);
         bselection.parseXML(bnode);
         setBrushSelection(bselection);
      }
      else {
         setBrushSelection(null);
      }

      Element dcNode = Tool.getChildNodeByTagName(elem, "state_dateComparison");

      if(dcNode != null && getChartInfo() != null) {
         DateComparisonInfo dateComparisonInfo = new DateComparisonInfo();
         dateComparisonInfo.parseXML(dcNode);

         if(dateComparisonInfo.getPeriods() != null &&
            dateComparisonInfo.getInterval() != null)
         {
            getChartInfo().setDateComparisonInfo(dateComparisonInfo);
         }
         else {
            getChartInfo().setDateComparisonInfo(null);
         }

         getChartInfo().setComparisonShareFrom(Tool.getChildValueByTagName(dcNode, "shareFrom"));
      }
      else if(getChartInfo() != null){
         getChartInfo().setDateComparisonInfo(null);
         getChartInfo().setComparisonShareFrom(null);
      }

      Element tableNode = Tool.getChildNodeByTagName(elem, "state_table");
      Element cnode = Tool.getChildNodeByTagName(elem, "state_info");

      if(cnode != null && getSourceName() != null &&
         getSourceName().equals(Tool.getValue(tableNode)))
      {
         cnode = Tool.getFirstChildNode(cnode);
         Boolean adhoc = null;

         if(getChartInfo() != null && getChartInfo().getVSChartInfo() != null) {
            adhoc = getChartInfo().getVSChartInfo().isAdhocEnabled();
         }

         VSChartInfo cinfo = VSChartInfo.createVSChartInfo(cnode);

         if(adhoc != null) {
            cinfo.setAdhocEnabled(adhoc);
         }

         setVSChartInfo(cinfo);
      }

      Element descNode = Tool.getChildNodeByTagName(elem, "state_descriptor");

      if(descNode != null) {
         descNode = Tool.getFirstChildNode(descNode);
         ChartDescriptor descriptor = new ChartDescriptor();
         descriptor.parseXML(descNode);
         setChartDescriptor(descriptor);
      }

      Element fmtNode = Tool.getChildNodeByTagName(elem, "state_format");

      if(fmtNode != null) {
         fmtNode = Tool.getFirstChildNode(fmtNode);
         VSCompositeFormat fmt = new VSCompositeFormat();
         fmt.parseXML(fmtNode);
         getChartInfo().setFormat(fmt);
      }

      String val = Tool.getChildValueByTagName(elem, "titleVisible");

      if(val != null) {
         setTitleVisible("true".equals(val));
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public ChartVSAssembly clone() {
      try {
         return (ChartVSAssembly) super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone ChartVSAssembly", ex);
      }

      return null;
   }

   /**
    * Check if contains script.
    */
   @Override
   public boolean containsScript() {
      String script = getScript();
      return getVSAssemblyInfo().isScriptEnabled() &&
             script != null && script.trim().length() > 0;
   }

   /**
    * Get the source name.
    * @return the source name.
    */
   private String getSourceName() {
      SourceInfo source = getSourceInfo();
      return source == null || source.isEmpty() ?
         null : source.getPrefix()  + "." + source.getSource();
   }

   /**
    * Get the worksheet assemblies depended on.
    * @return the worksheet assemblies depended on.
    */
   @Override
   public AssemblyRef[] getDependedWSAssemblies() {
      AssemblyRef[] refs = super.getDependedWSAssemblies();
      String script = getScript();
      Set<AssemblyRef> set = new HashSet<>();

      if(getVSAssemblyInfo().isScriptEnabled()) {
         VSUtil.getReferencedAssets(script, set, getViewsheet(), this);
      }

      Collections.addAll(set, refs);
      List<AssemblyRef> list = new ArrayList<>(set);

      for(int i = list.size() - 1; i >= 0; i--) {
         AssemblyRef ref = list.get(i);

         if(ref.getEntry().getType() != AbstractSheet.TABLE_ASSET) {
            list.remove(i);
         }
      }

      refs = new AssemblyRef[list.size()];
      list.toArray(refs);
      return refs;
   }

   /**
    * Get the view assemblies depended on.
    * @param set the set stores the presentation assemblies depended on.
    * @param self <tt>true</tt> to include self, <tt>false</tt> otherwise.
    */
   @Override
   public void getViewDependeds(Set<AssemblyRef> set, boolean self) {
      super.getViewDependeds(set, self);
      VSChartInfo cinfo = getVSChartInfo();
      Viewsheet vs = getViewsheet();
      HighlightGroup group = cinfo.getHighlightGroup();
      VSUtil.getHighlightDependeds(group, set, vs);
      ChartRef[] refs = cinfo.getXFields();
      getHighlightDependeds(set, refs);
      refs = cinfo.getYFields();
      getHighlightDependeds(set, refs);
   }

   /**
    * Get highlight dependeds from bound measures.
    */
   private void getHighlightDependeds(Set<AssemblyRef> set, ChartRef[] refs) {
      Viewsheet vs = getViewsheet();

      for(ChartRef ref : refs) {
         if(ref instanceof HighlightRef) {
            ((HighlightRef) ref).highlights()
               .forEach(group -> VSUtil.getHighlightDependeds(group, set, vs));
         }
      }
   }

   /**
    * Get the predefined dimension/measure information.
    */
   @Override
   public AggregateInfo getAggregateInfo() {
      VSChartInfo cinfo = getVSChartInfo();
      return cinfo == null ? null : cinfo.getAggregateInfo();
   }

   /**
    * Get the runtime tip view.
    * @return the runtime tip view.
    */
   public String getRuntimeTipView() {
      return getChartInfo().getRuntimeTipView();
   }

   /**
    * Get the binding data refs.
    */
   @Override
   public DataRef[] getBindingRefs() {
      VSChartInfo cinfo = getVSChartInfo();
      return cinfo == null ? new DataRef[]{} : cinfo.getRTFields();
   }

   @Override
   public DataRef[] getAllBindingRefs() {
      List<DataRef> datarefs = new ArrayList<>();
      DataRef[] refs = super.getAllBindingRefs();

      for(int i = 0; i < refs.length; i++) {
         datarefs.add(refs[i]);
      }

      // sycn the conditions in highlight
      VSChartInfo cinfo = getVSChartInfo();

      if(cinfo != null) {
         HighlightGroup grp = cinfo.getHighlightGroup();
         VSUtil.addHLConditionListRef(grp, datarefs);

         // sync hyperlinks
         Hyperlink link = info.getHyperlink();
         Viewsheet vs = getViewsheet();
         VSUtil.addHyperlinkRef(link, datarefs,
            vs.getCalcFields(getTableName()));

         // sync vsdimension
         Enumeration drefs = getXCube() == null ?
            null : getXCube().getDimensions();

         if(drefs != null) {
            while(drefs.hasMoreElements()) {
               XDimension dim = (XDimension) drefs.nextElement();

               if(dim instanceof VSDimension) {
                  VSUtil.addVSDimension((VSDimension) dim, datarefs);
               }
            }
         }

         // should rename the edit mode ref
         DataRef[] fields = cinfo.getFields();

         for(int i = 0; i < fields.length; i++) {
            if(fields[i] instanceof VSChartAggregateRef) {
               VSChartAggregateRef ref = (VSChartAggregateRef) fields[i];

               ref.highlights().forEach(hgrp -> {
                  VSUtil.addHLConditionListRef(hgrp, datarefs);
                  Hyperlink hlink = ref.getHyperlink();
                  VSUtil.addHyperlinkRef(hlink, datarefs, vs.getCalcFields(getTableName()));
               });
            }
         }
      }

      return datarefs.toArray(new DataRef[] {});
   }

   /**
    * Remove the binding ref from the element.
    */
   @Override
   public void removeBindingCol(String ref) {
      VSChartInfo cinfo = getVSChartInfo();

      if(cinfo == null) {
         return;
      }

      fixChartInfo(cinfo, ref);
      VSUtil.removeConditionListRef(getPreConditionList(), ref);

      // sync the conditions in highlight
      HighlightGroup grp = cinfo.getHighlightGroup();
      VSUtil.removeHLConditionListRef(grp, ref);

      // sync hyperlinks
      Hyperlink link = cinfo.getHyperlink();
      VSUtil.removeHyperlinkRef(link, ref);

      // sync vsdimension
      Enumeration drefs = getXCube() == null ?
         null : getXCube().getDimensions();

      if(drefs != null) {
         while(drefs.hasMoreElements()) {
            XDimension dim = (XDimension) drefs.nextElement();

            if(dim instanceof VSDimension) {
               VSUtil.removeVSDimension((VSDimension) dim, ref);
            }
         }
      }

      // should rename the edit mode ref
      DataRef[] fields = cinfo.getFields();

      for(int i = 0; i < fields.length; i++) {
         if(fields[i] instanceof VSChartAggregateRef) {
            VSChartAggregateRef aref = (VSChartAggregateRef) fields[i];
            aref.highlights().forEach(hgrp -> {
               VSUtil.removeHLConditionListRef(hgrp, ref);
               Hyperlink hlink = aref.getHyperlink();
               VSUtil.removeHyperlinkRef(hlink, ref);
            });
         }
         else if(fields[i] instanceof VSChartDimensionRef) {
            VSChartDimensionRef dref = (VSChartDimensionRef) fields[i];
            Hyperlink hlink = dref.getHyperlink();
            VSUtil.removeHyperlinkRef(hlink, ref);
         }
      }
   }

   /**
    * Rename the binding ref from the element.
    */
   @Override
   public void renameBindingCol(String oname, String nname) {
      VSChartInfo cinfo = getVSChartInfo();

      if(cinfo == null) {
         return;
      }

      fixChartInfo(cinfo, oname, nname);
      VSUtil.renameConditionListRef(getPreConditionList(), oname, nname);

      VSSelection bselecion = getBrushSelection();

      if(bselecion != null) {
         renameVSSelection(bselecion, oname, nname);
         renameVSSelection(bselecion.getOrigSelection(), oname, nname);
      }

      VSSelection zselecion = getZoomSelection();
      VSSelection xselecion = getZoomSelection();

      if(zselecion != null) {
         renameVSSelection(zselecion, oname, nname);
         renameVSSelection(zselecion.getOrigSelection(), oname, nname);
      }

      if(xselecion != null) {
         renameVSSelection(xselecion, oname, nname);
         renameVSSelection(xselecion.getOrigSelection(), oname, nname);
      }

      // sync vsdimension
      Enumeration drefs = getXCube() == null ?
         null : getXCube().getDimensions();

      if(drefs != null) {
         while(drefs.hasMoreElements()) {
            XDimension dim = (XDimension) drefs.nextElement();

            if(dim instanceof VSDimension) {
               VSUtil.renameVSDimension((VSDimension) dim, oname, nname);
            }
         }
      }

      // sync the conditions in highlight
      HighlightGroup grp = cinfo.getHighlightGroup();
      VSUtil.renameHLConditionListRef(grp, oname, nname);

      // sync hyperlinks
      Hyperlink link = cinfo.getHyperlink();
      VSUtil.renameHyperlinkRef(link, oname, nname);

      // should rename the edit mode ref
      DataRef[] fields = cinfo.getFields();

      for(int i = 0; i < fields.length; i++) {
         if(fields[i] instanceof VSChartAggregateRef) {
            VSChartAggregateRef ref = (VSChartAggregateRef) fields[i];
            ref.highlights().forEach(hgrp -> {
               VSUtil.renameHLConditionListRef(hgrp, oname, nname);
               Hyperlink hlink = ref.getHyperlink();
               VSUtil.renameHyperlinkRef(hlink, oname, nname);
            });
         }
         else if(fields[i] instanceof VSChartDimensionRef) {
            VSChartDimensionRef ref = (VSChartDimensionRef) fields[i];
            Hyperlink hlink = ref.getHyperlink();
            VSUtil.renameHyperlinkRef(hlink, oname, nname);
         }
      }
   }

   // For some chart styles, change calc type can works well, it can support binding both detail
   // and aggregate calc, do nothing.
   // For some chart styles, should remove cal if new calc type is not support.
   @Override
   public void changeCalcType(String refName, CalculateRef ref) {
      boolean baseOnDetail = ref.isBaseOnDetail();
      VSChartInfo cinfo = getVSChartInfo();

      if(cinfo == null) {
         return;
      }

      // If have invalid binding when change calc type, remove it.
      removeInvalidBindings(refName, ref.getDataType(), baseOnDetail, cinfo);
      changeBindingCalcType(refName, cinfo, ref, false);
   }

   private void removeInvalidBindings(String refName, String dtype, boolean baseOnDetail,
                                      VSChartInfo cinfo)
   {
      int type = cinfo.getChartType();
      int style = cinfo.getChartStyle();

      // if type is auto, change chart type according new calc, do not remove binding.
      if(GraphTypes.isAuto(type)) {
         return;
      }

      // For bar chart, can not binding measure on both x and y.
      if(GraphTypes.isBar(style)) {
         if(!baseOnDetail) {
            if(cinfo.containYMeasure()) {
               removeXBindingCol(refName, cinfo);
            }

            if(cinfo.containXMeasure()) {
               removeYBindingCol(refName, cinfo);
            }
         }

         return;
      }
      // For radar chart, x only support dimension.
      else if(GraphTypes.isRadar(style)) {
         // if calc change to aggregate, the remove cal from x field.
         if(!baseOnDetail) {
            removeXBindingCol(refName, cinfo);
         }

         return;
      }
      // For stock and candle, x/y only support dimension, high/low/open/close support aggregate.
      else if(GraphTypes.isStock(style) || GraphTypes.isCandle(style)) {
         if(!baseOnDetail) {
            removeXBindingCol(refName, cinfo);
            removeYBindingCol(refName, cinfo);
         }
         else {
            removeHighLowBindingCol(refName, cinfo);
         }

         return;
      }
      // For map chart, only support dimension on x/y
      else if(GraphTypes.isMap(style)) {
         if(!baseOnDetail) {
            removeXBindingCol(refName, cinfo);
            removeYBindingCol(refName, cinfo);
            removeGeoBindingCol(refName, (VSMapInfo) cinfo);
         }

         return;
      }
      // For treemap, x/y/T only support dimensions
      else if(GraphTypes.isTreemap(style)) {
         if(!baseOnDetail) {
            removeXBindingCol(refName, cinfo);
            removeYBindingCol(refName, cinfo);
            removeGroupBindingCol(refName, cinfo);
         }

         return;
      }
      else if(GraphTypes.isMekko(style)) {
         if(!baseOnDetail) {
            removeXBindingCol(refName, cinfo);
            removeGroupBindingCol(refName, cinfo);
         }
         else {
            removeYBindingCol(refName, cinfo);
         }

         return;
      }
      else if(GraphTypes.isGantt(style)) {
         if(!baseOnDetail) {
            removeXBindingCol(refName, cinfo);
            removeYBindingCol(refName, cinfo);

            if(!XSchema.isDateType(dtype)) {
               removeStartEndBindingCol(refName, cinfo);
            }
         }
         else {
            removeStartEndBindingCol(refName, cinfo);
         }

         return;
      }
      else if(GraphTypes.isFunnel(style)) {
         if(!baseOnDetail) {
            removeYBindingCol(refName, cinfo);
         }

         return;
      }
      else if(style == GraphTypes.CHART_TREE) {
         if(!baseOnDetail) {
            removeXBindingCol(refName, cinfo);
            removeYBindingCol(refName, cinfo);
            removeRelationBindingCol(refName, cinfo);
         }

         return;
      }
   }

   private ChartRef createNewCalcRef(String refName, CalculateRef ref) {
      if(ref.isBaseOnDetail()) {
         VSChartDimensionRef dim  = new VSChartDimensionRef(ref);
         dim.setGroupColumnValue(refName);
         dim.setDataType(ref.getDataType());
         dim.setRefType(ref.getRefType());

         return dim;
      }
      else {
         VSChartAggregateRef agg = new VSChartAggregateRef();
         agg.setRefType(ref.getRefType());
         agg.setColumnValue(ref.getName());
         agg.setOriginalDataType(ref.getDataType());

         return agg;
      }
   }

   private void removeXBindingCol(String refName, VSChartInfo cinfo) {
      DataRef temp = null;

      for(int i = cinfo.getXFieldCount() - 1; i > -1; i--) {
         temp = cinfo.getXField(i);

         if(equalsFieldName(refName, temp)) {
            cinfo.removeXField(i);
         }
      }
   }

   private void removeYBindingCol(String refName, VSChartInfo cinfo) {
      DataRef temp = null;

      for(int i = cinfo.getYFieldCount() - 1; i > -1; i--) {
         temp = cinfo.getYField(i);

         if(equalsFieldName(refName, temp)) {
            cinfo.removeYField(i);
         }
      }
   }

   private void removeGeoBindingCol(String refName, VSMapInfo cinfo) {
      DataRef temp = null;

      for(int i = cinfo.getGeoFieldCount() - 1; i > -1; i--) {
         temp = cinfo.getGeoFieldByName(i);

         if(equalsFieldName(refName, temp)) {
            cinfo.removeGeoField(i);
         }
      }
   }

   public void removeGeoColumns(String refName, VSChartInfo cinfo) {
      DataRef temp = null;
      ColumnSelection cols = cinfo.getGeoColumns();

      for(int i = cols.getAttributeCount() - 1 ; i > -1; i--) {
         temp = cols.getAttribute(i);

         if(equalsFieldName(refName, temp)) {
            cols.removeAttribute(i);
         }
      }

      cinfo.setGeoColumns(cols);
   }

   private void removeGroupBindingCol(String refName, VSChartInfo cinfo) {
      DataRef temp = null;

      for(int i = cinfo.getGroupFieldCount() - 1; i > -1; i--) {
         temp = cinfo.getGroupField(i);

         if(equalsFieldName(refName, temp)) {
            cinfo.removeGroupField(i);
         }
      }
   }

   private void removeHighLowBindingCol(String refName, VSChartInfo cinfo) {
      if(cinfo instanceof CandleVSChartInfo) {
         CandleVSChartInfo cdlinfo = (CandleVSChartInfo) cinfo;

         if(cdlinfo.getHighField() != null && equalsFieldName(refName, cdlinfo.getHighField())) {
            cdlinfo.setHighField(null);
         }

         if(cdlinfo.getLowField() != null && equalsFieldName(refName, cdlinfo.getLowField())) {
            cdlinfo.setLowField(null);
         }

         if(cdlinfo.getOpenField() != null && equalsFieldName(refName, cdlinfo.getOpenField())) {
            cdlinfo.setOpenField(null);
         }

         if(cdlinfo.getCloseField() != null && equalsFieldName(refName, cdlinfo.getCloseField())) {
            cdlinfo.setCloseField(null);
         }
      }
   }

   private void removeStartEndBindingCol(String refName, VSChartInfo cinfo) {
      if(cinfo instanceof GanttVSChartInfo) {
         GanttVSChartInfo cdlinfo = (GanttVSChartInfo) cinfo;

         if(cdlinfo.getStartField() != null && equalsFieldName(refName, cdlinfo.getStartField())) {
            cdlinfo.setStartField(null);
         }

         if(cdlinfo.getEndField() != null && equalsFieldName(refName, cdlinfo.getEndField())) {
            cdlinfo.setEndField(null);
         }

         if(cdlinfo.getMilestoneField() != null && equalsFieldName(refName, cdlinfo.getMilestoneField())) {
            cdlinfo.setMilestoneField(null);
         }
      }
   }

   private void removeRelationBindingCol(String refName, VSChartInfo cinfo) {
      RelationVSChartInfo rinfo = (RelationVSChartInfo) cinfo;

      if(rinfo.getSourceField() != null && equalsFieldName(refName, rinfo.getSourceField())) {
         rinfo.setSourceField(null);
      }

      if(rinfo.getTargetField() != null && equalsFieldName(refName, rinfo.getTargetField())) {
         rinfo.setTargetField(null);
      }
   }

   // For candle and map geo, if calc type is changed, high/low/open/close will cleared since it
   // only support measures and Map geo only support dimensions.
   // So only fix the x/y/group/color/shape/size/text bindings.
   public void changeBindingCalcType(String refName, VSChartInfo cinfo, CalculateRef ref,
                                     boolean wizard)
   {
      ChartRef nref = createNewCalcRef(refName, ref);
      changeXCalcType(refName, cinfo, nref, wizard);
      changeYCalcType(refName, cinfo, nref, wizard);
      removeGeoColumns(refName, cinfo);

      if(wizard) {
         return;
      }

      changeGroupCalcType(refName, cinfo, nref);

      if(cinfo.isMultiStyles()) {
         for(ChartRef aref : cinfo.getBindingRefs(false)) {
            if(aref instanceof ChartAggregateRef) {
               changeAestheticCalcType(refName, (ChartBindable) aref, nref);
            }
         }
      }
      else {
         changeAestheticCalcType(refName, cinfo, nref);
      }

      GraphUtil.fixVisualFrames(cinfo);
   }

   private void changeXCalcType(String refName, VSChartInfo cinfo, ChartRef nref, boolean wizard) {
      for(int i = cinfo.getXFieldCount() - 1; i >= 0 ; i--) {
         DataRef temp = cinfo.getXField(i);

         if(equalsFieldName(refName, temp)) {
            if(nref != null) {
               if(wizard && nref.isMeasure()) {
                  cinfo.removeXField(i);
                  cinfo.addYField(nref);
               }
               else {
                  cinfo.setXField(i, nref);
               }
            }
         }
      }
   }

   private void changeYCalcType(String refName, VSChartInfo cinfo, ChartRef nref, boolean wizard) {
      for(int i = cinfo.getYFieldCount() - 1; i >= 0; i--) {
         DataRef temp = cinfo.getYField(i);

         if(equalsFieldName(refName, temp)) {
            if(nref != null) {
               if(wizard && !nref.isMeasure()) {
                  cinfo.removeYField(i);
                  cinfo.addXField(nref);
               }
               else {
                  cinfo.setYField(i, nref);
               }
            }
         }
      }
   }

   private void changeGroupCalcType(String refName, VSChartInfo cinfo, ChartRef nref) {
      for(int i = cinfo.getGroupFieldCount() - 1; i > -1; i--) {
         ChartRef temp = cinfo.getGroupField(i);

         if(equalsFieldName(refName, temp)) {
            cinfo.setGroupField(i, nref);
         }
      }
   }

   private void changeAestheticCalcType(String refName, ChartBindable bindable, ChartRef nref) {
      if(bindable.getColorField() != null &&
         equalsFieldName(refName, bindable.getColorField().getDataRef()))
      {
         bindable.setColorField(createAestheticRef(nref));
      }

      if(bindable.getShapeField() != null &&
         equalsFieldName(refName, bindable.getShapeField().getDataRef()))
      {
         bindable.setShapeField(createAestheticRef(nref));
      }

      if(bindable.getSizeField() != null &&
         equalsFieldName(refName, bindable.getSizeField().getDataRef()))
      {
         // dimension binding for size not supported for interval
         if(GraphTypes.isInterval(bindable.getChartType()) && nref instanceof ChartDimensionRef) {
            bindable.setSizeField(null);
         }
         else {
            bindable.setSizeField(createAestheticRef(nref));
         }
      }

      if(bindable.getTextField() != null &&
         equalsFieldName(refName, bindable.getTextField().getDataRef()))
      {
         bindable.setTextField(createAestheticRef(nref));
      }

      if(bindable instanceof RelationChartInfo) {
         RelationChartInfo info2 = (RelationChartInfo) bindable;

         if(info2.getNodeColorField() != null &&
            equalsFieldName(refName, info2.getNodeColorField().getDataRef()))
         {
            info2.setNodeColorField(createAestheticRef(nref));
         }

         if(info2.getNodeSizeField() != null &&
            equalsFieldName(refName, info2.getNodeSizeField().getDataRef()))
         {
            info2.setNodeSizeField(createAestheticRef(nref));
         }
      }
   }

   protected VSAestheticRef createAestheticRef(ChartRef ref0) {
      VSAestheticRef aref = new VSAestheticRef();
      ChartRef ref = (ChartRef) ref0.clone();
      aref.setDataRef(ref);

      return aref;
   }

   /**
    * Fix the chart info. If the old expression measure which want to be deleted
    * in the binding, just remove it from the binding.
    */
   private void fixChartInfo(VSChartInfo cinfo, String refName) {
      fixChartInfo(getVSChartInfo(), refName, null);
   }

   /**
    * Fix the chart info. If the old expression measure which want to be deleted
    * or renamed.
    */
   private void fixChartInfo(VSChartInfo cinfo, String refName, String nName) {
      if(cinfo == null || refName == null) {
         return;
      }

      // Bug #57771, clear runtime refs when renaming/removing calc ref
      cinfo.clearRuntime();
      DataRef temp = null;

      // check the x binding
      for(int i = cinfo.getXFieldCount() - 1; i > -1; i--) {
         temp = cinfo.getXField(i);

         if(temp instanceof ChartBindable) {
            renameColumn((ChartBindable) temp, refName, nName);
         }

         if(equalsFieldName(refName, temp)) {
            if(nName != null) {
               renameColumn(temp, refName, nName);
            }
            else {
               cinfo.removeXField(i);
            }
         }
      }

      // check the y binding
      for(int i = cinfo.getYFieldCount() - 1; i > -1; i--) {
         temp = cinfo.getYField(i);

         if(temp instanceof ChartBindable) {
            renameColumn((ChartBindable) temp, refName, nName);
         }

         if(equalsFieldName(refName, temp)) {
            if(nName != null) {
               renameColumn(temp, refName, nName);
            }
            else {
               cinfo.removeYField(i);
            }
         }
      }

      // check the group by
      for(int i = cinfo.getGroupFieldCount() - 1; i > -1; i--) {
         temp = cinfo.getGroupField(i);

         if(equalsFieldName(refName, temp)) {
            if(nName != null) {
               renameColumn(temp, refName, nName);
            }
            else {
               cinfo.removeGroupField(i);
            }
         }
      }

      // check the high, low, close, open fields
      if(cinfo instanceof CandleVSChartInfo) {
         CandleVSChartInfo cdlinfo = (CandleVSChartInfo) cinfo;

         if(cdlinfo.getHighField() != null &&
            equalsFieldName(refName, cdlinfo.getHighField()))
         {
            if(nName != null) {
               renameColumn(cdlinfo.getHighField(), refName, nName);
            }
            else {
               cdlinfo.setHighField(null);
            }
         }

         if(cdlinfo.getLowField() != null &&
            equalsFieldName(refName, cdlinfo.getLowField()))
         {
            if(nName != null) {
               renameColumn(cdlinfo.getLowField(), refName, nName);
            }
            else {
               cdlinfo.setLowField(null);
            }
         }

         if(cdlinfo.getOpenField() != null &&
            equalsFieldName(refName, cdlinfo.getOpenField()))
         {
            if(nName != null) {
               renameColumn(cdlinfo.getOpenField(), refName, nName);
            }
            else {
               cdlinfo.setOpenField(null);
            }
         }

         if(cdlinfo.getCloseField() != null &&
            equalsFieldName(refName, cdlinfo.getCloseField()))
         {
            if(nName != null) {
               renameColumn(cdlinfo.getCloseField(), refName, nName);
            }
            else {
               cdlinfo.setCloseField(null);
            }
         }
      }
      else if(cinfo instanceof MapInfo) {
         MapInfo minfo = (MapInfo) cinfo;

         for(int i = minfo.getGeoFieldCount() - 1; i > -1; i--) {
            temp = minfo.getGeoFieldByName(i);

            if(equalsFieldName(refName, temp)) {
               if(nName != null) {
                  renameColumn(temp, refName, nName);
               }
               else {
                  minfo.removeGeoField(i);
               }
            }
         }
      }

      ColumnSelection geos = cinfo.getGeoColumns();

      for(int i = geos.getAttributeCount() -1; i >= 0; i--) {
         temp = geos.getAttribute(i);

         if(equalsFieldName(refName, temp)) {
            if(nName != null) {
               renameColumn(temp, refName, nName);
            }
            else {
               geos.removeAttribute(i);
            }
         }
      }

      // check aesthetic fields
      renameColumn(cinfo, refName, nName);
   }

   /**
    * Rename column in aesthetic fields.
    */
   private void renameColumn(ChartBindable cinfo, String refName, String nName) {
      if(cinfo.getColorField() != null &&
         equalsFieldName(refName, cinfo.getColorField().getDataRef()))
      {
         if(nName != null) {
            renameColumn(cinfo.getColorField().getDataRef(), refName, nName);
         }
         else {
            cinfo.setColorField(null);
         }
      }

      if(cinfo.getShapeField() != null &&
         equalsFieldName(refName, cinfo.getShapeField().getDataRef()))
      {
         if(nName != null) {
            renameColumn(cinfo.getShapeField().getDataRef(), refName, nName);
         }
         else {
            cinfo.setShapeField(null);
         }
      }

      if(cinfo.getSizeField() != null &&
         equalsFieldName(refName, cinfo.getSizeField().getDataRef()))
      {
         if(nName != null) {
            renameColumn(cinfo.getSizeField().getDataRef(), refName, nName);
         }
         else {
            cinfo.setSizeField(null);
         }
      }

      if(cinfo.getTextField() != null &&
         equalsFieldName(refName, cinfo.getTextField().getDataRef()))
      {
         if(nName != null) {
            renameColumn(cinfo.getTextField().getDataRef(), refName, nName);
         }
         else {
            cinfo.setTextField(null);
         }
      }

      if(cinfo instanceof RelationChartInfo) {
         RelationChartInfo info2 = (RelationChartInfo) cinfo;

         if(info2.getNodeColorField() != null &&
            equalsFieldName(refName, info2.getNodeColorField().getDataRef()))
         {
            if(nName != null) {
               renameColumn(info2.getNodeColorField().getDataRef(), refName, nName);
            }
            else {
               info2.setNodeColorField(null);
            }
         }

         if(info2.getNodeSizeField() != null &&
            equalsFieldName(refName, info2.getNodeSizeField().getDataRef()))
         {
            if(nName != null) {
               renameColumn(info2.getNodeSizeField().getDataRef(), refName, nName);
            }
            else {
               info2.setNodeSizeField(null);
            }
         }
      }
   }

   /**
    * rename binding column.
    */
   private void renameColumn(DataRef ref, String oname, String nname) {
      if(ref instanceof VSAggregateRef) {
         VSAggregateRef vref = (VSAggregateRef) ref;
         String sname = vref.getSecondaryColumnValue();

         if(Tool.equals(vref.getColumnValue(), oname)) {
            DataRef dref = vref.getDataRef();

            if(dref != null) {
               VSUtil.renameDataRef(dref, nname);
            }

            VSUtil.setVSAggregateRefName(vref, nname);
         }
         else if(VSUtil.matchRefName(sname, oname)) {
            vref.setSecondaryColumnValue(VSUtil.getMatchRefName(sname, nname));
         }
      }
      else if(ref instanceof VSDimensionRef) {
         VSDimensionRef vref = (VSDimensionRef) ref;
         String rankname = vref.getRankingColValue();
         String sortname = vref.getSortByColValue();

         if(Tool.equals(vref.getGroupColumnValue(), oname)) {
            DataRef dref = vref.getDataRef();

            if(dref != null) {
               VSUtil.renameDataRef(dref, nname);
            }

            VSUtil.setVSDimensionRefName(vref, nname);
         }
         else if(VSUtil.matchRefName(rankname, oname)) {
            vref.setRankingColValue(VSUtil.getMatchRefName(rankname, nname));
         }

         if(VSUtil.matchRefName(sortname, oname)) {
            vref.setSortByColValue(VSUtil.getMatchRefName(sortname, nname));
         }
      }
   }

   /**
    * Check if the ref's name equals to the specified expression measure name.
    */
   private boolean equalsFieldName(String refName, DataRef ref) {
      String name = null;
      String sname = null;
      String rname = null;

      if(ref instanceof VSAggregateRef) {
         name = ((VSAggregateRef) ref).getColumnValue();
         sname = ((VSAggregateRef) ref).getSecondaryColumnValue();
      }
      else if(ref instanceof VSDimensionRef) {
         name = ((VSDimensionRef) ref).getGroupColumnValue();
         rname = ((VSDimensionRef) ref).getRankingColValue();
         sname = ((VSDimensionRef) ref).getSortByColValue();
      }
      else if(ref instanceof BaseField){
         name = ref.getName();
      }

      return VSUtil.matchRefName(name, refName) ||
         VSUtil.matchRefName(sname, refName) ||
         VSUtil.matchRefName(rname, refName);
   }

   /**
    * Rename VSSelection.
    */
   private void renameVSSelection(VSSelection vselecion, String oname,
                                  String nname)
   {
      if(vselecion != null) {
         for(int i = 0; i < vselecion.getPointCount(); i++) {
            VSPoint vpoint = vselecion.getPoint(i);

            for(int j = 0; j < vpoint.getValueCount(); j++) {
               VSFieldValue fvalue = vpoint.getValue(j);
               String fname =  fvalue.getFieldName();

               if(VSUtil.matchRefName(fname, oname)) {
                  fvalue.setFieldName(VSUtil.getMatchRefName(fname, nname));
               }
            }
         }
      }
   }

   /**
    * Get the group title.
    * @return the title of the checkbox assembly.
    */
   public String getTitle() {
      return getChartInfo().getTitle();
   }

   /**
    * Get the group title value.
    * @return the title value of the checkbox assembly.
    */
   public String getTitleValue() {
      return getChartInfo().getTitleValue();
   }

   /**
    * Set the group title value.
    * @param value the specified group title.
    */
   public void setTitleValue(String value) {
      getChartInfo().setTitleValue(value);
   }

   @Override
   public DrillFilterInfo getDrillFilterInfo() {
      return getChartInfo().getDrillFilterInfo();
   }

   @Override
   public DataRef[] getDrillFilterAvailableRefs() {
      VSChartInfo info = getVSChartInfo();
      List<DataRef> refs = new ArrayList<>(Arrays.asList(info.getXFields()));
      refs.addAll(Arrays.asList(info.getYFields()));
      refs.addAll(Arrays.asList(info.getGroupFields()));

      Arrays.stream(info.getAestheticRefs(false))
         .map(aref -> aref.getDataRef()).forEach(a -> refs.add(a));

      if(info instanceof VSMapInfo) {
         refs.addAll(Arrays.asList(((VSMapInfo) info).getGeoFields()));
      }
      else if(info instanceof RelationChartInfo) {
         refs.add(((RelationChartInfo) info).getSourceField());
         refs.add(((RelationChartInfo) info).getTargetField());
      }

      return refs.toArray(new DataRef[refs.size()]);
   }

   // input data
   private VSSelection dselection;

   private static final Logger LOG = LoggerFactory.getLogger(ChartVSAssembly.class);
}
