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
package inetsoft.uql.rest.json;

import inetsoft.uql.tabular.*;
import inetsoft.uql.util.Config;
import inetsoft.util.ConfigurationContext;
import inetsoft.web.wiz.service.TabularQueryContractSupport;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationContext;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SHIP-BLOCKING ACCEPTANCE CHECK (design doc section 7.8, D6): the general read-back-equality
 * check (section 3.6) against {@link RestJsonQuery}'s real, full parameter set -- confirming no
 * setter that legitimately normalises its input produces a false positive. Every pagination
 * branch and the custom lookup chain are exercised, since those are exactly the params section
 * 3.6 had no direct precedent for.
 *
 * <p>No {@code XDataSource} is attached to the query -- the schema extractor and
 * {@code TabularQueryContractSupport} only need {@code query.getType()}/the dependency-probing
 * class, never a live data source, and attaching a real {@code RestJsonDataSource} here would
 * pull in credential-service bean wiring this check does not need.</p>
 */
@Tag("core")
class TabularQueryContractSupportRestJsonQueryReadBackTest {
   @BeforeAll
   static void installContext() {
      previous = ConfigurationContext.getContext();
      Config config = mock(Config.class);
      when(config.getResourceBundle(org.mockito.ArgumentMatchers.any())).thenReturn(null);

      ApplicationContext context = mock(ApplicationContext.class);
      when(context.getBean(Config.class)).thenReturn(config);
      ConfigurationContext.getContext().setApplicationContext(context);
   }

   @AfterAll
   static void clearContext() {
      if(previous != null) {
         previous.setApplicationContext(null);
      }
   }

   @Test
   void readBackEqualityHoldsForEveryPaginationBranch() throws Exception {
      for(String paginationType : PAGINATION_TYPES) {
         RestJsonQuery query = new RestJsonQuery();

         TabularQuerySchema schema =
            new TabularSchemaExtractor().extract(query, RestJsonDataSource.TYPE);
         Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());

         Map<String, Object> queryParams = new LinkedHashMap<>();

         for(TabularQuerySchema.Param param : schema.getParams()) {
            if(!param.isConditional() && !"paginationType".equals(param.getName())) {
               queryParams.put(param.getName(), legalValue(param));
            }
         }

         queryParams.put("paginationType", paginationType);

         List<String> gated = schema.getDependencyMatrix()
            .getOrDefault("paginationType", Map.of()).getOrDefault(paginationType, List.of());

         for(String name : gated) {
            queryParams.put(name, legalValue(schema.getParam(name)));
         }

         if(gated.contains("linkParamType")) {
            queryParams.put("linkParamType", "LINK_HEADER");
            String rowKey = "paginationType=" + paginationType + " & linkParamType=LINK_HEADER";
            List<String> comboGated = schema.getDependencyMatrix()
               .getOrDefault("paginationType & linkParamType", Map.of()).get(rowKey);

            if(comboGated != null) {
               for(String name : comboGated) {
                  queryParams.put(name, legalValue(schema.getParam(name)));
               }
            }
         }

         for(int i = 0; i < 5; i++) {
            queryParams.put("lookupUrl" + i, "/v1/x/{param" + (i + 1) + "}");
            queryParams.put("lookupJsonPath" + i, "$.data[*]");
            queryParams.put("lookupKey" + i, "id");
            queryParams.put("lookupIgnoreBaseUrl" + i, "false");
         }

         String description = assertDoesNotThrow(
            () -> TabularQueryContractSupport.applyQueryContract(
               query, pmap, schema, queryParams, "RestJsonQuery live check"),
            () -> "false positive from the read-back check under paginationType=" + paginationType);

         assertNotNull(description);
      }
   }

   /** A value legal for the param's own declared type/tags -- never one a setter would reject. */
   private static String legalValue(TabularQuerySchema.Param param) {
      if(param.getTags() != null && !param.getTags().isEmpty()) {
         return param.getTags().get(0);
      }

      String javaType = param.getJavaType();

      if("boolean".equals(javaType) || Boolean.class.getName().equals(javaType)) {
         return "true";
      }

      if("int".equals(javaType) || Integer.class.getName().equals(javaType)) {
         return "5";
      }

      return "v-" + param.getName();
   }

   private static final List<String> PAGINATION_TYPES = List.of(
      "NONE", "PAGE_COUNT", "TOTAL_COUNT_AND_OFFSET", "TOTAL_COUNT_AND_PAGE", "ITERATION",
      "LINK_ITERATION");

   private static ConfigurationContext previous;
}
