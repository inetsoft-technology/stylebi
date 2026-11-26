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

package inetsoft.uql.rest.listing;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.EndpointsTestConfig;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.DataSourceListing;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.tabular.TabularService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Checks, without any network access, what a user meets before ever running a query: the list of
 * data source types of the REST connector. A listing that was never registered, a missing icon or
 * display name, a type without a service, or a service that points at a class that does not exist
 * shows up as a missing or broken entry in the "new data source" dialog.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class, RestDataSourceListingTest.RepositoryConfig.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
class RestDataSourceListingTest {
   /** A repository with no data sources, which is all that naming a new data source needs. */
   @Configuration
   static class RepositoryConfig {
      @Bean
      XRepository xRepository() throws Exception {
         XRepository repository = mock(XRepository.class);
         when(repository.getDataSourceNames()).thenReturn(new String[0]);
         return repository;
      }
   }

   @Test
   void everyListingClassIsRegistered() throws Exception {
      Set<String> registered = new TreeSet<>();
      listings().forEach(l -> registered.add(l.getClass().getName()));
      List<String> missing = new ArrayList<>();

      for(String name : listingClassNames()) {
         if(!registered.contains(name) && !HIDDEN.contains(name)) {
            missing.add(name);
         }
      }

      assertTrue(missing.isEmpty(), "listings that are not returned by the service: " + missing);

      for(String name : HIDDEN) {
         assertFalse(registered.contains(name), name + " is listed now, remove it from HIDDEN");
      }
   }

   @Test
   void everyListingHasAUniqueNameAndACategory() {
      Set<String> names = new HashSet<>();
      List<String> problems = new ArrayList<>();

      for(DataSourceListing listing : listings()) {
         if(listing.getName() == null || listing.getName().isBlank()) {
            problems.add(listing.getClass().getSimpleName() + ": blank name");
         }
         else if(!names.add(listing.getName())) {
            problems.add(listing.getClass().getSimpleName() + ": duplicate name " +
                         listing.getName());
         }

         if(listing.getCategory() == null || listing.getCategory().isBlank()) {
            problems.add(listing.getClass().getSimpleName() + ": blank category");
         }
      }

      assertNoProblems(problems);
   }

   @Test
   void everyListingHasAnIconThatExists() {
      List<String> problems = new ArrayList<>();

      for(DataSourceListing listing : listings()) {
         String icon = listing.getIcon();

         if(icon == null || icon.isBlank()) {
            problems.add(listing.getName() + ": no icon");
         }
         else if(listing.getClass().getResource(icon) == null) {
            problems.add(listing.getName() + ": icon " + icon + " is not on the classpath");
         }
      }

      assertNoProblems(problems);
   }

   @Test
   void everyListingCreatesADataSourceWhoseTypeHasAServiceAndEveryServiceIsReachable()
      throws Exception
   {
      Map<String, TabularService> services = services();
      Map<String, String> producedBy = new HashMap<>();
      List<String> problems = new ArrayList<>();

      for(DataSourceListing listing : listings()) {
         try {
            XDataSource dataSource = listing.createDataSource();

            if(dataSource == null) {
               problems.add(listing.getName() + ": no data source created");
               continue;
            }

            if(dataSource.getName() == null || dataSource.getName().isBlank()) {
               problems.add(listing.getName() + ": the new data source has no name");
            }

            if(!services.containsKey(dataSource.getType())) {
               problems.add(listing.getName() + ": creates type " + dataSource.getType() +
                            ", which has no tabular service");
            }

            String other = producedBy.put(dataSource.getType(), listing.getName());

            if(other != null) {
               problems.add(listing.getName() + " and " + other + " both create type " +
                            dataSource.getType());
            }
         }
         catch(Exception e) {
            problems.add(listing.getName() + ": " + e);
         }
      }

      for(String type : services.keySet()) {
         if(!producedBy.containsKey(type) && !HIDDEN_TYPES.contains(type)) {
            problems.add(type + ": has a tabular service, but no listing creates it");
         }
      }

      for(String type : HIDDEN_TYPES) {
         assertFalse(producedBy.containsKey(type),
                     type + " is listed now, remove it from HIDDEN_TYPES");
      }

      assertNoProblems(problems);
   }

   @Test
   void everyServicePointsAtClassesThatExist() {
      List<String> problems = new ArrayList<>();

      for(TabularService service : services().values()) {
         String type = service.getDataSourceType();

         for(String className : new String[] {
            service.getDataSourceClass(), service.getQueryClass(), service.getRuntimeClass() })
         {
            try {
               Class.forName(className, false, getClass().getClassLoader());
            }
            catch(ClassNotFoundException | RuntimeException e) {
               problems.add(type + ": class " + className + " cannot be loaded");
            }
         }
      }

      assertNoProblems(problems);
   }

   /**
    * Listings that exist but are deliberately left out of the service, so that the data sources
    * cannot be created from the dialog (Bug #70342, "Hide unused datasources"). The hiding is done
    * by {@code RestDataSourceListingService}, which simply does not list these classes; to show one
    * again, add it there and remove it from this set. The test fails when one of them is
    * registered, so that the entry is removed.
    */
   private static final Set<String> HIDDEN = Set.of(
      "inetsoft.uql.rest.datasource.adobeanalytics.AdobeAnalyticsListing",
      "inetsoft.uql.rest.datasource.box.BoxListing",
      "inetsoft.uql.rest.datasource.fusebill.FusebillListing",
      "inetsoft.uql.rest.datasource.remedyforce.RemedyforceListing");

   /** The data source types of {@link #HIDDEN}, which still have a tabular service. */
   private static final Set<String> HIDDEN_TYPES = Set.of(
      "Rest.AdobeAnalytics", "Rest.Box", "Rest.Fusebill", "Rest.Remedyforce");

   private static List<DataSourceListing> listings() {
      return new RestDataSourceListingService().getDataSourceListings();
   }

   private static Map<String, TabularService> services() {
      Map<String, TabularService> services = new TreeMap<>();

      for(TabularService service : ServiceLoader.load(TabularService.class)) {
         if(service.getClass().getName().startsWith("inetsoft.uql.rest.")) {
            TabularService previous = services.put(service.getDataSourceType(), service);
            assertNull(previous, "two services for " + service.getDataSourceType());
         }
      }

      return services;
   }

   private static List<String> listingClassNames() {
      ClassPathScanningCandidateComponentProvider scanner =
         new ClassPathScanningCandidateComponentProvider(false);
      scanner.addIncludeFilter(new AssignableTypeFilter(DataSourceListing.class));
      List<String> names = new ArrayList<>();

      for(BeanDefinition definition : scanner.findCandidateComponents("inetsoft.uql.rest")) {
         names.add(definition.getBeanClassName());
      }

      names.sort(Comparator.naturalOrder());
      return names;
   }

   private static void assertNoProblems(List<String> problems) {
      assertTrue(problems.isEmpty(),
                 problems.size() + " problem(s):\n" + String.join("\n", problems));
   }
}
