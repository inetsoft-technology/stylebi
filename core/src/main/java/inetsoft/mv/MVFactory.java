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
package inetsoft.mv;

import inetsoft.sree.internal.cluster.MessageListener;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.TableAssembly;

/**
 * Service interface for classes that provide instances of MV creators and executors.
 */
public interface MVFactory {
   /**
    * Creates a new <tt>MVCreator</tt> instance.
    *
    * @param def the view definition.
    *
    * @return a new creator.
    */
   MVCreator newCreator(MVDef def);

   /**
    * Creates a new <tt>MVExecutor</tt> instance.
    *
    * @param table  the bound table assembly.
    * @param mvName the name of the materialized view.
    * @param vars   the query parameters.
    * @param user   a principal that identifies the user that is executing the query.
    *
    * @return a new executor.
    */
   MVExecutor newExecutor(TableAssembly table, String mvName, VariableTable vars,
                          XPrincipal user);

   /**
    * Create a new execution session.
    */
   MVSession newSession();

   /**
    * Creates a new message handler.
    */
   MessageListener newMessageHandler();
}
