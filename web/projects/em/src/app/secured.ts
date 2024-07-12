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
 * Meta-data used to build the component security tree entry for the annotated component.
 */
export interface SecuredDescriptor {
   /**
    * The route to the annotated component.
    */
   route: string;

   /**
    * The display name for the annotated component on the security actions page.
    */
   label: string;

   /**
    * The license components that are required for the annotated component to be displayed.
    */
   requiredLicenses?: string[];

   /**
    * The list of secured child sections that do not have a dedicated component implementation.
    */
   children?: SecuredDescriptor[];

   /**
    * Whether hide the view for multi tenancy
    */
   hiddenForMultiTenancy?: boolean
}

/**
 * Decorator used to associate security metadata with components. This decorator is not actually
 * used at runtime, but is used to statically build the component security tree during the build.
 */
export function Secured(descriptor: SecuredDescriptor) {
   // NO-OP
   return (target: any) => target;
}