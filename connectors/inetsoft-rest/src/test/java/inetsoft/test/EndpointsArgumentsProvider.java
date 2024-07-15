/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.rest.AbstractRestDataSource;
import inetsoft.uql.rest.json.EndpointJsonQuery;
import inetsoft.uql.rest.json.OAuthEndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.uql.tabular.oauth.*;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.yaml.snakeyaml.Yaml;

import java.beans.*;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Stream;

public class EndpointsArgumentsProvider implements ArgumentsProvider {
   @Override
   public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
      Class<?> testClass = context.getRequiredTestClass();
      EndpointsSource source = context.getElement()
         .orElseThrow(() -> new IllegalStateException("EndpointsArgumentsProvider used without @EndpointsSource"))
         .getAnnotation(EndpointsSource.class);

      EndpointTestDescriptors tests;

      try(InputStream input = testClass.getResourceAsStream("parameters.json")) {
         tests = new ObjectMapper().readValue(input, EndpointTestDescriptors.class);
      }

      return tests.getTests().stream()
         .map(test -> createParameters(test, source.dataSource(), source.query(), testClass));
   }

   private Arguments createParameters(EndpointTestDescriptor test,
                                      Class<? extends AbstractRestDataSource> dataSourceClass,
                                      Class<? extends EndpointJsonQuery<?>> queryClass,
                                      Class<?> testClass)
   {
      return () -> {
         EndpointJsonQuery<?> query =
            createQuery(dataSourceClass, queryClass, testClass, test);
         return new Object[] { test.getEndpoint(), query };
      };
   }

   private <Q extends EndpointJsonQuery<?>> Q createQuery(
      Class<? extends AbstractRestDataSource> dataSourceClass,
      Class<Q> queryClass, Class<?> testClass, EndpointTestDescriptor test)
   {
      AbstractRestDataSource dataSource = createInstance(dataSourceClass, testClass, "datasource");

      if(dataSource instanceof OAuthEndpointJsonDataSource) {
         authorize((OAuthEndpointJsonDataSource) dataSource, testClass);
      }

      Q query = createInstance(queryClass, testClass, "query");
      query.setDataSource(dataSource);
      query.setJsonPath(test.getJsonPath());
      query.setExpanded(test.isExpandArrays());
      query.setExpandedPath(test.getExpandedArrayPath());
      query.setEndpoint(test.getEndpoint());
      RestParameters restParameters = query.getParameters();

      for(Map.Entry<String, String> testParameter : test.getParameters().entrySet()) {
         for(RestParameter restParameter : restParameters.getParameters()) {
            if(testParameter.getKey().equals(restParameter.getName())) {
               restParameter.setValue(testParameter.getValue());
               break;
            }
         }
      }

      query.setParameters(restParameters);
      return query;
   }

   private void authorize(OAuthEndpointJsonDataSource dataSource, Class<?> testClass) {
      if(dataSource.getAccessToken() == null) {
         try {
            String serviceName = getOAuthServiceName(dataSource.getClass());
            Tokens tokens;

            if(serviceName == null) {
               String scope = dataSource.getScope();
               List<String> scopeList = new ArrayList<>();

               if(scope != null && !scope.isEmpty()) {
                  scopeList.addAll(Arrays.asList(scope.split(" ")));
               }

               String flags = dataSource.getOauthFlags();
               Set<String> flagsSet = new HashSet<>();

               if(flags != null && !flags.isEmpty()) {
                  flagsSet.addAll(Arrays.asList(flags.split(" ")));
               }

               tokens = AuthorizationClient.authorizeInBrowser(
                  dataSource.getClientId(), dataSource.getClientSecret(), scopeList,
                  dataSource.getAuthorizationUri(), dataSource.getTokenUri(), flagsSet, null);
            }
            else {
               tokens = AuthorizationClient.authorizeInBrowser(serviceName);
            }

            dataSource.updateTokens(tokens);
            Map<String, Object> properties = getProperties(testClass, "datasource");

            if(properties != null) {
               properties.put("accessToken", dataSource.getAccessToken());
               properties.put("refreshToken", dataSource.getRefreshToken());
               properties.put("tokenExpiration", dataSource.getTokenExpiration());
            }
         }
         catch(AuthorizationJobException e) {
            throw new RuntimeException("Authorization failed", e);
         }
      }
   }

   private String getOAuthServiceName(Class<?> dataSourceClass) {
      View view = dataSourceClass.getAnnotation(View.class);

      if(view != null) {
         for(View1 view1 : view.value()) {
            if(view1.type() == ViewType.BUTTON && view1.button().type() == ButtonType.OAUTH) {
               if(view1.button().oauth().serviceName().isEmpty()) {
                  return null;
               }

               return view1.button().oauth().serviceName();
            }
         }
      }

      return null;
   }

   private <T> T createInstance(Class<T> clazz, Class<?> testClass, String key) {
      try {
         T instance = clazz.getConstructor().newInstance();
         Map<String, Object> properties = getProperties(testClass, key);

         if(properties != null) {
            BeanInfo info = Introspector.getBeanInfo(clazz);

            for(PropertyDescriptor property : info.getPropertyDescriptors()) {
               Object value = properties.get(property.getName());

               if(value != null) {
                  property.getWriteMethod().invoke(instance, value);
               }
            }
         }

         return instance;
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to create instance of " + clazz.getName(), e);
      }
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   private Map<String, Object> getProperties(Class<?> testClass, String subkey) {
      String key = testClass.getPackage().getName();
      int index = key.lastIndexOf('.');

      if(index >= 0) {
         key = key.substring(index + 1);
      }

      Map<String, Object> map = (Map) Config.INSTANCE.properties.get(key);

      if(map != null) {
         return (Map) map.get(subkey);
      }

      return null;
   }

   enum Config {
      INSTANCE;

      private final Map<String, Object> properties;

      Config() {
         try(InputStream input = getClass().getResourceAsStream("/query-tests.yaml")) {
            properties = new Yaml().load(input);
         }
         catch(Exception e) {
            throw new RuntimeException("Failed to load query test settings", e);
         }
      }
   }

   @SuppressWarnings("unused")
   public static final class EndpointTestDescriptor {
      public String getEndpoint() {
         return endpoint;
      }

      public void setEndpoint(String endpoint) {
         this.endpoint = endpoint;
      }

      public String getJsonPath() {
         return jsonPath;
      }

      public void setJsonPath(String jsonPath) {
         this.jsonPath = jsonPath;
      }

      public boolean isExpandArrays() {
         return expandArrays;
      }

      public void setExpandArrays(boolean expandArrays) {
         this.expandArrays = expandArrays;
      }

      public String getExpandedArrayPath() {
         return expandedArrayPath;
      }

      public void setExpandedArrayPath(String expandedArrayPath) {
         this.expandedArrayPath = expandedArrayPath;
      }

      public Map<String, String> getParameters() {
         return parameters;
      }

      public void setParameters(Map<String, String> parameters) {
         this.parameters = parameters;
      }

      private String endpoint;
      private String jsonPath;
      private boolean expandArrays;
      private String expandedArrayPath;
      private Map<String, String> parameters;
   }

   @SuppressWarnings("unused")
   public static final class EndpointTestDescriptors {
      public List<EndpointTestDescriptor> getTests() {
         return tests;
      }

      public void setTests(List<EndpointTestDescriptor> tests) {
         this.tests = tests;
      }

      private List<EndpointTestDescriptor> tests;
   }
}
