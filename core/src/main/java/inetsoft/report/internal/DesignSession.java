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
package inetsoft.report.internal;

import inetsoft.report.*;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.internal.binding.*;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.*;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.SoftReference;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.*;

/**
 * A special XSessionManager for design time. It does not execute the
 * query to populate the report element data. Instead, it create data
 * models using the query meta data.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
@SingletonManager.Singleton(DesignSession.Reference.class)
public class DesignSession extends XSessionManager {
   /**
    * Get design session.
    */
   public static DesignSession getDesignSession() throws RemoteException {
      return SingletonManager.getInstance(DesignSession.class);
   }

   /**
    * Create a design session manager.
    */
   public DesignSession() throws RemoteException {
      super();
   }

   /**
    * Create a design session manager.
    */
   public DesignSession(XDataService service, Object session) {
      super(service, session);
   }

   /**
    * Get a table lens representing meta data.
    */
   public TableLens getQueryMetaData(String qname, ReportSheet report, int type,
                                     Principal user) throws Exception {
      switch(type) {
      case SourceAttr.MODEL:
         return new DefaultTableLens(0, 0);
      case SourceAttr.ASSET:
         return getAssetMetaData(qname, report, user);
      }

      return null;
   }

   /**
    * Get asset meta data.
    */
   private TableLens getAssetMetaData(String name, ReportSheet report, Principal user)
      throws Exception
   {
      AssetEntry entry = AssetEntry.createAssetEntry(name, ((XPrincipal) user).getOrgId());
      AssetRepository asset = AssetUtil.getAssetRepository(false);

      if(asset == null) {
         LOG.warn("Asset repository is not available");
         return null;
      }

      asset = new RuntimeAssetEngine(asset, report);
      Worksheet ws = (Worksheet) asset.getSheet(entry, user, false, AssetContent.ALL);

      if(entry != null && !asset.containsEntry(entry)) {
         LOG.warn("Asset not found: " + entry.getPath());
         throw new RuntimeException(Catalog.getCatalog().getString(
            "common.assetNotFound", entry.getPath()));
      }

      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      if(entry != null) {
         box.setWSName(entry.getSheetName());
      }

      box.setWSEntry(entry);
      box.setBaseUser(user);
      String assembly = ws.getPrimaryAssemblyName();

      try {
         if(assembly != null) {
            box.refreshColumnSelection(assembly, true);
         }
         else {
            LOG.warn("The dependent Data Worksheet has no primary assembly");
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to refresh column selection for assembly: " + assembly, ex);
      }

      return assembly == null ? null : box.getTableLens(assembly,
         AssetQuerySandbox.DESIGN_MODE | AssetQuerySandbox.EMBEDDED_MODE);
   }

   public static void setXMetaInfos(XLogicalModel lmodel, ColumnSelection columns, XTypeNode root) {
      if(lmodel == null) {
         return;
      }

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         String entity = columns.getAttribute(i).getEntity();
         XEntity xentity = lmodel.getEntity(entity);

         if(xentity != null) {
            XAttribute attr = xentity.getAttribute(columns.getAttribute(i).getAttribute());

            if(attr != null) {
               ((XTypeNode) root.getChild(i)).setXMetaInfo(attr.getXMetaInfo());
            }
         }
      }
   }

   /**
    * Clear data cache.
    */
   @Override
   public void clearCache() {
      super.clearCache();
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(DesignSession.class);

   public static final class Reference
      extends SingletonManager.Reference<DesignSession>
   {
      @Override
      public synchronized DesignSession get(Object ... parameters) {
         if(session == null) {
            try {
               session = new DesignSession();
               session.bind(System.getProperty("user.name"));
            }
            catch(RemoteException ex) {
               LOG.error("Failed to bind design session and add " +
                     "query refresh listener", ex);
               throw new RuntimeException("Failed to bind design session", ex);
            }
         }

         return session;
      }

      @Override
      public synchronized void dispose() {
         if(session != null) {
            session.tearDown();
            session = null;
         }
      }

      private DesignSession session;
   }
}
