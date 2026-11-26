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

import inetsoft.uql.tabular.HttpParameter;
import inetsoft.uql.tabular.PropertyMeta;
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.RestParameters;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared, connector-agnostic machinery for binding a {@link TabularQuery} to a REST/JSON
 * endpoint (or a hand-authored suffix) and, optionally, a lookup chain -- extracted out of
 * {@link WorksheetTableService} so {@code WorksheetAgentController}'s {@code add_table} op
 * (the composer plugin's write path) can build the exact same {@code TabularTableAssembly}
 * shape {@code WorksheetTableService.buildTabularTable} (the wiz-services {@code /ws/table}
 * write path) already does, without maintaining two independently-drifting copies of "how to
 * select an endpoint / lookup on a connector-agnostic query via reflection".
 *
 * <p>Every write here is read back before being trusted. Both
 * {@code EndpointJsonQuery.setLookupEndpoint(String, int)} and {@code RestJsonQuery}'s per-level
 * lookup setters SILENTLY NO-OP on bad input (an unknown lookup name, or an index past the
 * 5-level cap) rather than throwing -- exactly the "tool accepted malformed input and produced a
 * plausible-but-wrong result" failure mode this codebase's plugin testing principle calls out.
 * Reading every write back, the idiom {@link TabularQueryContractSupport#applyQueryContract}
 * now applies to every property it sets, is what turns that silent failure into a loud one.</p>
 */
public final class TabularEndpointBindingSupport {

   private TabularEndpointBindingSupport() {}

   /**
    * {@code EndpointJsonQuery.LOOKUP_QUERY_LIMIT} -- core cannot import the connector-module
    * constant, so this is a literal with a comment, same as this file already does for other
    * connector-side facts.
    */
   private static final int MAX_LOOKUP_DEPTH = 5;

   /** The connector default for both lookup flags (EndpointJsonQuery's field initialisers). */
   private static final boolean DEFAULT_LOOKUP_FLAG = true;

   /**
    * Set the endpoint and its parameter values on a NAMED CONNECTOR's tabular query (one whose
    * query class exposes an {@code endpoint} property), and return the URL suffix that results.
    *
    * <p>ORDER IS NOT INTERCHANGEABLE. {@code getParameters()} derives the contract from the
    * endpoint currently set on the bean, so setting the endpoint has to come first -- reversed,
    * the contract comes back empty and every supplied value is reported as unknown. Setting the
    * endpoint also runs the connector's {@code updatePagination}, which is what establishes the
    * pagination spec, the request method and the content type.</p>
    *
    * @return the built URL suffix, with every parameter substituted.
    */
   public static String applyEndpointContract(TabularQuery query, Map<String, PropertyMeta> pmap,
                                               String endpoint, Map<String, String> parameters,
                                               String jsonPath, Boolean expanded,
                                               String expandedPath, String dsName)
      throws Exception
   {
      return applyEndpointContract(
         query, pmap, endpoint, parameters, jsonPath, expanded, expandedPath, null, dsName);
   }

   /**
    * Same as the overload above, for a caller that also owns the endpoint-level settings carried
    * in {@code extraProperties}: {@value #CUSTOM_ENDPOINT_PROPERTY} (the literal URL suffix an
    * {@value #CUSTOM_ENDPOINT} endpoint calls) and {@value #ADDITIONAL_PARAMETERS_PROPERTY} (extra
    * query-string values). Both shape the URL suffix this method reads back at the end, so they
    * have to be written here, before that read -- applied afterwards, as every other
    * {@code extraProperties} entry is, a {@value #CUSTOM_ENDPOINT} endpoint has no suffix to read
    * and the call dies on it.
    *
    * @param extraProperties the request's {@code extraProperties} ({@code null} when the caller
    *                        does not manage them: the query keeps whatever it already holds).
    *                        Non-null means the request restates the whole query, so an omitted
    *                        {@value #ADDITIONAL_PARAMETERS_PROPERTY} is cleared instead of keeping
    *                        what an earlier edit set.
    */
   public static String applyEndpointContract(TabularQuery query, Map<String, PropertyMeta> pmap,
                                               String endpoint, Map<String, String> parameters,
                                               String jsonPath, Boolean expanded,
                                               String expandedPath,
                                               Map<String, Object> extraProperties, String dsName)
      throws Exception
   {
      PropertyMeta endpointProp = pmap.get("endpoint");

      if(endpointProp == null) {
         throw new IllegalArgumentException(
            "Data source '" + dsName + "' is tabular but not endpoint-based, so it has no " +
            "endpoint to select. Only connectors that ship an endpoint catalogue can be used " +
            "this way -- use suffix (+ optional customLookups) instead.");
      }

      String trimmedEndpoint = endpoint.trim();
      assertKnownEndpoint(query, trimmedEndpoint, dsName);
      endpointProp.setValue(query, trimmedEndpoint);

      // Read back, because setValue swallows a failed invocation. Everything below derives from
      // the endpoint being set, so an unnoticed failure here produces an empty contract and a
      // null suffix, both of which are much harder to attribute than this line.
      if(!trimmedEndpoint.equals(endpointProp.getValue(query))) {
         throw new IllegalStateException(
            "Failed to select endpoint '" + trimmedEndpoint + "' on the query for '" + dsName +
            "'; see the server log for the reflection failure.");
      }

      applyEndpointSettings(query, pmap, trimmedEndpoint, extraProperties, dsName);

      // Read AFTER the endpoint is set, because that is when the connector's updatePagination has
      // decided it. A POST endpoint is refused rather than attempted: the body it needs is carried
      // on a private field of the connector's query and is only moved into the request body by a
      // dialog BUTTON method this property-based path never runs.
      PropertyMeta requestTypeProp = pmap.get("requestType");

      if(requestTypeProp != null && "POST".equals(requestTypeProp.getValue(query))) {
         throw new IllegalArgumentException(
            "Endpoint '" + trimmedEndpoint + "' of '" + dsName + "' is a POST endpoint, which " +
            "cannot be created this way yet: its request body comes from a template that only " +
            "the connector's own dialog populates. Choose a GET endpoint.");
      }

      PropertyMeta paramProp = pmap.get("parameters");
      Object contract = paramProp == null ? null : paramProp.getValue(query);

      if(!(contract instanceof RestParameters params)) {
         throw new IllegalStateException(
            "Could not read the parameter contract for endpoint '" + trimmedEndpoint + "' of '" +
            dsName + "'.");
      }

      Map<String, String> supplied = parameters == null
         ? new LinkedHashMap<>() : new LinkedHashMap<>(parameters);
      List<String> missing = new ArrayList<>();
      List<String> invalid = new ArrayList<>();
      boolean enforceChoices = !CUSTOM_ENDPOINT.equals(trimmedEndpoint);

      for(RestParameter parameter : params.getParameters()) {
         String value = supplied.remove(parameter.getName());

         if(value != null && !value.isBlank()) {
            // A parameter whose placeholder is a closed list ("Cash|Accrual") takes only those
            // values: the vendor answers any other spelling with a 400 that names no cause. A date
            // format placeholder takes only a date the vendor can parse: it answers an unparsable
            // one with the unfiltered result.
            String resolved = enforceChoices
               ? RestParameterChoices.resolveValue(parameter.getPlaceholder(), value.trim())
               : value.trim();

            if(resolved == null) {
               invalid.add(TabularQueryContractSupport.invalidValueText(
                  parameter.getName(), value.trim(), parameter.getPlaceholder()));
               continue;
            }

            parameter.setValue(resolved);
         }
         else if(parameter.isRequired()) {
            missing.add(parameter.getName());
         }
         else {
            // The request restates the whole query, so an omitted (or blank) optional parameter
            // goes back to the endpoint default instead of keeping the value an earlier edit set.
            parameter.setValue(null);
         }
      }

      // Every missing required name AND every unknown name at once, rather than one round trip per
      // finding -- every round trip past this point is a metered request. A name the endpoint
      // does not declare is an error, not something to drop: dropping it runs a DIFFERENT query
      // than the one that was asked for and reports success, and the likeliest cause is a caller
      // using another endpoint's contract, which no later step can detect.
      TabularQueryContractSupport.requireValidParameters(
         "Endpoint '" + trimmedEndpoint + "'", missing, supplied.keySet(), invalid,
         params.getParameters().stream()
            .map(p -> RestParameterChoices.describeParameter(
               p.getName(), p.isRequired(), p.getPlaceholder()))
            .collect(Collectors.joining(", ")));

      paramProp.setValue(query, params);
      TabularQuery defaults = TabularQueryContractSupport.defaultsOf(query);
      setOrResetProperty(pmap, query, defaults, "jsonPath", jsonPath);
      setOrResetProperty(pmap, query, defaults, "expanded", expanded);
      setOrResetProperty(pmap, query, defaults, "expandedPath", expandedPath);

      // The one end-to-end check, and the reason it is worth a line of its own: reading "suffix"
      // back invokes the connector's getSuffix(), which rebuilds the URL from the endpoint
      // template and the values just set. A null means one of the silent paths above fired after
      // all -- the endpoint name resolved to no template, or a parameter never made it onto the
      // bean.
      Object suffix = readDerivedSuffix(query, pmap);

      if(!(suffix instanceof String s) || s.isBlank()) {
         throw new IllegalStateException(
            "Endpoint '" + trimmedEndpoint + "' of '" + dsName + "' produced no URL suffix " +
            "after its parameters were set; see the server log.");
      }

      return s;
   }

   /**
    * Read the URL suffix a named connector derived from its endpoint and parameter values.
    *
    * <p>Through the {@code suffix} property when the connector exposes it. A connector that
    * overrides {@code getSuffix()} (QuickBooks Reports, Salesforce Reports and Firebase patch the
    * template that way) without repeating {@code @Property} on the override does not expose it:
    * {@code PropertyMeta} reads the annotation from the declaring method only, so the property
    * drops out of the map even though the superclass getter carries it. Treating that as "no
    * suffix" refused every endpoint of those connectors with an unexpected server error, so the
    * public getter is called directly when the property is absent.</p>
    */
   private static Object readDerivedSuffix(TabularQuery query, Map<String, PropertyMeta> pmap) {
      PropertyMeta suffixProp = pmap.get("suffix");

      if(suffixProp != null) {
         return suffixProp.getValue(query);
      }

      try {
         return query.getClass().getMethod("getSuffix").invoke(query);
      }
      catch(NoSuchMethodException ex) {
         return null;
      }
      catch(java.lang.reflect.InvocationTargetException ex) {
         throw new IllegalStateException(
            "Could not build the URL suffix for " + query.getClass().getSimpleName() + ": " +
            ex.getCause(), ex.getCause());
      }
      catch(ReflectiveOperationException ex) {
         throw new IllegalStateException(
            "Could not read the URL suffix of " + query.getClass().getSimpleName() + ".", ex);
      }
   }

   /**
    * The connector's internal endpoint value for "call the URL suffix in
    * {@value #CUSTOM_ENDPOINT_PROPERTY}" ({@code EndpointJsonQuery.isCustomEndpoint}). It is in
    * every endpoint connector's {@code getEndpoints()} but has no endpoint-catalogue entry, so
    * no template, no parameter contract and no lookups.
    */
   public static final String CUSTOM_ENDPOINT = "CUSTOM";

   /** The query property holding the literal suffix of a {@value #CUSTOM_ENDPOINT} endpoint. */
   public static final String CUSTOM_ENDPOINT_PROPERTY = "customEndpt";

   /** The query property holding extra query-string values appended to the endpoint's URL. */
   public static final String ADDITIONAL_PARAMETERS_PROPERTY = "additionalParameters";

   /**
    * The {@code extraProperties} entries {@link #applyEndpointContract} owns for an endpoint-form
    * request. A caller hands whatever is left to the generic contract path, which refuses the
    * composite {@value #ADDITIONAL_PARAMETERS_PROPERTY} and would see
    * {@value #CUSTOM_ENDPOINT_PROPERTY} only after the suffix had to be read.
    */
   public static Map<String, Object> withoutEndpointSettings(Map<String, Object> extraProperties) {
      Map<String, Object> rest = new LinkedHashMap<>(extraProperties);
      rest.remove(CUSTOM_ENDPOINT_PROPERTY);
      rest.remove(ADDITIONAL_PARAMETERS_PROPERTY);
      return rest;
   }

   /**
    * Write the {@value #CUSTOM_ENDPOINT_PROPERTY} and {@value #ADDITIONAL_PARAMETERS_PROPERTY}
    * settings, each read back, and refuse by name what cannot work. Every refusal here replaces a
    * path that used to end in an unexpected server error or in a URL different from the one asked
    * for.
    */
   private static void applyEndpointSettings(TabularQuery query, Map<String, PropertyMeta> pmap,
                                             String endpoint, Map<String, Object> extraProperties,
                                             String dsName)
   {
      Object requestedPath =
         extraProperties == null ? null : extraProperties.get(CUSTOM_ENDPOINT_PROPERTY);
      PropertyMeta pathProp = pmap.get(CUSTOM_ENDPOINT_PROPERTY);

      if(CUSTOM_ENDPOINT.equals(endpoint)) {
         if(pathProp == null) {
            throw new IllegalArgumentException(
               "Endpoint '" + CUSTOM_ENDPOINT + "' of '" + dsName + "' has no " +
               CUSTOM_ENDPOINT_PROPERTY + " property to carry its URL suffix; choose a named " +
               "endpoint (see list_endpoint_lookups).");
         }

         if(requestedPath != null && !(requestedPath instanceof String)) {
            throw new IllegalArgumentException(
               "extraProperties." + CUSTOM_ENDPOINT_PROPERTY + " must be a string (the literal " +
               "URL suffix), got " + requestedPath.getClass().getSimpleName() + ".");
         }

         // The request's value, else what the query already carries: an edit restating
         // endpoint 'CUSTOM' works on a clone that holds the saved suffix.
         Object current = pathProp.getValue(query);
         String path = requestedPath != null ? ((String) requestedPath).trim()
            : current instanceof String c ? c.trim() : "";

         if(path.isEmpty()) {
            throw new IllegalArgumentException(
               "Endpoint '" + CUSTOM_ENDPOINT + "' of '" + dsName + "' calls the URL suffix you " +
               "supply, so extraProperties." + CUSTOM_ENDPOINT_PROPERTY + " is required, for " +
               "example {\"" + CUSTOM_ENDPOINT_PROPERTY + "\": \"/v1/items?limit=3\"}.");
         }

         if(path.indexOf('{') >= 0 || path.indexOf('}') >= 0) {
            throw new IllegalArgumentException(
               "extraProperties." + CUSTOM_ENDPOINT_PROPERTY + " '" + path + "' contains a " +
               "{placeholder}; endpoint '" + CUSTOM_ENDPOINT + "' has no parameters to fill it " +
               "from, so write the literal path.");
         }

         if(requestedPath != null) {
            pathProp.setValue(query, path);

            if(!path.equals(pathProp.getValue(query))) {
               throw new IllegalStateException(
                  "Failed to set " + CUSTOM_ENDPOINT_PROPERTY + " on the query for '" + dsName +
                  "'; see the server log for the reflection failure.");
            }
         }
      }
      else if(requestedPath != null) {
         throw new IllegalArgumentException(
            "extraProperties." + CUSTOM_ENDPOINT_PROPERTY + " only applies to endpoint '" +
            CUSTOM_ENDPOINT + "'; endpoint '" + endpoint + "' builds its own URL.");
      }

      if(extraProperties == null) {
         return;
      }

      Object rawExtra = extraProperties.get(ADDITIONAL_PARAMETERS_PROPERTY);
      PropertyMeta extraProp = pmap.get(ADDITIONAL_PARAMETERS_PROPERTY);

      if(extraProp == null) {
         if(rawExtra != null) {
            throw new IllegalArgumentException(
               "Data source '" + dsName + "' has no " + ADDITIONAL_PARAMETERS_PROPERTY +
               " property.");
         }

         return;
      }

      // Written even when omitted: the request restates the whole query, so an edit that no
      // longer names additionalParameters drops the ones an earlier call set.
      HttpParameter[] parsed = rawExtra == null ? null : parseAdditionalParameters(rawExtra);
      extraProp.setValue(query, parsed);
      Object readBack = extraProp.getValue(query);
      int expected = parsed == null ? 0 : parsed.length;
      int actual = readBack instanceof HttpParameter[] a ? a.length : 0;

      if(expected != actual) {
         throw new IllegalStateException(
            "Failed to set " + ADDITIONAL_PARAMETERS_PROPERTY + " on the query for '" + dsName +
            "'; see the server log for the reflection failure.");
      }
   }

   /**
    * Parse {@value #ADDITIONAL_PARAMETERS_PROPERTY}: a query string ({@code "a=1&b=2"}) or a list
    * of {@code {name, value}} objects. The query-string form splits on {@code &} and the first
    * {@code =} and does not URL-decode or encode, so a value containing either character has to
    * use the list form. Query-string values only -- the connector appends every
    * entry to the URL whatever its type, so a HEADER entry would reach the server as a query
    * value instead of a header, and is refused.
    */
   static HttpParameter[] parseAdditionalParameters(Object raw) {
      List<HttpParameter> result = new ArrayList<>();

      if(raw instanceof String text) {
         for(String pair : text.split("&")) {
            if(pair.isBlank()) {
               continue;
            }

            int eq = pair.indexOf('=');

            if(eq <= 0) {
               throw new IllegalArgumentException(
                  ADDITIONAL_PARAMETERS_PROPERTY + " entry '" + pair + "' is not name=value.");
            }

            result.add(queryParameter(pair.substring(0, eq), pair.substring(eq + 1)));
         }
      }
      else if(raw instanceof Collection<?> items) {
         for(Object item : items) {
            if(!(item instanceof Map<?, ?> entry)) {
               throw new IllegalArgumentException(
                  ADDITIONAL_PARAMETERS_PROPERTY + " list entries must be {name, value} objects, " +
                  "got " + (item == null ? "null" : item.getClass().getSimpleName()) + ".");
            }

            for(Object key : entry.keySet()) {
               if(!"name".equals(key) && !"value".equals(key) && !"type".equals(key)) {
                  throw new IllegalArgumentException(
                     ADDITIONAL_PARAMETERS_PROPERTY + " entries take name, value and type only; " +
                     "got '" + key + "'.");
               }
            }

            Object type = entry.get("type");

            if(type != null && !"QUERY".equalsIgnoreCase(type.toString())) {
               throw new IllegalArgumentException(
                  ADDITIONAL_PARAMETERS_PROPERTY + " supports query-string values only; type '" +
                  type + "' is not supported (a header value cannot be sent this way).");
            }

            Object name = entry.get("name");
            Object value = entry.get("value");
            result.add(queryParameter(name == null ? null : name.toString(),
                                      value == null ? "" : value.toString()));
         }
      }
      else {
         throw new IllegalArgumentException(
            ADDITIONAL_PARAMETERS_PROPERTY + " must be a query string like \"a=1&b=2\" or a " +
            "list of {name, value} objects, got " + raw.getClass().getSimpleName() + ".");
      }

      return result.isEmpty() ? null : result.toArray(new HttpParameter[0]);
   }

   private static HttpParameter queryParameter(String name, String value) {
      if(name == null || name.isBlank()) {
         throw new IllegalArgumentException(
            ADDITIONAL_PARAMETERS_PROPERTY + " entries need a non-blank name.");
      }

      HttpParameter parameter = new HttpParameter();
      parameter.setName(name.trim());
      parameter.setValue(value);
      parameter.setType(HttpParameter.ParameterType.QUERY);
      return parameter;
   }

   /**
    * Set a directly-authored URL suffix + jsonPath on a GENERIC/CUSTOM REST-JSON query (no
    * endpoint catalogue to select from). Read back for the same reason
    * {@link #applyEndpointContract} reads back {@code endpoint}: a caller who mistakenly calls
    * this on a named connector's query (whose {@code suffix} setter is a no-op derived from its
    * {@code endpoint}) would otherwise see silent success while the table stayed bound to
    * whatever {@code endpoint} happened to default to, rather than the suffix that was asked for.
    */
   public static String applyCustomSuffix(TabularQuery query, Map<String, PropertyMeta> pmap,
                                          String suffix, String jsonPath, String dsName)
   {
      PropertyMeta suffixProp = pmap.get("suffix");

      if(suffixProp == null) {
         throw new IllegalStateException(
            "'" + dsName + "' has no suffix property; see the server log.");
      }

      String trimmedSuffix = suffix.trim();
      suffixProp.setValue(query, trimmedSuffix);

      if(!trimmedSuffix.equals(suffixProp.getValue(query))) {
         throw new IllegalStateException(
            "Failed to set suffix '" + trimmedSuffix + "' on the query for '" + dsName + "' -- " +
            "if this datasource is a named connector (has a predefined endpoint catalogue), use " +
            "endpoint instead of suffix, since a named connector's suffix is derived from its " +
            "endpoint and cannot be set directly.");
      }

      setOrResetProperty(pmap, query, TabularQueryContractSupport.defaultsOf(query),
                         "jsonPath", jsonPath);
      return trimmedSuffix;
   }

   /**
    * Set an ordered "Join With" lookup chain on a NAMED CONNECTOR's endpoint query, reading back
    * every write because {@code EndpointJsonQuery.setLookupEndpoint} SILENTLY NO-OPS on an
    * unknown name rather than throwing. Must run AFTER the base endpoint is set and verified:
    * index 0's valid choices are read off THAT endpoint.
    *
    * <p>Also truncates every level from {@code lookup.size()} through the connector's max depth
    * (code review, PR #5391, finding B2): {@code EndpointJsonQuery.setEndpoint} only clears a
    * previously-set chain when the endpoint value actually CHANGES, so an {@code edit_table} that
    * keeps the same endpoint but supplies a shorter (or empty) {@code lookup} would otherwise
    * leave the old, longer chain in place. {@code setLookupEndpoint(null, i)} is the connector's
    * own documented way to clear and trim a level, so this is safe to call unconditionally --
    * on a query that never had a chain this deep, it is a no-op.</p>
    *
    * <p>An omitted {@code expandArrays}/{@code topLevelOnly} is reset to the connector default
    * ({@link #DEFAULT_LOOKUP_FLAG}) rather than left as-is, so {@code edit_table} -- which works
    * on a clone of the live query and restates the whole query, as it does for {@code lookup}
    * above -- does not keep an earlier {@code false}. On a fresh query (add_table) the reset
    * is a no-op. The reset runs here, before any {@code extraProperties}, so an explicit value
    * there still wins.</p>
    */
   public static void applyLookupChain(TabularQuery query, Map<String, PropertyMeta> pmap,
                                       List<String> lookup, Boolean expandArrays,
                                       Boolean topLevelOnly, String baseEndpoint, String dsName)
   {
      if(lookup.size() > MAX_LOOKUP_DEPTH) {
         throw new IllegalArgumentException(
            "lookup has " + lookup.size() + " entries; a chain can be at most 5 levels deep.");
      }

      for(int i = 0; i < lookup.size(); i++) {
         String name = lookup.get(i) == null ? null : lookup.get(i).trim();

         if(name == null || name.isBlank()) {
            throw new IllegalArgumentException("lookup[" + i + "] is blank.");
         }

         String[][] candidates = getLookupEndpointsAt(query, i);
         boolean known = candidates != null &&
            Arrays.stream(candidates).anyMatch(c -> c != null && c.length > 1 && name.equals(c[1]));

         if(!known) {
            String parent = i == 0 ? baseEndpoint : lookup.get(i - 1);
            String available = candidates == null ? "(none)" :
               Arrays.stream(candidates)
                  .filter(c -> c != null && c.length > 1)
                  .map(c -> c[1])
                  .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
               "'" + parent + "' has no lookup named '" + name + "' (lookup[" + i + "] of '" +
               dsName + "'). Available: " + available + ".");
         }

         PropertyMeta lookupProp = pmap.get("lookupEndpoint" + i);

         if(lookupProp == null) {
            throw new IllegalStateException(
               "Connector for '" + dsName + "' does not expose a lookupEndpoint" + i +
               " property; see the server log.");
         }

         lookupProp.setValue(query, name);

         if(!name.equals(lookupProp.getValue(query))) {
            throw new IllegalStateException(
               "Failed to select lookup '" + name + "' at chain position " + i + " for '" +
               dsName + "'; see the server log for the reflection failure.");
         }
      }

      for(int i = lookup.size(); i < MAX_LOOKUP_DEPTH; i++) {
         clearProperty(pmap, query, "lookupEndpoint" + i);
      }

      setOptionalProperty(pmap, query, "lookupExpanded",
                          expandArrays == null ? DEFAULT_LOOKUP_FLAG : expandArrays);
      setOptionalProperty(pmap, query, "lookupTopLevelOnly",
                          topLevelOnly == null ? DEFAULT_LOOKUP_FLAG : topLevelOnly);
   }

   /**
    * Set up to 5 hand-authored custom lookup levels on a GENERIC/CUSTOM REST-JSON query. Each
    * level's four properties ({@code lookupUrl}/{@code lookupJsonPath}/{@code lookupKey}/
    * {@code lookupIgnoreBaseUrl}{i}) silently no-op past index 4 -- read back every write for the
    * same reason {@link #applyLookupChain} does for the named-connector case.
    *
    * <p>Also truncates every level from {@code customLookups.size()} through the connector's max
    * depth (code review, PR #5391, finding B2): unlike {@code applyLookupChain}'s underlying
    * {@code EndpointJsonQuery.setEndpoint}, nothing here resets a stale chain when the request
    * changes {@code suffix} without restating the full {@code customLookups} chain, so an
    * {@code edit_table} that shortens or drops the chain would otherwise leave the old levels'
    * URL/jsonPath/key active. {@code setLookupUrl}{i}{@code (null)}/{@code setLookupJsonPath}{i}
    * {@code (null)}/{@code setLookupKey}{i}{@code (null)} are RestJsonQuery's own documented way to
    * clear and trim a level, so this is safe to call unconditionally -- on a query that never had
    * a chain this deep, it is a no-op. {@code lookupIgnoreBaseUrl}{i} is left alone: both the real
    * connector and this call's own read is gated by {@code lookupUrl}{i}'s presence, so it self-
    * clears once the URL is gone.</p>
    *
    * <p>Within one level, {@code lookupUrl}{i} MUST be set before the other three: it is what
    * grows the connector's backing lists to include index {@code i} at all (see
    * {@code RestJsonQuery.setLookupURL}); the other three setters only take effect once that
    * growth has already happened.</p>
    *
    * <p>Each level's {@code url} must contain the literal placeholder {@code {param<i+1>}}
    * (1-indexed by position) -- {@code RestJsonQuery} auto-names each level's substitution
    * parameter {@code "param" + (i + 1)} and substitutes into {@code url} by ordinary
    * {@code {name}} template replacement, with NO validation of its own that the placeholder is
    * actually present. An omitted placeholder is not caught by any read-back (the property is set
    * to exactly what the caller wrote), so it is checked here instead, before anything is written,
    * rather than silently accepting a URL guaranteed to request the same page for every row.</p>
    */
   public static void applyCustomLookupChain(TabularQuery query, Map<String, PropertyMeta> pmap,
                                             List<WorksheetMutationSupport.CustomLookupSpec> customLookups,
                                             String dsName)
   {
      if(customLookups.size() > MAX_LOOKUP_DEPTH) {
         throw new IllegalArgumentException(
            "customLookups has " + customLookups.size() +
            " entries; a chain can be at most 5 levels deep.");
      }

      TabularQuery defaults = TabularQueryContractSupport.defaultsOf(query);

      for(int i = 0; i < customLookups.size(); i++) {
         WorksheetMutationSupport.CustomLookupSpec level = customLookups.get(i);

         if(level.url() == null || level.url().isBlank()) {
            throw new IllegalArgumentException("customLookups[" + i + "].url is required.");
         }

         String placeholder = "{param" + (i + 1) + "}";

         if(!level.url().contains(placeholder)) {
            throw new IllegalArgumentException(
               "customLookups[" + i + "].url must contain the literal placeholder \"" +
               placeholder + "\" so the id extracted via this level's jsonPath/key is " +
               "substituted into the request -- got: \"" + level.url() + "\".");
         }

         setRequiredProperty(pmap, query, "lookupUrl" + i, level.url(), dsName, i);
         setOrResetProperty(pmap, query, defaults, "lookupJsonPath" + i, level.jsonPath());
         setOrResetProperty(pmap, query, defaults, "lookupKey" + i, level.key());
         setOptionalProperty(pmap, query, "lookupIgnoreBaseUrl" + i,
            level.ignoreBaseUrl() == null ? Boolean.FALSE : level.ignoreBaseUrl());
      }

      for(int i = customLookups.size(); i < MAX_LOOKUP_DEPTH; i++) {
         clearProperty(pmap, query, "lookupUrl" + i);
         clearProperty(pmap, query, "lookupJsonPath" + i);
         clearProperty(pmap, query, "lookupKey" + i);
      }
   }

   /**
    * Reflection: {@code EndpointQuery#getLookupEndpoints(int)} is generic and not
    * {@code @Property}-annotated, so it is unreachable through {@code pmap} -- the class
    * declaring it lives in the connector plugin and is not visible from core, same reasoning as
    * {@link #assertKnownEndpoint}'s {@code getEndpoints()}.
    */
   private static String[][] getLookupEndpointsAt(TabularQuery query, int index) {
      try {
         return (String[][]) query.getClass()
            .getMethod("getLookupEndpoints", int.class).invoke(query, index);
      }
      catch(Exception ex) {
         LOG.debug("Could not list lookup endpoints at depth {} for {}", index,
                   query.getClass(), ex);
         return null;
      }
   }

   /**
    * Refuse an unbounded row limit on a query that paginates. {@code isPaged()} is public but
    * not a {@code @Property}, so it is reached by name -- the same reflection
    * {@link #assertKnownEndpoint} uses for {@code getEndpoints}. A connector that does not answer
    * it is left alone rather than blocked: this check exists to stop a known-unbounded query, not
    * to reject an unfamiliar one.
    *
    * <p>Not only endpoints. Pagination is an ordinary property -- {@code setPaginationType} writes
    * the spec that {@code isPaged()} reads, with nothing in between -- so a target kind addressed
    * by its parameters rather than by an endpoint name can paginate just as readily; {@code target}
    * may be absent for that reason, since such a kind has no endpoint to name.</p>
    *
    * <p>The cap itself is {@link TabularQuery#getMaxRows()} -- the same {@code XQuery} row limit
    * every render already enforces ({@code AbstractQueryRunner.getTable()} feeds it straight into
    * both REST runners' {@code hasNext()}, and {@code BoundQuery.getDefinedMaxRows()} folds it into
    * the effective render-time limit), not a second, parallel concept. The caller is responsible
    * for calling {@code query.setMaxRows(...)} from whatever field the request carries (e.g.
    * {@code EditRequest.maxRows()}/{@code WorksheetTable.TabularSource.getMaxRows()}) BEFORE this
    * check runs -- this method only reads it back.</p>
    */
   public static void requireRowCapWhenPaged(TabularQuery query, String target, String dsName) {
      // Built first so the message reads correctly for a kind that has no target: there is no
      // endpoint to blame, and the data source is the whole of what can be pointed at.
      String subject = target == null || target.isBlank()
         ? "The query on '" + dsName + "'" : "Endpoint '" + target + "' of '" + dsName + "'";
      boolean paged;

      try {
         paged = (Boolean) query.getClass().getMethod("isPaged").invoke(query);
      }
      catch(Exception ex) {
         LOG.debug("Could not determine whether {} paginates; not requiring a row cap", subject, ex);
         return;
      }

      if(!paged) {
         return;
      }

      int maxRows;

      try {
         // XQuery.getMaxRows() reads the org-wide row-limit policy via SreeEnv, which needs a live
         // Spring context -- unavailable the same "we cannot tell" way isPaged() above can be, e.g.
         // a lightweight unit test with no application context. Same fallback: left alone, not
         // blocked, rather than a check that cannot run turning into a hard failure.
         maxRows = query.getMaxRows();
      }
      catch(Exception ex) {
         LOG.debug("Could not read the row cap already applied to {}; not requiring one", subject, ex);
         return;
      }

      if(maxRows <= 0) {
         throw new IllegalArgumentException(
            subject + " is paginated, so a row cap is required: without it every render of this " +
            "table requests pages until the service runs out of data. Set maxRows to a positive " +
            "value for the question being asked.");
      }
   }

   /** Set a property only when a value was supplied, leaving the connector's default otherwise. */
   public static void setOptionalProperty(Map<String, PropertyMeta> pmap, TabularQuery query,
                                          String name, Object value)
   {
      PropertyMeta prop = value == null ? null : pmap.get(name);

      if(prop != null) {
         prop.setValue(query, value);
      }
   }

   /**
    * Set a property to the supplied value, or back to the connector's default when none was
    * supplied: the request restates the whole query, so an omitted optional must not keep what an
    * earlier edit set.
    */
   private static void setOrResetProperty(Map<String, PropertyMeta> pmap, TabularQuery query,
                                          TabularQuery defaults, String name, Object value)
   {
      if(value == null) {
         TabularQueryContractSupport.resetToDefault(query, defaults, pmap, name);
      }
      else {
         setOptionalProperty(pmap, query, name, value);
      }
   }

   /**
    * Unconditionally write {@code null} to a property when the connector exposes it -- unlike
    * {@link #setOptionalProperty}, which treats a null value as "nothing to do." Used to truncate
    * a lookup chain's trailing levels, where {@code null} IS the write (see
    * {@code EndpointJsonQuery.setLookupEndpoint}/{@code RestJsonQuery.setLookupURL}, both of which
    * clear-and-trim on a null argument). A missing property is left alone, same as
    * {@link #setOptionalProperty}.
    */
   private static void clearProperty(Map<String, PropertyMeta> pmap, TabularQuery query,
                                     String name)
   {
      PropertyMeta prop = pmap.get(name);

      if(prop != null) {
         prop.setValue(query, null);
      }
   }

   /**
    * Like {@link #setOptionalProperty}, but for a value that must actually take -- read back and
    * throw on the silent-no-op-past-depth-5 failure mode instead of leaving a half-built lookup
    * chain.
    */
   private static void setRequiredProperty(Map<String, PropertyMeta> pmap, TabularQuery query,
                                           String name, Object value, String dsName, int level)
   {
      PropertyMeta prop = pmap.get(name);

      if(prop == null) {
         throw new IllegalStateException(
            "'" + dsName + "' has no " + name + " property; see the server log.");
      }

      prop.setValue(query, value);

      if(!value.equals(prop.getValue(query))) {
         throw new IllegalStateException(
            "Failed to set customLookups[" + level + "] on '" + dsName + "'; see the server log " +
            "for the reflection failure.");
      }
   }

   /**
    * Fail on an unknown endpoint name, listing what the connector does offer. Without this the
    * name simply resolves to no suffix template and the failure surfaces as "produced no URL
    * suffix", which does not say that the name was wrong. The tags method is reached by name
    * because the interface declaring it lives in the connector plugin and is not visible from
    * core; each row it returns is {@code {label, name}}.
    */
   public static void assertKnownEndpoint(TabularQuery query, String endpoint, String dsName) {
      String[][] endpoints;

      try {
         endpoints = (String[][]) query.getClass().getMethod("getEndpoints").invoke(query);
      }
      catch(Exception ex) {
         // Not fatal: a connector without this method still works, and applyEndpointContract's
         // suffix check catches a bad name -- just with a vaguer message.
         LOG.debug("Could not list endpoints for '{}'; skipping the name check", dsName, ex);
         return;
      }

      if(endpoints == null) {
         return;
      }

      List<String> names = new ArrayList<>();

      for(String[] tag : endpoints) {
         if(tag != null && tag.length > 1 && tag[1] != null) {
            names.add(tag[1]);
         }
      }

      if(!names.isEmpty() && !names.contains(endpoint)) {
         // CUSTOM is accepted but kept out of the sample: it is not an entry of the endpoint
         // catalogue list_endpoint_lookups shows, and it is described separately below.
         List<String> sample = names.stream()
            .filter(name -> !CUSTOM_ENDPOINT.equals(name)).sorted().limit(20).toList();
         throw new IllegalArgumentException(
            "Data source '" + dsName + "' has no endpoint named '" + endpoint + "'. It has " +
            names.size() + " endpoint(s), for example: " + String.join(", ", sample) + "." +
            (names.contains(CUSTOM_ENDPOINT)
               ? " To call a URL of your own use endpoint '" + CUSTOM_ENDPOINT + "' with " +
                 "extraProperties." + CUSTOM_ENDPOINT_PROPERTY + "."
               : ""));
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(TabularEndpointBindingSupport.class);
}
