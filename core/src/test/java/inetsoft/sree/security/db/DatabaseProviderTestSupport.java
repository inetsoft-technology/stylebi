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
package inetsoft.sree.security.db;

import inetsoft.sree.security.IdentityID;

import java.lang.reflect.Field;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test support for code outside this package that needs a {@link DatabaseAuthenticationProvider}
 * whose users query runs against an in-memory table instead of a database.
 */
public final class DatabaseProviderTestSupport {
   private DatabaseProviderTestSupport() {
   }

   /**
    * Replaces the provider's users query with one over <tt>passwords</tt> (stored user id to
    * plain text password) that binds the name the provider passes, as a database with the given
    * collation would.
    *
    * @param provider                 the provider.
    * @param passwords                the stored users and their passwords.
    * @param caseInsensitiveCollation <tt>true</tt> if the bound name matches ignoring case.
    *
    * @return the names the users query is run for, in order.
    */
   public static List<IdentityID> installUsersQuery(DatabaseAuthenticationProvider provider,
                                                    Map<IdentityID, String> passwords,
                                                    boolean caseInsensitiveCollation)
      throws Exception
   {
      List<IdentityID> queried = new ArrayList<>();
      AuthenticationDAO dao = mock(AuthenticationDAO.class);
      when(dao.getUserCredential(any())).thenAnswer(inv -> {
         IdentityID name = inv.getArgument(0);
         queried.add(name);
         return passwords.entrySet().stream()
            .filter(e -> caseInsensitiveCollation ?
               e.getKey().equalsIgnoreCase(name) : e.getKey().equals(name))
            .findFirst()
            .map(e -> new UserCredential(e.getValue(), null));
      });

      provider.setUserQuery("SELECT PW FROM U WHERE ORG=? AND NAME=?");
      provider.setHashAlgorithm("None");
      Field field = DatabaseAuthenticationProvider.class.getDeclaredField("dao");
      field.setAccessible(true);
      field.set(provider, dao);
      return queried;
   }
}
