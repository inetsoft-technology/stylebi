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

import inetsoft.sree.SreeEnv;
import inetsoft.util.Tool;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.security.Principal;
import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

/**
 * Implementation of connection pool factory that wraps legacy
 * {@link ConnectionPool} implementations.
 *
 * @since 12.2
 */
@SuppressWarnings("deprecation")
class LegacyConnectionPoolFactory implements ConnectionPoolFactory {
   /**
    * Creates a new instance of <tt>LegacyConnectionPoolFactory</tt>.
    *
    * @throws Exception if the legacy connection pool could not be instantiated.
    */
   public LegacyConnectionPoolFactory() throws Exception {
      String prop = SreeEnv.getProperty("jdbc.connection.pool");
      pool = (ConnectionPool) Class.forName(
         Tool.convertUserClassName(prop)).newInstance();
   }

   @Override
   public DataSource getConnectionPool(JDBCDataSource jdbcDataSource,
                                       Principal user)
   {
      return new LegacyDataSource(jdbcDataSource, user);
   }

   @Override
   public void closeConnectionPool(DataSource dataSource) {
      // NO-OP
   }

   @Override
   public void closeConnectionPools(Predicate<DataSource> filter) {
      // NO-OP
   }

   @Override
   public void closeAllConnectionsPools() {
      // NO-OP
   }

   @Override
   public void close() throws Exception {
      closeAllConnectionsPools();
   }

   @Override
   public boolean isDriverUsed(DataSource dataSource, String driverClassName) {
      return false;
   }

   private final ConnectionPool pool;

   /**
    * Implementation of <tt>DataSource</tt> that acts as an adapter for a legacy
    * connection pool implementation.
    */
   private final class LegacyDataSource implements DataSource {
      /**
       * Creates a new instance of <tt>LegacyDataSource</tt>.
       *
       * @param jdbcDataSource the JDBC data source.
       * @param principal      a principal that identifies the user for whom the
       *                       connection is being requested.
       */
      public LegacyDataSource(JDBCDataSource jdbcDataSource,
                              Principal principal)
      {
         this.jdbcDataSource = jdbcDataSource;
         this.principal = principal;
      }

      @Override
      public Connection getConnection() throws SQLException {
         Connection connection = pool.getConnection(jdbcDataSource, principal);
         return new LegacyConnectionWrapper(connection, jdbcDataSource);
      }

      @Override
      public Connection getConnection(String username, String password)
         throws SQLException
      {
         JDBCDataSource temp = (JDBCDataSource) jdbcDataSource.clone();
         temp.setUser(username);
         temp.setPassword(password);
         Connection connection = pool.getConnection(temp, principal);
         return new LegacyConnectionWrapper(connection, temp);
      }

      @Override
      public PrintWriter getLogWriter() throws SQLException {
         return logWriter;
      }

      @Override
      public void setLogWriter(PrintWriter out) throws SQLException {
         this.logWriter = out;
      }

      @Override
      public void setLoginTimeout(int seconds) throws SQLException {
      }

      @Override
      public int getLoginTimeout() throws SQLException {
         return 0;
      }

      @SuppressWarnings("unchecked")
      @Override
      public <T> T unwrap(Class<T> iface) throws SQLException {
         if(isWrapperFor(iface)) {
            return (T) this;
         }

         throw new SQLException("Does not wrap " + iface);
      }

      @Override
      public boolean isWrapperFor(Class<?> iface) throws SQLException {
         return (iface != null && iface.isAssignableFrom(this.getClass()));
      }

      @Override
      public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
         throw new SQLFeatureNotSupportedException();
      }

      private final JDBCDataSource jdbcDataSource;
      private final Principal principal;

