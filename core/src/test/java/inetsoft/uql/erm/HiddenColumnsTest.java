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
package inetsoft.uql.erm;

import inetsoft.sree.security.IdentityID;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HiddenColumns}.
 *
 * <p>For {@code evaluate()} tests that check role-based access we create an
 * {@link XPrincipal} with the special name {@code "unknown_user"}.
 * {@code XUtil.getUserRoles()} short-circuits for that name and delegates
 * directly to {@code XPrincipal.getRoles()}, which avoids any dependency on
 * the security engine / Spring context.
 */
class HiddenColumnsTest {

   private HiddenColumns hiddenColumns;

   @BeforeEach
   void setUp() {
      hiddenColumns = new HiddenColumns();
   }

   // ---- addRole / containsRole / getRoleCount / deduplication ----

   @Test
   void addRoleStoresRole() {
      hiddenColumns.addRole("analyst");
      assertTrue(hiddenColumns.containsRole(new IdentityID("analyst", null)));
   }

   @Test
   void addRoleDoesNotDuplicate() {
      hiddenColumns.addRole("analyst");
      hiddenColumns.addRole("analyst");

      // Count roles by draining the enumeration
      int count = countRoles(hiddenColumns.getRoles());
      assertEquals(1, count);
   }

   @Test
   void addMultipleDifferentRolesAreAllStored() {
      hiddenColumns.addRole("analyst");
      hiddenColumns.addRole("manager");
      hiddenColumns.addRole("viewer");

      assertEquals(3, countRoles(hiddenColumns.getRoles()));
   }

   // ---- containsRole ----

   @Test
   void containsRoleReturnsTrueForMatchingRole() {
      hiddenColumns.addRole("sales");
      assertTrue(hiddenColumns.containsRole(new IdentityID("sales", null)));
   }

   @Test
   void containsRoleReturnsFalseWhenNotAdded() {
      hiddenColumns.addRole("sales");
      assertFalse(hiddenColumns.containsRole(new IdentityID("manager", null)));
   }

   // ---- removeRole ----

   @Test
   void removeRoleRemovesMatchingRole() {
      hiddenColumns.addRole("analyst");
      hiddenColumns.addRole("manager");

      hiddenColumns.removeRole("analyst");

      assertFalse(hiddenColumns.containsRole(new IdentityID("analyst", null)));
      assertTrue(hiddenColumns.containsRole(new IdentityID("manager", null)));
   }

   @Test
   void removeRoleIsNoopWhenAbsent() {
      hiddenColumns.addRole("analyst");
      hiddenColumns.removeRole("nonexistent");  // Should not throw
      assertEquals(1, countRoles(hiddenColumns.getRoles()));
   }

   // ---- addHiddenColumn / removeHiddenColumn / getHiddenColumnCount ----

   @Test
   void addHiddenColumnIncreasesCount() {
      hiddenColumns.addHiddenColumn(new AttributeRef("salary"));
      assertEquals(1, countHiddenColumns(hiddenColumns.getHiddenColumns()));
   }

   @Test
   void addHiddenColumnDoesNotDuplicate() {
      AttributeRef col = new AttributeRef("salary");
      hiddenColumns.addHiddenColumn(col);
      hiddenColumns.addHiddenColumn(col);

      assertEquals(1, countHiddenColumns(hiddenColumns.getHiddenColumns()));
   }

   @Test
   void removeHiddenColumnRemovesMatchingColumn() {
      AttributeRef salary = new AttributeRef("salary");
      AttributeRef bonus  = new AttributeRef("bonus");
      hiddenColumns.addHiddenColumn(salary);
      hiddenColumns.addHiddenColumn(bonus);

      hiddenColumns.removeHiddenColumn(salary);

      assertEquals(1, countHiddenColumns(hiddenColumns.getHiddenColumns()));
      // Confirm "bonus" is still present
      Enumeration<DataRef> cols = hiddenColumns.getHiddenColumns();
      assertEquals("bonus", cols.nextElement().getAttribute());
   }

   @Test
   void removeHiddenColumnIsNoopWhenAbsent() {
      hiddenColumns.addHiddenColumn(new AttributeRef("salary"));
      hiddenColumns.removeHiddenColumn(new AttributeRef("nonexistent"));
      assertEquals(1, countHiddenColumns(hiddenColumns.getHiddenColumns()));
   }

   // ---- evaluate() — role-based logic ----

