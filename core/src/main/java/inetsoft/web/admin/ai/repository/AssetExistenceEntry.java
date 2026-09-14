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
package inetsoft.web.admin.ai.repository;

/**
 * One entry in {@link RepositoryImportPlan#willOverwrite()}/{@link RepositoryImportPlan#willCreate()}.
 *
 * <p>{@code currentLastModifiedTime} is left {@code null}: {@code AssetRepository} exposes no
 * direct timestamp lookup by {@code AssetEntry} (verified: no such method on the interface), and
 * computing it precisely would require replicating {@code DeployService}'s own private,
 * per-asset-type {@code XAsset}-based lookup -- a disclosed simplification, not a silent gap: the
 * existence/non-existence distinction itself (which drives {@code acknowledgeOverwrite} gating
 * and is pinned into {@code planHash}) is unaffected by this field being absent.
 */
public record AssetExistenceEntry(String path, String label, Long currentLastModifiedTime) {
}
