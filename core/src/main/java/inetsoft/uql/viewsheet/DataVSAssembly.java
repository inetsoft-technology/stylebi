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

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.internal.DataVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DataVSAssembly represents one data assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class DataVSAssembly extends AbstractVSAssembly
   implements DynamicBindableVSAssembly
{
   /**
    * Constructor.
    */
   public DataVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public DataVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Get the DataVSAssemblyInfo.
    * @return the DataVSAssemblyInfo.
    */
   protected DataVSAssemblyInfo getDataVSAssemblyInfo() {
      return (DataVSAssemblyInfo) getInfo();
   }

   /**
    * Get the assemblies depended on by its output values.
    * @param set the set stores the assemblies depended on.
    */
   @Override
   public void getDependeds(Set<AssemblyRef> set) {
      super.getDependeds(set);

      // maintain brush dependency properly. When chart has brush selection,
      // the data viewsheet assembly should apply the brush selection as
      // a condition list. Therefore some data might be filtered out
      String tname = getTableName();

      if(tname == null || tname.length() == 0) {
         return;
      }

      Viewsheet vs = getViewsheet();
      Assembly[] arr = vs.getAssemblies(false, false, false, false);
      ChartVSAssembly target = null;

      ConditionList conds = getPreConditionList();
      VSUtil.getConditionDependeds(conds, set, vs);

      if(this instanceof SelectionVSAssembly) {
         return;
      }
      else if(this instanceof EmbeddedTableVSAssembly) {
         return;
      }

      for(int i = 0; i < arr.length; i++) {
         if(arr[i] instanceof TimeSliderVSAssembly) {
            TimeSliderVSAssembly slider = (TimeSliderVSAssembly) arr[i];
            TimeInfo tinfo = ((TimeSliderVSAssembly) arr[i]).getTimeInfo();

            if(slider.getSourceType() == XSourceInfo.VS_ASSEMBLY) {
               String source = slider.getTableName();

               if(getName().equals(source)) {
                  set.add(new AssemblyRef(AssemblyRef.INPUT_DATA,
                                          arr[i].getAssemblyEntry()));
                  set.add(new AssemblyRef(AssemblyRef.OUTPUT_DATA,
                                          arr[i].getAssemblyEntry()));
               }
            }
         }
         else if(arr[i] instanceof DataVSAssembly) {
            SourceInfo sourceInfo = ((DataVSAssembly) arr[i]).getSourceInfo();

            if(sourceInfo != null && sourceInfo.getType() == XSourceInfo.VS_ASSEMBLY &&
               getName().equals(sourceInfo.getSource()))
            {
               set.add(new AssemblyRef(AssemblyRef.OUTPUT_DATA, arr[i].getAssemblyEntry()));
            }
         }

         if(!(arr[i] instanceof ChartVSAssembly)) {
            continue;
         }

         ChartVSAssembly chart = (ChartVSAssembly) arr[i];

         if(!tname.equals(chart.getTableName())) {
            continue;
         }

         // no selection
         if(!chart.containsBrushSelection() ||
            WizardRecommenderUtil.isTempAssembly(chart.getName()))
         {
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

         if(target != null && getName().equals(target.getName())) {
            target = null;
         }
      }

      if(target != null) {
         set.add(new AssemblyRef(AssemblyRef.OUTPUT_DATA, target.getAssemblyEntry()));
      }
   }

   /**
    * Get the worksheet assemblies depended on.
    * @return the worksheet assemblies depended on.
    */
   @Override
   public AssemblyRef[] getDependedWSAssemblies() {
      SourceInfo source = getSourceInfo();

      if(source != null) {
         int sourceType = source.getType();

         if(sourceType == XSourceInfo.VS_ASSEMBLY) {
            String ass = getTableName();
            Viewsheet vs = getViewsheet();

            if(vs != null && ass != null) {
               Assembly assembly = vs.getAssembly(ass);

               if(assembly instanceof VSAssembly) {
                  return ((VSAssembly) assembly).getDependedWSAssemblies();
               }
            }
         }
         else if(sourceType == SourceInfo.ASSET) {
            String table = source.getSource();
            Worksheet ws = getWorksheet();
            Assembly assembly = ws == null || table == null ? null :
               ws.getAssembly(table);

            if(assembly instanceof TableAssembly) {
               return new AssemblyRef[] {new AssemblyRef(AssemblyRef.INPUT_DATA,
                                                         assembly.getAssemblyEntry())};
            }
         }
      }

      return new AssemblyRef[0];
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
    * Get the source info of the target.
    * @return the source info of the target.
    */
   public SourceInfo getSourceInfo() {
      return getDataVSAssemblyInfo().getSourceInfo();
   }

   /**
    * Set the source info of the target.
    * @param src the specified source info of the target.
    */
   public void setSourceInfo(SourceInfo src) {
      getDataVSAssemblyInfo().setSourceInfo(src);
   }

   /**
    * Get show details table columns visibility property.
    */
   public ColumnSelection getDetailColumns() {
      if(getDataVSAssemblyInfo().getDetailColumns() != null) {
         this.detailColumns = getDataVSAssemblyInfo().getDetailColumns();
         getDataVSAssemblyInfo().setDetailColumns(null);
      }

      return this.detailColumns;
   }

   /**
    * Set show details table columns visibility property.
    */
   public void setDetailColumns(ColumnSelection infos) {
      this.detailColumns = infos;
   }

   /**
    * Get show data table columns visibility property.
    */
   public ColumnSelection getDataColumns() {
      return this.dataColumns;
   }

   /**
    * Set show data table columns visibility property.
    */
   public void setDataColumns(ColumnSelection infos) {
      this.dataColumns = infos;
   }

   /**
    * Get the target table.
    * @return the target table.
    */
   @Override
   public String getTableName() {
      SourceInfo source = getSourceInfo();
      boolean hasSource = source != null && !source.isEmpty() &&
         (source.getType() == SourceInfo.ASSET ||
            source.getType() == SourceInfo.VS_ASSEMBLY);
      return !hasSource ? null : source.getSource();
   }

   /**
    * Get the detail table name.
    * @return the detail table name.
    */
   public String getDetailTableName() {
      return DETAIL + getName();
   }

   /**
    * Set the name of the target table.
    * @param table the specified name of the target table.
    */
   @Override
   public void setTableName(String table) {
      SourceInfo source = getSourceInfo();

      if(source == null) {
         source = new SourceInfo();
      }

      source.setType(SourceInfo.ASSET);
      source.setSource(table);
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

      return isStandalone(set);
   }

   /**
    * Check if the dependency set is standalone.
    * @param set the specified dependency set.
    * @return <tt>true</tt> if standalone, <tt>false</tt> otherwise.
    */
   private boolean isStandalone(Set set) {
      Iterator it = set.iterator();

      while(it.hasNext()) {
         AssemblyRef ref = (AssemblyRef) it.next();
         AssemblyEntry entry = ref.getEntry();

         if(entry.isWSAssembly()) {
            continue;
         }
         else if(!entry.getName().equals(getName())) {
            return false;
         }
      }

      return true;
   }

   /**
    * Set column widths.
    */
   public void setColumnWidths(String widths) {
      this.colWidths = widths;
   }

   /**
    * Get column widths.
    */
   public String getColumnWidths() {
      return this.colWidths;
   }

   /**
    * Set show detailed sort ref.
    */
   public void setSortRef(SortRef sortRef) {
      this.sortRef = sortRef;
   }

   /**
    * Get show detailed column sort ref.
    */
   public SortRef getSortRef() {
      return this.sortRef;
   }

   /**
    * Set show data sort ref.
    */
   public void setDataSortRef(SortRef sortRef) {
      this.dataSortRef = sortRef;
   }

   /**
    * Get show data column sort ref.
    */
   public SortRef getDataSortRef() {
      return this.dataSortRef;
   }

   /**
    * Set show format.
    */
   public void setDataFormatInfo(FormatInfo finfo) {
      this.dataFormatInfo = finfo;
   }

   /**
    * Get show format .
    */
   public FormatInfo getDataFormatInfo() {
      return this.dataFormatInfo;
   }

   /**
    * Set show data column widths.
    */
   public void setDataColumnWidths(String widths) {
      this.dataColWidths = widths;
   }

   /**
    * Get show data column widths.
    */
   public String getDataColumnWidths() {
      return this.dataColWidths;
   }

   /**
    * Set the pre-condition list defined in this data viewsheet assembly.
    */
   @Override
   public int setPreConditionList(ConditionList conds) {
      return getDataVSAssemblyInfo().setPreConditionList(conds);
   }

   /**
    * Get the pre-condition list.
    */
   @Override
   public ConditionList getPreConditionList() {
      return getDataVSAssemblyInfo().getPreConditionList();
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      List<UserVariable> list = new ArrayList<>();
      ConditionList preconds = getPreConditionList();
      UserVariable[] vars = preconds == null ? new UserVariable[0] : preconds.getAllVariables();
      Viewsheet.mergeVariables(list, vars);
      final String tableName = getTableName();

      if(tableName != null) {
         list = list.stream().map(v -> {
            String choiceQuery = v.getChoiceQuery();

            if(choiceQuery != null && !choiceQuery.startsWith("[")) {
               v = v.clone();
               v.setChoiceQuery(tableName + "]:[" + v.getChoiceQuery());
            }

            return v;
         })
         .collect(Collectors.toList());
      }

      return list.toArray(new UserVariable[0]);
   }

   /**
    * Write the state.
    * @param writer the specified print writer.
    */
   @Override
   protected void writeStateContent(PrintWriter writer, boolean runtime) {
      super.writeStateContent(writer, runtime);

      if(colWidths != null) {
         writer.println("<colWidths>");
         writer.print("<![CDATA[" + colWidths + "]]>");
         writer.print("</colWidths>");
      }

      if(detailColumns != null) {
         writer.println("<columnsVisibility>");
         detailColumns.writeXML(writer);
         writer.println("</columnsVisibility>");
      }

      if(dataColWidths != null) {
         writer.println("<dataColWidths>");
         writer.print("<![CDATA[" + dataColWidths + "]]>");
         writer.print("</dataColWidths>");
      }

      if(dataColumns != null) {
         writer.println("<dataColumnsVisibility>");
         dataColumns.writeXML(writer);
         writer.println("</dataColumnsVisibility>");
      }

      if(sortRef != null) {
         writer.println("<sortRef>");
         sortRef.writeXML(writer);
         writer.print("</sortRef>");
      }

      if(dataSortRef != null) {
         writer.println("<dataSortRef>");
         dataSortRef.writeXML(writer);
         writer.print("</dataSortRef>");
      }

      if(dataFormatInfo != null) {
         dataFormatInfo.writeXML(writer);
      }

      if(runtime) {
         writeAnnotations(getDataVSAssemblyInfo(), writer);
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

      Element node = Tool.getChildNodeByTagName(elem, "colWidths");

      if(node != null) {
         colWidths = Tool.getValue(node);
      }

      node = Tool.getChildNodeByTagName(elem, "columnsVisibility");

      if(node != null) {
         detailColumns = new ColumnSelection();
         node = Tool.getChildNodeByTagName(node, "ColumnSelection");

         if(node != null) {
            detailColumns.parseXML(node);
         }
      }

      node = Tool.getChildNodeByTagName(elem, "dataColWidths");

      if(node != null) {
         dataColWidths = Tool.getValue(node);
      }

      node = Tool.getChildNodeByTagName(elem, "dataColumnsVisibility");

      if(node != null) {
         dataColumns = new ColumnSelection();
         node = Tool.getChildNodeByTagName(node, "ColumnSelection");

         if(node != null) {
            dataColumns.parseXML(node);
         }
      }

      node = Tool.getChildNodeByTagName(elem, "sortRef");

      if(node != null) {
         sortRef = new SortRef();
         node = Tool.getChildNodeByTagName(node, "dataRef");

         if(node != null) {
            sortRef.parseXML(node);
         }
      }

      node = Tool.getChildNodeByTagName(elem, "dataSortRef");

      if(node != null) {
         dataSortRef = new SortRef();
         node = Tool.getChildNodeByTagName(node, "dataRef");

         if(node != null) {
            dataSortRef.parseXML(node);
         }
      }

      node = Tool.getChildNodeByTagName(elem, "formatInfo");

      if(node != null) {
         dataFormatInfo = new FormatInfo();
         dataFormatInfo.reset();
         dataFormatInfo.parseXML(node);
      }

      if(runtime) {
         parseAnnotations(elem, getDataVSAssemblyInfo());
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public DataVSAssembly clone() {
      try {
         DataVSAssembly assembly = (DataVSAssembly) super.clone();

         if(detailColumns != null) {
            assembly.detailColumns = detailColumns.clone();
         }

         if(dataColumns != null) {
            assembly.dataColumns = dataColumns.clone();
         }

         return assembly;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone DataVSAssembly", ex);
         return null;
      }
   }

   @Override
   public DataRef[] getAllBindingRefs() {
      List<DataRef> datarefs = getBindingRefList();
      VSUtil.addConditionListRef(getPreConditionList(), datarefs);
      return datarefs.toArray(new DataRef[] {});
   }

   private String colWidths;  // detail column widths
   private ColumnSelection detailColumns;  // detail column visibility
   private String dataColWidths;  // data column widths
   private ColumnSelection dataColumns; // data column visiblity
   private SortRef sortRef; // detail sort ref
   private SortRef dataSortRef; // data sort ref
   private FormatInfo dataFormatInfo = new FormatInfo();
   private static final Logger LOG = LoggerFactory.getLogger(DataVSAssembly.class);
}
