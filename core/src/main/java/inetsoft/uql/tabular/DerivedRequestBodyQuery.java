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
package inetsoft.uql.tabular;

/**
 * Marks a query whose request body is built only from properties that are themselves settable
 * through the query contract (for example GraphQL: the body is {@code {query, variables}} built
 * from {@code queryString} and {@code variables}). Such a POST query can be created by writing
 * those properties, so the contract's "derived body" refusal does not apply to it. The body
 * itself is not a settable property of the query.
 */
public interface DerivedRequestBodyQuery {
}
