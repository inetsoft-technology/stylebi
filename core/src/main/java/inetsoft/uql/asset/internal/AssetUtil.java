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
package inetsoft.uql.asset.internal;

import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.*;
import inetsoft.report.internal.Util;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.table.*;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.XAggregateRef;
import inetsoft.uql.xmla.HierDimension;
import inetsoft.util.*;
import inetsoft.util.dep.*;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.security.Principal;
import java.sql.Time;
import java.text.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;

import static java.util.stream.Collectors.toSet;

/**
 * Asset utilities.
 *
 * @author InetSoft Technology Corp
 * @version 8.0
 */
public class AssetUtil {
   /**
    * Outer prefix.
    */
   public static final String OUTER_PREFIX = "OUTER";
   /**
    * Range Slider start suffix.
    */
   public static final String RANGE_SLIDER_START = "_start";
   /**
    * Range Slider end suffix.
    */
   public static final String RANGE_SLIDER_END = "_end";
   /**
    * Calendar start suffix.
    */
   public static final String CALENDAR_START = "_dateStart";
   /**
    * Calendar end suffix.
    */
   public static final String CALENDAR_END = "_dateEnd";
   /**
    * Logical model prefix.
    */
   private static final String DATA_MODEL = "Data Model";

   /**
    * Check if this column is a null column from multi-aesthetic.
    */
   public static boolean isNullExpression(DataRef ref) {
      if(ref instanceof ExpressionRef && !(ref instanceof DateRangeRef)) {
         String exp = null;

         try {
            exp = ((ExpressionRef) ref).getExpression();
         }
         catch(Exception ex) {
            // ignore it
         }

         if("null".equals(exp)) {
            return true;
         }
      }

      if(ref instanceof DataRefWrapper) {
         ref = ((DataRefWrapper) ref).getDataRef();
         return isNullExpression(ref);
      }

      return false;
   }

   /**
    * Check if parameter is from selection.
    */
   public static boolean isSelectionParam(String parameter) {
      return parameter != null && (parameter.endsWith(CALENDAR_START) ||
         parameter.endsWith(CALENDAR_END) ||
         parameter.endsWith(RANGE_SLIDER_START) ||
         parameter.endsWith(RANGE_SLIDER_END));
   }

   /**
    * Get selection parameter value.
    */
   public static Object getSelectionParam(String name, Object obj)
      throws Exception {
      if(!isSelectionParam(name)) {
         return null;
      }

      if(obj instanceof Object[]) {
         Object[] objects = (Object[]) obj;
         Date[] dates = new Date[objects.length];

         for(int i = 0; i < dates.length; i++) {
            dates[i] = getDate(name, objects[i]);
         }

         return dates;
      }

      return getDate(name, obj);
   }

   /**
    * Get date object from viewsheet TimeSlider & Calendar properly.
    */
   public static Date getDate(String name, Object val) throws Exception {
      if(val == null) {
         return null;
      }

      Date date = null;
      boolean time = false;

      // calendar
      if(name.endsWith(CALENDAR_START) || name.endsWith(CALENDAR_END)) {
         String value = (String) val;
         date = value.isEmpty() ? null : Tool.parseDate(value);
      }

      // time slider
      if(name.endsWith(RANGE_SLIDER_START) || name.endsWith(RANGE_SLIDER_END)) {
         String value = (String) val;

         if(value.startsWith("{y ")) {
            date = Tool.yearFmt.get().parse(value);

            if(name.endsWith(RANGE_SLIDER_END)) {
               Calendar cal = CoreTool.calendar.get();
               cal.setTime(date);
               cal.set(Calendar.MONTH, Calendar.DECEMBER);
               cal.set(Calendar.DAY_OF_MONTH, 31);
               date = cal.getTime();
            }
         }
         else if(value.startsWith("{m ")) {
            date = Tool.monthFmt.get().parse(value);

            if(name.endsWith(RANGE_SLIDER_END)) {
               Calendar cal = CoreTool.calendar.get();
               cal.setTime(date);
               cal.add(Calendar.MONTH, 1);
               long ts = cal.getTimeInMillis();
               cal.setTimeInMillis(ts - 1);
               date = cal.getTime();
            }
         }
         else if(value.startsWith("{d ")) {
            date = Tool.dayFmt.get().parse(value);
         }
         else if(value.startsWith("{t ")) {
            time = true;
            date = Tool.timeFmt.get().parse(value);
            date = new Time(date.getTime());
         }
      }

      if(date != null && (name.endsWith(CALENDAR_END) ||
         name.endsWith(RANGE_SLIDER_END) && !time)) {
         Calendar cal = CoreTool.calendar.get();
         cal.setTime(date);
         cal.set(Calendar.HOUR_OF_DAY, 23);
         cal.set(Calendar.MINUTE, 59);
         cal.set(Calendar.SECOND, 59);
         cal.set(Calendar.MILLISECOND, 999);
         date = cal.getTime();
      }

      return date;
   }

   /**
    * Get condition assemblies depended on.
    * 1. subquery table
    * 2. condition assembly
    * 3. condition list date range assembly
    */
   public static void getConditionDependeds(Worksheet ws,
                                            ConditionListWrapper list, Set set) {
      if(list == null) {
         return;
      }

      if(list instanceof ConditionAssembly) {
         ConditionAssembly assembly = (ConditionAssembly) list;
         set.add(new AssemblyRef(assembly.getAssemblyEntry()));
      }
      else {
         for(int i = 0; i < list.getConditionSize(); i += 2) {
            ConditionItem item = list.getConditionItem(i);
            XCondition condition = item.getXCondition();

            if(condition instanceof DateRangeAssembly) {
               DateRangeAssembly assembly = (DateRangeAssembly) condition;
               set.add(new AssemblyRef(assembly.getAssemblyEntry()));
            }
            else if(condition instanceof AssetCondition) {
               AssetCondition acondition = (AssetCondition) condition;
               acondition.getDependeds(set);
            }

            UserVariable[] vars = condition.getAllVariables();

            for(int j = 0; j < vars.length; j++) {
               String name = vars[j].getName();
               Assembly[] arr = ws.getAssemblies();

               for(int k = 0; k < arr.length; k++) {
                  if(arr[k] instanceof VariableAssembly) {
                     VariableAssembly vassembly = (VariableAssembly) arr[k];
                     UserVariable var = vassembly.getVariable();

                     if(var != null && name.equals(var.getName())) {
                        set.add(new AssemblyRef(vassembly.getAssemblyEntry()));
                     }
                  }
               }
            }
         }
      }
   }

   /**
    * Reset a condition list wrapper.
    *
    * @param list the specified condition list wrapper.
    */
   public static void resetCondition(ConditionListWrapper list) {
      for(int i = 0; i < list.getConditionSize(); i += 2) {
         ConditionItem item = list.getConditionItem(i);
         XCondition condition = item.getXCondition();

         if(condition instanceof AssetCondition) {
            AssetCondition acondition = (AssetCondition) condition;
            SubQueryValue value = acondition.getSubQueryValue();

            if(value != null) {
               value.reset();
            }
         }
      }
   }

   /**
    * Update an entries metadata; including created by and modified by
    *
    * @param entry   the entry to update
    * @param user the user to record in the update
    */
   public static void updateMetaData(AssetEntry entry, Principal user, long time) {
      String username = user == null ? null : IdentityID.getIdentityIDFromKey(user.getName()).name;
      Date now = new Date(time);

      if (entry.getModifiedDate() == null) {
         entry.setModifiedDate(now);
      }

      if (entry.getModifiedUsername() == null) {
         entry.setModifiedUsername(username);
      }

      if(entry.getCreatedDate() == null) {
         entry.setCreatedDate(now);
      }

      if(entry.getCreatedUsername() == null) {
         entry.setCreatedUsername(username);
      }
   }

   public static String getEntryLabel(AssetEntry entry, Catalog catalog) {
      String label = entry.getProperty("localStr");
      label = label != null && !label.equals("") ?
         label : catalog.getString(entry.toView());
      return label;
   }

   /**
    * Filter to shrink conditions.
    */
   public static interface Filter {
      /**
       * Check if condition item with the specified ref need to be kept.
       */
      public boolean keep(DataRef attr);
   }

   public static void printColumnsKey(ColumnSelection cols, PrintWriter writer,
                                      boolean fullyQualify)
   {
      if(cols == null) {
         return;
      }

      writer.print("COLS[");
      int cnt = cols.getAttributeCount();
      writer.print(cnt);

      for(int i = 0; i < cnt; i++) {
         writer.print(",");
         DataRef ref = cols.getAttribute(i);
         ConditionUtil.printDataRefKey(ref, writer, fullyQualify);
      }

      writer.print("]");
   }

