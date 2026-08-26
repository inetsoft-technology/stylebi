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
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.RestParameters;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
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

      for(RestParameter parameter : params.getParameters()) {
         String value = supplied.remove(parameter.getName());

         if(value != null && !value.isBlank()) {
            parameter.setValue(value.trim());
         }
         else if(parameter.isRequired()) {
            missing.add(parameter.getName());
         }
      }

      // Every missing required name at once, rather than one round trip per name -- every round
      // trip past this point is a metered request.
      if(!missing.isEmpty()) {
         throw new IllegalArgumentException(
            "Endpoint '" + trimmedEndpoint + "' requires parameter(s) with no value supplied: " +
            String.join(", ", missing) + ". Supply them; do not guess an identifier.");
      }

      // A name the endpoint does not declare is an error, not something to drop. Dropping it runs
      // a DIFFERENT query than the one that was asked for and reports success -- and the likeliest
      // cause is a caller using another endpoint's contract, which no later step can detect.
      if(!supplied.isEmpty()) {
         throw new IllegalArgumentException(
            "Endpoint '" + trimmedEndpoint + "' has no parameter(s) named: " +
            String.join(", ", supplied.keySet()) + ". Its parameters are: " +
            params.getParameters().stream()
               .map(p -> p.getName() + (p.isRequired() ? " (required)" : ""))
               .collect(Collectors.joining(", ")) + ".");
      }

      paramProp.setValue(query, params);
      setOptionalProperty(pmap, query, "jsonPath", jsonPath);
      setOptionalProperty(pmap, query, "expanded", expanded);
      setOptionalProperty(pmap, query, "expandedPath", expandedPath);

      // The one end-to-end check, and the reason it is worth a line of its own: reading "suffix"
      // back invokes the connector's getSuffix(), which rebuilds the URL from the endpoint
      // template and the values just set. A null means one of the silent paths above fired after
      // all -- the endpoint name resolved to no template, or a parameter never made it onto the
      // bean.
      PropertyMeta suffixProp = pmap.get("suffix");
      Object suffix = suffixProp == null ? null : suffixProp.getValue(query);

      if(!(suffix instanceof String s) || s.isBlank()) {
         throw new IllegalStateException(
            "Endpoint '" + trimmedEndpoint + "' of '" + dsName + "' produced no URL suffix " +
            "after its parameters were set; see the server log.");
      }

      return s;
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

      setOptionalProperty(pmap, query, "jsonPath", jsonPath);
      return trimmedSuffix;
   }

   /**
    * Set an ordered "Join With" lookup chain on a NAMED CONNECTOR's endpoint query, reading back
    * every write because {@code EndpointJsonQuery.setLookupEndpoint} SILENTLY NO-OPS on an
    * unknown name rather than throwing. Must run AFTER the base endpoint is set and verified:
    * index 0's valid choices are read off THAT endpoint.
    */
   public static void applyLookupChain(TabularQuery query, Map<String, PropertyMeta> pmap,
                                       List<String> lookup, Boolean expandArrays,
                                       Boolean topLevelOnly, String baseEndpoint, String dsName)
   {
      if(lookup.size() > 5 /* EndpointJsonQuery.LOOKUP_QUERY_LIMIT -- core cannot import the
                              connector-module constant, so this is a literal with a comment, same
                              as this file already does for other connector-side facts */) {
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

      setOptionalProperty(pmap, query, "lookupExpanded", expandArrays);
      setOptionalProperty(pmap, query, "lookupTopLevelOnly", topLevelOnly);
   }

   /**
    * Set up to 5 hand-authored custom lookup levels on a GENERIC/CUSTOM REST-JSON query. Each
    * level's four properties ({@code lookupUrl}/{@code lookupJsonPath}/{@code lookupKey}/
    * {@code lookupIgnoreBaseUrl}{i}) silently no-op past index 4 -- read back every write for the
    * same reason {@link #applyLookupChain} does for the named-connector case.
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
      if(customLookups.size() > 5) {
         throw new IllegalArgumentException(
            "customLookups has " + customLookups.size() +
            " entries; a chain can be at most 5 levels deep.");
      }

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
         setOptionalProperty(pmap, query, "lookupJsonPath" + i, level.jsonPath());
         setOptionalProperty(pmap, query, "lookupKey" + i, level.key());
         setOptionalProperty(pmap, query, "lookupIgnoreBaseUrl" + i,
            level.ignoreBaseUrl() == null ? Boolean.FALSE : level.ignoreBaseUrl());
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
    * by its parameters rather than by an endpoint name can paginate just as readily. Which kinds
    * are asked is {@code WorksheetTableService.rowCapRequiredFor}; what this needs to know is that
    * {@code target} may be absent, since such a kind has no endpoint to name.</p>
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

      if(paged) {
         throw new IllegalArgumentException(
            subject + " is paginated, so a row cap is required: without it every render of this " +
            "table requests pages until the service runs out of data. Choose a row cap for the " +
            "question being asked.");
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
         List<String> sample = names.stream().sorted().limit(20).toList();
         throw new IllegalArgumentException(
            "Data source '" + dsName + "' has no endpoint named '" + endpoint + "'. It has " +
            names.size() + " endpoint(s), for example: " + String.join(", ", sample) + ".");
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(TabularEndpointBindingSupport.class);
}
