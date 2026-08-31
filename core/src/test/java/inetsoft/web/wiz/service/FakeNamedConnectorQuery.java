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

import inetsoft.uql.VariableTable;
import inetsoft.uql.XQuery;
import inetsoft.uql.tabular.HttpParameter;
import inetsoft.uql.tabular.Property;
import inetsoft.uql.tabular.PropertyEditor;
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.RestParameters;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.uql.tabular.View;
import inetsoft.uql.tabular.View1;

import java.util.*;

/**
 * Minimal, REAL (non-mock) stand-in for a named-connector {@code TabularQuery} such as
 * {@code EndpointJsonQuery} -- reflects the exact {@code @Property}/{@code @PropertyEditor}/
 * {@code getEndpoints()}/{@code isPaged()} shape {@code TabularQueryContractSupport} reaches,
 * without depending on the {@code inetsoft-rest} connector module (core does not depend on it --
 * see {@code inetsoft.uql.rest.AbstractRestQuery}, the test-only stand-in for the same reason).
 *
 * <p>Endpoint graph, modeled on GitHub's real {@code endpoints.json} shape (one lookup per
 * endpoint, chained): {@code Repos -> Issues -> Comments}. {@code Repos} declares one REQUIRED
 * composite parameter, {@code id}, so {@code getParameters()} is a genuine Kind A skeleton --
 * fresh on every call, keyed off the currently-set {@code endpoint}, exactly like the real
 * {@code EndpointJsonQuery.getParameters()}. {@code Paged} and {@code PostEndpoint} exist only
 * to exercise {@code isPaged()}/the POST refusal.</p>
 *
 * <p>{@code @View} is REQUIRED here, not decorative: {@code LayoutCreator}'s no-{@code @View}
 * fallback path never attaches an editor to a COMPONENT-typed view built outside a real
 * {@code @View1}/{@code @View2} walk, so {@code TabularSchemaExtractor.extract} throws on a
 * class with no {@code @View} at all. Every shipped connector declares one; this fixture must
 * too to be extractable the same way.</p>
 */
@View(vertical = true, value = {
   @View1("endpoint"),
   @View1("parameters"),
   @View1("requestType"),
   @View1("suffix"),
   @View1("jsonPath"),
   @View1("expanded"),
   @View1("expandedPath"),
   @View1("lookupExpanded"),
   @View1("lookupTopLevelOnly"),
   @View1("lookupEndpoint0"),
   @View1("lookupEndpoint1"),
   @View1("additionalParameters"),
})
public class FakeNamedConnectorQuery extends TabularQuery {
   public FakeNamedConnectorQuery() {
      super("FakeNamedConnector");
   }

   /**
    * Overridden (rather than delegating to the real {@code TabularQuery.loadOutputColumns},
    * which runs a live {@code TabularHandler} execution this fixture has no backing runner for)
    * to capture the {@code XQuery.HINT_MAX_ROWS} value the caller put in {@code vtable} -- the
    * one thing {@code WorksheetTableServiceProbeHintTest} needs to observe. No columns are
    * produced, matching a connector this probe got nothing back from; the caller's own
    * empty-column check is expected to fire afterward and is not this fixture's concern.
    */
   @Override
   public void loadOutputColumns(VariableTable vtable) throws Exception {
      capturedHintMaxRows = (String) vtable.get(XQuery.HINT_MAX_ROWS);
   }

   public String getCapturedHintMaxRows() {
      return capturedHintMaxRows;
   }

   /**
    * Overridden as a no-op: the real {@code XQuery.revalidate()} looks up
    * {@code DataSourceRegistry.getRegistry()}, a Spring-bean-backed singleton this fixture's
    * test context does not provide (and does not need to -- nothing under test here depends on
    * data-source-registry revalidation).
    */
   @Override
   public void revalidate() {
   }

   private String capturedHintMaxRows;

   @Property(label = "Endpoint", required = true)
   @PropertyEditor(tagsMethod = "getEndpoints")
   public String getEndpoint() {
      return endpoint;
   }

