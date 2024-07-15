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
inetsoft {
   def useEfk = commandLine.contains('--use-efk')

   setup('@CONFIG_DIR@') {
      storage {
         restore(new File('@STORAGE_ZIP@'))
      }

      properties {
         put 'license.key', '@LICENSE_KEY@'
         put 'webapp.dir.uri', '@WEBAPP_DIR_URI@'
         put 'orders.jar.path', '@ORDERS_JAR_PATH@'
         put 'inetsoft.uql.jdbc.pool.Examples/Orders.readOnly', 'true'

         new File('@LOCAL_PROPERTIES@').with {localFile ->
            if(localFile.exists()) {
               Properties props = new Properties()
               localFile.withInputStream {props.load(it)}
               props.stringPropertyNames().each {put(it, props.getProperty(it))}
            }
         }

         if(useEfk) {
            put 'log.provider', 'fluentd'
            put 'log.fluentd.host', 'localhost'
            put 'log.fluentd.port', '24224'
            put 'log.fluentd.logViewUrl', 'http://localhost:5601/goto/cbff9d85f159c9e9065914eaaf08e76b'
            put 'log.fluentd.auditViewUrl', '' // todo configure audit view url
         }
      }
   }
}