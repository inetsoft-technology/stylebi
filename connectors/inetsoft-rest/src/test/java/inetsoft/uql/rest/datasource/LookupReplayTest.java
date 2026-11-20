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
import inetsoft.test.ReplaySupport;
import inetsoft.test.ScriptedHttpHandler;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.datasource.clockify.ClockifyDataSource;
import inetsoft.uql.rest.datasource.clockify.ClockifyQuery;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lookups of real endpoint configurations: the chain Workspaces, Users and Time Entries of
 * Clockify, selected the way a user selects it, with the responses scripted. It looks for what goes
 * wrong around a lookup rather than for the plain case: a neighbour row whose lookup fails, a field
 * that the lookup result overwrites, a parent without children, the same key looked up again, every
 * page of the parent, the row limit.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
class LookupReplayTest {
   @Test
   void twoLevelsAreReadAndEachLevelGetsTheKeyOfItsParent() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/workspaces/w1/user/u1/time-entries", "[{\"id\":\"t1\"}]", "[]")
         .on("/workspaces/w1/user/u2/time-entries", "[{\"id\":\"t2\"},{\"id\":\"t3\"}]", "[]")
         .on("/workspaces/w1/users", "[{\"id\":\"u1\"},{\"id\":\"u2\"}]", "[]")
         .on("/v1/workspaces", "[{\"id\":\"w1\",\"name\":\"One\"}]");

      String dump = dump(ReplaySupport.runTable(workspacesUsersTimeEntries(), http));

      assertNotNull(dump);
      // the workspaces, the users (two pages), and two pages of time entries for each of two users
      assertEquals(7, http.requests().size(), http.requests().toString());
      assertEquals(3, count(dump, "{"), dump);
      assertTrue(dump.contains("Time Entries.id=t1") && dump.contains("Time Entries.id=t2") &&
                 dump.contains("Time Entries.id=t3"), dump);
   }

   @Test
   void everyPageOfTheParentGetsItsLookups() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/workspaces/w1/user/u1/time-entries", "[{\"id\":\"t1\"}]", "[]")
         .on("/workspaces/w1/user/u2/time-entries", "[{\"id\":\"t2\"}]", "[]")
         .on("/workspaces/w1/users", "[{\"id\":\"u1\"}]", "[{\"id\":\"u2\"}]", "[]")
         .on("/v1/workspaces", "[{\"id\":\"w1\"}]");

      String dump = dump(ReplaySupport.runTable(workspacesUsersTimeEntries(), http));

      assertNotNull(dump);
      assertTrue(dump.contains("Time Entries.id=t1") && dump.contains("Time Entries.id=t2"),
                 dump + "\n" + http.requests());
   }

   @Test
   void aFailingLookupFailsTheWholeQueryEvenForTheHealthyNeighbours() throws Exception {
      // u1's time entries fail, u2's are fine: the query returns nothing at all, not even the
      // workspace and u2, and u2 is never asked for; this pins that
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .onReplies(url -> url.contains("/user/u1/time-entries"),
                    ScriptedHttpHandler.failure(new IOException("HTTP 500")))
         .on("/workspaces/w1/user/u2/time-entries", "[{\"id\":\"t2\"}]", "[]")
         .on("/workspaces/w1/users", "[{\"id\":\"u1\"},{\"id\":\"u2\"}]", "[]")
         .on("/v1/workspaces", "[{\"id\":\"w1\"}]");

      String dump = dump(ReplaySupport.runTable(workspacesUsersTimeEntries(), http));

      assertNull(dump, dump);
      assertTrue(http.requests().stream().noneMatch(u -> u.contains("/user/u2/")),
                 http.requests().toString());
   }

