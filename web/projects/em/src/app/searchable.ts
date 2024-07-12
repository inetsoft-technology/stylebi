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
 * Meta-data used to build the search index map for the annotated component.
 */
export interface SearchableDescriptor {
   /**
    * The route to the annotated component, including the anchor within the enclosing
    * page. For example, '/settings/general#license'.
    */
   route: string;

   /**
    * The component title for display in the search results. These should be
    * localization <b>key</b> strings. For example,
    *    title: "em.clusterMonitoring.title"
    */
   title: string;

   /**
    * Search keywords to be associated with the annotated component. These should be
    * localization <b>key</b> strings. For example,
    * [ 'em.license.keyword1', 'em.license.keyword2' ].
    */
   keywords: string[];
}

/**
 * Decorator used to associate search metadata with components. This decorator is not
 * actually used at runtime, but instead is used to statically build a search index map
 * during the build.
 */
export function Searchable(descriptor: SearchableDescriptor) {
   // NO-OP
   return (target: any) => target;
}
