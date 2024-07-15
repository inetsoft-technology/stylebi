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

import inetsoft.enterprise.audit.*;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.PutKeyValueTask;
import inetsoft.util.audit.AuditRecord;

import java.util.*;

public class MapDBAuditStorage implements AuditStorage {
   public MapDBAuditStorage() {
      this.cluster = Cluster.getInstance();
   }

   @Override
   public void insert(AuditRecord record) {
      String dbId = getDatabaseId(record);
      String recordId = UUID.randomUUID().toString();

      try {
         cluster.submit(dbId, new PutKeyValueTask<>(dbId, recordId, record)).get();
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to insert record", e);
      }
   }

   @Override
   public <T extends AuditRecord> List<T> query(Class<T> type, AuditQueryPredicate filter,
                                                AuditQuerySort sort, long offset, long limit)
   {
      String dbId = getDatabaseId(type);

      try {
         return Arrays.asList(cluster.submit(
            dbId, new MapDBAuditQueryTask<>(dbId, type, filter, sort, offset, limit)).get());
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to query records", e);
      }
   }

   @Override
   public <T extends AuditRecord> DistinctValues distinct(Class<T> type, AuditQueryPredicate filter,
                                                          List<String> fields, List<String> ranges)
   {
      String dbId = getDatabaseId(type);

      try {
         return cluster.submit(
            dbId, new MapDBAuditDistinctTask<>(dbId, type, filter, fields, ranges)).get();
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to query distinct records", e);
      }
   }

   @Override
   public long count(Class<? extends AuditRecord> type,  AuditQueryPredicate filter) {
      String dbId = getDatabaseId(type);

      try {
         return cluster.submit(dbId, new MapDBAuditCountTask<>(dbId, type, filter)).get();
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to count records", e);
      }
   }

   private String getDatabaseId(AuditRecord record) {
      return getDatabaseId(record.getClass());
   }

   private String getDatabaseId(Class<? extends AuditRecord> type) {
      return "audit" + type.getSimpleName();
   }

   private final Cluster cluster;
}
