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
package inetsoft.uql.jdbc;

import inetsoft.uql.path.XSelection;
import inetsoft.uql.util.XUtil;

import java.util.*;

/**
 * The JDBCSelection object contains information in the SQL select
 * column list, and store the table name for each selected column.
 * It is used in UniformSQL
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class JDBCSelection extends XSelection {
   /**
    * Check if is a valid alias.
    * @hidden
    */
   public static boolean isValidAlias(String alias, SQLHelper helper) {
      return helper.isValidAlias(alias);
   }

   /**
    * Default constructor.
    */
   public JDBCSelection() {
      super();
   }

   /**
    * Construct from XSelection.
    */
   public JDBCSelection(XSelection select) {
      expanded = select.isExpandSubtree();

      for(int i = 0; i < select.getColumnCount(); i++) {
         String path = select.getColumn(i);

         paths.addElement(path);
         opaths.addElement("");
         indexmap.clear();
         lowerPaths = null;
         setAlias(i, select.getAlias(i));
         setConversion(path, select.getType(path), select.getFormat(path));

         if(select instanceof JDBCSelection) {
            setTable(path, ((JDBCSelection) select).getTable(path));
            setDescription(path, select.getDescription(path));
            copyQuoted(path, (JDBCSelection) select, path);
            setQuotedAggregate(i, ((JDBCSelection) select).getQuotedAggregate(i));
         }
      }
   }

   /**
    * Check if the selection is a plan selection for displaying sql only.
    * @return <tt>true</tt> if is a plan selection, <tt>false</tt> otherwise.
    */
   public boolean isPlan() {
      return plan;
   }

   /**
    * Set the plan flag.
    * @param plan <tt>true</tt> if is a plan selection, <tt>false</tt> otherwise.
    */
   public void setPlan(boolean plan) {
      this.plan = plan;
   }

   /**
    * Set the table name of the path.
    * If the table is not null, the path is a column in the table.
    */
   public void setTable(String path, String table) {
      if(table == null) {
         tablemap.remove(path);
      }
      else {
         tablemap.put(path, table);
      }
   }

   /**
    * Get the table name of the path.
    * If the table is not null, the path is a column in the table.
    */
   public String getTable(String path) {
      // for 10.1 bc, a expression column is not table column
      if(isExpression(path)) {
         return null;
      }

      return tablemap.get(path);
   }

   /**
    * Remove all components from the path.
    */
   @Override
   public void clear() {
      clear(true);
   }

   /**
    * Remove all components from the path.
    */
   public void clear(boolean tables) {
      super.clear();

      if(tables) {
         tablemap.clear();
      }

      quoted.clear();
      quotedAggregates.clear();
   }

   /**
    * Remove a selected column.
    * @param path tree node path selected as a table column.
    */
   @Override
   public boolean removeColumn(String path) {
      boolean result = super.removeColumn(path);
      tablemap.remove(path);
      quoted.remove(path);

      return result;
   }

   /**
    * Get a valid alias.
    * @return the valid alias.
    * @hidden
    */
   public String getValidAlias(int col, String alias, SQLHelper helper) {
      if(plan || isValidAlias(alias, helper)) {
         return alias;
      }

      String existingAlias = getNewAlias(alias);

      if(existingAlias == null) {
         existingAlias = getNewAlias(paths.get(col));
      }

      if(existingAlias != null) {
         return existingAlias;
      }

      return generateValidAlias(alias, col);
   }

   /**
    * Generates a new alias that is not already present in the selection and adds it to the
    * selection.
    *
    * @param name the original name of the column
    * @param col  the index of the column
    *
    * @return the new alias.
    */
   private String generateValidAlias(String name, int col) {
      String prefix = "ALIAS_";
      int counter = 0;
      String valias;

      do {
         valias = prefix + (counter++);
      }
      while(aliasmap.contains(valias) || newToOldAlias.containsKey(valias));

      newToOldAlias.put(valias, name);
      oldToNewAlias.put(name, valias);
      String oalias = aliasmap.get(col);

      // maintain alias
      if(oalias == null || oalias.length() == 0) {
         aliasmap.set(col, name);
         pathAliases = null;
      }

      return valias;
   }

   /**
    * Get the generated alias for a column (in getValidAlias). An alias is generated when
    * the column alias/name is not a valid name in sql.
    */
   public String getColumnAlias(String col) {
      if(col != null) {
         return oldToNewAlias.getOrDefault(col, null);
      }

      return null;
   }

   /**
    * Get a valid alias.
    * @param col the column index.
    * @return column alias.
    * @hidden
    */
   public String getValidAlias(int col, SQLHelper helper) {
      String alias = getAlias(col);

      if(alias == null) {
         final String newAlias = getNewAlias(getColumn(col));

         if(newAlias != null) {
            return newAlias;
         }
      }

      return getValidAlias(col, alias, helper);
   }

   /**
    * Inherit an alias from a depending sql clause.
    *
    * @param i        the index that is aliased
    * @param subalias the alias of the depending sql clause
    */
   public void inheritAlias(int i, String subalias) {
      String name = getAlias(i);

      if(name == null) {
         name = paths.get(i);
      }

      if(name != null) {
         if(!newToOldAlias.containsKey(subalias)) {
            newToOldAlias.put(subalias, name);
            oldToNewAlias.put(name, subalias);
         }
         else {
            // if name is "<table>.<column>", then strip out the table part
            if(name.contains(".")) {
               name = name.substring(name.indexOf(".") + 1);
            }

            generateValidAlias(name, i);
         }
      }
   }

   public void inheritAlias(String originalName, String subalias) {
      newToOldAlias.put(subalias, originalName);
      oldToNewAlias.put(originalName, subalias);
   }

   /**
    * Get the original alias.
    * @param alias the specified alias.
    */
   public String getOriginalAlias(String alias) {
      if(newToOldAlias.containsKey(alias)) {
         return newToOldAlias.get(alias);
      }

      // check if the column is mapped to an alias in sub-query (which would also be used
      // in this query as the actual column name). (45764)
      if(alias.contains(".ALIAS_")) {
         int dot = alias.indexOf(".ALIAS_");
         String rootAlias = alias.substring(dot + 1);

         if(newToOldAlias.containsKey(rootAlias)) {
            return newToOldAlias.get(rootAlias);
         }
      }

      return alias;
   }

   /**
    * Get the new alias.
    *
    * @param col the specified alias.
    *
    * @return the new alias if a new alias mapping exists, null otherwise.
    */
   public String getNewAlias(String col) {
      if(oldToNewAlias.containsKey(col)) {
         return oldToNewAlias.get(col);
      }

      // check if the column is mapped to an alias in sub-query (which would also be used
      // in this query as the actual column name). (45764)
      if(col.contains(".")) {
         int dot = col.indexOf(".");
         String rootCol = col.substring(dot + 1);

         if(oldToNewAlias.containsKey(rootCol)) {
            return oldToNewAlias.get(rootCol);
         }
      }

      return null;
   }

   /**
    * Clear the original aliases.
    */
   public void clearOriginalAliases() {
      newToOldAlias.clear();
      oldToNewAlias.clear();
   }

   /**
    * Check if a column is an aggregate.
    * @param column the specified column.
    * @return <tt>true</tt> if an aggregate, <tt>false</tt> otherwise.
    */
   public boolean isAggregate(String column) {
      boolean contained = aggregates.contains(column);

      if(contained) {
         return true;
      }

      if(column == null) {
         return false;
      }

      if(!column.endsWith(")")) {
         return false;
      }

      int index = column.indexOf('(');

      if(index <= 0) {
         return false;
      }

      String func = column.substring(0, index);
      return XUtil.isAggregateFunction(func);
   }

   /**
    * Set whether a column is an aggregate.
    * @param column the specified column.
    * @param aggregate <tt>true</tt> if an aggregate, <tt>false</tt> otherwise.
    */
   public void setAggregate(String column, boolean aggregate) {
      if(aggregate) {
         aggregates.add(column);
      }
      else {
         aggregates.remove(column);
      }
   }

   public boolean hasAggregate() {
      return !aggregates.isEmpty();
   }

   /**
    * Check if a column was written as a quoted identifier (e.g. "x y") in the parsed SQL.
    * The quotes are not part of the column path, so they are restored when the SQL is
    * generated.
    * @param column the specified column.
    * @return <tt>true</tt> if quoted, <tt>false</tt> otherwise.
    */
   public boolean isQuoted(String column) {
      return quoted.containsKey(column);
   }

   /**
    * Get the column segment, as written, of a qualified quoted identifier (t."MixedCase").
    * @return the segment, or <tt>null</tt> for a bare quoted identifier or an unquoted column.
    */
   public String getQuotedColumn(String column) {
      String seg = quoted.get(column);
      return seg == null || seg.isEmpty() ? null : seg;
   }

   /**
    * Set a column as written as a qualified quoted identifier.
    * @param segment the column segment as written, or <tt>null</tt> for a bare identifier.
    */
   public void setQuoted(String column, String segment) {
      quoted.put(column, segment == null ? "" : segment);
   }

   /**
    * Copy the quoting of a column of another selection.
    */
   public void copyQuoted(String column, JDBCSelection from, String fromColumn) {
      if(from.isQuoted(fromColumn)) {
         setQuoted(column, from.getQuotedColumn(fromColumn));
      }
      else {
         quoted.remove(column);
      }
   }

   /**
    * Get the column segment, as written, of the qualified quoted column that is the only
    * argument of the aggregate at a position (MixedCase for sum(t."MixedCase")). The stored
    * text of the aggregate doesn't show the quotes on a case-sensitive helper, which quotes
    * every segment, and two columns may have the same text (sum(t.MixedCase) and
    * sum(t."MixedCase")), so it is kept by position.
    * @param col the column index.
    * @return the segment, or <tt>null</tt> if not recorded, which doesn't mean unquoted
    *         (e.g. a query saved before it was recorded).
    */
   public String getQuotedAggregate(int col) {
      return quotedAggregates.get(col);
   }

   /**
    * Set the column segment, as written, of the qualified quoted column that is the only
    * argument of the aggregate at a position.
    * @param col the column index.
    * @param segment the segment, or <tt>null</tt> to clear it.
    */
   public void setQuotedAggregate(int col, String segment) {
      if(segment == null || segment.isEmpty()) {
         quotedAggregates.remove(col);
      }
      else if(col >= 0) {
         quotedAggregates.put(col, segment);
      }
   }

   /**
    * Remove a selected column, the quoted aggregates after it move up.
    */
   @Override
   public boolean removeColumn(int idx) {
      boolean removed = super.removeColumn(idx);

      if(removed && !quotedAggregates.isEmpty()) {
         TreeMap<Integer, String> naggs = new TreeMap<>();

         for(Map.Entry<Integer, String> entry : quotedAggregates.entrySet()) {
            int col = entry.getKey();

            if(col != idx) {
               naggs.put(col > idx ? col - 1 : col, entry.getValue());
            }
         }

         quotedAggregates = naggs;
      }

      return removed;
   }

   /**
    * Set whether a column was written as a quoted identifier.
    * @param column the specified column.
    * @param quoted <tt>true</tt> if quoted, <tt>false</tt> otherwise.
    */
   public void setQuoted(String column, boolean quoted) {
      if(quoted) {
         this.quoted.putIfAbsent(column, "");
      }
      else {
         this.quoted.remove(column);
      }
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return super.toString(true) + "[tables: " + tablemap + "][aggregates: " +
         aggregates + "]";
   }

   /**
    * Get the identifier.
    */
   @Override
   public String toIdentifier() {
      return super.toIdentifier() + "[valias:" + newToOldAlias + ", tmap: " +
         tablemap + "]";
   }

   /**
    * Clone the selection.
    */
   @Override
   public JDBCSelection clone() {
      JDBCSelection select = (JDBCSelection) super.clone();

      select.expanded = expanded;
      select.tablemap = (HashMap<String, String>) tablemap.clone();
      select.newToOldAlias = new HashMap<>(newToOldAlias);
      select.oldToNewAlias = new HashMap<>(oldToNewAlias);
      select.aggregates = (HashSet) aggregates.clone();
      select.quoted = new HashMap<>(quoted);
      select.quotedAggregates = new TreeMap<>(quotedAggregates);

      return select;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) return true;
      if(o == null || getClass() != o.getClass()) return false;
      if(!super.equals(o)) return false;
      JDBCSelection that = (JDBCSelection) o;
      return Objects.equals(tablemap, that.tablemap) && Objects.equals(aggregates, that.aggregates) &&
         Objects.equals(quoted, that.quoted) &&
         Objects.equals(quotedAggregates, that.quotedAggregates);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), tablemap, aggregates, quoted, quotedAggregates);
   }

   private HashMap<String, String> tablemap = new HashMap(); // path -> table (String)
   // valias -> alias, generated in this query or base/sub queries
   private Map<String, String> newToOldAlias = new HashMap<>();
   // alias -> valias, generated in this query or base/sub queries
   private Map<String, String> oldToNewAlias = new HashMap<>();
   private HashSet<String> aggregates = new HashSet<>(); // aggregates
   // columns written as quoted identifiers -> the quoted column segment ("" if bare)
   private HashMap<String, String> quoted = new HashMap<>();
   // column index of an aggregate of a qualified quoted column -> the column segment
   private TreeMap<Integer, String> quotedAggregates = new TreeMap<>();
   private boolean plan = false; // plan flag
}
