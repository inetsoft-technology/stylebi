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
package inetsoft.mv;

import inetsoft.mv.fs.internal.ClusterUtil;
import inetsoft.mv.trans.UserInfo;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.Catalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * SharedMVUtil, utility mv shared methods.
 *
 * @author InetSoft Technology Corp.
 * @version 12.0
 */
public class SharedMVUtil {
   private SharedMVUtil() {
   }

   /**
    * Check mv shared for the analyzed mvs and stored mvs, the goal:
    * 1: register sheet(s) to stored def(MVManager) if the stored def can be shared
    * 2: unregister sheet(s) from stored def if the stored def cannot shared now
    */
   public static List<MVDef> shareAnalyzedMV(List<MVDef> analyzedDefs,
                                             List<UserInfo> hints)
   {
      // split vs mv and ws mv
      List<MVDef> vsMVdefs = new ArrayList<>();
      List<MVDef> wsMVdefs = new ArrayList<>();

      for(MVDef def : analyzedDefs) {
         if(def.isWSMV()) {
            wsMVdefs.add(def);
         }
         else {
            vsMVdefs.add(def);
         }
      }

      vsMVdefs = shareAnalyzedMV(vsMVdefs, hints, false);
      wsMVdefs = shareAnalyzedMV(wsMVdefs, hints, true);

      // add all to one list and return
      vsMVdefs.addAll(wsMVdefs);
      return vsMVdefs;
   }

   private static List<MVDef> shareAnalyzedMV(List<MVDef> analyzedDefs, List<UserInfo> hints,
                                              boolean wsMV)
   {
      if(analyzedDefs.isEmpty()) {
         return analyzedDefs;
      }

      Catalog catalog = Catalog.getCatalog();
      List<MVDef> swap = new ArrayList<>(analyzedDefs.size());
      swap.addAll(analyzedDefs);
      analyzedDefs = swap;
      List<MVDef> results = new ArrayList<>();

      // 1: check self shared
      shareMV(analyzedDefs);

      // 2: get all sheets in the analyzed mv
      Set<String> sheetIds = getSheetIds(analyzedDefs);
      MVManager mgr = MVManager.getManager();
      MVDef[] storedDefs = mgr.list(false, def -> def.isWSMV() == wsMV);

      // 3: build sheet name to mv def map
      Map<String, List<MVDef>> id2defs = buildSheetIdToMVDefMap(storedDefs);
      boolean changed = false;
      List<MVDef> notSharedAnalyzedDef = new ArrayList<>();

      // 4: check analyzed mv can use stored mv
      for(int i = analyzedDefs.size() - 1; i >= 0; i--) {
         MVDef def = analyzedDefs.get(i);
         boolean shared = false;
         List<String> hintList = new ArrayList<>();
         String hint;

         for(MVDef stored : storedDefs) {
            if(stored.isSharedBy(def)) {
               String[] registeredSheetIds = def.getMetaData().getRegisteredSheets();
               removeMVDefFromMap(id2defs, registeredSheetIds, stored);
               // add names to the shared mv def
               stored.shareMV(def);
               analyzedDefs.remove(i);
               def = stored;
               def.exist = true;
               shared = true;
               break;
            }

            hint = MVMetaData.SHARE_HINT.get();

            if(hint != null) {
               String avs = getName(def);
               String cvs = getName(stored);

               if(!cvs.startsWith("Recycle Bin/")) {
                  hint = hint + ", " + catalog.getString("mv.share.created.def",
                                                         cvs, avs, def.getBoundTable());
                  hintList.add(hint);
               }
            }
         }

         if(!shared) {
            notSharedAnalyzedDef.add(def);

            if(!hintList.isEmpty()) {
               for(String hint2 : hintList) {
                  hints.add(new UserInfo(getName(def), def.getBoundTable(), hint2));
               }
            }
         }

         results.add(def);
      }

      for(int i = notSharedAnalyzedDef.size() - 1; i >= 0; i--) {
         MVDef def = notSharedAnalyzedDef.get(i);

         for(int j = i - 1; j >= 0; j--) {
            MVDef def2 = notSharedAnalyzedDef.get(j);
            def.isSharedBy(def2); // set hint
            String hint = MVMetaData.SHARE_HINT.get();

            if(hint != null && def.getBoundTable() != null &&
               // ASSOCIATION MV are unique for each VS and not sharing
               // them should not generate a warning
               !def.isAssociationMV())
            {
               String avs1 = getName(def);
               String avs2 = getName(def2);
               hint = hint + ", " + catalog.getString("mv.share.analyzed.def",
                                                      avs1, avs2, def.getBoundTable());
               hints.add(new UserInfo(avs1, def.getBoundTable(), hint));
            }
         }
      }

      // 5: remove the sheet from old shared mv, cause cannot shared

      for(Map.Entry<String, List<MVDef>> entry : id2defs.entrySet()) {
         final String id = entry.getKey();

         if(!sheetIds.contains(id)) {
            continue;
         }

         List<MVDef> temp = entry.getValue();

         if(temp != null) {
            for(MVDef def : temp) {
               mgr.remove(def, id);
            }
         }
      }

      return results;
   }

