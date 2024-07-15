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
package inetsoft.sree.internal;

import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.internal.*;
import inetsoft.sree.*;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.*;

/**
 * This is the implementation of the AnalyticRepository to provide analytic
 * functionalities. It is the backend of adhoc report.
 *
 * @version 7.0
 * @author InetSoft Technology Corp
 */
public class AnalyticEngine extends RepletEngine implements AnalyticRepository {
   /**
    * Create a default local analytic engine.
    */
   public AnalyticEngine() {
      super();
   }

   /**
    * Create a local analytic engine.
    * @param id the unique engine ID.
    */
   public AnalyticEngine(String id) {
      super(id);
   }

   /**
    * Create a local analytic engine and evaluate against a given license key.
    * @param id the unique engine id.
    * @param licenseKey the license key for the slave.
    */
   public AnalyticEngine(String id, String licenseKey) {
      super(id, licenseKey);
   }

   /**
    * Initialize the engine. This method must be called once before the
    * engine is used.
    */
   @Override
   public void init() {
      try {
         aregistry = new AnalyticRegistry();
      }
      catch(Exception ex) {
         LOG.error("Failed to initialize analytic engine", ex);
      }

      initStorageRefreshListener();
      super.init();
   }

   /**
    * Get replet registry.
    * @param name the specified report name
    * @return replet registry contains the specified report
    */
   @Override
   public RepletRegistry getRegistry(String name, Principal principal) throws Exception {
      return SUtil.isAnalyticReport(name) ? aregistry : super.getRegistry(name, principal);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean checkPermission(Principal principal, ResourceType type, String resource,
                                  ResourceAction action) {
      // for an analytic report(created but not yet saved), only read is allowed
      if(SUtil.isAnalyticReport(resource)) {
         return action == ResourceAction.READ;
      }

      return super.checkPermission(principal, type, resource, action);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean checkPermission(Principal principal, ResourceType type, IdentityID identityID,
                                  ResourceAction action) {
      // for an analytic report(created but not yet saved), only read is allowed
      if(SUtil.isAnalyticReport(identityID.name)) {
         return action == ResourceAction.READ;
      }

      return super.checkPermission(principal, type, identityID, action);
   }

   /**
    * Get a logical model.
    * @param name the specified name, and its format is
    * logicalmodel_name::datasource_name.
    * @param principal the specified principal.
    */
   @Override
   public XLogicalModel getLogicalModel(String name, Principal principal) {
      try {
         String[] arr = Tool.split(name, "::", false);

         if(arr.length != 2) {
            LOG.error("Failed to get logical model, invalid name format (should be " +
                         "model_name::datasource_name): {} for user {}", name, principal);
            return null;
         }

         String lmname = arr[0];
         String dsname = arr[1];
         XRepository repository = (XRepository) DesignSession.getDesignSession().getDataService();
         SecurityEngine security = SecurityEngine.getSecurity();
         XDataModel model = repository.getDataModel(dsname);

         if(model == null) {
            LOG.error("Data source not found: {} for user {}", dsname, principal);
            return null;
         }

         if(security.checkPermission(principal, ResourceType.QUERY, name, ResourceAction.READ)) {
            return model.getLogicalModel(lmname);
         }

         return null;
      }
      catch(Exception ex) {
         LOG.error("Failed to get logical model " + name + " for user " + principal, ex);
      }

      return null;
   }

   /**
    * Get a serializable Object from engine.
    * @param req the specified request to process.
    * @return a serializable Object.
    */
   @Override
   public Serializable getObject(String req) throws RemoteException {
      int queryStringStart = req.indexOf('?');
      boolean param = queryStringStart >= 0;
      String op = param ? req.substring(0, queryStringStart) : req;

      // get date range provider
      if(op.equals("getDateRange")) {
         AssetRepository rep = AssetUtil.getAssetRepository(false);
         return WorksheetEngine.getDateRangeProvider(rep, null);
      }

      return null;
   }

   private AnalyticRegistry aregistry;

   private static final Logger LOG =
      LoggerFactory.getLogger(AnalyticEngine.class);
}
