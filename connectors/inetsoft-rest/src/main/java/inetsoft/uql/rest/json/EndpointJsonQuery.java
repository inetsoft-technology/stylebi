/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.rest.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.rest.*;
import inetsoft.uql.rest.json.lookup.*;
import inetsoft.uql.rest.pagination.PaginationSpec;
import inetsoft.uql.rest.pagination.PaginationType;
import inetsoft.uql.tabular.*;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Specialization for implementations of {@code EndpointJsonQuery} that require more complex
 * endpoint metadata and store it externally.
 */
public abstract class EndpointJsonQuery<T extends EndpointJsonQuery.Endpoint>
   extends RestJsonQuery implements EndpointQuery
{
   public EndpointJsonQuery(String type) {
      super(type);
   }

   @Property(label="URL Suffix")
   @Override
   public String getSuffix() {
      if(isCustomEndpoint()) {
         SuffixTemplate template = new SuffixTemplate(customEndpoint);
         template.withAdditionalParameters(additionalParameters);
         return template.build();
      }

      final Map<String, T> endpointMap = getEndpointMap();
      final SuffixTemplate suffix =
         delegate.getSuffix(endpoint, parameters, additionalParameters, endpointMap);

      if(suffix != null) {
         postprocessSuffix(suffix);
         return suffix.build();
      }
      else {
         return null;
      }
   }

   @Override
   public void setSuffix(String suffix) {
      // no-op
   }

   /**
    * Gets the selected endpoint.
    *
    * @return the endpoint.
    */
   @Property(label = "Endpoint", required = true)
   @PropertyEditor(
      tagsMethod = "getEndpoints",
      editorProperties = @EditorProperty(name = "searchable", value = "true"))
   @SuppressWarnings("unused")
   public String getEndpoint() {
      return endpoint;
   }

   /**
    * Sets the selected endpoint.
    *
    * @param endpoint the endpoint.
    */
   @SuppressWarnings("unused")
   public void setEndpoint(String endpoint) {
      if(!Objects.equals(endpoint, this.endpoint)) {
         this.endpoint = endpoint;
         updatePagination(endpoint);
         lookupEndpoints = new ArrayList<>(LOOKUP_QUERY_LIMIT);
      }
   }

   /**
    * Gets the endpoint to use as a template for the custom endpoint.
    *
    * @return the template endpoint.
    */
   @Property(label = "Template Endpoint")
   @PropertyEditor(
      tagsMethod = "getTemplateEndpoints",
      editorProperties = @EditorProperty(name = "searchable", value = "true"))
   @SuppressWarnings("unused")
   public String getTemplateEndpt() {
      return templateEndpoint;
   }

   /**
    * Sets the endpoint to use as a template for the custom endpoint.
    *
    * @param templateEndpoint the endpoint.
    */
   @SuppressWarnings("unused")
   public void setTemplateEndpt(String templateEndpoint) {
      this.templateEndpoint = templateEndpoint;
   }

   /**
    * Gets the endpoint string set when using a custom endpoint.
    *
    * @return the endpoint.
    */
   @Property(label = "Custom Suffix")
   @SuppressWarnings("unused")
   public String getCustomEndpt() {
      return customEndpoint;
   }

   /**
    * Sets the endpoint string when using a custom endpoint.
    *
    * @param customEndpoint the endpoint.
    */
   @SuppressWarnings("unused")
   public void setCustomEndpt(String customEndpoint) {
      this.customEndpoint = customEndpoint;
   }

   /**
    * Gets the parameters for the selected endpoint.
    *
    * @return the parameters.
    */
   @Property(label = "Parameters", required = true)
   @PropertyEditor(dependsOn = "endpoint")
   public RestParameters getParameters() {
      ResourceBundle bundle = getResourceBundle();
      final Map<String, T> endpointMap = getEndpointMap();
      return delegate.getRestParameters(parameters, endpoint, bundle, endpointMap);
   }

   /**
    * Sets the endpoint parameters.
    *
    * @param parameters the parameters.
    */
   public void setParameters(RestParameters parameters) {
      if(parameters != null && this.parameters != null) {
         parameters.copyParameterValues(this.parameters);
      }

      this.parameters = parameters;
   }

   /**
    * Gets the list of additional query parameters for the endpoint.
    *
    * @return the additional parameters.
    */
   @Property(label = "Additional Parameters")
   @PropertyEditor(editorProperties = @EditorProperty(name = "queryOnly", value = "true"))
   @SuppressWarnings("unused")
   public HttpParameter[] getAdditionalParameters() {
      return additionalParameters;
   }

   /**
    * Sets the list of additional query parameters for the endpoint.
    *
    * @param additionalParameters the additional parameters.
    */
   @SuppressWarnings("unused")
   public void setAdditionalParameters(HttpParameter[] additionalParameters) {
      this.additionalParameters = additionalParameters;
   }

   @Override
   @Property(label = "Join With")
   @PropertyEditor(
      dependsOn = "endpoint",
      tagsMethod = "getLookupEndpoints0",
      editorProperties = @EditorProperty(name = "searchable", value = "true"))
   public String getLookupEndpoint0() {
      return getLookupEndpoint(0);
   }

   @Override
   @SuppressWarnings("unused")
   public void setLookupEndpoint0(String lookupEndpoint) {
      setLookupEndpoint(lookupEndpoint, 0);
   }

   @Override
   @SuppressWarnings("unused")
   public String[][] getLookupEndpoints0() {
      return getLookupEndpoints(0);
   }

   @Override
   @SuppressWarnings("unused")
   public boolean isLookupEndpointVisible0() {
      return lookupEndpointVisible(0);
   }

   @Override
   @Property(label = "Join With")
   @PropertyEditor(
      dependsOn = "lookupEndpoint0",
      tagsMethod = "getLookupEndpoints1",
      editorProperties = @EditorProperty(name = "searchable", value = "true"))
   public String getLookupEndpoint1() {
      return getLookupEndpoint(1);
   }

   @Override
   @SuppressWarnings("unused")
   public void setLookupEndpoint1(String lookupEndpoint) {
      setLookupEndpoint(lookupEndpoint, 1);
   }

   @Override
   @SuppressWarnings("unused")
   public String[][] getLookupEndpoints1() {
      return getLookupEndpoints(1);
   }

   @Override
   @SuppressWarnings("unused")
   public boolean isLookupEndpointVisible1() {
      return lookupEndpointVisible(1);
   }

   @Override
   @Property(label = "Join With")
   @PropertyEditor(
      dependsOn = "lookupEndpoint1",
      tagsMethod = "getLookupEndpoints2",
      editorProperties = @EditorProperty(name = "searchable", value = "true"))
   public String getLookupEndpoint2() {
      return getLookupEndpoint(2);
   }

   @Override
   @SuppressWarnings("unused")
   public void setLookupEndpoint2(String lookupEndpoint) {
      setLookupEndpoint(lookupEndpoint, 2);
   }

   @Override
   @SuppressWarnings("unused")
   public String[][] getLookupEndpoints2() {
      return getLookupEndpoints(2);
   }

   @Override
   @SuppressWarnings("unused")
   public boolean isLookupEndpointVisible2() {
      return lookupEndpointVisible(2);
   }

   @Override
   @Property(label = "Join With")
   @PropertyEditor(
      dependsOn = "lookupEndpoint2",
      tagsMethod = "getLookupEndpoints3",
      editorProperties = @EditorProperty(name = "searchable", value = "true"))
   public String getLookupEndpoint3() {
      return getLookupEndpoint(3);
   }

   @Override
   @SuppressWarnings("unused")
   public void setLookupEndpoint3(String lookupEndpoint) {
      setLookupEndpoint(lookupEndpoint, 3);
   }

   @Override
   @SuppressWarnings("unused")
   public String[][] getLookupEndpoints3() {
      return getLookupEndpoints(3);
   }

   @Override
   @SuppressWarnings("unused")
   public boolean isLookupEndpointVisible3() {
      return lookupEndpointVisible(3);
   }

   @Override
   @Property(label = "Join With")
   @PropertyEditor(
      dependsOn = "lookupEndpoint3",
      tagsMethod = "getLookupEndpoints4",
      editorProperties = @EditorProperty(name = "searchable", value = "true"))
   public String getLookupEndpoint4() {
      return getLookupEndpoint(4);
   }

   @Override
   @SuppressWarnings("unused")
   public void setLookupEndpoint4(String lookupEndpoint) {
      setLookupEndpoint(lookupEndpoint, 4);
   }

   @Override
   @SuppressWarnings("unused")
   public String[][] getLookupEndpoints4() {
      return getLookupEndpoints(4);
   }

   @Override
   @SuppressWarnings("unused")
   public boolean isLookupEndpointVisible4() {
      return lookupEndpointVisible(4);
   }

   @Override
   @Property(label="Expand Lookup Arrays")
   public boolean isLookupExpanded() {
      return lookupExpanded;
   }

   @Override
   @SuppressWarnings("unused")
   public void setLookupExpanded(boolean expanded) {
      this.lookupExpanded = expanded;
   }

   @Override
   @Property(label="Top Level Only")
   @PropertyEditor(dependsOn = "lookupExpanded")
   @SuppressWarnings("unused")
   public boolean isLookupTopLevelOnly() {
      return lookupExpandTop;
   }

   @Override
   @SuppressWarnings("unused")
   public void setLookupTopLevelOnly(boolean lookupExpandTop) {
      this.lookupExpandTop = lookupExpandTop;
   }

   @Override
   public void addLookupQuery() {
      addLookupQuery(null);
   }

   // sessionId is just for being discovered by TabularUtil#callButtonMethods
   @Override
   public void addLookupQuery(String sessionId) {
      final int depth = getLookupQueryDepth();
      final String parentEndpoint = getParentEndpointOfLookupIndex(depth);

      if(parentEndpoint == null) {
         return;
      }

      final List<JsonLookupEndpoint> lookups = getEndpointMap().get(parentEndpoint).getLookups();

      if(lookups.isEmpty()) {
         return;
      }

      final JsonLookupEndpoint firstLookupEndpoint = lookups.get(0);
      lookupEndpoints.add(firstLookupEndpoint.endpoint());
   }

   @Override
   @SuppressWarnings("unused")
   public boolean isLookupExpandTopVisible() {
      return isLookupEndpointVisible0() && isLookupExpanded();
   }

   @Override
   public boolean isAddLookupQueryButtonEnabled() {
      final int depth = getLookupQueryDepth();

      if(depth >= LOOKUP_QUERY_LIMIT) {
         return false;
      }

      final String parentEndpoint = getParentEndpointOfLookupIndex(depth);

      if(parentEndpoint == null) {
         return false;
      }

      return getEndpointMap().get(parentEndpoint).getLookups().size() > 0;
   }

   @Override
   public void removeLookupQuery() {
      removeLookupQuery(null);
   }

   // sessionId is just for being discovered by TabularUtil#callButtonMethods
   @Override
   public void removeLookupQuery(String sessionId) {
      if(!lookupEndpoints.isEmpty()) {
         lookupEndpoints.remove(lookupEndpoints.size() - 1);
      }
   }

   @Override
   public boolean isRemoveLookupQueryButtonEnabled() {
      return isLookupEndpointVisible0();
   }

   @Override
   public int getLookupQueryDepth() {
      return lookupEndpoints.size();
   }

   @Override
   public String getLookupEndpoint(int index) {
      if(index >= lookupEndpoints.size()) {
         return null;
      }

      return lookupEndpoints.get(index);
   }

   @Override
   public boolean lookupEndpointVisible(int index) {
      if(index >= lookupEndpoints.size()) {
         return false;
      }

      if(index > 0 && !lookupEndpointVisible(index - 1)) {
         return false;
      }

      final String parentEndpoint = getParentEndpointOfLookupIndex(index);
      final String lookupEndpoint = lookupEndpoints.get(index);

      return getJsonLookupEndpoint(parentEndpoint, lookupEndpoint) != null;
   }

   @Override
   public String getParentEndpointOfLookupIndex(int index) {
      final String parentEndpoint;

      if(index == 0) {
         parentEndpoint = endpoint;
      }
      else if(index <= lookupEndpoints.size()) {
         parentEndpoint = lookupEndpoints.get(index - 1);
      }
      else {
         parentEndpoint = null;
      }

      return parentEndpoint;
   }

   @Override
   public void setLookupEndpoint(String lookupEndpoint, int index) {
      if(lookupEndpoint == null) {
         if(index < lookupEndpoints.size()) {
            lookupEndpoints.set(index, null);

            // don't leave null endpoints
            while(lookupEndpoints.size() > 0 &&
               lookupEndpoints.get(lookupEndpoints.size() - 1) == null)
            {
               lookupEndpoints.remove(lookupEndpoints.size() - 1);
            }
         }

         return;
      }

      final String parentEndpoint = getParentEndpointOfLookupIndex(index);
      final JsonLookupEndpoint jsonLookupEndpoint =
         getJsonLookupEndpoint(parentEndpoint, lookupEndpoint);

      if(jsonLookupEndpoint == null) {
         return;
      }

      if(index >= lookupEndpoints.size()) {
         lookupEndpoints = new ArrayList<>(lookupEndpoints);
         final int difference = index - lookupEndpoints.size() + 1;

         for(int i = 0; i < difference; i++) {
            lookupEndpoints.add(null);
         }
      }

      lookupEndpoints.set(index, lookupEndpoint);
   }

   @Override
   public String[][] getLookupEndpoints(int index) {
      final String parentEndpoint = getParentEndpointOfLookupIndex(index);

      if(parentEndpoint == null) {
         return null;
      }

      ResourceBundle bundle = getResourceBundle();
      Map<String, T> endpointMap = getEndpointMap();

      if(!endpointMap.containsKey(parentEndpoint)) {
         return null;
      }

      final List<String> lookupEndpoints = getEndpointMap().get(parentEndpoint).getLookups()
         .stream().map(JsonLookupEndpoint::endpoint).collect(Collectors.toList());
      return delegate.getEndpoints(bundle, lookupEndpoints);
   }

   /**
    * Constructs and returns a list of {@link JsonLookupQuery}.
    *
    * @return the currently selected lookup queries.
    */
   @Override
   public List<JsonLookupQuery> getLookupQueries() {
      final List<JsonLookupQuery> flatLookupQueries = new ArrayList<>();

      for(int i = 0; i < lookupEndpoints.size(); i++) {
         final String lookupEndpoint = lookupEndpoints.get(i);

         if(lookupEndpoint == null) {
            break;
         }

         final String parentEndpoint = getParentEndpointOfLookupIndex(i);
         final JsonLookupEndpoint jsonLookupEndpoint = getJsonLookupEndpoint(parentEndpoint, lookupEndpoint);

         if(jsonLookupEndpoint == null) {
            break;
         }

         final JsonLookupQuery lookupQuery = new JsonLookupQuery();
         lookupQuery.setLookupEndpoint(jsonLookupEndpoint);
         lookupQuery.setExpandArrays(true);
         lookupQuery.setJsonPath("$");
         flatLookupQueries.add(lookupQuery);
      }

      for(int i = flatLookupQueries.size() - 2; i >= 0; i--) {
         final JsonLookupQuery parentLookupQuery = flatLookupQueries.get(i);
         final JsonLookupQuery childLookupQuery = flatLookupQueries.get(i + 1);
         parentLookupQuery.setLookupQueries(Collections.singletonList(childLookupQuery));
      }

      if(!flatLookupQueries.isEmpty()) {
         final JsonLookupQuery lastLookupQuery = flatLookupQueries.get(flatLookupQueries.size() - 1);
         lastLookupQuery.setExpandArrays(lookupExpanded);
         lastLookupQuery.setTopLevelOnly(lookupExpandTop);

         final JsonLookupQuery firstLookupQuery = flatLookupQueries.get(0);
         return Collections.singletonList(firstLookupQuery);
      }
      else {
         return Collections.emptyList();
      }
   }

   /**
    * @return the {@link JsonLookupEndpoint} associated with the parentEndpoint and lookupEndpoint.
    */
   @Override
   public JsonLookupEndpoint getJsonLookupEndpoint(String parentEndpoint, String lookupEndpoint) {
      if(parentEndpoint == null || lookupEndpoint == null) {
         return null;
      }

      return getEndpointMap().get(parentEndpoint).getLookups().stream()
         .filter(l -> l.endpoint().equals(lookupEndpoint))
         .findFirst()
         .orElse(null);
   }

   @Override
   public void setLookupQueries(List<JsonLookupQuery> lookupQueries) {
      final List<String> lookupEndpoints = new ArrayList<>(LOOKUP_QUERY_LIMIT);

      while(!lookupQueries.isEmpty()) {
         lookupQueries.stream()
            .map(JsonLookupQuery::getLookupEndpoint)
            .map(JsonLookupEndpoint::endpoint)
            .forEach(lookupEndpoints::add);

         lookupQueries = lookupQueries.get(0).getLookupQueries();
      }

      this.lookupEndpoints = lookupEndpoints;
   }

   /**
    * Gets the names of the endpoints that can be selected.
    *
    * @return the endpoint names.
    */
   public String[][] getEndpoints() {
      ResourceBundle bundle = getResourceBundle();
      String[][] endpoints = delegate.getEndpoints(bundle, getEndpointMap());
      String[][] result = new String[endpoints.length + 1][];
      System.arraycopy(endpoints, 0, result, 0, endpoints.length);
      String customEndpointString = "Custom Endpoint";

      try {
         customEndpointString = bundle.getString("Custom Endpoint");
      }
      catch(Exception e) {
         //ignore
      }

      result[endpoints.length] = new String[]{customEndpointString , "CUSTOM"};
      return result;
   }

   private String[] getEndpointTag(String name, ResourceBundle bundle) {
      if(bundle != null) {
         try {
            return new String[] { bundle.getString(name), name };
         }
         catch(MissingResourceException ignore) {
         }
      }

      return new String[] { name, name };
   }

   public String[][] getTemplateEndpoints() {
      ResourceBundle bundle = getResourceBundle();
      String[][] endpoints = delegate.getEndpoints(bundle, getEndpointMap());
      String[][] result = new String[endpoints.length + 1][];
      System.arraycopy(endpoints, 0, result, 1, endpoints.length);
      String noneString = "None";

      try {
         noneString = bundle.getString("None");
      }
      catch(Exception e) {
         //ignore
      }

      result[0] = new String[]{noneString, ""};
      return result;
   }

   public boolean isCustomEndpoint() {
      return this.endpoint != null && this.endpoint.equals("CUSTOM");
   }

   public boolean isLookupEnabled() {
      return !isCustomEndpoint();
   }

   public void applyEndpointTemplate() {
      applyEndpointTemplate(null);
   }

   // sessionId is just for being discovered by TabularUtil#callButtonMethods
   public void applyEndpointTemplate(String sessionId) {
      if(!templateEndpoint.equals("None")) {
         final Map<String, T> endpointMap = getEndpointMap();
         customEndpoint = endpointMap.get(templateEndpoint).getSuffix();
         customEndpoint = customEndpoint.replace("?}", "}");
         int queryParamIndex = customEndpoint.indexOf('?');

         if(queryParamIndex != -1) {
            customEndpoint = customEndpoint.substring(0, queryParamIndex);
         }

         updatePagination(templateEndpoint);
      }
   }

   @Property(label="Pagination")
   @PropertyEditor(tags={"NONE", "PAGE_COUNT", "TOTAL_COUNT_AND_OFFSET", "TOTAL_COUNT_AND_PAGE", "ITERATION", "LINK_ITERATION"},
      labels={"None", "Page Count", "Total Count And Offset", "Total Count And Page", "Iteration", "Link Iteration"})
   @Override
   public void setPaginationType(PaginationType type) {
      if(isCustomEndpoint()) {
         super.setPaginationType(type);
      }
   }

   /**
    * Updates the pagination specification for the selected endpoint. The default implementation of
    * this method sets pagination to none.
    *
    * @param endpoint the name of the endpoint.
    */
   protected void updatePagination(String endpoint) {
      T ep = getEndpointMap().get(endpoint);

      if(ep == null) {
         useDefaultPagination();
      }
      else {
         updatePagination(ep);
      }
   }

   protected void updatePagination(T endpoint) {
      useDefaultPagination();
   }

   public abstract Map<String, T> getEndpointMap();

   /**
    * Gets the resource bundle that provides localized strings for this type of
    * data source.
    *
    * @return the resource bundle.
    */
   protected ResourceBundle getResourceBundle() {
      return getResourceBundle(ThreadContext.getLocale());
   }

   /**
    * Gets the resource bundle that provides localized strings for this type of
    * data source.
    * <p>
    * By default, it is expected that a resource bundle with the base name of
    * <tt>getClass().getPackage().getName() + ".Bundle"</tt> be provided by
    * implementing classes. If it is not, this method needs to be overridden to
    * provide an alternative.
    *
    * @return the resource bundle.
    */
   protected ResourceBundle getResourceBundle(Locale locale) {
      String baseName = getClass().getPackage().getName() + ".Bundle";
      return ResourceBundle.getBundle(baseName, locale, getClass().getClassLoader());
   }

   @Override
   public void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" lookupExpanded=\"" + lookupExpanded + "\"");
      writer.print(" lookupExpandTop=\"" + lookupExpandTop + "\"");
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(endpoint != null) {
         writer.format("<endpoint><![CDATA[%s]]></endpoint>%n", endpoint);
      }

      if(parameters != null) {
         parameters.writeXML(writer);
      }

      if(isCustomEndpoint() && customEndpoint != null) {
         writer.format("<customEndpoint><![CDATA[%s]]></customEndpoint>%n", customEndpoint);
      }

      if(additionalParameters != null && additionalParameters.length > 0) {
         writer.println("<additionalParameters>");

         for(HttpParameter parameter : additionalParameters) {
            parameter.writeXML(writer);
         }

         writer.println("</additionalParameters>");
      }

      if(!lookupEndpoints.isEmpty()) {
         writer.println("<lookupEndpoints>");

         for(String endpoint : lookupEndpoints) {
            writer.printf("<lookupEndpoint>%s</lookupEndpoint>%n", endpoint);

            if(endpoint == null) {
               break;
            }
         }

         writer.println("</lookupEndpoints>");
      }
   }

   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);

      lookupExpanded = "true".equals(Tool.getAttribute(tag, "lookupExpanded"));
      lookupExpandTop = "true".equals(Tool.getAttribute(tag, "lookupExpandTop"));
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      endpoint = Tool.getChildValueByTagName(root, "endpoint");
      Element elem;

      if((elem = Tool.getChildNodeByTagName(root, "restParameters")) != null) {
         parameters = new RestParameters();
         parameters.parseXML(elem);
      }

      customEndpoint = Tool.getChildValueByTagName(root, "customEndpoint");

      if((elem = Tool.getChildNodeByTagName(root, "additionalParameters")) != null) {
         NodeList nodes = Tool.getChildNodesByTagName(elem, "httpParameter");
         additionalParameters = new HttpParameter[nodes.getLength()];

         for(int i = 0; i < nodes.getLength(); i++) {
            additionalParameters[i] = new HttpParameter();
            additionalParameters[i].parseXML((Element) nodes.item(i));
         }
      }

      if((elem = Tool.getChildNodeByTagName(root, "lookupEndpoints")) != null) {
         NodeList nodes = Tool.getChildNodesByTagName(elem, "lookupEndpoint");
         lookupEndpoints = new ArrayList<>(LOOKUP_QUERY_LIMIT);

         for(int i = 0; i < nodes.getLength(); i++) {
            lookupEndpoints.add(Tool.getValue(nodes.item(i)));
         }
      }
   }

   private void useDefaultPagination() {
      paginationSpec = new PaginationSpec();
      paginationSpec.setType(PaginationType.NONE);
   }

   @Override
   public void copyInfo(TabularQuery query) {
      delegate.copyInfo(query, parameters);
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof EndpointJsonQuery)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      EndpointJsonQuery<?> that = (EndpointJsonQuery<?>) o;
      return Objects.equals(endpoint, that.endpoint) &&
         Objects.equals(parameters, that.parameters) &&
         Arrays.equals(additionalParameters, that.additionalParameters);
   }

   @Override
   public int hashCode() {
      int result = Objects.hash(super.hashCode(), endpoint, parameters);
      result = 31 * result + Arrays.hashCode(additionalParameters);
      return result;
   }

   @Override
   public EndpointJsonQuery<?> clone() {
      EndpointJsonQuery<?> copy = (EndpointJsonQuery<?>) super.clone();
      copy.lookupEndpoints = new ArrayList<>(lookupEndpoints);
      return copy;
   }

   private String endpoint;
   private RestParameters parameters;
   private HttpParameter[] additionalParameters;
   private List<String> lookupEndpoints = new ArrayList<>(LOOKUP_QUERY_LIMIT);
   private boolean lookupExpanded = true;
   private boolean lookupExpandTop = true;
   protected String customEndpoint;
   private String templateEndpoint;
   private final EndpointQueryDelegate<T> delegate = new EndpointQueryDelegate<>();

   public static final int LOOKUP_QUERY_LIMIT = 5;
   private static final Logger LOG = LoggerFactory.getLogger(EndpointJsonQuery.class);

   public interface Endpoint {
      String getName();
      String getSuffix();
      boolean isPaged();

      default List<JsonLookupEndpoint> getLookups() {
         return Collections.emptyList();
      }
   }

   public static abstract class Endpoints<E extends Endpoint> {
      public final List<E> getEndpoints() {
         return endpoints;
      }

      public final void setEndpoints(List<E> endpoints) {
         this.endpoints = endpoints;
      }

      public final Map<String, E> toMap() {
         if(endpoints == null) {
            return Collections.emptyMap();
         }

         return endpoints.stream().reduce(
            new LinkedHashMap<>(),
            (m, e) -> {
               if(m.containsKey(e.getName())) {
                  throw new IllegalStateException("Duplicate endpoint name: " + e.getName());
               }

               m.put(e.getName(), e);
               return m;
            },
            (m1, m2) -> {
               LinkedHashMap<String, E> map = new LinkedHashMap<>(m1);
               map.putAll(m2);
               return map;
            }
         );
      }

      public static <E extends Endpoint> Map<String, E> load(Class<? extends Endpoints<E>> type) {
         Map<String, E> map = null;

         if("true".equals(SreeEnv.getProperty("debug.endpoints"))) {
            URL url = type.getResource("endpoints.json");

            if(url.getProtocol().equals("file")) {
               String path = url.getFile();

               if(path.contains("runner/build/plugins")) {
                  path = path.replaceAll("runner/build/plugins\\/inetsoft-rest.*/classes/",
                                         "tabular/inetsoft-rest/src/main/resources/");

                  File file = new File(path);

                  if(file.exists()) {
                     try(InputStream input = new FileInputStream(file)) {
                        map = createObjectMapper().readValue(input, type).toMap();
                     }
                     catch(IOException e) {
                        // ignore
                     }
                  }
               }
            }
         }

         if(map == null) {
            // follows the convention that the endpoints will be stored in a file named
            // endpoints.json in the same package as the class.
            try(InputStream input = type.getResourceAsStream("endpoints.json")) {
               map = createObjectMapper().readValue(input, type).toMap();
            }
            catch(IOException e) {
               LOG.error("Failed to load endpoints for {}", type.getSimpleName(), e);
               map = Collections.emptyMap();
            }
         }

         return Collections.unmodifiableMap(new TreeMap<>(map));
      }

      private static ObjectMapper createObjectMapper() {
         final ObjectMapper mapper = new ObjectMapper();
         final SimpleModule module = new SimpleModule();
         module.setDeserializerModifier(new JsonLookupEndpointsModifier());
         mapper.registerModule(module);
         return mapper;
      }

      private List<E> endpoints;
   }
}
