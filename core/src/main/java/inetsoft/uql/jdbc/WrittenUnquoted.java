/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import java.io.Serializable;
import java.util.*;
import java.util.function.UnaryOperator;

/**
 * The names of a stored sql text (a select column, a condition expression, a group by or
 * order by field, a table name, or the sql of a derived table) that were written unquoted. A
 * case-sensitive helper (postgresql, snowflake, exasol) stores such a name inside quotes in
 * the case it was written, which isn't the name the database reads when the name is written
 * unquoted, so the generated sql folds them (Bug #77643). The stored text doesn't change.
 * <p>
 * A name is the n-th quoted name ("...") of the text, string literals aside. The names are
 * recorded for one text: a text that changed since (e.g. renamed by the metadata step or
 * edited) is generated as it is stored.
 */
public final class WrittenUnquoted implements Serializable {
   /**
    * Create the record of a text.
    * @param text the stored text.
    * @param names the positions of the quoted names written unquoted.
    * @param aliases the positions of the names written unquoted that reference a select alias
    *                (of the same query, or of a derived table of its from clause).
    */
   public WrittenUnquoted(String text, int[] names, int[] aliases) {
      this.text = text;
      this.names = names == null ? new int[0] : names.clone();
      this.aliases = aliases == null ? new int[0] : aliases.clone();
   }

   /**
    * Create the record of a text, or <tt>null</tt> if no name was written unquoted.
    */
   public static WrittenUnquoted of(String text, List<Integer> names, List<Integer> aliases) {
      if(text == null || names.isEmpty() && aliases.isEmpty()) {
         return null;
      }

      return new WrittenUnquoted(text, names.stream().mapToInt(Integer::intValue).toArray(),
                                 aliases.stream().mapToInt(Integer::intValue).toArray());
   }

   /**
    * Check if no name was written unquoted. A parsed text has a record, also empty, a text
    * built after the parse (e.g. by the query editor) has none.
    */
   public boolean isEmpty() {
      return names.length == 0 && aliases.length == 0;
   }

   /**
    * Get the record with more names written unquoted.
    * @param more the positions of the names.
    */
   public WrittenUnquoted withNames(int[] more) {
      Set<Integer> all = new TreeSet<>();
      Arrays.stream(names).forEach(all::add);
      Arrays.stream(more).forEach(all::add);
      return new WrittenUnquoted(text, all.stream().mapToInt(Integer::intValue).toArray(), aliases);
   }

   /**
    * Get the positions of the names written unquoted, that don't reference an alias.
    */
   public int[] getNames() {
      return names.clone();
   }

   /**
    * Get the text the names were recorded for.
    */
   public String getText() {
      return text;
   }

   /**
    * Check if the record is of a text.
    */
   public boolean isFor(Object text) {
      return text != null && this.text.equals(text.toString());
   }

   /**
    * Get the record of a text, if it's the text of the record.
    */
   public static WrittenUnquoted of(WrittenUnquoted record, Object text) {
      return record != null && record.isFor(text) ? record : null;
   }

   /**
    * Get the record as an xml attribute value, e.g. "0,2,a3" (a for an alias reference).
    */
   public String toAttribute() {
      // a parsed text without a name written unquoted
      if(isEmpty()) {
         return EMPTY;
      }

      StringBuilder sb = new StringBuilder();

      for(int name : names) {
         sb.append(sb.length() > 0 ? "," : "").append(name);
      }

      for(int alias : aliases) {
         sb.append(sb.length() > 0 ? "," : "").append('a').append(alias);
      }

      return sb.toString();
   }

   /**
    * Read the record of a text from an xml attribute value, see toAttribute.
    * @return the record, or <tt>null</tt> if absent or malformed.
    */
   public static WrittenUnquoted fromAttribute(String text, String value) {
      if(text == null || value == null || value.isEmpty()) {
         return null;
      }

      if(EMPTY.equals(value)) {
         return new WrittenUnquoted(text, null, null);
      }

      List<Integer> names = new ArrayList<>();
      List<Integer> aliases = new ArrayList<>();

      try {
         for(String item : value.split(",")) {
            boolean alias = item.startsWith("a");
            int pos = Integer.parseInt(alias ? item.substring(1) : item);

            if(pos < 0) {
               return null;
            }

            (alias ? aliases : names).add(pos);
         }
      }
      catch(NumberFormatException ex) {
         return null;
      }

      return of(text, names, aliases);
   }