   /**
    * Check if two column selections are equal in content.
    *
    * @param cols1 the specified column selection 1.
    * @param cols2 the specified column selection 2.
    * @return <tt>true</tt> if they are equal in content, <tt>false</tt>
    *         otherwise.
    */
   public static boolean equalsColumns(ColumnSelection cols1,
                                       ColumnSelection cols2) {
      if(cols1 == null || cols2 == null) {
         return cols1 == cols2;
      }

      if(cols1.getAttributeCount() != cols2.getAttributeCount()) {
         return false;
      }

      for(int i = 0; i < cols1.getAttributeCount(); i++) {
         DataRef ref1 = cols1.getAttribute(i);
         DataRef ref2 = cols2.getAttribute(i);

         if(!ConditionUtil.equalsDataRef(ref1, ref2)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Check if contains runtime condition.
    */
   public static boolean containsRuntimeCondition(TableAssembly table, boolean recursive) {
      if(table == null) {
         return false;
      }

      ConditionListWrapper conds = table.getPreRuntimeConditionList();

      if(conds != null && !conds.isEmpty()) {
         return true;
      }

      conds = table.getPostRuntimeConditionList();

      if(conds != null && !conds.isEmpty()) {
         return true;
      }

      if(!recursive || !(table instanceof ComposedTableAssembly)) {
         return false;
      }

      TableAssembly[] tables =
         ((ComposedTableAssembly) table).getTableAssemblies(false);

      for(int i = 0; tables != null && i < tables.length; i++) {
         if(containsRuntimeCondition(tables[i], recursive)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get the data source for the specified table if any.
    */
   public static XDataSource getDataSource(TableAssembly table) throws Exception {
      if(!(table instanceof BoundTableAssembly) &&
         !(table instanceof ComposedTableAssembly)) {
         return null;
      }

      if(table instanceof ComposedTableAssembly) {
         ComposedTableAssembly ctable = (ComposedTableAssembly) table;
         TableAssembly[] tables = ctable.getTableAssemblies(false);

         for(int i = 0; i < tables.length; i++) {
            XDataSource ds = getDataSource(tables[i]);

            if(ds != null) {
               return ds;
            }
         }

         return null;
      }

      SourceInfo source = ((BoundTableAssembly) table).getSourceInfo();

      if(source.isEmpty()) {
         return null;
      }

      int type = source.getType();

      if(type == SourceInfo.MODEL || type == SourceInfo.PHYSICAL_TABLE ||
        type == SourceInfo.DATASOURCE)
      {
         String name = source.getPrefix();
         XRepository repository = XFactory.getRepository();
         return repository.getDataSource(name);
      }
      else {
         throw new RuntimeException("Unsupported type found: " + source);
      }
   }

   /**
    * Get the user variables of a source.
    *
    * @param table the specified source.
    * @return the user variables of the source.
    */
   public static UserVariable[] getSourceVariables(TableAssembly table)
      throws Exception
   {
      SourceInfo source = null;

      if(table instanceof BoundTableAssembly) {
         source = ((BoundTableAssembly) table).getSourceInfo();
      }

      if(source == null || source.isEmpty()) {
         return new UserVariable[0];
      }

      XDataService service = XFactory.getDataService();
      Object session = service.bind(System.getProperty("user.name"));
      UserVariable[] vars = new UserVariable[0];
      int type = source.getType();

      if(type == SourceInfo.MODEL || type == SourceInfo.PHYSICAL_TABLE ||
         type == SourceInfo.CUBE)
      {
         String name = source.getPrefix();
         vars = service.getConnectionParameters(session, ":" + name);
         vars = vars == null ? new UserVariable[0] : vars;
         validateAlias(vars);
      }

      return vars;
   }

   /**
    * Get the column ref.
    *
    * @param attr the specified attribute.
    */
   public static ColumnRef getColumn(DataRef attr) {
      while(!(attr instanceof ColumnRef) && (attr instanceof DataRefWrapper)) {
         attr = ((DataRefWrapper) attr).getDataRef();
      }

      return attr instanceof ColumnRef ? (ColumnRef) attr : null;
   }

   /**
    * Get the base attibute of an attribute.
    *
    * @param attr the specified attribute.
    * @return the base attibute of the attibute.
    */
   public static DataRef getBaseAttribute(DataRef attr) {
      while(attr instanceof DataRefWrapper && !(attr instanceof RangeRef)) {
         attr = ((DataRefWrapper) attr).getDataRef();
      }

      return attr;
   }

   /**
    * Retrieve the column ref from the column selection that has the matching
    * attribute.
    */
   public static ColumnRef getColumnRefFromAttribute(ColumnSelection columns,
                                                     DataRef ref) {
      return getColumnRefFromAttribute(columns, ref, false);
   }

   /**
    * Retrieve the column ref from the column selection that has the matching
    * attribute.
    *
    * @param calias true to check alias when attributes are equal.
    */
   public static ColumnRef getColumnRefFromAttribute(ColumnSelection columns,
                                                     DataRef ref,
                                                     boolean calias) {
      if(ref == null) {
         return null;
      }

      if(ref instanceof ColumnRef) {
         ref = ((ColumnRef) ref).getDataRef();
      }

      Enumeration iter = columns.getAttributes();
      ColumnRef column2 = null;
      ColumnRef column3 = null;

      while(iter.hasMoreElements()) {
         ColumnRef dref = (ColumnRef) iter.nextElement();
         String alias = dref.getAlias();
         boolean aexisting = alias != null && alias.length() > 0;

         if(Tool.equals(alias, ref.getAttribute())) {
            return dref;
         }
         else if(Tool.equals(dref.getAttribute(), ref.getAttribute())) {
            if(!aexisting || !calias) {
               column2 = dref;
            }
            else {
               column3 = dref;
            }
         }
      }

      if(column2 != null) {
         return column2;
      }
      else if(column3 != null) {
         return column3;
      }

      // try contained data ref for alias data ref
      if(ref instanceof AliasDataRef) {
         ref = ((AliasDataRef) ref).getDataRef();
      }

      iter = columns.getAttributes();

      while(iter.hasMoreElements()) {
         ColumnRef dref = (ColumnRef) iter.nextElement();

         if(Tool.equals(dref.getAlias(), ref.getAttribute())) {
            return dref;
         }
         else if(Tool.equals(dref.getAttribute(), ref.getAttribute())) {
            column2 = dref;
         }
      }

      return column2;
   }

   /**
    * Get a data ref for the column in the mirror table.
    *
    * @param table the mirror table name.
    * @param column the column data ref of the inner table (e.g. base of mirror).
    * @return data ref refering to the same column in 'table'.
    */
   public static DataRef getOuterAttribute(String table, DataRef column) {
      if(column == null) {
         return null;
      }

      String talias = (column instanceof ColumnRef) ? ((ColumnRef) column).getAlias() : null;
      String name = talias == null ? column.getAttribute() : talias;
      DataRef ref = column instanceof ColumnRef ? ((ColumnRef) column).getDataRef() : column;
      AttributeRef attr = new AttributeRef(table, name,
         ref instanceof AttributeRef && ((AttributeRef) ref).isCubeCalcMember());
      attr.setRefType(column.getRefType());
      attr.setDefaultFormula(column.getDefaultFormula());

      if(column instanceof ColumnRef) {
         attr.setDataType(column.getDataType());
         attr.setSqlType(((ColumnRef) column).getSqlType());
      }

      if(ref instanceof AttributeRef) {
         String caption = ((AttributeRef) ref).getCaption();

         if(caption != null && caption.length() > 0) {
            attr.setCaption(caption);
         }

         attr.setSqlType(((AttributeRef) ref).getSqlType());
      }

      return attr;
   }

   /**
    * Check if a table assembly is or contains CubeTableAssembly.
    *
    * @param assembly the specified table assembly.
    * @return <tt>true</tt> if is, <tt>false</tt> otherwise.
    */
   public static boolean isCubeTable(TableAssembly assembly) {
      return getBaseCubeTable(assembly) != null;
   }

   /**
    * Check if a table assembly is worksheet cube.
    *
    * @param assembly the specified table assembly.
    * @return <tt>true</tt> if is, <tt>false</tt> otherwise.
    */
   public static boolean isWorksheetCube(TableAssembly assembly) {
      CubeTableAssembly cube = getBaseCubeTable(assembly);

      if(cube == null) {
         return false;
      }

      return cube.getName().indexOf(Assembly.CUBE_VS) < 0;
   }

   /**
    * Check if a table assembly is or contains CubeTableAssembly.
    *
    * @param assembly the specified table assembly.
    * @return <tt>true</tt> if is, <tt>false</tt> otherwise.
    */
   public static CubeTableAssembly getBaseCubeTable(TableAssembly assembly) {
      while(assembly instanceof MirrorTableAssembly) {
         assembly = ((MirrorTableAssembly) assembly).getTableAssembly();
      }

      return assembly instanceof CubeTableAssembly ?
         (CubeTableAssembly) assembly : null;
   }

   /**
    * Find the column in a column selection.
    *
    * @param columns the specified column selection.
    * @param column  the specified column.
    */
   public static ColumnRef findColumn(ColumnSelection columns, ColumnRef column) {
      int index = columns.indexOfAttribute(column);

      return index >= 0 ? (ColumnRef) columns.getAttribute(index) : column;
   }

   /**
    * Find the column of a table column index.
    *
    * @param table   the specified table.
    * @param col     the specified column index.
    * @param columns the specified column selection.
    * @return the column of the table column index.
    */
   public static ColumnRef findColumn(XTable table, int col, ColumnSelection columns) {
      return findColumn(table, col, columns, false);
   }

   /**
    * Find the column of a table column index.
    *
    * @param table   the specified table.
    * @param col     table column index.
    * @param columns column selection to search.
    * @param exact   <tt>true</tt> to match the data ref totally,
    * @return the column of the table column index.
    */
   public static ColumnRef findColumn(XTable table, int col, ColumnSelection columns, boolean exact)
   {
      ColumnRef column = null;
      ColumnRef column_name = null;
      ColumnRef column_part = null;
      String name = format(XUtil.getHeader(table, col));
      String identifier = table.getColumnIdentifier(col);

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef ref = (ColumnRef) columns.getAttribute(i);

         // @by stephenwebster, it is possible to have two joined tables
         // having the same entity and attribute name, but be in different
         // case.  For bug1433840009090, make it case sensitive match.
         if(Objects.equals(getAttributeString(ref), identifier)) {
            column = ref;
            break;
         }
         else if(isCaptionIdentified(identifier, ref)) {
            column = ref;
         }
         else if(ref.getHeaderName().equalsIgnoreCase(name)) {
            column_name = ref;
         }
         else if(!exact && name.equalsIgnoreCase(ref.getAttribute())) {
            column_part = ref;
         }
         else if(!exact && name.endsWith("." + ref.getAttribute())) {
            column_part = ref;
         }
      }

      if(column != null) {
         return column;
      }
      else if(column_name != null) {
         return column_name;
      }
      else {
         return column_part;
      }
   }

   /**
    * Find the column of a table by data ref.
    *
    * @param table the specified table.
    * @param ref   the specified ref.
    */
   public static int findColumn(XTable table, DataRef ref) {
      return findColumn(table, ref, false);
   }

   /**
    * Find the column of a table by data ref.
    *
    * @param table the specified table.
    * @param ref   the specified ref.
    * @param exact <tt>true</tt> to match the data ref totally,
    * @return the column index if found, <tt>-1</tt> otherwise.
    */
   public static int findColumn(XTable table, DataRef ref, boolean exact) {
      return findColumn(table, ref, exact, true, 0);
   }

   /**
    * Find the column of a table by data ref.
    *
    * @param table          the specified table.
    * @param ref            the specified ref.
    * @param exact          <tt>true</tt> to match the data ref totally,
    * @param identified     true to apply identifier.
    * @param start          the column of the table to start search.
    * @return the column index if found, <tt>-1</tt> otherwise.
    */
   public static int findColumn(XTable table, DataRef ref, boolean exact,
                                boolean identified, int start)
   {
      if(table == null || ref == null) {
         return -1;
      }

      int col = -1;
      int col2 = -1;
      int col_name = -1;
      int col_part = -1;
      int col_part2 = -1;
      String ent = ref.getEntity();
      boolean fullName = ent != null && ent.length() > 0;
      String hname = ref instanceof ColumnRef ? ((ColumnRef) ref).getHeaderName() : ref.getName();
      final String attributeString = getAttributeString(ref);

      for(int i = start; i < table.getColCount(); i++) {
         String header = format(XUtil.getHeader(table, i));
         String id = table.getColumnIdentifier(i);

         if((identified || fullName) && attributeString.equals(id)) {
            col = i;
            break;
         }
         else if(identified && (attributeString.equalsIgnoreCase(id) ||
            attributeString.equalsIgnoreCase(header)))
         {
            col2 = i;
         }
         else if(hname.equalsIgnoreCase(header)) {
            col_name = i;
         }
         else if(identified && isCaptionIdentified(id, ref)) {
            col = i;
            break;
         }
         else if(!exact && header.equalsIgnoreCase(ref.getAttribute()) && col_part < 0) {
            col_part = i;
         }
         else if(!exact && identified && id != null &&
            id.endsWith("." + ref.getAttribute()) && col_part2 < 0)
         {
            col_part2 = i;
         }
      }

      if(col >= 0) {
         return col;
      }
      else if(col2 >= 0) {
         return col2;
      }
      else if(col_name >= 0) {
         return col_name;
      }
      else if(col_part >= 0) {
         return col_part;
      }
      else if(col_part2 >= 0) {
         return col_part2;
      }

      String attr = ref.getAttribute();

      if(attr != null && attr.startsWith("Column [") && attr.endsWith("]")) {
         return Util.findColumn(table, attr, start, true);
      }

      return -1;
   }

   /**
    * Find the column of a table by data ref.
    *
    * @param table          the specified table.
    * @param ref            the specified ref.
    * @param columnIndexMap     the map which can get column index by column identifier or header.
    * @return the column index if found, <tt>-1</tt> otherwise.
    */
   public static int findColumn(XTable table, DataRef ref, ColumnIndexMap columnIndexMap) {
      return findColumn(table, ref, false, true, columnIndexMap);
   }

   /**
    * Find the column of a table by data ref.
    *
    * @param table          the specified table.
    * @param ref            the specified ref.
    * @param exact          <tt>true</tt> to match the data ref totally,
    * @param identified     true to apply identifier.
    * @param columnIndexMap     the map which can get column index by column identifier or header.
    * @return the column index if found, <tt>-1</tt> otherwise.
    */
   public static int findColumn(XTable table, DataRef ref, boolean exact, boolean identified,
                                ColumnIndexMap columnIndexMap)
   {
      if(table == null || ref == null) {
         return -1;
      }

      String ent = ref.getEntity();
      boolean fullName = ent != null && ent.length() > 0;
      // if aggregate, search by full name. (53311)
      String hname = ref instanceof AggregateRef ? ref.toView()
         : (ref instanceof ColumnRef ? ((ColumnRef) ref).getHeaderName() : ref.getName());
      final String attributeString = getAttributeString(ref);
      int idx = -1;

      if(identified || fullName) {
         idx = columnIndexMap.getColIndexByIdentifier(attributeString);

         if(idx != -1) {
            return idx;
         }
      }

      // use col2
      if(identified) {
         idx = columnIndexMap.getColIndexByIdentifier(attributeString, true);

         if(idx == -1) {
            idx = columnIndexMap.getColIndexByFormatedHeader(attributeString);
         }

         if(idx == -1) {
            idx = columnIndexMap.getColIndexByFormatedHeader(attributeString, true);
         }

         if(idx != -1) {
            return idx;
         }
      }

      // use col_name
      idx = columnIndexMap.getColIndexByFormatedHeader(hname, true);

      if(idx != -1) {
         return idx;
      }

      if(identified) {
         String captionIdentifier = getCaptionIdentifier(ref);

         if(captionIdentifier != null) {
            idx = columnIndexMap.getColIndexByIdentifier(captionIdentifier);
         }

         if(idx != -1) {
            return idx;
         }
      }

      // use col_part
      if(!exact) {
         idx = columnIndexMap.getColIndexByFormatedHeader(ref.getAttribute(), true);

         if(idx != -1) {
            return idx;
         }
      }

      // col_part2
      if(!exact && identified) {
         Set<Map.Entry<Object, Integer>> entrySet = columnIndexMap.getIdentifierEntrySet();

         if(entrySet != null) {
            String attribute = ref.getAttribute();
            idx = entrySet.stream()
               .filter(entry -> (entry.getKey() + "").endsWith("." + attribute))
               .map(entry -> entry.getValue())
               .findFirst().orElse(-1);
         }
      }

      return idx;
   }

   /**
    * Get caption of the target data ref.
    *
    * @param ref the sepecified ref
    */
   private static String getCaptionIdentifier(DataRef ref) {
      if(!(ref instanceof ColumnRef)) {
         return null;
      }

      DataRef sub = ((ColumnRef) ref).getDataRef();

      if(!(sub instanceof AttributeRef)) {
         return null;
      }

      return ((AttributeRef) sub).getCaption();
   }

   /**
    * Check if a caption is the identifier
    *
    * @param id the specified column identifier
    * @param ref the sepecified ref
    */
   private static boolean isCaptionIdentified(String id, DataRef ref) {
      if(id == null) {
         return false;
      }

      return Tool.equals(id, getCaptionIdentifier(ref));
   }

   /**
    * Fix alias for columns to avoid duplicate alias.
    */
   public static void fixAlias(ColumnSelection columns) {
      Map<String, ArrayList<Integer>> map = new CaseInsensitiveMap<>();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);
         int index = column.isExpression() ? -1 : i;

         String base = column.getAlias() != null ? column.getAlias() : column.getAttribute();
         base = base.length() == 0 ? "a" : base;

         if(!map.containsKey(base)) {
            ArrayList<Integer> list = new ArrayList<>();
            list.add(index);
            map.put(base, list);
         }
         else {
            if(index == -1) {
               map.get(base).add(0, index);
            }
            else {
               map.get(base).add(i);
            }
         }
      }

      Set<Map.Entry<String, ArrayList<Integer>>> entrySet = map.entrySet();

      if(entrySet != null) {
         for(Map.Entry<String, ArrayList<Integer>> entry : entrySet) {
            ArrayList<Integer> list = entry.getValue();

            for(int i = 1; i < list.size(); i++) {
               if(list.get(i) != -1) {
                  ColumnRef column = (ColumnRef) columns.getAttribute(list.get(i));
                  String base = column.getAlias() != null ?
                     column.getAlias() : column.getAttribute();
                  String alias = base + "_" + i;

                  // make sure that there isn't a column with this name already
                  while(map.containsKey(alias)) {
                     alias += "_" + i;
                  }

                  column.setAlias(alias);
               }
            }
         }
      }
   }

   /**
    * Find an alias for a column.
    *
    * @param columns the specified column selection.
    * @param column  the specified column.
    * @return the default alias for the column, <tt>null</tt> not required.
    */
   public static String findAlias(ColumnSelection columns, ColumnRef column) {
      String base = column.getAttribute();
      return findAlias(columns, column, base);
   }

   /**
    * Find an alias for a column.
    *
    * @param columns the specified column selection.
    * @param column  the specified column.
    * @param base    the specified alias base.
    * @return the default alias for the column, <tt>null</tt> not required.
    */
   public static String findAlias(ColumnSelection columns, ColumnRef column, String base) {
      base = base == null ? column.getAttribute() : base;
      int spos = 0;

      base = spos == 0 ? base : base.substring(spos);
      base = base.length() == 0 ? "a" : base;

      String alias = base;
      int suffix = 1;

      while(findColumnConflictingWithAlias(columns, column, alias) != null) {
         alias = base + "_" + (suffix++);
      }

      return alias.equals(column.getAttribute()) ? null : alias;
   }

   /**
    * Check if an alias is valid.
    *
    * @param columns the specified column selection.
    * @param column the specified column ref.
    * @param alias the specified alias.
    * @return the conflicting column ref if it exists, otherwise <tt>null</tt>.
    */
   public static ColumnRef findColumnConflictingWithAlias(ColumnSelection columns, ColumnRef column,
                                                          String alias) {
      return findColumnConflictingWithAlias(columns, column, alias, false);
   }

   /**
    * Check if an alias is valid.
    *
    * @param columns the specified column selection.
    * @param column the specified column ref.
    * @param alias the specified alias.
    * @param strict <tt>true</tt> to check the alias strictly, <tt>false</tt>
    * otherwise.
    * @return the conflicting column ref if it exists, otherwise <tt>null</tt>.
    */
   public static ColumnRef findColumnConflictingWithAlias(ColumnSelection columns, ColumnRef column,
                                                          String alias, boolean strict) {
      int size = columns.getAttributeCount();

      for(int i = 0; i < size; i++) {
         ColumnRef tcolumn = (ColumnRef) columns.getAttribute(i);

         if(column == null || !column.equals(tcolumn)) {
            String talias = tcolumn.getAlias();
            String tattr = tcolumn.getAttribute();

            if(talias != null && alias.equalsIgnoreCase(talias)) {
               return tcolumn;
            }
            else if((strict || talias == null) && alias.equalsIgnoreCase(tattr)) {
               return tcolumn;
            }
         }
      }

      return null;
   }

   /**
    * Validate the aliases of the user variables.
    *
    * @param vars the specified user variables.
    */
   public static void validateAlias(UserVariable[] vars) {
      for(int i = 0; vars != null && i < vars.length; i++) {
         String name = vars[i].getName();

         if(name.startsWith(XUtil.DB_USER_PREFIX)) {
            name = name.substring(XUtil.DB_USER_PREFIX.length());
            String alias = name + " " + Catalog.getCatalog().getString("User");
            vars[i].setAlias(alias);
         }
         else if(name.startsWith(XUtil.DB_PASSWORD_PREFIX)) {
            name = name.substring(XUtil.DB_PASSWORD_PREFIX.length());
            String alias = name + " " +
               Catalog.getCatalog().getString("Password");
            vars[i].setAlias(alias);
         }
      }
   }

   /**
    * Get the string representation of an attribute.
    *
    * @param attr the specified attribute.
    * @return the string representation of the attribute.
    */
   public static String getAttributeString(DataRef attr) {
      String entity = attr.isExpression() ? null : attr.getEntity();
      String attribute = attr.getAttribute();
      return entity == null ? attribute : entity + "." + attribute;
   }

   /**
    * Get the column has all info of cube.
    * @param cols the columnselection.
    * @param ref the ref to find.
    * @return the new column in cols.
    */
   public static ColumnRef findCubeColumn(ColumnSelection cols, ColumnRef ref) {
      String attr = ref.getAttribute();

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) cols.getAttribute(i);
         String alias = column.getAlias();
         DataRef colRef = column.getDataRef();

         if(alias == null && colRef instanceof AttributeRef) {
            alias = ((AttributeRef) colRef).getAttribute();
         }

         if(Tool.equals(attr, alias)) {
            return column;
         }
      }

      return null;
   }

   /**
    * Get a typed value from its string representation. For example
    * getData("Integer", "15") returns an Integer object with value 15
    *
    * @param type String representation of the type.
    * @param val  String representation of the value.
    * @return typed value.
    */
   public static Object parse(String type, String val) throws Exception {
      if(type == null || val == null) {
         return val;
      }
      else if(type.equalsIgnoreCase(XSchema.NULL)) {
         return null;
      }
      else if(type.equalsIgnoreCase(XSchema.STRING)) {
         return val;
      }
      else if(type.equalsIgnoreCase(XSchema.BOOLEAN)) {
         try{
            return Double.valueOf(val) == 0 ? false : true;
         }
         catch(Exception e) {
            return Boolean.valueOf(val);
         }
      }
      else if(type.equalsIgnoreCase(XSchema.BYTE)) {
         return Byte.valueOf(val);
      }
      else if(type.equalsIgnoreCase(XSchema.CHAR)) {
         return val;
      }
      else if(type.equalsIgnoreCase(XSchema.CHARACTER)) {
         return Character.valueOf(val.charAt(0));
      }
      else if(type.equalsIgnoreCase(XSchema.DOUBLE)) {
         return Double.valueOf(val);
      }
      else if(type.equalsIgnoreCase(XSchema.FLOAT)) {
         return Float.valueOf(val);
      }
      else if(type.equalsIgnoreCase(XSchema.INTEGER)) {
         return Integer.valueOf(val);
      }
      else if(type.equalsIgnoreCase(XSchema.LONG)) {
         return Long.valueOf(val);
      }
      else if(type.equalsIgnoreCase(XSchema.SHORT)) {
         return Short.valueOf(val);
      }
      else if(type.equalsIgnoreCase(XSchema.DATE)) {
         Date date = getDateFormat().parse(val);
         return new java.sql.Date(date.getTime());
      }
      else if(type.equalsIgnoreCase(XSchema.TIME_INSTANT)) {
         Date date = getDateTimeFormat().parse(val);
         return new java.sql.Timestamp(date.getTime());
      }
      else if(type.equalsIgnoreCase(XSchema.TIME)) {
         Date date = getTimeFormat().parse(val);
         return new java.sql.Time(date.getTime());
      }
      else {
         throw new Exception("Unsupported type found: " + type);
      }
   }

   /**
    * Get a string representation of a value.
    *
    * @param val the object to get a string representation of.
    */
   public static String format(Object val) {
      return format(val, false);
   }

   /**
    * Get a string representation of a value.
    *
    * @param val the object to get a string representation of.
    */
   public static String format(Object val, boolean trim) {
      // NaN?
      if(val == null || (val instanceof Number &&
         Double.isNaN(((Number) val).doubleValue()))) {
         return "";
      }
      else if(val instanceof java.sql.Date) {
         return getDateFormat().format((Date) val);
      }
      else if(val instanceof java.sql.Time) {
         return getTimeFormat().format((Date) val);
      }
      else if(val instanceof java.sql.Timestamp) {
         return getDateTimeFormat().format((Date) val);
      }
      else if(val instanceof Date) {
         return getDateTimeFormat().format((Date) val);
      }
      else if(val instanceof Float || val instanceof Double ||
         val instanceof BigDecimal) {
         return getNumberFormat(trim).format((Number) val);
      }
      else {
         try {
            return String.valueOf(val);
         }
         catch(Exception ex) {
            LOG.debug("Failed to convert value to string: " + val, ex);
            return "";
         }
      }
   }

   /**
    * Get the date format.
    *
    * @return the date format.
    */
   public static SimpleDateFormat getDateFormat() {
      return Tool.getDateFormat();
   }

   /**
    * Get the date time format.
    *
    * @return the date time format.
    */
   public static SimpleDateFormat getDateTimeFormat() {
      return Tool.getDateTimeFormat();
   }

   /**
    * Get the time format.
    *
    * @return the time format.
    */
   public static SimpleDateFormat getTimeFormat() {
      return Tool.getTimeFormat();
   }

   /**
    * Get the number format.
    *
    * @return the number format.
    */
   public static DecimalFormat getNumberFormat(boolean trim) {
      String def = trim ? "#.####" : "0.00##"; // support 4 digits by default

      if(numFmt == null || numFmt.toPattern() == null ||
         numFmt.toPattern().equals("")) {
         String prop = SreeEnv.getProperty("format.number", def);

         if(prop == null || prop.equals("")) {
            prop = def;
         }

         try {
            numFmt = new DecimalFormat(prop);
            numFmt.format(Float.valueOf(1F));
         }
         catch(Exception ex) {
            LOG.warn("Invalid decimal format pattern (format.number): " + prop +
               ", using default", ex);
            numFmt = new DecimalFormat(def);
         }
      }

      return numFmt;
   }

   /**
    * Check if two data types are mergeable.
    *
    * @param dtype1 the specified data type a.
    * @param dtype2 the specified data type b.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   public static boolean isMergeable(String dtype1, String dtype2) {
      if(dtype1.equals(dtype2)) {
         return true;
      }
      else if(isStringType(dtype1) && isStringType(dtype2)) {
         return true;
      }
      else if(isNumberType(dtype1) && isNumberType(dtype2)) {
         return true;
      }
      else if(isDateType(dtype1) && isDateType(dtype2)) {
         return true;
      }

      return false;
   }

   /**
    * Check if is a string type.
    *
    * @param dtype the specified data type.
    * @return <tt>true</tt> if is, <tt>false</tt> otherwise.
    */
   public static boolean isStringType(String dtype) {
      return dtype.equals(XSchema.STRING) || dtype.equals(XSchema.CHAR);
   }

   /**
    * Check if is a number data type.
    *
    * @param dtype the specified data type.
    * @return <tt>true</tt> if is, <tt>false</tt> otherwise.
    */
   public static boolean isNumberType(String dtype) {
      return dtype.equals(XSchema.FLOAT) || dtype.equals(XSchema.DOUBLE) ||
         dtype.equals(XSchema.BYTE) || dtype.equals(XSchema.SHORT) ||
         dtype.equals(XSchema.INTEGER) || dtype.equals(XSchema.LONG);
   }

   /**
    * Check if is a date data type.
    *
    * @param dtype the specified data type.
    * @return <tt>true</tt> if is, <tt>false</tt> otherwise.
    */
   public static boolean isDateType(String dtype) {
      return dtype.equals(XSchema.DATE) || dtype.equals(XSchema.TIME_INSTANT);
   }

   /**
    * Check if the specified entry is Invalid.
    *
    * @param engine the specified engine.
    * @param entry  the specified entry.
    * @return <tt>true</tt> if duplicated, <tt>false</tt> otherwise.
    */
   public static boolean isInvalidEntry(AssetRepository engine,
                                        AssetEntry entry)
      throws Exception {
      ArrayList types = new ArrayList();

      switch(entry.getType()) {
      case VIEWSHEET:
      case VIEWSHEET_SNAPSHOT:
         types.add("" + AssetEntry.Type.REPOSITORY_FOLDER.id());
         break;
      }

      for(int i = 0; i < types.size(); i++) {
         int ttype = Integer.parseInt((String) types.get(i));
         AssetEntry tentry = new AssetEntry(entry.getScope(), ttype,
            entry.getPath(), entry.getUser());
         tentry.copyProperties(entry);

         if(engine.containsEntry(tentry)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Check if the specified entry is duplicated.
    *
    * @param engine the specified engine.
    * @param entry  the specified entry.
    * @return <tt>true</tt> if duplicated, <tt>false</tt> otherwise.
    */
   public static boolean isDuplicatedEntry(AssetRepository engine, AssetEntry entry)
         throws Exception
   {
      Set<AssetEntry.Type> types = new HashSet<>();

      switch(entry.getType()) {
      case FOLDER:
      case WORKSHEET:
         types.add(AssetEntry.Type.WORKSHEET);
         types.add(AssetEntry.Type.FOLDER);
         break;
      case REPOSITORY_FOLDER:
      case VIEWSHEET:
      case VIEWSHEET_SNAPSHOT:
         types.add(AssetEntry.Type.VIEWSHEET);
         types.add(AssetEntry.Type.VIEWSHEET_SNAPSHOT);
         types.add(AssetEntry.Type.REPOSITORY_FOLDER);
         break;
      }

      for(AssetEntry.Type ttype : types) {
         AssetEntry tentry = new AssetEntry(
            entry.getScope(), ttype, entry.getPath(), entry.getUser());
         tentry.copyProperties(entry);

         if(engine.containsEntry(tentry)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if two types are compatible for grouping.
    *
    * @param type1 the specified data type a.
    * @param type2 the specified data type b.
    * @return <tt>true</tt> if is, <tt>false</tt> otherwise.
    */
   public static boolean isCompatible(String type1, String type2) {
      return getBaseType(type1).equals(getBaseType(type2));
   }

   /**
    * Get the base type for type compatibility test.
    *
    * @param type the specified data type.
    * @return the base data type.
    */
   private static String getBaseType(String type) {
      if(XSchema.STRING.equals(type)) {
         return XSchema.STRING;
      }
      else if(XSchema.CHAR.equals(type)) {
         return XSchema.STRING;
      }
      else if(XSchema.CHARACTER.equals(type)) {
         return XSchema.STRING;
      }
      else if(XSchema.BOOLEAN.equals(type)) {
         return XSchema.BOOLEAN;
      }
      else if(XSchema.FLOAT.equals(type)) {
         return XSchema.DOUBLE;
      }
      else if(XSchema.DOUBLE.equals(type)) {
         return XSchema.DOUBLE;
      }
      else if(XSchema.BYTE.equals(type)) {
         return XSchema.DOUBLE;
      }
      else if(XSchema.SHORT.equals(type)) {
         return XSchema.DOUBLE;
      }
      else if(XSchema.INTEGER.equals(type)) {
         return XSchema.DOUBLE;
      }
      else if(XSchema.LONG.equals(type)) {
         return XSchema.DOUBLE;
      }
      else if(XSchema.TIME_INSTANT.equals(type)) {
         return XSchema.DATE;
      }
      else if(XSchema.DATE.equals(type)) {
         return XSchema.DATE;
      }
      else if(XSchema.TIME.equals(type)) {
         return XSchema.TIME;
      }

      return type;
   }

   /**
    * Normalize a table name.
    *
    * @param name the specified table name.
    * @return the normalized table name.
    */
   public static String normalizeTable(String name) {
      if(name == null) {
         return name;
      }

      int dot = name.lastIndexOf('.');

      if(dot >= 0 && dot < name.length() - 1) {
         name = name.substring(dot + 1);
      }

      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < name.length(); i++) {
         char c = name.charAt(i);

         if(Character.isLetterOrDigit(c) || c == ' ') {
            sb.append(c);
         }
         else if(sb.length() > 0) {
            sb.append('_');
         }
      }

      if(sb.length() == 0) {
         sb.append('T');
      }

      return sb.toString();
   }

   /**
    * Get the next name.
    *
    * @param sheet  the specified abstract sheet.
    */
   public static String getNextName(AbstractSheet sheet, int type) {
      return getNextName(sheet, type, null);
   }

   /**
    * Get the next name.
    *
    * @param sheet     the specified abstract sheet.
    * @param preferred the preferred name.
    */
   public static String getNextName(AbstractSheet sheet, int type, String preferred) {
      String prefix;

      if(type == AbstractSheet.CONDITION_ASSET) {
         prefix = "Condition";
      }
      else if(type == AbstractSheet.NAMED_GROUP_ASSET) {
         prefix = "Grouping";
      }
      else if(type == AbstractSheet.VARIABLE_ASSET) {
         prefix = "Variable";
      }
      else if(type == AbstractSheet.TABLE_ASSET) {
         prefix = "Query";
      }
      else if(type == AbstractSheet.DATE_RANGE_ASSET) {
         prefix = "DateRange";
      }
      else if(type == AbstractSheet.TABLE_VIEW_ASSET) {
         prefix = "TableView";
      }
      else if(type == AbstractSheet.CHART_ASSET) {
         prefix = "Chart";
      }
      else if(type == AbstractSheet.CROSSTAB_ASSET) {
         prefix = "Crosstab";
      }
      else if(type == AbstractSheet.CUBE_ASSET) {
         prefix = "Cube";
      }
      else if(type == AbstractSheet.SLIDER_ASSET) {
         prefix = "Slider";
      }
      else if(type == AbstractSheet.SPINNER_ASSET) {
         prefix = "Spinner";
      }
      else if(type == AbstractSheet.CHECKBOX_ASSET) {
         prefix = "CheckBox";
      }
      else if(type == AbstractSheet.RADIOBUTTON_ASSET) {
         prefix = "RadioButton";
      }
      else if(type == AbstractSheet.COMBOBOX_ASSET) {
         prefix = "ComboBox";
      }
      else if(type == AbstractSheet.TEXT_ASSET) {
         prefix = "Text";
      }
      else if(type == AbstractSheet.IMAGE_ASSET) {
         prefix = "Image";
      }
      else if(type == AbstractSheet.GAUGE_ASSET) {
         prefix = "Gauge";
      }
      else if(type == AbstractSheet.THERMOMETER_ASSET) {
         prefix = "Thermometer";
      }
      else if(type == AbstractSheet.SLIDING_SCALE_ASSET) {
         prefix = "SlidingScale";
      }
      else if(type == AbstractSheet.CYLINDER_ASSET) {
         prefix = "Cylinder";
      }
      else if(type == AbstractSheet.SELECTION_LIST_ASSET) {
         prefix = "SelectionList";
      }
      else if(type == AbstractSheet.SELECTION_TREE_ASSET) {
         prefix = "SelectionTree";
      }
      else if(type == AbstractSheet.TIME_SLIDER_ASSET) {
         prefix = "RangeSlider";
      }
      else if(type == AbstractSheet.CALENDAR_ASSET) {
         prefix = "Calendar";
      }
      else if(type == AbstractSheet.DRILL_BOX_ASSET) {
         prefix = "DrillBox";
      }
      else if(type == AbstractSheet.TAB_ASSET) {
         prefix = "Tab";
      }
      else if(type == AbstractSheet.VIEWSHEET_ASSET) {
         prefix = "Viewsheet";
      }
      else if(type == AbstractSheet.EMBEDDEDTABLE_VIEW_ASSET) {
         prefix = "EmbeddedTable";
      }
      else if(type == AbstractSheet.LINE_ASSET) {
         prefix = "Line";
      }
      else if(type == AbstractSheet.RECTANGLE_ASSET) {
         prefix = "Rectangle";
      }
      else if(type == AbstractSheet.OVAL_ASSET) {
         prefix = "Oval";
      }
      else if(type == AbstractSheet.GROUPCONTAINER_ASSET) {
         prefix = "GroupContainer";
      }
      else if(type == AbstractSheet.CURRENTSELECTION_ASSET) {
         prefix = "CurrentSelection";
      }
      else if(type == AbstractSheet.TEXTINPUT_ASSET) {
         prefix = "TextInput";
      }
      else if(type == AbstractSheet.SUBMIT_ASSET) {
         prefix = "Submit";
      }
      else if(type == AbstractSheet.FORMULA_TABLE_ASSET) {
         prefix = "FreehandTable";
      }
      else if(type == AbstractSheet.ANNOTATION_ASSET) {
         prefix = "Annotation";
      }
      else if(type == AbstractSheet.ANNOTATION_LINE_ASSET) {
         prefix = "AnnotationLine";
      }
      else if(type == AbstractSheet.ANNOTATION_RECTANGLE_ASSET) {
         prefix = "AnnotationRectangle";
      }
      else if(type == AbstractSheet.UPLOAD_ASSET) {
         prefix = "Upload";
      }
      else {
         throw new RuntimeException("Unsupported type found: " + type);
      }

      return getNextName(sheet, prefix, preferred);
   }

   /**
    * Get the next name.
    *
    * @param sheet  the specified abstract sheet.
    * @param prefix the specified prefix.
    */
   public static String getNextName(AbstractSheet sheet, String prefix) {
      return getNextName(sheet, prefix, null);
   }

   /**
    * Get the next name.
    *
    * @param sheet     the specified abstract sheet.
    * @param prefix    the specified prefix.
    * @param preferred the preferred name.
    */
   public static String getNextName(AbstractSheet sheet, String prefix, String preferred) {
      if(preferred != null && sheet.getAssembly(preferred) == null) {
         return preferred;
      }

      String name;
      // @by larryl, we start the name as Chart1, Chart2, to avoid using
      // Chart as an object name (Chart is the name of the js object holding
      // all graph constants)
      int i = 1;

      if(sheet instanceof Worksheet) {
         Set<String> uppercaseAssemblyNames = Arrays.stream(sheet.getAssemblies()).map(
            (assembly) -> assembly.getName().toUpperCase()).collect(
            toSet());

         do {
            name = prefix + i;
            i++;
         }
         while(uppercaseAssemblyNames.contains(name.toUpperCase()));
      }
      else {
         do {
            name = prefix + i;
            i++;
         }
         while(sheet.getAssembly(name) != null);
      }

      return name;
   }

   /**
    * Get a list of asset entries the matches the asset type and optionally
    * the worksheet type.
    *
    * @param worksheetType -1 if ignore worksheet type.
    */
   public static AssetEntry[] getEntries(AssetRepository engine,
                                         AssetEntry entry, Principal user,
                                         AssetEntry.Type assetType,
                                         int worksheetType, boolean recursive)
      throws Exception {
      List list = new ArrayList();

      getEntries0(engine, entry, user, assetType, worksheetType, recursive,
         list);

      return (AssetEntry[]) list.toArray(new AssetEntry[list.size()]);
   }

   /**
    * Get entries Recursively.
    */
   private static void getEntries0(AssetRepository engine, AssetEntry entry,
                                   Principal user, AssetEntry.Type assetType,
                                   int worksheetType, boolean recursive,
                                   List list)
      throws Exception {
      if(entry == null || !entry.isFolder() || !entry.isValid() ||
         !engine.supportsScope(entry.getScope())) {
         return;
      }

      AssetEntry[] entries = engine.getEntries(
         entry, user, ResourceAction.READ,
         new AssetEntry.Selector(assetType, AssetEntry.Type.FOLDER));

      for(int i = 0; i < entries.length; i++) {
         String val = entries[i].getProperty(AssetEntry.WORKSHEET_TYPE);

         if(assetType == AssetEntry.Type.WORKSHEET && val == null &&
            !entries[i].isFolder()) {
            continue;
         }

         if(worksheetType >= 0 && val != null) {
            int type = Integer.parseInt(val);

            if(type != worksheetType) {
               continue;
            }
         }

         if(!entries[i].isFolder()) {
            list.add(entries[i]);
         }
         else if(recursive && entries[i].isFolder()) {
            getEntries0(engine, entries[i], user, assetType, worksheetType,
               recursive, list);
         }
      }
   }

   /**
    * Check if a table should be shown in a hierarchical mode.
    *
    * @param table the specified table assembly.
    * @return <tt>true</tt> to show in a hierarchical mode, <tt>false</tt> to
    *         show metadata.
    */
   public static boolean isHierarchical(TableAssembly table) {
      if(table.isRuntime() || table.isLiveData() ||
         !(table instanceof ComposedTableAssembly)) {
         return false;
      }

      ComposedTableAssembly ctable = (ComposedTableAssembly) table;

      if(!ctable.isComposed()) {
         return false;
      }

      return ctable.isHierarchical();
   }

   /**
    * Get xtable values.
    *
    * @param table the specified xtable.
    * @param index the specified index.
    * @return the values of the table at the index.
    */
   public static Object[] getXTableValues(XTable table, int index) {
      List list = new ArrayList();
      table.moreRows(Integer.MAX_VALUE);

      for(int i = table.getHeaderRowCount(); i < table.getRowCount(); i++) {
         list.add(table.getObject(i, index));
      }

      Object[] arr = new Object[list.size()];
      list.toArray(arr);

      return arr;
   }

   /**
    * Copy outer assemblies.
    *
    * @param engine the specified asset repository.
    * @param fentry the worksheet entry to copy from.
    * @param user   the specified user.
    * @param to     the specified target worksheet to copy to.
    * @return the newly created assemblies.
    */
   public static WSAssembly[] copyOuterAssemblies(AssetRepository engine,
                                                  AssetEntry fentry,
                                                  Principal user, Worksheet to,
                                                  Point pos)
      throws Exception
   {
      Worksheet from = (Worksheet) engine.getSheet(fentry, user, false, AssetContent.ALL);
      Catalog catalog = Catalog.getCatalog();

      if(from == null) {
         MessageException ex = new MessageException(catalog.getString(
            "common.worksheetNotExist", fentry));
         throw ex;
      }

      from = (Worksheet) from.clone();
      WSAssembly assembly = from.getPrimaryAssembly();
      fixPrimaryOuterAssembly(assembly);

      if(assembly == null) {
         MessageException ex = new MessageException(catalog.getString(
            "common.undefinedPrimaryAssembly2", fentry));
         throw ex;
      }

      String prefix = createPrefix(fentry);

      // remove existing ones
      Assembly[] assemblies = to.getAssemblies();

      for(int i = 0; i < assemblies.length; i++) {
         String name = assemblies[i].getName();

         if(name.startsWith(prefix)) {
            to.removeAssembly(name);
         }
      }

      // rename the to be copied assemblies to avoid conflict
      assemblies = getDependedAssemblies(assembly.getSheet(), assembly, true);

      // sort assemblies according to dependencies
      Arrays.sort(assemblies, new DependencyComparator(assembly.getSheet(), true));

      // key = new name, value = old name
      Map<String, String> nameChangeMap = new HashMap<>();

      for(int i = 0; i < assemblies.length; i++) {
         String oname = assemblies[i].getName();
         String nname = prefix + i;
         from.renameAssembly(oname, nname, false);
         nameChangeMap.put(nname, oname);
      }

      int counter = 0;
      List created = new ArrayList();
      pos = pos == null ? new Point(AssetUtil.defw, AssetUtil.defh) : pos;

      // copy as outer assemblies
      for(int i = 0; i < assemblies.length; i++) {
         WSAssembly assembly2 = (WSAssembly) assemblies[i].clone();
         assembly2.setOuter(true);
         assembly2.setPixelOffset(new Point(pos.x, pos.y + ((counter++) * AssetUtil.defh)));

         if(assembly2 instanceof TableAssembly) {
            ((TableAssembly) assembly2).setLiveData(false);
         }

         setOuterTableNames(assembly2, nameChangeMap);
         to.addAssembly(assembly2);
         created.add(assembly2);
      }

      WSAssembly[] arr = new WSAssembly[created.size()];
      created.toArray(arr);
      return arr;
   }

   private static void setOuterTableNames(WSAssembly assembly, Map<String, String> nameChangeMap) {
      if(!(assembly instanceof TabularTableAssembly)) {
         return;
      }

      TabularTableAssembly tabularAssembly = (TabularTableAssembly) assembly;
      TabularTableAssemblyInfo info = (TabularTableAssemblyInfo) tabularAssembly.getTableInfo();

      if(info.getQuery() != null && info.getQuery().dependsOnMVSession()) {
         TabularQuery query = info.getQuery();
         query.setProperty(TabularQuery.IS_OUTER_TABLE, true);

         for(String newName : nameChangeMap.keySet()) {
            query.setProperty(TabularQuery.OUTER_TABLE_NAME_PROPERTY_PREFIX + newName,
                              nameChangeMap.get(newName));
         }
      }
   }

   /**
    * Get a set of outer assembly names given a worksheet entry. Same names as created in
    * AssetUtil.copyOuterAssemblies
    */
   public static Set<String> getOuterAssemblyNames(AssetRepository engine, AssetEntry fentry,
                                                    Principal user) throws Exception
   {
      Worksheet ws = (Worksheet) engine.getSheet(fentry, user, false, AssetContent.ALL);
      WSAssembly primaryAssembly = ws.getPrimaryAssembly();

      if(primaryAssembly == null) {
         MessageException ex = new MessageException(Catalog.getCatalog().getString(
            "common.undefinedPrimaryAssembly2", fentry));
         throw ex;
      }

      String prefix = createPrefix(fentry);
      Assembly[] assemblies = getDependedAssemblies(primaryAssembly.getSheet(), primaryAssembly,
                                                    true);
      Set<String> assemblyNames = new HashSet<>();

      for(int i = 0; i < assemblies.length; i++) {
         assemblyNames.add(prefix + i);
      }

      return assemblyNames;
   }

   // Bug 32520. it seems the primary table's column selection maybe corrected at some point.
   // The renamed table name (OUTER...) is written back to the base worksheet. This should
   // never happen since the OUTER should only be user when it has been copied to the containing
   // ws. This fix the incorrect name. Should consider removing this in the future (12.3)
   private static void fixPrimaryOuterAssembly(Assembly assembly) {
      if(!(assembly instanceof ComposedTableAssembly)) {
         return;
      }

      ComposedTableAssembly table = (ComposedTableAssembly) assembly;
      String[] children = table.getTableNames();

      if(children.length > 0) {
         ColumnSelection pcols = table.getColumnSelection();
         String child0 = children[0];

         for(int i = 0; i < pcols.getAttributeCount(); i++) {
            ColumnRef ref = (ColumnRef) pcols.getAttribute(i);
            String entity = ref.getEntity();

            if(entity != null && entity.startsWith("OUTER")) {
               ref.setDataRef(new AttributeRef(child0, ref.getAttribute()));
            }
         }
      }
   }

   /**
    * Create the prefix of an asset entry.
    *
    * @param entry the specified asset entry.
    * @return the created prefix.
    */
   public static String createPrefix(AssetEntry entry) {
      String name = entry.getPath();
      IdentityID user = entry.getUser();

      if(user != null) {
         name = "USR_" + user + "/" + name;
      }

      if(name.matches("[a-zA-Z_0-9/\\s]+")) {
         name = OUTER_PREFIX + "_" + name + "_";
         return Tool.replaceAll(name, "/", "__");
      }

      return OUTER_PREFIX + entry.hashCode() + "_";
   }

   /**
    * Fix the outer table assembly column name.
    */
   public static String fixOuterName(String name) {
      // fix bug1366144697117
      String outerReg = OUTER_PREFIX + "[-]?[\\d]+[_][\\S\\s]*";

      if(name.matches(outerReg)) {
         int dot = name.indexOf(".");
         return dot >= 0 ? name.substring(dot + 1) : name;
      }

      return name;
   }

   /**
    * Get all the assemblies depended on recursively.
    *
    * @param sheet    the specified sheet container.
    * @param assembly the specified assembly.
    * @param included <tt>true</tt> to include itself, <tt>false</tt> otherwise.
    * @return all the assemblies depended on.
    */
   public static Assembly[] getDependedAssemblies(AbstractSheet sheet,
                                                  Assembly assembly,
                                                  boolean included)
   {
      return getDependedAssemblies(sheet, assembly, included, true, false);
   }

   /**
    * Get all the assemblies depended on recursively.
    *
    * @param sheet    the specified sheet container.
    * @param assembly the specified assembly.
    * @param view     <tt>true</tt> to include view, <tt>false</tt> otherwise.
    * @param included <tt>true</tt> to include itself, <tt>false</tt> otherwise.
    * @param out      <tt>out</tt> to include out, <tt>false</tt> otherwise.
    * @return all the assemblies depended on.
    */
   public static Assembly[] getDependedAssemblies(
      AbstractSheet sheet, Assembly assembly, boolean included,
      boolean view, boolean out)
   {
      List<Assembly> assemblies = new ArrayList<>();
      getDependedAssemblies0(sheet, assembly, assemblies, included, view, out);
      Assembly[] arr = new Assembly[assemblies.size()];
      assemblies.toArray(arr);

      return arr;
   }

   /**
    * Get all the assemblies depended on.
    *
    * @param sheet      the specified sheet container.
    * @param assembly   the specified assembly.
    * @param assemblies the assembly container.
    * @param included   <tt>true</tt> to include itself, <tt>false</tt> otherwise.
    * @return all the assemblies depended on.
    */
   private static void getDependedAssemblies0(
      AbstractSheet sheet, Assembly assembly, List<Assembly> assemblies,
      boolean included, boolean view, boolean out)
   {
      if(assemblies.contains(assembly)) {
         return;
      }

      if(included) {
         assemblies.add(assembly);
      }

      AssemblyRef[] refs = sheet.getDependeds(assembly.getAssemblyEntry(), view, out);

      for(int i = 0; i < refs.length; i++) {
         AssemblyRef ref = refs[i];

         if(ref.getType() != AssemblyRef.INPUT_DATA &&
            ref.getType() != AssemblyRef.OUTPUT_DATA)
         {
            continue;
         }

         AssemblyEntry entry = ref.getEntry();
         Assembly assembly2 = sheet.getAssembly(entry);

         // shouldn't happen unless internal states get out of sync
         if(assembly2 == null) {
            continue;
         }

         getDependedAssemblies0(sheet, assembly2, assemblies, true, view, out);
      }
   }

   /**
    * Check if a variable is contained in a list.
    *
    * @param list the specified list.
    * @param var  the specified variable.
    * @return <tt>true</tt> if already contained, <tt>false</tt> otherwise.
    */
   public static boolean containsVariable(List list, UserVariable var) {
      for(int i = 0; i < list.size(); i++) {
         UserVariable var2 = (UserVariable) list.get(i);

         if(Tool.equals(var2.getName(), var.getName())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get the column attached assembly.
    *
    * @param table  the specified table assembly.
    * @param column the specified column.
    * @return the column attached assembly, <tt>null</tt> not found.
    */
   public static AttachedAssembly getColumnAttachedAssembly(TableAssembly table,
                                                            ColumnRef column) {
      ColumnSelection cols = table.getColumnSelection();

      if(!cols.containsAttribute(column)) {
         return null;
      }

      if(column.isExpression()) {
         return null;
      }

      if(!(table instanceof ComposedTableAssembly)) {
         if(table instanceof BoundTableAssembly) {
            AttachedAssemblyImpl impl = new AttachedAssemblyImpl();
            SourceInfo source = ((BoundTableAssembly) table).getSourceInfo();
            impl.setAttachedSource(source);
            impl.setAttachedType(AttachedAssembly.COLUMN_ATTACHED);
            impl.setAttachedAttribute(column);
            return impl;
         }
         else {
            return null;
         }
      }

      String entity = column.getEntity();
      String attribute = column.getAttribute();

      if(entity == null) {
         return null;
      }

      Worksheet ws = table.getWorksheet();
      Assembly bassembly = ws.getAssembly(entity);

      if(!(bassembly instanceof TableAssembly)) {
         return null;
      }

      TableAssembly btable = (TableAssembly) bassembly;
      ColumnSelection bcols = btable.getColumnSelection(true);
      ColumnRef bcolumn = null;

      for(int i = 0; i < bcols.getAttributeCount(); i++) {
         ColumnRef bcol = (ColumnRef) bcols.getAttribute(i);
         String balias = bcol.getAlias();
         String battr = bcol.getAttribute();

         if(attribute.equals(bcol.getAlias()) ||
            (balias == null && attribute.equals(battr))) {
            bcolumn = bcol;
            break;
         }
      }

      if(bcolumn == null) {
         return null;
      }

      return getColumnAttachedAssembly(btable, bcolumn);
   }

   /**
    * Get XCube by data source name and cube name.
    *
    * @param prefix the specified datasource name.
    * @param source the specified cube name.
    * @return XCube if any.
    */
   public static XCube getCube(String prefix, String source) {
      if(source == null) {
         return null;
      }

      if(source.startsWith(Assembly.CUBE_VS)) {
         source = source.substring(Assembly.CUBE_VS.length());
         int idx = source.lastIndexOf("/");

         if(idx >= 0) {
            prefix = source.substring(0, idx);
            source = source.substring(idx + 1);
         }
      }

      try {
         XRepository repository = XFactory.getRepository();
         XDomain domain = repository.getDomain(prefix);

         if(domain == null) {
            return null;
         }

         XCube cube = domain.getCube(source);

         return cube;
      }
      catch(Exception ex) {
      }

      return null;
   }

   /**
    * Get binding cube type of a data assembly.
    *
    * @param prefix the specified datasource name.
    * @param source the specified cube name.
    * @return binding cube type.
    */
   public static String getCubeType(String prefix, String source) {
      XCube cube = getCube(prefix, source);

      return cube == null ? null : cube.getType();
   }

   /**
    * Split a delimited string into an array of Strings.
    *
    * @param str   the original String which is to be split.
    * @param delim the delimiter to be used in splitting the string.
    */
   public static String[] split(String str, String delim) {
      if(str == null || delim == null || delim.length() == 0) {
         return null;
      }

      int len = delim.length();
      List list = new ArrayList();
      int lindex = -len;
      int index;

      while((index = str.indexOf(delim, lindex + len)) >= 0) {
         String sub = str.substring(lindex + len, index);

         if(sub.length() > 0) {
            list.add(sub);
         }

         lindex = index;
      }

      String sub = str.substring(lindex + len);

      if(sub.length() > 0) {
         list.add(sub);
      }

      String[] arr = new String[list.size()];
      list.toArray(arr);

      return arr;
   }

   /**
    * Get the sql helper if any.
    */
   public static SQLHelper getSQLHelper(TableAssembly table) {
      if(table instanceof BoundTableAssembly) {
         BoundTableAssembly btable = (BoundTableAssembly) table;
         return btable.getSQLHelper();
      }
      else if(table instanceof ComposedTableAssembly) {
         TableAssembly[] tables =
            ((ComposedTableAssembly) table).getTableAssemblies(false);

         for(int i = 0; tables != null && i < tables.length; i++) {
            SQLHelper helper = getSQLHelper(tables[i]);

            if(helper != null) {
               return helper;
            }
         }
      }

      return null;
   }

   /**
    * Set the asset repository.
    *
    * @param design <tt>true</tt> if is design time, <tt>false</tt> runtime.
    * @param rep    the specified asset repository.
    */
   public static void setAssetRepository(boolean design, AssetRepository rep) {
      String key = design ? DESIGN_REPOSITORY_KEY : REPOSITORY_KEY;
      ConfigurationContext.getContext().put(key, new WeakReference<>(rep));
   }

   /**
    * Get the asset repository.
    *
    * @param design <tt>true</tt> if is design time, <tt>false</tt> runtime.
    * @return the asset repository.
    */
   public static AssetRepository getAssetRepository(boolean design) {
      String key = design ? DESIGN_REPOSITORY_KEY : REPOSITORY_KEY;
      WeakReference<AssetRepository> ref = ConfigurationContext.getContext().get(key);
      AssetRepository rep = ref == null ? null : ref.get();

      // no runtime asset repository? use design time asset repository instead
      if(rep == null && !design) {
         ref = ConfigurationContext.getContext().get(DESIGN_REPOSITORY_KEY);
         rep = ref == null ? null : ref.get();
      }

      if(rep == null) {
         rep = (AssetRepository) SingletonManager.getInstance(AnalyticRepository.class);
      }

      return rep;
   }

   /**
    * Print table assembly.
    */
   public static void printTable(TableAssembly table) {
      if(table == null) {
         return;
      }

      StringBuilder sb = new StringBuilder();
      table.print(0, sb);
      System.err.println(sb);
   }

   /**
    * Remove entity from a fully qualified attribute name (entity:attr).
    *
    * @param entity null to remove any string before a colon.
    */
   public static String trimEntity(String attr, String entity) {
      if(attr == null) {
         return attr;
      }

      if(entity == null) {
         int idx = attr.indexOf(':');

         if(idx >= 0) {
            String text = attr.substring(idx + 1);
            int len = text.length();
            boolean number = len > 0;

            if(number) {
               for(int i = 0; i < len; i++) {
                  char c = text.charAt(i);

                  if(!Character.isDigit(c)) {
                     number = false;
                     break;
                  }
               }
            }

            if(!number) {
               return attr;
            }
         }
      }
      else if(attr.startsWith(entity + ":")) {
         return attr.substring(entity.length() + 1);
      }

      return attr;
   }

   /**
    * Localize the specified asset entry.
    */
   public static String localizeAssetEntry(String path, Principal principal,
                                           AssetEntry entry,
                                           boolean isUserScope) {
      if(isUserScope) {
         path = Tool.MY_DASHBOARD + "/" + path;
      }

      String newPath = localize(path, principal, entry);
      return isUserScope ?
         newPath.substring(newPath.indexOf('/') + 1) : newPath;
   }

   /**
    * Localize a path.
    *
    * @param path      the path to be localized.
    * @param principal the user principal.
    * @param entry     the asset entry to be localized.
    */
   public static String localize(String path, Principal principal,
                                 AssetEntry entry) {
      Catalog catalog = Catalog.getCatalog(principal);

      if("/".equals(path) || "".equals(path)) {
         return catalog.getString(path);
      }

      Catalog userCatalog = Catalog.getCatalog(principal, Catalog.REPORT);
      String[] values = Tool.split(path, '/');
      StringBuilder result = new StringBuilder();
      String curPath = null;

      for(int i = 0; i < values.length; i++) {
         String folder = values[i];
         curPath = curPath == null ? folder : curPath + "/" + folder;
         String alias = null;
         boolean isMyReport = Tool.MY_DASHBOARD.equals(curPath);

         if(entry != null && i == values.length - 1) {
            alias = entry.getAlias();
            folder = alias != null && alias.length() > 0 ? alias : folder;
         }

         if(isMyReport) {
            result.append(catalog.getString(folder));
         }
         else {
            result.append(userCatalog.getString(folder));
         }

         if(i < values.length - 1) {
            result.append("/");
         }
      }

      return result.toString();
   }

   /**
    * Get original data type.
    */
   public static String getOriginalType(DataRef ref) {
      DataRef ref0 = ref;

      while(ref0 instanceof DataRefWrapper) {
         if(ref0 instanceof ColumnRef) {
            ColumnRef col = (ColumnRef) ref0;
            String dtype = col.getOriginalType();

            if(dtype != null && dtype.length() > 0) {
               return dtype;
            }
         }

         if(ref0 instanceof DateRangeRef) {
            break;
         }

         // @by davyc, AttributeRef always as the inner most data ref, but
         // it does not implement the data type, so here to prevent get
         // data type from AttributeRef
         if(((DataRefWrapper) ref0).getDataRef() instanceof AttributeRef) {
            break;
         }

         DataRef ref2 = ((DataRefWrapper) ref0).getDataRef();

         if(ref2 == null) {
            break;
         }
         else {
            ref0 = ref2;
         }
      }

      if(ref0 instanceof DateRangeRef) {
         String otype = ((DateRangeRef) ref0).getOriginalType();

         // STRING is the default type (same as null), it doesn't make sense
         // don't use it for date
         if(!otype.equals(XSchema.STRING)) {
            return otype;
         }
      }

      return ref0.getDataType();
   }

   /**
    * Get default formula.
    */
   public static AggregateFormula getDefaultFormula(DataRef ref) {
      int refType = ref.getRefType();

      if(refType == DataRef.AGG_CALC || refType == DataRef.AGG_EXPR) {
         return AggregateFormula.NONE;
      }
      else if(refType == DataRef.MEASURE) {
         AggregateFormula formula = AggregateFormula.getFormula(ref.getDefaultFormula());

         if(formula != null) {
            return formula;
         }
      }

      if(refType == DataRef.DIMENSION) {
         return AggregateFormula.COUNT_ALL;
      }
      else if(ref instanceof XAggregateRef) {
         XAggregateRef agg = (XAggregateRef) ref;
         String type = agg.getOriginalDataType() != null ? agg.getOriginalDataType() :
            ref.getDataType();
         return AggregateFormula.getDefaultFormula(type);
      }
      else {
         return AggregateFormula.getDefaultFormula(ref.getDataType());
      }
   }

   /**
    * Check if aggregate info could be merged into MDX.
    */
   public static boolean isMergeable(AggregateInfo ainfo) {
      if(ainfo == null || ainfo.isEmpty()) {
         return false;
      }

      for(int i = 0; i < ainfo.getGroupCount(); i++) {
         GroupRef group = ainfo.getGroup(i);
         ColumnRef column = (ColumnRef) group.getDataRef();

         if(column.getDataRef() instanceof NamedRangeRef) {
            return true;
         }
      }

      for(int i = 0; i < ainfo.getAggregateCount(); i++) {
         AggregateRef aref = ainfo.getAggregate(i);
         AggregateFormula formula = aref.getFormula();

         if(!AggregateFormula.NONE.equals(formula)) {
            return true;
         }
         else if(getExpressionRef(aref.getDataRef()) != null) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get expression measure DataRef if any.
    */
   public static ExpressionRef getExpressionRef(DataRef measure) {
      if(!(measure instanceof ColumnRef)) {
         return null;
      }

      ColumnRef column = (ColumnRef) measure;
      DataRef ref = column.getDataRef();

      if(ref instanceof AliasDataRef) {
         ref = ((AliasDataRef) ref).getDataRef();
      }

      return ref instanceof ExpressionRef ? ((ExpressionRef) ref) : null;
   }

   /**
    * Get full dimension caption of a hierarchy.
    */
   public static String getFullCaption(XDimension dim) {
      String caption = getCaption(dim);

      if(dim instanceof HierDimension) {
         String parentCaption = ((HierDimension) dim).getParentDimension();

         if(parentCaption != null) {
            caption = parentCaption + "." + caption;
         }
      }

      return caption;
   }

   /**
    * Get dimension caption if any.
    */
   public static String getCaption(XDimension dim) {
      if(dim instanceof HierDimension) {
         return ((HierDimension) dim).getHierCaption();
      }
      else if(dim instanceof inetsoft.uql.xmla.Dimension) {
         return ((inetsoft.uql.xmla.Dimension) dim).getCaption();
      }

      return dim.getName();
   }

   /**
    * Copy worksheet information from one asset entry to another.
    */
   public static void copyWSInfo(AssetEntry source, AssetEntry target) {
      target.setReportDataSource(source.isReportDataSource());
      String alias = source.getAlias();

      if(alias != null && alias.trim().length() > 0) {
         target.setAlias(alias);
      }

      String description = source.getProperty("description");

      if(description != null) {
         target.setProperty("description", description);
      }
   }

   /**
    * Unpivot: header row is changed to a column named "Dimension", and crosstab
    * cells are changed to a column called "Measure".
    */
   public static XSwappableTable unpivot(XTable data, int hcol) {
      Object[] row0 = getRow(data, 0);
      hcol = Math.max(0, Math.min(hcol, row0.length - 1));
      List<String> ntypes = new ArrayList<>();

      for(int i = 0; i < hcol; i++) {
         ntypes.add(Tool.getDataType(data.getColType(i)));
      }

      ntypes.add(XSchema.STRING);

      // all measures same type
      boolean sameType = true;
      boolean allNumber = true;

      for(int i = hcol + 1; i < data.getColCount(); i++) {
         Class clazz0 = data.getColType(i - 1);
         Class clazz1 = data.getColType(i);

         if(!XSchema.isNumericType(Tool.getDataType(clazz0)) ||
            !XSchema.isNumericType(Tool.getDataType(clazz1)))
         {
            allNumber = false;
         }

         if(!Objects.equals(clazz0, clazz1)) {
            sameType = false;
         }
      }

      if(sameType) {
         ntypes.add(Tool.getDataType(data.getColType(hcol)));
      }
      else {
         ntypes.add(allNumber ? XSchema.DOUBLE : XSchema.STRING);
         sameType = allNumber;
      }

      XSwappableTable rows = new XSwappableTable();
      XTableColumnCreator[] creators = new XTableColumnCreator[ntypes.size()];

      for(int i = 0; i < creators.length; i++) {
         creators[i] = XObjectColumn.getCreator(ntypes.get(i));
         creators[i].setDynamic(false);
      }

      rows.init(creators);

      // header
      Object[] header = new Object[hcol + 2];
      System.arraycopy(row0, 0, header, 0, hcol);
      header[hcol] = getNextColumnName(header, "Dimension");
      header[hcol + 1] = getNextColumnName(header, "Measure");
      convertToString(header, hcol);
      rows.addRow(header);

      for(int r = 1; data.moreRows(r); r++) {
         Object[] row2 = getRow(data, r);

         for(int c = hcol; c < row2.length; c++) {
            Object[] rdata = new Object[hcol + 2];

            System.arraycopy(row2, 0, rdata, 0, hcol);
            rdata[hcol] = row0[c] != null ? format(row0[c]) : null;
            rdata[hcol + 1] = sameType ? row2[c] : (row2[c] == null ? null : format(row2[c]));

            rows.addRow(rdata);
         }
      }

      rows.complete();
      return rows;
   }

   private static String getNextColumnName(Object[] headers, String prefix) {
      Collection existing = Arrays.stream(headers).collect(toSet());
      String name = prefix;

      for(int i = 1; existing.contains(name); name = prefix + "_" + i++) {
         // empty
      }

      return name;
   }

   // get a table row
   private static Object[] getRow(XTable table, int r) {
      Object[] row = new Object[table.getColCount()];

      for(int i = 0; i < row.length; i++) {
         row[i] = table.getObject(r, i);
      }

      return row;
   }

   // convert the first n columns to string.
   private static void convertToString(Object[] row, int ncol) {
      for(int i = 0; i < ncol; i++) {
         if(row[i] != null && !(row[i] instanceof String)) {
            row[i] = row[i].toString();
         }
         else if(row[i] == null || ((String) row[i]).trim().isEmpty()) {
            row[i] = XUtil.getDefaultColumnName(i);
         }
      }
   }

   /**
    * Init default data types.
    */
   public static void initDefaultTypes(Object[] header, Map<Object, String> oldTypes,
                                       List<String> types, boolean retain)
   {
      String type = null;

      if(!retain) {
         types.clear();
      }

      for(int i = 0; i < header.length; i++) {
         type = oldTypes.get(header[i]);

         if(retain && type != null) {
            types.set(i, type);
         }
         else if(!retain) {
            // if we are detecting type, we still keep user specified column types from
            // before. if the type is not set (or is the default string), we will try
            // to detect again.
            if(type == null || type.equals(XSchema.STRING)) {
               types.add(XSchema.INTEGER);
            }
            else {
               types.add(type);
            }
         }
      }
   }

   // remove leading +
   private static String trimPlus(String str) {
      if(str.startsWith("+")) {
         str = str.substring(1);
      }

      return str;
   }

   /**
    * Get column type.
    *
    * @param isDmyOrder true if day is before month, else month is before day.
    */
   public static String getType(Object header, String type, Object data,
                                Map<String, Format> fmtMap, Boolean isDmyOrder)
   {
      if(!(data instanceof String) || "".equals(data) || Util.isNotApplicableValue(data)) {
         return type;
      }

      String str = (String) data;

      if(XSchema.INTEGER.equals(type)) {
         try {
            Integer.parseInt(trimPlus(str));
         }
         catch(Throwable ex) {
            try {
               if(Tool.isTime(str)) {
                  try {
                     LocalTime.parse(str, DateTimeFormatter.ofPattern("HH:mm:ss"));
                     type = XSchema.TIME;
                  }
                  catch(Exception ex2) {
                     type = XSchema.STRING;
                  }
               }
               else if(Tool.isDate(str)) {
                  if(str.indexOf(':') > 0) {
                     type = XSchema.TIME_INSTANT;
                  }
                  else {
                     Tool.parseDate(str, isDmyOrder);
                     type = XSchema.DATE;
                  }
               }
               // recognize boolean for csv.
               else if(isDmyOrder != null &&
                  ("true".equalsIgnoreCase(str) || "false".equalsIgnoreCase(str)))
               {
                  type = XSchema.BOOLEAN;
               }
               else {
                  type = XSchema.DOUBLE;
               }
            }
            catch(Throwable ex2) {
               if(str.indexOf(':') > 0) {
                  try {
                     LocalTime.parse(str, DateTimeFormatter.ofPattern("HH:mm:ss"));
                     type = XSchema.TIME;
                  }
                  catch(Exception ex3) {
                     type = XSchema.STRING;
                  }
               }
               else {
                  type = XSchema.DOUBLE;
               }
            }
         }
      }

      if(XSchema.DOUBLE.equals(type)) {
         NumberFormat fmt = NFMT;
         str = trimPlus(str);

         if(str.endsWith("%")) {
            fmt = PFMT;
         }
         else if(str.length() > 0 && str.charAt(0) == '$') {
            fmt = CFMT;
         }
         else if(str.startsWith("-$")) {
            fmt = CFMT;
         }
         else if(str.startsWith("($") && str.endsWith(")")) {
            fmt = C2FMT;
         }

         try {
            ParsePosition pos = new ParsePosition(0);
            fmt.parseObject(str, pos);

            if(pos.getIndex() != str.length()) {
               throw new RuntimeException("failed: " + pos.getIndex());
            }

            fmtMap.put((String) header, fmt);
         }
         catch(Throwable ex) {
            type = XSchema.STRING;
         }
      }

      return type;
   }

   /**
    * Handle sqlmergeable.
    */
   public static AssetQuery handleMergeable(RuntimeWorksheet rws, TableAssembly table) {
      if(table != null) {
         AssetQuerySandbox box = rws.getAssetQuerySandbox();
         int mode = AssetQuerySandbox.RUNTIME_MODE; // always use runtime mode
         table = (TableAssembly) table.clone();

         try {
            AssetQuery query = AssetQuery.createAssetQuery(
               table, mode, box, false, -1L, true, false);
            query.setLevel(0);

            try {
               query.setPlan(true);
               query.merge(box.getVariableTable());
            }
            catch(Exception ex) {
               // ignore it
            }

            return query;
         }
         catch(Exception e) {
            //do nothing
         }
      }

      return null;
   }

   /**
    * Gets the resource path used to obtain the permission object of the
    * specified asset.
    *
    * @param entry the asset of which to get the resource path.
    * @return the resource path or <tt>null</tt> if not applicable.
    */
   public static Resource getSecurityResource(AssetEntry entry) {
      Resource resource = null;

      if(entry != null) {
         if(entry.isScript()) {
            resource = new Resource(ResourceType.SCRIPT, entry.getProperty("source"));
         }
         else if(entry.isScriptFolder()) {
            resource = new Resource(ResourceType.SCRIPT_LIBRARY, "*");
         }
         else if(entry.isTableStyleSubFolder()) {
            resource = new Resource(ResourceType.TABLE_STYLE, entry.getProperty("folder"));
         }
         else if(entry.isTableStyleFolder()) {
            resource = new Resource(ResourceType.TABLE_STYLE_LIBRARY, "*");
         }
         else if(entry.isTableStyle()) {
            resource = new Resource(ResourceType.TABLE_STYLE, entry.getProperty("styleName"));
         }
         else if(entry.isDataSourceFolder()) {
            resource = new Resource(ResourceType.DATA_SOURCE_FOLDER, entry.getPath());
         }
         else if(entry.isDataSource()) {
            String path = entry.getProperty("source");
            path = path == null ? entry.getPath() : path;
            resource = new Resource(ResourceType.DATA_SOURCE, path);
         }
         else if(entry.isDataModel()) {
            resource = new Resource(ResourceType.DATA_SOURCE, entry.getPath());
         }
         else if(entry.isDataModelFolder()) {
            String ds = entry.getProperty("prefix");

            if(ds == null) {
               String ppath = entry.getParentPath();

               if(ppath != null && ppath.endsWith(DATA_MODEL)) {
                  int idx = ppath.lastIndexOf("/" + DATA_MODEL);
                  ds = ppath.substring(0, idx);
               }
            }

            resource = new Resource(ResourceType.DATA_MODEL_FOLDER, ds + "/" + entry.getName());
         }
         else if(entry.isQuery()) {
            resource = new Resource(ResourceType.QUERY, entry.getName());
         }
         else if(entry.isQueryFolder()) {
            // use datasource permission
            resource = new Resource(
               ResourceType.QUERY_FOLDER, entry.getName() + "::" + entry.getParentPath());
         }
         else if(entry.isLogicModel()) {
            String dsName = entry.getProperty("prefix");
            String folder = entry.getProperty("folder");

            if(dsName == null) {
               folder = getDataModelFolder(entry);
               dsName = getDataModelSource(entry);
            }

            String path = entry.getName() + "::" + dsName;
            path += folder == null ? "" : XUtil.DATAMODEL_FOLDER_SPLITER + folder;
            resource = new Resource(ResourceType.QUERY, path);
         }
         else if(entry.isExtendedLogicModel()) {
            String path = entry.getParent().getParent().getParentPath();

            if(!DATA_MODEL.equals(entry.getParent().getParent().getName())) {
               path = entry.getParent().getParentPath();
            }

            // @by davidd bug1325676386641, use datasource permission
            resource = new Resource(ResourceType.DATA_SOURCE, path);
         }
         else if(entry.isPartition()) {
            String dsName = entry.getProperty("prefix");
            String folder = entry.getProperty("folder");

            if(dsName == null) {
               folder = getDataModelFolder(entry);
               dsName = getDataModelSource(entry);
            }

            // use data model folder permission
            if(folder != null) {
               resource = new Resource(ResourceType.DATA_MODEL_FOLDER, dsName + "/" + folder);
            }
            // use datasource permission
            else {
               resource = new Resource(ResourceType.DATA_SOURCE, dsName);
            }
         }
         else if(entry.isExtendedPartition()) {
            // use datasource permission
            resource = new Resource(
               ResourceType.DATA_SOURCE, entry.getParent().getParent().getParentPath());
         }
         else if(entry.isVPM()) {
            // use datasource permission
            resource = new Resource(ResourceType.DATA_SOURCE, entry.getParentPath());
         }
         else if(entry.isReplet()) {
            resource = new Resource(ResourceType.REPORT, entry.getPath());
         }
         else if(entry.isRepositoryFolder()) {
            resource = new Resource(ResourceType.REPORT, entry.getPath());
         }
         else if(entry.isFolder() && entry.getScope() == AssetRepository.REPOSITORY_SCOPE) {
            // replet repository root
            resource = new Resource(ResourceType.REPORT, entry.getPath());
         }
         else if(entry.getScope() == AssetRepository.QUERY_SCOPE) {
            resource = new Resource(ResourceType.DATA_SOURCE, "*");
         }
         else if(entry.isWorksheet()) {
            resource = new Resource(ResourceType.ASSET, entry.getPath());
         }
         else if(entry.isFolder()) {
            resource = new Resource(ResourceType.ASSET, entry.getPath());
         }
         else if(entry.isViewsheet()) {
            resource = new Resource(ResourceType.REPORT, entry.getPath());
         }
      }

      return resource;
   }

   /**
    * Get data model folder of data model.
    */
   public static String getDataModelFolder(AssetEntry entry) {
      if(entry == null) {
         return null;
      }

      if(entry.isDataModelFolder()) {
         return entry.getName();
      }

      if(entry.isDataModel() || entry.isDataSource()) {
         return null;
      }

      return getDataModelFolder(entry.getParent());
   }

   /**
    * Get database name by data model entry.
    */
   public static String getDataModelSource(AssetEntry entry) {
      if(entry == null) {
         return null;
      }

      if(entry.isDataSource()) {
         return entry.getName();
      }

      return getDataModelSource(entry.getParent());
   }

   /**
    * Gets the resource path used to obtain the permission object of the
    * specified asset.
    *
    * @param asset the xasset.
    * @return the resource path or <tt>null</tt> if not applicable.
    */
   public static Resource getParentSecurityResource(XAsset asset) {
      return getParentSecurityResource(asset.getPath(), asset.getType());
   }

   /**
    * Gets the resource path used to obtain the permission object of the
    * specified asset.
    *
    * @param path the asset path.
    * @param type the asset type.
    * @return the resource path or <tt>null</tt> if not applicable.
    */
   public static Resource getParentSecurityResource(String path, String type) {
      Resource resource = null;

      if(type.equals(ScriptAsset.SCRIPT)) {
         resource = new Resource(ResourceType.SCRIPT_LIBRARY, "*");
      }
      else if(type.equals(TableStyleAsset.TABLESTYLE)) {
         resource = new Resource(path.equals("*") ? ResourceType.TABLE_STYLE_LIBRARY :
                                    ResourceType.TABLE_STYLE, path);
      }
      else if(type.equals(XDataSourceAsset.XDATASOURCE)) {
         resource = new Resource(ResourceType.DATA_SOURCE, "*");
      }
      else if(type.equals(XPartitionAsset.XPARTITION) ||
            type.equals(VirtualPrivateModelAsset.VPM))
      {
         resource = new Resource(ResourceType.DATA_SOURCE, path.substring(0, path.indexOf("^")));
      }
      else if(type.equals(XQueryAsset.XQUERY)) {
         resource = new Resource(ResourceType.QUERY, path);
      }
      else if(type.equals(XLogicalModelAsset.XLOGICALMODEL)) {
         int index = path.indexOf("^");
         resource = new Resource(
            ResourceType.QUERY, path.substring(index + 1) + "::" + path.substring(0, index));
      }
      else if(type.equals(ViewsheetAsset.VIEWSHEET)) {
         resource = new Resource(ResourceType.REPORT, path);
      }
      else if(type.equals(WorksheetAsset.WORKSHEET)) {
         resource = new Resource(ResourceType.ASSET, path);
      }
      else if(type.equals(DeviceAsset.DEVICE)) {
         resource = new Resource(ResourceType.DEVICE, "*");
      }
      else if(type.equals(DashboardAsset.DASHBOARD)) {
         resource = new Resource(ResourceType.DASHBOARD, "*");
      }

      return resource;
   }

   public static boolean isLibraryType(AssetEntry entry) {
      return entry.isScript() || entry.isScriptFolder() || entry.isTableStyle() || entry.isTableStyleFolder();
   }

   public static final ResourceType getLibraryAssetType(AssetEntry entry) {
      if(entry.isTableStyleFolder() && entry.isRoot()) {
         return ResourceType.TABLE_STYLE_LIBRARY;
      }
      else if(entry.isTableStyleFolder() || entry.isTableStyle()) {
         return ResourceType.TABLE_STYLE;
      }
      else if(entry.isScriptFolder()) {
         return ResourceType.SCRIPT_LIBRARY;
      }
      else if(entry.isScript()) {
         return ResourceType.SCRIPT;
      }

      return null;
   }

   public static String getLibraryPermissionPath(AssetEntry entry, ResourceType type) {
      String path = null;

      if(entry.isRoot() || (type == ResourceType.TABLE_STYLE_LIBRARY || type == ResourceType.SCRIPT_LIBRARY)) {
         path = "*";
      }
      else if(entry.isScript()){
         path = entry.getName();
      }
      else if(entry.isTableStyle()) {
         path = entry.getProperty("styleName");
      }
      else if(entry.isTableStyleFolder()) {
         path = entry.getProperty("folder");
      }

      return path;
   }

   public static ResourceAction getAssetDeployPermission(Resource resource) {
      switch(resource.getType()) {
      case DEVICE:
         // devices only have access permission, so use that instead of admin
         return ResourceAction.ACCESS;
      default:
         return ResourceAction.ADMIN;
      }
   }

   /**
    * Check if DataRef is an Aggregate CalcField and the DataRef's datatype is a string.
    */
   public static boolean isStringAggCalcField(XAggregateRef ref) {
      return ref.getRefType() == AbstractDataRef.AGG_CALC &&
         XSchema.STRING.equals(ref.getOriginalDataType());
   }

   public static TableLens applyAlias(TableAssembly assembly, TableLens table) {
      if(table == null) {
         return table;
      }

      ColumnSelection columns = assembly.getColumnSelection();
      boolean isCube = AssetUtil.isCubeTable(assembly) && !AssetUtil.isWorksheetCube(assembly);
      Object[] headers = new Object[table.getColCount()];
      String[] ids = new String[table.getColCount()];

      for(int i = 0; i < headers.length; i++) {
         headers[i] = table.getObject(0, i);
         ids[i] = table.getColumnIdentifier(i);
      }

      ColumnIndexMap columnIndexMap = new ColumnIndexMap(table);
      ColumnIndexMap fuzzyColumnIndexMap = new ColumnIndexMap(table, true);

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);

         if(!column.isProcessed() && column.isExpression()) {
            continue;
         }

         int col;

         if(column.getEntity() == null || column.getEntity().isEmpty()) {
            col = Util.findColumn(fuzzyColumnIndexMap, column.getAttribute());
         }
         else {
            col = Util.findColumn(fuzzyColumnIndexMap,
               column.getEntity() + "." + column.getAttribute());
         }

         if(col < 0) {
            col = AssetUtil.findColumn(table, column, isCube, false, columnIndexMap);
         }

         String alias = column.getAlias();
         String id = AssetUtil.getAttributeString(column);
         String caption = column.getCaption();

         if(column.getDataRef() instanceof AttributeRef && caption == null) {
            AttributeRef ref0 = (AttributeRef) column.getDataRef();
            caption = ref0.getCaption() == null ? alias : ref0.getCaption();
         }

         if(col >= 0 && alias != null) {
            if(isCube) {
               headers[col] = caption == null ? alias : caption;
            }
            else {
               headers[col] = alias;
            }
         }

         if(col >= 0) {
            ids[col] = AssetUtil.getColumnIdentifier(id, column, AssetUtil.isBCSpecial(assembly));
         }
      }

      return PostProcessor.renameColumns(table, headers, ids);
   }

   public static String getColumnIdentifier(String id, ColumnRef column, boolean bc) {
      // for 10.1 bc
      if(bc && column != null && column.getAlias() != null &&
         column.getDataRef() instanceof DateRangeRef &&
         !column.getAlias().equals(column.getDataRef().getName()))
      {
         return column.getAlias();
      }

      return id;
   }

   public static boolean isBCSpecial(TableAssembly table) {
      return table != null && "true".equalsIgnoreCase(table.getProperty("BC_VALIDATE"));
   }

   /**
    * Find the data ref from the specified aggregate info.
    */
   public static DataRef findRef(AggregateInfo ainfo, DataRef ref) {
      for(int i = 0; i < ainfo.getAggregateCount(); i++) {
         AggregateRef aggregate = ainfo.getAggregate(i);
         ColumnRef column = (ColumnRef) aggregate.getDataRef();
         DataRef aref = column.getDataRef();

         if(aref.getName().equals(ref.getName())) {
            return column;
         }
      }

      for(int i = 0; i < ainfo.getAggregateCount(); i++) {
         AggregateRef aggregate = ainfo.getAggregate(i);
         ColumnRef column = (ColumnRef) aggregate.getDataRef();
         DataRef bref = column.getDataRef();

         if(bref instanceof ColumnRef) {
            bref = ((ColumnRef) bref).getDataRef();
         }

         if(bref instanceof AliasDataRef) {
            AliasDataRef alias = (AliasDataRef) bref;
            bref = alias.getDataRef();
         }

         if(ref.equals(bref)) {
            return column;
         }
      }

      return null;
   }

   /**
    * Check if a range column is used by table filtering.
    */
   public static void validateConditions(ColumnSelection columns, TableAssembly tbl) {
      validateConditionList(columns, tbl.getPreRuntimeConditionList());
      validateConditionList(columns, tbl.getPostRuntimeConditionList());
      validateConditionList(columns, tbl.getPreConditionList());
      validateConditionList(columns, tbl.getPostConditionList());
      validateConditionList(columns, tbl.getRankingConditionList());
   }

   /**
    * Check if a range column is used by a condition list wrapper.
    */
   private static void validateConditionList(
      ColumnSelection columns,
      ConditionListWrapper wrapper)
   {
      if(wrapper != null) {
         ConditionList clist = wrapper.getConditionList();

         if(clist != null) {
            clist.validate(columns);
         }
      }
   }

   public static boolean isDateRangeValid(DataRef ref, ColumnSelection cols) {
      ref = getBaseAttribute(ref);

      // not a date range so just return true
      if(!(ref instanceof DateRangeRef)) {
         return true;
      }

      DateRangeRef dateRangeRef = (DateRangeRef) ref;
      DataRef baseRef = dateRangeRef.getDataRef();
      DataRef colRef = cols.getAttribute(baseRef.getAttribute());

      if(colRef == null || !baseRef.hasDataType() ||
         Tool.equals(baseRef.getDataType(), colRef.getDataType()) ||
         XSchema.TIME_INSTANT.equals(colRef.getDataType()))
      {
         return true;
      }

      if(!XSchema.isDateType(colRef.getDataType())) {
         return false;
      }

      return XSchema.DATE.equals(colRef.getDataType()) !=
         DateRangeRef.isTimeOption(dateRangeRef.getDateOption());
   }

//   private static final class ScriptDependencyListener implements ScriptIterator.ScriptListener {
//      ScriptDependencyListener(Worksheet worksheet, Assembly excludedAssembly) {
//         this.worksheet = worksheet;
//         this.excludedAssembly = excludedAssembly;
//      }
//
//      @Override
//      public void nextElement(ScriptIterator.Token token, ScriptIterator.Token pref,
//                              ScriptIterator.Token cref)
//      {
//
//      }
//
//      private Assembly parseText(String str) {
//
//      }
//
//      private final Worksheet worksheet;
//      private final Assembly excludedAssembly;
//      private final Map<String, Set<AssemblyEntry>> dependencies = new HashMap<>();
//   }

   public static final int defw = 100;
   public static final int defh = 20;
   private static final Logger LOG = LoggerFactory.getLogger(AssetUtil.class);

   public static boolean debug = false;
   private static DecimalFormat numFmt = null;
   private static final DecimalFormat NFMT = new DecimalFormat("#,###.#");
   private static final NumberFormat PFMT = NumberFormat.getPercentInstance();
   private static final NumberFormat CFMT = new DecimalFormat("$#,##0.##;-$#,##0.##");
   private static final NumberFormat C2FMT = new DecimalFormat("$#,##0.##;($#,##0.##)");

   private static final String REPOSITORY_KEY = AssetUtil.class.getName() + ".repository";
   private static final String DESIGN_REPOSITORY_KEY =
      AssetUtil.class.getName() + ".designRepository";
}
