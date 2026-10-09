package com.bank.product.dto.request;
import jakarta.validation.constraints.*;
public class CreateAttributeDefinitionRequest{
 @NotNull(message="categoryId is required") @Positive private Integer categoryId; @NotBlank(message="attributeKey is required") @Size(max=100) private String attributeKey; @NotBlank(message="dataType is required") @Size(max=30) private String dataType; @Size(max=255) private String displayName; private Boolean required=false;
 public Integer getCategoryId(){return categoryId;}public void setCategoryId(Integer v){categoryId=v;}public String getAttributeKey(){return attributeKey;}public void setAttributeKey(String v){attributeKey=v;}public String getDataType(){return dataType;}public void setDataType(String v){dataType=v;}public String getDisplayName(){return displayName;}public void setDisplayName(String v){displayName=v;}public Boolean getRequired(){return required;}public void setRequired(Boolean v){required=v;}
}
