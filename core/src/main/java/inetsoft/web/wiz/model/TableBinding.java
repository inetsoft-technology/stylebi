package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TableBinding implements BindingInfo {
   public List<SimpleFieldInfo> getDetails() {
      return details;
   }

   public void setDetails(List<SimpleFieldInfo> details) {
      this.details = details;
   }

   private List<SimpleFieldInfo> details;
}
