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
package inetsoft.web.viewsheet.service;

import inetsoft.util.MessageException;
import inetsoft.util.log.LogLevel;

/**
 * Thrown by {@link VSExportService#beginExport} when another export of the same runtime
 * viewsheet already holds the export claim (Bug #77227).
 *
 * <p>The condition is transient: the running export will finish and release the claim. It is a
 * {@link MessageException} so every existing handler that matches {@code MessageException}
 * (viewer, Composer, portal) keeps showing the same "export in progress" message. It is its own
 * type so a caller that needs to tell "retry later" apart from a permanent user-facing error,
 * such as the wiz agent API (Bug #77597), can do so without matching on the localized text.
 */
public class ExportInProgressException extends MessageException {
   public ExportInProgressException(String message, LogLevel logLevel, boolean dumpStack) {
      super(message, logLevel, dumpStack);
   }
}