   @Test
   @Disabled("Bug #81927: a lookup result replaces a field of the parent with the same name; " +
             "enable when it is fixed")
   void aLookupDoesNotOverwriteAFieldOfTheParentThatHasTheSameName() throws Exception {
      // the workspace already has a field called "Users"
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/workspaces/w1/users", "[{\"id\":\"u1\"}]", "[]")
         .on("/v1/workspaces", "[{\"id\":\"w1\",\"Users\":\"own value\"}]");

      String dump = dump(ReplaySupport.runTable(workspacesUsers(), http));

      assertNotNull(dump);
      assertTrue(dump.contains("own value"), "the field of the workspace was overwritten: " + dump);
   }

   @Test
   void aParentWithNoChildrenIsNotDropped() throws Exception {
      // u1 has no time entries at all, u2 has one
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/workspaces/w1/user/u1/time-entries", "[]")
         .on("/workspaces/w1/user/u2/time-entries", "[{\"id\":\"t2\"}]", "[]")
         .on("/workspaces/w1/users", "[{\"id\":\"u1\"},{\"id\":\"u2\"}]", "[]")
         .on("/v1/workspaces", "[{\"id\":\"w1\"}]");

      String dump = dump(ReplaySupport.runTable(workspacesUsersTimeEntries(), http));

      assertNotNull(dump);
      assertTrue(dump.contains("Users.id=u1"), "the user without time entries is gone: " + dump);
   }

   @Test
   void aRowWithoutTheKeyAtTheSecondLevelIsSkippedAndItsNeighboursAreNot() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/workspaces/w1/user/u2/time-entries", "[{\"id\":\"t2\"}]", "[]")
         .on("/workspaces/w1/users", "[{\"name\":\"no id\"},{\"id\":\"u2\"}]", "[]")
         .on("/v1/workspaces", "[{\"id\":\"w1\"}]");

      String dump = dump(ReplaySupport.runTable(workspacesUsersTimeEntries(), http));

      assertNotNull(dump);
      assertTrue(dump.contains("Time Entries.id=t2"), dump);
      assertTrue(http.requests().stream().noneMatch(u -> u.contains("user/null")),
                 http.requests().toString());
   }

   @Test
   @Disabled("Bug #81927 (related): lookups are not cached per key, so a key that repeats over " +
             "many rows is requested once per row; not a wrong result, enable if it is changed")
   void theSameKeyIsLookedUpOnceNotOncePerRow() throws Exception {
      // the same user listed twice: its time entries are asked for twice
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/workspaces/w1/user/u1/time-entries", "[{\"id\":\"t1\"}]", "[]")
         .on("/workspaces/w1/users", "[{\"id\":\"u1\"},{\"id\":\"u1\"}]", "[]")
         .on("/v1/workspaces", "[{\"id\":\"w1\"}]");

      ReplaySupport.runTable(workspacesUsersTimeEntries(), http);

      long asked = http.requests().stream()
         .filter(u -> u.contains("/user/u1/time-entries?page-size=200&page=1")).count();
      assertEquals(1, asked, http.requests().toString());
   }

   @Test
   void theRowLimitStopsTheLookupsToo() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/workspaces/w1/user/u1/time-entries", "[{\"id\":\"t1\"}]", "[]")
         .on("/workspaces/w1/user/u2/time-entries", "[{\"id\":\"t2\"}]", "[]")
         .on("/workspaces/w1/user/u3/time-entries", "[{\"id\":\"t3\"}]", "[]")
         .on("/workspaces/w1/users", "[{\"id\":\"u1\"},{\"id\":\"u2\"},{\"id\":\"u3\"}]", "[]")
         .on("/v1/workspaces", "[{\"id\":\"w1\"}]");
      ClockifyQuery query = workspacesUsersTimeEntries();
      query.setMaxRows(1);

      String dump = dump(ReplaySupport.runTable(query, http));

      assertNotNull(dump);
      assertEquals(1, count(dump, "{"), dump);
      long entryRequests = http.requests().stream().filter(u -> u.contains("time-entries")).count();
      assertTrue(entryRequests <= 2, "looked up time entries for rows beyond the limit: " +
                 http.requests());
   }

   private static ClockifyQuery workspacesUsersTimeEntries() {
      ClockifyQuery query = workspacesUsers();
      query.setLookupEndpoint("Time Entries", 1);
      return query;
   }

   private static ClockifyQuery workspacesUsers() {
      ClockifyDataSource dataSource = new ClockifyDataSource();
      dataSource.setApiKey("key");
      ClockifyQuery query = ReplaySupport.query(dataSource, new ClockifyQuery(), "Workspaces", "$",
                                                Map.of());
      query.setLookupEndpoint("Users", 0);
      return query;
   }

   private static int count(String text, String part) {
      int n = 0;

      for(int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) {
         n++;
      }

      return n;
   }

   private static String dump(XTableNode table) {
      if(table == null) {
         return null;
      }

      StringBuilder out = new StringBuilder();

      while(table.next()) {
         out.append('{');

         for(int i = 0; i < table.getColCount(); i++) {
            out.append(table.getName(i)).append('=').append(table.getObject(i)).append(';');
         }

         out.append('}');
      }

      return out.toString();
   }
}
