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

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AbstractSheetEnumeration implements the XAssetEnumeration interface,
 * generates a series of sheet element, one at a time.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public abstract class AbstractSheetEnumeration implements XAssetEnumeration<XAsset> {
   /**
    * Constructor.
    */
   protected AbstractSheetEnumeration() {
      engine = AssetUtil.getAssetRepository(false);
   }

   /**
    * Get enumeration.
    */
   protected Enumeration<XAsset> getEnumeration() {
      try {
         IdentityID[] susers = SUtil.getAllAssetUsers();
         IdentityID[] users = new IdentityID[susers.length + 1];
         System.arraycopy(susers, 0, users, 1, susers.length);
         List<Enumeration<XAsset>> senums = Arrays.stream(users)
            .map(SheetEnumeration::new)
            .collect(Collectors.toList());
         return new EnumEnumeration<>(senums);
      }
      catch(Exception e) {
         LOG.error("Failed to enumerate sheets", e);
      }

      return null;
   }

   /**
    * Check if is the expected entry type.
    */
   protected boolean entryExpected(AssetEntry entry) {
      return true;
   }

   /**
    * Get folder type, wether AssetEntry.Type.FOLDER or AssetEntry.Type.REPOSITORY_FOLDER.
    */
   protected abstract AssetEntry.Type getFolderType();

   /**
    * Get selector.
    */
   protected abstract AssetEntry.Selector getSelector();

   /**
    * Get corresponding XAsset class name.
    */
   protected abstract String getXAssetClassName();

   /**
    * Get all child entries of a parent entry recursively.
    */
   private List<AssetEntry> getEntries(AssetEntry parent,
      AssetEntry.Selector selector, AssetRepository engine)
   {
      List<AssetEntry> vec = new ArrayList<>();

      try {
         AssetEntry[] entries = engine.getEntries(parent, null, null, selector);

         for(AssetEntry entry : entries) {
            if(entry.isFolder()) {
               if(!entry.isRoot() && entry.getName().equals("")) {
                  continue;
               }

               vec.addAll(getEntries(entry, selector, engine));
            }
            else if(entryExpected(entry)) {
               vec.add(entry);
            }
         }
      }
      catch(Exception e) {
         LOG.error("Failed to get children of asset entry: " + parent, e);
      }

      return vec;
   }

   /**
    * Sheet enumeration enumerates a sheet.
    */
   private class SheetEnumeration implements DisposableEnumeration<XAsset> {
      /**
       * Constructor.
       */
      SheetEnumeration(IdentityID user) {
         this.user = user;
         prepareAssetEntries();
      }

      /**
       * Tests if this enumeration contains more elements.
       * @return <code>true</code> if and only if this enumeration object
       * contains at least one more element to provide,
       * <code>false</code> otherwise.
       */
      @Override
      public boolean hasMoreElements() {
         return entries != null && currentIndex < entries.size();
      }

      /**
       * Returns the next element of this enumeration if this enumeration object
       * has at least one more element to provide.
       * @return the next element of this enumeration.
       */
      @Override
      public XAsset nextElement() {
         AssetEntry entry = entries.get(currentIndex++);
         String cls = getXAssetClassName();
         XAsset asset;

         try {
            asset = (XAsset) Class.forName(cls).newInstance();
         }
         catch(Exception ex) {
            LOG.error("Failed to instantiate asset class: " + cls, ex);
            return null;
         }

         String identifier = cls + "^" + entry.toIdentifier();
         asset.parseIdentifier(identifier);
         return asset;
      }

      /**
       * Dispose the enumeration.
       */
      @Override
      public void dispose() {
         entries.clear();
         entries = null;
      }

      /**
       * Prepare asset entries got by parent.
       */
      private void prepareAssetEntries() {
         int scope = user == null ? AssetRepository.GLOBAL_SCOPE : AssetRepository.USER_SCOPE;
         AssetEntry parent = new AssetEntry(scope, getFolderType(), "/", user);
         entries = getEntries(parent, getSelector(), engine);
      }

      private IdentityID user;
      private List<AssetEntry> entries;
      private int currentIndex;
   }

   private AssetRepository engine;

   private static final Logger LOG = LoggerFactory.getLogger(AbstractSheetEnumeration.class);
}
