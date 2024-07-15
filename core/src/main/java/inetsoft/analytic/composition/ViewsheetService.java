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
package inetsoft.analytic.composition;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.VSSnapshot;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.vslayout.AbstractLayout;
import inetsoft.util.SingletonManager;

import java.rmi.RemoteException;
import java.security.Principal;

/**
 * Viewsheet service, includes a viewsheet repository to be the server
 * of exploratory analyzer. It receives asset events as requests from analyzer
 * and sends asset commands as responses.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
@SingletonManager.Singleton(ViewsheetService.Reference.class)
public interface ViewsheetService extends WorksheetService {
   /**
    * Preview viewsheet.
    */
   String PREVIEW_VIEWSHEET = PREVIEW_PREFIX + "VIEWSHEET__";

   /**
    * Open a temporary viewsheet.
    * @param entry the specified base viewsheet entry.
    * @param user the specified user.
    * @param rid the specified report id.
    * @return the viewsheet id.
    */
   String openTemporaryViewsheet(AssetEntry entry, Principal user, String rid)
      throws Exception;

   /**
    * Open a preview viewsheet.
    * @param id the specified viewsheet id.
    * @param user the specified user.
    * @return the viewsheet id.
    */
   String openPreviewViewsheet(String id, Principal user, AbstractLayout layout)
      throws Exception;

   /**
    * Open a preview viewsheet.
    * @param id the specified viewsheet id.
    * @param user the specified user.
    * @param previewId the previewId, when opening to refresh an existing preview viewsheet.
    *                  The old viewsheet will be closed.
    * @return the viewsheet id.
    */
   String openPreviewViewsheet(String id, Principal user, AbstractLayout layout, String previewId)
      throws Exception;

   /**
    * Refresh preview viewsheet.
    * @param id the viewsheet id which is to be refreshed, the viewsheet is
    *  preview viewsheet.
    * @param user the specified user.
    * @return if refresh successful.
    */
   boolean refreshPreviewViewsheet(String id, Principal user, AbstractLayout layout)
      throws Exception;

   /**
    * Open an existing viewsheet.
    * @param entry the specified asset entry.
    * @param user the specified user.
    * @param viewer <tt>true</tt> if is viewer, <tt>false</tt> otherwise.
    * @return the viewsheet id.
    */
   String openViewsheet(AssetEntry entry, Principal user, boolean viewer)
      throws Exception;

   /**
    * Get the runtime viewsheet.
    * @param id the specified viewsheet id.
    * @param user the specified user.
    * @return the runtime viewsheet if any.
    */
   RuntimeViewsheet getViewsheet(String id, Principal user)
      throws Exception;

   /**
    * Save the viewsheet.
    * @param vs the specified viewsheet.
    * @param entry the specified asset entry.
    * @param user the specified user.
    * @param force <tt>true</tt> to set viewsheet forcely without
    * @param updateDependency <tt>true</tt> to update dependency or not
    * checking.
    */
   void setViewsheet(Viewsheet vs, AssetEntry entry, Principal user, boolean force,
                     boolean updateDependency)
      throws Exception;

   /**
    * Save the snapshop.
    * @param vs the specified snapshot.
    * @param entry the specified asset entry.
    * @param user the specified user.
    * @param force <tt>true</tt> to set viewsheet forcely without
    * checking.
    */
   void setSnapshot(VSSnapshot vs, AssetEntry entry, Principal user, boolean force)
      throws Exception;

   /**
    * Close a viewsheet.
    * @param id the specified viewsheet id.
    */
   void closeViewsheet(String id, Principal user) throws Exception;

   /**
    * Add excution id to map.
    * @param id the specified viewsheet id.
    */
   public void addExecution(String id);

   /**
    * Delete the excution id from map.
    * @param id the specified viewsheet id.
    */
   public void removeExecution(String id);

   /**
    * Get the runtime viewsheets.
    * @return the runtime viewsheets.
    */
   RuntimeViewsheet[] getRuntimeViewsheets(Principal user);

   /**
    * Update the all runtime viewsheet bookmark based on entry.
    *
    * @param viewsheet
    */
   void updateBookmarks(AssetEntry viewsheet);

   final class Reference extends SingletonManager.Reference<ViewsheetService> {
      @Override
      public synchronized ViewsheetService get(Object ... parameters) {
         if(engine == null) {
            try {
               engine = new ViewsheetEngine();
            }
            catch(RemoteException e) {
               throw new RuntimeException("Failed to create viewsheet engine", e);
            }
         }

         return engine;
      }

      @Override
      public synchronized void dispose() {
         if(engine != null) {
            engine.dispose();
            engine = null;
         }
      }

      private ViewsheetEngine engine;
   }
}
