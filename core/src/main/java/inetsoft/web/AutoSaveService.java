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
import org.springframework.stereotype.Service;
import inetsoft.storage.BlobStorage;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Lazy;
import java.io.FileNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
@Service
@Lazy(false)
public class AutoSaveService {
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
      for(String fileName: autoSavedFiles) {
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
}