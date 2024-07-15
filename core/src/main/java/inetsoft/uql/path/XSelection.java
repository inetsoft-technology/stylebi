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
package inetsoft.uql.path;

import inetsoft.uql.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XValueNode;
import inetsoft.uql.table.XObjectColumn;
import inetsoft.uql.table.XTableColumnCreator;
import inetsoft.uql.util.TableWrapper;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.util.expr.ExprParser;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import inetsoft.util.algo.BidiMap;
import org.pojava.datetime.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.text.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * An XSelection is a list of column selections. When applied to a tree,
 * the XSelection selects nodes from the tree according to the column
 * specification, and create a table node from the tree nodes.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class XSelection implements java.io.Serializable, java.lang.Cloneable {
   /**
    * Parse a selection string and create a selection object.
    */
   public static XSelection parse(String select) throws ParseException {
      try {
         ExprParser parser = new ExprParser(new StringReader(select));

         return parser.xselection();
      }
      catch(Exception e) {
         LOG.error("Failed to parse selection expression: " + select, e);
         throw new java.text.ParseException(e.toString(), 0);
      }
   }

   /**
    * Create an empty selection.
    */
   public XSelection() {
      super();
   }

   /**
    * If ExpandSubtree is set to true, all subtrees in a selected tree
    * are expanded. For example, if a node contains a subtree constaining
    * 5 children, the node generates 5 rows. If a node contains two subtrees
    * containing 4 and 5 children respectively, it generates 20 rows.
    */
   public void setExpandSubtree(boolean expanded) {
      this.expanded = expanded;
   }

   /**
    * Check if the subtrees should be expanded.
    */
   public boolean isExpandSubtree() {
      return expanded;
   }

   /**
    * Apply the path to the tree, and return selected nodes from the tree
    * as a table.
    * @param root tree root.
    * @return selected nodes as table.
    */
   public XTableNode select(XNode root) throws Exception {
      XSequenceNode seq = null;

      if(root instanceof XTableNode) {
         return new TableTable((XTableNode) root);
      }
      else if(!(root instanceof XSequenceNode)) {
         seq = new XSequenceNode();
         seq.addChild(root);
         seq.setName(root.getName());
      }
      else {
         seq = (XSequenceNode) root;
      }

      if(expanded) {
         // optimization, creating a new ExpandedSeqTable for every branch
         // is expensive. Since it can be shared for one XSelection, we
         // reuse it here instead of creating a new one. We don't use the
         // cached table if it has not been completed consumed (closed).
         if(cachedSeqTable != null && cachedSeqTable.isClosed()) {
            cachedSeqTable.setSequenceNode(seq);
         }
         else {
            // if sequence is empty, don't cache it since the meta info in
            // ExpandedSeqTable may not be 100% accurate
            if(seq.getChildCount() == 0) {
               return new ExpandedSeqTable(seq);
            }

            cachedSeqTable = new ExpandedSeqTable(seq);
         }

         return cachedSeqTable;
      }
      else {
         return new SeqTable(seq);
      }
   }

   /**
    * Remove all components from the path.
    */
   public void clear() {
      paths.removeAllElements();
      opaths.removeAllElements();
      indexmap.clear();
      lowerPaths = null;
      aliasmap.removeAllElements();
      pathAliases = null;
      expmap.removeAllElements();
      metamap.removeAllElements();
      cachedSeqTable = null;
   }

   /**
    * Add a column to the selection list. The column can be a node path, e.g.,
    * employee.name, or a node attribute, e.g., employee@title.
    * @param path tree node path to select as a table column.
    */
   public int addColumn(String path) {
      paths.add(path);
      opaths.add("");
      indexmap.clear();
      lowerPaths = null;
      aliasmap.add(null);
      expmap.add(Boolean.FALSE);
      metamap.add(null);

      cachedSeqTable = null;
      return paths.size() - 1;
   }

   /**
    * Remove an alias column.
    */
   public boolean removeAliasColumn(String alias) {
      int index = aliasmap.indexOf(alias);

      if(index >= 0) {
         removeColumn(index);
         pathAliases = null;
         return true;
      }

      return false;
   }

   /**
    * Remove a selected column.
    * @param path tree node path selected as a table column.
    */
   public boolean removeColumn(String path) {
      int idx = paths.indexOf(path);

      if(idx >= 0) {
         removeColumn(idx);
         return true;
      }

      return false;
   }

   /**
    * Remove a selected column.
    * @param idx column index in the list.
    */
   public boolean removeColumn(int idx) {
      if(idx >= 0) {
         String path = paths.get(idx);
         paths.removeElementAt(idx);
         opaths.removeElementAt(idx);
         indexmap.clear();
         lowerPaths = null;
         cachedSeqTable = null;

         if(idx < aliasmap.size()) {
            aliasmap.remove(idx);
            pathAliases = null;
         }

         if(idx < expmap.size()) {
            expmap.remove(idx);
         }

         if(idx < metamap.size()) {
            metamap.remove(idx);
         }

         // @by larryl, a column may appear multiple times in a list. If there
         // is still same column on the list, the format and type should not
         // be dropped
         if(paths.indexOf(path) < 0) {
            typemap.remove(path);
            fmtmap.remove(path);
            fixedFmtMap.remove(path);
         }

         return true;
      }

      return false;
   }

   /**
    * Set an alias for a table column.
    * @param col the column index.
    * @param alias column alias.
    * @param quote for quoted alias for some db.
    */
   public void setAlias(int col, String alias, String quote) {
      if(alias == null || alias.length() == 0) {
         if(col < aliasmap.size()) {
            aliasmap.set(col, null);
            pathAliases = null;
         }
      }
      else {
         if(alias.startsWith(quote) && alias.endsWith(quote) && alias.length() > 1) {
            alias = alias.substring(1, alias.length() - 1);
         }

         if(col >= aliasmap.size()) {
            aliasmap.setSize(col + 1);
         }

         aliasmap.set(col, alias);
         pathAliases = null;
      }
   }

   /**
    * Set an alias for a table column.
    * @param col the column index.
    * @param alias column alias.
    */
   public void setAlias(int col, String alias) {
      setAlias(col, alias, "'");
   }

   /**
    * Get the alias for a table column.
    * @param col the column index.
    * @return column alias.
    */
   public String getAlias(int col) {
      return col < aliasmap.size() ? aliasmap.get(col) : null;
   }

   /**
    * Set a col is expression column.
    */
   public void setExpression(int col, boolean b) {
      if(col >= 0 && col < expmap.size()) {
         expmap.set(col, b ? Boolean.TRUE : Boolean.FALSE);
      }
      else {
         expmap.add(b ? Boolean.TRUE : Boolean.FALSE);
      }
   }

   /**
    * Check the column is a expression column.
    */
   public boolean isExpression(String column) {
      return isExpression(indexOfColumn(column));
   }

   /**
    * Check the index is a expression column.
    */
   public boolean isExpression(int idx) {
      if(idx >= 0 && idx < expmap.size()) {
         return expmap.get(idx);
      }

      return false;
   }

   /**
    * Set the base column of an expression.
    */
   public void setBaseColumn(String exp, String col) {
      Objects.requireNonNull(exp, "The base column expression cannot be null");
      Objects.requireNonNull(col, "The base column cannot be null");
      bmap.put(exp, col);
   }

   /**
    * Set the base column of an expression.
    */
   public String getBaseColumn(String exp) {
      return bmap.get(exp);
   }

   /**
    * Set the type of a column. The value of the tree node is converted
    * to the specified type during selection.
    * @param path tree node path.
    * @param type type name. One of the value defined in XSchema class.
    * @param fmt format used to create a java.text.Format object.
    */
   public void setConversion(String path, String type, String fmt) {
      if(type == null) {
         typemap.remove(path);
      }
      else {
         typemap.put(path, type);

         if(fmt != null) {
            fmtmap.put(path, fmt);
         }
      }
   }

   /**
    * Set the type of a column. Setting a column type could force the
    * column values to be converted to the specified type if the
    * datasource returns string instead of the specified value type.
    */
   public void setType(String path, String type) {
      if(type == null || type.length() == 0) {
         typemap.remove(path);
      }
      else {
         typemap.put(path, type);
      }
   }

   /**
    * Get the type conversion for the column.
    * @param path tree node path.
    */
   public String getType(String path) {
      return typemap.get(path);
   }

   /**
    * Set description.
    */
   public void setDescription(String path, String des) {
      if(des == null || des.length() == 0) {
         desmap.remove(path);
      }
      else {
         desmap.put(path, des);
      }
   }

   /**
    * Get description.
    * @param path tree node path.
    */
   public String getDescription(String path) {
      return desmap.get(path);
   }

   /**
    * Get the format string used for converting the input node
    * value to the specified type. The format depends on the type.
    * For date related types, the format is a SimpleDateFormat specification.
    * For numeric types, the format is a DecimalFormat specification.
    * @param path tree node path.
    * @return format string or null.
    */
   public String getFormat(String path) {
      return fmtmap.get(path);
   }

   /**
    * Set the format of a column. The format is used to parse data if the
    * type is also set for this column.
    */
   public void setFormat(String path, String fmt) {
      if(fmt == null || fmt.length() == 0) {
         fmtmap.remove(path);
      }
      else {
         fmtmap.put(path, fmt);
      }
   }

   /**
    * Gets the flag that determines if the format is fixed by the source of the
    * data or may be modified by the user.
    *
    * @param path the column path.
    *
    * @return <tt>true</tt> if fixed; <tt>false</tt> otherwise.
    */
   public boolean isFormatFixed(String path) {
      Boolean value = fixedFmtMap.get(path);
      return value != null && value.booleanValue();
   }

   /**
    * Sets the flag that determines if the format is fixed by the source of the
    * data or may be modified by the user.
    *
    * @param path        the column path.
    * @param formatFixed <tt>true</tt> if fixed; <tt>false</tt> otherwise.
    */
   public void setFormatFixed(String path, boolean formatFixed) {
      fixedFmtMap.remove(path);

      if(formatFixed) {
         fixedFmtMap.put(path, Boolean.TRUE);
      }
   }

   /**
    * Get the number of columns in this selection.
    */
   public int getColumnCount() {
      return paths.size();
   }

   /**
    * Check if the selection is empty.
    */
   public boolean isEmpty() {
      return paths.isEmpty();
   }

   /**
    * Get the specified column.
    */
   public String getColumn(int idx) {
      return paths.get(idx);
   }

   /**
    * Get lower case version of the column. This is an optimization to avoid repeatedly
    * converting string to lower case.
    * @hidden
    */
   public String getLowerCaseColumn(int idx) {
      if(lowerPaths == null) {
         lowerPaths = paths.stream().map(p -> p.toLowerCase()).collect(Collectors.toList());
      }

      return lowerPaths.get(idx);
   }

   /**
    * Get the original column.
    */
   public String getOriginalColumn(int idx) {
      return opaths.get(idx);
   }

   /**
    * Set the original name of the specified column.
    */
   public void setOriginalColumn(int idx, String col) {
      opaths.set(idx, col);
   }

   /**
    * Set the name of the specified column.
    */
   public void setColumn(int idx, String col) {
      paths.set(idx, col);
      indexmap.clear();
      lowerPaths = null;
      cachedSeqTable = null;
   }

   /**
    * Get the all meta data.
    * @return all mete data.
    */
   public Enumeration<XMetaInfo> getXMetaInfos() {
      return metamap.elements();
   }

   /**
    * Get the meta data.
    * @param idx the column index.
    * @return mete data.
    */
   public XMetaInfo getXMetaInfo(int idx) {
      return getXMetaInfo(idx, true);
   }

   /**
    * Get the meta data.
    * @param idx the column index.
    * @return mete data.
    */
   public XMetaInfo getXMetaInfo(int idx, boolean create) {
      XMetaInfo meta = null;

      if(idx > -1 && idx < metamap.size()) {
         meta = metamap.get(idx);
      }

      meta = meta == null && create ? new XMetaInfo() : meta;

      return meta;
   }

   /**
    * Set the meta data.
    * @param idx the column index.
    * @param meta the mete data.
    */
   public void setXMetaInfo(int idx, XMetaInfo meta) {
      if(idx > -1) {
         metamap.set(idx, meta);
      }
   }

   /**
    * Get the meta data.
    * @param path tree node path.
    * @return mete data.
    */
   public XMetaInfo getXMetaInfo(String path) {
      return getXMetaInfo(path, true);
   }

   /**
    * Get the meta data.
    * @param path tree node path.
    * @return mete data.
    */
   public XMetaInfo getXMetaInfo(String path, boolean create) {
      XMetaInfo meta = null;
      int idx = -1;

      if(path != null && (idx = indexOf(path)) != -1) {
         meta = metamap.get(idx);
      }

      meta = meta == null && create ? new XMetaInfo() : meta;

      return meta;
   }

   /**
    * Set the meta data of a column.
    * @param path tree node path.
    * @param meta the meta data.
    */
   @SuppressWarnings("unused")
   private void setXMetaInfo(String path, XMetaInfo meta) {
      if(path == null) {
         return;
      }

      int idx = indexOf(path);

      if(idx > -1) {
         metamap.set(idx, meta);
      }
   }

   /**
    * Check if the specified column is in the selection.
    */
   public boolean contains(String path) {
      return paths.indexOf(path) >= 0;
   }

   /**
    * Get the column with the specified alias.
    */
   public String getAliasColumn(String alias) {
      return getPathAliases().getKey(alias);
   }

   private BidiMap<String, String> getPathAliases() {
      BidiMap<String, String> map = this.pathAliases;

      if(map == null) {
         map = new BidiMap<>();
         int size = Math.min(paths.size(), aliasmap.size());

         for(int i = 0; i < size; i++) {
            map.put(paths.get(i), aliasmap.get(i));
         }

         this.pathAliases = map;
      }

      return map;
   }

   /**
    * Check if a column name is an alias. A column name is an alias if and only
    * if it's an existing alias and the corresponding column is not equal to it.
    */
   public boolean isAlias(String alias) {
      if(alias == null) {
         return false;
      }

      String path = getPathAliases().getKey(alias);

      if(path != null) {
         return !alias.equals(path);
      }

      return false;
   }

   /**
    * Get the index of the specified path.
    */
   public int indexOf(String path) {
      return paths.indexOf(path);
   }

   /**
    * Convert to the string representation of this selection. The
    * string can be used in the parse() method to reconstruct the
    * object.
    */
   @Override
   public String toString() {
      return toString(false);
   }

   /**
    * Get the identifier.
    */
   public String toIdentifier() {
      String cls = getClass().getName();
      int idx = cls.lastIndexOf(".");

      if(idx >= 0) {
         cls = cls.substring(idx + 1);
      }

      return cls + "[paths:" + paths + ",opaths:" + opaths+",alias:" + aliasmap+
         ",expression:" + expmap + ",meta:" + metamap + ",bmap:" + bmap + "]";
   }

   /**
    * Convert to a SQL compliant selection.
    */
   public String toString(boolean sql) {
      StringBuilder str = new StringBuilder("select ");

      if(expanded) {
         str.append("expanded ");
      }

      for(int i = 0; i < paths.size(); i++) {
         if(i > 0) {
            str.append(",");
         }

         // @by billh, fix customer bug bug1319731194403. For non-sql, it's not
         // allowed to has dot in name, so here we need quote it
         if(!sql) {
            str.append(XUtil.quoteAlias(paths.get(i).toString(), null));
         }
         else {
            // some database allows space in column names, and some does
            // not (in which case quotes are not allowed)
            str.append(XUtil.quoteName(paths.get(i).toString(), null));
         }

         String path = paths.get(i);
         String alias = getAlias(i);

         if(alias != null) {
            str.append(" as \"" + alias + '"');
         }

         if(!sql) {
            String type = getType(path);
            boolean print_type = false;

            if(type != null) {
               for(int k = 0; k < PRIMITIVE_TYPES.length; k++) {
                  if(PRIMITIVE_TYPES[k].equals(type)) {
                     print_type = true;
                     break;
                  }
               }
            }

            if(print_type) {
               str.append(" to " + type);
               String fmt = getFormat(path);

               if(fmt != null) {
                  str.append("('" + fmt + "')");
               }
            }
         }
      }

      return str.toString();
   }

   /**
    * Get the column index with a name, which might be the alias, column or
    * the suffix of the column.
    * @param name the specified name.
    * @return the column index if found, <tt>null</tt> otherwise.
    */
   public int indexOfColumn(String name) {
      return indexOfColumn(name, false);
   }

   /**
    * Get the column index with a name, which might be the alias, column or
    * the suffix of the column.
    * @param name the specified name.
    * @return the column index if found, <tt>null</tt> otherwise.
    */
   public int indexOfColumn(String name, boolean upperCasedAlias) {
      return indexOfColumn(name, upperCasedAlias, false);
   }

   /**
    * Get the column index with a name, which might be the alias, column or
    * the suffix of the column.
    * @param name the specified name.
    * @return the column index if found, <tt>null</tt> otherwise.
    */
   public int indexOfColumn(String name, boolean upperCasedAlias, boolean ignoreCase) {
      Integer idx = indexmap.get(name);

      // optimization, for very large selection. (53160)
      if(idx != null) {
         return idx;
      }

      int i = -1;
      int size = paths.size();

      for(i = 0; i < size; i++) {
         String column = getColumn(i);

         if(Tool.equals(name, column, !ignoreCase)) {
            indexmap.put(name, i);
            return i;
         }
      }

      for(i = 0; i < size; i++) {
         String alias = getAlias(i);

         if(Tool.equals(alias, name, !upperCasedAlias && !ignoreCase)) {
            indexmap.put(name, i);
            return i;
         }
      }

      // is the suffix of the column?
      for(i = 0; i < size; i++) {
         if(getAlias(i) != null) {
            continue;
         }

         String column = getColumn(i);

         if(ignoreCase) {
            column = column != null ? column.toUpperCase() : column;
            name = name != null ? name.toUpperCase() : name;
         }

         if(column.endsWith("." + name)) {
            indexmap.put(name, i);
            return i;
         }
      }

      indexmap.put(name, -1);
      return -1;
   }

   /**
    * Find the column with a name, which might be the alias, column or the
    * suffix of the column.
    * @param name the specified name.
    * @return the column if found, <tt>null</tt> otherwise.
    */
   public String findColumn(String name) {
      int length = name == null ? 0 : name.length();

      // if name is quoted, we remove the quotes
      if(length > 2 && name.charAt(0) == '\"' &&
         name.charAt(length - 1) == '\"')
      {
         name = name.substring(1, length - 1);
      }

      // is the column itself?
      if(contains(name)) {
         return name;
      }
      // is the alias of the column?
      else if(getAliasColumn(name) != null) {
         return getAliasColumn(name);
      }
      // is the suffix of the column?
      else {
         for(int i = 0; i < getColumnCount(); i++) {
            if(getAlias(i) != null) {
               continue;
            }

            String column = getColumn(i);

            if(column.endsWith("." + name)) {
               return column;
            }
         }
      }

      return null;
   }

   // base table class
   abstract class Table extends XTableNode {
      @SuppressWarnings("unchecked")
      public Table() {
         this.paths = (Vector<String>) XSelection.this.paths.clone();
         fmts = new Format[paths.size()];
         types = new Class[fmts.length];
         errors = new int[fmts.length];

         for(int i = 0; i < paths.size(); i++) {
            String path = paths.get(i);
            String type = XSelection.this.getType(path);
            String fmt = getFormat(path);
            // don't default to String or formatting may not work
            // e.g. date would be forced to string in XSwappableTable
            // and date formatting would no longer work. The actual
            // type is assigned in getObject()
            types[i] = Object.class;

            if(type != null) {
               types[i] = Tool.getDataClass(type);

               if(type.equals(XSchema.INTEGER) || type.equals(XSchema.LONG) ||
                  type.equals(XSchema.BYTE) || type.equals(XSchema.SHORT))
               {
                  if(fmt == null) {
                     switch(type) {
                     case XSchema.INTEGER:
                        fmts[i] = new IntegerFormat();
                        break;
                     case XSchema.LONG:
                        fmts[i] = new LongFormat();
                        break;
                     case XSchema.BYTE:
                        fmts[i] = new ByteFormat();
                        break;
                     case XSchema.SHORT:
                        fmts[i] = new ShortFormat();
                        break;
                     }
                  }
                  else {
                     fmts[i] = new DecimalFormat(fmt);
                  }

                  if(fmts[i] instanceof NumberFormat) {
                     ((NumberFormat) fmts[i]).setParseIntegerOnly(true);
                  }
               }
               else if(type.equals(XSchema.FLOAT) ||
                  type.equals(XSchema.DOUBLE))
               {
                  if(fmt == null) {
                     switch(type) {
                     case XSchema.FLOAT:
                        fmts[i] = new FloatFormat();
                        break;
                     case XSchema.DOUBLE:
                        fmts[i] = new DoubleFormat();
                        break;
                     }
                  }
                  else {
                     fmts[i] = new DecimalFormat(fmt);
                  }

                  if(fmts[i] instanceof NumberFormat) {
                     ((NumberFormat) fmts[i]).setParseIntegerOnly(true);
                  }
               }
               else if(type.equals(XSchema.DATE) ||
                  type.equals(XSchema.TIME_INSTANT) ||
                  type.equals(XSchema.TIME))
               {
                  if(fmt == null) {
                     fmts[i] = DateFormat.getDateTimeInstance();
                  }
                  else {
                     fmts[i] = Tool.createDateFormat(fmt);
                  }
               }
               else if(type.equals(XSchema.BOOLEAN)) {
                  fmts[i] = new BooleanFormat();
               }
            }
         }
      }

      @Override
      public int getColCount() {
         return paths.size();
      }

      @Override
      public String getName(int col) {
         String name = paths.get(col);
         String alias = getAlias(col);

         return (alias == null) ? name : alias;
      }

      @Override
      public Class<?> getType(int col) {
         return types[col];
      }

      @Override
      public Object getObject(int col) {
         Object val = getColumnValue(col);

         // @by mikec, optimize, only parse the string value by
         // the predefined format or default system format.
         // If the value object is already a non-string object,
         // assume it has already be parsed when create the node,
         // in which case we return the object directly.
         if(fmts[col] != null && val instanceof String) {
            String str = ((String) val).trim();

            if(str.length() == 0) {
               val = null;
            }
            else {
               try {
                  if("null".equals(str)) {
                     // treat null as null
                     val = null;
                  }
                  else if(errors[col] > 10 && fmts[col] instanceof DateFormat) {
                     DateTime dt = DateTime.parse(str, CoreTool.getDateTimeConfig());

                     if(dt != null) {
                        val = dt.toDate();
                     }
                  }
                  else if(errors[col] < 1000) {
                     val = fmts[col].parseObject(str);
                  }
               }
               catch(Exception e) {
                  if(prevException == null || prevException.getClass() != e.getClass()) {
                     LOG.warn("Error parsing value: [" + val + "]: " + fmts[col]);
                  }
                  else if(LOG.isDebugEnabled()) {
                     LOG.debug("Error parsing value: [" + val + "]: " + fmts[col]);
                  }

                  errors[col]++;
                  prevException = e;
               }
            }
         }

         // assign real type to column
         if(types[col] == Object.class && val != null) {
            types[col] = val.getClass();
         }

         // make sure that all the objects share the same data type, for we
         // might create column to store the data by the first non-null object
         if(val instanceof Number) {
            if(types[col] == Double.class) {
               if(!(val instanceof Double)) {
                  val = ((Number) val).doubleValue();
               }
            }
            else if(types[col] == Float.class) {
               if(!(val instanceof Float)) {
                  val = ((Number) val).floatValue();
               }
            }
            else if(types[col] == Integer.class) {
               if(!(val instanceof Integer)) {
                  val = ((Number) val).intValue();
               }
            }
            else if(types[col] == Short.class) {
               if(!(val instanceof Short)) {
                  val = ((Number) val).shortValue();
               }
            }
         }

         return val;
      }

      @Override
      public XMetaInfo getXMetaInfo(int col) {
         return XSelection.this.getXMetaInfo(getColumn(col));
      }

      // get the original value for the column
      public abstract Object getColumnValue(int col);

      Format[] fmts = null;
      int[] errors;
      Class<?>[] types = null;
      List<String> paths;
      private Throwable prevException;
   }

   // convert a sequence node to a table
   class SeqTable extends Table {
      public SeqTable(XSequenceNode seq) {
         this.seq = seq;
         setName(seq.getName());
      }

      @Override
      public boolean next() {
         return ++idx < seq.getChildCount();
      }

      @Override
      public Object getColumnValue(int col) {
         XNode node = seq.getChild(idx);

         return node.getValue(node.getName() + "." + paths.get(col));
      }

      @Override
      public boolean rewind() {
         idx = -1;
         return true;
      }

      @Override
      public boolean isRewindable() {
         return true;
      }

      @Override
      public void close() {
         if(seq != null) {
            seq.removeAllChildren();
         }
      }

      @Override
      public int getAppliedMaxRows() {
         return seq.getAppliedMaxRows();
      }

      int idx = -1;
      XSequenceNode seq;
   }

   /**
    * converts a table node to a table node (with column selection and
    * type convertion.
    */
   class TableTable extends Table implements TableWrapper {
      public TableTable(XTableNode seq) {
         this.seq = seq;
         setName(seq.getName());
         colmap = new int[paths.size()];
         ccount = 0;

         for(int i = 0; i < colmap.length; i++) {
            String name = paths.get(i);
            colmap[i] = -1;

            for(int j = 0; j < seq.getColCount(); j++) {
               if(equalsColumnName(name, getAlias(i), seq.getName(j), j)) {
                  colmap[i] = j;
                  ccount++;
                  break;
               }
            }
         }

         List<String> npaths = new ArrayList<>();
         alias = new String[ccount];
         int[] ncolmap = new int[ccount];
         Format[] nfmts = new Format[ccount];
         Class<?>[] ntypes = new Class[ccount];
         int counter = 0;

         for(int i = 0; i < colmap.length; i++) {
            if(colmap[i] == -1) {
               continue;
            }

            npaths.add(paths.get(i));
            alias[counter] = getAlias(i);
            ncolmap[counter] = colmap[i];
            nfmts[counter] = fmts[i];
            ntypes[counter] = types[i];
            counter++;
         }

         paths = npaths;
         colmap = ncolmap;
         fmts = nfmts;
         types = ntypes;
         errors = new int[fmts.length];
         row = new Object[ccount];
      }

      @Override
      public int getColCount() {
         return ccount;
      }

      @Override
      public XTableNode getTable() {
         return seq;
      }

      @Override
      public int getAppliedMaxRows() {
         return seq.getAppliedMaxRows();
      }

      @Override
      public boolean next() {
         // clear cache, the cache is used because a column in a ResultSet
         // can only be accessed once, if it is requested more than once,
         // we have to get the value from the cache
         for(int i = 0; i < row.length; i++) {
            row[i] = null;
         }

         return seq.next();
      }

      @Override
      public String getName(int col) {
         String name = paths.get(col);
         String alias0 = alias[col];

         if(alias0 != null) {
            return alias0;
         }

         return name;
      }

      @Override
      public Object getColumnValue(int col) {
         // check cache first
         if(row[col] != null) {
            return row[col];
         }

         return row[col] = seq.getObject(colmap[col]);
      }

      @Override
      public XMetaInfo getXMetaInfo(int col) {
         if(seq.getXMetaInfo(colmap[col]) == null) {
            String path = getColumn(col);

            return XSelection.this.getXMetaInfo(path);
         }

         return seq.getXMetaInfo(colmap[col]);
      }

      @Override
      public boolean rewind() {
         return seq.rewind();
      }

      @Override
      public boolean isRewindable() {
         return seq.isRewindable();
      }

      @Override
      public void cancel() {
         seq.cancel();
      }

      @Override
      public void close() {
         seq.close();
      }

      @Override
      public XTableColumnCreator getColumnCreator(int col) {
         return XObjectColumn.getCreator();
      }

      /**
       * Check if the path name matches the column name. Handles 'columnN'.
       */
      private boolean equalsColumnName(String pathname, String alias,
                                       String cname, int idx) {
         return pathname.equals(cname) ||
                pathname.equalsIgnoreCase("column" + idx) ||
                (alias != null && alias.length() > 0 && alias.equals(cname));
      }

      Object[] row; // cache current row
      String[] alias; // cache current row
      int[] colmap; // column map
      int ccount; // column count
      XTableNode seq; // base table node
   }

   /**
    * Convert a sequence node to a table with subtree expanded.
    * The implementation is as follows:
    * 1. For each subtree branch, we traverse the tree, and mark the number
    * of rows a branch would generate. For sequence node, the number of rows
    * in all children are summed into the number of rows for the sequence. For
    * non-sequence node, the number of rows are the product of all number of
    * rows of its children (this generates a permutation of all sub-branches).
    * 2. When constructing a row with a specified row index, we find the
    * node in the subtree by traversing down the path.
    *   a. We first try to adjust the row index so it would be the row index
    *      of the child when we traverse to the child. For sequence node, we
    *      simply subtract the #row from the index until we hit a child,
    *      and then traverse down into the child.
    *   b. For non-sequence node, we first find the product of all nodes from
    *      left to right that is less than the row index, and modulo the
    *      row index by that product. This reduces the row index to the first
    *      iteration without any permutation of the children on the right
    *      of the child.
    *      Second we divide the product of #row of all nodes to the left of
    *      the child, this reduces the row index to the row index of the child
    *      since it removes the effect of permutation of all the nodes on the
    *      left.
    * 3. We traverse done the tree, at each point, the row index on a branch
    *    is the row index without that branch, without any dependency on the
    *    nodes outside of the branch.
    */
   class ExpandedSeqTable extends Table {
      public ExpandedSeqTable(XSequenceNode seq) {
         this.seq = seq;

         XNode seqChild0 = seq.getChild(0); // optimization
         setName(seq.getName());
         ptree = new XNode(seq.getName());

         for(int i = 0; i < xpaths.length; i++) {
            String path = paths.get(i);
            int idx = path.lastIndexOf('@');

            if(idx >= 0) {
               path = path.substring(0, idx);

               // the notation node.@attr and node@attr are both used
               if(path.endsWith(".")) {
                  path = path.substring(0, path.length() - 1);
               }
            }

            path = (path.length() > 0) ? (seq.getName() + "." + path) :
               seq.getName();
            xpathstrs[i] = path;

            // @by larryl, parse the path into XNodePath by first looking
            // up the path on the data tree, and reverse it to create a
            // path, this way if there is dot in the node name, the path
            // will be handled properly
            XNode child = (seq.getChildCount() > 0) ?
               seqChild0.getNode(path) : null;

            if(child != null) {
               Vector<String> names = new Vector<>();
               boolean self = true;

               while(child != seq) {
                  if(self || !(child instanceof XSequenceNode)) {
                     names.add(child.getName());
                  }

                  child = child.getParent();
                  self = false;
               }

               xpaths[i] = new XNodePath();

               for(int k = names.size() - 1; k >= 0; k--) {
                  xpaths[i].add(names.get(k));
               }
            }
            else {
               xpaths[i] = XNodePath.parseSimplePath(path);
            }

            // builds the ptree, which is used to check if a branch is
            // included in the selection for efficiency
            XNode root = ptree;

            for(int k = 1; k < xpaths[i].getPathNodeCount(); k++) {
               // optimization, avoid too many getChild()
               String cname = xpaths[i].getPathNode(k).getName();
               child = root.getChild(cname);

               if(child == null) {
                  child = new XNode(cname);
                  root.addChild(child, false, false);
               }

               root = child;
            }
         }
      }

      /**
       * Set the sequence node for extracting data.
       */
      public synchronized void setSequenceNode(XSequenceNode seq) {
         this.seq = seq;
         closed = false; // new data node supplied, start a new
         rewind(); // start from beginning
      }

      @Override
      public int getAppliedMaxRows() {
         return seq.getAppliedMaxRows();
      }

      /**
       * Advance to the next row.
       */
      @Override
      public boolean next() {
         // advance to the next node on sequence if necessary
         if(currnode == null || ++subidx >= getRowCount(currnode)) {
            idx++;
            subidx = 0;

            if(idx >= seq.getChildCount()) {
               return false;
            }

            if(currnode != null) {
               currnode.removeAllChildren();
            }

            currnode = seq.getChild(idx);

            // mark the row count on the tree
            mark(currnode, ptree);
         }

         return true;
      }

      /**
       * Retrieve current row value.
       */
      @Override
      public Object getColumnValue(int col) {
         String path = paths.get(col);
         String attr = null;
         int aidx = path.indexOf('@');

         if(aidx >= 0) {
            attr = path.substring(aidx + 1);
         }

         // find the node at the specified row index, see comment for this class
         XNode node = findNode(currnode, subidx, xpaths[col], 0);

         return (node == null) ? null :
            (attr != null) ? node.getAttribute(attr) : node.getValue();
      }

      /**
       * Recursively mark the row count on a branch.
       */
      private int mark(XNode root, XNode prefix) {
         boolean seq = (root instanceof XSequenceNode);
         int prod = seq ? 0 : 1;
         XNode ptree2;
         int cnt = root.getChildCount();

         for(int i = 0; i < cnt; i++) {
            XNode child = root.getChild(i);

            if(seq) {
               prod += mark(child, prefix);
            }
            // ignore the branch that is not on the selection
            else if((ptree2 = prefix.getChild(child.getName())) != null) {
               prod *= mark(child, ptree2);
            }
         }

         setRowCount(root, prod);
         return prod;
      }

      /**
       * Find a node for the specified row index.
       */
      private XNode findNode(XNode root, int idx, XNodePath xpath, int pathidx)
      {
         // if a sequence node, find the child at the specified index
         if(root instanceof XSequenceNode) {
            int nchild = root.getChildCount();

            // @by larryl, if a branch as no child, and we are trying to get the
            // first row, we return a dummy node. This way, one row will be
            // created when a sub-branch is empty and is joint with the
            // main branch
            if(idx == 0 && nchild == 0) {
               return new XValueNode(root.getName());
            }

            Object tableMode = root.getAttribute("table.mode");

            // @by billh, fix customer bug bug1307377781696
            // performance optimization to 100 times faster
            if(tableMode == null) {
               tableMode = SIMPLE;

               for(int i = 0; i < nchild; i++) {
                  XNode c1 = root.getChild(i);
                  int nrows = getRowCount(c1);

                  if(nrows != 1) {
                     tableMode = COMPLEX;
                     break;
                  }
               }

               root.setAttribute("table.mode", tableMode);
            }

            if(tableMode == SIMPLE) {
               XNode c1 = root.getChild(idx);
               return findNode(c1, 0, xpath, pathidx);
            }

            for(int i = 0; i < nchild; i++) {
               XNode c1 = root.getChild(i);
               int nrows = getRowCount(c1);

               if(idx < nrows) {
                  return findNode(c1, idx, xpath, pathidx);
               }

               idx -= nrows;
            }

            // this is an internal error if row index is not found on a sequence
            throw new RuntimeException("Internal error: XML data corrupted");
         }

         // if we are at the last node on the path, there is no where to go
         if(pathidx >= xpath.getPathNodeCount() - 1) {
            return root;
         }

         XNode child = root.getChild(xpath.getPathNode(pathidx + 1).getName());

         if(child == null) {
            return null;
         }

         int childidx = root.getChildIndex(child);
         int nchild = root.getChildCount();

         if(nchild > 1) {
            // prodLeft is the product of row count of all nodes on the left of
            // the child. prodPerm is the product of all nodes from left to
            // right that has the largest value that is less than the index
            int prodLeft = 1, prodPerm = 1;

            for(int i = 0, prod = 1; i < (childidx + 1); i++) {
               XNode c1 = root.getChild(i);
               prod = prod * getRowCount(c1);

               if(i < childidx) {
                  prodLeft = prod;
               }

               prodPerm = prod;
            }

            // ajdust index so the index is the row index in the child
            idx = (idx % prodPerm) / prodLeft;
         }

         return findNode(child, idx, xpath, pathidx + 1);
      }

      // get the row count from a node
      private int getRowCount(XNode node) {
         Object prop = node.getAttribute("row.count");
         return (prop == null) ? 1 : ((Integer) prop).intValue();
      }

      // set the row count on a node
      private void setRowCount(XNode node, int cnt) {
         if(cnt > 1) {
            node.setAttribute("row.count", Integer.valueOf(cnt));
         }
      }

      /**
       * Start from beginning.
       */
      @Override
      public boolean rewind() {
         idx = -1;
         subidx = -1;
         currnode = null;

         return true;
      }

      /**
       * Allow rewind.
       */
      @Override
      public boolean isRewindable() {
         return true;
      }

      public synchronized boolean isClosed() {
         return closed;
      }

      @Override
      public synchronized void close() {
         if(currnode != null) {
            currnode.removeAllChildren();
         }

         if(seq != null) {
            seq.removeAllChildren();
         }

         closed = true;
      }

      private int idx = -1;
      private XSequenceNode seq;
      private XNodePath[] xpaths = new XNodePath[paths.size()];
      private String[] xpathstrs = new String[xpaths.length];
      private int subidx = -1;
      private XNode ptree;
      private XNode currnode;
      private boolean closed = false;
   }

   @Override
   @SuppressWarnings("unchecked")
   public Object clone() {
      XSelection select = null;

      try {
         select = (XSelection) super.clone();
      }
      catch(CloneNotSupportedException e) {
         // impossible
      }

      select.expanded = expanded;
      select.paths = (Vector<String>) paths.clone();
      select.opaths = (Vector<String>) opaths.clone();
      select.aliasmap = (Vector<String>) aliasmap.clone();
      select.expmap = (Vector<Boolean>) expmap.clone();
      select.metamap = Tool.deepCloneCollection(metamap);
      select.typemap = (HashMap<String, String>) typemap.clone();
      select.fmtmap = (HashMap<String, String>) fmtmap.clone();
      select.desmap = (HashMap<String, String>) desmap.clone();
      select.bmap = (Hashtable<String, String>) bmap.clone();
      select.fixedFmtMap = new HashMap<>(fixedFmtMap);
      select.indexmap = new HashMap<>(indexmap);

      if(lowerPaths != null) {
         select.lowerPaths = new ArrayList<>(lowerPaths);
      }

      return select;
   }

   /**
    * Check if the columns are identical
    */
   public boolean equalsColumns(XSelection that) {
      return Objects.equals(paths, that.paths);
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) return true;
      if(o == null || getClass() != o.getClass()) return false;
      XSelection that = (XSelection) o;
      return expanded == that.expanded && Objects.equals(paths, that.paths) &&
         Objects.equals(aliasmap, that.aliasmap) && Objects.equals(expmap, that.expmap) &&
         Objects.equals(bmap, that.bmap) && Objects.equals(metamap, that.metamap) &&
         Objects.equals(typemap, that.typemap) && Objects.equals(fmtmap, that.fmtmap) &&
         Objects.equals(desmap, that.desmap);
   }

   @Override
   public int hashCode() {
      return Objects.hash(expanded, paths, aliasmap, expmap, bmap, metamap, typemap, fmtmap, desmap);
   }

   static final String[] PRIMITIVE_TYPES = { "integer", "string", "long",
      "float", "double", "date", "boolean", "char", "byte", "short",
      "timeInstant", "time", "enum", "userDefined"};
   private static final Object COMPLEX = new Object();
   private static final Object SIMPLE = new Object();

   protected boolean expanded = true; // expand subtrees
   protected Vector<String> paths = new Vector<>(); // column path
   protected List<String> lowerPaths = new ArrayList<>(); // path in lower case
   protected Vector<String> aliasmap = new Vector<>(); // path -> alias
   // for 10.1 bc, path -> expression column? default is false
   private Vector<Boolean> expmap = new Vector<>();

   private Hashtable<String, String> bmap = new Hashtable<>(); // exp -> col
   protected Vector<String> opaths = new Vector<>(); // original path
   private Vector<XMetaInfo> metamap = new Vector<>(); //path -> meta
   protected Map<String, Integer> indexmap = new HashMap<>();

   private HashMap<String, String> typemap = new HashMap<>(); // path -> type
   private HashMap<String, String> fmtmap = new HashMap<>(); // path -> format
   private HashMap<String, String> desmap = new HashMap<>(); // path -> description
   private ExpandedSeqTable cachedSeqTable; // optimization

   private Map<String, Boolean> fixedFmtMap = new HashMap<>();
   protected transient BidiMap<String, String> pathAliases = new BidiMap<>();

   private static final Logger LOG =
      LoggerFactory.getLogger(XSelection.class);
}
