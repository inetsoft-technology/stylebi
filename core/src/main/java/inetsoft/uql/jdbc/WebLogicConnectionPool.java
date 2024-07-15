/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.jdbc;

import inetsoft.sree.SreeEnv;

import javax.naming.*;
import java.util.Hashtable;

/**
 * Bridge class for WebLogic connection pools.
 *
 * @author  InetSoft Technology
 * @since   6.1
 */
public class WebLogicConnectionPool extends JNDIConnectionPool {
   /**
    * Get the initial JNDI context used to lookup up the datasource.
    *
    * @return the initial JNDI context.
    *
    * @throws NamingException if an error occurs while creating the context.
    */
   @Override
   protected Context getInitialContext() throws NamingException {
      Hashtable env = new Hashtable();
      env.put(Context.INITIAL_CONTEXT_FACTORY,
              "weblogic.jndi.WLInitialContextFactory");
      
      String host = SreeEnv.getProperty("weblogic.cp.host");
      String port = SreeEnv.getProperty("weblogic.cp.port");
      
      env.put(Context.PROVIDER_URL, "t3://" + host + ":" + port);

      return new InitialContext(env);
   }
   
   /**
    * Get the path that should be prepended to the datasource name when looking
    * it up in JNDI.
    *
    * @return the path to the datasource.
    */
   @Override
   protected String getDataSourcePrefix() {
      return SreeEnv.getProperty("jdbc.connection.jndi.prefix");
   }
}
