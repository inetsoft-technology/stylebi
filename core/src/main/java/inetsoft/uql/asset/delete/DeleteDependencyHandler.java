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
package inetsoft.uql.asset.delete;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.DependencyTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class DeleteDependencyHandler {
   public static DeleteDependencyInfo createWsDependencyInfo(List<DeleteInfo> dinfos,
                                                             RuntimeWorksheet rws)
   {
      DeleteDependencyInfo info = new DeleteDependencyInfo();

      if(dinfos.size() == 0 || rws == null || rws.getWorksheet() == null) {
         return info;
      }

      AssetEntry bentry = rws.getEntry();
      String id = bentry.toIdentifier();
      List<AssetObject> assets = DependencyTool.getDependencies(id);

      if(assets == null) {
         return info;
      }

      for(AssetObject asset : assets) {
         info.setDeleteInfo(asset, dinfos);
      }

      return info;
   }

   /**
    * only check whether has dependency. end logic when find first.
    */
   @SuppressWarnings("unused")
   public static Boolean hasDependency(DeleteDependencyInfo info) {
      DependencyException ex = checkDependency(info);

      return ex != null && !ex.isEmpty();
   }

   public static String checkDependencyStatus(DeleteDependencyInfo info) {
      DependencyException dependency = DeleteDependencyHandler.checkDependency(info, true);

      return dependency != null ? dependency.getMessage() : null;
   }

   @Nullable
   public static DependencyException checkDependency(DeleteDependencyInfo info) {
      if(info == null) {
         return null;
      }

      return checkDependency(info, false);
   }

   @Nullable
   public static DependencyException checkDependency(DeleteDependencyInfo info, boolean checkAll) {

      // dispatch tasks to multi thread
      List<Future<Map<DeleteInfo, List<AssetObject>>>> mapResult = map(info, checkAll);

      // Combination and calculate of results
      return reduce(mapResult, checkAll);
   }

   private static List<Future<Map<DeleteInfo, List<AssetObject>>>> map(DeleteDependencyInfo info,
                                                                       final boolean checkAll)
   {
      AssetObject[] entries = info.getAssetObjects();
      List<Future<Map<DeleteInfo, List<AssetObject>>>> result = new ArrayList<>();

      int nthread = DependencyTool.getThreadNumber(entries.length);
      int count = (int) Math.ceil(entries.length * 1.0 / nthread);

      ExecutorService executors = new ThreadPoolExecutor(nthread, nthread,
         0L, TimeUnit.MILLISECONDS,
         new LinkedBlockingQueue<>(),
         // Too much info doesn't make much sense to the user, so discard subsequent tasks.
         new ThreadPoolExecutor.DiscardPolicy());

      for(int i = 0; i < nthread; i++) {
         List<AssetObject> list = DependencyTool.getThreadAssets(i, count, entries);

         Future<Map<DeleteInfo, List<AssetObject>>> future =
            executors.submit(() -> doCheckDependency(list, info, checkAll));

         result.add(future);
      }

      executors.shutdown();

      return result;
   }

   @Nullable
   private static DependencyException reduce(List<Future<Map<DeleteInfo,
      List<AssetObject>>>> result, final boolean checkAll)
   {
      Map<DeleteInfo, List<AssetObject>> map = new ConcurrentHashMap<>();

      result.parallelStream()
         .map(future -> {
            if(checkAll || map.size() < 1) {
               try {
                  // Deleting column checks should not take too long
                  return future.get(15, TimeUnit.SECONDS);
               } catch(Exception e) {
                  LOG.warn("Thread task execute error.", e);
               }
            }

            return null;
         })
         .filter(m -> !CollectionUtils.isEmpty(m))
         .flatMap(m -> m.entrySet().stream())
         .filter(entry -> entry.getValue() != null && entry.getValue().size() > 0)
         .forEach(entry -> map.compute(entry.getKey(), (k, oldValue) -> {
            if(oldValue == null) {
               return entry.getValue();
            }

            oldValue.addAll(entry.getValue());

            return oldValue;
         }));

      List<DependencyException> exs = map.entrySet()
         .stream()
         .map(entry -> {
            DependencyException dependEx = new DependencyException(entry.getKey());
            entry.getValue().forEach(dependEx::addDependency);

            return dependEx;
         })
         .collect(Collectors.toList());

      return exs.size() < 1 ? null : new DependencyException(exs);
   }

   private static Map<DeleteInfo, List<AssetObject>> doCheckDependency(List<AssetObject> entries,
                                                                       DeleteDependencyInfo info,
                                                                       boolean checkAll)
      throws Exception
   {
      Map<DeleteInfo, List<AssetObject>> map = new HashMap<>();

      for(AssetObject entry : entries) {
         if(entry instanceof AssetEntry) {
            AssetEntry aentry = (AssetEntry) entry;
            List<DeleteInfo> infos = info.getDeleteInfo(aentry);

            if(aentry.isWorksheet()) {
               putResult(map, entry, new AssetDependencyChecker(aentry)
                  .hasDependency(infos, checkAll));
            }

            if(aentry.isViewsheet()) {
               putResult(map, entry, new ViewsheetDependencyChecker(aentry)
                  .hasDependency(infos, checkAll));
            }
         }
      }

      return map;
   }

   private static void putResult(@NonNull Map<DeleteInfo, List<AssetObject>> map,
                                 @NonNull AssetObject assetObject,
                                 @Nullable List<DeleteInfo> result)
   {
      if(!CollectionUtils.isEmpty(result)) {
         for(DeleteInfo info : result) {
            map.compute(info, (key, oldValue) -> {
               if(oldValue == null) {
                  oldValue = new ArrayList<>();
               }

               oldValue.add(assetObject);

               return oldValue;
            });
         }
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(DeleteDependencyHandler.class);
}