   public void setEndpoint(String endpoint) {
      this.endpoint = endpoint;
      lookupEndpoints.clear();
   }

   /**
    * A FRESH {@code RestParameters}, derived from the currently-set {@code endpoint}, on EVERY
    * call -- the exact shape {@code EndpointJsonQuery.getParameters()} has (:157-161), which is
    * why mutating the elements of one call's result does not persist without an explicit
    * {@link #setParameters} afterward. Previously-set values are recovered via
    * {@code getKnownParameterValue}, the same mechanism the real class uses.
    */
   @Property(label = "Parameters", required = true)
   @PropertyEditor(dependsOn = "endpoint")
   public RestParameters getParameters() {
      RestParameters fresh = new RestParameters();
      fresh.setEndpoint(endpoint);
      List<RestParameter> list = new ArrayList<>();

      for(String name : endpoint == null ? List.<String>of() : PARAM_MAP.getOrDefault(endpoint, List.of())) {
         RestParameter rp = new RestParameter();
         rp.setName(name);
         rp.setRequired(REQUIRED_PARAM_NAMES.contains(name));
         rp.setValue(parameters.getKnownParameterValue(name));
         list.add(rp);
      }

      fresh.setParameters(list);
      return fresh;
   }

   public void setParameters(RestParameters parameters) {
      if(parameters != null && this.parameters != null) {
         parameters.copyParameterValues(this.parameters);
      }

      this.parameters = parameters;
   }

   /**
    * Kind B, exactly like the real {@code EndpointJsonQuery.additionalParameters}: starts null,
    * has NO {@code dependsOn}, and never resolves to a skeleton under any sequence of writes.
    * Exists to exercise the Kind B refusal-by-name path without a live skeleton to fill.
    */
   @Property(label = "Additional Parameters")
   public HttpParameter[] getAdditionalParameters() {
      return additionalParameters;
   }

   public void setAdditionalParameters(HttpParameter[] additionalParameters) {
      this.additionalParameters = additionalParameters;
   }

   @Property(label = "Request Type")
   public String getRequestType() {
      return "PostEndpoint".equals(endpoint) ? "POST" : "GET";
   }

   /** Computed from {@code endpoint} + the parameter values -- the real class's shape. */
   @Property(label = "Suffix")
   public String getSuffix() {
      if(endpoint == null) {
         return null;
      }

      StringBuilder s = new StringBuilder("/").append(endpoint);

      for(RestParameter p : parameters.getParameters()) {
         if(p.getValue() != null) {
            s.append('/').append(p.getValue());
         }
      }

      return s.toString();
   }

   /** No-op, matching {@code EndpointJsonQuery.setSuffix} exactly -- the real silent-no-op case. */
   public void setSuffix(String suffix) {
   }

   @Property(label = "Json Path")
   public String getJsonPath() {
      return jsonPath;
   }

   public void setJsonPath(String jsonPath) {
      this.jsonPath = jsonPath;
   }

   @Property(label = "Expanded")
   public Boolean getExpanded() {
      return expanded;
   }

   public void setExpanded(Boolean expanded) {
      this.expanded = expanded;
   }

   @Property(label = "Expanded Path")
   public String getExpandedPath() {
      return expandedPath;
   }

   public void setExpandedPath(String expandedPath) {
      this.expandedPath = expandedPath;
   }

   @Property(label = "Lookup Expanded")
   public boolean isLookupExpanded() {
      return lookupExpanded;
   }

   public void setLookupExpanded(boolean lookupExpanded) {
      this.lookupExpanded = lookupExpanded;
   }

   @Property(label = "Lookup Top Level Only")
   public boolean isLookupTopLevelOnly() {
      return lookupTopLevelOnly;
   }

   public void setLookupTopLevelOnly(boolean lookupTopLevelOnly) {
      this.lookupTopLevelOnly = lookupTopLevelOnly;
   }

