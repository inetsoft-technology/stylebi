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
package inetsoft.sree.security.db;

import inetsoft.sree.security.*;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.*;
import java.sql.*;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static inetsoft.sree.security.db.DatabaseCacheTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79352: a database security provider that was torn down (a chain reload or an EM edit
 * replaced it) must not open a new connection pool or connect a new cache client when something
 * still holding it calls it, because nothing closes them again.
 * <p>
 * Real provider and in-memory Derby behind a driver that counts the physical connections that
 * are open.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DatabaseProviderTearDownTest {
   @BeforeEach
   void setUp() throws Exception {
      support = new DatabaseCacheTestSupport();
      support.setUp("3600000");
      open.set(0);
   }

   @AfterEach
   void tearDown() {
      support.tearDown();
   }

   @Test
   void callsAfterTearDownOpenNoPool() throws Exception {
      DatabaseAuthenticationProvider p = counted(support.provider("td79352a", ALL_USERS));
      IdentityID bob = new IdentityID("bob", Organization.getDefaultOrganizationID());

      assertTrue(p.authenticate(bob, new DefaultTicket(bob, "pw-bob")));
      assertTrue(open.get() > 0, "the login opens the pool");

      p.tearDown();
      assertTrue(await(() -> open.get() == 0, 5_000), "tear down closes the pool: " + open);

      // a login that still holds the old instance is refused, it does not revive the pool
      assertFalse(p.authenticate(bob, new DefaultTicket(bob, "pw-bob")));
      assertThrows(SQLException.class, () -> p.getConnectionProvider().getConnection());

      // what the cache loader does after a failed load, and what the setters do
      p.resetConnection();
      assertThrows(SQLException.class, () -> p.getConnectionProvider().getConnection());
      assertEquals(0, p.getUsers().length);

      Thread.sleep(300);
      assertEquals(0, open.get(), "no pool was opened after tear down");
      assertTrue(p.getConnectionProvider().isClosed());
   }

   @Test
   void connectionProviderStaysClosedAfterReset() throws Exception {
      DatabaseAuthenticationProvider p = counted(support.provider("td79352b", ALL_USERS));
      ConnectionProvider connections = p.getConnectionProvider();
      connections.getConnection().close();
      assertFalse(connections.isClosed());

      connections.close();
      connections.resetConnection();
      assertTrue(connections.isClosed(), "reset does not reopen a closed provider");
      assertThrows(SQLException.class, connections::getConnection);
      assertTrue(await(() -> open.get() == 0, 5_000), "open connections: " + open);
   }

   @Test
   void cacheClientIsNotRevivedAfterTearDown() throws Exception {
      String name = "td79352c";
      AuthenticationChain chain = support.initEngine(List.of(support.provider(name, ALL_USERS)));
      DatabaseAuthenticationProvider p = counted(find(chain, name));
      assertTrue(await(() -> p.getUsers().length > 0, 10_000), "the cache never loaded");

      p.tearDown();
      long clients = clusterLong(name, "clientCount");

      // the same calls that revived a cache client (clientCount++) and, through the service
      // that resolves this instance from the chain, a pool
      p.clearCache();
      p.resetConnection();
      assertEquals(0, p.getUsers().length);
      assertEquals(clients, clusterLong(name, "clientCount"),
                   "a torn down provider connected a cache client");
      Thread.sleep(500);
      assertEquals(0, open.get(), "no pool was opened after tear down");
   }

   @Test
   void serviceDoesNotLoadWithTornDownInstanceFoundInChain() throws Exception {
      String name = "td79352d";
      DatabaseAuthenticationProvider p = counted(support.provider(name, ALL_USERS));
      p.tearDown();

      TestService service = new TestService(name);
      service.target = p;

      try {
         service.init();
         invokeLoad(service);
         assertTrue(clusterLong(name, "lastFailureTime") > 0L, "the load is retried later");
         assertEquals(0, open.get(), "a pool was opened for the torn down instance");
      }
      finally {
         service.cancel();
      }
   }

   @Test
   void serviceDoesNotLoadWithTornDownLastBoundInstance() throws Exception {
      String name = "td79352e";
      DatabaseAuthenticationProvider p = counted(support.provider(name, ALL_USERS));
      TestService service = new TestService(name);
      service.target = p;

      try {
         service.init();
         // binds the instance, as the first client does
         service.connect();
         p.tearDown();
         // the chain has no instance now, as while its providers are swapped or after a remove
         service.target = null;
         invokeLoad(service);

         assertTrue(clusterLong(name, "lastFailureTime") > 0L, "the load is retried later");
         assertEquals(0, open.get(), "a pool was opened for the torn down instance");
      }
      finally {
         service.cancel();
      }
   }

   private static void invokeLoad(DatabaseAuthenticationCacheServiceImpl service)
      throws Exception
   {
      Method load = DatabaseAuthenticationCacheServiceImpl.class
         .getDeclaredMethod("loadInternal", boolean.class);
      load.setAccessible(true);
      load.invoke(service, false);
   }

   /**
    * Makes the provider's pool use a driver that counts its open connections.
    */
   private DatabaseAuthenticationProvider counted(DatabaseAuthenticationProvider p) {
      p.setDriverSupplier(c -> new CountingDriver());
      return p;
   }

   private static final class TestService extends DatabaseAuthenticationCacheServiceImpl {
      TestService(String providerName) {
         super(providerName);
      }

      @Override
      DatabaseAuthenticationProvider lookupProvider() {
         DatabaseAuthenticationProvider result = target;

         if(result == null) {
            throw new IllegalStateException("not found");
         }

         return result;
      }

      volatile DatabaseAuthenticationProvider target;
   }

   private static final class CountingDriver implements Driver {
      @Override
      public Connection connect(String url, Properties info) throws SQLException {
         Connection connection = delegate().connect(url, info);

         if(connection == null) {
            return null;
         }

         open.incrementAndGet();
         AtomicInteger closed = new AtomicInteger();
         return (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(), new Class<?>[] { Connection.class },
            (proxy, method, args) -> {
               if("close".equals(method.getName()) && closed.getAndIncrement() == 0) {
                  open.decrementAndGet();
               }

               try {
                  return method.invoke(connection, args);
               }
               catch(InvocationTargetException e) {
                  throw e.getCause();
               }
            });
      }

      private Driver delegate() throws SQLException {
         try {
            return (Driver) Class.forName(DRIVER).getConstructor().newInstance();
         }
         catch(ReflectiveOperationException e) {
            throw new SQLException(e);
         }
      }

      @Override
      public boolean acceptsURL(String url) throws SQLException {
         return delegate().acceptsURL(url);
      }

      @Override
      public DriverPropertyInfo[] getPropertyInfo(String url, Properties info)
         throws SQLException
      {
         return delegate().getPropertyInfo(url, info);
      }

      @Override
      public int getMajorVersion() {
         return 1;
      }

      @Override
      public int getMinorVersion() {
         return 0;
      }

      @Override
      public boolean jdbcCompliant() {
         return false;
      }

      @Override
      public Logger getParentLogger() throws SQLFeatureNotSupportedException {
         throw new SQLFeatureNotSupportedException();
      }
   }

   private DatabaseCacheTestSupport support;
   // the physical connections that are open, over all providers of the test
   private static final AtomicInteger open = new AtomicInteger();
}
