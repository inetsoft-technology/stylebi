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

package inetsoft.uql.rest.datasource;

import inetsoft.test.EndpointsSpringContext;
import inetsoft.test.SreeHome;
import inetsoft.uql.rest.AbstractRestDataSource;
import inetsoft.uql.rest.json.EndpointJsonQuery;
import inetsoft.uql.rest.datasource.azureblob.AzureBlobEndpoint;
import inetsoft.uql.rest.datasource.azureblob.AzureBlobEndpoints;
import inetsoft.uql.rest.json.SuffixTemplate;
import inetsoft.uql.rest.json.TemplateComponent;
import inetsoft.uql.rest.json.lookup.JsonLookupEndpoint;
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.RestParameters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static inetsoft.uql.rest.datasource.EndpointRules.malformed;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks, without any network access, the declarative endpoint definitions
 * ({@code endpoints.json}) of every endpoint-based data source query: each one loads, its
 * endpoints have unique names, every suffix template can be built from its own parameters, and
 * every lookup refers to an endpoint that exists.
 */
@EndpointsSpringContext
@SreeHome
class EndpointDefinitionsTest {
   @Test
   void everyEndpointQueryTypeIsDiscovered() {
      // guards the scan itself: an empty or tiny list would make the other tests pass vacuously
      assertTrue(queryTypes().size() >= 50, "found only " + queryTypes());
   }

   @Test
   void everyDataSourceDefinesAtLeastOneEndpoint() throws Exception {
      List<String> problems = new ArrayList<>();

      for(Class<?> type : queryTypes()) {
         if(newQuery(type).getEndpointMap().isEmpty()) {
            problems.add(type.getSimpleName() + ": no endpoints loaded");
         }
      }

      assertNoProblems(problems);
   }

   @Test
   void everyEndpointHasANameAndABuildableSuffix() throws Exception {
      List<String> problems = new ArrayList<>();

      for(Class<?> type : queryTypes()) {
         EndpointJsonQuery<?> query = newQuery(type);

         for(String key : query.getEndpointMap().keySet()) {
            String where = type.getSimpleName() + "/" + key;

            if(!KNOWN_BROKEN.contains(where)) {
               problems.addAll(nameAndSuffixProblems(query, key, where));
            }
         }
      }

      assertNoProblems(problems);
   }

   @Test
   void everyParameterNameIsUniqueWithinItsEndpoint() throws Exception {
      List<String> problems = new ArrayList<>();

      for(Class<?> type : queryTypes()) {
         EndpointJsonQuery<?> query = newQuery(type);

         for(String name : query.getEndpointMap().keySet()) {
            query.setEndpoint(name);
            Set<String> seen = new HashSet<>();

            for(RestParameter parameter : query.getParameters().getParameters()) {
               if(!seen.add(parameter.getName())) {
                  problems.add(type.getSimpleName() + "/" + name + ": duplicate parameter " +
                               parameter.getName());
               }
            }
         }
      }

      assertNoProblems(problems);
   }

   @Test
   void everyLookupRefersToAnExistingEndpoint() throws Exception {
      List<String> problems = new ArrayList<>();

      for(Class<?> type : queryTypes()) {
         EndpointJsonQuery<?> query = newQuery(type);
         Map<String, ? extends EndpointJsonQuery.Endpoint> endpoints = query.getEndpointMap();

         for(Map.Entry<String, ? extends EndpointJsonQuery.Endpoint> e : endpoints.entrySet()) {
            for(JsonLookupEndpoint lookup : e.getValue().getLookups()) {
               if(!endpoints.containsKey(lookup.endpoint())) {
                  problems.add(type.getSimpleName() + "/" + e.getKey() +
                               ": lookup to unknown endpoint " + lookup.endpoint());
               }
            }
         }
      }

      assertNoProblems(problems);
   }

