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
package inetsoft.web.security;

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation that describes a permission that is required to access a web handler method.
 * The annotated method may have one and only one parameter of the type
 * <tt>Principal</tt> annotated with {@link PermissionUser}. If there is no such
 * parameter, the user associated with the current HTTP request is used.
 *
 * @since 12.3
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiredPermission {
   ResourceType resourceType() default ResourceType.REPORT;

   /**
    * The name of the resource on which the permission must be granted. If not specified,
    * a parameter must be annotated with {@link PermissionPath} to indicate the resource.
    */
   String resource() default "";

   /**
    * The type of access that must be granted on the resource. The default value
    * is {@link ResourceAction#READ}.
    */
   ResourceAction[] actions() default ResourceAction.READ;

   /**
    * Gets the type of asset at the resource path obtained from the
    * {@link PermissionPath} annotated parameter. Defaults to
    * {@link AssetEntry.Type#VIEWSHEET}.
    */
   AssetEntry.Type type() default AssetEntry.Type.VIEWSHEET;

   /**
    * Gets the scope of the asset at the resource path obtained from the
    * {@link PermissionPath} annotated parameter. Defaults to
    * {@link AssetRepository#GLOBAL_SCOPE}.
    */
   int scope() default AssetRepository.GLOBAL_SCOPE;

   /**
    * Gets the owner of the asset at the resource path obtained from the
    * {@link PermissionPath} annotated parameter. If this is not specified and the value
    * of {@link #scope()} is {@link AssetRepository#USER_SCOPE}, then there must be
    * one and only one parameter of type <tt>String</tt> annotated with
    * {@link PermissionOwner}.
    */
   String owner() default "";
}
