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
package inetsoft.report.composition.execution.reliability;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The E_ws script corpus of the reliability harness (Testing #77123): 979 unique expression
 * column and condition bodies found in the test assets, their column references mapped onto
 * the standard table of {@link RelPipeline} so most of them compute real values.
 */
public final class RelCorpus {
   private RelCorpus() {
   }

   /**
    * @param script the body with its column references renamed onto the standard table.
    * @param scope  "vs" or "ws", the kind of asset it was found in.
    * @param kind   "expr", "expr?" (a column whose sql flag was not found) or "cond".
    * @param cls    what the harness expects of it.
    */
   public record Entry(String script, String scope, String kind, Kind cls) {
   }

   public enum Kind {
      /** reads only the standard table and known globals */
      PURE,
      /** declares a var/let/const */
      USES_VAR,
      /** assigns an undeclared global or a host member, or changes a prototype */
      SIDE_EFFECT,
      /** references a name the standard table and the known globals do not have */
      NEEDS_CONTEXT,
      /** an SQL expression (runs as a script, usually as an error) */
      SQL
   }

   public static List<Entry> load() {
      List<Entry> list = corpus;

      if(list == null) {
         corpus = list = load0();
      }

      return list;
   }

   private static List<Entry> load0() {
      try(InputStream in = RelCorpus.class.getResourceAsStream("ws-corpus.json")) {
         String[][] rows = new ObjectMapper().readValue(in, String[][].class);
         List<Entry> list = new ArrayList<>();

         for(String[] row : rows) {
            String script = rename(row[0]);
            list.add(new Entry(script, row[1], row[2], classify(script)));
         }

         return Collections.unmodifiableList(list);
      }
      catch(Exception ex) {
         throw new IllegalStateException("cannot read the corpus", ex);
      }
   }

   /**
    * Map the column references of a corpus body onto the standard table: a name of the
    * standard table is kept, a date-looking name becomes day, a numeric-looking one value, and
    * any other one name. The map is by distinct name, so a body reads one standard column for
    * each of its columns.
    */
   public static String rename(String script) {
      Map<String, String> map = new HashMap<>();
      Matcher matcher = FIELD_REF.matcher(script);
      StringBuilder out = new StringBuilder();

      while(matcher.find()) {
         String mapped = map.computeIfAbsent(matcher.group(3), RelCorpus::standardColumn);
         matcher.appendReplacement(out, Matcher.quoteReplacement(
            matcher.group(1) + matcher.group(2) + mapped + matcher.group(2) + "]"));
      }

      matcher.appendTail(out);
      return out.toString();
   }

   private static String standardColumn(String column) {
      String lower = column.toLowerCase(Locale.ROOT);

      if(STANDARD.contains(lower)) {
         return lower;
      }

      if(DATE_LIKE.matcher(lower).find()) {
         return "day";
      }

      if(NUMBER_LIKE.matcher(lower).find()) {
         return "value";
      }

      return "name";
   }

   public static Kind classify(String script) {
      if(SQL_LIKE.matcher(script).find()) {
         return Kind.SQL;
      }

      String code = stripStrings(script);
      Set<String> declared = new HashSet<>();
      collect(DECL, code, declared);
      collect(FUNCTION_NAME, code, declared);
      collect(CATCH, code, declared);
      collect(ARROW_PARAM, code, declared);
      Matcher params = FUNCTION_PARAMS.matcher(code);

      while(params.find()) {
         for(String p : params.group(1).split(",")) {
            if(!p.isBlank()) {
               declared.add(p.trim());
            }
         }
      }

      Set<String> assigned = new HashSet<>();
      collect(ASSIGN, code, assigned);
      assigned.removeAll(declared);
      assigned.removeAll(KEYWORDS);
      boolean sideEffect = !assigned.isEmpty() || HOST_WRITE.matcher(code).find();
      Set<String> free = new LinkedHashSet<>();
      Matcher ids = IDENTIFIER.matcher(code);

      while(ids.find()) {
         String id = ids.group(1);

         if(!declared.contains(id) && !assigned.contains(id) && !KEYWORDS.contains(id) &&
            !GLOBALS.contains(id))
         {
            free.add(id);
         }
      }

      if(!free.isEmpty()) {
         return Kind.NEEDS_CONTEXT;
      }

      if(sideEffect) {
         return Kind.SIDE_EFFECT;
      }

      return DECL.matcher(code).find() ? Kind.USES_VAR : Kind.PURE;
   }

   /**
    * @return the undeclared names the body assigns and also reads, i.e. implicit globals whose
    * value one execution may pass to the next.
    */
   public static Set<String> implicitGlobals(String script) {
      String code = stripStrings(script);
      Set<String> declared = new HashSet<>();
      collect(DECL, code, declared);
      collect(FUNCTION_NAME, code, declared);
      Set<String> assigned = new TreeSet<>();
      collect(ASSIGN, code, assigned);
      assigned.removeAll(declared);
      assigned.removeAll(KEYWORDS);
      assigned.removeIf(name -> {
         Matcher uses = Pattern.compile("(?<![\\w$.])" + Pattern.quote(name) + "(?![\\w$])")
            .matcher(code);
         int n = 0;

         while(uses.find()) {
            n++;
         }

         return n < 2;
      });
      return assigned;
   }

   /**
    * @return the body with its comments removed and its string literals emptied.
    */
   static String stripStrings(String script) {
      String s = script.replaceAll("(?s)/\\*.*?\\*/", " ");
      s = s.replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"");
      s = s.replaceAll("'(?:\\\\.|[^'\\\\])*'", "''");
      return s.replaceAll("//[^\\n]*", " ");
   }

   private static void collect(Pattern pattern, String code, Set<String> into) {
      Matcher matcher = pattern.matcher(code);

      while(matcher.find()) {
         into.add(matcher.group(1));
      }
   }

   // field['X'], field["X"], field[-1]['X']
   private static final Pattern FIELD_REF = Pattern.compile(
      "(field\\s*(?:\\[\\s*-?\\d+\\s*\\]\\s*)?\\[\\s*)(['\"])(.*?)\\2\\s*\\]");
   private static final Set<String> STANDARD = Set.of("id", "value", "name", "day", "flag");
   private static final Pattern DATE_LIKE = Pattern.compile(
      "date|year|month|day|time|quarter|week|hour|minute|second");
   private static final Pattern NUMBER_LIKE = Pattern.compile(
      "sum|count|avg|average|max|min|total|amount|price|cost|sales|qty|quantity|num|value|" +
      "profit|revenue|rate|percent|population|area|duration|score|weight|size|age|discount|" +
      "budget|margin|income|salary|balance|unit|rto|_id|\\bid\\b|range|altitude|returned|" +
      "purchased|measure");
   private static final Pattern SQL_LIKE = Pattern.compile(
      "(?i)\\bcase\\s+when\\b|\\bover\\s*\\(|\\binterval\\s+['\\d]|^\\s*--|" +
      "\\bextract\\s*\\(\\s*\\w+\\s+from\\b|\\b(date_add|date_sub|concat|length|format|" +
      "row_number|cume_dist|lead|lag|dateadd|date_part|substring|coalesce|cast|to_char|nvl)" +
      "\\s*\\(|\\bselect\\b[\\s\\S]*\\bfrom\\b|[\\]']\\s+hours?\\b");
   private static final Pattern DECL =
      Pattern.compile("\\b(?:var|let|const)\\s+([A-Za-z_$][\\w$]*)");
   private static final Pattern FUNCTION_NAME =
      Pattern.compile("\\bfunction\\s+([A-Za-z_$][\\w$]*)");
   private static final Pattern FUNCTION_PARAMS =
      Pattern.compile("\\bfunction\\s*[\\w$]*\\s*\\(([^)]*)\\)");
   private static final Pattern ARROW_PARAM = Pattern.compile("([A-Za-z_$][\\w$]*)\\s*=>");
   private static final Pattern CATCH =
      Pattern.compile("\\bcatch\\s*\\(\\s*([A-Za-z_$][\\w$]*)");
   // an identifier assigned at a statement start, as the corpus scan finds one
   private static final Pattern ASSIGN = Pattern.compile(
      "(?:^|[;{}\\n()]|\\belse)\\s*([A-Za-z_$][\\w$]*)\\s*(?:[+\\-*/%]?=)(?!=)");
   private static final Pattern HOST_WRITE = Pattern.compile(
      "\\b(?:parameter|field|row|CALC|Math|Date|Array|Object|String|Number)" +
      "\\s*(?:\\.[\\w$]+|\\[[^\\]]*\\])+\\s*(?:[+\\-*/%]?=)(?!=)|\\.prototype\\b");
   // a name that is not a member (after a dot) or an object literal key
   private static final Pattern IDENTIFIER =
      Pattern.compile("(?<![\\w$.])([A-Za-z_$][\\w$]*)(?![\\w$])(?!\\s*:)");
   private static final Set<String> KEYWORDS = Set.of((
      "var let const function return if else for while do switch case break continue new " +
      "typeof instanceof in of this null true false undefined try catch finally throw delete " +
      "void with default").split(" "));
   private static final Set<String> GLOBALS = Set.of((
      // host names and standard globals
      "field row parameter CALC Math Date String Number Boolean Array Object JSON RegExp " +
      "Error TypeError parseInt parseFloat isNaN isFinite NaN Infinity encodeURIComponent " +
      "decodeURIComponent escape unescape " +
      // library functions of the worksheet script env
      "isNull isDate isNumber isArray dateAdd dateDiff datePart parseDate formatDate " +
      "formatNumber now today year month day hour minute second weekday quarter toList " +
      "rowList mapList sum average avg count max min trim log pmt rand randbetween").split(" "));

   private static volatile List<Entry> corpus;
}
