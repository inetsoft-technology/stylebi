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
package inetsoft.report;

import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.security.IdentityID;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.XEngine;
import inetsoft.uql.util.XSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #76760: {@code XSessionManager.removeQueryCacheData} used to gate the actual
 * cache-clear call with {@code service instanceof XEngine}. Spring's {@code @Lazy}
 * injection point for {@code xSessionManager}'s {@code dataService} parameter builds a JDK
 * dynamic proxy whose interface list is fixed at the injection point's declared type
 * ({@code XDataService}, pre-fix) -- such a proxy can never pass {@code instanceof XEngine}
 * (a concrete class), so the cache-clear silently never ran in that (the default,
 * every-deployment) wiring shape.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class XSessionManagerTest {
   @Test
   void removeQueryCacheDataInvokesCacheClearThroughLazyProxy() throws Exception {
      XEngine delegate = mock(XEngine.class);
      XRepository proxy = (XRepository) Proxy.newProxyInstance(
         XRepository.class.getClassLoader(),
         new Class<?>[]{ XRepository.class },
         (p, method, args) -> method.invoke(delegate, args));

      XSessionManager manager = new XSessionManager(
         proxy, mock(XSessionService.class), mock(DataSourceRegistry.class));
      VariableTable vars = new VariableTable();
      JDBCQuery query = new JDBCQuery();
      XPrincipal user = new XPrincipal(new IdentityID("someUser", null));

      manager.removeQueryCacheData(query, vars, user, XNodeTableLens.class);

      verify(delegate).removeQueryCache(any(), eq(query), eq(vars), eq(user), eq(XNodeTableLens.class));
   }

   /**
    * After {@code tearDown()} nulls {@code service} (e.g. the manager is being disposed),
    * {@code removeQueryCacheData} must still no-op safely instead of throwing -- the
    * {@code service != null} guard that replaced the old {@code instanceof XEngine} check
    * is deliberate null-safety, not a residual bug.
    */
   @Test
   void removeQueryCacheDataNoOpsSafelyWhenServiceIsNull() throws Exception {
      XSessionManager manager = new XSessionManager(
         null, mock(XSessionService.class), mock(DataSourceRegistry.class));
      VariableTable vars = new VariableTable();
      JDBCQuery query = new JDBCQuery();
      XPrincipal user = new XPrincipal(new IdentityID("someUser", null));

      assertDoesNotThrow(() -> manager.removeQueryCacheData(query, vars, user, XNodeTableLens.class));
   }
}