   @Test
   void everyDataSourceHasALoadableResourceBundle() throws Exception {
      List<String> problems = new ArrayList<>();

      for(Class<?> type : queryTypes()) {
         try {
            // the endpoint labels and parameter labels come from this bundle
            EndpointJsonQuery<?> query = newQuery(type);
            ResourceBundle bundle = (ResourceBundle) BUNDLE_GETTER.invoke(query);
            assertNotNull(bundle, "no resource bundle");
            assertTrue(bundle.getKeys().hasMoreElements(), "resource bundle is empty");
         }
         catch(Exception ex) {
            problems.add(type.getSimpleName() + ": " + ex);
         }
      }

      assertNoProblems(problems);
   }

   @Test
   void everyEndpointsFileBelongsToAScannedQueryAndTheOtherWayAround() throws Exception {
      Set<String> filePackages = new TreeSet<>();

      for(Resource resource : new PathMatchingResourcePatternResolver().getResources(
         "classpath*:inetsoft/uql/rest/datasource/*/endpoints.json"))
      {
         String path = resource.getURL().getPath();
         String dir = path.substring(0, path.lastIndexOf('/'));
         filePackages.add("inetsoft.uql.rest.datasource." + dir.substring(dir.lastIndexOf('/') + 1));
      }

      Set<String> queryPackages = new TreeSet<>();

      for(Class<?> type : queryTypes()) {
         queryPackages.add(type.getPackageName());
      }

      // the only XML endpoint query, covered by azureBlobEndpointsAreWellFormed
      queryPackages.add("inetsoft.uql.rest.datasource.azureblob");
      Set<String> withoutQuery = new TreeSet<>(filePackages);
      withoutQuery.removeAll(queryPackages);
      Set<String> withoutFile = new TreeSet<>(queryPackages);
      withoutFile.removeAll(filePackages);
      withoutFile.remove("inetsoft.uql.rest.datasource.azureblob");
      assertTrue(filePackages.size() >= 60, "found only " + filePackages);
      assertTrue(withoutQuery.isEmpty() && withoutFile.isEmpty(),
                 "endpoints.json without a scanned query: " + withoutQuery +
                 "\nscanned query without endpoints.json: " + withoutFile);
   }

   @Test
   void everyQueryHasTheDataSourceNextToIt() throws Exception {
      List<String> problems = new ArrayList<>();

      for(Class<?> type : queryTypes()) {
         if(newQuery(type).getDataSource() == null) {
            problems.add(type.getSimpleName() + ": no data source class found next to the query");
         }
      }

      assertNoProblems(problems);
   }

   @Test
   void builtRequestPathAndQueryStringAreWellFormed() throws Exception {
      List<String> problems = new ArrayList<>();

      for(Class<?> type : queryTypes()) {
         EndpointJsonQuery<?> query = newQuery(type);

         for(String name : query.getEndpointMap().keySet()) {
            String where = type.getSimpleName() + "/" + name;

            if(!KNOWN_BROKEN.contains(where)) {
               problems.addAll(requestProblems(query, name, where));
            }
         }
      }

      assertNoProblems(problems);
   }

   @Test
   void everyOptionOfEveryParameterBuildsAWellFormedRequest() throws Exception {
      List<String> problems = new ArrayList<>();

      for(Class<?> type : queryTypes()) {
         EndpointJsonQuery<?> query = newQuery(type);

         for(String name : query.getEndpointMap().keySet()) {
            String where = type.getSimpleName() + "/" + name;

            if(!KNOWN_BROKEN.contains(where)) {
               problems.addAll(optionProblems(query, name, where));
            }
         }
      }

      assertNoProblems(problems);
   }

