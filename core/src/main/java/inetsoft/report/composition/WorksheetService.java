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
package inetsoft.report.composition;

import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.RenameInfo;
import inetsoft.util.SingletonManager;

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.List;
import java.util.Vector;

/**
 * Worksheet service, includes an asset repository to be the server of
 * exploratory analyzer. It receives asset events as requests from analyzer
 * and sends asset commands as responses.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
@SingletonManager.Singleton(WorksheetService.Reference.class)
public interface WorksheetService {
   /**
    * Preview prefix.
    */
   String PREVIEW_PREFIX = "__PREVIEW_";
   /**
    * Preview worksheet.
    */
   String PREVIEW_WORKSHEET = PREVIEW_PREFIX + "WORKSHEET__";

   /**
    * Thread local.
    */
   ThreadLocal<List<Exception>> ASSET_EXCEPTIONS = new ThreadLocal<>();

   /**
    * Get the asset repository.
    * @return the associated asset repository.
    */
   AssetRepository getAssetRepository();

   /**
    * Set the asset repository.
    * @param engine the specified asset repository.
    */
   void setAssetRepository(AssetRepository engine);

   /**
    * Dispose the worksheet service.
    */
   void dispose();

   /**
    * Open a temporary worksheet.
    * @param user the specified user.
    * @param entry the specified AssetEntry.
    * @return the worksheet id.
    */
   String openTemporaryWorksheet(Principal user, AssetEntry entry) throws Exception;

   /**
    * Open a preview worksheet.
    * @param id the specified worksheet id.
    * @param name the specified table assembly name.
    * @param user the specified user.
    */
   String openPreviewWorksheet(String id, String name, Principal user)
      throws Exception;

   /**
    * Open an existing worksheet.
    * @param entry the specified asset entry.
    * @param user the specified user.
    * @return the worksheet id.
    */
   String openWorksheet(AssetEntry entry, Principal user)
      throws Exception;

   /**
    * Get the runtime worksheet.
    * @param id the specified worksheet id.
    * @param user the specified user.
    * @return the runtime worksheet if any.
    */
   RuntimeWorksheet getWorksheet(String id, Principal user)
      throws Exception;

   /**
    * Get the runtime worksheet.
    * @param id the specified worksheet id.
    * @param user the specified user.
    * @return the runtime sheet if any.
    */
   RuntimeSheet getSheet(String id, Principal user);

   RuntimeSheet getSheet(String id, Principal user, boolean touch);

   /**
    * Save the worksheet.
    * @param ws the specified worksheet.
    * @param entry the specified asset entry.
    * @param user the specified user.
    * @param force <tt>true</tt> to set worksheet forcely without
    * checking.
    */
   void setWorksheet(Worksheet ws, AssetEntry entry,
                     Principal user, boolean force, boolean updateDependency)
      throws Exception;

   /**
    * Close a worksheet.
    * @param id the specified worksheet id.
    */
   void closeWorksheet(String id, Principal user) throws Exception;

   /**
    * Get the runtime sheets.
    * @return the runtime sheets.
    */
   RuntimeSheet[] getRuntimeSheets(Principal user);

   /**
    * Set the server flag.
    * @param server <tt>true</tt> if server, <tt>false</tt> otherwise.
    */
   void setServer(boolean server);

   /**
    * Check if server is turned on.
    * @return <tt>true</tt> if turned on, <tt>false</tt> turned off.
    */
   boolean isServer();

   /**
    * Copy runtime condition to worksheet.
    * @param vid viewsheet id
    * @param rws runtime worksheet.
    */
   void applyRuntimeCondition(String vid, RuntimeWorksheet rws);

   /**
    * Get thread definitions of executing event according the id.
    */
   Vector getExecutingThreads(String id);

   /**
    * Check if the specified entry is duplicated.
    * @param engine the specified engine.
    * @param entry the specified entry.
    * @return <tt>true</tt> if duplicated, <tt>false</tt> otherwise.
    */
   boolean isDuplicatedEntry(AssetRepository engine, AssetEntry entry)
      throws Exception;

   /**
    * Localize the specified asset entry.
    */
   String localizeAssetEntry(String path, Principal principal,
                             boolean isReplet, AssetEntry entry,
                             boolean isUserScope);

   /**
    * Get the cached propery by providing the specified key.
    */
   Object getCachedProperty(Principal user, String key);

   /**
    * Set the cached property by providing the specified key-value pair.
    */
   void setCachedProperty(Principal user, String key, Object val);

   String getLockOwner(AssetEntry entry);

   long getDataChangedTime(AssetEntry entry);

   /**
    * update dependence
    */
   void renameDep(String rid);

   /**
    * roll back the dependence modify, start at the next level
    */
   void rollbackRenameDep(String rid);

   /**
    * Whether need to update dependence.
    */
   boolean needRenameDep(String rid);

   /**
    * Clear rename dependence.
    */
   void clearRenameDep(String rid);

   void updateRenameInfos(Object rid, AssetObject assetEntry, List<RenameInfo> renameInfos);

   RuntimeWorksheet[] getAllRuntimeWorksheetSheets();

   /**
    * fix the dependence entry when runtime sheet entry is changed(save as)
    */
   void fixRenameDepEntry(String rid, AssetObject newEntry);

   final class Reference extends SingletonManager.Reference<WorksheetService> {
      @SuppressWarnings("unchecked")
      @Override
      public synchronized WorksheetService get(Object ... parameters) {
         if(service == null) {
            Class<? extends WorksheetService> implementation = null;

            try {
               implementation = (Class<? extends WorksheetService>)
                  Class.forName("inetsoft.analytic.composition.ViewsheetEngine");
               implementation =
                  (Class<? extends WorksheetService>)
                  Class.forName("inetsoft.analytic.composition.ViewsheetService");
            }
            catch(Exception ignore) {
            }

            if(implementation == null) {
               try {
                  service = new WorksheetEngine();
               }
               catch(RemoteException e) {
                  throw new RuntimeException("Failed to create worksheet service", e);
               }
            }
            else {
               // if the viewsheet engine is available, use that
               service = SingletonManager.getInstance(implementation);
            }
         }

         return service;
      }

      @Override
      public synchronized void dispose() {
         if(service != null) {
            service.dispose();
            service = null;
         }
      }

      private WorksheetService service;
   }
}
