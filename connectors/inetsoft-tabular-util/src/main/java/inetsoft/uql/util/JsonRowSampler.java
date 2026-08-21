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
package inetsoft.uql.util;

import java.util.*;

/**
 * Take a bounded sample of the rows a JSON response actually carried, in the response's own
 * structure.
 *
 * <p>The counterpart of {@link JsonShapeDistiller}, and deliberately its opposite: that one reports
 * which paths exist and drops every value, this one reports values. So the two answer different
 * questions — where the rows are and what they are called, versus what is in them — and neither
 * replaces the other.</p>
 *
 * <p>CARRYING VALUES IS THE WHOLE POINT AND ALSO THE WHOLE RISK. These are customer data, not a
 * property of the connector: unlike a shape, a sample must never be recorded once and reused for
 * everyone, and the amount of it that travels has to be bounded at the source. Hence the budget
 * below, and hence the caller-side kill switch that decides whether to ask for a sample at all
 * ({@code EndpointJsonQueryRunner}).</p>
 *
 * <p>THE BUDGET HAS FOUR LIMITS, and they are not interchangeable:</p>
 * <ul>
 *   <li>rows — how many rows a caller needs to read a set of parameter values off;</li>
 *   <li>nodes — the total size of the sample, which rows alone do not bound: one row of a payments
 *       API can carry a hundred fields;</li>
 *   <li>depth — how far into a nested object the sample goes;</li>
 *   <li>string length — the one limit that a row count cannot approximate at all, since a single
 *       description, HTML body or base64 blob outweighs every other field in the response.</li>
 * </ul>
 *
 * <p>WHOLE ROWS ONLY. When the node budget runs out mid-row the row is dropped rather than
 * half-copied, because a half row reads as a row whose remaining fields the response did not have.
 * The same reasoning drives the markers: an over-long value is REPLACED by
 * {@code "<omitted: N chars>"} and never truncated in place, because a truncated id is a wrong
 * value that looks entirely valid and will be sent back as a parameter, whereas a marker cannot be
 * mistaken for data. Any limit firing sets {@link Result#isTruncated()}: "this path is absent from
 * the sample" then means "not sampled", not "not in the response".</p>
 */
public final class JsonRowSampler {
   /** Rows per sample. Enough to read a set of parameter values off, not enough to be a data feed. */
   public static final int DEFAULT_MAX_ROWS = 20;

   /** Total nodes across all rows. Beyond this, whole rows are dropped. */
   public static final int DEFAULT_MAX_NODES = 2000;

   /** Nesting depth beyond which a subtree is replaced by {@link #OMITTED_DEPTH}. */
   public static final int DEFAULT_MAX_DEPTH = 8;

   /**
    * String values longer than this are replaced by a marker. Ids, dates, emails and gids — the
    * values a caller samples for — are far shorter; bodies and blobs are far longer.
    */
   public static final int DEFAULT_MAX_STRING = 200;

   /** Stands in for a subtree that the depth cap cut off. Never a value from the response. */
   public static final String OMITTED_DEPTH = "<omitted: depth>";

   private JsonRowSampler() {
   }

   /** The sampled rows, plus whether any limit cut the sample short. */
   public static final class Result {
      private final List<?> rows;
      private final boolean truncated;

      Result(List<?> rows, boolean truncated) {
         this.rows = rows;
         this.truncated = truncated;
      }

      /**
       * The rows, in the response's own structure — nested objects stay nested, and NOT flattened
       * the way {@link JsonTable#loadStreamed} flattens them into columns. Never null; empty when
       * the selection held no rows.
       *
       * <p>UNMODIFIABLE, recursively. It is held by a query, shared with every clone of that query
       * and with the response that reports it, so in-place mutation anywhere would corrupt state
       * shared across query instances.</p>
       */
      public List<?> getRows() {
         return rows;
      }

      /**
       * True when the sample is not a faithful copy of the rows it was taken from: rows were left
       * out, or a value was replaced by a marker. A caller extracting parameter values has to
       * treat this as "I have not necessarily seen every distinct value".
       */
      public boolean isTruncated() {
         return truncated;
      }
   }

   public static Result sample(Object selectedData, int maxRows) {
      return sample(selectedData, maxRows, DEFAULT_MAX_NODES, DEFAULT_MAX_DEPTH, DEFAULT_MAX_STRING);
   }

