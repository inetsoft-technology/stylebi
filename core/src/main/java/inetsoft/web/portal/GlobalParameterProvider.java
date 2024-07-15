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
package inetsoft.web.portal;

import inetsoft.web.reportviewer.model.ParameterPageModel;

import java.security.Principal;

/**
 * Interface for classes that provide the global parameters for reports and viewsheets.
 */
public interface GlobalParameterProvider {
   /**
    * Gets the parameters for a report or viewsheet.
    *
    * @param path   the path to the report or viewsheet.
    * @param report {@code true} if a report or {@code false} if a viewsheet.
    * @param user   a principal that identifies the remote user.
    *
    * @return the parameters or {@code null} if none are required.
    */
   ParameterPageModel getParameters(String path, boolean report, Principal user);
}