      private PrintWriter logWriter;
   }

   /**
    * Wrapper for a connection that releases the underlying connection when it
    * is closed.
    */
   private final class LegacyConnectionWrapper implements Connection {
      /**
       * Creates a new instance of <tt>LegacyConnectionWrapper</tt>.
       *
       * @param connection     the wrapped connection.
       * @param jdbcDataSource the JDBC data source.
       */
      public LegacyConnectionWrapper(Connection connection,
                                     JDBCDataSource jdbcDataSource)
      {
         this.connection = connection;
         this.jdbcDataSource = jdbcDataSource;
      }

      @Override
      public void close() throws SQLException {
         pool.releaseConnection(jdbcDataSource, connection);
      }

      @Override
      public <T> T unwrap(Class<T> iface) throws SQLException {
         return null;
      }

      @Override
      public boolean isWrapperFor(Class<?> iface) throws SQLException {
         return false;
      }

      @Override
      public Statement createStatement() throws SQLException {
         return connection.createStatement();
      }

      @Override
      public PreparedStatement prepareStatement(String sql) throws SQLException
      {
         return connection.prepareStatement(sql);
      }

      @Override
      public CallableStatement prepareCall(String sql) throws SQLException {
         return connection.prepareCall(sql);
      }

      @Override
      public String nativeSQL(String sql) throws SQLException {
         return connection.nativeSQL(sql);
      }

      @Override
      public void setAutoCommit(boolean autoCommit) throws SQLException {
         connection.setAutoCommit(autoCommit);
      }

      @Override
      public boolean getAutoCommit() throws SQLException {
         return connection.getAutoCommit();
      }

      @Override
      public void commit() throws SQLException {
         connection.commit();
      }

      @Override
      public void rollback() throws SQLException {
         connection.rollback();
      }

      @Override
      public boolean isClosed() throws SQLException {
         return connection.isClosed();
      }

      @Override
      public DatabaseMetaData getMetaData() throws SQLException {
         return connection.getMetaData();
      }

      @Override
      public void setReadOnly(boolean readOnly) throws SQLException {
         connection.setReadOnly(readOnly);
      }

      @Override
      public boolean isReadOnly() throws SQLException {
         return connection.isReadOnly();
      }

      @Override
      public void setCatalog(String catalog) throws SQLException {
         connection.setCatalog(catalog);
      }

      @Override
      public String getCatalog() throws SQLException {
         return connection.getCatalog();
      }

      @Override
      public void setTransactionIsolation(int level) throws SQLException {
         connection.setTransactionIsolation(level);
      }

      @Override
      public int getTransactionIsolation() throws SQLException {
         return connection.getTransactionIsolation();
      }

      @Override
      public SQLWarning getWarnings() throws SQLException {
         return connection.getWarnings();
      }

      @Override
      public void clearWarnings() throws SQLException {
         connection.clearWarnings();
      }

      @Override
      public Statement createStatement(int resultSetType,
                                       int resultSetConcurrency)
         throws SQLException
      {
         return connection.createStatement(resultSetType, resultSetConcurrency);
      }

      @Override
      public PreparedStatement prepareStatement(String sql, int resultSetType,
                                                int resultSetConcurrency)
         throws SQLException
      {
         return connection.prepareStatement(
            sql, resultSetType, resultSetConcurrency);
      }

      @Override
      public CallableStatement prepareCall(String sql, int resultSetType,
                                           int resultSetConcurrency)
         throws SQLException
      {
         return connection.prepareCall(
            sql, resultSetType, resultSetConcurrency);
      }

      @Override
      public Map<String, Class<?>> getTypeMap() throws SQLException {
         return connection.getTypeMap();
      }

      @Override
      public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
         connection.setTypeMap(map);
      }

      @Override
      public void setHoldability(int holdability) throws SQLException {
         connection.setHoldability(holdability);
      }

      @Override
      public int getHoldability() throws SQLException {
         return connection.getHoldability();
      }

      @Override
      public Savepoint setSavepoint() throws SQLException {
         return connection.setSavepoint();
      }

      @Override
      public Savepoint setSavepoint(String name) throws SQLException {
         return connection.setSavepoint(name);
      }

      @Override
      public void rollback(Savepoint savepoint) throws SQLException {
         connection.rollback(savepoint);
      }

      @Override
      public void releaseSavepoint(Savepoint savepoint) throws SQLException {
         connection.releaseSavepoint(savepoint);
      }

      @Override
      public Statement createStatement(int resultSetType,
                                       int resultSetConcurrency,
                                       int resultSetHoldability)
         throws SQLException
      {
         return connection.createStatement(
            resultSetType, resultSetConcurrency, resultSetHoldability);
      }

      @Override
      public PreparedStatement prepareStatement(String sql, int resultSetType,
                                                int resultSetConcurrency,
                                                int resultSetHoldability)
         throws SQLException
      {
         return connection.prepareStatement(
            sql, resultSetType, resultSetConcurrency, resultSetHoldability);
      }

      @Override
      public CallableStatement prepareCall(String sql, int resultSetType,
                                           int resultSetConcurrency,
                                           int resultSetHoldability)
         throws SQLException
      {
         return connection.prepareCall(
            sql, resultSetType, resultSetConcurrency, resultSetHoldability);
      }

      @Override
      public PreparedStatement prepareStatement(String sql,
                                                int autoGeneratedKeys)
         throws SQLException
      {
         return connection.prepareStatement(sql, autoGeneratedKeys);
      }

      @Override
      public PreparedStatement prepareStatement(String sql, int[] columnIndexes)
         throws SQLException
      {
         return connection.prepareStatement(sql, columnIndexes);
      }

      @Override
      public PreparedStatement prepareStatement(String sql,
                                                String[] columnNames)
         throws SQLException
      {
         return connection.prepareStatement(sql, columnNames);
      }

      @Override
      public Clob createClob() throws SQLException {
         return connection.createClob();
      }

      @Override
      public Blob createBlob() throws SQLException {
         return connection.createBlob();
      }

      @Override
      public NClob createNClob() throws SQLException {
         return connection.createNClob();
      }

      @Override
      public SQLXML createSQLXML() throws SQLException {
         return connection.createSQLXML();
      }

      @Override
      public boolean isValid(int timeout) throws SQLException {
         return connection.isValid(timeout);
      }

      @Override
      public void setClientInfo(String name, String value)
         throws SQLClientInfoException
      {
         connection.setClientInfo(name, value);
      }

      @Override
      public void setClientInfo(Properties properties)
         throws SQLClientInfoException
      {
         connection.setClientInfo(properties);
      }

      @Override
      public String getClientInfo(String name) throws SQLException {
         return connection.getClientInfo(name);
      }

      @Override
      public Properties getClientInfo() throws SQLException {
         return connection.getClientInfo();
      }

      @Override
      public Array createArrayOf(String typeName, Object[] elements)
         throws SQLException
      {
         return connection.createArrayOf(typeName, elements);
      }

      @Override
      public Struct createStruct(String typeName, Object[] attributes)
         throws SQLException
      {
         return connection.createStruct(typeName, attributes);
      }

      @Override
      public int getNetworkTimeout() throws SQLException {
         return connection.getNetworkTimeout();
      }

      @Override
      public void setNetworkTimeout(Executor ex, int timeout) 
          throws SQLException
      {
         connection.setNetworkTimeout(ex, timeout);
      }

      @Override
      public void abort(Executor ex) throws SQLException {
         connection.abort(ex);
      }

      @Override
      public String getSchema() throws SQLException {
         return connection.getSchema();
      }

      @Override
      public void setSchema(String schema) throws SQLException {
         connection.setSchema(schema);
      }

      private final Connection connection;
      private final JDBCDataSource jdbcDataSource;
   }
}
