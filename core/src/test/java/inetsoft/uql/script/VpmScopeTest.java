/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.uql.script;

import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.XUtil;
import inetsoft.util.script.graal.ScriptScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VpmScopeTest {

   private VpmScope vpmScope;
   private ScriptScope parentScopeMock;

   @BeforeEach
   void setUp() {
      parentScopeMock = mock(ScriptScope.class);
      vpmScope = new VpmScope();
      vpmScope.setParentScope(parentScopeMock);
   }

   @Test
   void testConstructor() {
      assertEquals(parentScopeMock, vpmScope.getParentScope());
   }

   @Test
   void testGetClassName() {
      assertEquals("VpmScope", vpmScope.getClassName());
   }

   @Test
   public void testSetAndGetUser() {
      assertNull(vpmScope.getMember("user"));

      Principal testUser = new XPrincipal(new IdentityID("testUser", "testOrg"));
      vpmScope.setUser(testUser);

      // Verify getUser returns the same user
      Principal retrievedUser = vpmScope.getUser();
      assertNotNull(retrievedUser);
      assertEquals("testUser", XUtil.getUserName(retrievedUser));

      // Verify get user property
      Object userProperty = vpmScope.getMember("user");
      assertEquals("testUser", userProperty);

      // Verify roles and groups are set correctly
      assertNotNull(vpmScope.getMember("roles"));
      assertNotNull(vpmScope.getMember("groups"));
   }

   @Test
   void testSetAndGetVariableTable() {
      VariableTable mockTable = mock(VariableTable.class);
      vpmScope.setVariableTable(mockTable);
      assertEquals(mockTable, vpmScope.getVariableTable());
   }

   @Test
   void testHasProperty() {
      assertTrue(vpmScope.hasMember("user"));
      assertFalse(vpmScope.hasMember("nonexistent"));
   }

   @Test
   void testRunQuery() {
      String queryName = "testQuery";
      Object queryParams = new Object();
      Object expectedResult = new Object();
      Principal mockUser = mock(Principal.class);

      try(MockedStatic<XUtil> xUtilMock = mockStatic(XUtil.class)) {
         xUtilMock.when(() -> XUtil.runQuery(queryName, queryParams, mockUser, null))
            .thenReturn(expectedResult);

         VpmScope spyScope = spy(vpmScope);
         doReturn(mockUser).when(spyScope).getUser();

         Object result = spyScope.runQuery(queryName, queryParams);

         xUtilMock.verify(() -> XUtil.runQuery(queryName, queryParams, mockUser, null));
         verify(spyScope).getUser();

         assertEquals(expectedResult, result);
      }
   }

   @Test
   void testExecute() throws Exception {
      VpmScope scope = new VpmScope();
      Principal guest = new XPrincipal(new IdentityID("guest", "host-org"));
      scope.setUser(guest);

      //condition statement
      String statement = "if (user == 'guest') {\n" +
         "  condition = 'SA.CONTACTS.CONTACT_ID>20';\n" +
         "}\n" +
         "condition;";
      assertEquals("SA.CONTACTS.CONTACT_ID>20", VpmScope.execute(statement, scope));

      //hiddenColumns statement
      statement = "if (user == 'guest') {\n" +
         "  hiddenColumns  = ['SA.CONTACTS.FIRST_NAME'];\n" +
         "}\n" +
         "hiddenColumns ;";
      assertEquals("[SA.CONTACTS.FIRST_NAME]",
                   Arrays.toString((Object[]) VpmScope.execute(statement, scope)));

      //lookup statement
      statement = "if(user == 'guest') {\n" +
         "   false;\n" +
         "} else {\n" +
         "  true;\n" +
         "}\n";
      assertEquals(false, VpmScope.execute(statement, scope));

      //invalid statement
      statement = "if(user == 'guest') {";
      assertThrows(Exception.class, () -> VpmScope.execute("if(user == 'guest') {", scope),
                   "Expected exception for invalid statement");
   }
}
