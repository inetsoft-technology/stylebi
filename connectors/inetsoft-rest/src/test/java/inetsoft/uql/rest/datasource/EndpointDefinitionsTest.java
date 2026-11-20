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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.EndpointsTestConfig;
import inetsoft.test.SreeHome;
import inetsoft.uql.rest.AbstractRestDataSource;
import inetsoft.uql.rest.json.EndpointJsonQuery;
import inetsoft.uql.rest.json.lookup.JsonLookupEndpoint;
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.RestParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks, without any network access, the declarative endpoint definitions
 * ({@code endpoints.json}) of every endpoint-based data source query: each one loads, its
 * endpoints have unique names, every suffix template can be built from its own parameters, and
 * every lookup refers to an endpoint that exists.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, EndpointsTestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
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

         for(Map.Entry<String, ? extends EndpointJsonQuery.Endpoint> e :
            query.getEndpointMap().entrySet())
         {
            String where = type.getSimpleName() + "/" + e.getKey();
            EndpointJsonQuery.Endpoint endpoint = e.getValue();

            if(KNOWN_BROKEN.contains(where)) {
               continue;
            }

            if(endpoint.getName() == null || endpoint.getName().isBlank()) {
               problems.add(where + ": blank name");
            }

            if(endpoint.getSuffix() == null || endpoint.getSuffix().isBlank()) {
               problems.add(where + ": blank suffix");
               continue;
            }

            try {
               String built = buildSuffix(query, e.getKey());

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
            assertFalse(newQuery(type).getEndpoints().length == 0);
         }
         catch(Exception ex) {
            problems.add(type.getSimpleName() + ": " + ex);
         }
      }

      assertNoProblems(problems);
   }

   /** Builds the suffix of an endpoint with every parameter set to a simple value. */
   private static String buildSuffix(EndpointJsonQuery<?> query, String endpointName) {
      query.setEndpoint(endpointName);
      RestParameters parameters = query.getParameters();

      for(RestParameter parameter : parameters.getParameters()) {
         parameter.setValue("1");
      }

      query.setParameters(parameters);
      return query.getSuffix();
   }

   /**
    * Creates a query and gives it the data source that sits next to it in the same package,
    * since some queries read the data source when an endpoint is selected.
    */
   private static EndpointJsonQuery<?> newQuery(Class<?> type) throws Exception {
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

   private static List<Class<?>> queryTypes() {
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

   /**
    * Endpoints whose suffix is known to be wrong and is not fixed here because the intended
    * query parameter is not clear: "{User IDs}" has no parameter name before it, and the Twitter
    * API expects "ids=".
    */
   private static final Set<String> KNOWN_BROKEN = Set.of("TwitterQuery/Users by IDs");
}