   /**
    * Share mv for stored mvs(MVManager), the gobal:
    * 1: remove all stored mvs which can use other stored mv as shared mv.
    */
   public static MVDef shareCreatedMV(MVDef def) {
      MVDef shared = def;
      MVManager mgr = MVManager.getManager();
      boolean wsMV = def.isWSMV();
      MVDef[] storedDefs = mgr.list(false, def1 -> def1.isWSMV() == wsMV);

      for(MVDef stored : storedDefs) {
         // same?
         if(stored == def || stored.getName().equals(def.getName())) {
            continue;
         }

         MVDef rdef = null; // remove def
         MVDef adef = null; // added def

         // remove stored
         if(def.isSharedBy(stored)) {
            def.shareMV(stored);
            rdef = stored;
            adef = def;
         }
         // remove def
         else if(stored.isSharedBy(def)) {
            stored.shareMV(def);
            rdef = def;
            adef = stored;
            def = stored;
            shared = stored;
         }

         if(rdef != null) {
            mgr.remove(rdef);
            mgr.add(adef);

            if(LOG.isDebugEnabled()) {
               LOG.debug("MV deleted because it can be shared with " + adef.getName() +
                            ": " + rdef.getName());
            }

            try {
               ClusterUtil.deleteClusterMV(def.getName());
            }
            catch(Exception ex) {
               LOG.warn("Failed to remove mv file '"
                           + def.getName() + "'", ex);
            }
         }
      }

      return shared;
   }

   /**
    * Update mv valid option for re-analyze action in mv manager page, the goal:
    * 1: mark stored mv def to valid or not
    */
   public static List<MVDef> checkMVValid(List<MVDef> analyzedDefs) {
      // split vs mv and ws mv
      List<MVDef> vsMVdefs = new ArrayList<>();
      List<MVDef> wsMVdefs = new ArrayList<>();

      for(MVDef def : analyzedDefs) {
         if(def.isWSMV()) {
            wsMVdefs.add(def);
         }
         else {
            vsMVdefs.add(def);
         }
      }

      vsMVdefs = checkMVValid(vsMVdefs, false);
      wsMVdefs = checkMVValid(wsMVdefs, true);

      // add all to one list and return
      vsMVdefs.addAll(wsMVdefs);
      return vsMVdefs;
   }

   private static List<MVDef> checkMVValid(List<MVDef> analyzedDefs, boolean wsMV) {
      if(analyzedDefs.isEmpty()) {
         return analyzedDefs;
      }

      // 1: check self shared
      shareMV(analyzedDefs);

      // 2: get all sheet names in the analyzed mv
      Set<String> sheetIds = getSheetIds(analyzedDefs);

      MVManager mgr = MVManager.getManager();
      MVDef[] storedDefs = mgr.list(false, def -> def.isWSMV() == wsMV);

      // 3: remove all invalidation from mv defs
      for(MVDef stored : storedDefs) {
         stored.getMetaData().validRegister(sheetIds.toArray(new String[0]));
      }

      // 4: build sheet name to mv def map
      Map<String, List<MVDef>> id2defs = buildSheetIdToMVDefMap(storedDefs);

      // 5: remove stored mv def from the map if it can be shared
      for(int i = analyzedDefs.size() - 1; i >= 0; i--) {
         MVDef def = analyzedDefs.get(i);

         for(MVDef stored : storedDefs) {
            if(stored.isSharedBy(def)) {
               String[] registeredSheetIds = def.getMetaData().getRegisteredSheets();
               removeMVDefFromMap(id2defs, registeredSheetIds, stored);
               analyzedDefs.remove(i);
               break;
            }
         }
      }

      // 6: mark the stored mv def as invalid

      for(Map.Entry<String, List<MVDef>> entry : id2defs.entrySet()) {
         final String id = entry.getKey();

         if(!sheetIds.contains(id) || !id2defs.containsKey(id)) {
            continue;
         }

         List<MVDef> notShared = entry.getValue();

         notShared.stream()
            // only invalid if def is shareable but not shared
            .filter(MVDef::isShareable)
            .forEach(def -> {
               def.getMetaData().invalidRegister(id);
               def.setChanged(true);
            });
      }

      // external mv def
      return analyzedDefs;
   }