   /**
    * Keeps {@link #KNOWN_BROKEN} honest: every entry must name an endpoint that exists and that
    * still fails one of the checks above, otherwise it silently exempts a fixed, renamed or
    * mistyped endpoint.
    */
   @Test
   void everyKnownBrokenEntryExistsAndIsStillBroken() throws Exception {
      Map<String, Class<?>> typesByName = new HashMap<>();

      for(Class<?> type : queryTypes()) {
         typesByName.put(type.getSimpleName(), type);
      }

      List<String> problems = new ArrayList<>();

      for(String entry : new TreeSet<>(KNOWN_BROKEN)) {
         int slash = entry.indexOf('/');
         Class<?> type = slash < 0 ? null : typesByName.get(entry.substring(0, slash));

         if(type == null) {
            problems.add(entry + ": no such query type, remove it from KNOWN_BROKEN");
            continue;
         }

         EndpointJsonQuery<?> query = newQuery(type);
         String key = entry.substring(slash + 1);

         if(!query.getEndpointMap().containsKey(key)) {
            problems.add(entry + ": no such endpoint, remove it from KNOWN_BROKEN");
            continue;
         }

         List<String> found = new ArrayList<>();
         found.addAll(nameAndSuffixProblems(query, key, entry));
         found.addAll(requestProblems(query, key, entry));
         found.addAll(optionProblems(query, key, entry));

         if(found.isEmpty()) {
            problems.add(entry + ": no longer broken, remove it from KNOWN_BROKEN");
         }
      }

      assertNoProblems(problems);
   }

   @Test
   void aMissingRequiredParameterIsRejectedAndAMissingOptionalOneIsOmitted() throws Exception {
      List<String> problems = new ArrayList<>();

      for(Class<?> type : queryTypes()) {
         EndpointJsonQuery<?> query = newQuery(type);

         for(Map.Entry<String, ? extends EndpointJsonQuery.Endpoint> e :
            query.getEndpointMap().entrySet())
         {
            String where = type.getSimpleName() + "/" + e.getKey();

            if(KNOWN_BROKEN.contains(where)) {
               continue;
            }

            for(String problem : EndpointRules.requiredParameterProblems(e.getValue().getSuffix())) {
               problems.add(where + ": " + problem);
            }
         }
      }

      assertNoProblems(problems);
   }

   /**
    * The Azure Blob query extends the XML endpoint query, not {@link EndpointJsonQuery}, so the
    * scan does not find it. It has no parameter objects to go through, so its suffix templates
    * are filled in directly.
    */
   @Test
   void azureBlobEndpointsAreWellFormed() {
      List<String> problems = new ArrayList<>();
      Map<String, AzureBlobEndpoint> endpoints = EndpointJsonQuery.Endpoints.load(
         AzureBlobEndpoints.class);
      assertFalse(endpoints.isEmpty());

      for(AzureBlobEndpoint endpoint : endpoints.values()) {
         try {
            SuffixTemplate template = new SuffixTemplate(endpoint.getSuffix());

            for(TemplateComponent part : template.getPath()) {
               if(part.isVariable()) {
                  template.setVariable(part.getValue(), "1");
               }
            }

            for(TemplateComponent part : template.getQuery().values()) {
               if(part.isVariable()) {
                  template.setVariable(part.getValue(), "1");
               }
            }

            String problem = malformed(template.build());

            if(problem != null) {
               problems.add("AzureBlob/" + endpoint.getName() + ": " + problem);
            }

            for(JsonLookupEndpoint lookup : endpoint.getLookups()) {
               if(!endpoints.containsKey(lookup.endpoint())) {
                  problems.add("AzureBlob/" + endpoint.getName() + ": lookup to unknown endpoint " +
                               lookup.endpoint());
               }
            }
         }
         catch(Exception ex) {
            problems.add("AzureBlob/" + endpoint.getName() + ": " + ex);
         }
      }

      assertNoProblems(problems);
   }

   /** The name and suffix problems of one endpoint. */
   private static List<String> nameAndSuffixProblems(EndpointJsonQuery<?> query, String key,
                                                     String where)
   {
      List<String> problems = new ArrayList<>();
      EndpointJsonQuery.Endpoint endpoint = query.getEndpointMap().get(key);

      String nameProblem = EndpointRules.nameProblem(endpoint.getName());

      if(nameProblem != null) {
         problems.add(where + ": " + nameProblem);
      }

      if(endpoint.getSuffix() == null || endpoint.getSuffix().isBlank()) {
         problems.add(where + ": blank suffix");
         return problems;
      }

      try {
         String built = buildSuffix(query, key);

         if(built == null || built.isEmpty()) {
            problems.add(where + ": suffix builds to nothing");
         }
         else if(built.contains("{") || built.contains("}")) {
            problems.add(where + ": unreplaced variable in " + built);
         }
      }
      catch(Exception ex) {
         problems.add(where + ": " + ex);
      }

      return problems;
   }

