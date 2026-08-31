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

package inetsoft.web.wiz.service;

import inetsoft.uql.tabular.PropertyMeta;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.uql.tabular.TabularQuerySchema;
import inetsoft.uql.tabular.TabularSchemaExtractor;
import inetsoft.uql.tabular.TabularUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The one general routine for filling a {@link TabularQuery}'s bean properties from a flat,
 * connector-agnostic {@code queryParams} map -- by the connector's own property names, using
 * only declarative metadata (@Property/@PropertyEditor) the connector already publishes.
 *
 * <p>Replaces {@code TabularEndpointBindingSupport}'s five hand-written, kind-specific methods
 * (one per: endpoint selection, custom suffix, named-connector lookup chain, custom lookup
 * chain) with three general capabilities driven off that same metadata -- composite-by-name
 * filling (Kind A only, see {@link #fillNamedSkeleton}), {@code dependsOn} topological
 * ordering, and {@code tagsMethod} candidate validation -- plus two narrow, name-pattern rules
 * that have no declarative equivalent (custom lookup URL placeholder validation, POST refusal)
 * and one type-based rule (a {@code java.io.File} property accepts a string path).
 *
 * <p>Shared by the SAME two callers {@code TabularEndpointBindingSupport} was:
 * {@code WorksheetTableService.buildTabularTable} (wiz-services' {@code /api/wiz/ws/table}
 * write path) and {@code WorksheetAgentController.addTabularTable} (the composer MCP plugin's
 * own {@code add_table} op) -- so "how to fill a connector-agnostic query via reflection" has
 * exactly one implementation instead of two independently-drifting copies.</p>
 *
 * <p>Every write here is read back before being trusted. A connector's setter is free to
 * silently no-op on bad input (an unknown lookup name, a name mismatch on a composite whose
 * setter compares by position) rather than throw -- exactly the "tool accepted malformed input
 * and produced a plausible-but-wrong result" failure mode this codebase's plugin testing
 * principle calls out. Reading every write back is what turns that silent failure into a loud
 * one.</p>
 */
public final class TabularQueryContractSupport {

   private TabularQueryContractSupport() {}

   /**
    * Fill {@code query}'s bean properties from {@code queryParams}, by the connector's own
    * property names.
    *
    * <p>{@code schema} MUST be {@code new TabularSchemaExtractor().extract(query,
    * dataSourceType)} against the SAME {@code query} instance -- its {@code params} list order
    * is the only reliable tie-break for the topological sort below: {@code pmap} is an
    * unordered {@code HashMap}, and {@code java.beans.Introspector}'s descriptor order is not
    * guaranteed to be declaration order, whereas the schema's params list is explicitly built in
    * {@code @View} declaration order.</p>
    *
    * <p>TWO-LAYER VALIDATION BOUNDARY, restated because getting it backwards produces the
    * single most confusing failure this contract can produce. Everything through step 3 (the
    * topological sort) validates TOP-LEVEL names against {@code schema}/{@code pmap}, which
    * needs no live query state. Everything from there down validates NESTED keys against a
    * composite SKELETON that only exists correctly once that param's own {@code dependsOn}
    * prerequisites were written in an EARLIER iteration of the same loop -- guaranteed by the
    * topological order, never by iteration order over {@code queryParams} itself. A composite
    * skeleton derives its legal key set from whatever else is currently set on the bean; reading
    * it before its prerequisite is set returns a skeleton built for nothing, and every nested
    * key the caller sent would be reported UNKNOWN when the real problem is that the
    * prerequisite was never set (or was rejected earlier in the same request).</p>
    *
    * @return a redacted, human-readable description of what was set -- for the empty-columns
    *         error message the caller builds if the query still returns no data.
    */
   public static String applyQueryContract(TabularQuery query, Map<String, PropertyMeta> pmap,
                                            TabularQuerySchema schema,
                                            Map<String, Object> queryParams, String dsName)
      throws Exception
   {
      if(queryParams == null || queryParams.isEmpty()) {
         throw new IllegalArgumentException(
            "tabularSource.queryParams is required -- it is the whole of what addresses the " +
            "data. Ask GET /api/wiz/tabular/query-schema for the names this data source accepts.");
      }

      List<String> unknownTop = new ArrayList<>();

      for(String name : queryParams.keySet()) {
         if(schema.getParam(name) == null) {
            unknownTop.add(name);
         }
      }

      if(!unknownTop.isEmpty()) {
         throw new IllegalArgumentException(
            "'" + String.join("', '", unknownTop) + "' is not a parameter of data source '" +
            dsName + "'. It accepts: " + String.join(", ", new TreeSet<>(pmap.keySet())) + ".");
      }

      validateCustomLookupUrls(queryParams);

      List<String> order = topologicalSort(queryParams.keySet(), schema, dsName);
      List<String> applied = new ArrayList<>();

      for(String name : order) {
         TabularQuerySchema.Param param = schema.getParam(name);
         PropertyMeta prop = pmap.get(name);
         Object value = queryParams.get(name);
         Class<?> type = prop.getDescriptor().getPropertyType();
         boolean composite = TabularSchemaExtractor.isCompositeType(type) && type != File.class;

         // TAGSMETHOD VALIDATION, before the write, only for a non-composite param -- the same
         // composite test the write branch below uses, not a class-name-specific exclusion.
         if(!composite && param.getTagsMethod() != null && !param.getTagsMethod().isEmpty()) {
            validateTagsMethod(query, param, name, value, dsName);
         }

         if(composite) {
            Object skeleton = prop.getValue(query);
            List<Object> elements = TabularUtil.compositeElementsOf(skeleton);
            boolean namesOk = elements != null && !elements.isEmpty() &&
               elements.stream().allMatch(e -> TabularUtil.compositeElementName(e) != null);

            if(namesOk) {
               fillNamedSkeleton(query, prop, name, value, dsName);
            }
            else {
               throw new IllegalArgumentException(kindBRefusalMessage(name));
            }
         }
         else if(type == File.class) {
            String path = requireString(value, name);
            File file = resolveTargetFile(query, path, dsName);
            invokeWriteMethod(prop, query, file);
            Object readBack = prop.getValue(query);

            if(!(readBack instanceof File f) ||
               !f.getCanonicalPath().equals(file.getCanonicalPath()))
            {
               throw new IllegalStateException(
                  "Failed to point '" + dsName + "' at '" + name + "'=" + describe(prop, path) +
                  "; see the server log for the reflection failure.");
            }

            checkExcelAmbiguity(query, schema, name, path, queryParams, dsName);
         }
         else {
            Object coerced = coerceParam(prop, name, text(value));
            invokeWriteMethod(prop, query, coerced);
            Object readBack = prop.getValue(query);

            if(!Objects.equals(text(readBack), text(coerced))) {
               throw new IllegalStateException(
                  "Setting '" + name + "' to " + describe(prop, coerced) + " appears to have " +
                  "had no effect: reading it back returned " + describe(prop, readBack) +
                  " instead. Either '" + name + "' is derived from another property rather " +
                  "than directly settable on this connector (check GET .../query-schema for a " +
                  "property this one depends on), or the write failed silently. See the server " +
                  "log.");
            }
         }

         applied.add(name + "=" + describe(prop, prop.getValue(query)));
      }

      warnInapplicable(query, queryParams.keySet(), dsName);

      // POST REFUSAL, after the whole fill: updatePagination(endpoint), a side effect of
      // writing endpoint above, is what decides requestType.
      PropertyMeta requestTypeProp = pmap.get("requestType");

      if(requestTypeProp != null && "POST".equals(requestTypeProp.getValue(query))) {
         Object endpointValue = queryParams.get("endpoint");
         String subject = endpointValue != null && !text(endpointValue).isBlank()
            ? "Endpoint '" + text(endpointValue) + "' of '" + dsName + "'"
            : "The query on '" + dsName + "'";
         throw new IllegalArgumentException(
            subject + " is a POST endpoint, which cannot be created this way yet: its request " +
            "body comes from a template that only the connector's own dialog populates.");
      }

      return String.join(", ", applied);
   }

   /**
    * Order {@code names} so that every param whose {@code dependsOn} names another param IN
    * THIS SAME SET is written after it -- a deterministic Kahn's-algorithm topological sort,
    * seeded by the connector's own {@code @View} declaration order ({@code schema.getParams()})
    * so ties break the same way the connector's dialog would render them, not by iteration
    * order over an unordered map.
    *
    * <p>A self-referential {@code dependsOn} (a param naming itself) is defused rather than
    * treated as a cycle -- not observed in any shipped connector, but cheap to make safe.</p>
    */
   private static List<String> topologicalSort(Set<String> names, TabularQuerySchema schema,
                                                String dsName)
   {
      List<String> seed = new ArrayList<>();

      for(TabularQuerySchema.Param p : schema.getParams()) {
         if(names.contains(p.getName())) {
            seed.add(p.getName());
         }
      }

      Map<String, List<String>> edges = new LinkedHashMap<>();

      for(String n : seed) {
         TabularQuerySchema.Param p = schema.getParam(n);
         List<String> deps = new ArrayList<>();

         if(p != null && p.getDependsOn() != null) {
            for(String d : p.getDependsOn()) {
               if(names.contains(d) && !d.equals(n)) {
                  deps.add(d);
               }
            }
         }

         edges.put(n, deps);
      }

      List<String> remaining = new ArrayList<>(seed);
      List<String> resolved = new ArrayList<>();
      Set<String> resolvedSet = new LinkedHashSet<>();

      while(!remaining.isEmpty()) {
         String next = null;

         for(String candidate : remaining) {
            if(resolvedSet.containsAll(edges.get(candidate))) {
               next = candidate;
               break;
            }
         }

         if(next == null) {
            throw new IllegalStateException(
               "The connector for '" + dsName + "' declares a dependsOn cycle among: " +
               String.join(", ", remaining) + ". This is a connector bug (its own " +
               "@PropertyEditor annotations), not a request error.");
         }

         resolved.add(next);
         resolvedSet.add(next);
         remaining.remove(next);
      }

      return resolved;
   }

   /**
    * Validate one already-supplied scalar value against its param's {@code tagsMethod}
    * candidate list -- subsumes endpoint-name validation, lookup-chain-name validation, and (now
    * that this runs for ANY tagsMethod-bearing param, not just endpoint/lookup) Excel sheet-name
    * validation, all as one generic check.
    */
   private static void validateTagsMethod(TabularQuery query, TabularQuerySchema.Param param,
                                           String name, Object value, String dsName)
   {
      String[][] candidates = TabularUtil.invokeTagsMethod(query, param.getTagsMethod());

      if(candidates == null || candidates.length == 0) {
         return;
      }

      String requested = text(value);
      boolean known = false;

      for(String[] c : candidates) {
         if(c != null && c.length > 1 && Objects.equals(requested, c[1])) {
            known = true;
            break;
         }
      }

      if(!known) {
         List<String> sample = new ArrayList<>();

         for(String[] c : candidates) {
            if(c != null && c.length > 1 && c[1] != null) {
               sample.add(c[1]);
            }
         }

         Collections.sort(sample);
         List<String> capped = sample.size() > 20 ? sample.subList(0, 20) : sample;
         String label = param.getLabel() == null ? name : param.getLabel();

         throw new IllegalArgumentException(
            "'" + name + "' has no value '" + requested + "' among '" + dsName + "''s " + label +
            " choices. Choices: " + String.join(", ", capped) + ".");
      }
   }

   /**
    * Fill a Kind A composite's live skeleton by name -- the SAME routine for
    * {@code EndpointJsonQuery.parameters} (a {@code RestParameters}) and
    * {@code ODataQuery.functionParameters} (an {@code HttpParameter[]}), or any future
    * composite of any shape whose live value, once its own {@code dependsOn} prerequisites are
    * set, resolves to a non-empty collection of named elements. No per-class code: the caller
    * (the composite branch of {@link #applyQueryContract}) already confirmed the skeleton is
    * non-empty and every element carries a name before calling this.
    */
   private static void fillNamedSkeleton(TabularQuery query, PropertyMeta prop, String name,
                                         Object requestedValue, String dsName) throws Exception
   {
      if(!(requestedValue instanceof Map)) {
         throw new IllegalArgumentException(
            "'" + name + "' must be a JSON object of {parameterName: value}, got: " +
            shapeOf(requestedValue) + ".");
      }

      Object skeleton = prop.getValue(query);
      List<Object> elements = TabularUtil.compositeElementsOf(skeleton);

      if(elements == null || elements.isEmpty()) {
         throw new IllegalStateException(
            "Could not read the parameter contract for '" + name + "' of '" + dsName + "'.");
      }

      @SuppressWarnings("unchecked")
      Map<String, Object> supplied = new LinkedHashMap<>((Map<String, Object>) requestedValue);
      List<String> missing = new ArrayList<>();
      Map<String, String> filled = new LinkedHashMap<>();

      for(Object element : elements) {
         String elementName = TabularUtil.compositeElementName(element);
         Object v = supplied.remove(elementName);
         String vText = v == null ? null : text(v);

         if(vText != null && !vText.isBlank()) {
            String trimmed = vText.trim();
            TabularUtil.compositeElementSetValue(element, trimmed);
            filled.put(elementName, trimmed);
         }
         else if(Boolean.TRUE.equals(TabularUtil.compositeElementRequired(element))) {
            missing.add(elementName);
         }
      }

      if(!missing.isEmpty()) {
         throw new IllegalArgumentException(
            "'" + name + "' of '" + dsName + "' requires: " + String.join(", ", missing) +
            ". Supply them; do not guess an identifier.");
      }

      if(!supplied.isEmpty()) {
         throw new IllegalArgumentException(
            "'" + name + "' of '" + dsName + "' has no parameter(s) named: " +
            String.join(", ", supplied.keySet()) + ". Its parameters are: " +
            describeElements(elements) + ".");
      }

      // The skeleton object itself, not a copy -- some composite getters (RestParameters,
      // HttpParameter[]) return a FRESH value on every call, so mutating elements in place does
      // not persist without this explicit write-back.
      invokeWriteMethod(prop, query, skeleton);

      Object readBack = prop.getValue(query);
      List<Object> readBackElements = TabularUtil.compositeElementsOf(readBack);

      for(Map.Entry<String, String> f : filled.entrySet()) {
         Object match = readBackElements == null ? null : readBackElements.stream()
            .filter(e -> f.getKey().equals(TabularUtil.compositeElementName(e)))
            .findFirst().orElse(null);
         String actual = match == null ? null : TabularUtil.compositeElementValue(match);

         if(!f.getValue().equals(actual)) {
            throw new IllegalStateException(
               "Setting '" + name + "." + f.getKey() + "' to '" + f.getValue() + "' appears " +
               "to have had no effect: reading it back returned " +
               (actual == null ? "null" : "'" + actual + "'") + " instead. See the server log.");
         }
      }
   }

   private static String describeElements(List<Object> elements) {
      return elements.stream()
         .map(e -> {
            String n = TabularUtil.compositeElementName(e);
            Boolean req = TabularUtil.compositeElementRequired(e);
            return n + (Boolean.TRUE.equals(req) ? " (required)" : "");
         })
         .collect(Collectors.joining(", "));
   }

   private static String shapeOf(Object value) {
      if(value == null) {
         return "null";
      }

      if(value instanceof List) {
         return "an array";
      }

      return "a " + value.getClass().getSimpleName();
   }

   /**
    * Kind B refusal -- a composite whose live skeleton is empty/unnamed even after its own
    * dependsOn prerequisites are set (e.g. {@code EndpointJsonQuery.additionalParameters},
    * which starts null and has no dependsOn at all). Refused by name, never dropped silently;
    * see the design doc's section 3.4 for why this codebase cannot yet build a fresh element
    * list (the element classes carry no {@code @Property} of their own).
    */
   private static String kindBRefusalMessage(String name) {
      return "'" + name + "' is a composite parameter this connector builds from a list of " +
         "new elements, which is not supported yet. For an additional query-string or header " +
         "value on a REST endpoint, write it directly into the endpoint's URL suffix / " +
         "parameters instead. This connector's own query-schema does not carry a usable " +
         "element description for '" + name + "' either (its element type declares no " +
         "per-field metadata), so there is no other endpoint to check before concluding this " +
         "connector has no way to express it through this API today.";
   }

   /**
    * CUSTOM LOOKUP URL PLACEHOLDER VALIDATION -- a narrow, name-pattern rule with no
    * declarative equivalent (there is no {@code @PropertyEditor} shape for "this string must
    * contain a literal token whose index is this level's own position"), run BEFORE any write
    * so a malformed template never reaches the connector even once. Dropping this would convert
    * a pre-flight refusal into a silent, metered failure: every row of the lookup would request
    * the same URL, one billed request per row, with no error anywhere.
    */
   private static void validateCustomLookupUrls(Map<String, Object> queryParams) {
      for(int i = 0; i <= 4; i++) {
         String name = "lookupUrl" + i;

         if(!queryParams.containsKey(name)) {
            continue;
         }

         String value = text(queryParams.get(name));

         if(value == null || value.isBlank()) {
            throw new IllegalArgumentException("'" + name + "' must not be blank.");
         }

         String placeholder = "{param" + (i + 1) + "}";

         if(!value.contains(placeholder)) {
            throw new IllegalArgumentException(
               "'" + name + "' must contain the literal placeholder '" + placeholder + "' so " +
               "the id extracted via this level's jsonPath/key is substituted into the " +
               "request -- got: '" + value + "'.");
         }
      }
   }

   /**
    * EXCEL MULTI-SHEET AMBIGUITY REFUSAL, run immediately after a successful File-typed write.
    * ServerFile's own default (first sheet) is deterministic but silent -- a multi-sheet
    * workbook bound without naming a sheet builds a table from the wrong one during annotation,
    * poisoning everything read from that file, with no error anywhere. This message is a
    * cross-repo contract: wiz-services' tabularFileClient.ts parses it by regex.
    */
   private static void checkExcelAmbiguity(TabularQuery query, TabularQuerySchema schema,
                                           String fileParamName, String fileValue,
                                           Map<String, Object> queryParams, String dsName)
   {
      Object excel = callQueryMethod(query, "isExcel", dsName);

      if(!(excel instanceof Boolean isExcel) || !isExcel) {
         return;
      }

      List<String> sheets = excelSheetNames(query, dsName);

      if(sheets.size() <= 1) {
         return;
      }

      TabularQuerySchema.Param sheetParam = findSheetParam(schema, fileParamName);

      if(sheetParam != null && !queryParams.containsKey(sheetParam.getName())) {
         throw new IllegalArgumentException(
            "'" + fileValue + "' of '" + dsName + "' has " + sheets.size() + " sheets, so one " +
            "has to be named: " + String.join(", ", sheets) + ". Supply it as queryParams." +
            sheetParam.getName() + ".");
      }
   }

   /**
    * The sheet-selecting param for a given file param, matched EXACTLY to the wiz-side design's
    * own heuristic so both sides agree on which property is "the sheet one": a String param
    * with a non-empty tagsMethod whose dependsOn includes the file param's name.
    */
   private static TabularQuerySchema.Param findSheetParam(TabularQuerySchema schema,
                                                           String fileParamName)
   {
      for(TabularQuerySchema.Param p : schema.getParams()) {
         if("java.lang.String".equals(p.getJavaType()) &&
            p.getTagsMethod() != null && !p.getTagsMethod().isEmpty() &&
            p.getDependsOn() != null && p.getDependsOn().contains(fileParamName))
         {
            return p;
         }
      }

      return null;
   }

   /** The workbook's sheet names, blanks dropped -- the connector answers {@code [""]} for a miss. */
   private static List<String> excelSheetNames(TabularQuery query, String dsName) {
      Object names = callQueryMethod(query, "getExcelSheetNames", dsName);
      List<String> sheets = new ArrayList<>();

      if(names instanceof String[] array) {
         for(String n : array) {
            if(n != null && !n.isBlank()) {
               sheets.add(n);
            }
         }
      }

      return sheets;
   }

   /**
    * Resolve {@code relativePath} against the connector's root folder, refusing anything that
    * leaves it. The root folder IS the grant -- a {@code ServerFileDataSource} authorizes one
    * directory and nothing above it -- so an absolute path or a {@code ".."} segment is refused
    * by shape, and the resolved path is checked against the root canonically too, since a
    * symlink inside the root satisfies the shape check and still points out.
    *
    * <p>{@code getRootFolder()} is reached by name: the class declaring it lives in the
    * connector plugin and is not visible from core. A connector that does not answer it is left
    * to resolve the path itself rather than blocked.</p>
    */
   private static File resolveTargetFile(TabularQuery query, String relativePath, String dsName)
      throws Exception
   {
      String normalized = relativePath.replace('\\', '/');

      if(new File(normalized).isAbsolute() || normalized.startsWith("/") ||
         normalized.matches("^[A-Za-z]:.*"))
      {
         throw new IllegalArgumentException(
            "'" + relativePath + "' must be relative to the data source's root folder, not an " +
            "absolute path.");
      }

      for(String segment : normalized.split("/")) {
         if("..".equals(segment)) {
            throw new IllegalArgumentException(
               "'" + relativePath + "' must not contain '..'. The data source's root folder " +
               "is the whole of what it grants access to.");
         }
      }

      String root = (String) callQueryMethod(query, "getRootFolder", dsName);
      File file = root == null || root.isBlank()
         ? new File(normalized) : new File(root, normalized);

      if(root != null && !root.isBlank()) {
         String rootPath = new File(root).getCanonicalPath();
         String filePath = file.getCanonicalPath();

         if(!filePath.equals(rootPath) && !filePath.startsWith(rootPath + File.separator)) {
            throw new IllegalArgumentException(
               "'" + relativePath + "' resolves outside the data source's root folder.");
         }
      }

      if(!file.exists()) {
         throw new IllegalArgumentException(
            "Data source '" + dsName + "' has no file at '" + relativePath + "'. Browse the " +
            "data source to see what it holds; the path is relative to its root folder.");
      }

      return file;
   }

   private static String requireString(Object value, String name) {
      String s = text(value);

      if(s == null || s.isBlank()) {
         throw new IllegalArgumentException("'" + name + "' must not be blank.");
      }

      return s;
   }

   /**
    * Log the parameters that were set but do not apply to the rest of the request. Kept out of
    * the response deliberately: a table that built correctly must not come back looking like it
    * failed; the caller that wants to know beforehand asks the schema, whose dependency matrix
    * says which parameters each choice turns on.
    */
   private static void warnInapplicable(TabularQuery query, Set<String> names, String dsName) {
      Set<String> inapplicable = new TabularSchemaExtractor().findInapplicable(query, names);

      if(!inapplicable.isEmpty()) {
         LOG.warn("Tabular query on '{}' was given parameters that do not apply to the rest of " +
                     "the request and will not be read: {}. See the dependency matrix in " +
                     "GET /api/wiz/tabular/query-schema for what each choice turns on.",
                  dsName, String.join(", ", inapplicable));
      }
   }

   /**
    * A JSON value as the text {@link #coerceParam} converts. A composite arrives as a Map and
    * is handled separately ({@link #fillNamedSkeleton}) before this is ever called on it.
    */
   private static String text(Object value) {
      return value == null ? null : String.valueOf(value);
   }

   /**
    * A value as it should appear in a message -- and never a secret. What is built here can be
    * embedded verbatim in an exception a caller sees, so a connector declaring an API key as a
    * query parameter must not have it printed back.
    */
   private static String describe(PropertyMeta prop, Object value) {
      if(prop != null && prop.getProperty() != null && prop.getProperty().password()) {
         return value == null ? "null" : "***";
      }

      if(value == null) {
         return "null";
      }

      return value instanceof String ? "'" + value + "'" : String.valueOf(value);
   }

   /**
    * Convert one parameter's text to the type its setter takes. A value the setter's type
    * cannot hold is refused with both the parameter name and what it expected, rather than
    * silently becoming {@code 0} or {@code false}.
    */
   private static Object coerceParam(PropertyMeta prop, String name, String raw) {
      Class<?> type = prop.getDescriptor().getWriteMethod().getParameterTypes()[0];

      if(raw == null) {
         if(type.isPrimitive()) {
            throw new IllegalArgumentException(
               "Parameter '" + name + "' has no value, and it cannot be cleared: it is a " +
               type.getSimpleName() + ".");
         }

         return null;
      }

      String value = raw.trim();

      try {
         if(type == String.class) {
            // Not trimmed: a delimiter of " " is a legitimate value.
            return raw;
         }
         else if(type == boolean.class || type == Boolean.class) {
            if(!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
               throw new NumberFormatException(value);
            }

            return Boolean.valueOf(value);
         }
         else if(type == int.class || type == Integer.class) {
            return Integer.valueOf(value);
         }
         else if(type == long.class || type == Long.class) {
            return Long.valueOf(value);
         }
         else if(type == short.class || type == Short.class) {
            return Short.valueOf(value);
         }
         else if(type == double.class || type == Double.class) {
            return Double.valueOf(value);
         }
         else if(type == float.class || type == Float.class) {
            return Float.valueOf(value);
         }
         else if(type == char.class || type == Character.class) {
            // Refused rather than truncated, for the same reason "12x" is not read as 12: taking
            // charAt(0) off a longer string writes something the caller did not ask for and
            // reports success, which is the whole failure class this method exists to close.
            if(value.length() != 1) {
               throw new IllegalArgumentException(
                  "Parameter '" + name + "' expects a single character, got \"" + value + "\".");
            }

            return value.charAt(0);
         }
         else if(type.isEnum()) {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            Object constant = Enum.valueOf((Class<Enum>) type, value);

            return constant;
         }
      }
      catch(IllegalArgumentException ex) {
         String expected = type.isEnum()
            ? "one of " + Arrays.toString(type.getEnumConstants()) : "a " + type.getSimpleName();

         throw new IllegalArgumentException(
            "Parameter '" + name + "' expects " + expected + ", got \"" + raw + "\".");
      }

      throw new IllegalArgumentException(
         "Parameter '" + name + "' is a " + type.getSimpleName() +
         ", which cannot be supplied as text.");
   }

   /**
    * Write a property through its setter directly, so a failed write throws. {@code
    * PropertyMeta.setValue} swallows the invocation failure with a {@code LOG.error} and
    * returns, which on this path would leave the connector's default in place and report
    * success having silently ignored the value.
    */
   private static void invokeWriteMethod(PropertyMeta prop, Object bean, Object value)
      throws Exception
   {
      try {
         prop.getDescriptor().getWriteMethod().invoke(bean, value);
      }
      catch(InvocationTargetException ex) {
         Throwable cause = ex.getCause() == null ? ex : ex.getCause();
         String shown = prop.getProperty() != null && prop.getProperty().password()
            ? "***" : String.valueOf(value);

         throw new IllegalArgumentException(
            "Setting '" + prop.getName() + "' to \"" + shown + "\" failed: " +
            (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()),
            cause);
      }
   }

   /**
    * Invoke a connector method core cannot see the declaring type of. A connector that does not
    * answer is left alone rather than blocked, because these calls sharpen an error message or
    * a default, never the check that makes the build correct.
    */
   private static Object callQueryMethod(TabularQuery query, String method, String dsName) {
      try {
         return query.getClass().getMethod(method).invoke(query);
      }
      catch(Exception ex) {
         LOG.debug("Could not call {}() on the query for '{}'", method, dsName, ex);
         return null;
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(TabularQueryContractSupport.class);
}
