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
            copyQuoted(i, (JDBCSelection) select, i);
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
      quotedAliases.clear();
      columnSql.clear();
   }

   /**
    * Remove a selected column.
    * @param path tree node path selected as a table column.
    */
   @Override
   public boolean removeColumn(String path) {
      boolean result = super.removeColumn(path);
      tablemap.remove(path);

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

      return generateValidAlias(alias, alias, col);
   }

   /**
    * Generates a new alias that is not already present in the selection and adds it to the
    * selection. The generation doesn't change the alias of the column, it only records the
    * mapping (Bug #77712).
    *
    * @param key  the name of the column at this level, which the mapping is found by
    * @param name the original name of the column
    * @param col  the index of the column
    *
    * @return the new alias.
    */
   private String generateValidAlias(String key, String name, int col) {
      // the new alias must not be the output name of another column. Database names are
      // compared ignoring case, as an unquoted name is (Bug #77716)
      Set<String> outputNames = getOutputNames(col);
      String prefix = "ALIAS_";
      int counter = 0;
      String valias;

      do {
         valias = prefix + (counter++);
      }
      while(outputNames.contains(valias));

      newToOldAlias.put(valias, name);

      // two columns with the same name and no alias are told apart by their index
      if(oldToNewAlias.containsKey(key)) {
         colToNewAlias.put(col, valias);
      }
      else {
         oldToNewAlias.put(key, valias);
      }

      return valias;
   }

   /**
    * Get the names, in upper case, that the columns of this selection other than the
    * specified column output: the generated and inherited aliases, the aliases, and the
    * column names of the columns with no alias. An expression with no alias has no
    * stable output name.
    */
   private Set<String> getOutputNames(int except) {
      Set<String> names = new HashSet<>();

      for(String valias : newToOldAlias.keySet()) {
         names.add(valias.toUpperCase(Locale.ROOT));
      }

      for(int i = 0; i < getColumnCount(); i++) {
         if(i == except) {
            continue;
         }

         String alias = getAlias(i);

         if(alias != null && !alias.isEmpty()) {
            names.add(alias.toUpperCase(Locale.ROOT));
         }
         else if(!isExpression(i)) {
            String path = getColumn(i);
            // the column name, without the quotes of a quoted identifier
            String column = path.substring(path.lastIndexOf('.') + 1)
               .replaceAll("^[\"`\\[]|[\"`\\]]$", "");
            names.add(column.toUpperCase(Locale.ROOT));
         }
      }

      return names;
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
         String newAlias = colToNewAlias.get(col);

         if(newAlias == null) {
            newAlias = getNewAlias(getColumn(col));
         }

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
         // the column is output by the inherited name only if no other column outputs it
         // (Bug #77717)
         if(!getOutputNames(i).contains(subalias.toUpperCase(Locale.ROOT))) {
            newToOldAlias.put(subalias, name);
            oldToNewAlias.put(name, subalias);
         }
         else {
            String oname = name;

            // if name is "<table>.<column>", then strip out the table part
            if(oname.contains(".")) {
               oname = oname.substring(oname.indexOf(".") + 1);
            }

            // keyed by the name of this column, so another column with the same column name
            // doesn't find it (Bug #77713)
            generateValidAlias(name, oname, i);
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
      // the name is matched as is. A column of another table with the same column name is
      // another column (Bug #77713)
      return oldToNewAlias.get(col);
   }

   /**
    * Clear the original aliases.
    */
   public void clearOriginalAliases() {
      newToOldAlias.clear();
      oldToNewAlias.clear();
      colToNewAlias.clear();
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
    * Check if the first column with a path was written as a quoted identifier.
    * @param column the specified column.
    * @return <tt>true</tt> if quoted, <tt>false</tt> otherwise.
    * @see #isQuoted(int)
    */
   public boolean isQuoted(String column) {
      return isQuoted(indexOf(column));
   }

   /**
    * Check if a column was written as a quoted identifier (e.g. "x y") in the parsed SQL.
    * The quotes are not part of the column path, so they are restored when the SQL is
    * generated. The flag is kept by position, two columns may have the same path
    * ("MixedCase" and MixedCase, Bug #77573). It only describes the name it was set for: a
    * column replaced by another text (e.g. edited in the query editor) is generated as that
    * text is written. Code that qualifies a column or renames its table keeps the flag with
    * renameColumn.
    * @param col the column index.
    * @return <tt>true</tt> if quoted, <tt>false</tt> otherwise.
    */
   public boolean isQuoted(int col) {
      return getColumnQuote(col) != null;
   }

   // the quoting of a column, if it was set for the current name
   private ColumnQuote getColumnQuote(int col) {
      ColumnQuote quote = quoted.get(col);
      return quote != null && Objects.equals(quote.column(), getColumn(col)) ? quote : null;
   }

   /**
    * Replace the name of a column with a name of the same column, qualified by its table or
    * under a renamed table or qualifier (MixedCase to t.MixedCase, t.MixedCase to x.MixedCase),
    * keeping its quoting. Any other change of the name (setColumn) drops it, the new name is
    * generated as it is written.
    * @param col the column index.
    * @param path the new name.
    */
   public void renameColumn(int col, String path) {
      ColumnQuote quote = getColumnQuote(col);
      setColumn(col, path);

      if(quote != null) {
         quoted.put(col, new ColumnQuote(path, quote.segment()));
      }
   }

   /**
    * Replace a column with another text (e.g. an expression edited in the query editor). Its
    * quoted flag is dropped, also for the same text, the text is generated as it is written.
    * @see #renameColumn(int, String)
    */
   @Override
   public void setColumn(int idx, String col) {
      super.setColumn(idx, col);
      quoted.remove(idx);
      columnSql.remove(idx);
   }

   /**
    * Get the sql generated for a column in place of its text, for the parameter values of a
    * run (e.g. a scalar subquery of the select list with a sentinel parameter rewritten,
    * Bug #77620). The column keeps its text, which names it.
    * @param col the column index.
    * @return the sql, or <tt>null</tt> if not set for the current text of the column.
    */
   public String getColumnSQL(int col) {
      ColumnSql sql = columnSql.get(col);
      return sql != null && Objects.equals(sql.column(), getColumn(col)) ? sql.sql() : null;
   }

   /**
    * Set the sql generated for a column in place of its text, for the parameter values of a
    * run. It isn't saved.
    * @param col the column index.
    * @param sql the sql, or <tt>null</tt> to generate the text of the column.
    */
   public void setColumnSQL(int col, String sql) {
      if(sql == null) {
         columnSql.remove(col);
      }
      else {
         columnSql.put(col, new ColumnSql(getColumn(col), sql));
      }
   }

   /**
    * Get the column segment, as written, of the first column with a path.
    * @see #getQuotedColumn(int)
    */
   public String getQuotedColumn(String column) {
      return getQuotedColumn(indexOf(column));
   }

   /**
    * Get the column segment, as written, of a qualified quoted identifier (t."MixedCase").
    * @param col the column index.
    * @return the segment, or <tt>null</tt> for a bare quoted identifier or an unquoted column.
    */
   public String getQuotedColumn(int col) {
      ColumnQuote quote = getColumnQuote(col);
      return quote == null || quote.segment().isEmpty() ? null : quote.segment();
   }

   /**
    * Set every column with a path as written as a qualified quoted identifier.
    * @param segment the column segment as written, or <tt>null</tt> for a bare identifier.
    * @see #setQuoted(int, String)
    */
   public void setQuoted(String column, String segment) {
      for(int i = 0; i < getColumnCount(); i++) {
         if(Objects.equals(column, getColumn(i))) {
            setQuoted(i, segment);
         }
      }
   }

   /**
    * Set a column as written as a quoted identifier.
    * @param col the column index.
    * @param segment the column segment of a qualified quoted identifier (t."MixedCase") as
    *                written, or <tt>null</tt> for a bare identifier.
    */
   public void setQuoted(int col, String segment) {
      if(col >= 0) {
         quoted.put(col, new ColumnQuote(getColumn(col), segment == null ? "" : segment));
      }
   }

   /**
    * Set whether a column was written as a quoted identifier.
    * @param col the column index.
    * @param quoted <tt>true</tt> if quoted, <tt>false</tt> otherwise.
    */
   public void setQuoted(int col, boolean quoted) {
      if(!quoted) {
         this.quoted.remove(col);
      }
      else if(!isQuoted(col)) {
         setQuoted(col, (String) null);
      }
   }

   /**
    * Copy the quoting of a column of another selection.
    * @param col the column index in this selection.
    * @param from the selection to copy from.
    * @param fromCol the column index in the other selection.
    */
   public void copyQuoted(int col, JDBCSelection from, int fromCol) {
      ColumnQuote quote = from.getColumnQuote(fromCol);

      if(quote != null && col >= 0) {
         quoted.put(col, quote);
      }
      else {
         quoted.remove(col);
      }

      // the quoting of the alias goes with the alias it was recorded for
      AliasQuote aliasQuote = from.quotedAliases.get(fromCol);

      if(aliasQuote != null && col >= 0) {
         quotedAliases.put(col, aliasQuote);
      }
      else {
         quotedAliases.remove(col);
      }
   }

   /**
    * Check if the alias of a column was written as a quoted identifier (as "A") or not
    * (as A). A database that folds unquoted names (e.g. postgresql) gives the two other
    * names, and the alias is stored without its quotes.
    * @param col the column index.
    * @return <tt>TRUE</tt> if quoted, <tt>FALSE</tt> if not, or <tt>null</tt> if not known:
    *         not recorded (e.g. a query saved before it was), or the alias has changed since.
    */
   public Boolean isAliasQuoted(int col) {
      AliasQuote aliasQuote = quotedAliases.get(col);

      return aliasQuote == null || !Objects.equals(aliasQuote.alias(), getAlias(col)) ?
         null : aliasQuote.quoted();
   }

   /**
    * Record whether the current alias of a column was written as a quoted identifier.
    * @param col the column index.
    * @param quoted <tt>TRUE</tt> if quoted, <tt>FALSE</tt> if not, or <tt>null</tt> if not
    *               known.
    */
   public void setAliasQuoted(int col, Boolean quoted) {
      if(quoted == null) {
         quotedAliases.remove(col);
      }
      else if(col >= 0) {
         quotedAliases.put(col, new AliasQuote(getAlias(col), quoted));
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
    * Remove a selected column, the quoted flags and aggregates after it move up.
    */
   @Override
   public boolean removeColumn(int idx) {
      boolean removed = super.removeColumn(idx);

      if(removed) {
         quotedAggregates = removeIndex(quotedAggregates, idx);
         quoted = removeIndex(quoted, idx);
         quotedAliases = removeIndex(quotedAliases, idx);
         columnSql = removeIndex(columnSql, idx);
      }

      return removed;
   }

   // remove the entry of a removed column, the entries after it move up
   private static <V> TreeMap<Integer, V> removeIndex(TreeMap<Integer, V> map, int idx) {
      if(map.isEmpty() || map.lastKey() < idx) {
         return map;
      }

      TreeMap<Integer, V> nmap = new TreeMap<>();

      for(Map.Entry<Integer, V> entry : map.entrySet()) {
         int col = entry.getKey();

         if(col != idx) {
            nmap.put(col > idx ? col - 1 : col, entry.getValue());
         }
      }

      return nmap;
   }

   /**
    * Set whether every column with a path was written as a quoted identifier.
    * @param column the specified column.
    * @param quoted <tt>true</tt> if quoted, <tt>false</tt> otherwise.
    * @see #setQuoted(int, boolean)
    */
   public void setQuoted(String column, boolean quoted) {
      for(int i = 0; i < getColumnCount(); i++) {
         if(Objects.equals(column, getColumn(i))) {
            setQuoted(i, quoted);
         }
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
      select.colToNewAlias = new HashMap<>(colToNewAlias);
      select.aggregates = (HashSet) aggregates.clone();
      select.quoted = new TreeMap<>(quoted);
      select.quotedAggregates = new TreeMap<>(quotedAggregates);
      select.quotedAliases = new TreeMap<>(quotedAliases);
      select.columnSql = new TreeMap<>(columnSql);

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
         Objects.equals(quotedAggregates, that.quotedAggregates) &&
         Objects.equals(quotedAliases, that.quotedAliases) &&
         Objects.equals(columnSql, that.columnSql);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), tablemap, aggregates, quoted, quotedAggregates,
                          quotedAliases, columnSql);
   }

   private HashMap<String, String> tablemap = new HashMap(); // path -> table (String)
   // valias -> alias, generated in this query or base/sub queries
   private Map<String, String> newToOldAlias = new HashMap<>();
   // alias -> valias, generated in this query or base/sub queries
   private Map<String, String> oldToNewAlias = new HashMap<>();
   // column index -> valias, generated for a column whose name is mapped for another column
   private Map<Integer, String> colToNewAlias = new HashMap<>();
   private HashSet<String> aggregates = new HashSet<>(); // aggregates
   // index of a column written as a quoted identifier -> the name and quoted column segment,
   // by position as two columns may have the same path (Bug #77573)
   private TreeMap<Integer, ColumnQuote> quoted = new TreeMap<>();
   // column index of an aggregate of a qualified quoted column -> the column segment
   private TreeMap<Integer, String> quotedAggregates = new TreeMap<>();
   // index of a column -> the quoting of its alias, kept with the alias it was recorded for
   private TreeMap<Integer, AliasQuote> quotedAliases = new TreeMap<>();
   // index of a column -> the sql generated in place of its text for a run, kept with the text
   // it was set for (Bug #77620)
   private TreeMap<Integer, ColumnSql> columnSql = new TreeMap<>();
   private boolean plan = false; // plan flag

   private record AliasQuote(String alias, boolean quoted) implements java.io.Serializable {
   }

   // the name a column was written quoted as, and its column segment ("" if bare)
   private record ColumnQuote(String column, String segment) implements java.io.Serializable {
   }

   // the text of a column and the sql generated in its place
   private record ColumnSql(String column, String sql) implements java.io.Serializable {
   }
}
