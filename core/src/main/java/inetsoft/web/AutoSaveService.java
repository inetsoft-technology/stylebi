/*
 * Copyright (c) 2025, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 */
package inetsoft.web;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.storage.BlobStorage;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Lazy(false)
@ClusterProxy
public class AutoSaveService {
   public AutoSaveService(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   @Scheduled(fixedRate = 10800000L)
   public void removeExpiredAutoSaveFiles() {
      BlobStorage<AutoSaveUtils.Metadata> blobStorage = AutoSaveUtils.getStorage(null);
      Instant sevenDaysAgo = Instant.now().minus(Duration.ofDays(7));
      loopCleanAutoSaveFiles(blobStorage, false, sevenDaysAgo);
      loopCleanAutoSaveFiles(blobStorage, true, sevenDaysAgo);
   }

   private void loopCleanAutoSaveFiles(BlobStorage<AutoSaveUtils.Metadata> blobStorage, boolean isRecycle, Instant sevenDaysAgo) {
      List<String> autoSavedFiles = AutoSaveUtils.getAutoSavedFiles(null, isRecycle);
      long assetLastModifiedTime;

      for(String fileName : autoSavedFiles) {
         String file = AutoSaveUtils.getAutoSavedByName(fileName, isRecycle);

         try {
            assetLastModifiedTime = blobStorage.getLastModified(file).toEpochMilli();
         }
         catch(FileNotFoundException e) {
            continue;
         }

         Instant lastModified = Instant.ofEpochMilli(assetLastModifiedTime);

         if(lastModified.isBefore(sevenDaysAgo)) {
            AutoSaveUtils.deleteAutoSaveFile(fileName, null, isRecycle);
         }
      }
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Boolean restoreAutoSaveAssets(@ClusterProxyKey String id, String assetName, boolean override,
                                        Principal principal)
      throws Exception
   {
      // Get auto save sheet from engine.
      AssetEntry entry = AutoSaveUtils.createAssetEntry(id);
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      AbstractSheet sheet = repository.getSheet(entry, principal, false, AssetContent.ALL);
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());

      // Save auto save sheet to engine.
      AssetEntry.Type type = id.startsWith("8^VIEWSHEET") ? AssetEntry.Type.VIEWSHEET :
         AssetEntry.Type.WORKSHEET;
      AssetEntry nentry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, type, assetName,
                                         pId);

      if(!override && viewsheetService.isDuplicatedEntry(repository, nentry)) {
         return false;
      }

      repository.setSheet(nentry, sheet, principal, false);
      ActionRecord actionRecord = SUtil.getActionRecord(principal, ActionRecord.ACTION_NAME_CREATE,
                                                        assetName, AssetEventUtil.getObjectType(entry));
      Audit.getInstance().auditAction(actionRecord, principal);
      return true;
   }

   private final ViewsheetService viewsheetService;
}