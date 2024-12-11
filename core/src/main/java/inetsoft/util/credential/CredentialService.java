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

package inetsoft.util.credential;

import inetsoft.util.SingletonManager;

import java.util.*;

@SingletonManager.Singleton(CredentialService.Reference.class)
public class CredentialService {
   private CredentialService() {
      for(CredentialFactory factory : ServiceLoader.load(CredentialFactory.class)) {
         factories.put(factory.getType(), factory);
      }
   }

   public static synchronized CredentialService getInstance() {
      return SingletonManager.getInstance(CredentialService.class);
   }

   public static Credential newCredential(CredentialType type) {
      return newCredential(type, false);
   }

   public static Credential newCredential(CredentialType type, boolean forceLocal) {
      return CredentialService.getInstance().createCredential(type, forceLocal);
   }

   private Credential createCredential(CredentialType type) {
      return createCredential(type, false);
   }

   private Credential createCredential(CredentialType type, boolean forceLocal) {
      return factories.get(type).createCredential(forceLocal);
   }

   public static final class Reference extends SingletonManager.Reference<CredentialService> {
      @Override
      public synchronized CredentialService get(Object ... parameters) {
         if(service == null) {
            service = new CredentialService();
         }

         return service;
      }

      @Override
      public synchronized void dispose() {
         if(service != null) {
            service = null;
         }
      }

      private CredentialService service;
   }

   private Map<CredentialType, CredentialFactory> factories = new HashMap<>();
}
