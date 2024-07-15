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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.Hyperlink;
import inetsoft.report.TableDataPath;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.*;

/**
 * This is the base class of CrosstabVSAssemblyInfo and ChartVSAssemblyInfo.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class CrossBaseVSAssemblyInfo extends TableDataVSAssemblyInfo implements
   CubeVSAssemblyInfo, CrosstabDataVSAssemblyInfo
{
   /**
    * Constructor.
    */
   public CrossBaseVSAssemblyInfo() {
      super();
   }

   /**
    * Initialize the default format.
    */
   @Override
   public void initDefaultFormat() {
      setDefaultFormat(true);
   }

   /**
    * Get the range condition list.
    * @return the range condition list.
    */
   public ConditionList getRangeConditionList() {
      return range;
   }

   /**
    * Set the range condition list.
    * @param range the specified range condition list.
    * @return the changed hint.
    */
   public int setRangeConditionList(ConditionList range) {
      if(Tool.equals(this.range, range)) {
         return VSAssembly.NONE_CHANGED;
      }

      this.range = range;
      return VSAssembly.INPUT_DATA_CHANGED;
   }

   /**
    * Get the xcube.
    * @return the xcube.
    */
   @Override
   public XCube getXCube() {
      return cube;
   }

   /**
    * Set the xcube.
    * @param cube the specified xcube.
    */
   @Override
   public void setXCube(XCube cube) {
      this.cube = cube;
   }

   /**
    * Get the crosstab data info.
    * @return the crosstab data info.
    */
   @Override
   public VSCrosstabInfo getVSCrosstabInfo() {
      return cinfo;
   }

   /**
    * Set the crosstab data info.
    * @param info the crosstab data info.
    */
   @Override
   public void setVSCrosstabInfo(VSCrosstabInfo info) {
      this.cinfo = info;
   }

   @Override
   public void clearBinding() {
      super.clearBinding();
      this.cinfo = new VSCrosstabInfo();
   }

   /**
    * Get the header row height of the specified row.
    */
   public int[] getHeaderRowHeights(boolean isWrappedHeader, int headerRowCount) {
      updateCrossHeaderRowHeights(isWrappedHeader, headerRowCount);

      return getTableHeaderRowHeights();
   }

   /**
    * Update header row heights.
    */
   private void updateCrossHeaderRowHeights(boolean isWrappedHeader, int headerRowCount) {
      if(cinfo != null && (headerRowCount != getHeaderRowHeightsLength() ||
         cinfo.getAggregates().length > 1))
      {
         int[] rowHeights = new int[headerRowCount];

         for(int i = 0; i < headerRowCount; i++) {
            rowHeights[i] = getHeaderRowHeight(i);

            //Fixed bug #23732 that crosstab head string too long,
            //the cell height to be automatically adjusted
            //1.!getExplicitTableWidthValue(default layout) and string length is too long
            //2.Summary Cells Side By Side
            //3.is summary row header
            //4. isWrappedHeader
            if(getExplicitTableWidthValue() || !cinfo.isSummarySideBySide() ||
               getLongestAggrNameLength() < 10 || cinfo.getAggregates().length < 1 ||
               i != (headerRowCount - 1))
            {
               continue;
            }

            rowHeights[i] = isWrappedHeader ?
               getLongestAggrNameLength() / 10 * AssetUtil.defh : AssetUtil.defh;
         }

         setHeaderRowHeights(rowHeights);
      }
   }

   /**
    * update table header row height
    */
   public void updateHeaderRowHeights(boolean isWrappedHeader) {
      updateCrossHeaderRowHeights(isWrappedHeader, getHeaderRowHeightsLength());
   }

   private int getLongestAggrNameLength() {
      int longestAggrNameLength = 0;

      for(DataRef aggr: cinfo.getAggregates()) {
         if(longestAggrNameLength < ((VSAggregateRef) aggr).getFullName().length()) {
            longestAggrNameLength = ((VSAggregateRef) aggr).getFullName().length();
         }
      }

      return longestAggrNameLength;
   }

   /**
    * Get the crosstab hierarchical tree.
    */
   public CrosstabTree getCrosstabTree() {
      return ctree;
   }

   /**
    * Get the dynamic property values. Dynamic properties are properties that
    * can be a variable or a script.
    * @return the dynamic values.
    */
   @Override
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = super.getDynamicValues();

      if(cinfo != null) {
         list.addAll(cinfo.getDynamicValues());
      }

      return list;
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      writer.print("<range>");

      if(range != null) {
         range.writeXML(writer);
      }

      writer.print("</range>");

      if(cube instanceof VSCube) {
         ((VSCube) cube).writeXML(writer);
      }

      if(cinfo != null) {
         cinfo.writeXML(writer);
      }

      if(ctree != null) {
         ctree.writeXML(writer);
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element dnode = Tool.getChildNodeByTagName(elem, "VSCube");

      if(dnode != null) {
         cube = new VSCube();
         ((VSCube) cube).parseXML(dnode);
      }

      Element rnode = Tool.getChildNodeByTagName(elem, "range");

      if(rnode != null) {
         Element rcnode = Tool.getChildNodeByTagName(rnode, "conditions");

         if(rcnode != null) {
            range = new ConditionList();
            range.parseXML(rcnode);
         }
      }

      Element enode = Tool.getChildNodeByTagName(elem, "VSCrosstabInfo");

      if(enode != null) {
         cinfo = new VSCrosstabInfo();
         cinfo.parseXML(enode);
      }

      enode = Tool.getChildNodeByTagName(elem, "CrosstabTree");

      if(enode != null) {
         ctree.parseXML(enode);
      }
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public CrossBaseVSAssemblyInfo clone(boolean shallow) {
      try {
         CrossBaseVSAssemblyInfo info = (CrossBaseVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            if(range != null) {
               info.range = range.clone();
            }

            if(cube instanceof VSCube) {
               info.cube = (XCube) ((VSCube) cube).clone();
            }

            if(cinfo != null) {
               info.cinfo = (VSCrosstabInfo) cinfo.clone();
            }

            if(ctree != null) {
               info.ctree = (CrosstabTree) ctree.clone();
            }
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone CrossBaseVSAssemblyInfo", ex);
      }

      return null;
   }

   /**
    * Copy the input data part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return new hint.
    */
   @Override
   protected int copyInputDataInfo(VSAssemblyInfo info, int hint) {
      CrossBaseVSAssemblyInfo cinfo = (CrossBaseVSAssemblyInfo) info;
      boolean src_changed = !Tool.equals(getSourceInfo(), cinfo.getSourceInfo());

      hint = super.copyInputDataInfo(info, hint);
      SourceInfo source = getSourceInfo();

      if(source != null && (source.getType() == SourceInfo.ASSET ||
         source.getType() == SourceInfo.VS_ASSEMBLY) &&
         !Tool.equals(cube, cinfo.cube))
      {
         cube = cinfo.cube;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
         hint |= VSAssembly.BINDING_CHANGED;
      }

      // clear hierarchy if source is changed
      if(src_changed) {
         cube = null;
      }

      if(!Tool.equals(this.cinfo, cinfo.cinfo)) {
         // if not re-executing, make sure runtime list not lost
         if(this.cinfo != null && this.cinfo.equalsIgnoreSorting(cinfo.cinfo))
         {
            cinfo.cinfo.setRuntimeRowHeaders(this.cinfo.getRuntimeRowHeaders());
            cinfo.cinfo.setRuntimeColHeaders(this.cinfo.getRuntimeColHeaders());
            cinfo.cinfo.setRuntimeAggregates(this.cinfo.getRuntimeAggregates());
         }

         this.cinfo = cinfo.cinfo;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
         hint |= VSAssembly.BINDING_CHANGED;
      }

      if(!Tool.equals(range, cinfo.range)) {
         range = cinfo.range;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      if(this.cinfo != null) {
         this.cinfo.setRuntime(false);
      }

      return hint;
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();

      if(cinfo == null) {
         return;
      }

      cinfo.resetRuntimeValues();
   }

   /**
    * Update the info to fill in runtime value.
    * @param vs the specified viewsheet.
    * @param columns the specified column selection.
    */
   @Override
   public void update(Viewsheet vs, ColumnSelection columns) throws Exception {
      super.update(vs, columns);

      if(cinfo != null) {
         DataRef[] aggrs = cinfo.getRuntimeAggregates();
         DataRef[] dims = (DataRef[]) Tool.mergeArray(
            cinfo.getRuntimeRowHeaders(), cinfo.getRuntimeColHeaders());

         SourceInfo source = getSourceInfo();

         if(source == null) {
            return;
         }

         String table = source.getSource();
         String cubeType = VSUtil.getCubeType(source);

         if(cube == null) {
            SourceInfo src = getSourceInfo();

            if(src != null) {
               cube = AssetUtil.getCube(src.getPrefix(), src.getSource());
            }
         }

         // cube not supports aggregate except SQLServer Analysis Service
         boolean aalias = cubeType == null ||
            cubeType != null && XCube.SQLSERVER.equals(cubeType);

         DateComparisonInfo dcInfo = null;

         if(this instanceof CrosstabVSAssemblyInfo) {
            CrosstabVSAssemblyInfo cinfo = (CrosstabVSAssemblyInfo) this;
            dcInfo = DateComparisonUtil.getDateComparison(cinfo, vs);

            if(dcInfo == null || dcInfo.invalid()) {
               dcInfo = null;
               cinfo.resetRuntimeDateComparisonInfo();
            }
         }

         cinfo.update(vs, columns, cube, aalias, source.getSource(), dcInfo);
         removeUselessChildRefs();
         VSDimensionRef dref = VSUtil.createPeriodDimensionRef(getViewsheet(),
            (DataVSAssembly) vs.getAssembly(getAbsoluteName()), columns, cinfo);

         if(dref != null) {
            if(cinfo.getPeriodRuntimeRowHeaders().length == cinfo.getRuntimeRowHeaders().length) {
               DataRef[] rheaders = cinfo.getRuntimeRowHeaders();
               DataRef[] nrheaders = new DataRef[rheaders.length + 1];
               System.arraycopy(rheaders, 0, nrheaders, 1, rheaders.length);
               nrheaders[0] = dref;
               cinfo.setPeriodRuntimeRowHeaders(nrheaders);
            }

            ctree.updateHierarchy(cinfo, cube, cubeType);
         }
         else {
            cinfo.setPeriodRuntimeRowHeaders(null);
            ctree.updateHierarchy(cinfo, cube, cubeType);
         }

         if(this instanceof CrosstabVSAssemblyInfo) {
            DateComparisonUtil.syncWeekGroupingLevels((CrosstabVSAssemblyInfo) this);
         }

         // @by billh, as column value is dynamic, here we need to sync
         // fmt/highlight/hyperlink for backward compatibility properly
         if(getFormatInfo() != null && aalias) {
            syncPath(getFormatInfo().getFormatMap());
         }

         if(getHyperlinkAttr() != null && aalias) {
            Map<TableDataPath, Hyperlink> map = getHyperlinkAttr().getHyperlinkMap();
            syncPath(map);
            Map<String, String> kmap = new HashMap<>();

            for(int i = 0; i < aggrs.length; i++) {
               VSAggregateRef aggr = (VSAggregateRef) aggrs[i];

               if(aggr == null) {
                  continue;
               }

               DataRef ref = aggr.getDataRef();

               if(ref instanceof ColumnRef) {
                  DataRef bref = ((ColumnRef) ref).getDataRef();
                  boolean aliased = bref instanceof AliasDataRef;

                  if(aliased) {
                     AliasDataRef aref = (AliasDataRef) bref;
                     ref = aref.getDataRef();
                     String fname = ref == null ? null : ref.getName();

                     if(fname != null) {
                        String tname = aggr.getVSName();
                        kmap.put(fname, tname);
                     }
                  }
               }
            }

            // @by ankitmathur, For bug1431551619407, add the Hyperlink parameter
            // substitute for Dimensions (row/col headers) to kmap as well.
            for(int i = 0; i < dims.length; i++) {
               VSDimensionRef vsdim = (VSDimensionRef) dims[i];

               if(vsdim.isDynamic()) {
                  DataRef dvsDim = vsdim.getDataRef();
                  String fname = dvsDim == null ? null :
                     vsdim.getGroupColumnValue();

                  if(fname != null) {
                     String tname = vsdim.getName();
                     kmap.put(fname, tname);
                  }
               }
            }

            List<TableDataPath> list = new ArrayList<>(map.keySet());

            for(TableDataPath path : list) {
               Hyperlink link = map.get(path);
               replaceParameters(link, kmap);
            }
         }

         if(getHighlightAttr() != null && aalias) {
            syncPath(getHighlightAttr().getHighlightMap());
         }

         signNoneDateLevelHyperlinkFields(dims);
      }
   }

   /**
    * fix
    * @param dims
    */
   private void signNoneDateLevelHyperlinkFields(DataRef[] dims) {
      TableHyperlinkAttr hyperlinkAttr = getHyperlinkAttr();

      if(hyperlinkAttr != null) {
         Enumeration<Hyperlink> allHyperlinks = hyperlinkAttr.getAllHyperlinks();

         while(allHyperlinks.hasMoreElements()) {
            Hyperlink hyperlink = allHyperlinks.nextElement();

            if(hyperlink == null || hyperlink.getParameterNames() == null) {
               continue;
            }

            hyperlink.clearNodeDateLevelFields();

            for(String parameter : hyperlink.getParameterNames()) {
               String filed = hyperlink.getParameterField(parameter);

               if(filed == null) {
                  continue;
               }

               VSDataRef dim = searchVSDataRef(dims, filed);

               if(dim instanceof VSDimensionRef && ((VSDimensionRef) dim).isDate() &&
                  ((VSDimensionRef) dim).getDateLevel() == DateRangeRef.NONE_INTERVAL)
               {
                  hyperlink.setNodeDateLevelField(filed);
               }
            }
         }
      }
   }

   private VSDataRef searchVSDataRef(DataRef[] refs, String field) {
      if(refs == null || Tool.isEmptyString(field)) {
         return null;
      }

      for(DataRef ref : refs) {
         if(ref instanceof VSDataRef && Objects.equals(((VSDataRef) ref).getFullName(), field)) {
            return (VSDataRef) ref;
         }
      }

      return null;
   }

   /**
    * Replace parameters.
    */
   private void replaceParameters(Hyperlink link, Map<String, String> kmap) {
      if(link == null) {
         return;
      }

      List<String> list = link.getParameterNames();

      for(String pname : list) {
         String pval = link.getParameterField(pname);
         String pval2 = kmap.get(pval);

         if(pval2 != null) {
            link.setParameterField(pname, pval2);
            link.setParameterLabel(pname, pval2);
         }
      }
   }

   /**
    * Synchronize the table data path in this map, only process bc problem.
    */
   private <V> void syncPath(Map<TableDataPath, V> map) {
      DataRef[] refs = cinfo == null ? null : cinfo.getRuntimeAggregates();
      List<TableDataPath> list = new ArrayList<>(map.keySet());
      int len = list.size();

      for(TableDataPath tp : list) {
         if(tp == null) {
            continue;
         }

         String[] arr = tp.getPath();

         if(tp.getType() == TableDataPath.GROUP_HEADER) {
            // no col header? summary column will be shown
            if(arr.length == 0 && refs != null && refs.length == 1) {
               String[] narr = new String[1];
               narr[0] = ((VSAggregateRef) refs[0]).getVSName();
               TableDataPath ntp = (TableDataPath) tp.clone(narr);
               V obj = map.remove(tp);
               map.put(ntp, obj);
            }
         }
         else if(tp.getType() == TableDataPath.SUMMARY ||
                 tp.getType() == TableDataPath.GRAND_TOTAL ||
                 tp.getType() == TableDataPath.HEADER)
         {
            String col = arr == null || arr.length == 0 ?
               null : arr[arr.length - 1];
            VSAggregateRef aggr = findRuntimeAggregate(col);
            String name = aggr == null ? null : aggr.getVSName();

            if(name != null && !name.equals(col)) {
               arr = arr.clone();
               arr[arr.length - 1] = name;
               TableDataPath ntp = (TableDataPath) tp.clone(arr);
               V obj = map.remove(tp);
               map.put(ntp, obj);
            }
         }
      }
   }

   /**
    * Get the aggregate for the given column.
    */
   private VSAggregateRef findRuntimeAggregate(String col) {
      DataRef[] refs = cinfo == null ? null : cinfo.getRuntimeAggregates();

      if(col == null || refs == null) {
         return null;
      }

      for(int i = 0; i < refs.length; i++) {
         VSAggregateRef aggr = (VSAggregateRef) refs[i];
         DataRef ref = aggr == null ? null : aggr.getDataRef();
         String fname = ref == null ? null : ref.getName();

         if(col.equals(fname)) {
            return aggr;
         }
      }

      for(int i = 0; i < refs.length; i++) {
         VSAggregateRef aggr = (VSAggregateRef) refs[i];
         DataRef ref = aggr == null ? null : aggr.getDataRef();

         if(ref instanceof ColumnRef) {
            DataRef bref = ((ColumnRef) ref).getDataRef();
            boolean aliased = bref instanceof AliasDataRef;

            if(aliased) {
               AliasDataRef aref = (AliasDataRef) bref;
               ref = aref.getDataRef();
            }
         }

         String fname = ref == null ? null : ref.getName();

         if(col.equals(fname)) {
            return aggr;
         }
      }

      return null;
   }

   /**
    * If date range field using variable dlevel, should remove the useless childrefs
    * when runtime dlevel changed to avoid keeping the wrong childref.
    */
   private void removeUselessChildRefs() {
      removeUselessChildRefs(cinfo.getRuntimeRowHeaders(), ctree);
      removeUselessChildRefs(cinfo.getRuntimeColHeaders(), ctree);
   }

   private void removeUselessChildRefs(DataRef[] dims, CrosstabTree tree) {
      if(dims == null || dims.length == 0) {
         return;
      }

      for(DataRef ref : dims) {
         if(ref instanceof VSDimensionRef && ((VSDimensionRef) ref).runtimeDateLevelChange()) {
            removeParentRef(((VSDimensionRef) ref), tree);
            removeChildRef(((VSDimensionRef) ref), tree);
         }
      }
   }

   private void removeParentRef(VSDimensionRef ref, CrosstabTree tree) {
      VSDimensionRef parentRef = VSUtil.getLastDrillLevelRef(ref, cube);

      if(parentRef == null) {
         String rootKey = CrosstabTree.getHierarchyRootKey(ref);
         VSDimensionRef childRef = tree.getChildRef(rootKey);

         if(childRef != null) {
            tree.removeChildRef(rootKey);
         }

         return;
      }

      tree.removeChildRef(ref.getFullName());
      removeParentRef(parentRef, tree);
   }

   private void removeChildRef(VSDimensionRef ref, CrosstabTree tree) {
      VSDimensionRef childRef = tree.getChildRef(ref.getFullName());

      if(childRef == null) {
         return;
      }

      tree.removeChildRef(ref.getFullName());
      removeChildRef(ref, tree);
   }

   /**
    * Check if the dynamic value of the format should be processed.
    */
   @Override
   protected boolean isProcessFormat(TableDataPath path) {
      return path.getType() != TableDataPath.SUMMARY &&
         path.getType() != TableDataPath.GRAND_TOTAL &&
         super.isProcessFormat(path);
   }

   // input data
   private XCube cube;
   private ConditionList range;
   private VSCrosstabInfo cinfo = new VSCrosstabInfo();
   private CrosstabTree ctree = new CrosstabTree();

   private static final Logger LOG =
      LoggerFactory.getLogger(CrossBaseVSAssemblyInfo.class);
}
