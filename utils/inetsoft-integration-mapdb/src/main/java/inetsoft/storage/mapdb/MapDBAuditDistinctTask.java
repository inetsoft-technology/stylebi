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
import inetsoft.enterprise.audit.DistinctValues;
import inetsoft.sree.internal.cluster.SingletonCallableTask;
import inetsoft.storage.KeyValueEngine;
import inetsoft.storage.KeyValuePair;
import inetsoft.util.audit.AuditRecord;

import java.util.*;

public class MapDBAuditDistinctTask<T extends AuditRecord>
   extends MapDBAuditTask<T> implements SingletonCallableTask<DistinctValues>
{
   public MapDBAuditDistinctTask(String id, Class<T> type, AuditQueryPredicate filter,
                                 List<String> fields, List<String> ranges)
   {
      this.id = id;
      this.type = type;
      this.filter = filter;
      this.fields = fields;
      this.ranges = ranges;
   }

   @Override
   public DistinctValues call() {
      return KeyValueEngine.getInstance().stream(id)
         .map(KeyValuePair::getValue)
         .map(type::cast)
         .filter(r -> applyFilter(r, filter))
         .reduce(new DistinctValues(), this::reduce, DistinctValues::combine);
   }

   private DistinctValues reduce(DistinctValues results, T record) {
      for(String field : fields) {
         results.addDistinct(field, getValue(record, field));
      }

      for(String range : ranges) {
         results.addRange(range, getValue(record, range));
      }

      return results;
   }

   private final String id;
   private final Class<T> type;
   private final AuditQueryPredicate filter;
   private final List<String> fields;
   private final List<String> ranges;
}
