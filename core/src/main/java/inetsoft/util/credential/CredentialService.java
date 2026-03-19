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

import inetsoft.util.ConfigurationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Lazy
public class CredentialService {
   CredentialService() {
      for(CredentialFactory factory : ServiceLoader.load(CredentialFactory.class)) {
         factories.put(factory.getType(), factory);
      }
   }

   public static synchronized CredentialService getInstance() {
      return ConfigurationContext.getContext().getSpringBean(CredentialService.class);
   }

   public Credential createCredential(CredentialType type) {
      return createCredential(type, false);
   }

   public Credential createCredential(CredentialType type, boolean forceLocal) {
      return factories.get(type).createCredential(forceLocal);
   }

   private Map<CredentialType, CredentialFactory> factories = new HashMap<>();
}
