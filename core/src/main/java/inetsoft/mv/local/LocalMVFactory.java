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
package inetsoft.mv.local;

import com.google.auto.service.AutoService;
import inetsoft.mv.*;
import inetsoft.mv.fs.internal.FSMessageHandler;
import inetsoft.sree.internal.cluster.MessageListener;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.TableAssembly;

/**
 * Implementation of <tt>MVFactory</tt> for the embedded MV engine.
 */
@AutoService(MVFactory.class)
public class LocalMVFactory implements MVFactory {
   @Override
   public MVCreator newCreator(MVDef def) {
      return new LocalMVCreator(def);
   }

   @Override
   public MVExecutor newExecutor(TableAssembly table, String mvName, VariableTable vars,
                                 XPrincipal user)
   {
      return new LocalMVExecutor(table, mvName, vars, user);
   }

   @Override
   public MVSession newSession() {
      return new MVSession();
   }

   @Override
   public MessageListener newMessageHandler() {
      return new FSMessageHandler();
   }
}
