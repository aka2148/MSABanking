package com.bank.product.dto.request;
import jakarta.validation.Valid; import jakarta.validation.constraints.NotEmpty; import java.util.List;
public class ValidateRoleConfigRequest{@NotEmpty @Valid private List<ProductRoleConfigRequest> configs;public List<ProductRoleConfigRequest> getConfigs(){return configs;}public void setConfigs(List<ProductRoleConfigRequest> v){configs=v;}}
