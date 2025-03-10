package inetsoft.uql.rest.datasource.mailgun;

import inetsoft.uql.rest.json.AbstractEndpoint;
import inetsoft.uql.rest.pagination.PaginationType;

import java.util.Objects;

public class MailgunEndpoint extends AbstractEndpoint {
   public PaginationType getPageType() {
      return pageType;
   }

   public void setPageType(PaginationType type) {
      this.pageType = type;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      MailgunEndpoint that = (MailgunEndpoint) o;
      return pageType == that.pageType;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), pageType);
   }

   private PaginationType pageType;
}
