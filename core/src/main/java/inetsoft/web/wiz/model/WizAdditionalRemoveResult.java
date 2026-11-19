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


package inetsoft.web.wiz.model;

import java.util.List;

/**
 * Per-name outcome of removing additional connections. A name that cannot be removed never stops
 * the others.
 *
 * @param results one entry per requested name, in request order.
 */
public record WizAdditionalRemoveResult(List<Item> results) {
   public static final String HAS_DEPENDENCIES = "HAS_DEPENDENCIES";
   public static final String DATASOURCE_LOST = "DATASOURCE_LOST";
   public static final String UNKNOWN = "UNKNOWN";

   /**
    * @param name              the requested connection name.
    * @param ok                whether it was removed.
    * @param reason            null when {@code ok}; {@code HAS_DEPENDENCIES} (a model uses it and
    *                          {@code force} was not set), {@code DATASOURCE_LOST} (no such
    *                          connection) or {@code UNKNOWN} (the save was refused; see message).
    * @param dependencyMessage why it has dependents, when {@code HAS_DEPENDENCIES}.
    * @param message           the server's text for an {@code UNKNOWN} refusal.
    */
   public record Item(String name, boolean ok, String reason, String dependencyMessage,
                      String message)
   {
   }
}