   /**
    * When roles list is empty, all users see the hidden columns (no role exception).
    */
   @Test
   void evaluateWithNoRolesReturnsHiddenColumnsForAllUsers() throws Exception {
      hiddenColumns.addHiddenColumn(new AttributeRef("salary"));
      hiddenColumns.addHiddenColumn(new AttributeRef("ssn"));

      // User with no particular role
      XPrincipal user = makeUnknownUser(new IdentityID[0]);

      String[] result = hiddenColumns.evaluate(
         new String[]{"employees"}, new String[]{"salary", "ssn", "name"},
         new VariableTable(), user, false, null);

      assertEquals(2, result.length);
   }

   /**
    * When the user has a matching role, no columns are hidden.
    */
   @Test
   void evaluateWithMatchingRoleReturnsEmptyHiddenColumns() throws Exception {
      hiddenColumns.addRole("analyst");
      hiddenColumns.addHiddenColumn(new AttributeRef("salary"));

      IdentityID analystRole = new IdentityID("analyst", null);
      XPrincipal user = makeUnknownUser(new IdentityID[]{ analystRole });

      String[] result = hiddenColumns.evaluate(
         new String[]{"employees"}, new String[]{"salary", "name"},
         new VariableTable(), user, false, null);

      assertEquals(0, result.length);
   }

   /**
    * When the user does NOT have a matching role, hidden columns are returned.
    */
   @Test
   void evaluateWithoutMatchingRoleReturnsHiddenColumns() throws Exception {
      hiddenColumns.addRole("analyst");
      hiddenColumns.addHiddenColumn(new AttributeRef("salary"));
      hiddenColumns.addHiddenColumn(new AttributeRef("bonus"));

      // User has "viewer" role which is NOT "analyst"
      IdentityID viewerRole = new IdentityID("viewer", null);
      XPrincipal user = makeUnknownUser(new IdentityID[]{ viewerRole });

      String[] result = hiddenColumns.evaluate(
         new String[]{"employees"}, new String[]{"salary", "bonus", "name"},
         new VariableTable(), user, false, null);

      assertEquals(2, result.length);
   }

   /**
    * A null principal (user == null) results in hidden columns being returned.
    */
   @Test
   void evaluateWithNullUserReturnsHiddenColumns() throws Exception {
      hiddenColumns.addHiddenColumn(new AttributeRef("salary"));

      String[] result = hiddenColumns.evaluate(
         new String[]{"employees"}, new String[]{"salary"},
         new VariableTable(), null, false, null);

      assertEquals(1, result.length);
      assertEquals("salary", result[0]);
   }

   // ---- clone() ----

   @Test
   void cloneProducesIndependentRolesList() {
      hiddenColumns.addRole("analyst");

      HiddenColumns cloned = (HiddenColumns) hiddenColumns.clone();
      assertNotNull(cloned);

      // Adding a role to clone should not affect original
      cloned.addRole("manager");

      assertEquals(1, countRoles(hiddenColumns.getRoles()));
      assertEquals(2, countRoles(cloned.getRoles()));
   }

   @Test
   void cloneProducesIndependentHiddenColumnsList() {
      hiddenColumns.addHiddenColumn(new AttributeRef("salary"));

      HiddenColumns cloned = (HiddenColumns) hiddenColumns.clone();
      assertNotNull(cloned);

      cloned.addHiddenColumn(new AttributeRef("bonus"));

      assertEquals(1, countHiddenColumns(hiddenColumns.getHiddenColumns()));
      assertEquals(2, countHiddenColumns(cloned.getHiddenColumns()));
   }

   @Test
   void clonePreservesRolesAndHiddenColumns() {
      hiddenColumns.addRole("analyst");
      hiddenColumns.addHiddenColumn(new AttributeRef("salary"));

      HiddenColumns cloned = (HiddenColumns) hiddenColumns.clone();

      assertTrue(cloned.containsRole(new IdentityID("analyst", null)));
      assertEquals(1, countHiddenColumns(cloned.getHiddenColumns()));
   }

   // ---- helper methods ----

   /**
    * Creates an XPrincipal whose name resolves as "unknown_user" so that
    * XUtil.getUserRoles() returns the embedded roles array without calling
    * out to the security engine.
    */
   private XPrincipal makeUnknownUser(IdentityID[] roles) {
      IdentityID id = new IdentityID("unknown_user", null);
      XPrincipal principal = new XPrincipal(id, roles, new String[0], null);
      return principal;
   }

   private int countRoles(Enumeration<String> e) {
      int count = 0;
      while(e.hasMoreElements()) {
         e.nextElement();
         count++;
      }
      return count;
   }

   private int countHiddenColumns(Enumeration<DataRef> e) {
      int count = 0;
      while(e.hasMoreElements()) {
         e.nextElement();
         count++;
      }
      return count;
   }
}
