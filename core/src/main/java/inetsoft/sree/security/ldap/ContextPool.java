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

package inetsoft.sree.security.ldap;

import inetsoft.util.ObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.AuthenticationException;
import javax.naming.ldap.LdapContext;

final class ContextPool extends ObjectPool<LdapContext> {
   private final LdapAuthenticationProvider provider;

   ContextPool(LdapAuthenticationProvider provider) throws Exception {
      super(0, 4, 4, 0L);
      this.provider = provider;
   }

   @Override
   protected LdapContext create() throws Exception {
      LdapContext context;

      try {
         context = provider.createContext();
      }
      catch(AuthenticationException exc) {
         LOG.error(
            "Failed to authenticate with the LDAP server using the " +
            "provided administrator credentials. The credentials may have " +
            "been changed on the server. If this is the case, you may log " +
            "into the Enterprise Manager using the distinguished name " +
            "(DN) of the administrator as the user name and the new " +
            "password as the password. This will update the LDAP " +
            "credentials. You may then log into the Enterprise Manager as " +
            "normal.");
         throw exc;
      }

      provider.initInstance(context);
      return context;
   }

   @Override
   protected void destroy(LdapContext object) {
      try {
         object.close();
      }
      catch(Throwable exc) {
         LOG.warn("Failed to close directory context", exc);
      }
   }

   @Override
   protected boolean validate(LdapContext object) {
      return provider.testContext(object);
   }

   private static final Logger LOG = LoggerFactory.getLogger(ContextPool.class);
}
