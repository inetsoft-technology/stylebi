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
package inetsoft.report.composition;

import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.erm.AbstractModelTrapContext;
import inetsoft.uql.erm.DataRef;

import java.security.Principal;
import java.util.HashSet;

/**
 * Model trap context for worksheet.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class WSModelTrapContext extends AbstractModelTrapContext {
   /**
    * Constructor.
    */
   public WSModelTrapContext(TableAssembly table, Principal user) {
      this(table, user, false, false);
   }

   /**
    * Constructor.
    */
   public WSModelTrapContext(TableAssembly table, Principal user,
                             boolean agg, boolean initAgg)
   {
      isWS = true;
      this.agg = agg;
      this.initAgg = initAgg;
      init(table, user, false);
   }

   /**
    * Return TrapInfo as the result after checking trap.
    */
   public TrapInfo checkTrap(TableAssembly otable, TableAssembly table) {
      HashSet<DataRef> all = new HashSet<>();
      HashSet<DataRef> aggs = new HashSet<>();
      getAttributes(table, all, aggs, agg);
      DataRef[] allAttributes = new DataRef[all.size()];
      all.toArray(allAttributes);
      tables = getTables(allAttributes);
      this.aggs = new DataRef[aggs.size()];
      aggs.toArray(this.aggs);

      if(initAgg) {
         initAggregateRefs();
      }

      if(otable != null) {
         helper.setTable(otable);
         all = new HashSet<>();
         aggs = new HashSet<>();
         getAttributes(otable, all, aggs, agg);
         allAttributes = new DataRef[all.size()];
         all.toArray(allAttributes);
         otables = getTables(allAttributes);
         oaggs  = new DataRef[aggs.size()];
         aggs.toArray(oaggs);
         helper.setTable(table);
      }

      return super.checkTrap();
   }

   private boolean agg;
}
