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

import inetsoft.util.CoreTool;

import javax.json.JsonValue;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Reduce a parsed JSON response to its SHAPE: which paths exist and what type each leaf is,
 * carrying none of the values.
 *
 * <p>This exists so a caller can be told what an endpoint returns without being handed what it
 * returned. The distinction is the whole point: a REST response body is customer data — a page of
 * charges, of contacts, of tickets — while its shape is a property of the connector. Only the
 * latter is safe to record once and reuse for everyone.</p>
 *
 * <p>VALUES ARE DROPPED, BUT THAT IS NOT SUFFICIENT. Object keys become part of the shape, and some
 * responses key an object BY DATA — {@code {"cus_9f2a": {...}, "cus_71bd": {...}}}. Enumerating
 * those keys would record thousands of customer ids under the guise of structure, and produce a
 * shape nobody can use besides. Such objects are collapsed to a single {@link #WILDCARD_KEY} entry;
 * see {@link #isDictionary}.</p>
 *
 * <p>TYPES ARE BEST-EFFORT AND DELIBERATELY SO. They come from {@link JsonTable#getTypeClass}, the
 * same function the table itself uses, so a number is reported as {@code double} exactly as the
 * built table will report it. What is NOT reproduced is date detection on string values: that lives
 * in {@link BaseJsonTable#parseValue} and depends on per-column state accumulated across rows
 * ({@code colTypes2}), so a string holding a date may be reported here as {@code string} while the
 * table types it as {@code date}. Structure is what this is for; a type that reads narrower than
 * the table's costs a caller nothing it cannot recover by looking at the built table.</p>
 */
public final class JsonShapeDistiller {
   /**
    * The key standing in for every key of an object that is keyed by data rather than by field
    * name. A caller must treat a path through this segment as NOT referenceable as a column: the
    * real segment is a value, and it differs per response.
    */
   public static final String WILDCARD_KEY = "*";

   /** Beyond this many nodes the shape is cut off and reported truncated. */
   public static final int DEFAULT_MAX_NODES = 2000;

   /** Beyond this depth recursion stops and the subtree is reported truncated. */
   public static final int DEFAULT_MAX_DEPTH = 16;

   /**
    * An object with more children than this whose children all share one shape is read as a
    * dictionary rather than a record. Deliberately high: the count alone must not decide, because
    * some APIs return a record with dozens of flat fields. See {@link #isDictionary}.
    */
   static final int DICTIONARY_MIN_KEYS = 50;

   /**
    * Keys that are values rather than field names. Matched against a SINGLE key, and one match
    * collapses the whole object — a dictionary with three entries is still a dictionary, and no
    * count-based rule can see it.
    *
    * <p>This is a heuristic and it will miss: a small map keyed by person names or by user-defined
    * labels reads as a record. It narrows the exposure from certain to rare, and does not remove
    * it.</p>
    */
   private static final Pattern[] DATA_LIKE_KEY = {
      // uuid
      Pattern.compile("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"),
      // Prefixed opaque id: cus_9f2aB1, evt_00Ab, acct_1H2x.
      //
      // THE SUFFIX MUST MIX LETTERS AND DIGITS, and both halves of that requirement are there to
      // stop a false positive -- which is the expensive direction here, because a single matching key
      // collapses its whole parent object and every real field name in it becomes "*".
      //
      //  - Without the DIGIT requirement the pattern matches ordinary snake_case: has_more,
      //    fee_details, available_on, reporting_category are all "two-to-six lowercase letters,
      //    underscore, four-plus alphanumerics". snake_case is the prevailing JSON field convention,
      //    so that is not an edge case, it is most responses.
      //  - Without the LETTER requirement it matches a word followed by a number -- batch_2024,
      //    fy_2023 -- which is how fiscal-year, quarter and batch fields are commonly named.
      //
      // The cost is an id whose suffix is purely numeric (order_123456) no longer matching. That is
      // the cheaper way to be wrong: a missed key leaves a few keys recorded in a small dictionary,
      // and a large one is still caught by the homogeneity rule below, whereas a false positive
      // destroys a legitimate record's field names outright.
      Pattern.compile("^[a-z]{2,6}_(?=[A-Za-z0-9]*[0-9])(?=[A-Za-z0-9]*[A-Za-z])[A-Za-z0-9]{4,}$"),
      // bare opaque id / long hex / digits
      Pattern.compile("^[0-9]+$"),
      Pattern.compile("(?i)^[0-9a-f]{16,}$"),
      // ISO date or timestamp
      Pattern.compile("^\\d{4}-\\d{2}-\\d{2}([T ].*)?$"),
      // email
      Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
   };

   private JsonShapeDistiller() {
   }

   /** The distilled shape, plus whether a cap cut it short. */
   public static final class Result {
      private final Object shape;
      private final boolean truncated;

      Result(Object shape, boolean truncated) {
         this.shape = shape;
         this.truncated = truncated;
      }

      /**
       * A {@code Map<String, Object>} for an object, a single-element {@code List} for an array, or
       * a type name for a leaf. Never null.
       *
       * UNMODIFIABLE, recursively. It is held by a query, shared with every clone of that query and
       * with the response that reports it, so in-place mutation anywhere would corrupt state shared
       * across query instances. See {@link #freeze}.
       */
      public Object getShape() {
         return shape;
      }

      /**
       * True when a node or depth cap stopped the walk. A consumer MUST distinguish this from a
       * complete shape: under truncation, "this path is absent" means "not reached", not "not there".
       */
      public boolean isTruncated() {
         return truncated;
      }
   }

   public static Result distill(Object json) {
      return distill(json, DEFAULT_MAX_NODES, DEFAULT_MAX_DEPTH);
   }

   public static Result distill(Object json, int maxNodes, int maxDepth) {
      Budget budget = new Budget(maxNodes, maxDepth);
      Object shape = shapeOf(json, budget, 0);
      return new Result(freeze(shape), budget.truncated);
   }

   /**
    * Make the shape unmodifiable, all the way down.
    *
    * The result outlives this call by a long way: {@code TabularQuery} holds it, every clone of that
    * query shares the same reference, {@code TabularHandler} copies the reference back onto the
    * original, and the web layer serializes it. Nothing mutates it today, and "immutable once set"
    * was documented as an invariant -- so it is enforced rather than left as convention, because a
    * single in-place `put` anywhere in that chain would corrupt state shared across query instances
    * with no failure at the point of the mistake.
    *
    * Recursive, not just the top level: a shape is a tree, and wrapping only the root would leave
    * every nested object mutable while claiming the whole thing was not. Applied once here rather
    * than inside the walk, so the merging the walk does still operates on writable maps.
    */
   @SuppressWarnings("unchecked")
   private static Object freeze(Object shape) {
      if(shape instanceof Map) {
         Map<String, Object> frozen = new LinkedHashMap<>();

         for(Map.Entry<String, Object> entry : ((Map<String, Object>) shape).entrySet()) {
            frozen.put(entry.getKey(), freeze(entry.getValue()));
         }

         return Collections.unmodifiableMap(frozen);
      }

      if(shape instanceof List) {
         List<Object> frozen = new ArrayList<>();

         for(Object element : (List<Object>) shape) {
            frozen.add(freeze(element));
         }

         return Collections.unmodifiableList(frozen);
      }

      return shape;
   }

   private static Object shapeOf(Object value, Budget budget, int depth) {
      if(depth > budget.maxDepth) {
         budget.truncated = true;
         return CoreTool.STRING;
      }

      if(!budget.spend()) {
         return CoreTool.STRING;
      }

      if(value instanceof Map) {
         return objectShape((Map<?, ?>) value, budget, depth);
      }

      if(value instanceof List) {
         return arrayShape((List<?>) value, budget, depth);
      }

      return leafType(value);
   }

   private static Object objectShape(Map<?, ?> map, Budget budget, int depth) {
      Map<String, Object> children = new LinkedHashMap<>();

      for(Map.Entry<?, ?> entry : map.entrySet()) {
         if(!(entry.getKey() instanceof String)) {
            continue;
         }

         children.put((String) entry.getKey(), shapeOf(entry.getValue(), budget, depth + 1));
      }

      if(children.isEmpty() || !isDictionary(children)) {
         return children;
      }

      // Collapse. The keys were data, so they are dropped rather than recorded, and the values --
      // which are homogeneous by the very test that got us here -- are merged into one entry.
      Object merged = null;

      for(Object child : children.values()) {
         merged = merged == null ? child : merge(merged, child);
      }

      Map<String, Object> collapsed = new LinkedHashMap<>();
      collapsed.put(WILDCARD_KEY, merged);
      return collapsed;
   }

   /**
    * An array contributes ONE element shape, merged across every element present.
    *
    * <p>Merged rather than taken from the first element because an optional field is exactly what a
    * caller needs to know about and exactly what the first element may omit. The merge is still
    * bounded by the response: a field absent from every element here is absent from the shape.</p>
    */
   private static Object arrayShape(List<?> list, Budget budget, int depth) {
      Object merged = null;

      for(Object element : list) {
         Object shape = shapeOf(element, budget, depth + 1);
         merged = merged == null ? shape : merge(merged, shape);
      }

      List<Object> result = new ArrayList<>(1);

      if(merged != null) {
         result.add(merged);
      }

      return result;
   }

   /**
    * Whether an object is keyed by data rather than by field name.
    *
    * <p>TWO RULES, because they catch different things and neither catches both:</p>
    *
    * <ul>
    *   <li><b>Key shape</b> — any single key that reads as an id, a date or an email. This is the
    *   only rule that can see a LOW-CARDINALITY dictionary: a tenant with three customers produces
    *   three keys, and no threshold will ever fire on that.</li>
    *   <li><b>Homogeneity and count</b> — many children that all share one shape. This catches a
    *   dictionary whose keys look perfectly ordinary (country codes, month names, SKUs), which the
    *   key-shape rule cannot.</li>
    * </ul>
    *
    * <p>The second rule requires homogeneity and not merely a count, so that a WIDE RECORD is not
    * mistaken for a dictionary and stripped of its real field names. A record's children differ in
    * shape — a number here, an object there; a dictionary's values are alike, and that is the
    * discriminator.</p>
    */
   private static boolean isDictionary(Map<String, Object> children) {
      for(String key : children.keySet()) {
         for(Pattern pattern : DATA_LIKE_KEY) {
            if(pattern.matcher(key).matches()) {
               return true;
            }
         }
      }

      if(children.size() < DICTIONARY_MIN_KEYS) {
         return false;
      }

      Object first = null;

      for(Object child : children.values()) {
         if(first == null) {
            first = child;
         }
         else if(!first.equals(child)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Combine two shapes for the same path.
    *
    * <p>Kinds that disagree collapse to {@code string}, which is what the table does too: a column
    * seen with two types is typed {@code String} ({@link JsonTable} walkRecord's mixed-type
    * branch).</p>
    */
   @SuppressWarnings("unchecked")
   static Object merge(Object a, Object b) {
      if(a.equals(b)) {
         return a;
      }

      if(a instanceof Map && b instanceof Map) {
         Map<String, Object> left = (Map<String, Object>) a;
         Map<String, Object> right = (Map<String, Object>) b;
         Map<String, Object> merged = new LinkedHashMap<>(left);

         for(Map.Entry<String, Object> entry : right.entrySet()) {
            Object existing = merged.get(entry.getKey());
            merged.put(entry.getKey(),
                       existing == null ? entry.getValue() : merge(existing, entry.getValue()));
         }

         return merged;
      }

      if(a instanceof List && b instanceof List) {
         List<Object> left = (List<Object>) a;
         List<Object> right = (List<Object>) b;

         if(left.isEmpty()) {
            return right;
         }

         if(right.isEmpty()) {
            return left;
         }

         List<Object> merged = new ArrayList<>(1);
         merged.add(merge(left.get(0), right.get(0)));
         return merged;
      }

      // A leaf against a leaf, or a leaf against a container. Either way there is no shape both
      // agree on, and the table's own answer for a column of mixed type is String.
      if(CoreTool.NULL.equals(a)) {
         return b;
      }

      if(CoreTool.NULL.equals(b)) {
         return a;
      }

      return CoreTool.STRING;
   }

   /**
    * The XSchema type name for a leaf, taken from the same function the table uses so the two
    * agree. A JSON number therefore reports as {@code double}, never {@code integer}.
    */
   private static String leafType(Object value) {
      if(value == null || isJsonNull(value)) {
         return CoreTool.NULL;
      }

      return CoreTool.getDataType(JsonTable.getTypeClass(value));
   }

   /**
    * JSON-P models null as a VALUE ({@link JsonValue#NULL}) rather than as a Java null, and
    * {@code getTypeClass} has no branch for it — it would fall through and report the
    * implementation class as the type.
    */
   private static boolean isJsonNull(Object value) {
      return value instanceof JsonValue
         && ((JsonValue) value).getValueType() == JsonValue.ValueType.NULL;
   }

   /** Node and depth caps, carried through the walk so one place records that a cap fired. */
   private static final class Budget {
      private final int maxNodes;
      private final int maxDepth;
      private int nodes;
      private boolean truncated;

      Budget(int maxNodes, int maxDepth) {
         this.maxNodes = maxNodes;
         this.maxDepth = maxDepth;
      }

      boolean spend() {
         if(nodes >= maxNodes) {
            truncated = true;
            return false;
         }

         nodes++;
         return true;
      }
   }
}
