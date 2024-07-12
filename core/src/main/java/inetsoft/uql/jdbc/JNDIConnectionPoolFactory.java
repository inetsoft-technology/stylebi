/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.jdbc;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.XPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.*;
import javax.sql.DataSource;
import java.security.Principal;
import java.util.*;
import java.util.function.Predicate;

/**
 * Implementation of <tt>ConnectionPoolFactory</tt> that obtains the connection pool data
 * sources from JNDI.
 */
public class JNDIConnectionPoolFactory extends DefaultConnectionPoolFactory {
   /**
    * Creates a new instance of <tt>JNDIConnectionPoolFactory</tt> using the
    * {@link Type#TOMCAT} type.
    */
   public JNDIConnectionPoolFactory() {
      this(Type.TOMCAT);
   }

   /**
    * Creates a new instance of <tt>JNDIConnectionPoolFactory</tt>.
    *
    * @param type the JNDI host type.
    */
   public JNDIConnectionPoolFactory(Type type) {
      this.type = type;
   }

   @Override
   public DataSource getConnectionPool(JDBCDataSource jdbcDataSource, Principal user) {
      DataSource dataSource = null;

      synchronized(dataSources) {
         if(dataSources.containsKey(jdbcDataSource.getFullName())) {
            dataSource = dataSources.get(jdbcDataSource.getFullName());
         }
         else {
            Exception failedJNDIException = null;

            try {
               Context context = getInitialContext();
               dataSource = (DataSource)
                  context.lookup(getDataSourcePrefix() + jdbcDataSource.getFullName());
               dataSources.put(jdbcDataSource.getFullName(), dataSource);
            }
            catch(NamingException e) {
               // fix customer bug: bug1286828514751
               if(user == null || ((XPrincipal) user).getProperty("__test__") == null) {
                  failedJNDIException = e;
               }
            }

            if(dataSource == null) {
               LOG.warn(String.format(
                  "Using default connection pool.  Unable to find JNDI datasource %s%s",
                  getDataSourcePrefix(), jdbcDataSource.getFullName()), failedJNDIException);
            }
         }
      }

      if(dataSource == null) {
         dataSource = super.getConnectionPool(jdbcDataSource, user);
      }

      return dataSource;
   }

   @Override
   public void closeConnectionPool(DataSource dataSource) {
      boolean found = false;

      for(Iterator<DataSource> i = dataSources.values().iterator(); i.hasNext();) {
         DataSource ds = i.next();

         if(ds == dataSource) {
            i.remove();
            found = true;
            break;
         }
      }

      if(!found) {
         super.closeConnectionPool(dataSource);
      }
   }

   @Override
   public void closeConnectionPools(Predicate<DataSource> filter) {
      dataSources.values().removeIf(filter);
      super.closeConnectionPools(filter);
   }

   @Override
   public void closeAllConnectionsPools() {
      dataSources.clear();
      super.closeAllConnectionsPools();
   }

   /**
    * Gets the type of JNDI host.
    *
    * @return the JNDI type.
    */
   public Type getType() {
      return type;
   }

   /**
    * Sets the type of JNDI host.
    *
    * @param type the JNDI type.
    */
   public void setType(Type type) {
      this.type = type;
   }

   private Context getInitialContext() throws NamingException {
      Context context;
      Hashtable<String, Object> env;

      switch(type) {
      case WEBLOGIC:
         env = new Hashtable<>();
         env.put(Context.INITIAL_CONTEXT_FACTORY,
                 "weblogic.jndi.WLInitialContextFactory");

         String host = SreeEnv.getProperty("weblogic.cp.host");
         String port = SreeEnv.getProperty("weblogic.cp.port");

         env.put(Context.PROVIDER_URL, "t3://" + host + ":" + port);

         context = new InitialContext(env);
         break;

      case WEBSPHERE:
         env = new Hashtable<>();
         env.put(Context.INITIAL_CONTEXT_FACTORY,
                 "com.ibm.websphere.naming.WsnInitialContextFactory");

         context = new InitialContext(env);
         break;

      case TOMCAT:
      default:
         context = (Context) new InitialContext().lookup("java:/comp/env");
      }

      return context;
   }

   private String getDataSourcePrefix() {
      return SreeEnv.getProperty("jdbc.connection.jndi.prefix");
   }

   private Type type = Type.TOMCAT;
   private final Map<String, DataSource> dataSources = new HashMap<>();
   private static final Logger LOG =
      LoggerFactory.getLogger(JNDIConnectionPoolFactory.class);

   public enum Type {
      TOMCAT, WEBLOGIC, WEBSPHERE
   }
}