   /**
    * Fold the names written unquoted in the sql generated for the text. The quoted names of
    * the generated sql are matched with the quoted names of the text in order, by their
    * content (the most names that match in order), a name the generated sql doesn't have
    * (e.g. replaced by an alias, or written unquoted by the helper) is skipped.
    * @param generated the sql generated for the text.
    * @param quote the quote of the helper.
    * @param fold the name to generate in quotes for a name written unquoted.
    * @param aliasFold the sql to generate in place of a name written unquoted that references
    *                  an alias, e.g. the alias as it's generated, or <tt>null</tt> to keep it.
    * @return the sql with the names replaced.
    */
   public String apply(String generated, String quote, UnaryOperator<String> fold,
                       UnaryOperator<String> aliasFold)
   {
      if(generated == null || quote == null || quote.isEmpty()) {
         return generated;
      }

      List<int[]> stored = findNames(text, quote);
      List<int[]> found = findNames(generated, quote);
      int[] matches = match(text, stored, generated, found);
      Map<Integer, String> folded = new HashMap<>();

      for(int i = 0; i < stored.size(); i++) {
         String name = getName(text, stored.get(i));
         int match = matches[i];

         if(match < 0) {
            continue;
         }

         String nname = contains(names, i) ? quote + fold.apply(name) + quote :
            contains(aliases, i) ? aliasFold.apply(name) : null;

         if(nname != null && !nname.equals(quote + name + quote)) {
            folded.put(match, nname);
         }
      }

      if(folded.isEmpty()) {
         return generated;
      }

      StringBuilder sb = new StringBuilder();
      int last = 0;

      for(int j = 0; j < found.size(); j++) {
         String name = folded.get(j);

         if(name != null) {
            sb.append(generated, last, found.get(j)[0] - quote.length()).append(name);
            last = found.get(j)[1] + quote.length();
         }
      }

      return sb.append(generated.substring(last)).toString();
   }

   /**
    * Get the record of a text that renames this text keeping some of its names, e.g. the
    * metadata step resolving a column to its exact name ("t"."MixedCase" to "t".mixedcase).
    * A name of the new text is written unquoted if it's matched, in order and by its content,
    * with a name of this text written unquoted.
    * @param ntext the new text.
    * @param quote the quote of the helper.
    * @return the record of the new text, empty if it has no name written unquoted, or
    *         <tt>null</tt> if the quote is not known.
    */
   public WrittenUnquoted rename(String ntext, String quote) {
      if(ntext == null || quote == null || quote.isEmpty()) {
         return null;
      }

      if(text.equals(ntext)) {
         return this;
      }

      List<int[]> stored = findNames(text, quote);
      List<int[]> found = findNames(ntext, quote);
      int[] matches = match(text, stored, ntext, found);
      List<Integer> nnames = new ArrayList<>();
      List<Integer> naliases = new ArrayList<>();

      for(int i = 0; i < stored.size(); i++) {
         if(matches[i] >= 0 && contains(names, i)) {
            nnames.add(matches[i]);
         }
         else if(matches[i] >= 0 && contains(aliases, i)) {
            naliases.add(matches[i]);
         }
      }

      // the new text is of the parsed text, its record is kept, also empty
      return new WrittenUnquoted(ntext, nnames.stream().mapToInt(Integer::intValue).toArray(),
                                 naliases.stream().mapToInt(Integer::intValue).toArray());
   }

   /**
    * Match the quoted names of two texts by their content, keeping their order, so that the
    * most names match (a longest common subsequence). The names the two texts start and end
    * with in common are matched first, usually all of them. Above MAX_MATCH cells the rest is
    * matched greedily, in order.
    * @return the index in the names of the second text of each name of the first text, or -1.
    */
   private static int[] match(String text1, List<int[]> names1, String text2, List<int[]> names2) {
      int n = names1.size();
      int m = names2.size();
      int[] result = new int[n];
      Arrays.fill(result, -1);
      int start = 0;

      while(start < n && start < m && sameName(text1, names1.get(start), text2, names2.get(start))) {
         result[start] = start;
         start++;
      }

      int end1 = n;
      int end2 = m;

      while(end1 > start && end2 > start &&
         sameName(text1, names1.get(end1 - 1), text2, names2.get(end2 - 1)))
      {
         result[--end1] = --end2;
      }

      int rows = end1 - start;
      int cols = end2 - start;

      if(rows == 0 || cols == 0) {
         return result;
      }

      if((long) rows * cols > MAX_MATCH) {
         for(int i = start, next = start; i < end1; i++) {
            for(int j = next; j < end2; j++) {
               if(sameName(text1, names1.get(i), text2, names2.get(j))) {
                  result[i] = j;
                  next = j + 1;
                  break;
               }
            }
         }

         return result;
      }

      int[][] lcs = new int[rows + 1][cols + 1];

      for(int i = rows - 1; i >= 0; i--) {
         for(int j = cols - 1; j >= 0; j--) {
            lcs[i][j] = sameName(text1, names1.get(start + i), text2, names2.get(start + j)) ?
               lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
         }
      }

      for(int i = 0, j = 0; i < rows && j < cols;) {
         if(sameName(text1, names1.get(start + i), text2, names2.get(start + j))) {
            result[start + i++] = start + j++;
         }
         else if(lcs[i + 1][j] >= lcs[i][j + 1]) {
            i++;
         }
         else {
            j++;
         }
      }

      return result;
   }

