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
package inetsoft.uql.rest.datasource.toggl;

import inetsoft.test.*;
import inetsoft.uql.rest.json.EndpointJsonQuery;
import inetsoft.uql.rest.json.JsonTransformer;
import inetsoft.uql.rest.json.lookup.*;
import inetsoft.uql.tabular.RestParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81943: the "Workspace Projects" lookups must bind the project row's id to a project
 * parameter of the lookup endpoint, while the workspace id is inherited from the parent query.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, CredentialTestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
class TogglLookupTest {
   @Test
   void projectUsersLookupFiltersByProjectInInheritedWorkspace() {
      assertEquals("/api/v9/workspaces/9001/project_users?project_ids=1001",
                   lookupSuffix("Workspace Project Users"));
   }

   @Test
   void projectTasksLookupPutsProjectIdInPath() {
      assertEquals("/api/v9/workspaces/9001/projects/1001/tasks",
                   lookupSuffix("Workspace Project Tasks"));
   }

   @Test
   void standaloneProjectUsersWithoutProjectIdsListsWholeWorkspace() {
      final TogglQuery query = createQuery("Workspace Project Users");
      assertEquals("/api/v9/workspaces/9001/project_users", query.getSuffix());
   }

   private static String lookupSuffix(String lookupEndpointName) {
      final TogglQuery parent = createQuery("Workspace Projects");
      final JsonLookupEndpoint lookupEndpoint =
         parent.getJsonLookupEndpoint("Workspace Projects", lookupEndpointName);
      assertNotNull(lookupEndpoint, lookupEndpointName);

      final JsonLookupQuery lookupQuery = new JsonLookupQuery();
      lookupQuery.setLookupEndpoint(lookupEndpoint);
      lookupQuery.setExpandArrays(true);
      lookupQuery.setTopLevelOnly(false);
      lookupQuery.setJsonPath("$");
      lookupQuery.setLookupQueries(Collections.emptyList());

      final String json = "[{\"id\": 1001, \"workspace_id\": 9001, \"name\": \"P1\"}]";
      final Object rows = new JsonTransformer().transform(
         new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
      final Object row = JsonTransformer.getJsonProvider().getArrayIndex(rows, 0);

      final EndpointJsonQuery<?> lookup = (EndpointJsonQuery<?>) new LookupService()
         .createQueryFromLookupQuery(parent, parent, lookupQuery, row);
      assertNotNull(lookup, lookupEndpointName);
      assertEquals(lookupEndpointName, lookup.getEndpoint());
      return lookup.getSuffix();
   }

   private static TogglQuery createQuery(String endpoint) {
      final TogglQuery query = new TogglQuery();
      query.setEndpoint(endpoint);
      final RestParameters parameters = query.getParameters();
      parameters.findParameter("Workspace ID").setValue("9001");
      query.setParameters(parameters);
      return query;
   }
}
