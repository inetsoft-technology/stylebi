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
package inetsoft.uql.tabular;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Builds {@link TabularQuerySchema#getQueryParamsSchema}: a JSON Schema projection of the same
 * {@code params}/{@code dependencyMatrix} {@link TabularSchemaExtractor} already produces,
 * shaped for an LLM tool call rather than a form renderer.
 *
 * <p>{@code params}/{@code dependencyMatrix} are UNCHANGED by this class -- it only reads them.
 * dependencyMatrix already partitions parameters into "always relevant" (top-level
 * {@code properties}) and "relevant only once some other parameter has a given value"
 * ({@code allOf}/{@code if}/{@code then} branches), so pruning falls out of the structure with
 * no client-side filtering needed.</p>
 *
 * <p>THE ROOT CARRIES {@code unevaluatedProperties: false}, NEVER {@code additionalProperties}.
 * {@code additionalProperties} only sees the sibling {@code properties} map, so with every
 * conditional param deliberately sunk into an {@code allOf[].then} branch, a root
 * {@code additionalProperties: false} would reject every correctly-formed paginated request.
 * {@code unevaluatedProperties} runs after the applicators and sees branch-introduced names too.
 * The {@code additionalProperties} INSIDE a Kind A composite fragment is a different, correct
 * usage -- the value type of an open-ended map -- and is unchanged.</p>
 *
 * <p>COMPOSITE KIND A/B, AT SCHEMA-GENERATION TIME, is a WEAKER, schema-time approximation of
 * the runtime rule {@code TabularQueryContractSupport} uses at write time -- {@code query} here
 * is still blank (no {@code @Property} has been written), so a {@code dependsOn}-gated
 * composite's live skeleton cannot be observed yet. A composite with a non-empty
 * {@code dependsOn} is therefore assumed POTENTIALLY Kind A (never refused ahead of time,
 * because mislabeling a true Kind A composite as unsupported would deadlock the agent); one with
 * no {@code dependsOn} is resolved definitively against the blank query, the one case a blank
 * probe CAN answer -- Kind A if it already yields a non-empty, named skeleton, Kind B (omitted
 * from the schema entirely) otherwise.</p>
 */
public final class TabularQueryParamsSchemaBuilder {

   private TabularQueryParamsSchemaBuilder() {}

   /**
    * @param query       a freshly created query instance for this data source -- must still be
    *                    in its post-{@code createQuery}, pre-any-{@code @Property}-write state
    *                    (the same instance {@link TabularSchemaExtractor#extract} was just
    *                    called against; it does not mutate its {@code prototype} argument).
    * @param schema      the schema just extracted from that same {@code query} instance.
    * @param resolveTags whether to inline runtime candidate values for tagsMethod-backed params
    *                    with no {@code dependsOn} prerequisite (see the class doc's Kind A/B
    *                    note for why a dependent param is never resolved here regardless).
    */
   public static JsonNode build(TabularQuery query, TabularQuerySchema schema,
                                 boolean resolveTags)
   {
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());
      Map<String, JsonNode> fragments = new LinkedHashMap<>();

      for(TabularQuerySchema.Param param : schema.getParams()) {
         PropertyMeta prop = pmap.get(param.getName());

         if(prop == null) {
            continue;
         }

         JsonNode fragment = buildParamFragment(query, prop, param, resolveTags, schema);

         if(fragment != null) {
            fragments.put(param.getName(), fragment);
         }
      }

      ObjectNode properties = FACTORY.objectNode();
      ArrayNode required = FACTORY.arrayNode();

      for(TabularQuerySchema.Param param : schema.getParams()) {
         if(param.isConditional()) {
            continue;
         }

         JsonNode fragment = fragments.get(param.getName());

         if(fragment == null) {
            continue;
         }

         properties.set(param.getName(), fragment);

         // A composite (fragment "type" == "object") is NEVER placed in required, regardless of
         // @Property.required -- true even for a dependsOn-gated one only ASSUMED potentially
         // Kind A (11.4): its own completeness is resolved dynamically at write time by
         // TabularQueryContractSupport, not by this schema's static presence check, and for a
         // Kind B composite the property would not even be in `properties` to be marked required
         // (A2/A4).
         boolean composite = "object".equals(fragment.path("type").asText(""));

         if(param.isRequired() && !composite) {
            required.add(param.getName());
         }
      }

      ObjectNode root = FACTORY.objectNode();
      root.put("$schema", JSON_SCHEMA_DIALECT);
      root.put("type", "object");
      root.set("properties", properties);
      root.set("required", required);
      root.set("allOf", buildAllOf(schema, fragments));
      root.put("unevaluatedProperties", false);
      root.put("description", rootDescription(schema));

      return root;
   }

   /**
    * The root {@code description}: the required-array caveat, plus any free-standing explanatory
    * text the connector's own {@code @View} carries.
    *
    * <p>Those notes are the LABEL elements a layout places on its own rather than beside one
    * property, so there is no property fragment for them to belong to. Folding them in here is
    * what lets the published schema be the whole contract -- they used to ride out as a separate
    * {@code notes} array, which meant a consumer had to read two things to see one contract, and
    * nothing ever read the second.</p>
    */
   private static String rootDescription(TabularQuerySchema schema) {
      List<String> parts = new ArrayList<>();
      parts.add(REQUIRED_ARRAY_NOTE);

      if(schema.getNotes() != null) {
         schema.getNotes().stream()
            .filter(n -> n != null && !n.isBlank())
            .map(String::trim)
            .forEach(parts::add);
      }

      return String.join(" ", parts);
   }

   /**
    * One {@code {"if": ..., "then": ...}} entry per {@code dependencyMatrix} row, both shapes
    * (single-axis and combination, distinguished by whether the outer key contains {@code " & "}
    * -- see {@link TabularSchemaExtractor#buildDependencyMatrix}) emitted as FLAT SIBLING
    * entries, not one nested inside the other's {@code then}: the extractor already resolves a
    * two-level gate to one compound condition rather than a tree, so a flat entry per matrix key
    * is a mechanical, lossless translation of what is already computed.
    */
   private static ArrayNode buildAllOf(TabularQuerySchema schema, Map<String, JsonNode> fragments) {
      ArrayNode allOf = FACTORY.arrayNode();
      Map<String, Map<String, List<String>>> matrix = schema.getDependencyMatrix();

      if(matrix == null) {
         return allOf;
      }

      for(Map.Entry<String, Map<String, List<String>>> outer : matrix.entrySet()) {
         String key = outer.getKey();
         boolean combination = key.contains(" & ");

         for(Map.Entry<String, List<String>> row : outer.getValue().entrySet()) {
            ObjectNode ifProps = FACTORY.objectNode();
            ArrayNode ifRequired = FACTORY.arrayNode();

            if(combination) {
               for(String pair : row.getKey().split(" & ")) {
                  int eq = pair.indexOf('=');

                  if(eq < 0) {
                     continue;
                  }

                  String axisName = pair.substring(0, eq);
                  String valueText = pair.substring(eq + 1);
                  ifProps.set(axisName, constFragment(valueText, schema.getParam(axisName)));
                  ifRequired.add(axisName);
               }
            }
            else {
               ifProps.set(key, constFragment(row.getKey(), schema.getParam(key)));
               ifRequired.add(key);
            }

            ObjectNode thenProps = FACTORY.objectNode();
            boolean any = false;

            for(String gatedName : row.getValue()) {
               JsonNode fragment = fragments.get(gatedName);

               if(fragment != null) {
                  thenProps.set(gatedName, fragment);
                  any = true;
               }
            }

            // A gated param that resolved to no fragment (a Kind B composite, or a name the
            // schema no longer carries) leaves this branch with nothing to admit -- omit the
            // branch itself rather than emit a "then" with an empty properties map.
            if(!any) {
               continue;
            }

            ObjectNode ifNode = FACTORY.objectNode();
            ifNode.set("properties", ifProps);
            ifNode.set("required", ifRequired);

            ObjectNode thenNode = FACTORY.objectNode();
            thenNode.set("properties", thenProps);

            ObjectNode entry = FACTORY.objectNode();
            entry.set("if", ifNode);
            entry.set("then", thenNode);
            allOf.add(entry);
         }
      }

      return allOf;
   }

   /**
    * The JSON Schema fragment an {@code if.properties} entry needs: {@code {"const": value}},
    * not the bare value -- a property's schema there is a schema object, and a literal value in
    * its place is not valid JSON Schema at all, let alone one that matches only that value.
    */
   private static JsonNode constFragment(String valueText, TabularQuerySchema.Param axisParam) {
      ObjectNode node = FACTORY.objectNode();
      node.set("const", constNode(valueText, axisParam));
      return node;
   }

   /** A matrix value's text, typed to match its axis param's own JSON Schema type. */
   private static JsonNode constNode(String valueText, TabularQuerySchema.Param axisParam) {
      String jt = jsonType(axisParam == null ? null : axisParam.getJavaType());

      if("boolean".equals(jt)) {
         return BooleanNode.valueOf(Boolean.parseBoolean(valueText));
      }

      if("integer".equals(jt)) {
         try {
            return LongNode.valueOf(Long.parseLong(valueText));
         }
         catch(NumberFormatException ex) {
            return TextNode.valueOf(valueText);
         }
      }

      if("number".equals(jt)) {
         try {
            return DoubleNode.valueOf(Double.parseDouble(valueText));
         }
         catch(NumberFormatException ex) {
            return TextNode.valueOf(valueText);
         }
      }

      return TextNode.valueOf(valueText);
   }

   private static JsonNode buildParamFragment(TabularQuery query, PropertyMeta prop,
                                              TabularQuerySchema.Param param, boolean resolveTags,
                                              TabularQuerySchema schema)
   {
      Class<?> type = prop.getDescriptor().getPropertyType();
      boolean composite = TabularSchemaExtractor.isCompositeType(type) && type != File.class;

      if(composite) {
         return buildCompositeFragment(query, prop, param);
      }

      JsonNode node = buildScalarFragment(query, param, resolveTags);

      if(node instanceof ObjectNode obj) {
         applyRoleFormat(obj, param, schema);
      }

      return node;
   }

   /**
    * Stamps {@code format} on the two properties a caller has to identify by ROLE rather than by
    * name, and which nothing else in the schema distinguishes.
    *
    * <p>A file path is a {@code java.io.File} property, and {@code jsonType} flattens it to
    * {@code "string"} like every other non-numeric -- so without this, "which property names the
    * file" is unanswerable from the schema. The sheet selector is worse: its signature is
    * "a String with a tagsMethod that dependsOn the file property", three facts that only exist
    * in the extractor's own {@code Param} view.</p>
    *
    * <p>DECIDED HERE, not by the caller, because this is where the evidence is. A consumer reading
    * the published schema sees a projection: the File type is gone, the tagsMethod name is
    * deliberately not published, and dependsOn survives only as {@code if}/{@code then} structure.
    * Re-deriving the role from that is guesswork over a lossy view, while this side still holds the
    * raw values.</p>
    *
    * <p>Ambiguity REFUSES rather than picking one. A wrong sheet property builds a table over the
    * wrong sheet and reports success -- there is no error anywhere for anyone to notice. Today no
    * shipped connector reaches that branch (ServerFileQuery declares exactly one candidate), so
    * the strict choice costs nothing now and only ever fires on a connector nobody has looked at.</p>
    */
   private static void applyRoleFormat(ObjectNode node, TabularQuerySchema.Param param,
                                       TabularQuerySchema schema)
   {
      if(File.class.getName().equals(param.getJavaType())) {
         node.put("format", "file-path");
         return;
      }

      if(!"java.lang.String".equals(param.getJavaType())
         || param.getTagsMethod() == null || param.getTagsMethod().isEmpty()
         || param.getDependsOn() == null || param.getDependsOn().isEmpty())
      {
         return;
      }

      List<String> filePaths = schema.getParams().stream()
         .filter(p -> File.class.getName().equals(p.getJavaType()))
         .map(TabularQuerySchema.Param::getName)
         .toList();

      if(filePaths.stream().noneMatch(f -> param.getDependsOn().contains(f))) {
         return;
      }

      List<String> siblings = schema.getParams().stream()
         .filter(p -> "java.lang.String".equals(p.getJavaType()))
         .filter(p -> p.getTagsMethod() != null && !p.getTagsMethod().isEmpty())
         .filter(p -> p.getDependsOn() != null
                       && p.getDependsOn().stream().anyMatch(filePaths::contains))
         .map(TabularQuerySchema.Param::getName)
         .toList();

      if(siblings.size() > 1) {
         throw new IllegalStateException(
            "Data source type '" + schema.getDataSourceType() + "' declares " + siblings.size() +
            " candidate sheet-selector properties (" + String.join(", ", siblings) +
            ") -- ambiguous, refusing rather than guessing which one selects the sheet.");
      }

      node.put("format", "file-sheet");
   }

   /**
    * A Kind A composite (dependsOn-gated, assumed potentially fillable, or immediately fillable
    * on a blank query) becomes an open-ended string map with {@code x-skeleton} naming its
    * gating prerequisite when one exists. A Kind B composite (schema-time detectable: no
    * dependsOn, empty/unnamed on a blank query) returns {@code null} -- omitted from the schema
    * entirely, the default per this design's D9, same treatment as a derived no-op.
    */
   private static JsonNode buildCompositeFragment(TabularQuery query, PropertyMeta prop,
                                                   TabularQuerySchema.Param param)
   {
      ObjectNode node = FACTORY.objectNode();
      node.put("type", "object");

      ObjectNode additional = FACTORY.objectNode();
      additional.put("type", "string");
      node.set("additionalProperties", additional);

      if(param.getDependsOn() != null && !param.getDependsOn().isEmpty()) {
         String dependsOn = param.getDependsOn().get(0);
         node.put("x-skeleton", dependsOn);
         node.put("description",
            "Fill by name once '" + dependsOn + "' is set; legal keys are read from the live " +
            "skeleton at write time, not listed here. See GET .../query-schema again after " +
            "setting '" + dependsOn + "', or just try -- an unknown key is refused by name, " +
            "not silently dropped.");
         return node;
      }

      Object skeleton;

      try {
         skeleton = prop.getValue(query);
      }
      catch(Exception ex) {
         skeleton = null;
      }

      List<Object> elements = TabularUtil.compositeElementsOf(skeleton);
      boolean namesOk = elements != null && !elements.isEmpty() &&
         elements.stream().allMatch(e -> TabularUtil.compositeElementName(e) != null);

      if(!namesOk) {
         return null;
      }

      String keys = elements.stream()
         .map(TabularUtil::compositeElementName)
         .collect(Collectors.joining(", "));
      node.put("description", "Fill by name; legal keys: " + keys + ".");

      return node;
   }

   private static JsonNode buildScalarFragment(TabularQuery query, TabularQuerySchema.Param param,
                                                boolean resolveTags)
   {
      ObjectNode node = FACTORY.objectNode();
      node.put("type", jsonType(param.getJavaType()));

      if(param.getMin() != null) {
         node.put("minimum", param.getMin());
      }

      if(param.getMax() != null) {
         node.put("maximum", param.getMax());
      }

      List<String> patterns = param.getPattern() == null ? List.of() : param.getPattern().stream()
         .filter(p -> p != null && !p.isEmpty())
         .toList();

      if(patterns.size() == 1) {
         node.put("pattern", patterns.get(0));
      }
      else if(patterns.size() > 1) {
         ArrayNode patternAllOf = FACTORY.arrayNode();

         for(String p : patterns) {
            ObjectNode pn = FACTORY.objectNode();
            pn.put("pattern", p);
            patternAllOf.add(pn);
         }

         node.set("allOf", patternAllOf);
      }

      List<String> descParts = new ArrayList<>();

      if(param.getLabel() != null && !param.getLabel().isBlank()) {
         String label = param.getLabel().trim();
         descParts.add(label.endsWith(".") ? label : label + ".");
      }

      if(param.getHints() != null) {
         for(String hint : param.getHints()) {
            if(hint != null && !hint.isBlank()) {
               descParts.add(hint);
            }
         }
      }

      boolean hasTagsMethod = param.getTagsMethod() != null && !param.getTagsMethod().isEmpty();

      if(!hasTagsMethod && param.getTags() != null && !param.getTags().isEmpty()) {
         ArrayNode enumNode = FACTORY.arrayNode();
         param.getTags().forEach(enumNode::add);
         node.set("enum", enumNode);

         if(param.getTagLabels() != null && !param.getTagLabels().isEmpty()) {
            ArrayNode labels = FACTORY.arrayNode();
            param.getTagLabels().forEach(labels::add);
            node.set("x-enumLabels", labels);
         }
      }

      if(hasTagsMethod) {
         applyTagsMethod(node, query, param, resolveTags, descParts);
      }

      if(descParts.isEmpty()) {
         descParts.add(param.getName() + ".");
      }

      node.put("description", String.join(" ", descParts));

      return node;
   }

   /**
    * The {@code x-} keywords this class emits are a FIXED LIST for this pass -- do not add one
    * without the same cross-repo coordination this list already went through.
    * {@code x-tagsMethod} was dropped before shipping: it named the connector method behind a
    * value set, which no consumer can act on. An agent cannot invoke a Java method, and
    * {@code resolveTags} is a boolean the server resolves itself, so the name was never read.
    * It belongs back here only alongside an endpoint that takes it as an argument.
    * {@code x-valueSource} is handled here; {@code x-skeleton} in
    * {@link #buildCompositeFragment}; {@code x-enumLabels}/{@code x-candidateCount} in both,
    * where relevant; {@code x-output} is not emitted at all (the omit-by-default Kind B choice).
    *
    * <p>{@code x-valueSource} is only ever emitted when the legal value set is NOT carried as
    * {@code enum} in this response -- {@code "external"} (not attempted, or unattemptable ahead
    * of a dependsOn prerequisite), {@code "unavailable"} (attempted, timed out or threw), or
    * {@code "too-large"} (attempted, candidate count exceeded the cap). When resolution
    * succeeds, {@code enum} itself is the signal that the value set is present -- there is
    * nothing left for {@code x-valueSource} to say.</p>
    */
   private static void applyTagsMethod(ObjectNode node, TabularQuery query,
                                       TabularQuerySchema.Param param, boolean resolveTags,
                                       List<String> descParts)
   {
      boolean dependent = param.getDependsOn() != null && !param.getDependsOn().isEmpty();

      if(dependent) {
         node.put("x-valueSource", "external");
         descParts.add("The legal values are decided by the data source itself and are not " +
            "carried here. They cannot be listed until '" +
            String.join("', '", param.getDependsOn()) + "' is set, because they depend on it -- " +
            "set that first, then re-request with resolveTags=true.");
         return;
      }

      if(!resolveTags) {
         node.put("x-valueSource", "external");
         descParts.add("The legal values are decided by the data source itself and are not " +
            "carried here. Re-request with resolveTags=true to have them listed, or take the " +
            "value from how you reached this data source.");
         return;
      }

      String[][] candidates = resolveWithBudget(query, param.getTagsMethod());

      if(candidates == null) {
         node.put("x-valueSource", "unavailable");
         descParts.add("The legal values are decided by the data source itself. This request " +
            "tried to list them and could not (timeout or connector error). Retry with " +
            "resolveTags=true, or ask the user to name the value -- there is no other way to " +
            "learn it.");
         return;
      }

      if(candidates.length > CANDIDATE_CAP) {
         node.put("x-valueSource", "too-large");
         node.put("x-candidateCount", candidates.length);
         descParts.add("The data source offers " + candidates.length + " legal values here -- " +
            "too many to list. Ask the user to name the one they want rather than guessing from " +
            "a partial list.");
         return;
      }

      ArrayNode enumNode = FACTORY.arrayNode();
      ArrayNode labels = FACTORY.arrayNode();

      for(String[] c : candidates) {
         if(c != null && c.length > 1) {
            enumNode.add(c[1]);
            labels.add(c[0]);
         }
      }

      node.set("enum", enumNode);
      node.set("x-enumLabels", labels);
      descParts.add("The legal values are decided by the data source itself; this request " +
         "listed them -- see 'enum'.");
   }

   /**
    * A {@code tagsMethod} invocation, bounded by a timeout and (by the caller, against
    * {@link #CANDIDATE_CAP}) a candidate-count cap -- the ONLY source of candidate values this
    * pass for a METADATA-class connector reached by naming it directly, since there is no
    * {@code query-tags} endpoint and no catalogue ingestion (see the design doc's section 12).
    * A timeout or thrown exception here is therefore the schema-generation request's own
    * feature failing for that one param, not a residual risk -- both funnel to the same
    * {@code null} return, mapped by the caller to {@code x-valueSource: "unavailable"}.
    */
   static String[][] resolveWithBudget(TabularQuery query, String tagsMethod) {
      Future<String[][]> f = EXECUTOR.submit(() -> TabularUtil.invokeTagsMethod(query, tagsMethod));

      try {
         return f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      }
      catch(TimeoutException | ExecutionException | InterruptedException ex) {
         // Best-effort; a reflective call already in flight cannot be forcibly interrupted, the
         // same caveat GroupedThread's own async dialog path already lives with today.
         f.cancel(true);
         return null;
      }
   }

   private static String jsonType(String javaType) {
      if(javaType == null) {
         return "string";
      }

      return switch(javaType) {
         case "int", "java.lang.Integer", "long", "java.lang.Long", "short", "java.lang.Short" ->
            "integer";
         case "double", "java.lang.Double", "float", "java.lang.Float" -> "number";
         case "boolean", "java.lang.Boolean" -> "boolean";
         default -> "string";
      };
   }

   private static final String JSON_SCHEMA_DIALECT = "https://json-schema.org/draft/2020-12/schema";

   /** Per-tagsMethod-call timeout -- see the design doc's section 12.3 for why this value. */
   private static final int TIMEOUT_SECONDS = 5;
   /** Per-tagsMethod-call candidate-count cap -- see the design doc's section 12.3. */
   private static final int CANDIDATE_CAP = 200;

   private static final String REQUIRED_ARRAY_NOTE =
      "'required' lists what this connector's own @Property annotations and layout declare " +
      "mandatory when a parameter is unconditionally visible. It is known to be incomplete: " +
      "some connectors enforce a requirement only in their own runtime code (pagination " +
      "parameters are the common case), which this list cannot see. A parameter's own " +
      "'description' is the more complete guide when the two disagree.";

   private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

   private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
      Thread t = new Thread(r, "tabular-resolve-tags");
      t.setDaemon(true);
      return t;
   });
}
