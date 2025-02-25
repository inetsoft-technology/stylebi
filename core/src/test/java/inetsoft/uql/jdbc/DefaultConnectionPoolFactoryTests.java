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
package inetsoft.uql.jdbc;

import com.zaxxer.hikari.HikariConfig;
import inetsoft.test.SreeHome;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.*;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

@SreeHome
@Disabled("Requires driver plugins to be installed, no longer valid")
class DefaultConnectionPoolFactoryTests {
   private final DefaultConnectionPoolFactory factory = new DefaultConnectionPoolFactory();

   @ParameterizedTest(name = "configShouldGetAutoCommitFromDefaults {0}")
   @MethodSource("provideArgsForAutoCommit")
   void configShouldGetAutoCommitFromDefaults(String driver, String url, boolean requiresLogin, boolean autoCommit) {
      withHikariConfig(driver, url, requiresLogin, config -> {
         assertNotNull(config);
         assertEquals(autoCommit, config.isAutoCommit());
      });
   }

   private static Stream<Arguments> provideArgsForAutoCommit() {
      return Stream.of(
         Arguments.of("org.apache.derby.jdbc.EmbeddedDriver", "jdbc:derby:test", true, false),
         Arguments.of("com.dremio.jdbc.Driver", "jdbc:dremio:direct=localhost", true, true),
         Arguments.of("com.simba.googlebigquery.jdbc42.Driver", "jdbc:bigquery://localhost;ProjectId=my-project", false, true),
         Arguments.of("org.apache.drill.jdbc.Driver", "jdbc:drill:zk=maprdemo:5181", true, true)
      );
   }

   @ParameterizedTest(name = "configShouldGetMinimumIdleFromDefaults {0}")
   @MethodSource("provideArgsForMinimumIdle")
   void configShouldGetMinimumIdleFromDefaults(String driver, String url, boolean requiresLogin, int minimumIdle) {
      withHikariConfig(driver, url, requiresLogin, config -> {
         assertNotNull(config);
         assertEquals(minimumIdle, config.getMinimumIdle());
      });
   }

   private static Stream<Arguments> provideArgsForMinimumIdle() {
      return Stream.of(
         Arguments.of("org.apache.derby.jdbc.EmbeddedDriver", "jdbc:derby:test", true, -1),
         Arguments.of("com.simba.googlebigquery.jdbc42.Driver", "jdbc:bigquery://localhost;ProjectId=my-project", false, 1)
      );
   }

   private void withHikariConfig(String driver, String url, boolean requiresLogin, Consumer<HikariConfig> fn) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setDriver(driver);
      ds.setURL(url);
      ds.setRequireLogin(requiresLogin);

      if(requiresLogin) {
         ds.setUser("testuser");
         ds.setPassword("testpassword");
      }

      ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();

      try {
         Class.forName(driver);
      }
      catch(Exception ignore) {
         ClassLoader parentLoader = oldLoader == null ? getClass().getClassLoader() : oldLoader;
         Class<?> driverClass = new ByteBuddy()
            .subclass(DriverStub.class)
            .name(driver)
            .make()
            .load(parentLoader, ClassLoadingStrategy.Default.WRAPPER)
            .getLoaded();
         Thread.currentThread().setContextClassLoader(driverClass.getClassLoader());
      }

      try {
         HikariConfig config = factory.createDataSourceConfig(ds, true);
         fn.accept(config);
      }
      finally {
         Thread.currentThread().setContextClassLoader(oldLoader);
      }
   }

   public static class DriverStub implements Driver {
      @Override
      public Connection connect(String url, Properties info) {
         return mock(Connection.class, withSettings().stubOnly());
      }

      @Override
      public boolean acceptsURL(String url) {
         return true;
      }

      @Override
      public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
         return new DriverPropertyInfo[0];
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
         return true;
      }

      @Override
      public Logger getParentLogger() {
         return null;
      }
   }
}