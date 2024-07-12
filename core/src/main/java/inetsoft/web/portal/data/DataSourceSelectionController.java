/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.web.portal.data;

import inetsoft.uql.*;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.factory.RemainingPath;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class DataSourceSelectionController {
   @GetMapping("api/portal/data/datasource-selection-view")
   public DatasourceSelectionViewModel getDataSourceSelectionViewModel(Principal principal) {
      final Catalog catalog = Catalog.getCatalog(principal);

      final List<DataSourceListingModel> listings = DataSourceListingService
         .getAllDataSourceListings(true)
         .stream()
         .map(listing -> DataSourceListingModel.builder()
            .name(listing.getDisplayName())
            .category(catalog.getString(listing.getCategory()))
            .iconUrl(listing.getIcon())
            .addKeywords(listing.getKeywords())
            .build())
         .sorted(Comparator.comparing(l -> l.name().toLowerCase()))
         .collect(Collectors.toList());

      final List<String> categories = listings.stream()
         .map(DataSourceListingModel::category)
         .map(catalog::getString)
         .distinct()
         .sorted()
         .collect(Collectors.toList());

      return DatasourceSelectionViewModel.builder()
         .listings(listings)
         .categories(categories)
         .build();
   }

   @GetMapping("api/portal/data/datasource-listing/is-tabular/**")
   public boolean isTabularDataSource(@RemainingPath String listingName) throws Exception {
      DataSourceListing listing = DataSourceListingService
         .getAllDataSourceListings(true)
         .stream()
         .filter(l -> listingName.equals(l.getDisplayName()))
         .findFirst()
         .orElse(null);

      return listing != null && listing.createDataSource() instanceof TabularDataSource;
   }

   @GetMapping("api/portal/data/datasource-listing/sourceType/**")
   public String getDataSourceType(@RemainingPath String listingName) throws Exception {
      DataSourceListing listing = DataSourceListingService
         .getAllDataSourceListings(true)
         .stream()
         .filter(l -> listingName.equals(l.getDisplayName()))
         .findFirst()
         .orElse(null);

      String type = null;

      if(listing != null) {
         XDataSource dataSource = listing.createDataSource();

         if(dataSource instanceof TabularDataSource) {
            type = DataSourceType.TABULAR.getValue();
         }
         else if(dataSource instanceof XMLADataSource) {
            type = DataSourceType.CUBE.getValue();
         }
         else {
            type = DataSourceType.JDBC.getValue();
         }
      }

      return type;
   }

   @GetMapping("images/portal/data/datasource-listing/icon/**")
   public ResponseEntity<Resource> getDatasourceListingIcon(@RemainingPath String listingName) {
      return DataSourceListingService.getAllDataSourceListings(true)
         .stream()
         .filter(l -> listingName.equals(l.getDisplayName()))
         .findFirst()
         .map(this::getIconResource)
         .map(this::getIconEntity)
         .orElse(ResponseEntity.notFound().build());
   }

   private Resource getIconResource(DataSourceListing listing) {
      return new ClassPathResource(listing.getIcon(), listing.getClass());
   }

   private ResponseEntity<Resource> getIconEntity(Resource resource) {
      HttpHeaders headers = new HttpHeaders();
      headers.set("Content-Type", "image/svg+xml");
      headers.set("Cache-Control", "public, max-age=2592000");
      return ResponseEntity.ok().headers(headers).body(resource);
   }
}