   /**
    * Check shared mvs.
    */
   private static void shareMV(List<MVDef> defs) {
      List<MVDef> sdefs = new ArrayList<>(defs.size());

      OUTER:
      while(!defs.isEmpty()) {
         MVDef def = defs.remove(defs.size() - 1);

         for(int i = defs.size() - 1; i >= 0; i--) {
            MVDef def2 = defs.get(i);

            // def can be shared by def2, remove def2
            if(def.isSharedBy(def2)) {
               defs.remove(i);
               def.shareMV(def2);
               continue;
            }

            // def2 can be shared by def, remove def directly
            if(def2.isSharedBy(def)) {
               def2.shareMV(def);
               continue OUTER;
            }
         }

         // this def is the last def
         sdefs.add(def);
      }

      defs.addAll(sdefs);
   }

   /**
    * Get all sheet names.
    */
   private static Set<String> getSheetIds(List<MVDef> defs) {
      Set<String> sheetIds = new HashSet<>();

      for(MVDef def : defs) {
         String[] registeredIds = def.getMetaData().getRegisteredSheets();
         Collections.addAll(sheetIds, registeredIds);
      }

      return sheetIds;
   }

   private static String getName(MVDef def) {
      String[] vss = Arrays.stream(def.getMetaData().getRegisteredSheets())
         .map(id -> AssetEntry.createAssetEntry(id).getPath())
         .toArray(String[]::new);
      StringBuilder buf = new StringBuilder();

      for(int i = 0; i < vss.length; i++) {
         if(i > 0) {
            buf.append(",");
         }

         buf.append(vss[i]);
      }

      return buf.toString();
   }

   /**
    * Build map between each viewsheet to its mv defs.
    */
   private static Map<String, List<MVDef>> buildSheetIdToMVDefMap(MVDef[] defs) {
      Map<String, List<MVDef>> id2defs = new HashMap<>();

      for(MVDef def : defs) {
         String[] ids = def.getMetaData().getRegisteredSheets();

         for(String v : ids) {
            List<MVDef> temp = id2defs.computeIfAbsent(v, k -> new ArrayList<>());
            temp.add(def);
         }
      }

      return id2defs;
   }

   /**
    * Remove the specified mv def from the map for the given sheet names.
    */
   private static void removeMVDefFromMap(Map<String, List<MVDef>> id2defs,
                                          String[] sheetIds, MVDef storedDef)
   {
      for(String id : sheetIds) {
         List<MVDef> defs = id2defs.get(id);

         if(defs != null) {
            for(int i = 0; i < defs.size(); i++) {
               if(storedDef == defs.get(i)) {
                  defs.remove(i);
                  break;
               }
            }
         }
      }
   }

   /**
    * Remove the MV for the vs/ws. Only actually remove the MV if no other sheet is
    * using it.
    */
   public static void removeMV(AssetEntry entry) {
      MVManager manager = MVManager.getManager();
      boolean wsMV = entry.getType() == AssetEntry.Type.WORKSHEET;
      MVDef[] defs = manager.list(true, def -> def.isWSMV() == wsMV);

      for(MVDef def : defs) {
         MVMetaData data = def.getMetaData();

         if(data.isRegistered(entry.toIdentifier())) {
            manager.remove(def, entry.toIdentifier());

            if(data.getRegisteredSheets().length == 0) {
               manager.removeDependencies(entry);
            }
         }
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(SharedMVUtil.class);
}