   /** The problems of the request built from one endpoint with every parameter set. */
   private static List<String> requestProblems(EndpointJsonQuery<?> query, String name,
                                               String where)
   {
      List<String> problems = new ArrayList<>();

      try {
         String problem = malformed(buildSuffix(query, name));

         if(problem != null) {
            problems.add(where + ": " + problem);
         }
      }
      catch(Exception ex) {
         problems.add(where + ": " + ex);
      }

      return problems;
   }

   /** The problems of the requests built with each option of each option-list parameter. */
   private static List<String> optionProblems(EndpointJsonQuery<?> query, String name,
                                              String where)
   {
      List<String> problems = new ArrayList<>();
      query.setEndpoint(name);

      for(RestParameter parameter : query.getParameters().getParameters()) {
         String placeholder = parameter.getPlaceholder();

         if(placeholder == null || !placeholder.contains("|")) {
            continue;
         }

         for(String option : placeholder.split("\\|")) {
            if(option.isBlank() || option.contains("...")) {
               continue;
            }

            try {
               String problem = malformed(buildSuffix(query, name, parameter.getName(),
                                                      option.trim()));

               if(problem != null) {
                  problems.add(where + " [" + parameter.getName() + "=" + option + "]: " +
                               problem);
               }
            }
            catch(Exception ex) {
               problems.add(where + " [" + parameter.getName() + "=" + option + "]: " + ex);
            }
         }
      }

      return problems;
   }

   /** Builds the suffix of an endpoint with every parameter set to a simple value. */
   private static String buildSuffix(EndpointJsonQuery<?> query, String endpointName) {
      return buildSuffix(query, endpointName, null, null);
   }

   /** Same, but sets one parameter to the given value. */
   private static String buildSuffix(EndpointJsonQuery<?> query, String endpointName,
                                     String parameterName, String value)
   {
      query.setEndpoint(endpointName);
      RestParameters parameters = query.getParameters();

      for(RestParameter parameter : parameters.getParameters()) {
         parameter.setValue(parameter.getName().equals(parameterName) ? value : "1");
      }

      query.setParameters(parameters);
      return query.getSuffix();
   }

   /**
    * Creates a query and gives it the data source that sits next to it in the same package,
    * since some queries read the data source when an endpoint is selected.
    */
   static EndpointJsonQuery<?> newQuery(Class<?> type) throws Exception {
      EndpointJsonQuery<?> query = (EndpointJsonQuery<?>) type.getConstructor().newInstance();
      String name = type.getName();
      String dataSourceName = name.substring(0, name.length() - "Query".length()) + "DataSource";

      try {
         Class<?> dataSourceType = Class.forName(dataSourceName);

         if(AbstractRestDataSource.class.isAssignableFrom(dataSourceType)) {
            Object dataSource = dataSourceType.getConstructor().newInstance();

            try {
               // some suffixes embed the project of the data source
               dataSourceType.getMethod("setProjectId", String.class).invoke(dataSource, "p");
            }
            catch(NoSuchMethodException e) {
               // no project id
            }

            query.setDataSource((AbstractRestDataSource<?>) dataSource);
         }
      }
      catch(ClassNotFoundException e) {
         // the query uses a data source defined elsewhere
      }

      return query;
   }

   static List<Class<?>> queryTypes() {
      if(queryTypes == null) {
         ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
         scanner.addIncludeFilter(new AssignableTypeFilter(EndpointJsonQuery.class));
         List<Class<?>> types = new ArrayList<>();

         for(BeanDefinition definition :
            scanner.findCandidateComponents("inetsoft.uql.rest.datasource"))
         {
            try {
               types.add(Class.forName(definition.getBeanClassName()));
            }
            catch(ClassNotFoundException e) {
               throw new IllegalStateException(e);
            }
         }

         types.sort(Comparator.comparing(Class::getName));
         queryTypes = types;
      }

      return queryTypes;
   }