   // check if two quoted names have the same content, without copying them
   private static boolean sameName(String text1, int[] name1, String text2, int[] name2) {
      int len = name1[1] - name1[0];
      return len == name2[1] - name2[0] && text1.regionMatches(name1[0], text2, name2[0], len);
   }

   /**
    * Find the quoted names of a sql text that are in a nested query, (select ...).
    * @return for each quoted name, whether it's in a nested query.
    */
   public static boolean[] getNestedNames(String sql, String quote) {
      List<int[]> found = findNames(sql, quote);
      boolean[] nested = new boolean[found.size()];
      Deque<Boolean> parens = new ArrayDeque<>();
      int depth = 0;
      int name = 0;
      int len = sql == null ? 0 : sql.length();

      for(int i = 0; i < len; i++) {
         char c = sql.charAt(i);

         if(name < found.size() && i == found.get(name)[0] - quote.length()) {
            nested[name] = depth > 0;
            i = found.get(name)[1] + quote.length() - 1;
            name++;
         }
         else if(c == '\'') {
            // a string literal, '' is a quote in it
            i++;

            while(i < len && !(sql.charAt(i) == '\'' && (i + 1 >= len || sql.charAt(i + 1) != '\''))) {
               i += sql.charAt(i) == '\'' ? 2 : 1;
            }
         }
         else if(c == '(') {
            int next = i + 1;

            while(next < len && Character.isWhitespace(sql.charAt(next))) {
               next++;
            }

            boolean query = sql.regionMatches(true, next, "select", 0, 6) &&
               (next + 6 >= len || !Character.isLetterOrDigit(sql.charAt(next + 6)));
            parens.push(query);
            depth += query ? 1 : 0;
         }
         else if(c == ')' && !parens.isEmpty()) {
            depth -= parens.pop() ? 1 : 0;
         }
      }

      return nested;
   }

   /**
    * Check if the last segment of the text, a column, was written unquoted.
    * @param column the column segment, in quotes as stored ("MixedCase").
    * @param quote the quote of the helper.
    */
   public boolean isLastNameWrittenUnquoted(String column, String quote) {
      List<int[]> found = findNames(text, quote);

      if(found.isEmpty() || quote == null || quote.isEmpty() ||
         !text.endsWith(column) || !column.equals(quote + getName(text, found.get(found.size() - 1)) + quote) ||
         found.get(found.size() - 1)[1] + quote.length() != text.length())
      {
         return false;
      }

      return contains(names, found.size() - 1);
   }

   /**
    * Find the quoted names of a sql text, string literals aside. A doubled quote in a name is
    * part of it.
    * @return the start and end of the content of each name, in order.
    */
   public static List<int[]> findNames(String sql, String quote) {
      List<int[]> result = new ArrayList<>();
      int len = sql == null ? 0 : sql.length();
      int i = 0;

      while(i < len) {
         if(sql.charAt(i) == '\'') {
            // a string literal, '' is a quote in it
            i++;

            while(i < len && !(sql.charAt(i) == '\'' &&
               (i + 1 >= len || sql.charAt(i + 1) != '\'')))
            {
               i += sql.charAt(i) == '\'' ? 2 : 1;
            }

            i++;
         }
         else if(sql.startsWith(quote, i)) {
            int start = i + quote.length();
            int end = sql.indexOf(quote, start);

            // a doubled quote is a quote in the name ("a""b"), not its end (#77661)
            while(end >= 0 && sql.startsWith(quote, end + quote.length())) {
               end = sql.indexOf(quote, end + 2 * quote.length());
            }

            if(end < 0) {
               break;
            }

            result.add(new int[] { start, end });
            i = end + quote.length();
         }
         else {
            i++;
         }
      }

      return result;
   }

   // the content of a quoted name
   private static String getName(String sql, int[] range) {
      return sql.substring(range[0], range[1]);
   }

