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
package inetsoft.uql.jdbc;

import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.LocalPasswordCredential;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * A data source whose sql is generated with the base SQLHelper, the same sql as a query
 * without a data source. Bug #77434 refuses a query that has a RIGHT or FULL join mixed
 * with an inner join, or a nested join on the right side of an outer join, when it's
 * parsed without a data source, because the sql helper that generates it later is
 * unknown. Tests of such queries parse them with this data source.
 */
final class GenericJDBCDataSource {
   private GenericJDBCDataSource() {
   }

   static JDBCDataSource create() {
      CredentialService credentials = mock(CredentialService.class);
      when(credentials.createCredential(any(), anyBoolean()))
         .thenAnswer(inv -> new LocalPasswordCredential());

      try(MockedStatic<CredentialService> service = mockStatic(CredentialService.class)) {
         service.when(CredentialService::getInstance).thenReturn(credentials);
         JDBCDataSource source = new JDBCDataSource();
         source.setName("generic");
         // an unknown product uses the base SQLHelper, and getting it doesn't connect
         source.setRuntimeProductName("");
         return source;
      }
   }
}
