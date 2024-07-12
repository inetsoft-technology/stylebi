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
package inetsoft.util.dep;

import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.VSSnapshot;
import inetsoft.util.TransformerManager;

/**
 * VSSnapshotAsset represents a snaphot type asset.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class VSSnapshotAsset extends AbstractSheetAsset {
   /**
    * Snapshot type XAsset.
    */
   public static final String VSSNAPSHOT = "VSSNAPSHOT";

   /**
    * Constructor.
    */
   public VSSnapshotAsset() {
      super();
   }

   /**
    * Constructor.
    * @param snapshot the snapshot asset entry.
    */
   public VSSnapshotAsset(AssetEntry snapshot) {
      this();
      this.entry = snapshot;
   }

   /**
    * Get all dependencies of this asset.
    * @return an array of XAssetDependency.
    */
   @Override
   public XAssetDependency[] getDependencies() {
      AssetRepository engine = AssetUtil.getAssetRepository(false);
      VSSnapshot snap = (VSSnapshot) getCurrentSheet(engine);

      if(snap != null) {
         // check the depenency with the viewsheet
         AssetEntry entry = snap.getOuterDependents()[0];

         if(entry != null) {
            String desc = generateDescription(catalog.getString("VSSnapshot"),
               entry.getDescription());

            return new XAssetDependency[] {new XAssetDependency(
               new ViewsheetAsset(entry), this,
               XAssetDependency.SNAPSHOT_VIEWSHEET, desc)};
         }
      }

      return new XAssetDependency[0];
   }

   /**
    * Get the type of this asset.
    * @return the type of this asset.
    */
   @Override
   public String getType() {
      return VSSNAPSHOT;
   }

   /**
    * Parse an identifier to a real asset.
    * @param identifier the specified identifier, usually with the format of
    * ClassName^identifier.
    */
   @Override
   public void parseIdentifier(String identifier) {
      int idx = identifier.indexOf("^");
      String className = identifier.substring(0, idx);

      if(!className.equals(getClass().getName())) {
         return;
      }

      identifier = identifier.substring(idx + 1);
      entry = AssetEntry.createAssetEntry(identifier);
   }

   /**
    * Create an asset by its path and owner if any.
    *
    * @param path         the specified asset path.
    * @param userIdentity the specified asset owner if any.
    */
   @Override
   public void parseIdentifier(String path, IdentityID userIdentity) {
      int scope = userIdentity != null ?
         AssetRepository.USER_SCOPE : AssetRepository.GLOBAL_SCOPE;
      entry = new AssetEntry(scope,
         AssetEntry.Type.VIEWSHEET_SNAPSHOT, path, userIdentity);
   }

   /**
    * Convert this asset to an identifier.
    * @return an identifier.
    */
   @Override
   public String toIdentifier() {
      return getClass().getName() + "^" + entry.toIdentifier();
   }

   /**
    * Clone this object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      AssetEntry snapshot2 = (AssetEntry) entry.clone();
      return new VSSnapshotAsset(snapshot2);
   }

   /**
    * Get corresponding sheet object.
    */
   @Override
   protected AbstractSheet getSheet0() {
      return new VSSnapshot();
   }
   
   /**
    * Get transformer type.
    */
   @Override
   protected String getTransformerType() {
      return TransformerManager.VIEWSHEET;
   }

   /**
    * Check if should validate repository path.
    */
   @Override
   protected boolean checkRepositoryPath() {
      return true;
   }
}
