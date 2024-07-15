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
package inetsoft.uql.viewsheet;

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.XNodeMetaTable;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.xmla.XMLAUtil;
import inetsoft.util.Tool;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;

/**
 * CrosstabVSAssembly represents one crosstab assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class CrosstabVSAssembly extends CrossBaseVSAssembly implements
   DrillFilterVSAssembly, DateCompareAbleAssembly
{
   /**
    * Constructor.
    */
   public CrosstabVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public CrosstabVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new CrosstabVSAssemblyInfo();
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.CROSSTAB_ASSET;
   }

   /**
    * Get the xcube.
    * @return the xcube.
    */
   @Override
   public XCube getXCube() {
      return getCrosstabInfo().getXCube();
   }

   /**
    * Set the xcube.
    * @param cube the specified xcube.
    */
   @Override
   public void setXCube(XCube cube) {
      getCrosstabInfo().setXCube(cube);
   }

   /**
    * Get the crosstab data info.
    * @return the crosstab data info.
    */
   @Override
   public VSCrosstabInfo getVSCrosstabInfo() {
      return getCrosstabInfo().getVSCrosstabInfo();
   }

   /**
    * Set the crosstab data info.
    * @param cinfo the crosstab data info.
    */
   @Override
   public void setVSCrosstabInfo(VSCrosstabInfo cinfo) {
      getCrosstabInfo().setVSCrosstabInfo(cinfo);
   }

   /**
    * Get the crosstab hierarchical tree.
    */
   @Override
   public CrosstabTree getCrosstabTree() {
      return getCrosstabInfo().getCrosstabTree();
   }

   /**
    * Get crosstab assembly info.
    * @return the crosstab assembly info.
    */
   public CrosstabVSAssemblyInfo getCrosstabInfo() {
      return (CrosstabVSAssemblyInfo) getInfo();
   }

   /**
    * Get share from assembly for date comparison.
    * @return share assembly name
    */
   @Override
   public String getComparisonShareFromAssembly() {
      return getCrosstabInfo().getComparisonShareFrom();
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

      VSCrosstabInfo cinfo = getVSCrosstabInfo();

      if(cinfo != null) {
         cinfo.renameDepended(oname, nname, getViewsheet());
      }

      renameHighlightDependeds(oname, nname);
   }

   private void renameHighlightDependeds(String oname, String nname) {
      CrosstabVSAssemblyInfo crosstabInfo = getCrosstabInfo();

      if(crosstabInfo == null) {
         return;
      }

      TableHighlightAttr highlightAttr = crosstabInfo.getHighlightAttr();

      if(highlightAttr == null) {
         return;
      }

      Enumeration highlights = highlightAttr.getAllHighlights();

      if(highlights != null) {
         while(highlights.hasMoreElements()) {
            VSUtil.renameHighlightDependeds(oname, nname,
               (HighlightGroup) highlights.nextElement());
         }
      }
   }

   /**
    * Set the assembly info.
    * @param info the specified viewsheet assembly info.
    * @return the hint to reset view, input data or output data.
    */
   @Override
   public int setVSAssemblyInfo(VSAssemblyInfo info) {
      DataRef[] rows = new DataRef[0];
      DataRef[] cols = new DataRef[0];
      DataRef[] aggs = new DataRef[0];

      if(getVSCrosstabInfo() != null) {
         rows = getVSCrosstabInfo().getRuntimeRowHeaders();
         cols = getVSCrosstabInfo().getRuntimeColHeaders();
         aggs = getVSCrosstabInfo().getRuntimeAggregates();
      }

      int hint = super.setVSAssemblyInfo(info);
      DataRef[] newAggs = getVSCrosstabInfo().getAggregates();
      SourceInfo sourceInfo = getSourceInfo();

      if(sourceInfo != null && AssetEventUtil.isCubeType(sourceInfo.getType()) &&
         (aggs == null || aggs.length == 1 && VSUtil.isFake(aggs[0])) && newAggs != null &&
         newAggs.length > 0 && !VSUtil.isFake(newAggs[0]))
      {
         CrosstabTree ctree = getCrosstabTree();

         if(ctree != null) {
            ctree.clearDrills();
         }
      }

      if(getVSCrosstabInfo() != null) {
         updateExpand(rows, getVSCrosstabInfo().getRowHeaders());
         updateExpand(cols, getVSCrosstabInfo().getColHeaders());
      }

      return hint;
   }

   /**
    * Update the CrosstabTree's expanded path.
    */
   private void updateExpand(DataRef[] orefs, DataRef[] nrefs) {
      if(getCrosstabTree() == null) {
         return;
      }

      for(DataRef nref : nrefs) {
         boolean found = false;
         String name = XMLAUtil.getDisplayName(nref.getName());

         for(DataRef oref : orefs) {
            if(oref != null && Objects.equals(name, XMLAUtil.getDisplayName(oref.getName()))) {
               found = true;
               updateGroupExpandPath(nref, oref);
               break;
            }
         }

         XSourceInfo source = getSourceInfo();

         if(!found && source != null) {
            XCube cube = AssetUtil.getCube(source.getPrefix(), source.getSource());

            if(cube == null) {
               return;
            }

            getCrosstabTree().updateExpanded(nref, nrefs, cube);
         }
      }
   }

   /**
    * If the NamedGroupInfo changed, drill up all the dimension.
    */
   private void updateGroupExpandPath(DataRef nref, DataRef oref) {
      CrosstabTree ctree = getCrosstabTree();

      if(ctree == null || !(nref instanceof VSDimensionRef) ||
         !(oref instanceof VSDimensionRef))
      {
         return;
      }

      String field = ctree.getFieldName(oref);

      if(!ctree.isDrilled(field)) {
         return;
      }

      VSDimensionRef nvref = (VSDimensionRef) nref;
      VSDimensionRef ovref = (VSDimensionRef) oref;
      SNamedGroupInfo ngroupInfo = (SNamedGroupInfo) nvref.getNamedGroupInfo();
      SNamedGroupInfo ogroupInfo = (SNamedGroupInfo) ovref.getNamedGroupInfo();

      boolean ungroup = ngroupInfo == null &&
         ogroupInfo != null && !ogroupInfo.isEmpty();
      boolean group = ogroupInfo == null
         && ngroupInfo != null && !ngroupInfo.isEmpty();
      boolean changeGroup = ngroupInfo != null &&
         ogroupInfo != null && !ngroupInfo.equals(ogroupInfo);

      if(ungroup || group || changeGroup) {
         ctree.removeDrill(field);
      }
   }

   /**
    * Check if this viewsheet assembly has a cube.
    * @return <tt>true</tt> if has a cube, <tt>false</tt> otherwise.
    */
   @Override
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

      // write range
      ConditionList range = getRangeConditionList();

      if(range != null) {
         writer.println("<state_range>");
         range.writeXML(writer);
         writer.println("</state_range>");
      }

      // write crosstab info
      VSCrosstabInfo cinfo = getVSCrosstabInfo();
      FormatInfo finfo =  getFormatInfo();

      if(cinfo != null) {
         String table = getSourceName();

         if(table != null) {
            writer.println("<state_table>");
            writer.print("<![CDATA[" + table + "]]>");
            writer.println("</state_table>");
         }

         writer.println("<state_crosstab>");
         cinfo.writeXML(writer);
         writer.println("</state_crosstab>");
      }

      if(finfo != null) {
          writer.println("<state_crosstabformat>");
          finfo.writeContents(writer);
          writer.println("</state_crosstabformat>");
      }

      CrosstabVSAssemblyInfo crosstabInfo = getCrosstabInfo();

      if(crosstabInfo != null && (crosstabInfo.getDateComparisonInfo() != null ||
         !Tool.isEmptyString(crosstabInfo.getComparisonShareFrom())))
      {
         writer.println("<state_dateComparison>");

         if(crosstabInfo.getDateComparisonInfo() != null) {
            crosstabInfo.getDateComparisonInfo().writeXML(writer);
         }

         if(crosstabInfo.getComparisonShareFrom() != null) {
            writer.println("<shareFrom>");
            writer.print("<![CDATA[" + getCrosstabInfo().getComparisonShareFrom() + "]]>");
            writer.println("</shareFrom>");
         }

         writer.println("</state_dateComparison>");
      }

      getCrosstabInfo().writeHiddenColumns(writer);
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

      Element rnode = Tool.getChildNodeByTagName(elem, "state_range");

      if(rnode != null) {
         ConditionList conds = new ConditionList();
         rnode = Tool.getFirstChildNode(rnode);
         conds.parseXML(rnode);
         setRangeConditionList(conds);
      }
      else {
         setRangeConditionList(null);
      }

      Element tableNode = Tool.getChildNodeByTagName(elem, "state_table");
      Element cnode = Tool.getChildNodeByTagName(elem, "state_crosstab");
      Element fnode = Tool.getChildNodeByTagName(elem, "state_crosstabformat");

      if(cnode != null && getSourceName() != null &&
         getSourceName().equals(Tool.getValue(tableNode)))
      {
         cnode = Tool.getFirstChildNode(cnode);
         VSCrosstabInfo cinfo = new VSCrosstabInfo();
         cinfo.parseXML(cnode);

         if(hasCube() ) {
            setVSCrosstabInfo(cinfo);
         }
         else {
            VSCrosstabInfo cinfo2 = getVSCrosstabInfo();

            if(cinfo2 != null && !cinfo2.equals(cinfo)) {
               setVSCrosstabInfo(cinfo);
            }
         }
      }

      if(fnode != null) {
          FormatInfo finfo = new FormatInfo();
          finfo.parseContents(fnode);
          setFormatInfo(finfo);
      }

      Element dcNode = Tool.getChildNodeByTagName(elem, "state_dateComparison");

      if(dcNode != null && getCrosstabInfo() != null) {
         DateComparisonInfo dateComparisonInfo = new DateComparisonInfo();
         dateComparisonInfo.parseXML(dcNode);

         if(dateComparisonInfo.getPeriods() != null &&
            dateComparisonInfo.getInterval() != null)
         {
            getCrosstabInfo().setDateComparisonInfo(dateComparisonInfo);
         }
         else {
            getCrosstabInfo().setDateComparisonInfo(null);
         }

         getCrosstabInfo().setComparisonShareFrom(Tool.getChildValueByTagName(dcNode, "shareFrom"));
      }
      else if(getCrosstabInfo() != null){
         getCrosstabInfo().setDateComparisonInfo(null);
         getCrosstabInfo().setComparisonShareFrom(null);
      }

      getCrosstabInfo().parseHiddenColumns(elem);
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public CrosstabVSAssembly clone() {
      try {
         return (CrosstabVSAssembly) super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone CrosstabDataVSAssembly", ex);
      }

      return null;
   }

   /**
    * Get the range condition list.
    * @return the range condition list.
    */
   @Override
   public ConditionList getRangeConditionList() {
      return getCrosstabInfo().getRangeConditionList();
   }

   /**
    * Set the range condition list.
    * @param range the specified range condition list.
    * @return the changed hint.
    */
   @Override
   public int setRangeConditionList(ConditionList range) {
      return getCrosstabInfo().setRangeConditionList(range);
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
    * Get the predefined dimension/measure information.
    */
   @Override
   public AggregateInfo getAggregateInfo() {
      VSCrosstabInfo cinfo = getVSCrosstabInfo();
      return (cinfo == null) ? null : cinfo.getAggregateInfo();
   }

   /**
    * Get the highlight attr.
    */
   @Override
   protected TableHighlightAttr getHighlightAttr() {
      return getCrosstabInfo().getHighlightAttr();
   }

   /**
    * Get the hyperlink attr.
    */
   @Override
   protected TableHyperlinkAttr getHyperlinkAttr() {
      return getCrosstabInfo().getHyperlinkAttr();
   }

   /**
    * Get the binding data refs.
    */
   @Override
   public DataRef[] getBindingRefs() {
      List<DataRef> datarefs = new ArrayList<>();
      VSCrosstabInfo cinfo = getVSCrosstabInfo();

      if(cinfo != null) {
         datarefs.addAll(Arrays.asList(cinfo.getRuntimeColHeaders()));
         datarefs.addAll(Arrays.asList(cinfo.getRuntimeRowHeaders()));
         datarefs.addAll(Arrays.asList(cinfo.getRuntimeAggregates()));

         // runtime is not updated? try design time instead
         // fix bug1313985448435
         if(datarefs.size() <= 0) {
            DataRef[] refs = cinfo.getColHeaders();
            refs = refs == null ? new DataRef[0] : refs;
            datarefs.addAll(Arrays.asList(refs));

            refs = cinfo.getRowHeaders();
            refs = refs == null ? new DataRef[0] : refs;
            datarefs.addAll(Arrays.asList(refs));

            refs = cinfo.getAggregates();
            refs = refs == null ? new DataRef[0] : refs;
            datarefs.addAll(Arrays.asList(refs));
         }
      }

      return datarefs.toArray(new DataRef[] {});
   }

   @Override
   public DataRef[] getAllBindingRefs() {
      List<DataRef> datarefs = new ArrayList<>();
      DataRef[] refs = super.getAllBindingRefs();

      for(int i = 0; i < refs.length; i++) {
         datarefs.add(refs[i]);
      }

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

      return datarefs.toArray(new DataRef[] {});
   }

   @Override
   public void removeBindingCol(String ref) {
      super.removeBindingCol(ref);
      VSCrosstabInfo cinfo = getVSCrosstabInfo();

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

      if(cinfo != null) {
         // clean the aggregate info
         AggregateInfo ainfo = cinfo.getAggregateInfo();
         AggregateRef[] aggs = ainfo.getAggregates();

         for(int i = 0; i < aggs.length; i++) {
            if(VSUtil.matchRef(aggs[i], ref)) {
               ainfo.removeAggregate(i);
               break;
            }
         }

         GroupRef[] grps = ainfo.getGroups();

         for(int i = grps.length - 1; i >= 0; i--) {
            if(VSUtil.matchRef(grps[i], ref)) {
               ainfo.removeGroup(i);
            }
         }

         // clean the crosstab binding
         DataRef[] daggs = cinfo.getDesignAggregates();
         boolean removedAgg = false;

         for(int i = daggs.length - 1; i >= 0; i--) {
            if(Tool.equals(((VSAggregateRef) daggs[i]).
               getColumnValue(), ref))
            {
               daggs = VSUtil.removeRow(daggs, i);
               removedAgg = true;
            }
         }

         if(removedAgg) {
            cinfo.setDesignAggregates(daggs);
         }

         DataRef[] cols = cinfo.getDesignColHeaders();

         for(int i = cols.length - 1; i >= 0; i--) {
            if(Tool.equals(((VSDimensionRef) cols[i]).
               getGroupColumnValue(), ref))
            {
               cols = VSUtil.removeRow(cols, i);
            }

            if(i == 0) {
               cinfo.setDesignColHeaders(cols);
            }
         }

         DataRef[] rows = cinfo.getDesignRowHeaders();

         for(int i = rows.length - 1; i >= 0; i--) {
            if(Tool.equals(((VSDimensionRef) rows[i]).
               getGroupColumnValue(), ref))
            {
               rows = VSUtil.removeRow(rows, i);
            }

            if(i == 0) {
               cinfo.setDesignRowHeaders(rows);
            }
         }
      }
   }

   @Override
   public void renameBindingCol(String oname, String nname) {
      super.renameBindingCol(oname, nname);
      VSCrosstabInfo cinfo = getVSCrosstabInfo();

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

      if(cinfo != null) {
         // change the aggregate info
         AggregateInfo ainfo = cinfo.getAggregateInfo();
         AggregateRef[] aggs = ainfo.getAggregates();

         for(int i = 0; i < aggs.length; i++) {
            if(VSUtil.matchRef(aggs[i], oname)) {
               VSUtil.renameDataRef(aggs[i].getDataRef(), nname);
            }
         }

         GroupRef[] grps = ainfo.getGroups();

         for(int i = 0; i < grps.length; i++) {
            if(VSUtil.matchRef(grps[i], oname)) {
               VSUtil.renameDataRef(grps[i].getDataRef(), nname);
            }
         }

         // change the crosstab binding
         DataRef[] daggs = cinfo.getDesignAggregates();

         for(int i = 0; i < daggs.length; i++) {
            VSAggregateRef aref = (VSAggregateRef) daggs[i];
            String sname = aref.getSecondaryColumnValue();

            if(Tool.equals(aref.getColumnValue(), oname)) {
               VSUtil.setVSAggregateRefName(aref, nname);
            }

            if(VSUtil.matchRefName(sname, oname)) {
               aref.setSecondaryColumnValue(nname);
            }
         }

         DataRef[] cols = cinfo.getDesignColHeaders();

         for(int i = 0; i < cols.length; i++) {
            VSDimensionRef dref = (VSDimensionRef) cols[i];
            String rankname = dref.getRankingColValue();

            if(Tool.equals(dref.getGroupColumnValue(), oname)) {
               VSUtil.setVSDimensionRefName(dref, nname);
            }

            if(VSUtil.matchRefName(rankname, oname)) {
               dref.setRankingColValue(VSUtil.getMatchRefName(rankname, nname));
            }
         }

         DataRef[] rows = cinfo.getDesignRowHeaders();

         for(int i = 0; i < rows.length; i++) {
            VSDimensionRef dref = (VSDimensionRef) rows[i];
            String rankname = dref.getRankingColValue();

            if(Tool.equals(dref.getGroupColumnValue(), oname)) {
               VSUtil.setVSDimensionRefName(dref, nname);
            }

            if(VSUtil.matchRefName(rankname, oname)) {
               dref.setRankingColValue(VSUtil.getMatchRefName(rankname, nname));
            }
         }
      }
   }

   /**
    * Gets the last Table Lens sent to the client, that should represent the
    * current view.
    * @see inetsoft.analytic.composition.event.LoadTableLensEvent
    */
   public VSTableLens getLastTableLens() {
      return lastTableLens != null ? lastTableLens.get() : null;
   }

   /**
    * Sets the last Table Lens that was sent to the client.
    */
   public void setLastTableLens(VSTableLens lastTableLens) {
      // this should be the same as the one turned by ViewsheetSandbox.getData()
      // so it's treated as a cache.
      this.lastTableLens = new WeakReference<>(lastTableLens);
      this.metaData = lastTableLens != null &&
         Util.getNestedTable(lastTableLens, XNodeMetaTable.class) != null;
   }

   /**
    * Check if the table is showing meta data.
    */
   public boolean isMetaData() {
      return metaData;
   }

   /**
    * Get the last time a drilldown was requested for this assembly
    * @return the last time a drilldown was requested for this assembly
    */
   public long getLastDrillDownRequest() {
      return lastDrillDownRequest;
   }

   /**
    * Sets the last time a drilldown was requested for this assembly
    * @param lastDrillDownRequest the last time a drilldown was requested
    */
   public void setLastDrillDownRequest(long lastDrillDownRequest) {
      this.lastDrillDownRequest = lastDrillDownRequest;
   }

   public boolean isAggregateTopN() {
      return true;
   }

   public boolean supportPeriod() {
      return true;
   }

   @Override
   public DrillFilterInfo getDrillFilterInfo() {
      return getCrosstabInfo().getDrillFilterInfo();
   }

   @Override
   public DataRef[] getDrillFilterAvailableRefs() {
      VSCrosstabInfo info = getVSCrosstabInfo();
      DataRef[] refs = (DataRef[]) ArrayUtils.addAll(info.getRowHeaders(), info.getColHeaders());

      return refs;
   }

   // input data
   private Reference<VSTableLens> lastTableLens = null;
   private boolean metaData = false; // true if table contains meta data
   private long lastDrillDownRequest = 0;

   private static final Logger LOG =
      LoggerFactory.getLogger(CrosstabVSAssembly.class);
}