   @Property(label = "Lookup 0")
   @PropertyEditor(dependsOn = "endpoint", tagsMethod = "getLookupEndpoints0")
   public String getLookupEndpoint0() {
      return lookupEndpoints.size() > 0 ? lookupEndpoints.get(0) : null;
   }

   public void setLookupEndpoint0(String v) {
      setLookupEndpoint(v, 0);
   }

   @Property(label = "Lookup 1")
   @PropertyEditor(dependsOn = "lookupEndpoint0", tagsMethod = "getLookupEndpoints1")
   public String getLookupEndpoint1() {
      return lookupEndpoints.size() > 1 ? lookupEndpoints.get(1) : null;
   }

   public void setLookupEndpoint1(String v) {
      setLookupEndpoint(v, 1);
   }

   // Non-@Property reflection surface, mirroring EndpointJsonQuery/RestJsonQuery exactly.

   public boolean isPaged() {
      return "Paged".equals(endpoint);
   }

   public String[][] getEndpoints() {
      return ENDPOINT_MAP.keySet().stream()
         .map(name -> new String[] { name, name })
         .toArray(String[][]::new);
   }

   /**
    * Split into per-level, ZERO-ARG methods (rather than one {@code getLookupEndpoints(int)}) --
    * a {@code tagsMethod} target must be reachable by {@code Class.getMethod(name)} with no
    * parameters, exactly like {@code EndpointJsonQuery.getLookupEndpoints0()}/{@code ...1()}.
    */
   public String[][] getLookupEndpoints0() {
      return lookupEndpointsAt(0);
   }

   public String[][] getLookupEndpoints1() {
      return lookupEndpointsAt(1);
   }

   private String[][] lookupEndpointsAt(int index) {
      String parent = getParentEndpointOfLookupIndex(index);
      List<String> lookups = parent == null
         ? List.of() : ENDPOINT_MAP.getOrDefault(parent, List.of());
      return lookups.stream().map(name -> new String[] { name, name }).toArray(String[][]::new);
   }

   /** Index 0's parent is the base endpoint; index i>0's parent is whatever was chosen at i-1. */
   private String getParentEndpointOfLookupIndex(int index) {
      if(index == 0) {
         return endpoint;
      }

      return index <= lookupEndpoints.size() ? lookupEndpoints.get(index - 1) : null;
   }

   /**
    * SILENTLY NO-OPS on an unknown name -- exactly {@code EndpointJsonQuery.setLookupEndpoint}'s
    * real, documented behavior that {@code TabularQueryContractSupport}'s general read-back
    * check exists to catch.
    */
   private void setLookupEndpoint(String name, int index) {
      if(name == null) {
         return;
      }

      String parent = getParentEndpointOfLookupIndex(index);
      List<String> validNames = parent == null ? List.of() : ENDPOINT_MAP.getOrDefault(parent, List.of());

      if(!validNames.contains(name)) {
         return;
      }

      if(index >= lookupEndpoints.size()) {
         if(index >= LOOKUP_LIMIT) {
            return;
         }

         while(lookupEndpoints.size() <= index) {
            lookupEndpoints.add(null);
         }
      }

      lookupEndpoints.set(index, name);
   }

   private static final int LOOKUP_LIMIT = 5;
   private static final Map<String, List<String>> ENDPOINT_MAP = Map.of(
      "Repos", List.of("Issues"),
      "Issues", List.of("Comments"),
      "Comments", List.of(),
      "Paged", List.of(),
      "PostEndpoint", List.of());
   /** Which endpoint declares which composite parameter names. */
   private static final Map<String, List<String>> PARAM_MAP = Map.of(
      "Repos", List.of("id"));
   private static final Set<String> REQUIRED_PARAM_NAMES = Set.of("id");

   private String endpoint;
   private RestParameters parameters = new RestParameters();
   private HttpParameter[] additionalParameters;
   private String jsonPath;
   private Boolean expanded;
   private String expandedPath;
   private boolean lookupExpanded = true;
   private boolean lookupTopLevelOnly = true;
   private final List<String> lookupEndpoints = new ArrayList<>();
}