   /**
    * @param selectedData what the row path selected — a list of rows to sample from. Anything else
    *                     (a single object, a scalar, null) yields no rows, which is not an error:
    *                     it is a response this sample has nothing to say about.
    * @param maxRows      rows to keep; zero or less yields no rows.
    */
   public static Result sample(Object selectedData, int maxRows, int maxNodes, int maxDepth,
                               int maxString)
   {
      if(!(selectedData instanceof List) || maxRows <= 0) {
         return new Result(List.of(), false);
      }

      List<?> selected = (List<?>) selectedData;
      List<Object> rows = new ArrayList<>();
      Budget budget = new Budget(maxNodes, maxDepth, maxString);
      boolean dropped = false;

      for(int i = 0; i < selected.size(); i++) {
         // Reached here with an element still in hand, so there IS something the sample leaves out.
         if(rows.size() >= maxRows) {
            dropped = true;
            break;
         }

         Object row = copyOf(selected.get(i), budget, 0);

         if(budget.exhausted) {
            dropped = true;
            break;
         }

         rows.add(row);
      }

      return new Result(Collections.unmodifiableList(rows), dropped || budget.trimmed);
   }

   /**
    * Copy one value, spending budget as it goes.
    *
    * <p>The copy is built fresh rather than sharing the response's own maps: the response object is
    * still being consumed by {@code loadStreamed} and by lookups after this returns, and a sample
    * that aliased it would report whatever those did next. Each container is wrapped unmodifiable
    * as it is finished, so the tree is immutable on the way out with no second pass.</p>
    *
    * @return the copy, or an incomplete value once {@link Budget#exhausted} is set — the caller
    *         discards the whole row in that case, so what is returned no longer matters.
    */
   private static Object copyOf(Object value, Budget budget, int depth) {
      if(!budget.spend()) {
         return null;
      }

      // Only containers are cut by depth. A scalar at the cut is already bounded by maxString, and
      // dropping it would cost the caller a value it could have used for nothing.
      if((value instanceof Map || value instanceof List) && depth >= budget.maxDepth) {
         budget.trimmed = true;
         return OMITTED_DEPTH;
      }

      if(value instanceof Map) {
         Map<String, Object> copy = new LinkedHashMap<>();

         for(Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            if(!(entry.getKey() instanceof String)) {
               continue;
            }

            Object child = copyOf(entry.getValue(), budget, depth + 1);

            if(budget.exhausted) {
               return null;
            }

            copy.put((String) entry.getKey(), child);
         }

         return Collections.unmodifiableMap(copy);
      }

      if(value instanceof List) {
         List<Object> copy = new ArrayList<>();

         for(Object element : (List<?>) value) {
            Object child = copyOf(element, budget, depth + 1);

            if(budget.exhausted) {
               return null;
            }

            copy.add(child);
         }

         return Collections.unmodifiableList(copy);
      }

      return leaf(value, budget);
   }

   /**
    * A leaf value, converted through the same function the table uses
    * ({@link JsonTable#getJavaValue}) so a sampled value and the built table's value agree, and so
    * the JSON-P and Jackson object models arrive as Java values rather than as wrapper objects a
    * caller could not use.
    */
   private static Object leaf(Object value, Budget budget) {
      Object plain = JsonTable.getJavaValue(value);

      if(plain instanceof String && ((String) plain).length() > budget.maxString) {
         budget.trimmed = true;
         return "<omitted: " + ((String) plain).length() + " chars>";
      }

      return plain;
   }

   /**
    * The four limits, carried through the walk so one place records that a limit fired.
    *
    * <p>{@code exhausted} and {@code trimmed} are separate because they have different remedies:
    * the node cap means the row being copied cannot be kept at all, while a marker means the row is
    * kept and one value in it is not what the response held.</p>
    */
   private static final class Budget {
      private final int maxNodes;
      private final int maxDepth;
      private final int maxString;
      private int nodes;
      private boolean exhausted;
      private boolean trimmed;

      Budget(int maxNodes, int maxDepth, int maxString) {
         this.maxNodes = maxNodes;
         this.maxDepth = maxDepth;
         this.maxString = maxString;
      }

      boolean spend() {
         if(nodes >= maxNodes) {
            exhausted = true;
            return false;
         }

         nodes++;
         return true;
      }
   }
}
