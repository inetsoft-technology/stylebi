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
package inetsoft.uql.tabular.impl;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.util.credential.CredentialType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #75751: two organizations with independently-scoped tabular data sources that happen to
 * share the same name must not collide on the same tabular query cache key.
 *
 * Bug #76658: a data source whose result depends on the user executing the query must not have
 * its result served to another user from the process wide cache.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TabularHandlerTest {
   @Test
   void getQueryKeyIsScopedByOrganizationForIndependentPerOrgDataSources() throws Exception {
      TabularHandler handler = new TabularHandler();

      String keyOrgA = handler.getQueryKey(newQuery(), newVars(), principal("admin", "orgA"));
      String keyOrgB = handler.getQueryKey(newQuery(), newVars(), principal("user0", "orgB"));

      // Same data source name, same query, and same variable value ("A") in both organizations
      // -- only the organization of the querying user differs. Bug #75751: the two orgs'
      // independent, same-named data sources shared a cache entry because the key wasn't scoped
      // by organization.
      assertNotEquals(keyOrgA, keyOrgB,
         "cache keys for the same query/data source in two different organizations must not " +
         "collide");
   }

   @Test
   void getQueryKeyIsStableForSameUserAndQuery() throws Exception {
      TabularHandler handler = new TabularHandler();
      Principal user = principal("admin", "orgA");

      String key1 = handler.getQueryKey(newQuery(), newVars(), user);
      String key2 = handler.getQueryKey(newQuery(), newVars(), user);

      assertEquals(key1, key2,
         "the same user re-running the same query must still hit the cache");
   }

   @Test
   void getQueryKeyDistinguishesUsersWhenTheResultIsUserDependent() throws Exception {
      TabularHandler handler = new TabularHandler();
      Principal user = principal("admin", "orgA");

      // the same query against the same data source, run by users that resolve to different
      // impersonated identities. Bug #76658: with kerberos constrained delegation the endpoint
      // applies its own per-user authorization, so these two results are not interchangeable.
      String key1 = handler.getQueryKey(newQuery("PRINCIPAL|5|user1"), newVars(), user);
      String key2 = handler.getQueryKey(newQuery("PRINCIPAL|5|user2"), newVars(), user);

      assertNotEquals(key1, key2,
         "cache keys for a user dependent data source must not collide across users");
   }

   @Test
   void getQueryKeyIsUnchangedWhenTheResultIsNotUserDependent() throws Exception {
      TabularHandler handler = new TabularHandler();
      Principal user = principal("admin", "orgA");

      String baseKey = handler.getQueryKey(newQuery(null), newVars(), user);
      String discriminatedKey = handler.getQueryKey(newQuery("PRINCIPAL|5|user1"), newVars(), user);

      // every data source that is not user dependent returns a null discriminator, and must
      // therefore produce exactly the key it produced before Bug #76658 was fixed
      assertTrue(discriminatedKey.startsWith(baseKey),
         "the discriminator must only be appended, leaving the key of a data source that is " +
         "not user dependent unchanged");
      assertNotEquals(baseKey, discriminatedKey,
         "a non-null discriminator must change the key");
   }

   @Test
   void getQueryKeyIsSharedByUsersThatResolveToTheSameDiscriminator() throws Exception {
      TabularHandler handler = new TabularHandler();

      // two separate callers in the same organization whose data source resolves them to the
      // same identity, e.g. STATIC impersonation
      String key1 = handler.getQueryKey(
         newQuery("STATIC|4|svc1"), newVars(), principal("user1", "orgA"));
      String key2 = handler.getQueryKey(
         newQuery("STATIC|4|svc1"), newVars(), principal("user2", "orgA"));

      assertEquals(key1, key2,
         "users that resolve to the same identity must still share a cache entry");
   }

   private static TestTabularQuery newQuery() {
      return newQuery(null);
   }

   private static TestTabularQuery newQuery(String cacheDiscriminator) {
      TestTabularQuery query = new TestTabularQuery();
      query.addVariable(new UserVariable("param"));
      TestTabularDataSource ds = new TestTabularDataSource();
      ds.setName("Tabular1");
      ds.cacheDiscriminator = cacheDiscriminator;
      query.setDataSource(ds);
      return query;
   }

   private static VariableTable newVars() {
      VariableTable vars = new VariableTable();
      vars.put("param", "A");
      return vars;
   }

   private static Principal principal(String name, String orgID) {
      return new SRPrincipal(
         new IdentityID(name, orgID), new IdentityID[0], new String[0], orgID, 0L);
   }

   private static final class TestTabularDataSource extends TabularDataSource<TestTabularDataSource> {
      TestTabularDataSource() {
         super("Test", TestTabularDataSource.class);
      }

      @Override
      protected CredentialType getCredentialType() {
         return null;
      }

      @Override
      public String getCacheDiscriminator() {
         return cacheDiscriminator;
      }

      private String cacheDiscriminator;
   }

   private static final class TestTabularQuery extends TabularQuery {
      TestTabularQuery() {
         super("Test");
      }
   }
}
