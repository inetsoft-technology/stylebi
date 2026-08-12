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

/**
 * The outcome of creating or updating a tabular data source.
 *
 * <p>Deliberately the same shape as {@link WizDatabaseSaveResult}, reached differently. The JDBC
 * save path reports a business failure as a status string inside an HTTP 200; the tabular path
 * throws a {@code MessageException} carrying a Catalog-translated sentence. Either way the client
 * branches on a stable {@code reason} code and writes its own message — the server's wording never
 * reaches it.</p>
 *
 * @param ok     whether the data source was saved.
 * @param reason null when {@code ok}. Otherwise one of {@code DUPLICATE_NAME} (a data source
 *               already exists at that path), {@code INVALID_FOLDER} (the parent folder does not
 *               exist), {@code DATASOURCE_LOST} (the update found nothing at the given path — it
 *               vanished between load and save), or {@code UNKNOWN} — StyleBI refused for a reason
 *               this mapping does not recognize, most often an invalid name, and the original is
 *               logged verbatim rather than swallowed.
 * @param path   the saved data source's full repository path, which differs from the requested one
 *               when the connection was renamed. Null on failure.
 */
public record WizTabularSaveResult(boolean ok, String reason, String path) {
   public static WizTabularSaveResult ok(String path) {
      return new WizTabularSaveResult(true, null, path);
   }

   public static WizTabularSaveResult failed(String reason) {
      return new WizTabularSaveResult(false, reason, null);
   }

   public static final String DUPLICATE_NAME = "DUPLICATE_NAME";
   public static final String INVALID_FOLDER = "INVALID_FOLDER";
   public static final String DATASOURCE_LOST = "DATASOURCE_LOST";
   public static final String UNKNOWN = "UNKNOWN";
}
