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

import inetsoft.report.TableFilter;
import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.PostProcessor;
import inetsoft.report.filter.*;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.uql.*;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.ExpressionValue;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.script.ExpressionFailedException;
import inetsoft.util.script.ScriptSpan;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;

import org.graalvm.polyglot.Value;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs one script through a real worksheet pipeline shape over the standard table and
 * returns one string per result cell (Testing #77123). Two runs of the same script must give
 * equal lists whatever the pool configuration and read pattern, except for the documented
 * drifts the tests classify.
 */
public final class RelPipeline {
   private RelPipeline() {
   }

   /** data rows of the standard table */
   public static final int ROWS = 1200;
   /** the header of the expression column */
   public static final String COLUMN = "f";
   /** rows per calc field group */
   public static final int GROUP = 50;
   /** condition groups a CONDITION run builds in its sandbox */
   public static final int CONDITIONS = 3;

   /**
    * Run one formula over the standard base table (1200 rows, columns id:int, value:double,
    * name:string, day:date, flag:boolean, a null every 17th row) and return one string per
    * cell, "E:"+exception class when a cell/row throws. The configuration is applied before
    * the sandbox is built and cleared afterwards.
    */
   public static List<String> run(String formula, Shape shape, RelConfig cfg, ReadPattern read)
      throws Exception
   {
      AssetQuerySandbox box = sandbox(cfg);

      try {
         return run(formula, shape, box, read);
      }
      finally {
         box.dispose();
      }
   }

   /**
    * Build a sandbox and its env under a configuration. The sandbox reads the pool mode when
    * it is built and its env reads the tuning when it is created, so the configuration is
    * applied for just that long, under a lock: runs on other threads can build their own
    * sandboxes under other configurations.
    */
   public static AssetQuerySandbox sandbox(RelConfig cfg) {
      return sandbox(cfg, new Worksheet());
   }

   /**
    * Build a sandbox of a worksheet under a configuration, as {@link #sandbox(RelConfig)}.
    */
   public static AssetQuerySandbox sandbox(RelConfig cfg, Worksheet ws) {
      synchronized(CONFIG_LOCK) {
         cfg.apply();

         try {
            AssetQuerySandbox box = new AssetQuerySandbox(ws);

            if(box.isScriptPoolMode() != cfg.pool() ||
               (box.getScriptEnv() instanceof WorksheetScriptEnv) != cfg.pool())
            {
               box.dispose();
               throw new IllegalStateException("the sandbox is not in the pool mode of " + cfg);
            }

            return box;
         }
         finally {
            cfg.clear();
         }
      }
   }

   private static final Object CONFIG_LOCK = new Object();

   /**
    * Run one formula in an existing sandbox, e.g. one of several threads sharing it.
    */
   public static List<String> run(String formula, Shape shape, AssetQuerySandbox box,
                                  ReadPattern read) throws Exception
   {
      return switch(shape) {
         case FTL -> ftl(formula, box, read, false);
         case FTL_UNDER_CF2 -> ftl(formula, box, read, true);
         case CONDITION -> condition(formula, box, read);
         case CALC_FIELD -> calcField(formula, box, read);
      };
   }

   /**
    * @return the standard base table, a new instance each call.
    */
   public static DefaultTableLens baseTable() {
      Object[][] data = new Object[ROWS + 1][];
      data[0] = new Object[] { "id", "value", "name", "day", "flag" };

      for(int r = 1; r <= ROWS; r++) {
         Object[] row = {
            r,
            ((r * 37) % 1000) / 10.0 - 20,
            NAMES[r % NAMES.length],
            new Timestamp(DAY0 + r * 86_400_000L + (r % 24) * 3_600_000L),
            r % 3 == 0
         };

         // a null every 17th row, in each of the value columns in turn
         if(r % 17 == 0) {
            row[1 + (r / 17) % 4] = null;
         }

         data[r] = row;
      }

      if(COUNT_BASE.get()) {
         CountingTable table = new CountingTable(data);
         LAST_BASE.set(table);
         return table;
      }

      return new DefaultTableLens(data);
   }

   /**
    * A base table that counts its moreRows() calls, for a performance probe only (see
    * {@link #COUNT_BASE}).
    */
   static final class CountingTable extends DefaultTableLens {
      CountingTable(Object[][] data) {
         super(data);
      }

      @Override
      public boolean moreRows(int row) {
         moreRows.incrementAndGet();
         return super.moreRows(row);
      }

      final AtomicLong moreRows = new AtomicLong();
   }

   /** when true on a thread, its base tables count moreRows() calls; off by default */
   static final ThreadLocal<Boolean> COUNT_BASE = ThreadLocal.withInitial(() -> false);
   /** the last counting base table this thread built */
   static final ThreadLocal<CountingTable> LAST_BASE = new ThreadLocal<>();

   private static List<String> ftl(String formula, AssetQuerySandbox box, ReadPattern read,
                                   boolean cf2) throws Exception
   {
      TableLens base = baseTable();
      FormulaTableLens lens = new FormulaTableLens(base, new String[] { COLUMN },
         new String[] { formula }, box.getScriptEnv(), box.getScope());
      int fcol = base.getColCount();
      TableLens table = lens;
      int rows = ROWS;

      if(cf2) {
         // a pass-most JavaScript condition: id != 90 + 7
         AssetCondition condition = new AssetCondition();
         condition.setOperation(XCondition.EQUAL_TO);
         condition.setNegated(true);
         condition.setType(XSchema.INTEGER);
         ExpressionValue value = new ExpressionValue();
         value.setExpression("90 + 7");
         value.setType(ExpressionValue.JAVASCRIPT);
         condition.addValue(value);
         ConditionList list = new ConditionList();
         list.append(new ConditionItem(new AttributeRef(null, "id"), condition, 0));
         table = PostProcessor.filter(lens, new ConditionGroup(lens, list, box), box);
         rows = ROWS - 1;
      }

      // the failed rows of the formula lens by base row, which is the id
      Map<Integer, String> errors = new HashMap<>();
      String[] cells = new String[rows];
      TableLens t = table;
      boolean filtered = cf2;
      CellReader reader = r -> {
         probe(t, lens, r, errors);
         int id = filtered ? (Integer) t.getObject(r, 0) : r;
         String error = errors.get(id);
         String cell = error != null ? error : str(t.getObject(r, fcol));
         cells[r - 1] = filtered ? id + "|" + cell : cell;
      };

      switch(read.kind()) {
      case PAGED_100:
         for(int k = 1; (k - 1) * 100 < rows; k++) {
            int last = Math.min(k * 100, rows);
            probe(t, lens, last, errors);

            for(int r = (k - 1) * 100 + 1; r <= last; r++) {
               reader.read(r);
            }
         }
         break;
      case INVALIDATE_THEN_SEQUENTIAL:
         for(int r = 1; r <= 300; r++) {
            reader.read(r);
         }

         ((TableFilter) table).invalidate();

         // the formula lens computes its rows again, and fails them again
         if(!cf2) {
            errors.clear();
         }

         Arrays.fill(cells, null);

         for(int r = 1; r <= rows; r++) {
            reader.read(r);
         }
         break;
      default:
         for(int r : read.order(rows)) {
            reader.read(r);
         }
      }

      List<String> result = new ArrayList<>(Arrays.asList(cells));

      // the table has no more rows than expected
      if(table.moreRows(rows + 1)) {
         result.add("extra row " + (rows + 1));
      }

      return result;
   }

   /**
    * The cells a new formula lens computes from row {@code from} of the standard table on, the
    * pool off, read sequentially: what a lens gives once its vars restart at that row. For
    * scripts that read only the current row's fields and do not fail.
    */
   public static List<String> restartedAt(String formula, int from) {
      TableLens base = baseTable();
      Object[][] data = new Object[ROWS - from + 2][];

      for(int r = 0; r < data.length; r++) {
         int row = r == 0 ? 0 : from + r - 1;
         data[r] = new Object[base.getColCount()];

         for(int c = 0; c < data[r].length; c++) {
            data[r][c] = base.getObject(row, c);
         }
      }

      AssetQuerySandbox box = sandbox(RelConfig.off());

      try {
         TableLens part = new DefaultTableLens(data);
         FormulaTableLens lens = new FormulaTableLens(part, new String[] { COLUMN },
            new String[] { formula }, box.getScriptEnv(), box.getScope());
         List<String> cells = new ArrayList<>();

         for(int r = 1; r < data.length; r++) {
            lens.moreRows(r);
            cells.add(str(lens.getObject(r, part.getColCount())));
         }

         return cells;
      }
      finally {
         box.dispose();
      }
   }

   /**
    * Read several formula lenses over the standard table in one sandbox, interleaved:
    * {@code step} rows of each in turn, so their batches claim contexts one after the other
    * and take over each other's idle homes (B1 residual hand-offs). For scripts that do not
    * fail: a failed row stops the read.
    *
    * @return the cells of each lens, as {@link #run} gives them for a sequential FTL read.
    */
   public static List<List<String>> interleaved(List<String> formulas, AssetQuerySandbox box,
                                                int step)
   {
      List<FormulaTableLens> lenses = new ArrayList<>();
      List<List<String>> cells = new ArrayList<>();

      for(String formula : formulas) {
         TableLens base = baseTable();
         lenses.add(new FormulaTableLens(base, new String[] { COLUMN },
            new String[] { formula }, box.getScriptEnv(), box.getScope()));
         cells.add(new ArrayList<>());
      }

      int fcol = baseTable().getColCount();

      for(int from = 1; from <= ROWS; from += step) {
         for(int i = 0; i < lenses.size(); i++) {
            for(int r = from; r < from + step && r <= ROWS; r++) {
               lenses.get(i).moreRows(r);
               cells.get(i).add(str(lenses.get(i).getObject(r, fcol)));
            }
         }
      }

      for(int i = 0; i < lenses.size(); i++) {
         if(lenses.get(i).moreRows(ROWS + 1)) {
            cells.get(i).add("extra row " + (ROWS + 1));
         }
      }

      return cells;
   }

   /**
    * Make row {@code r} of {@code table} available. A formula error thrown on the way is
    * recorded against every failed row of the formula lens it reports (a batch reports all
    * its failed rows in one exception, Testing #77123 O2), else the last row the lens added,
    * and the read resumed, as a reader that skips a failed row would.
    */
   private static void probe(TableLens table, FormulaTableLens lens, int r,
                             Map<Integer, String> errors) throws Exception
   {
      for(int attempt = 0; ; attempt++) {
         try {
            table.moreRows(r);
            return;
         }
         catch(RuntimeException ex) {
            int[] failed = failedRows(ex);

            if(failed.length == 0) {
               failed = new int[] { processedRows(lens) };
            }

            if(attempt > ROWS || errors.containsKey(failed[0]) && attempt > 0) {
               throw new IllegalStateException("no progress past row " + failed[0], ex);
            }

            String error = error(ex);

            for(int row : failed) {
               errors.put(row, error);
            }
         }
      }
   }

   /**
    * The formula lens rows an exception reports as failed, from the expression failure in its
    * cause chain; the lens rows are the ids of the standard table.
    */
   private static int[] failedRows(Throwable ex) {
      for(int depth = 0; ex != null && depth < 16; ex = ex.getCause(), depth++) {
         if(ex instanceof ExpressionFailedException failure) {
            return failure.getFailedRows();
         }
      }

      return new int[0];
   }

   private static List<String> condition(String formula, AssetQuerySandbox box,
                                         ReadPattern read)
   {
      TableLens base = baseTable();
      List<String> result = new ArrayList<>();

      // a worksheet with several JavaScript conditions builds them one after the other
      for(int i = 0; i < CONDITIONS; i++) {
         AssetCondition condition = new AssetCondition();
         condition.setOperation(XCondition.GREATER_THAN);
         condition.setType(XSchema.DOUBLE);
         ExpressionValue value = new ExpressionValue();
         value.setExpression(formula);
         value.setType(ExpressionValue.JAVASCRIPT);
         condition.addValue(value);
         ConditionList list = new ConditionList();
         list.append(new ConditionItem(new AttributeRef(null, "value"), condition, 0));
         ConditionGroup group;

         try {
            group = new ConditionGroup(base, list, box);
         }
         catch(RuntimeException ex) {
            result.add(error(ex));
            continue;
         }

         // the group evaluates a clone of the condition, whose value the script's result
         // replaced; the condition built here keeps its expression
         XCondition evaluated = ((XConditionGroup.CondItem) group.getItem(0)).condition;
         result.add("v:" + str(((Condition) evaluated).getValue(0)));
         String[] cells = new String[ROWS];

         for(int r : read.order(ROWS)) {
            try {
               cells[r - 1] = String.valueOf(group.evaluate(base, r));
            }
            catch(RuntimeException ex) {
               cells[r - 1] = error(ex);
            }
         }

         result.addAll(Arrays.asList(cells));
      }

      return result;
   }

   /**
    * A calc field over the aggregates value = sum(value), id = max(id), name = count(name) of
    * each 50-row group; field['x'] of an aggregate name reads the aggregate. The read pattern
    * is ignored (see below).
    */
   private static List<String> calcField(String formula, AssetQuerySandbox box,
                                         ReadPattern read)
   {
      String expression = formula.replaceAll(
         "field\\s*\\[\\s*(['\"])(value|id|name)\\1\\s*\\]", "$2");
      TableLens base = baseTable();
      int groups = ROWS / GROUP;
      String[] cells = new String[groups];
      CalcFieldFormula calc;

      try {
         calc = new CalcFieldFormula(expression, new String[] { "value", "id", "name" },
            new Formula[] { new SumFormula(), new MaxFormula(), new CountFormula() },
            new int[] { 1, 0, 2 }, box.getScriptEnv(), box.getScope());
      }
      catch(RuntimeException ex) {
         return List.of(error(ex));
      }

      // an aggregation visits its groups in its own order, so a stateful calc field depends on
      // that order and a read pattern does not apply: the groups are always computed in order
      int[] order = ReadPattern.SEQUENTIAL.order(groups);

      // an aggregation runs its calc field groups under one span, as SummaryFilter does
      try(ScriptSpan span = CalcFieldFormula.openSpan(new Formula[] { calc })) {
         for(int g : order) {
            calc.reset();

            for(int r = (g - 1) * GROUP + 1; r <= g * GROUP; r++) {
               calc.addValue(new Object[] {
                  null, base.getObject(r, 1), base.getObject(r, 0), base.getObject(r, 2) });
            }

            try {
               cells[g - 1] = str(calc.getResult());
            }
            catch(RuntimeException ex) {
               cells[g - 1] = error(ex);
            }
         }
      }

      return Arrays.asList(cells);
   }

   /**
    * The canonical string of a cell value: its type and value, containers by content. A
    * container (or a GraalJS value, which the pool off can return for a script object) is
    * followed by {@link #CONTENT} and its content with numbers as plain decimals, so a test
    * can tell the documented host-copy display drift (C6: a copied object with 1.0 for 1)
    * from a real difference.
    */
   public static String str(Object value) {
      String exact = exact(value);
      return value instanceof Object[] || value instanceof Collection || value instanceof Map ||
         value instanceof Value ? exact + CONTENT + content(value) : exact;
   }

   private static String exact(Object value) {
      if(value == null) {
         return "null";
      }

      if(value instanceof String) {
         return "S:" + value;
      }

      if(value instanceof Date date) {
         return value.getClass().getSimpleName() + ":" + date.getTime();
      }

      if(value instanceof Object[] array) {
         StringJoiner join = new StringJoiner(",", "A[", "]");
         Arrays.stream(array).forEach(v -> join.add(exact(v)));
         return join.toString();
      }

      if(value instanceof List<?> list) {
         StringJoiner join = new StringJoiner(",", "L[", "]");
         list.forEach(v -> join.add(exact(v)));
         return join.toString();
      }

      if(value instanceof Map<?, ?> map) {
         StringJoiner join = new StringJoiner(",", "M{", "}");
         map.forEach((k, v) -> join.add(k + "=" + exact(v)));
         return join.toString();
      }

      return value.getClass().getSimpleName() + ":" + value;
   }

   /**
    * The content of a value: each element with its type class (N: number as a plain decimal,
    * so 1 and 1.0 are equal but "1" is not; S: string; B: boolean; D: date), containers and
    * GraalJS values by their elements or members.
    */
   private static String content(Object value) {
      try {
         if(value instanceof Value v) {
            if(v.isNull()) {
               return "null";
            }
            else if(v.isString()) {
               return "S:" + v.asString();
            }
            else if(v.isBoolean()) {
               return "B:" + v.asBoolean();
            }
            else if(v.isNumber()) {
               return content(v.asDouble());
            }
            else if(v.isDate() && v.isTime()) {
               return "D:" + v.asInstant().toEpochMilli();
            }
            else if(v.hasArrayElements()) {
               StringJoiner join = new StringJoiner(",", "[", "]");

               for(long i = 0; i < v.getArraySize(); i++) {
                  join.add(content(v.getArrayElement(i)));
               }

               return join.toString();
            }
            else if(v.hasMembers()) {
               StringJoiner join = new StringJoiner(",", "{", "}");

               for(String key : v.getMemberKeys()) {
                  join.add(key + "=" + content(v.getMember(key)));
               }

               return join.toString();
            }

            return "V:" + v;
         }
      }
      catch(RuntimeException ex) {
         return "<unreadable " + ex.getClass().getSimpleName() + ">";
      }

      if(value instanceof Number number) {
         double d = number.doubleValue();
         return "N:" + (Double.isFinite(d)
            ? new BigDecimal(d).stripTrailingZeros().toPlainString() : String.valueOf(d));
      }

      if(value instanceof Date date) {
         return "D:" + date.getTime();
      }

      if(value instanceof String || value instanceof Boolean) {
         return (value instanceof String ? "S:" : "B:") + value;
      }

      if(value instanceof Object[] array) {
         return content(Arrays.asList(array));
      }

      if(value instanceof Collection<?> list) {
         StringJoiner join = new StringJoiner(",", "[", "]");
         list.forEach(v -> join.add(content(v)));
         return join.toString();
      }

      if(value instanceof Map<?, ?> map) {
         StringJoiner join = new StringJoiner(",", "{", "}");
         map.forEach((k, v) -> join.add(k + "=" + content(v)));
         return join.toString();
      }

      return value == null ? "null" : value.getClass().getSimpleName() + ":" + value;
   }

   /** separates the exact string of a container cell from its content */
   public static final String CONTENT = " ~content~ ";

   /**
    * @return the cell string of an exception; a GraalJS multi-threaded access error anywhere
    * in its cause chain is also counted in {@link #MULTI_THREADED}.
    */
   static String error(Throwable ex) {
      for(Throwable t = ex; t != null && t.getCause() != t; t = t.getCause()) {
         if(t.getMessage() != null && t.getMessage().contains("Multi threaded access")) {
            MULTI_THREADED.incrementAndGet();
            break;
         }
      }

      List<String> messages = MESSAGES.get();

      if(messages != null) {
         StringBuilder chain = new StringBuilder();

         for(Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            chain.append(chain.length() == 0 ? "" : " <- ").append(t);
         }

         messages.add(chain.toString());
      }

      // opt-in (-Drel.errlog=true): log each distinct cell error, so an unexplained mismatch
      // (R1, Testing #77123) records its message
      if(Boolean.getBoolean("rel.errlog")) {
         StringBuilder k = new StringBuilder();
         for(Throwable t = ex; t != null && k.length() < 600; t = t.getCause() == t ? null : t.getCause()) {
            k.append(t.getClass().getSimpleName()).append(": ")
               .append(String.valueOf(t.getMessage()).replaceAll("\\s+", " ")).append(" <- ");
         }
         String key = k.length() > 400 ? k.substring(0, 400) : k.toString();
         boolean odd = key.matches("(?is).*(interrupt|timed out|timeout|cancel|closed|multi thread).*");
         if(ERRS.merge(key, 1, Integer::sum) == 1 || odd) {
            StringBuilder fr = new StringBuilder();
            StackTraceElement[] st = ex.getStackTrace();
            for(int i = 0; i < Math.min(8, st.length); i++) fr.append(" | ").append(st[i]);
            System.out.println("[rel-errlog] " + (odd ? "ODD " : "NEW ") + java.time.LocalTime.now() + " " +
               Thread.currentThread().getName() + " " + key + fr);
         }
      }

      return "E:" + ex.getClass().getSimpleName();
   }

   static final java.util.concurrent.ConcurrentHashMap<String, Integer> ERRS = new java.util.concurrent.ConcurrentHashMap<>();

   static {
      if(Boolean.getBoolean("rel.errlog")) {
         Runtime.getRuntime().addShutdownHook(new Thread(() -> ERRS.forEach(
            (k, v) -> System.out.println("[rel-errlog] " + v + " x " + k))));
      }
   }

   /** when set on a thread, the message chain of each cell error of its runs is added to it */
   static final ThreadLocal<List<String>> MESSAGES = new ThreadLocal<>();

   /** the cell errors seen so far that were GraalJS multi-threaded access errors */
   public static final AtomicLong MULTI_THREADED = new AtomicLong();

   /**
    * @return the rows the formula lens has added, i.e. the last row it computed or failed.
    */
   static int processedRows(FormulaTableLens lens) {
      try {
         return (Integer) PROCESSED.invoke(lens);
      }
      catch(Exception ex) {
         throw new IllegalStateException(ex);
      }
   }

   @FunctionalInterface
   private interface CellReader {
      void read(int r) throws Exception;
   }

   private static final String[] NAMES = { "CA", "NJ", "New York", "tx", "", "a b c", "Z" };
   // 2020-01-01T00:00:00Z
   private static final long DAY0 = 1_577_836_800_000L;
   private static final Method PROCESSED;

   static {
      try {
         PROCESSED = FormulaTableLens.class.getDeclaredMethod("getProcessedRowCount");
         PROCESSED.setAccessible(true);
      }
      catch(NoSuchMethodException ex) {
         throw new ExceptionInInitializerError(ex);
      }
   }
}
