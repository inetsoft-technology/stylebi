/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * ComponentPermissions encapsulates the permission for all child components of a given component.
 */
export interface ComponentPermissions {
   /**
    * The component permissions. This is a map of component names to a boolean indicating if the
    * current user is authorized to access the named component (true) or is not authorized (false).
    */
   permissions: {[name: string]: boolean};
   /**
    * The component labels. This is a map of component names to a string indicating the label of
    * the named component.
    */
   labels: {[name: string]: string};
   /**
    * Hide components for multi tenancy.
    */
   multiTenancyHiddenComponents: {[name: string]: boolean}
}

/**
 * Structure that encapsulates the path to a parent component and the name of a child of that
 * component.
 */
export interface ComponentPaths {
   /**
    * The path to the parent component.
    */
   parent: string;

   /**
    * The child component name.
    */
   child: string;
}