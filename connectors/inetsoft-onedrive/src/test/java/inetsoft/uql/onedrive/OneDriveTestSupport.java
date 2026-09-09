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
package inetsoft.uql.onedrive;

import inetsoft.uql.util.Config;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.credential.ClientCredentials;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.CredentialType;
import org.springframework.context.ApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared {@code @BeforeAll}/{@code @AfterAll} plumbing for every test in this module that
 * constructs a real {@link OneDriveDataSource} or a real {@link OneDriveQuery} without a full
 * Spring context -- mirrors {@code OneDriveTabularContractTest}'s original {@code installContext}
 * (needed for {@code Config.getResourceBundle}, resolved through {@code LayoutCreator}) and extends
 * it with a mocked {@link CredentialService} bean, needed because {@code TabularDataSource}'s own
 * constructor eagerly calls {@code CredentialService.getInstance().createCredential(...)} --
 * unavoidable the moment ANY test in this module constructs a bare {@code new OneDriveDataSource()}
 * rather than only a bare {@code new OneDriveQuery()} the way the original test did.
 */
final class OneDriveTestSupport {
   private OneDriveTestSupport() {
   }

   static ConfigurationContext installMockContext() {
      ConfigurationContext previous = ConfigurationContext.getContext();
      Config config = mock(Config.class);
      when(config.getResourceBundle(any())).thenReturn(null);

      CredentialService credentialService = mock(CredentialService.class);
      ClientCredentials credentials = mock(ClientCredentials.class);
      when(credentialService.createCredential(eq(CredentialType.CLIENT), anyBoolean()))
         .thenReturn(credentials);

      ApplicationContext context = mock(ApplicationContext.class);
      when(context.getBean(Config.class)).thenReturn(config);
      when(context.getBean(CredentialService.class)).thenReturn(credentialService);
      ConfigurationContext.getContext().setApplicationContext(context);

      return previous;
   }

   static void clearContext(ConfigurationContext previous) {
      if(previous != null) {
         previous.setApplicationContext(null);
      }
   }

   static OneDriveDataSource fakeDataSource(String name) {
      OneDriveDataSource ds = new OneDriveDataSource();
      ds.setName(name);
      return ds;
   }
}
