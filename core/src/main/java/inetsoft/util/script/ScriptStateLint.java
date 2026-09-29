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
package inetsoft.util.script;

import inetsoft.report.script.TableRow;
import inetsoft.uql.XTable;
import inetsoft.uql.util.XUtil;
import inetsoft.util.script.graal.ScriptScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Warns once per node about a worksheet script that reads script state before it writes it
 * in the same evaluation, so its value comes from an earlier evaluation (an accumulator such
 * as {@code var acc = (acc || 0) + field['Sales']}). Such a value is not reliable: it is shared
 * across tables and runs on one env with the script context pool off, and it resets at every
 * pooled batch with the pool on (Feature #77123, context-pool brief §5 P2). Detection only; it
 * never changes how a script runs. The exception is a top-level var of an expression column's
 * formula, which its table owns (Testing #77123): it is not reported, except, with the pool
 * on, when it is assigned an array, object or function, which is kept only within one batch
 * ({@link #checkColumn(String, Object, XTable, int, String, String, String, Set, boolean)}).
 *
 * <p>The detector is a token-level lexer (comments, strings, templates, regex vs division) with
 * two rules:
 * <ul>
 *    <li><b>R1</b>: a declared top-level {@code var} (or a depth-0 {@code let}/{@code const} with
 *    an initializer, which #76980 rewrites to {@code var}) is read before its first top-level
 *    write, e.g. {@code var x = x || 0}, {@code var n; n++}.</li>
 *    <li><b>R2</b>: an undeclared name that the script assigns at top level is read before that
 *    first write, e.g. {@code total = (total || 0) + v}, {@code count++}.</li>
 * </ul>
 * No finding for reads inside nested functions, names written inside a nested function, names
 * shadowed by a nested {@code let}/{@code const} or a catch parameter, a read in the same loop as
 * the first write (loop-carried), a top-level {@code let}/{@code const} without an initializer
 * (#77181 resets it every run), member names, object keys and labels, and names that the host
 * resolves (a column of the row, a member of the condition scope).
 *
 * <p>Each distinct script text is lexed once per node (a bounded set of 64-bit hashes), and a
 * flagged text warns once per node lifetime (a separate bounded set that the checked set's
 * overflow does not clear). The WARN goes to the logger {@value #LOGGER_NAME}; set its level to
 * ERROR to silence the check.
 */
public final class ScriptStateLint {
   private ScriptStateLint() {
   }

   /**
    * Check the formula of an expression column of a table (FormulaTableLens) once it has
    * compiled. A bare name that the row resolves to a column is not script state: it is
    * resolved exactly as {@link TableRow#hasMember} resolves it (case-insensitive, table
    * prefix, column identifier, base tables of the filter chain).
    *
    * @param formula  the formula text.
    * @param compiled the compiled script, {@code null} if it failed to compile (not checked).
    * @param table    the table whose row the formula reads.
    * @param hrows    the header row count of the table.
    * @param colName  the name of the expression column.
    * @param tableName the table (assembly) name, may be {@code null}.
    * @param contextName the report or context name, may be {@code null}.
    */
   public static void checkColumn(String formula, Object compiled, XTable table, int hrows,
                                  String colName, String tableName, String contextName)
   {
      checkColumn(formula, compiled, table, hrows, colName, tableName, contextName, null,
                  false);
   }

   /**
    * Check the formula of an expression column of a table that owns the top-level vars of
    * its formulas (Testing #77123): an owned var keeps its value from row to row for the
    * whole table, so reading it before writing it is a supported accumulator and not a
    * finding. The exception is the pool: with the script context pool on, a script object in
    * an owned var (array, object, function; a Date is kept) is kept only within one batch of
    * rows, so a read-before-write of an owned var that is assigned such a value is still
    * reported.
    * R2 (an undeclared global) is not affected.
    *
    * @param owned  the names the table owns ({@code GraalJavaScriptEngine.collectOwnedVarNames}
    *               of all its formulas), or {@code null} for a table that owns no vars.
    * @param pooled true when the formulas run on the worksheet script context pool.
    *
    * @see #checkColumn(String, Object, XTable, int, String, String, String)
    */
   public static void checkColumn(String formula, Object compiled, XTable table, int hrows,
                                  String colName, String tableName, String contextName,
                                  Set<String> owned, boolean pooled)
   {
      // the column resolution reads header row 0, which is a data row without a header
      if(compiled == null || table == null || hrows < 1) {
         return;
      }

      String where = "the expression column \"" + colName + "\"" +
         (tableName != null ? " of table \"" + tableName + "\"" : "") +
         (contextName != null ? " (" + contextName + ")" : "");
      Predicate<String> column = new Predicate<>() {
         @Override
         public boolean test(String name) {
            if(row == null) {
               row = new TableRow(table, hrows);
            }

            return row.hasMember(name);
         }

         private TableRow row;
      };

      checkFindings(formula, where, colName, f -> {
         if(column.test(f.name())) {
            return Disposition.SKIP;
         }

         // R2 stays as is: an undeclared global is not a var of this formula
         if(!"R1".equals(f.rule())) {
            return Disposition.REPORT;
         }

         if(owned == null) {
            return Disposition.REPORT;
         }

         if(owned.contains(f.name())) {
            return pooled && f.objectValue() ? Disposition.REPORT_POOLED_OBJECT
               : Disposition.SKIP;
         }

         // declared with let/const in a formula of this table too: the table does not own it
         return Disposition.REPORT_NOT_OWNED;
      });
   }

   /** What to do with one finding, and which explanation its WARN gets. */
   enum Disposition {
      /** A host name or a supported accumulator: not a finding. */
      SKIP,
      /** A value kept in a global or in a variable nothing owns. */
      REPORT,
      /** An expression column's var that its table does not own (a let/const of the name). */
      REPORT_NOT_OWNED,
      /** A var its table owns that holds an array, object or function, with the pool on. */
      REPORT_POOLED_OBJECT
   }

   /**
    * Check a condition script once it has compiled, before it runs. A name that the scope
    * already has is host state, not script state.
    *
    * @param compiled the compiled script, returned as is. {@code null} is not checked.
    * @param exp      the script text.
    * @param scope    the scope the script runs on, may be {@code null}.
    * @param site     what the script is, e.g. "condition".
    *
    * @return {@code compiled}.
    */
   public static <T> T checkCondition(T compiled, String exp, ScriptScope scope, String site) {
      if(compiled != null) {
         check(exp, "a " + site + " script", null,
               name -> scope != null && scope.hasMember(name));
      }

      return compiled;
   }

   /**
    * Check a script once per node and warn if it reads state before it writes it. Never throws.
    *
    * @param script     the script text.
    * @param where      the subject of the warning, e.g. {@code the expression column "C"}.
    * @param column     the expression column, for the {@code field[-1]} suggestion, or
    *                   {@code null}.
    * @param hostOwned  true for a name the host resolves, which is never a finding.
    */
   public static void check(String script, String where, String column,
                            Predicate<String> hostOwned)
   {
      checkFindings(script, where, column,
            f -> hostOwned != null && hostOwned.test(f.name()) ? Disposition.SKIP
               : Disposition.REPORT);
   }

   private static void checkFindings(String script, String where, String column,
                                     Function<Finding, Disposition> disposition)
   {
      try {
         check0(script, where, column, disposition);
      }
      catch(StackOverflowError ex) {
         // a deeply nested script: never let the check affect the query
         NODE_ERRORS.incrementAndGet();
         LOG.debug("Failed to check script state", ex);
      }
      catch(VirtualMachineError ex) {
         throw ex;
      }
      catch(Throwable ex) {
         NODE_ERRORS.incrementAndGet();
         LOG.debug("Failed to check script state", ex);
      }
   }

   private static void check0(String script, String where, String column,
                              Function<Finding, Disposition> disposition)
   {
      if(script == null || script.isEmpty()) {
         return;
      }

      if(script.length() > MAX_SCRIPT_LENGTH) {
         NODE_SKIPPED.incrementAndGet();
         return;
      }

      long hash = hash(script);

      if(WARNED.contains(hash)) {
         return;
      }

      if(CHECKED.size() >= MAX_CHECKED) {
         CHECKED.clear();
      }

      if(!CHECKED.add(hash)) {
         return;
      }

      NODE_CHECKS.incrementAndGet();
      List<Finding> raw = detect(script);
      List<Finding> findings = new ArrayList<>();
      List<Disposition> kinds = new ArrayList<>();

      for(Finding finding : raw) {
         Disposition kind = disposition.apply(finding);

         if(kind != Disposition.SKIP) {
            findings.add(finding);
            kinds.add(kind);
         }
      }

      if(findings.isEmpty()) {
         // the host owned every name here (e.g. a column, or a var its table owns); on
         // another table, scope or pool mode it may not, so check this text again next time
         // (once per compile, never per row)
         if(!raw.isEmpty()) {
            CHECKED.remove(hash);
         }

         return;
      }

      // once per node lifetime; past the cap a flagged text can warn again after a clear
      if(WARNED.size() < MAX_WARNED && !WARNED.add(hash)) {
         return;
      }

      NODE_HAZARD_SCRIPTS.incrementAndGet();

      for(Finding finding : findings) {
         ("R1".equals(finding.rule()) ? NODE_R1 : NODE_R2).incrementAndGet();
      }

      if(LOG.isWarnEnabled()) {
         LOG.warn(message(script, where, column, findings, kinds));
      }
   }

   static String message(String script, String where, String column, List<Finding> findings) {
      return message(script, where, column, findings,
                     Collections.nCopies(findings.size(), Disposition.REPORT));
   }

   static String message(String script, String where, String column, List<Finding> findings,
                         List<Disposition> kinds)
   {
      StringBuilder buf = new StringBuilder("Script state carried between evaluations: ");
      buf.append(where);

      for(int i = 0; i < findings.size() && i < MAX_REPORTED; i++) {
         Finding f = findings.get(i);
         buf.append(i == 0 ? " " : "; ");

         if("R1".equals(f.rule())) {
            buf.append("reads variable \"").append(f.name()).append("\" before assigning it");
         }
         else {
            buf.append("reads global \"").append(f.name())
               .append("\" before assigning it (it is not declared with var)");
         }

         buf.append(" (rule ").append(f.rule()).append(", line ")
            .append(line(script, f.offset())).append(")");

         if(kinds.get(i) == Disposition.REPORT_POOLED_OBJECT) {
            buf.append(" and assigns it an array, object or function");
         }
         else if(kinds.get(i) == Disposition.REPORT_NOT_OWNED) {
            buf.append(", which a formula of this table also declares with let or const, " +
                       "so the table does not keep it");
         }
      }

      boolean pooledObject = false;
      boolean shared = false;

      for(int i = 0; i < findings.size() && i < MAX_REPORTED; i++) {
         pooledObject |= kinds.get(i) == Disposition.REPORT_POOLED_OBJECT;
         shared |= kinds.get(i) != Disposition.REPORT_POOLED_OBJECT;
      }

      buf.append(".");

      if(shared) {
         buf.append(column != null
            ? " A value kept from an earlier evaluation in a global, or in a variable its " +
              "table does not keep, is not reliable: "
            : " A value kept from an earlier evaluation is not reliable: ");
         buf.append("it is not reset between tables, conditions or queries, and its value " +
                    "can depend on how the table is read (for example, it resets every batch " +
                    "when the worksheet script context pool is on).");
      }

      if(pooledObject) {
         buf.append(" A top-level var of an expression column's formula keeps its value " +
                    "from row to row for its whole table, but with the worksheet script " +
                    "context pool on an array, object or function in it is kept only " +
                    "within one batch of rows: a batch that runs on another pooled context " +
                    "reads it as undefined. A number, string, boolean or Date is always kept " +
                    "(a Date by its time value).");
      }

      if(column != null) {
         buf.append(" For a running total use field[-1]['").append(column)
            .append("'], or a running-total calculation.");
      }
      else {
         buf.append(" Assign the variable before reading it.");
      }

      buf.append(" To silence this check, set the log level of ").append(LOGGER_NAME)
         .append(" to ERROR.\nScript:\n").append(XUtil.numbering(script));
      return buf.toString();
   }

   private static int line(String s, int offset) {
      int line = 1;

      for(int i = 0; i < offset && i < s.length(); i++) {
         if(s.charAt(i) == '\n') {
            line++;
         }
      }

      return line;
   }

   /** FNV-1a over the chars. */
   static long hash(String s) {
      long h = 0xcbf29ce484222325L;

      for(int i = 0; i < s.length(); i++) {
         h ^= s.charAt(i);
         h *= 0x100000001b3L;
      }

      return h;
   }

   /** Distinct scripts lexed on this node. */
   public static long nodeStateLintChecks() {
      return NODE_CHECKS.get();
   }

   /** Distinct scripts flagged (warned) on this node. */
   public static long nodeStateHazardScripts() {
      return NODE_HAZARD_SCRIPTS.get();
   }

   /**
    * Findings on this node for a rule.
    *
    * @param rule "R1" or "R2".
    */
   public static long nodeStateHazardsByRule(String rule) {
      return "R1".equals(rule) ? NODE_R1.get() : "R2".equals(rule) ? NODE_R2.get() : 0;
   }

   /** Scripts not checked because they are longer than 64 KB. */
   public static long nodeStateLintSkipped() {
      return NODE_SKIPPED.get();
   }

   /** Checks that failed internally (logged at DEBUG, never propagated). */
   public static long nodeStateLintErrors() {
      return NODE_ERRORS.get();
   }

   /** For tests: forget the checked and warned scripts. */
   static void reset() {
      CHECKED.clear();
      WARNED.clear();
   }

   static int checkedCount() {
      return CHECKED.size();
   }

   // ---------------------------------------------------------------- detector

   /**
    * A read of {@code name} at {@code offset} (a char offset) before it is written.
    * {@code objectValue} is true when a top-level write of the name assigns a value that
    * looks like a script object that the pool keeps only within one batch: an object or array
    * literal, {@code new} (except {@code new Date}), a function or an arrow function (a lexical
    * check; a call that returns an object is not seen).
    */
   public record Finding(String rule, String name, int offset, boolean objectValue) {
      public Finding(String rule, String name, int offset) {
         this(rule, name, offset, false);
      }

      @Override
      public String toString() {
         return rule + ":" + name;
      }
   }

   private enum T { ID, NUM, STR, P }

   private record Tok(T type, String text, int start, boolean nl) {
   }

   /**
    * The read-before-write findings of a script, at most one per name, at its first offending
    * read. The caller filters out host-owned names.
    */
   public static List<Finding> detect(String src) {
      List<Tok> t = lex(src);
      int n = t.size();
      int[] match = new int[n];
      Arrays.fill(match, -1);
      Deque<Integer> st = new ArrayDeque<>();
      int[] depth = new int[n];

      for(int i = 0; i < n; i++) {
         String x = t.get(i).text;
         depth[i] = st.size();

         if(t.get(i).type != T.P) {
            continue;
         }

         if(x.equals("(") || x.equals("[") || x.equals("{")) {
            st.push(i);
         }
         else if(x.equals(")") || x.equals("]") || x.equals("}")) {
            if(!st.isEmpty()) {
               int o = st.pop();
               match[o] = i;
               match[i] = o;
               depth[i] = st.size();
            }
         }
      }

      // function regions (function keyword or arrow), parameters included
      boolean[] inFunc = new boolean[n];
      Set<String> funcDeclNames = new HashSet<>();

      for(int i = 0; i < n; i++) {
         Tok k = t.get(i);

         if(k.type == T.ID && k.text.equals("function") && !isMember(t, i)) {
            int j = i + 1;

            if(j < n && t.get(j).text.equals("*")) {
               j++;
            }

            if(j < n && t.get(j).type == T.ID) {
               if(!inFunc[i] && isStatementStart(t, i)) {
                  funcDeclNames.add(t.get(j).text);
               }

               j++;
            }

            if(j < n && t.get(j).text.equals("(") && match[j] > 0) {
               int b = match[j] + 1;

               if(b < n && t.get(b).text.equals("{") && match[b] > 0) {
                  mark(inFunc, i, match[b]);
               }
            }
         }
         else if(k.type == T.P && k.text.equals("=>")) {
            int ps;
            int pe = i - 1;

            if(pe < 0) {
               continue;
            }

            ps = t.get(pe).text.equals(")") && match[pe] >= 0 ? match[pe] : pe;

            if(ps > 0 && t.get(ps - 1).text.equals("async")) {
               ps--;
            }

            int b = i + 1;
            int end;

            if(b < n && t.get(b).text.equals("{") && match[b] > 0) {
               end = match[b];
            }
            else {
               end = exprEnd(t, match, depth, b, false) - 1;
            }

            mark(inFunc, ps, Math.max(end, i));
         }
      }

      // loop regions
      List<int[]> loops = new ArrayList<>();

      for(int i = 0; i < n; i++) {
         Tok k = t.get(i);

         if(k.type != T.ID || isMember(t, i)) {
            continue;
         }

         if((k.text.equals("for") || k.text.equals("while")) && i + 1 < n &&
            t.get(i + 1).text.equals("(") && match[i + 1] > 0)
         {
            // the while of a do-while: its body is the do block, already recorded
            if(k.text.equals("while") && i > 0 && t.get(i - 1).text.equals("}") &&
               match[i - 1] > 0 && t.get(match[i - 1] - 1).text.equals("do"))
            {
               continue;
            }

            int b = match[i + 1] + 1;
            int end;

            if(b < n && t.get(b).text.equals("{") && match[b] > 0) {
               end = match[b];
            }
            else {
               end = stmtEnd(t, depth, b);
            }

            loops.add(new int[] { i, end });
         }
         else if(k.text.equals("do") && i + 1 < n && t.get(i + 1).text.equals("{") &&
                 match[i + 1] > 0)
         {
            int end = match[i + 1];

            if(end + 2 < n && t.get(end + 1).text.equals("while") && match[end + 2] > 0) {
               end = match[end + 2];
            }

            loops.add(new int[] { i, end });
         }
      }

      Set<String> declared = new HashSet<>();        // top-level var, depth-0 let/const w/ init
      Set<String> lexicalNoInit = new HashSet<>();   // depth-0 let/const without initializer
      Set<String> shadowed = new HashSet<>();        // nested let/const, catch params
      Set<String> writtenInFunc = new HashSet<>();
      Map<String, Integer> firstWrite = new HashMap<>();
      // the token index where the first write starts (its name), so a read that is part of
      // the first write itself (x = x + 1, x += 1, x++) is not taken for a loop-carried read
      Map<String, Integer> firstWriteStart = new HashMap<>();
      Map<String, List<Integer>> reads = new LinkedHashMap<>();
      // names that a top-level write may give a script object (array, object, function)
      Set<String> objectWrites = new HashSet<>();
      int declDepth = -1;
      boolean declExpectName = false;
      String declKw = null;

      for(int i = 0; i < n; i++) {
         Tok k = t.get(i);

         if(declDepth >= 0) {
            // the end of the declarator list
            if(depth[i] < declDepth || depth[i] == declDepth && k.text.equals(";")) {
               declDepth = -1;
            }
            else if(depth[i] == declDepth && k.text.equals(",")) {
               declExpectName = true;
               continue;
            }
            else if(depth[i] == declDepth && k.nl && i > 0 && endsValue(t.get(i - 1)) &&
                    startsStatement(k))
            {
               declDepth = -1;
            }
         }

         if(k.type != T.ID || KEYWORDS.contains(k.text) && !k.text.equals("var") &&
            !k.text.equals("let") && !k.text.equals("const") && !k.text.equals("catch"))
         {
            continue;
         }

         if(isMember(t, i)) {
            declExpectName = false;
            continue;
         }

         String name = k.text;

         if(name.equals("catch")) {
            if(i + 2 < n && t.get(i + 1).text.equals("(") && t.get(i + 2).type == T.ID) {
               shadowed.add(t.get(i + 2).text);
            }

            continue;
         }

         if(name.equals("var") || name.equals("let") || name.equals("const")) {
            declDepth = depth[i];
            declExpectName = true;
            declKw = inFunc[i] ? "fn" + name : name;
            continue;
         }

         if(declDepth >= 0 && declExpectName && depth[i] == declDepth) {
            declExpectName = false;
            boolean init = i + 1 < n && t.get(i + 1).text.equals("=");
            boolean forInOf = i + 1 < n &&
               (t.get(i + 1).text.equals("in") || t.get(i + 1).text.equals("of"));

            if(declKw.startsWith("fn")) {
               continue;   // a function local
            }

            if(declKw.equals("var") || depth[i] == 0) {
               if(!declKw.equals("var") && !init && !forInOf) {
                  lexicalNoInit.add(name);
               }
               else {
                  declared.add(name);
               }
            }
            else {
               shadowed.add(name);   // nested let/const: block scoped
               continue;
            }

            if(init) {
               int end = exprEnd(t, match, depth, i + 2, true);
               write(firstWrite, firstWriteStart, name, i, end);

               if(objectValue(t, i + 2, end)) {
                  objectWrites.add(name);
               }
            }
            else if(forInOf) {
               write(firstWrite, firstWriteStart, name, i, i);
            }

            continue;
         }

         if(inFunc[i]) {
            if(isWrite(t, i)) {
               writtenInFunc.add(name);
            }

            continue;
         }

         // top level
         if(isObjectKeyOrLabel(t, i)) {
            continue;
         }

         Tok prev = i > 0 ? t.get(i - 1) : null;
         Tok next = i + 1 < n ? t.get(i + 1) : null;

         if(next != null && next.text.equals("=")) {
            int end = exprEnd(t, match, depth, i + 2, false);
            write(firstWrite, firstWriteStart, name, i, end);

            if(objectValue(t, i + 2, end)) {
               objectWrites.add(name);
            }
         }
         else if(next != null && COMPOUND.contains(next.text)) {
            read(reads, name, i);
            write(firstWrite, firstWriteStart, name, i, i + 1);

            // x ||= [], x ??= {}: the logical assignments can store an object
            if(LOGICAL_ASSIGN.contains(next.text) &&
               objectValue(t, i + 2, exprEnd(t, match, depth, i + 2, false)))
            {
               objectWrites.add(name);
            }
         }
         else if(next != null && !next.nl && (next.text.equals("++") || next.text.equals("--")) ||
                 prev != null && prev.type == T.P &&
                 (prev.text.equals("++") || prev.text.equals("--")) &&
                 (i < 2 || !endsValue(t.get(i - 2)) || prev.nl) &&
                 (next == null || !(next.text.equals(".") || next.text.equals("[") ||
                                    next.text.equals("?.") || next.text.equals("("))))
         {
            read(reads, name, i);
            write(firstWrite, firstWriteStart, name, i, i + 1);
         }
         else if(next != null && (next.text.equals("in") || next.text.equals("of")) &&
                 prev != null && prev.text.equals("(") && i >= 2 && t.get(i - 2).text.equals("for"))
         {
            write(firstWrite, firstWriteStart, name, i, i);
         }
         else {
            read(reads, name, i);
         }
      }

      List<Finding> out = new ArrayList<>();

      for(Map.Entry<String, List<Integer>> e : reads.entrySet()) {
         String name = e.getKey();

         if(shadowed.contains(name) || writtenInFunc.contains(name) ||
            funcDeclNames.contains(name) || lexicalNoInit.contains(name) && !declared.contains(name))
         {
            continue;
         }

         Integer w = firstWrite.get(name);
         Integer ws = firstWriteStart.get(name);
         boolean decl = declared.contains(name);

         for(int pos : e.getValue()) {
            // a read in the same loop as the first write runs after it from the second
            // iteration on, unless it is a read inside that write (an accumulator)
            boolean inFirstWrite = w != null && ws != null && pos >= ws && pos < w;

            if(w != null && (pos >= w || !inFirstWrite && sameLoop(loops, pos, w))) {
               continue;
            }

            if(decl) {
               out.add(new Finding("R1", name, t.get(pos).start, objectWrites.contains(name)));
               break;
            }

            // a plain read of a name the script never writes is a column, function, etc.
            if(w != null) {
               out.add(new Finding("R2", name, t.get(pos).start, objectWrites.contains(name)));
               break;
            }
         }
      }

      return out;
   }

   private static List<Tok> lex(String s) {
      List<Tok> out = new ArrayList<>();
      int n = s.length();
      int i = 0;
      boolean nl = false;
      String prevWord = null;   // previous identifier/keyword token, else null
      // as in GraalJavaScriptEngine's scanTopLevel/skipInitializer/
      // stripStringsAndComments: one entry per open bracket, whether it is the
      // `(` of an if/while/for/with head, whose `)` is followed by a statement
      // (so a `/` there starts a regex, bug #77305)
      Deque<Boolean> brackets = new ArrayDeque<>();
      boolean afterHead = false;   // the previous token closed a control-flow head

      while(i < n) {
         char c = s.charAt(i);

         if(c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029') {
            nl = true;
            i++;
            continue;
         }

         if(Character.isWhitespace(c) || c == '\u00a0' || c == '\ufeff') {
            i++;
            continue;
         }

         if(c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
            while(i < n && s.charAt(i) != '\n' && s.charAt(i) != '\r') {
               i++;
            }

            continue;
         }

         if(c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
            int e = s.indexOf("*/", i + 2);
            e = e < 0 ? n : e + 2;

            if(s.substring(i, e).indexOf('\n') >= 0) {
               nl = true;
            }

            i = e;
            continue;
         }

         int start = i;

         if(c == '"' || c == '\'') {
            i++;

            while(i < n && s.charAt(i) != c) {
               if(s.charAt(i) == '\\') {
                  i++;
               }

               i++;
            }

            i = Math.min(i + 1, n);
            out.add(new Tok(T.STR, "\"\"", start, nl));
            nl = false;
            prevWord = null;
            afterHead = false;
            continue;
         }

         if(c == '`') {
            i = skipTemplate(s, i + 1);
            out.add(new Tok(T.STR, "``", start, nl));
            nl = false;
            prevWord = null;
            afterHead = false;
            continue;
         }

         if(c == '/' && (afterHead || regexAllowed(out))) {
            int e = regexEnd(s, i);

            if(e > 0) {
               i = e;
               out.add(new Tok(T.STR, "//", start, nl));
               nl = false;
               prevWord = null;
               afterHead = false;
               continue;
            }
         }

         if(Character.isJavaIdentifierStart(c)) {
            boolean afterDot = !out.isEmpty() && out.get(out.size() - 1).type == T.P &&
               out.get(out.size() - 1).text.equals(".");
            i++;

            while(i < n && Character.isJavaIdentifierPart(s.charAt(i))) {
               i++;
            }

            String word = s.substring(start, i);
            out.add(new Tok(T.ID, word, start, nl));
            nl = false;
            prevWord = afterDot ? null : word;   // a property name isn't a keyword
            afterHead = false;
            continue;
         }

         if(Character.isDigit(c) || c == '.' && i + 1 < n && Character.isDigit(s.charAt(i + 1))) {
            i++;

            while(i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '.')) {
               i++;
            }

            out.add(new Tok(T.NUM, s.substring(start, i), start, nl));
            nl = false;
            prevWord = null;
            afterHead = false;
            continue;
         }

         String p = String.valueOf(c);

         for(String q : PUNCT) {
            if(s.startsWith(q, i)) {
               p = q;
               break;
            }
         }

         // `?.` followed by a digit is a ternary `?` then `.5`
         if(p.equals("?.") && i + 2 < n && Character.isDigit(s.charAt(i + 2))) {
            p = "?";
         }

         boolean closedHead = false;

         if(p.equals("(") || p.equals("[") || p.equals("{")) {
            brackets.push(p.equals("(") && prevWord != null && CONTROL_HEAD_KEYWORDS.contains(prevWord));
         }
         else if(p.equals(")") || p.equals("]") || p.equals("}")) {
            if(!brackets.isEmpty()) {
               closedHead = brackets.pop() && p.equals(")");
            }
         }

         i += p.length();
         out.add(new Tok(T.P, p, start, nl));
         nl = false;
         prevWord = null;
         afterHead = closedHead;
      }

      return out;
   }

   /**
    * Whether a {@code /} at the current lexing position begins a
    * regular-expression literal rather than a division operator, based on the
    * last emitted token. Does not by itself account for a {@code /} right after
    * an {@code if}/{@code while}/{@code for}/{@code with} head's closing
    * {@code )} \u2014 that case is handled by the caller's {@code afterHead} flag
    * (bug #77305), since the last token here is always {@code )}, never the
    * control-head keyword itself, so {@link #REGEX_AFTER_WORD} (which covers a
    * regex directly after a keyword token, e.g. {@code return /x/}) does not
    * apply to this shape.
    */
   private static boolean regexAllowed(List<Tok> out) {
      if(out.isEmpty()) {
         return true;
      }

      Tok t = out.get(out.size() - 1);

      if(t.type == T.ID) {
         return REGEX_AFTER_WORD.contains(t.text);
      }

      if(t.type == T.NUM || t.type == T.STR) {
         return false;
      }

      return !(t.text.equals(")") || t.text.equals("]") || t.text.equals("}") ||
               t.text.equals("++") || t.text.equals("--"));
   }

   private static int regexEnd(String s, int i) {
      int n = s.length();
      boolean cls = false;
      i++;

      while(i < n) {
         char c = s.charAt(i);

         if(c == '\n' || c == '\r') {
            return -1;
         }

         if(c == '\\') {
            i += 2;
            continue;
         }

         if(c == '[') {
            cls = true;
         }
         else if(c == ']') {
            cls = false;
         }
         else if(c == '/' && !cls) {
            i++;

            while(i < n && Character.isJavaIdentifierPart(s.charAt(i))) {
               i++;
            }

            return i;
         }

         i++;
      }

      return -1;
   }

   private static int skipTemplate(String s, int i) {
      int n = s.length();

      while(i < n) {
         char c = s.charAt(i);

         if(c == '\\') {
            i += 2;
            continue;
         }

         if(c == '`') {
            return i + 1;
         }

         if(c == '$' && i + 1 < n && s.charAt(i + 1) == '{') {
            int d = 1;
            i += 2;

            while(i < n && d > 0) {
               char k = s.charAt(i);

               if(k == '{') {
                  d++;
               }
               else if(k == '}') {
                  d--;
               }
               else if(k == '"' || k == '\'') {
                  i++;

                  while(i < n && s.charAt(i) != k) {
                     if(s.charAt(i) == '\\') {
                        i++;
                     }

                     i++;
                  }
               }
               else if(k == '`') {
                  i = skipTemplate(s, i + 1) - 1;
               }

               i++;
            }

            continue;
         }

         i++;
      }

      return n;
   }

   private static boolean sameLoop(List<int[]> loops, int a, int b) {
      for(int[] l : loops) {
         if(a >= l[0] && a <= l[1] && b >= l[0] && b <= l[1]) {
            return true;
         }
      }

      return false;
   }

   /**
    * Whether the expression in tokens {@code [from, to)} can evaluate to a script object the
    * pool keeps only within one batch: it contains an object literal, an array literal (not a
    * subscript), {@code new} (but not {@code new Date}: a Date is kept across batches,
    * Testing #77123 B1 residual), {@code function} or {@code =>}.
    */
   private static boolean objectValue(List<Tok> t, int from, int to) {
      for(int i = Math.max(0, from); i < to && i < t.size(); i++) {
         Tok k = t.get(i);

         if(k.type == T.P) {
            if(k.text.equals("{") || k.text.equals("=>") ||
               k.text.equals("[") && (i == from || !endsValue(t.get(i - 1))))
            {
               return true;
            }
         }
         else if(k.type == T.ID && (k.text.equals("new") || k.text.equals("function")) &&
                 !isMember(t, i) && !newDate(t, i, to))
         {
            return true;
         }
      }

      return false;
   }

   // new Date, not new Date.x: a Date is kept across pooled batches
   private static boolean newDate(List<Tok> t, int i, int to) {
      return t.get(i).text.equals("new") && i + 1 < to && i + 1 < t.size() &&
         t.get(i + 1).type == T.ID && t.get(i + 1).text.equals("Date") &&
         (i + 2 >= to || i + 2 >= t.size() || !t.get(i + 2).text.equals("."));
   }

   private static void mark(boolean[] a, int from, int to) {
      for(int i = Math.max(0, from); i <= to && i < a.length; i++) {
         a[i] = true;
      }
   }

   private static void write(Map<String, Integer> w, Map<String, Integer> ws, String name,
                             int start, int pos)
   {
      Integer old = w.get(name);

      if(old == null || pos < old) {
         w.put(name, pos);
         ws.put(name, start);
      }
   }

   private static void read(Map<String, List<Integer>> r, String name, int pos) {
      r.computeIfAbsent(name, x -> new ArrayList<>()).add(pos);
   }

   private static boolean isMember(List<Tok> t, int i) {
      if(i == 0) {
         return false;
      }

      String p = t.get(i - 1).text;
      return p.equals(".") || p.equals("?.");
   }

   private static boolean isStatementStart(List<Tok> t, int i) {
      if(i == 0) {
         return true;
      }

      String p = t.get(i - 1).text;
      return p.equals(";") || p.equals("{") || p.equals("}") ||
         t.get(i).nl && endsValue(t.get(i - 1));
   }

   private static boolean isObjectKeyOrLabel(List<Tok> t, int i) {
      if(i + 1 >= t.size() || !t.get(i + 1).text.equals(":")) {
         // a break/continue label
         return i > 0 && (t.get(i - 1).text.equals("break") || t.get(i - 1).text.equals("continue"));
      }

      if(i == 0) {
         return true;
      }

      String p = t.get(i - 1).text;
      return p.equals("{") || p.equals(",") || p.equals(";") || p.equals("}") || p.equals(")") ||
         t.get(i).nl;
   }

   private static boolean isWrite(List<Tok> t, int i) {
      int n = t.size();

      if(i + 1 < n && (t.get(i + 1).text.equals("=") || COMPOUND.contains(t.get(i + 1).text) ||
         t.get(i + 1).text.equals("++") || t.get(i + 1).text.equals("--")))
      {
         return true;
      }

      return i > 0 && (t.get(i - 1).text.equals("++") || t.get(i - 1).text.equals("--"));
   }

   private static boolean endsValue(Tok k) {
      if(k.type == T.NUM || k.type == T.STR) {
         return true;
      }

      if(k.type == T.ID) {
         return !REGEX_AFTER_WORD.contains(k.text) && !k.text.equals("var") &&
            !k.text.equals("let") && !k.text.equals("const");
      }

      return k.text.equals(")") || k.text.equals("]") || k.text.equals("}") ||
         k.text.equals("++") || k.text.equals("--");
   }

   private static boolean startsStatement(Tok k) {
      if(k.type == T.ID || k.type == T.NUM || k.type == T.STR) {
         return true;
      }

      return k.text.equals("{") || k.text.equals("!") || k.text.equals("++") ||
         k.text.equals("--") || k.text.equals("~");
   }

   /**
    * The index just past the expression that starts at {@code from}: the first token at the
    * same depth that is {@code ;}, {@code ,} (when {@code stopAtComma}), a closer below, or an
    * ASI line break.
    */
   private static int exprEnd(List<Tok> t, int[] match, int[] depth, int from,
                              boolean stopAtComma)
   {
      int n = t.size();

      if(from >= n) {
         return n;
      }

      int d = depth[from];

      for(int j = from; j < n; j++) {
         Tok k = t.get(j);

         if(depth[j] < d) {
            return j;
         }

         if(depth[j] > d) {
            continue;
         }

         String x = k.text;

         if(k.type == T.P && (x.equals(")") || x.equals("]") || x.equals("}") || x.equals(";") ||
            x.equals(",") && stopAtComma))
         {
            return j;
         }

         if(j > from && k.nl && endsValue(t.get(j - 1)) && startsStatement(k) &&
            !(k.type == T.ID && (x.equals("in") || x.equals("of") || x.equals("instanceof"))))
         {
            return j;
         }

         if(k.type == T.P && (x.equals("(") || x.equals("[") || x.equals("{")) && match[j] > 0) {
            j = match[j];
         }
      }

      return n;
   }

   private static int stmtEnd(List<Tok> t, int[] depth, int from) {
      int n = t.size();

      if(from >= n) {
         return n - 1;
      }

      int d = depth[from];

      for(int j = from; j < n; j++) {
         if(depth[j] < d) {
            return j - 1;
         }

         if(depth[j] == d && t.get(j).text.equals(";")) {
            return j;
         }
      }

      return n - 1;
   }

   public static final String LOGGER_NAME = "inetsoft.util.script.ScriptStateLint";
   static final int MAX_SCRIPT_LENGTH = 64 * 1024;
   static final int MAX_CHECKED = 2048;
   static final int MAX_WARNED = 512;
   private static final int MAX_REPORTED = 3;

   private static final Set<String> KEYWORDS = Set.of(
      "var", "let", "const", "function", "return", "if", "else", "for", "while", "do", "switch",
      "case", "default", "break", "continue", "new", "typeof", "instanceof", "in", "of", "this",
      "null", "true", "false", "undefined", "try", "catch", "finally", "throw", "delete", "void",
      "with", "class", "extends", "super", "yield", "await", "async", "debugger", "import",
      "export", "NaN", "Infinity", "arguments", "eval");
   private static final Set<String> REGEX_AFTER_WORD = Set.of(
      "return", "typeof", "instanceof", "in", "of", "new", "delete", "void", "throw", "case",
      "do", "else", "yield", "await");
   // mirrors GraalJavaScriptEngine.CONTROL_HEAD_KEYWORDS (bug #77305): a `)` that
   // closes one of these heads is followed by a statement, so a `/` right after
   // it starts a regex, not a division — a structurally different case from
   // REGEX_AFTER_WORD above (that set is keyed on the token directly preceding
   // the `/` being the keyword itself; here the token directly preceding the
   // `/` is always `)`, never the keyword).
   private static final Set<String> CONTROL_HEAD_KEYWORDS = Set.of("if", "while", "for", "with");
   private static final String[] PUNCT = {
      ">>>=", "...", "===", "!==", "**=", "<<=", ">>=", ">>>", "&&=", "||=", "??=",
      "=>", "==", "!=", "<=", ">=", "&&", "||", "??", "?.", "++", "--", "+=", "-=", "*=", "/=",
      "%=", "&=", "|=", "^=", "<<", ">>", "**" };
   private static final Set<String> LOGICAL_ASSIGN = Set.of("&&=", "||=", "??=");
   private static final Set<String> COMPOUND = Set.of(
      "+=", "-=", "*=", "/=", "%=", "**=", "<<=", ">>=", ">>>=", "&=", "|=", "^=", "&&=", "||=",
      "??=");

   private static final Set<Long> CHECKED = ConcurrentHashMap.newKeySet();
   private static final Set<Long> WARNED = ConcurrentHashMap.newKeySet();
   private static final AtomicLong NODE_CHECKS = new AtomicLong();
   private static final AtomicLong NODE_HAZARD_SCRIPTS = new AtomicLong();
   private static final AtomicLong NODE_R1 = new AtomicLong();
   private static final AtomicLong NODE_R2 = new AtomicLong();
   private static final AtomicLong NODE_SKIPPED = new AtomicLong();
   private static final AtomicLong NODE_ERRORS = new AtomicLong();
   private static final Logger LOG = LoggerFactory.getLogger(LOGGER_NAME);
}
