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


package inetsoft.web.wiz.request;

import inetsoft.web.wiz.model.WizXmlaDefinition;

/**
 * An unsaved XMLA definition to test or to list catalogs for.
 *
 * @param path       the stored data source the definition came from, when it already exists. Naming
 *                   one requires WRITE on it and lets a null password mean "the stored one".
 * @param definition the settings to use. May be omitted when {@code path} is given, in which case
 *                   the stored settings are used.
 */
public record WizXmlaDraftRequest(String path, WizXmlaDefinition definition) {
}
