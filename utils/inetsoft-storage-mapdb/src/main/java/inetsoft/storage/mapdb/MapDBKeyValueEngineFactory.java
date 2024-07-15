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
import inetsoft.storage.KeyValueEngine;
import inetsoft.storage.KeyValueEngineFactory;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.util.config.MapDBConfig;

import java.nio.file.Paths;
import java.util.Objects;

/**
 * {@code MapDBKeyValueEngineFactory} is an implementation of {@link KeyValueEngineFactory} that
 * creates instances of {@link MapDBKeyValueEngine}.
 */
@AutoService(KeyValueEngineFactory.class)
public class MapDBKeyValueEngineFactory implements KeyValueEngineFactory {
   @Override
   public String getType() {
      return "mapdb";
   }

   @Override
   public KeyValueEngine createEngine(InetsoftConfig config) {
      MapDBConfig mapdb = config.getKeyValue().getMapdb();
      Objects.requireNonNull(mapdb, "The MapDB configuration cannot be null");
      Objects.requireNonNull(mapdb.getDirectory(), "The MapDB database directory cannot be null");
      return new MapDBKeyValueEngine(Paths.get(mapdb.getDirectory()));
   }
}
