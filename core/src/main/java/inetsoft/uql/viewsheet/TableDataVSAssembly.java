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
import inetsoft.report.TableDataPath;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.AssemblyRef;
import inetsoft.uql.asset.SortInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.util.List;
import java.util.*;

/**
 * TableDataVSAssembly represents one table data assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class TableDataVSAssembly extends DataVSAssembly
   implements CompositeVSAssembly, AggregateVSAssembly
{
   /**
    * Constructor.
    */
   public TableDataVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public TableDataVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Get the DataVSAssemblyInfo.
    * @return the DataVSAssemblyInfo.
    */
   public TableDataVSAssemblyInfo getTableDataVSAssemblyInfo() {
      return (TableDataVSAssemblyInfo) getInfo();
   }

   /**
    * Get the detail condition list.
    * @return the detail condition list.
    */
   @Override
   public ConditionList getDetailConditionList() {
      return dconds;
   }

   /**
    * Set the detail condition list.
    * @param detail the specified detail condition list.
    * @return the changed hint.
    */
   @Override
   public int setDetailConditionList(ConditionList detail) {
      if(Tool.equals(dconds, detail)) {
         return NONE_CHANGED;
      }

      this.dconds = detail;
      return DETAIL_INPUT_DATA_CHANGED;
   }

   /**
    * Get the runtime style of the target table.
    * @return the style of the target table.
    */
   public String getTableStyle() {
      return getTableDataVSAssemblyInfo().getTableStyle();
   }

   /**
    * Get the design time style of the target table.
    * @return the style of the target table.
    */
   public String getTableStyleValue() {
      return getTableDataVSAssemblyInfo().getTableStyleValue();
   }

   /**
    * Set the design time style of the target table.
    * @param style the specified style of the target table.
    */
   public void setTableStyleValue(String style) {
      getTableDataVSAssemblyInfo().setTableStyleValue(style);
   }

   /**
    * Get the group title.
    * @return the title of the checkbox assembly.
    */
   @Override
   public String getTitle() {
      return getTableDataVSAssemblyInfo().getTitle();
   }

   /**
    * Get the group title value.
    * @return the title value of the checkbox assembly.
    */
   @Override
   public String getTitleValue() {
      return getTableDataVSAssemblyInfo().getTitleValue();
   }

   /**
    * Get the sort info.
    * @return the sort info.
    */
   public SortInfo getSortInfo() {
      return getTableDataVSAssemblyInfo().getSortInfo();
   }

   /**
    * Set the sort info.
    * @param info the specified sort info.
    */
   public void setSortInfo(SortInfo info) {
      getTableDataVSAssemblyInfo().setSortInfo(info);
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      List<UserVariable> list = new ArrayList<>();
      Viewsheet.mergeVariables(list, super.getAllVariables());

      if(getTableDataVSAssemblyInfo().getHighlightAttr() != null) {
         Viewsheet.mergeVariables(list,
            getTableDataVSAssemblyInfo().getHighlightAttr().getAllVariables());
      }

      UserVariable[] vars = new UserVariable[list.size()];
      list.toArray(vars);
      return vars;
   }

   /**
    * Set the group title value.
    * @param value the specified group title.
    */
   @Override
   public void setTitleValue(String value) {
      getTableDataVSAssemblyInfo().setTitleValue(value);
   }

   /**
    * Get the view assemblies depended on.
    * @param set the set stores the presentation assemblies depended on.
    * @param self <tt>true</tt> to include self, <tt>false</tt> otherwise.
    */
   @Override
   public void getViewDependeds(Set<AssemblyRef> set, boolean self) {
      super.getViewDependeds(set, self);
      TableHighlightAttr attr = getTableDataVSAssemblyInfo().getHighlightAttr();
      Viewsheet vs = getViewsheet();

      if(attr != null) {
         Enumeration<?> highlights = attr.getAllHighlights();

         while(highlights.hasMoreElements()) {
            HighlightGroup group = (HighlightGroup) highlights.nextElement();
            VSUtil.getHighlightDependeds(group, set, vs);
         }
      }
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
      TableHighlightAttr attr = getTableDataVSAssemblyInfo().getHighlightAttr();
      Viewsheet vs = getViewsheet();

      if(attr != null) {
         Enumeration highlights = attr.getAllHighlights();

         while(highlights.hasMoreElements()) {
            HighlightGroup group = (HighlightGroup) highlights.nextElement();
            VSUtil.renameHighlightDependeds(oname, nname, group);
         }
      }

      VSUtil.renameTipDependeds(getTableDataVSAssemblyInfo(), oname, nname);
   }

   /**
    * Get the cell width.
    * @return the cell width.
    */
   @Override
   public int getCellWidth() {
      return 1;
   }

   /**
    * Write the state.
    * @param writer the specified print writer.
    */
   @Override
   protected void writeStateContent(PrintWriter writer, boolean runtime) {
      super.writeStateContent(writer, runtime);
      TableDataVSAssemblyInfo tinfo = getTableDataVSAssemblyInfo();

      if(runtime) {
         Dimension size = info.getPixelSize();

         if(size != null) {
            writer.println("<assembly_size>");
            writer.print("<width><![CDATA[" + size.width + "]]></width>");
            writer.print("<height><![CDATA[" + size.height + "]]></height>");
            writer.println("</assembly_size>");
         }

         writer.println("<headerRowHeights>" +
            Tool.arrayToString(tinfo.getHeaderRowHeights()) + "</headerRowHeights>");
         writer.println("<dataRowHeight>" + tinfo.getDataRowHeight() + "</dataRowHeight>");

         tinfo.writeColWidth(writer);
         tinfo.writeRowHeight(writer, true);
         ConditionList conditions = tinfo.getPreConditionList();

         // @davidd 11.5 2013-02-21. Macquarie feature, to allow end-user
         // modification of tables requires bookmarks to save condition state.
         // See sree.property: viewsheet.viewer.advancedFeatures
         if(conditions != null) {
            writer.println("<state_conditions>");
            conditions.writeXML(writer);
            writer.println("</state_conditions>");
         }
         else {
            // Insert empty condition node to explicitly signify no conditions
            writer.println("<state_conditions/>");
         }

         TableHighlightAttr highlights = tinfo.getHighlightAttr();

         if(highlights != null) {
            writer.println("<state_highlights>");
            highlights.writeXML(writer);
            writer.println("</state_highlights>");
         }
         else {
            // Insert empty highlights node to explicitly signify no highlights
            writer.println("<state_highlights/>");
         }

         SortInfo sinfo = getSortInfo();

         if(sinfo != null) {
            writer.println("<state_sort>");
            sinfo.writeXML(writer);
            writer.println("</state_sort>");
         }
      }

      // write details
      ConditionList details = getDetailConditionList();

      if(details != null) {
         writer.println("<state_details>");
         details.writeXML(writer);
         writer.println("</state_details>");
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

      if(runtime) {
         Element snode = Tool.getChildNodeByTagName(elem, "assembly_size");

         if(snode != null) {
            Element wnode = Tool.getChildNodeByTagName(snode, "width");
            Element hnode = Tool.getChildNodeByTagName(snode, "height");
            int width = Integer.parseInt(Tool.getValue(wnode));
            int height = Integer.parseInt(Tool.getValue(hnode));
            setPixelSize(new Dimension(width, height));
         }

         String headerStr = Tool.getChildValueByTagName(elem, "headerRowHeights");

         if(StringUtils.hasText(headerStr)) {
            String[] heights = Tool.split(headerStr, ',');
            int[] heightInts = new int[heights.length];

            for(int i = 0; i < heightInts.length; i++) {
               heightInts[i] = Integer.parseInt(heights[i]);
            }

            getTableDataVSAssemblyInfo().setHeaderRowHeights(heightInts);
         }

         String str = Tool.getChildValueByTagName(elem, "dataRowHeight");

         if(str != null) {
            getTableDataVSAssemblyInfo().setDataRowHeight(Integer.parseInt(str));
         }

         NodeList nodes = Tool.getChildNodesByTagName(elem, "colWidth");

         // if no state, do not replace default
         if(nodes != null || nodes.getLength() > 0) {
            getTableDataVSAssemblyInfo().parseColWidth(elem);
         }

         getTableDataVSAssemblyInfo().parseRowHeight(elem);

         Element conditionsNode = Tool.getChildNodeByTagName(elem, "state_conditions");

         if(conditionsNode != null) {
            conditionsNode = Tool.getFirstChildNode(conditionsNode);
            ConditionList conditions = new ConditionList();

            if(conditionsNode != null) {
               conditions.parseXML(conditionsNode);
            }

            setPreConditionList(conditions);
         }

         Element highlightsNode =
            Tool.getChildNodeByTagName(elem, "state_highlights");

         if(highlightsNode != null) {
            highlightsNode = Tool.getFirstChildNode(highlightsNode);
            TableHighlightAttr highlights = new TableHighlightAttr();

            if(highlightsNode != null) {
               highlights.parseXML(highlightsNode);
            }

            //fix bug#36202 sync listener
            syncHighlightAppliedListener(highlights);
            ((TableDataVSAssemblyInfo) getInfo()).setHighlightAttr(highlights);
         }

         Element sortNode = Tool.getChildNodeByTagName(elem, "state_sort");

         if(sortNode != null) {
            sortNode = Tool.getFirstChildNode(sortNode);
            SortInfo sinfo = new SortInfo();
            sinfo.parseXML(sortNode);
            setSortInfo(sinfo);
         }
         else {
            setSortInfo(null);
         }
      }

      Element cnode = Tool.getChildNodeByTagName(elem, "state_details");

      if(cnode != null) {
         cnode = Tool.getFirstChildNode(cnode);
         ConditionList conds = new ConditionList();
         conds.parseXML(cnode);
         setDetailConditionList(conds);
      }
      else {
         setDetailConditionList(null);
      }
   }

   /**
    * Synchronize highlight applied listener when parse state_highlights
    * to avoid missing highlight applied listener
    * @param highlights
    */
   private void syncHighlightAppliedListener(TableHighlightAttr highlights) {
      Map<TableDataPath, HighlightGroup> highlightMap = highlights.getHighlightMap();
      TableHighlightAttr highlightAttr = ((TableDataVSAssemblyInfo) getInfo()).getHighlightAttr();

      for(TableDataPath path : highlightMap.keySet()) {
         HighlightGroup group = highlightMap.get(path);
         HighlightGroup group1 = highlightAttr.getHighlight(path);

         if(group != null && group1 != null) {
            group.setHighlightAppliedListener(group1.getHighlightAppliedListener());
         }
      }
   }

   /**
    * Get the highlight attr.
    */
   protected abstract TableHighlightAttr getHighlightAttr();

   /**
    * Get the hyperlink attr.
    */
   protected abstract TableHyperlinkAttr getHyperlinkAttr();

   @Override
   public DataRef[] getAllBindingRefs() {
      List<DataRef> datarefs = new ArrayList<>();
      DataRef[] refs = super.getAllBindingRefs();

      for(int i = 0; i < refs.length; i++) {
         datarefs.add(refs[i]);
      }

      // sync the highlights
      TableHighlightAttr thattr = getHighlightAttr();

      if(thattr != null) {
         Enumeration highlights = thattr.getAllHighlights();

         if(highlights != null) {
            while(highlights.hasMoreElements()) {
               HighlightGroup hgroup = (HighlightGroup) highlights.nextElement();
               VSUtil.addHLConditionListRef(hgroup, datarefs);
            }
         }
      }

      // sycn the hyperlinks
      TableHyperlinkAttr hattr = getHyperlinkAttr();

      if(hattr != null) {
         Enumeration links = hattr.getAllHyperlinks();

         if(links != null) {
            Viewsheet vs = getViewsheet();

            while(links.hasMoreElements()) {
               Hyperlink link = (Hyperlink) links.nextElement();
               VSUtil.addHyperlinkRef(link, datarefs,
                  vs.getCalcFields(getTableName()));
            }
         }
      }

      return datarefs.toArray(new DataRef[] {});
   }

   @Override
   public void removeBindingCol(String ref) {
      VSUtil.removeConditionListRef(getPreConditionList(), ref);
      // sync the highlights
      TableHighlightAttr thattr = getHighlightAttr();

      if(thattr != null) {
         Set<TableDataPath> pathSet = new HashSet<>(thattr.getHighlightMap().keySet());

         for(TableDataPath path : pathSet) {
            String[] stringpath = path.getPath();

            if(stringpath != null) {
               for(int i = 0; i < stringpath.length; i++) {
                  if(VSUtil.matchRefName(stringpath[i], ref)) {
                     thattr.setHighlight(path, null);
                  }
               }
            }
         }

         Enumeration highlights = thattr.getAllHighlights();

         while(highlights.hasMoreElements()) {
            HighlightGroup hgroup = (HighlightGroup) highlights.nextElement();
            VSUtil.removeHLConditionListRef(hgroup, ref);
         }
      }

      // sycn the hyperlinks
      TableHyperlinkAttr hattr = getHyperlinkAttr();

      if(hattr != null) {
         Set<TableDataPath> pathSet = new HashSet<>(hattr.getHyperlinkMap().keySet());

         for(TableDataPath path : pathSet) {
            String[] stringpath = path.getPath();

            if(stringpath != null) {
               for(int i = 0; i < stringpath.length; i++) {
                  if(VSUtil.matchRefName(stringpath[i], ref)) {
                     hattr.setHyperlink(path, null);
                  }
               }
            }
         }

         Enumeration links = hattr.getAllHyperlinks();

         while(links.hasMoreElements()) {
            Hyperlink link = (Hyperlink) links.nextElement();
            VSUtil.removeHyperlinkRef(link, ref);
         }
      }
   }

   @Override
   public void renameBindingCol(String oname, String nname) {
      VSUtil.renameConditionListRef(getPreConditionList(), oname, nname);
      // sync the highlights
      TableHighlightAttr thattr = getHighlightAttr();

      if(thattr != null) {
         Enumeration<TableDataPath> paths =
            (Enumeration<TableDataPath>) thattr.getAllDataPaths();
         Map<TableDataPath, TableDataPath> changes =
            syncTableDataPath(paths, oname, nname);

         Iterator<TableDataPath> keys = changes.keySet().iterator();

         while(keys.hasNext()) {
            TableDataPath npath = keys.next();
            TableDataPath opath = changes.get(npath);
            HighlightGroup highlight = thattr.getHighlight(opath);
            thattr.setHighlight(opath, null);
            thattr.setHighlight(npath, highlight);
         }

         Enumeration highlights = thattr.getAllHighlights();

         while(highlights.hasMoreElements()) {
            HighlightGroup hgroup = (HighlightGroup) highlights.nextElement();
            VSUtil.renameHLConditionListRef(hgroup, oname, nname);
         }
      }

      // sycn the hyperlinks
      TableHyperlinkAttr hattr = getHyperlinkAttr();

      if(hattr != null) {
         Enumeration<TableDataPath> paths =
            (Enumeration<TableDataPath>) hattr.getAllDataPaths();
         Map<TableDataPath, TableDataPath> changes =
            syncTableDataPath(paths, oname, nname);

         Iterator<TableDataPath> keys = changes.keySet().iterator();

         while(keys.hasNext()) {
            TableDataPath npath = keys.next();
            TableDataPath opath = changes.get(npath);
            Hyperlink link = hattr.getHyperlink(opath);
            hattr.setHyperlink(opath, null);
            hattr.setHyperlink(npath, link);
         }

         Enumeration links = hattr.getAllHyperlinks();

         while(links.hasMoreElements()) {
            Hyperlink link = (Hyperlink) links.nextElement();
            VSUtil.renameHyperlinkRef(link, oname, nname);
         }
      }
   }

   /**
    * Sync the table data path.
    */
   private Map<TableDataPath, TableDataPath> syncTableDataPath(
      Enumeration<TableDataPath> paths, String oname, String nname)
   {
      Map<TableDataPath, TableDataPath> changedList =
         new HashMap<>();

      while(paths.hasMoreElements()) {
         TableDataPath path = paths.nextElement();
         TableDataPath npath = (TableDataPath) path.clone();

         boolean changed = false;
         String[] stringpath = npath.getPath();

         if(stringpath != null) {
            for(int i = 0; i < stringpath.length; i++) {
               if(VSUtil.matchRefName(stringpath[i], oname)) {
                  stringpath[i] = VSUtil.getMatchRefName(stringpath[i], nname);
                  changed = true;
               }
            }

            if(changed) {
               changedList.put(npath, path);
            }
         }
      }

      return changedList;
   }

   @Override
   public void changeCalcType(String refName, CalculateRef ref) {
      removeBindingCol(refName);
   }

   /**
    * Get the start row last time the table is loaded.
    */
   public int getLastStartRow() {
      return lastStartRow;
   }

   /**
    * Set the start row last time the table is loaded.
    */
   public void setLastStartRow(int row) {
      this.lastStartRow = row;
   }

   protected ConditionList dconds;
   private transient int lastStartRow = 0; // top row last time the table is loaded
}
