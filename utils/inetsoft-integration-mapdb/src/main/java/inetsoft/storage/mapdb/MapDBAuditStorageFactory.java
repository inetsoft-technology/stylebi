/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
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
package inetsoft.storage.mapdb;

import com.google.auto.service.AutoService;
import inetsoft.enterprise.audit.AuditStorage;
import inetsoft.enterprise.audit.AuditStorageFactory;
import inetsoft.util.config.AuditConfig;

@AutoService(AuditStorageFactory.class)
public class MapDBAuditStorageFactory implements AuditStorageFactory {
   @Override
   public String getName() {
      return "mapdb";
   }

   @Override
   public AuditStorage createStorage(AuditConfig config) {
      return new MapDBAuditStorage();
   }
}
