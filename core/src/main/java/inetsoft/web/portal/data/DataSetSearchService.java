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
package inetsoft.web.portal.data;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.IndexedStorage;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DataSetSearchService {
   @Autowired
   public DataSetSearchService(AssetRepository assetRepository) {
      this.assetRepository = assetRepository;
   }

   public List<AssetEntry> findAssets(AssetEntry entry, String query, Principal principal)
      throws Exception
   {
      AssetEntry.Selector selector = new AssetEntry.Selector(AssetEntry.Type.WORKSHEET,
                                                             AssetEntry.Type.FOLDER);
      IndexedStorage storage = assetRepository.getStorage(entry);
      SearchFilter filter = new SearchFilter(query, entry, selector, principal, storage,
                                             assetRepository);
      return search(filter, storage);
   }

   /**
    * Executes a search query.
    *
    * @param entry  the entry for the parent folder.
    * @param filter the search filter.
    *
    * @param <T> the search result type.
    *
    * @return the search results.
    *
    * @throws Exception if the search could not be performed.
    */
   private List<AssetEntry> search(SearchFilter filter, IndexedStorage storage)
      throws Exception
   {
      storage.getKeys(filter);
      return filter.getResults();
   }

   private final AssetRepository assetRepository;

   /**
    * Class that represents a result for a search query.
    */
   private static final class SearchResult implements Comparable<SearchResult> {
      /**
       * Creates a new instance of <tt>SearchResult</tt>.
       *
       * @param entry    the asset entry associated with the result.
       * @param distance the Levenshtein distance for the result.
       * @param prefix   the common prefix length for the result.
       */
      public SearchResult(AssetEntry entry, int distance, int prefix) {
         this.entry = entry;
         this.distance = distance;
         this.prefix = prefix;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         SearchResult result = (SearchResult) o;
         return distance == result.distance && prefix == result.prefix &&
            entry.equals(result.entry);
      }

      @Override
      public int hashCode() {
         int result = entry.hashCode();
         result = 31 * result + distance;
         result = 31 * result + prefix;
         return result;
      }

      @Override
      public int compareTo(SearchResult o) {
         int result = 0;

         if(prefix > o.prefix) {
            result = -1;
         }
         else if(prefix < o.prefix) {
            result = 1;
         }
         else if(distance < o.distance) {
            result = -1;
         }
         else if(distance > o.distance) {
            result = 1;
         }

         if(result == 0) {
            result = entry.getPath().compareTo(o.entry.getPath());
         }

         return result;
      }

      private final AssetEntry entry;
      private final int distance;
      private final int prefix;
   }

   /**
    * Specialization of <tt>IndexedStorage.Filter</tt> that builds its results
    * during the filtering stage.
    *
    * @param <T> the result type.
    */
   private static class SearchFilter implements IndexedStorage.Filter {
      /**
       * Creates a new instance of <tt>SearchFilter</tt>
       *
       * @param query       the search query.
       * @param parentEntry the entry of the parent folder.
       */
      public SearchFilter(String query, AssetEntry parentEntry, AssetEntry.Selector selector,
                          Principal principal, IndexedStorage storage,
                          AssetRepository assetRepository)
      {
         this.query = query.toLowerCase();
         this.selector = selector;
         this.searchResults = new TreeSet<>();
         this.storage = storage;
         this.assetRepository = assetRepository;
         String orgID = OrganizationManager.getInstance().getCurrentOrgID();
         IdentityID user = parentEntry.getScope() == AssetRepository.USER_SCOPE && principal != null ?
            IdentityID.getIdentityIDFromKey(principal.getName()) : null;
         AssetEntry entry = new AssetEntry(
            parentEntry.getScope(), AssetEntry.Type.WORKSHEET, parentEntry.getPath(), user);

         if(parentEntry.isRoot() || parentEntry.toIdentifier().endsWith(orgID)) {
            folderPrefix = parentEntry.toIdentifier()
               .substring(0, parentEntry.toIdentifier().length() - orgID.length() - 2);
         }
         else {
            folderPrefix = parentEntry.toIdentifier() + "/";
         }

         if(parentEntry.isRoot() || parentEntry.toIdentifier().endsWith(orgID)) {
            sheetPrefix = entry.toIdentifier()
               .substring(0, entry.toIdentifier().length() - orgID.length() - 2);
         }
         else {
            sheetPrefix = entry.toIdentifier() + "/";
         }
      }

      @Override
      public boolean accept(String key) {
         if(!key.equals(folderPrefix) &&
            (key.startsWith(folderPrefix) || key.startsWith(sheetPrefix)))
         {
            AssetEntry entry = AssetEntry.createAssetEntry(key);
            String parent = entry.getPath();
            String[] parentFolders = parent.split("/");

            if(entry.getParentPath() != null && "Recycle Bin".equals(parentFolders[0])){
               return false;
            }

            assetRepository.copyEntryProperty(entry, storage);

            List<String> names = new ArrayList<>();
            names.add(entry.getName().toLowerCase());
            String alias = entry.getAlias();

            if(alias != null) {
               names.add(alias.toLowerCase());
            }

            for(String name : names) {
               int distance = StringUtils.getLevenshteinDistance(query, name);
               // ignore any distance that is blank. If user typed only part of the name, it should still
               // be a match even though the distance is large since the end of the name is missing.
               // eg 'cat' will match 'cat in the hat' otherwise distance would be all missing chars (11)
               int lengthDifference = Math.abs(query.length() - name.length());
               // the max difference is the min length of the two strings since we are ignoring the
               // length difference of the strings
               int maxDistance = Math.min(query.length(), name.length());
               double equalPercentage = (maxDistance - (distance - lengthDifference)) / (double) maxDistance;

               // At least a 80% match ignoring the length difference between strings,
               // allows for 1/5 characters to be mistyped
               if(equalPercentage > 0.8) {
                  int prefix = prefix(query, name);
                  searchResults.add(new SearchResult(entry, distance, prefix));
                  return true;
               }
            }
         }

         return false;
      }

      /**
       * Creates the filter results.
       *
       * @param searchResults the results of the search.
       *
       * @return the filter results.
       */
      public List<AssetEntry> createResults(Set<SearchResult> searchResults) {
        return searchResults.stream()
           .map(result -> result.entry)
           .filter(entry -> selector.matches(entry.getType()))
           .collect(Collectors.toList());
      }

      /**
       * Gets the filter results.
       *
       * @return the results.
       */
      public List<AssetEntry> getResults() {
         if(results == null) {
            results = createResults(searchResults);
         }

         return results;
      }

      /**
       * Gets the length of any shared prefix between two strings.
       *
       * @param s1 the first string.
       * @param s2 the second string.
       *
       * @return the length of the shared prefix.
       */
      private int prefix(String s1, String s2) {
         int len = Math.min(s1.length(), s2.length());
         int result = 0;

         for(int i = 0; i < len; i++) {
            if(s1.charAt(i) != s2.charAt(i)) {
               break;
            }

            ++result;
         }

         return result;
      }

      private final String query;
      private final String folderPrefix;
      private final String sheetPrefix;
      private final Set<SearchResult> searchResults;
      private List<AssetEntry> results;
      private final AssetEntry.Selector selector;
      private final IndexedStorage storage;
      private final AssetRepository assetRepository;
   }
}
