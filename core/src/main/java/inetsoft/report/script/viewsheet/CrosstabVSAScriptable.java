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
package inetsoft.report.script.viewsheet;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * The crosstab viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class CrosstabVSAScriptable extends TableDataVSAScriptable {
   /**
    * Create crosstab viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public CrosstabVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "CrosstabVSA";
   }

   /**
    * Add crosstab properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      CrosstabVSAssemblyInfo info = (CrosstabVSAssemblyInfo) getVSAssemblyInfo();
      VSCrosstabInfo cinfo = getCrosstabInfo();

      if(info == null || cinfo == null) {
         return;
      }

      addProperty("fillBlankWithZero", "isFillBlankWithZero",
                  "setFillBlankWithZero", boolean.class, cinfo.getClass(), cinfo);
      addProperty("summarySideBySide", "isSummarySideBySide",
                  "setSummarySideBySide", boolean.class, cinfo.getClass(), cinfo);
      addProperty("drillEnabled", "isDrillEnabled", "setDrillEnabled",
                  boolean.class, info.getClass(), info);
      addProperty("mergeSpan", "isMergeSpan", "setMergeSpan",
         boolean.class, cinfo.getClass(), cinfo);
      addProperty("sortOthersLast", "isSortOthersLast", "setSortOthersLast",
         boolean.class, cinfo.getClass(), cinfo);
      addProperty("computeTrendAndComparisonForTotals", "isCalculateTotal", "setCalculateTotal",
         boolean.class, cinfo.getClass(), cinfo);

      addProperty("query", "getQuery", "setQuery",
         String.class, getClass(), this);
      addProperty("bindingInfo",
         bindingInfo = new VSCrosstabBindingScriptable(this));
      addProperty("dateComparisonEnabled", "isDateComparisonEnabled", "setDateComparisonEnabled",
         boolean.class, info.getClass(), info);
   }

   /**
    * Get Query.
    */
   public String getQuery() {
      if(getInfo() instanceof CrosstabVSAssemblyInfo){
         return getInfo().getSourceInfo() == null ?
            null : getInfo().getSourceInfo().getSource();
      }

      return null;
   }

   /**
    * Set Query.
    */
   public void setQuery(String source) {
      getCrosstabInfo().removeFields();
      Viewsheet vs = box.getViewsheet();

      if(vs != null) {
         SourceInfo sinfo = new SourceInfo();

         sinfo.setType(SourceInfo.ASSET);
         // use cube:: to separate the viewsheet source and cube source
         sinfo.setSource(source.startsWith("cube::") ?
            Assembly.CUBE_VS + source.substring(6) : source);
         sinfo.setProperty("wsCube","false");
         getInfo().setSourceInfo(sinfo);
      }
   }

   /**
    * Set the size.
    * @param dim the dimension of size.
    */
   @Override
   public void setSize(Dimension dim) {
      VSAssemblyInfo info = getVSAssemblyInfo();
      Viewsheet vs = box.getViewsheet();
      String name = vs.getAssembly(assembly).getAbsoluteName();

      if(box.isRuntime()) {
         try {
            TableLens table = (TableLens) box.getData(name);

            if(table != null) {
               int width = (table.getHeaderColCount() + 1) * AssetUtil.defw;
               int height = (table.getHeaderRowCount() + 2) * AssetUtil.defh;
               Dimension osize = info.getPixelSize();

               if(dim.width < width || dim.height < height)
               {
                  dim.width = width;
                  dim.height = height;
               }

               if(dim.width > osize.width && info instanceof CrosstabVSAssemblyInfo) {
                  CrosstabVSAssemblyInfo cinfo = (CrosstabVSAssemblyInfo) info;
                  int count = cinfo.getColumnCount();
                  cinfo.setColumnCount(((dim.width - osize.width) / AssetUtil.defw) + count);

                  for(int i = count; i < cinfo.getColumnCount(); i++) {
                     cinfo.setColumnWidth(i, 70); // default width
                  }
               }
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to set crosstab size to " + dim, ex);
         }

         super.setSize(dim);
      }
   }

   /**
    * Get the assembly info of current crosstab.
    */
   protected CrosstabVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof CrosstabVSAssemblyInfo) {
         return (CrosstabVSAssemblyInfo) getVSAssemblyInfo();
      }

      return new CrosstabVSAssemblyInfo();
   }

   /**
    * Get cross tab info.
    */
   protected VSCrosstabInfo getCrosstabInfo() {
      Viewsheet vs = box.getViewsheet();
      VSAssembly vassembly = assembly == null ? null : vs.getAssembly(assembly);

      if(!(vassembly instanceof CrosstabVSAssembly)) {
         return new VSCrosstabInfo();
      }

      VSCrosstabInfo crosstabInfo = ((CrosstabVSAssembly) vassembly).getVSCrosstabInfo();
      return crosstabInfo == null ? new VSCrosstabInfo() : crosstabInfo;
   }

   @Override
   protected boolean isCrosstabOrCalc() {
      return true;
   }

   /**
    * Get the binding info of the cross tab.
    * @return the cross tab binding scriptable.
    */
   public VSCrosstabBindingScriptable getBindingInfo() {
      return bindingInfo;
   }

   @Override
   public void clearCache() {
      addProperties();
   }

   @Override
   public void clearCache(int type) {
      clearCache();
   }

   private VSCrosstabBindingScriptable bindingInfo;
   private static final Logger LOG =
      LoggerFactory.getLogger(CrosstabVSAScriptable.class);
}
