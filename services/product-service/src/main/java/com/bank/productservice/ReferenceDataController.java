package com.bank.productservice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Locale;

@RestController
public class ReferenceDataController {
    private final JdbcTemplate jdbc;
    public ReferenceDataController(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @GetMapping("/api/v1/currencies")
    public List<Currency> currencies(@RequestParam(required=false) Boolean active){return jdbc.query("SELECT cur_currency_id,cur_iso_code,cur_iso_numeric_code,cur_currency_name,cur_currency_symbol,cur_decimal_places,cur_is_active FROM currency"+(Boolean.TRUE.equals(active)?" WHERE cur_is_active=1":"")+" ORDER BY cur_iso_code",(r,n)->new Currency(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getInt(6),r.getBoolean(7)));}
    @GetMapping("/api/v1/currencies/{id}") public Currency currency(@PathVariable long id){return oneCurrency(id);}
    @GetMapping("/api/v1/currencies/by-iso-code/{code}") public Currency currencyByCode(@PathVariable String code){return jdbc.query("SELECT cur_currency_id,cur_iso_code,cur_iso_numeric_code,cur_currency_name,cur_currency_symbol,cur_decimal_places,cur_is_active FROM currency WHERE UPPER(cur_iso_code)=UPPER(?)",r->r.next()?mapCurrency(r):null,code);}
    @PostMapping("/api/v1/currencies") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public Currency createCurrency(@Valid @RequestBody CurrencyRequest request){int scale=validatedScale(request.decimalPlaces);String code=request.isoCode.toUpperCase(Locale.ROOT);if(!code.matches("[A-Z]{3}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Currency ISO code must contain three letters");jdbc.update("INSERT INTO currency(cur_iso_code,cur_iso_numeric_code,cur_currency_name,cur_currency_symbol,cur_decimal_places,cur_is_active,cur_created_by) VALUES(?,?,?,?,?,?,?)",code,request.isoNumericCode,request.currencyName,request.currencySymbol,scale,request.active==null?true:request.active,"API");return currencyByCode(code);}
    @PutMapping("/api/v1/currencies/{id}") @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public Currency updateCurrency(@PathVariable long id,@Valid @RequestBody CurrencyRequest request){oneCurrency(id);jdbc.update("UPDATE currency SET cur_iso_code=?,cur_iso_numeric_code=?,cur_currency_name=?,cur_currency_symbol=?,cur_decimal_places=?,cur_is_active=?,cur_updated_by='API',cur_updated_date=UTC_TIMESTAMP(6) WHERE cur_currency_id=?",request.isoCode.toUpperCase(Locale.ROOT),request.isoNumericCode,request.currencyName,request.currencySymbol,validatedScale(request.decimalPlaces),request.active==null?true:request.active,id);return oneCurrency(id);}
    @DeleteMapping("/api/v1/currencies/{id}") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCurrency(@PathVariable long id){oneCurrency(id);try{jdbc.update("DELETE FROM currency WHERE cur_currency_id=?",id);}catch(org.springframework.dao.DataIntegrityViolationException ex){throw new ResponseStatusException(HttpStatus.CONFLICT,"Currency is referenced by product configuration");}}

    @GetMapping("/api/v1/product-categories") public List<Category> categories(){return jdbc.query("SELECT pc_category_id,pc_category_code,pc_category_name,pc_parent_category_id FROM product_category ORDER BY pc_category_name",(r,n)->new Category(r.getLong(1),r.getString(2),r.getString(3),(Long)r.getObject(4)));}
    @GetMapping("/api/v1/product-categories/{id}") public Category category(@PathVariable long id){return oneCategory(id);}
    @PostMapping("/api/v1/product-categories") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public Category createCategory(@Valid @RequestBody CategoryRequest request){if(request.parentCategoryId!=null)oneCategory(request.parentCategoryId);jdbc.update("INSERT INTO product_category(pc_category_code,pc_category_name,pc_parent_category_id,pc_created_by) VALUES(?,?,?,?)",request.categoryCode.toUpperCase(Locale.ROOT),request.categoryName,request.parentCategoryId,"API");Long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);return oneCategory(id);}
    @PutMapping("/api/v1/product-categories/{id}") @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public Category updateCategory(@PathVariable long id,@Valid @RequestBody CategoryRequest request){oneCategory(id);if(request.parentCategoryId!=null){if(request.parentCategoryId==id)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Category cannot be its own parent");oneCategory(request.parentCategoryId);}jdbc.update("UPDATE product_category SET pc_category_code=?,pc_category_name=?,pc_parent_category_id=?,pc_updated_by='API',pc_updated_date=UTC_TIMESTAMP(6) WHERE pc_category_id=?",request.categoryCode.toUpperCase(Locale.ROOT),request.categoryName,request.parentCategoryId,id);return oneCategory(id);}
    @DeleteMapping("/api/v1/product-categories/{id}") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable long id){oneCategory(id);try{jdbc.update("DELETE FROM product_category WHERE pc_category_id=?",id);}catch(org.springframework.dao.DataIntegrityViolationException ex){throw new ResponseStatusException(HttpStatus.CONFLICT,"Category is referenced by products or child categories");}}

    private Currency oneCurrency(long id){return jdbc.query("SELECT cur_currency_id,cur_iso_code,cur_iso_numeric_code,cur_currency_name,cur_currency_symbol,cur_decimal_places,cur_is_active FROM currency WHERE cur_currency_id=?",r->r.next()?mapCurrency(r):null,id);}
    private Currency mapCurrency(java.sql.ResultSet r)throws java.sql.SQLException{return new Currency(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getInt(6),r.getBoolean(7));}
    private Category oneCategory(long id){return jdbc.query("SELECT pc_category_id,pc_category_code,pc_category_name,pc_parent_category_id FROM product_category WHERE pc_category_id=?",r->r.next()?new Category(r.getLong(1),r.getString(2),r.getString(3),(Long)r.getObject(4)):null,id);}
    private int validatedScale(Integer value){if(value==null||value<0||value>8)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Currency decimal places must be between 0 and 8");return value;}
    public record Currency(Long currencyId,String isoCode,String isoNumericCode,String currencyName,String currencySymbol,Integer decimalPlaces,boolean active){}
    public record CurrencyRequest(@NotBlank String isoCode,String isoNumericCode,@NotBlank String currencyName,String currencySymbol,@NotNull @Min(0) @Max(8) Integer decimalPlaces,Boolean active){}
    public record Category(Long categoryId,String categoryCode,String categoryName,Long parentCategoryId){}
    public record CategoryRequest(@NotBlank String categoryCode,@NotBlank String categoryName,Long parentCategoryId){}
}
