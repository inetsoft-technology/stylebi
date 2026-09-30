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
package inetsoft.sree.security;

import inetsoft.sree.ClientInfo;
import inetsoft.uql.XPrincipal;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77384: {@link XPrincipal#detachedCopy()} must share no mutable state with the
 * session principal, including state changed in place through returned values, and the
 * copy must stay equal to it.
 */
class PrincipalDetachedCopyTest {
   @Test
   void srPrincipalCopyIsEqualAndShareNoMutableState() {
      ClientInfo client = new ClientInfo(new IdentityID("alice", "orgA"), "10.0.0.1", "s1");
      client.setLoginUserName(new IdentityID("admin", "orgA"));
      DestinationUserNameProviderPrincipal principal = new DestinationUserNameProviderPrincipal(
         client, new IdentityID[] { new IdentityID("Everyone", "orgA") },
         new String[] { "readers" }, "orgA", 12345L, "Alice A");
      principal.setProperty("__internal__", "true");
      principal.setParameter("list", new ArrayList<>(List.of("a")));
      principal.setParameter("map", new HashMap<>(Map.of("k", "v")));
      principal.setParameter("date", new Date(1000L));
      Object session = new Object();
      principal.setSession(session);

      XPrincipal copy = principal.detachedCopy();

      assertNotSame(principal, copy);
      assertInstanceOf(DestinationUserNameProviderPrincipal.class, copy);
      assertEquals(principal, copy);
      assertEquals(copy, principal);
      assertEquals(principal.hashCode(), copy.hashCode());
      // SecurityEngine keys its logged-in users by ClientInfo
      assertEquals(principal.getUser(), ((SRPrincipal) copy).getUser());
      assertEquals(principal.getUser().hashCode(), ((SRPrincipal) copy).getUser().hashCode());
      assertEquals(principal.getAge(), ((SRPrincipal) copy).getAge());
      assertNull(((SRPrincipal) copy).getSession());
      assertEquals("admin", ((SRPrincipal) copy).getUser().getLoginUserID().getName());

      // change the copy in place through the values it returns
      copy.getRoles()[0].setName("Administrator");
      copy.getGroups()[0] = "admins";
      ((List<String>) copy.getParameter("list")).add("b");
      ((Map<String, String>) copy.getParameter("map")).put("k", "x");
      ((Date) copy.getParameter("date")).setTime(2000L);
      ((SRPrincipal) copy).getUser().getLoginUserID().setName("mallory");
      ((SRPrincipal) copy).getUser().getUserIdentity().setOrgID("orgB");
      copy.setParameter("new", "x");

      assertEquals("Everyone", principal.getRoles()[0].getName());
      assertEquals("readers", principal.getGroups()[0]);
      assertEquals(List.of("a"), principal.getParameter("list"));
      assertEquals(Map.of("k", "v"), principal.getParameter("map"));
      assertEquals(1000L, ((Date) principal.getParameter("date")).getTime());
      assertEquals("admin", principal.getUser().getLoginUserID().getName());
      assertEquals("orgA", principal.getUser().getUserIdentity().getOrgID());
      assertNull(principal.getParameter("new"));
      assertEquals(0L, principal.getParameterTS("new"));
      assertSame(session, principal.getSession());
   }

   @Test
   void loginUserFallbackIsPreserved() {
      SRPrincipal principal = new SRPrincipal(
         new ClientInfo(new IdentityID("bob", "orgA"), "10.0.0.2"),
         new IdentityID[0], new String[0], "orgA", 1L);
      SRPrincipal copy = (SRPrincipal) principal.detachedCopy();

      assertEquals(principal, copy);
      // no explicit login user: it still falls back to the (copied) user
      assertSame(copy.getUser().getUserIdentity(), copy.getUser().getLoginUserID());
      assertNotSame(principal.getUser().getUserIdentity(), copy.getUser().getUserIdentity());
   }

   @Test
   void plainXPrincipalCopyShareNoMutableState() {
      XPrincipal principal = new XPrincipal(
         new IdentityID("carol", "orgA"), new IdentityID[] { new IdentityID("r1", "orgA") },
         new String[] { "g1" }, "orgA");
      principal.setProperty("k", "v");
      principal.setParameter("arr", new String[] { "a" });

      XPrincipal copy = principal.detachedCopy();

      assertEquals(XPrincipal.class, copy.getClass());
      assertEquals(principal, copy);
      assertEquals(principal.hashCode(), copy.hashCode());

      copy.getRoles()[0].setName("admin");
      copy.getGroups()[0] = "admins";
      copy.setProperty("k", "x");
      ((String[]) copy.getParameter("arr"))[0] = "z";

      assertEquals("r1", principal.getRoles()[0].getName());
      assertEquals("g1", principal.getGroups()[0]);
      assertEquals("v", principal.getProperty("k"));
      assertEquals("a", ((String[]) principal.getParameter("arr"))[0]);
   }
}
