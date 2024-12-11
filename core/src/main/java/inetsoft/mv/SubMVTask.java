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
package inetsoft.mv;

import inetsoft.mv.data.*;
import inetsoft.mv.fs.BlockFile;
import inetsoft.mv.fs.XBlockSystem;
import inetsoft.mv.mr.AbstractMapTask;
import inetsoft.mv.mr.XMapResult;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.ThreadContext;

import java.security.Principal;
import java.util.Arrays;

/**
 * SubMVTask, the map task to be executed at one data node. It will execute
 * a SubMVQuery to generate data.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public final class SubMVTask extends AbstractMapTask {
   /**
    * Create an instance of SubMVTask.
    */
   public SubMVTask() {
      super();
   }

   /**
    * Get the query.
    */
   public SubMVQuery getQuery() {
      return (SubMVQuery) get("squery");
   }

   /**
    * Set the query.
    */
   public void setQuery(SubMVQuery query) {
      set("squery", query);
   }

   /**
    * Set the key-value pair.
    */
   @Override
   public void setProperty(String key, String val) {
      if("blockIndex".equals(key)) {
         SubMVQuery query = getQuery();

         if(query != null) {
            query = (SubMVQuery) query.clone();
            query.setBlockIndex(Integer.parseInt(val));
            setQuery(query);
         }
      }
      else {
         super.setProperty(key, val);
      }
   }

   /**
    * Run the SubMVTask by providing a block system.
    */
   @Override
   public XMapResult run(XBlockSystem sys) throws Exception {
      String bid = getXBlock();
      String mvOrgId = getMVOrgId(bid);

      return OrganizationManager.runInOrgScope(mvOrgId, () -> {
         try {
            BlockFile file = sys.getFile(bid);

            if(file == null) {
               throw new Exception("The file of the block not found: " + bid);
            }

            SubMV mv = SubMV.get(file);

            if(mv == null) {
               throw new Exception("The sub mv of the block not found: " + bid);
            }

            query = getQuery();
            SubTableBlock data = null;

            try {
               data = query.execute(mv);
            }
            catch(Exception e) {
               if(SUtil.isDefaultVSGloballyVisible()) {
                  Principal oPrincipal = ThreadContext.getContextPrincipal();
                  IdentityID pId = IdentityID.getIdentityIDFromKey(oPrincipal.getName());
                  pId.setOrgID(Organization.getDefaultOrganizationID());
                  XPrincipal tmpPrincipal = new XPrincipal(pId);
                  tmpPrincipal.setOrgId(Organization.getDefaultOrganizationID());
                  ThreadContext.setContextPrincipal(tmpPrincipal);

                  try {
                     data = query.execute(mv);
                  }
                  finally {
                     ThreadContext.setContextPrincipal(oPrincipal);
                  }
               }
            }

            return new SubMVResult(this, data);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      });
   }

   private String getMVOrgId(String bid) {
      String[] paths = bid.split("\\_");
      String path = paths[paths.length-1];

      SecurityProvider securityProvider = SecurityEngine.getSecurity().getSecurityProvider();
      String[] organizationIDs = securityProvider.getOrganizationIDs();

      return Arrays.stream(organizationIDs).filter(id -> path.startsWith(id + "-"))
         .findFirst().get();
   }

   /**
    * Cancel the task.
    */
   @Override
   public void cancel() {
      super.cancel();

      if(query != null) {
         query.cancel();
      }
   }

   @Override
   public String getOrgID() {
      return null;
   }

   @Override
   public void setOrgID(String orgID) {
      //
   }


   private SubMVQuery query;
}
