/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.uql.service;

import inetsoft.util.MessageException;

/**
 * Thrown when the storage failed or did not respond in time while an object of the data source
 * registry was saved. The message is plain text for the user and the call can be retried.
 */
public class DataSourceStorageException extends MessageException {
   public DataSourceStorageException(String message) {
      super(message);
   }

   public DataSourceStorageException(String message, Exception cause) {
      super(message, cause);
   }
}
