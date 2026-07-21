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

   /**
    * Bug #75669: a VPM trigger script that references {@code condition} inside a loop
    * activated the condition under Rhino regardless of iteration order. Under GraalJS a
    * trailing non-matching {@code if} clobbers the loop completion value with undefined,
    * so the raw script result is null when the matching iteration is not last. The fix
    * tracks that the script referenced {@code condition} so VpmCondition can fall back to
    * the condition value.
    */
   @Test
   void testConditionUsedWhenMatchNotLastIteration() throws Exception {
      VpmScope scope = new VpmScope();
      Principal user = new XPrincipal(new IdentityID("user1", "host-org"));
      scope.setUser(user);
      scope.putMember("condition", "public.orders.discount = 0.05");
      // matching group ("group0") is first; a non-matching group is last, mirroring the
      // transitive-group resolution order [group0, group0_1] from the reported case.
      scope.putMember("groups", new String[]{ "group0", "group0_1" });

      String loop =
         "for(var i=0; i<groups.length; i++){\n" +
         "   if(groups[i]=='group0'){\n" +
         "      condition;\n" +
         "   }\n" +
         "}";

      // The trailing non-matching iteration clobbers the completion value under GraalJS...
      assertNull(VpmScope.execute(loop, scope));
      // ...but the script referenced `condition`, which the fix records so the condition
      // is still applied.
      assertTrue(scope.isConditionUsed());

      // When no group matches, `condition` is never referenced -> flag stays false.
      VpmScope noMatch = new VpmScope();
      noMatch.setUser(user);
      noMatch.putMember("condition", "public.orders.discount = 0.05");
      noMatch.putMember("groups", new String[]{ "groupX", "groupY" });
      assertNull(VpmScope.execute(loop, noMatch));
      assertFalse(noMatch.isConditionUsed());
   }

   /**
    * Bug #75669: the condition-used flag must reflect only the current execution. The
    * setup {@code putMember("condition", ...)} that VpmCondition performs before running
    * the script (and any prior execution) must not leave the flag set.
    */
   @Test
   void testConditionUsedResetPerExecution() throws Exception {
      VpmScope scope = new VpmScope();
      Principal user = new XPrincipal(new IdentityID("user1", "host-org"));
      scope.setUser(user);
      // pre-execution setup assignment (as VpmCondition.evaluate does) must not count
      scope.putMember("condition", "public.orders.discount = 0.05");

      // a script that never touches `condition`
      assertEquals("x", VpmScope.execute("'x';", scope));
      assertFalse(scope.isConditionUsed());
   }

   /**
    * Bug #75669: the referenced-variable tracking is generic (not condition-specific), so
    * a hidden-columns trigger script that references {@code hiddenColumns} inside a loop
    * is detected the same way. Mirrors HiddenColumns.getHiddenColumns()'s fallback.
    */
   @Test
   void testVariableUsedForHiddenColumns() throws Exception {
      VpmScope scope = new VpmScope();
      Principal user = new XPrincipal(new IdentityID("user1", "host-org"));
      scope.setUser(user);
      scope.putMember("hiddenColumns", new String[]{ "public.orders.discount" });
      scope.putMember("groups", new String[]{ "group0", "group0_1" });

      String loop =
         "for(var i=0; i<groups.length; i++){\n" +
         "   if(groups[i]=='group0'){\n" +
         "      hiddenColumns;\n" +
         "   }\n" +
         "}";

      // The trailing non-matching iteration clobbers the completion value under GraalJS...
      assertNull(VpmScope.execute(loop, scope));
      // ...but the script referenced `hiddenColumns`, which the fix records so the hidden
      // columns are still applied.
      assertTrue(scope.isVariableUsed("hiddenColumns"));
      // an unrelated variable is not flagged as used
      assertFalse(scope.isVariableUsed("condition"));

      // When no group matches, `hiddenColumns` is never referenced -> flag stays false.
      VpmScope noMatch = new VpmScope();
      noMatch.setUser(user);
      noMatch.putMember("hiddenColumns", new String[]{ "public.orders.discount" });
      noMatch.putMember("groups", new String[]{ "groupX", "groupY" });
      assertNull(VpmScope.execute(loop, noMatch));
      assertFalse(noMatch.isVariableUsed("hiddenColumns"));
   }
}
