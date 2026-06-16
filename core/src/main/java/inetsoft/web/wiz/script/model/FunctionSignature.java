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
package inetsoft.web.wiz.script.model;

/**
 * A resolved entry from the Tern-format {@code js-functions.json} API metadata.
 *
 * @param name the fully-qualified name, e.g. {@code "formatDate"} or
 *             {@code "Number.toFixed"}
 * @param type the Tern {@code !type} string (e.g.
 *             {@code "fn(date: +Date, format_spec: string) -> +Date"}); may be
 *             {@code null} when no type signature is recorded
 * @param url  the {@code !url} help-page link; may be {@code null}
 */
public record FunctionSignature(String name, String type, String url) {}