   private static void assertNoProblems(List<String> problems) {
      assertTrue(problems.isEmpty(),
                 problems.size() + " problem(s):\n" + String.join("\n", problems));
   }

   private static List<Class<?>> queryTypes;

   /** {@code EndpointJsonQuery.getResourceBundle()} is protected, and some queries override it. */
   private static final java.lang.reflect.Method BUNDLE_GETTER;

   static {
      try {
         BUNDLE_GETTER = EndpointJsonQuery.class.getDeclaredMethod("getResourceBundle");
         BUNDLE_GETTER.setAccessible(true);
      }
      catch(NoSuchMethodException e) {
         throw new ExceptionInInitializerError(e);
      }
   }

   /**
    * Endpoints whose suffix template is known to be wrong. They are skipped here so that this test
    * can guard every other endpoint; fix the template and remove the entry. The defects are
    * tracked in Redmine #81907.
    * <ul>
    *   <li>a variable is left unreplaced: a missing '&amp;', a '?' where '&amp;' belongs, a stray
    *   '}', a missing '=' or a missing ':' before an option list;</li>
    *   <li>a stray space, newline or non-breaking space in a name or suffix;</li>
    *   <li>an empty parameter ("?&amp;x=", "&amp;&amp;") or a second '?' or '//' in the path;</li>
    *   <li>"Twitter / Users by IDs" has "{User IDs}" with no parameter name before it, and the
    *   Twitter API expects "ids=".</li>
    * </ul>
    */
   private static final Set<String> KNOWN_BROKEN = Set.of(
      "AdobeAnalyticsQuery/Report Suites",
      "BoxQuery/Search for Content",
      "BoxQuery/Trashed Items",
      "HubSpotQuery/Blog Posts",
      "HubSpotQuery/Contacts\u00a0Informations",
      "HubSpotQuery/Line items Change Log",
      "InfluxDBQuery/Installed Templates",
      "InfluxDBQuery/Sources",
      "InsightlyQuery/Contact File Attachments",
      "JiraQuery/Audit Records",
      "JiraQuery/Screen Tab Fields",
      "LinkedinQuery/Ad Account Users",
      "JiveQuery/Direct Message Comments",
      "LiveAgentQuery/Phone numbers list",
      "PipelineCRMQuery/Companies",
      "PipelineCRMQuery/Company Custom Label Entries",
      "PipelineCRMQuery/Deal Custom Groups",
      "PipelineCRMQuery/Deal Custom Labels",
      "PipelineCRMQuery/Deal Loss Reasons",
      "PipelineCRMQuery/Deal Stages",
      "PipelineCRMQuery/Documents Tags",
      "PipelineCRMQuery/Event Categories",
      "PipelineCRMQuery/Lead Sources",
      "PipelineCRMQuery/Lead Statuses",
      "PipelineCRMQuery/Note Categories",
      "PipelineCRMQuery/Notes",
      "PipelineCRMQuery/Person Custom Groups",
      "PipelineCRMQuery/Person Custom Labels",
      "PipelineCRMQuery/Predefined Contact Tags",
      "PipelineCRMQuery/Users",
      "PipedriveQuery/Deal Timeline",
      "QuickbooksReportsQuery/Transaction List By Vendor",
      "ServiceNowQuery/Aggregate Statistics",
      "ServiceNowQuery/Service Catalog Items",
      "SquareQuery/Cash Drawer Shift Events",
      "TwilioQuery/Alerts",
      "TwitterQuery/Users by IDs",
      "XeroQuery/Aged Receivables By Contact",
      "XeroQuery/Contact Groups",
      "XeroQuery/Quotes",
      "XeroQuery/Repeating Invoices",
      "ZohoCRMQuery/Variable Group");
}
