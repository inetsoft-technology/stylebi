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

import inetsoft.enterprise.audit.AuditQueryPredicate;
import inetsoft.enterprise.audit.AuditQuerySort;
import inetsoft.sree.internal.cluster.SingletonCallableTask;
import inetsoft.storage.KeyValueEngine;
import inetsoft.storage.KeyValuePair;
import inetsoft.util.audit.AuditRecord;

import java.lang.reflect.Array;
import java.util.stream.Stream;

public class MapDBAuditQueryTask<T extends AuditRecord>
   extends MapDBAuditTask<T> implements SingletonCallableTask<T[]>
{
   public MapDBAuditQueryTask(String id, Class<T> type, AuditQueryPredicate filter,
                              AuditQuerySort sort, long offset, long limit)
   {
      this.id = id;
      this.type = type;
      this.filter = filter;
      this.sort = sort;
      this.offset = offset;
      this.limit = limit;
   }

   @SuppressWarnings("unchecked")
   @Override
   public T[] call() {
      Stream<T> stream = KeyValueEngine.getInstance().stream(id)
         .map(KeyValuePair::getValue)
         .map(type::cast)
         .filter(r -> applyFilter(r, filter));

      if(sort != null && !sort.getFields().isEmpty()) {
         stream = stream.sorted(createSort(type, sort));
      }

      return stream
         .skip(offset)
         .limit(limit)
         .toArray(n -> (T[]) Array.newInstance(type, n));
   }

   private final String id;
   private final Class<T> type;
   private final AuditQueryPredicate filter;
   private final AuditQuerySort sort;
   private final long offset;
   private final long limit;
}
