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
package inetsoft.uql.tabular;

import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.util.credential.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77699: a query runs on a clone of the data source that the registry caches, with its
 * variables replaced with the values of the query. The clone shared its credential with the
 * cached instance, so replacing a variable of a property kept in the credential rewrote the
 * cached instance, and every later query and user got the value of the first query.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  TabularDataSourceCloneTest.CredentialServiceConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TabularDataSourceCloneTest {
   @Test
   void cloneHasItsOwnCredential() {
      CredentialDataSource ds = new CredentialDataSource();
      ds.setUser("$(user)");

      CredentialDataSource copy = (CredentialDataSource) ds.clone();

      assertNotNull(copy.getCredential());
      assertNotSame(ds.getCredential(), copy.getCredential());
      assertEquals(ds, copy);
      assertEquals("$(user)", copy.getUser());

      copy.setUser("changed");
      assertEquals("$(user)", ds.getUser());
   }

   // what TabularHandler.execute does: each query clones the cached instance and replaces the
   // variables of the clone, the cached instance keeps its template
   @Test
   void replacingVariablesOfAQueryCloneKeepsTheTemplate() {
      CredentialDataSource cached = new CredentialDataSource();
      cached.setName("credDs");
      cached.setUser("$(user)");

      CredentialDataSource first = (CredentialDataSource) cached.clone();
      TabularUtil.replaceVariables(first, vars("alice"));
      CredentialDataSource second = (CredentialDataSource) cached.clone();
      TabularUtil.replaceVariables(second, vars("bob"));

      assertEquals("alice", first.getUser());
      assertEquals("bob", second.getUser());
      assertEquals("$(user)", cached.getUser());
   }

   // a cloud credential is copied with its id and the values read from the secrets manager,
   // without reading them again
   @Test
   void cloneOfACloudCredential() {
      CountingCloudCredential credential = new CountingCloudCredential();
      credential.setId("secret-id");
      credential.setDBType("db");
      credential.setUser("fetched-user");
      CredentialDataSource ds = new CredentialDataSource();
      ds.setCredential(credential);

      CredentialDataSource copy = (CredentialDataSource) ds.clone();

      assertNotSame(credential, copy.getCredential());
      CloudPasswordCredential copied = (CloudPasswordCredential) copy.getCredential();
      assertEquals("secret-id", copied.getId());
      assertEquals("db", copied.getDBType());
      assertEquals("fetched-user", copy.getUser());
      assertEquals(0, credential.fetches);
      assertEquals(0, ((CountingCloudCredential) copied).fetches);
      assertEquals(ds, copy);
   }

   @Test
   void cloneWithoutACredential() {
      NoCredentialDataSource ds = new NoCredentialDataSource();
      assertNull(ds.getCredential());

      NoCredentialDataSource copy = (NoCredentialDataSource) ds.clone();

      assertNotNull(copy);
      assertNull(copy.getCredential());
      assertEquals(ds, copy);
   }

   private static VariableTable vars(String user) {
      VariableTable vars = new VariableTable();
      vars.put("user", user);
      return vars;
   }

   public static final class CredentialDataSource extends TabularDataSource<CredentialDataSource> {
      CredentialDataSource() {
         super("test", CredentialDataSource.class);
      }

      @Override
      protected CredentialType getCredentialType() {
         return CredentialType.PASSWORD;
      }

      @Property(label = "User")
      public String getUser() {
         return ((PasswordCredential) getCredential()).getUser();
      }

      public void setUser(String user) {
         ((PasswordCredential) getCredential()).setUser(user);
      }
   }

   public static final class NoCredentialDataSource extends TabularDataSource<NoCredentialDataSource> {
      NoCredentialDataSource() {
         super("test", NoCredentialDataSource.class);
      }

      @Override
      protected CredentialType getCredentialType() {
         return null;
      }
   }

   public static final class CountingCloudCredential extends CloudPasswordCredential {
      @Override
      public void fetchCredential() {
         fetches++;
      }

      private int fetches;
   }

   // the constructor of the CredentialService bean is package private
   @Configuration
   static class CredentialServiceConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }
   }
}
