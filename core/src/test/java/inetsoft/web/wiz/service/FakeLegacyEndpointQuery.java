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

import inetsoft.uql.tabular.Property;
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.RestParameters;
import inetsoft.uql.tabular.TabularQuery;

import java.util.*;

/**
 * Minimal, REAL (non-mock) stand-in for a named-connector {@code TabularQuery} such as
 * {@code EndpointJsonQuery} -- reflects the exact {@code @Property}/{@code getEndpoints()}/
 * {@code getLookupEndpoints(int)}/{@code isPaged()} shape
 * {@link TabularEndpointBindingSupport} reaches by reflection, without depending on the
 * {@code inetsoft-rest} connector module (core does not depend on it -- see
 * {@code inetsoft.uql.rest.AbstractRestQuery}, the test-only stand-in for the same reason).
 *
 * <p>Endpoint graph, modeled on GitHub's real {@code endpoints.json} shape (one lookup per
 * endpoint, chained): {@code Repos -> Issues -> Comments}. {@code Paged} and {@code PostEndpoint}
 * exist only to exercise {@code requireRowCapWhenPaged}/the POST refusal.</p>
 */
public class FakeLegacyEndpointQuery extends TabularQuery {
   public FakeLegacyEndpointQuery() {
      super("FakeNamedConnector");
   }

   @Property(label = "Endpoint", required = true)
   public String getEndpoint() {
      return endpoint;
   }

   public void setEndpoint(String endpoint) {
      this.endpoint = endpoint;
      lookupEndpoints.clear();
   }

   @Property(label = "Parameters", required = true)
   public RestParameters getParameters() {
      return parameters;
   }

   public void setParameters(RestParameters parameters) {
      this.parameters = parameters;
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
   public String getLookupEndpoint0() {
      return lookupEndpoints.size() > 0 ? lookupEndpoints.get(0) : null;
   }

   public void setLookupEndpoint0(String v) {
      setLookupEndpoint(v, 0);
   }

   @Property(label = "Lookup 1")
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

   public String[][] getLookupEndpoints(int index) {
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
    * real, documented behavior that {@link TabularEndpointBindingSupport#applyLookupChain}'s
    * read-back exists to catch.
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

   private String endpoint;
   private RestParameters parameters = new RestParameters();
   private String jsonPath;
   private Boolean expanded;
   private String expandedPath;
   private boolean lookupExpanded = true;
   private boolean lookupTopLevelOnly = true;
   private final List<String> lookupEndpoints = new ArrayList<>();
}