   private static boolean contains(int[] arr, int value) {
      for(int item : arr) {
         if(item == value) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get the xml attribute of a record, " unquoted=..." or "" if none. An empty record is
    * written too: it tells a parsed text from a text built after the parse. The parser makes
    * one only for a helper that folds the names, so other helpers' xml doesn't have it.
    */
   public static String toXMLAttribute(WrittenUnquoted names) {
      return names == null ? "" : " unquoted=\"" + names.toAttribute() + "\"";
   }

   /**
    * Check if the sql is generated on this thread to be saved: the names written unquoted
    * are marked instead of folded, see mark and unmark.
    */
   public static boolean isMarking() {
      return MARKING.get() > 0;
   }

   /**
    * Generate sql to be saved, the names written unquoted are marked instead of folded.
    */
   public static <T> T marking(java.util.function.Supplier<T> action) {
      MARKING.set(MARKING.get() + 1);

      try {
         return action.get();
      }
      finally {
         MARKING.set(MARKING.get() - 1);
      }
   }

   /**
    * Check if the sql is generated on this thread as stored, without folding the names
    * written unquoted, e.g. the text of a searched case built at parse.
    */
   public static boolean isUnfolded() {
      return UNFOLDED.get() > 0;
   }

   /**
    * Generate sql with the names written unquoted as stored, see isUnfolded.
    */
   public static <T> T unfolded(java.util.function.Supplier<T> action) {
      UNFOLDED.set(UNFOLDED.get() + 1);

      try {
         return action.get();
      }
      finally {
         UNFOLDED.set(UNFOLDED.get() - 1);
      }
   }

   /**
    * Mark a name written unquoted in sql generated to be saved, see marking.
    * @param alias <tt>true</tt> if the name references an alias.
    */
   public static String mark(String name, boolean alias) {
      return (alias ? ALIAS_MARK : NAME_MARK) + name;
   }

   /**
    * Remove the marks from sql generated to be saved.
    */
   public static String unmark(String sql, String quote) {
      if(sql == null || quote == null || quote.isEmpty()) {
         return sql;
      }

      StringBuilder sb = new StringBuilder();
      int last = 0;

      // the marks are the first character of a quoted name, see mark
      for(int[] range : findNames(sql, quote)) {
         if(range[1] > range[0] && isMark(sql.charAt(range[0]))) {
            sb.append(sql, last, range[0]);
            last = range[0] + 1;
         }
      }

      return sb.append(sql.substring(last)).toString();
   }

   private static boolean isMark(char c) {
      return c == NAME_MARK || c == ALIAS_MARK;
   }

   /**
    * Get the record of the marked names of sql generated to be saved.
    * @return the record of the sql without the marks, or <tt>null</tt> if none is marked.
    */
   public static WrittenUnquoted ofMarked(String sql, String quote) {
      if(sql == null || quote == null || quote.isEmpty()) {
         return null;
      }

      List<Integer> names = new ArrayList<>();
      List<Integer> aliases = new ArrayList<>();
      List<int[]> found = findNames(sql, quote);

      for(int i = 0; i < found.size(); i++) {
         String name = getName(sql, found.get(i));

         if(name.startsWith(String.valueOf(NAME_MARK))) {
            names.add(i);
         }
         else if(name.startsWith(String.valueOf(ALIAS_MARK))) {
            aliases.add(i);
         }
      }

      return of(unmark(sql, quote), names, aliases);
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof WrittenUnquoted)) {
         return false;
      }

      WrittenUnquoted other = (WrittenUnquoted) obj;
      return text.equals(other.text) && Arrays.equals(names, other.names) &&
         Arrays.equals(aliases, other.aliases);
   }

   @Override
   public int hashCode() {
      return Objects.hash(text, Arrays.hashCode(names), Arrays.hashCode(aliases));
   }

   @Override
   public String toString() {
      return "WrittenUnquoted[" + text + ": " + toAttribute() + "]";
   }

   private final String text;
   private final int[] names;
   private final int[] aliases;

   // private use characters marking the names in sql generated to be saved
   private static final char NAME_MARK = '';
   private static final char ALIAS_MARK = '';
   // the attribute value of a record without a name, see toAttribute
   private static final String EMPTY = "-";
   // the most cells of the longest common subsequence of the names, see match
   private static final long MAX_MATCH = 250_000;
   private static final ThreadLocal<Integer> MARKING = ThreadLocal.withInitial(() -> 0);
   private static final ThreadLocal<Integer> UNFOLDED = ThreadLocal.withInitial(() -> 0);
}
