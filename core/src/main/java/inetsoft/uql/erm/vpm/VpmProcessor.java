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
package inetsoft.uql.erm.vpm;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XQuery;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Catalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.function.BiFunction;

/**
 * Processing of VPM.
 *
 * @version 14.0
 * @author InetSoft Technology Corp
 */
public class VpmProcessor {
   public static VpmProcessor getInstance() {
      VpmProcessor processor = VpmProcessor.processor;

      if(processor == null) {
         try {
            processor = (VpmProcessor)
               Class.forName("inetsoft.enterprise.vpm.VpmProcessor").newInstance();
         }
         catch(Exception ex) {
            processor = new VpmProcessor();
         }

         VpmProcessor.processor = processor;
      }

      return processor;
   }

   /**
    * Check if should use vpm when this is running in designer.
    */
   public static boolean useVpmSecurity() {
      try {
         if(SecurityEngine.getSecurity().getSecurityProvider().isVirtual()) {
            return false;
         }
      }
      catch(Exception ex) {
         LOG.error(ex.getMessage(), ex);
      }

      return true;
   }

   public XQuery applyConditions(XQuery query, VariableTable vars, boolean checkVariable,
                                 Principal user) throws Exception
   {
      return query;
   }

   public String testVPM(IdentityID user, String[] groups, IdentityID[] roles, String orgID,
                         XDataModel dataModel, Catalog catalog,
                         VirtualPrivateModel vpm)
      throws Exception
   {
      return "";
   }

   public UserVariable[] getVPMParameters(XQuery query, Principal user,
                                                 boolean promptOnly)
   {
      return new UserVariable[0];
   }

   public BiFunction<String, String, Boolean> getHiddenColumnsSelector(
      String[] tables, String[] columns, String modelName,
      String partition, VariableTable vars, Principal user) throws Exception
   {
      return (a, b) -> false;
   }

   public XQuery applyHiddenColumns(XQuery query, VariableTable vars,
                                    Principal user) throws Exception
   {
      return query;
   }

   private static VpmProcessor processor;
   private static final Logger LOG = LoggerFactory.getLogger(XUtil.class);
}
