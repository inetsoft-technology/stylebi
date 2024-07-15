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

import javax.naming.*;
import java.util.Hashtable;

/**
 * Bridge class for WebSphere connection pools.
 *
 * @author  InetSoft Technology
 * @since   6.1
 */
public class WebSphereConnectionPool extends JNDIConnectionPool {
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
              "com.ibm.websphere.naming.WsnInitialContextFactory");

      Context initCtx = new InitialContext(env);
      return initCtx;
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
