package com.bank.identitycustomer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class IdentityAdminController {
    private final JdbcTemplate jdbc;
    public IdentityAdminController(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @GetMapping("/users") @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public Page<UserView> users(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){int p=Math.max(0,page),s=Math.min(100,Math.max(1,size));long total=jdbc.queryForObject("SELECT COUNT(*) FROM users",Long.class);List<UserView> rows=jdbc.query("SELECT u.usr_user_id,c.ucr_username,u.usr_status,u.usr_user_type FROM users u JOIN user_credentials c ON c.ucr_user_id=u.usr_user_id ORDER BY u.usr_user_id LIMIT ? OFFSET ?",(rs,n)->view(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4)),s,(long)p*s);return new Page<>(rows,total,(total+s-1)/s,p,s);}

    @GetMapping("/users/{userId}") @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')") public UserView user(@PathVariable long userId){return findUser(userId);}

    @PatchMapping("/users/{userId}/status") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public UserView setUserStatus(@PathVariable long userId,@RequestBody StatusRequest request,Authentication auth){
        if(request.status==null||!List.of("ACTIVE","SUSPENDED","LOCKED").contains(request.status.toUpperCase()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Status must be ACTIVE, SUSPENDED, or LOCKED");
        String status=request.status.toUpperCase();
        if(!"ACTIVE".equals(status)&&isLastActiveAdmin(userId))throw new ResponseStatusException(HttpStatus.CONFLICT,"Cannot disable the last active administrator");
        jdbc.update("UPDATE users SET usr_status=? WHERE usr_user_id=?",status,userId);
        jdbc.update("UPDATE user_credentials SET ucr_token_version=ucr_token_version+1 WHERE ucr_user_id=?",userId);
        return findUser(userId);
    }

    @GetMapping("/roles") @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')") public List<RoleView> roles(){return jdbc.query("SELECT rol_role_id,rol_role_code,rol_role_name,rol_description FROM roles ORDER BY rol_role_id",(rs,n)->new RoleView(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4)));}
    @PostMapping("/roles") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public RoleView createRole(@Valid @RequestBody RoleRequest request){String code=normalizeRole(request.code);jdbc.update("INSERT INTO roles(rol_role_code,rol_role_name,rol_description) VALUES(?,?,?)",code,request.name.trim(),request.description);long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);return findRole(id);}
    @PutMapping("/roles/{roleId}") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public RoleView updateRole(@PathVariable long roleId,@Valid @RequestBody RoleRequest request){findRole(roleId);String code=normalizeRole(request.code);jdbc.update("UPDATE roles SET rol_role_code=?,rol_role_name=?,rol_description=? WHERE rol_role_id=?",code,request.name.trim(),request.description,roleId);return findRole(roleId);}
    @PostMapping("/users/{userId}/roles") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public void assignRole(@PathVariable long userId,@RequestParam long roleId){findUser(userId);RoleView role=findRole(roleId);if(!List.of("ROLE_CUSTOMER","ROLE_BANK_OFFICER","ROLE_ADMIN").contains(role.code))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported role");jdbc.update("INSERT IGNORE INTO user_roles(ur_user_id,ur_role_id) VALUES(?,?)",userId,roleId);jdbc.update("UPDATE user_credentials SET ucr_token_version=ucr_token_version+1 WHERE ucr_user_id=?",userId);}
    @GetMapping("/users/{userId}/roles") @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public List<String> userRoles(@PathVariable long userId){findUser(userId);return jdbc.queryForList("SELECT r.rol_role_code FROM roles r JOIN user_roles ur ON ur.ur_role_id=r.rol_role_id WHERE ur.ur_user_id=? ORDER BY r.rol_role_code",String.class,userId);}
    @DeleteMapping("/users/{userId}/roles/{roleId}") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public void removeRole(@PathVariable long userId,@PathVariable long roleId){findUser(userId);RoleView role=findRole(roleId);if("ROLE_ADMIN".equals(role.code)&&isLastActiveAdmin(userId))throw new ResponseStatusException(HttpStatus.CONFLICT,"Cannot remove the last active administrator");jdbc.update("DELETE FROM user_roles WHERE ur_user_id=? AND ur_role_id=?",userId,roleId);jdbc.update("UPDATE user_credentials SET ucr_token_version=ucr_token_version+1 WHERE ucr_user_id=?",userId);}

    @GetMapping("/branches") @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')") public List<BranchView> branches(){return jdbc.query("SELECT bra_branch_id,bra_branch_code,bra_branch_name,bra_city,bra_is_active FROM branches ORDER BY bra_branch_id",(rs,n)->branchView(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBoolean(5)));}
    @GetMapping("/branches/{id}") @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')") public BranchView branch(@PathVariable long id){return findBranch(id);}
    @PostMapping("/branches") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.CREATED)
    public BranchView createBranch(@Valid @RequestBody BranchRequest req){jdbc.update("INSERT INTO branches(bra_branch_code,bra_branch_name,bra_city,bra_is_active) VALUES(?,?,?,?)",req.code.trim(),req.name.trim(),req.city,req.active==null?true:req.active);return findBranch(jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class));}
    @PutMapping("/branches/{id}") @PreAuthorize("hasRole('ADMIN')")
    public BranchView updateBranch(@PathVariable long id,@Valid @RequestBody BranchRequest req){findBranch(id);jdbc.update("UPDATE branches SET bra_branch_code=?,bra_branch_name=?,bra_city=? WHERE bra_branch_id=?",req.code.trim(),req.name.trim(),req.city,id);return findBranch(id);}
    @PatchMapping("/branches/{id}/status") @PreAuthorize("hasRole('ADMIN')")
    public BranchView branchStatus(@PathVariable long id,@RequestBody StatusRequest req){findBranch(id);if(req.active==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"active is required");jdbc.update("UPDATE branches SET bra_is_active=? WHERE bra_branch_id=?",req.active,id);return findBranch(id);}

    @GetMapping("/employees") @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public List<EmployeeView> employees(@RequestParam(required=false) String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){int p=Math.max(0,page),s=Math.min(100,Math.max(1,size));String term=q==null?"":q.trim();return jdbc.query("SELECT emp_employee_profile_id,emp_user_id,emp_employee_code,emp_full_name,emp_home_branch_id,emp_designation,emp_date_of_joining,emp_date_of_leaving,emp_email FROM employee_profile WHERE ?='' OR emp_full_name LIKE ? OR emp_employee_code LIKE ? ORDER BY emp_employee_profile_id LIMIT ? OFFSET ?",(rs,n)->employee(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),(Long)rs.getObject(5),rs.getString(6),rs.getDate(7)==null?null:rs.getDate(7).toLocalDate(),rs.getDate(8)==null?null:rs.getDate(8).toLocalDate(),rs.getString(9)),term,"%"+term+"%","%"+term+"%",s,(long)p*s);}
    @PostMapping("/employees") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public EmployeeView createEmployee(@Valid @RequestBody EmployeeRequest req){if(req.userId==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"userId is required");findUser(req.userId);if(req.homeBranchId!=null)findBranch(req.homeBranchId);jdbc.update("INSERT INTO employee_profile(emp_user_id,emp_employee_code,emp_full_name,emp_email,emp_home_branch_id,emp_designation,emp_date_of_joining,emp_date_of_leaving) VALUES(?,?,?,?,?,?,?,?)",req.userId,req.employeeCode.trim(),req.fullName.trim(),req.email,req.homeBranchId,req.designation,req.dateOfJoining,req.dateOfLeaving);return findEmployee(jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class));}
    @GetMapping("/employees/{id}") @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')") public EmployeeView employee(@PathVariable long id){return findEmployee(id);}
    @GetMapping("/employees/search") @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public List<EmployeeView> searchEmployees(@RequestParam String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return employees(q,page,size);}
    @PutMapping("/employees/{id}") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public EmployeeView updateEmployee(@PathVariable long id,@Valid @RequestBody EmployeeRequest req){EmployeeView current=findEmployee(id);if(req.homeBranchId!=null)findBranch(req.homeBranchId);jdbc.update("UPDATE employee_profile SET emp_employee_code=?,emp_full_name=?,emp_email=?,emp_home_branch_id=?,emp_designation=?,emp_date_of_joining=?,emp_date_of_leaving=? WHERE emp_employee_profile_id=?",req.employeeCode.trim(),req.fullName.trim(),req.email,req.homeBranchId,req.designation,req.dateOfJoining,req.dateOfLeaving,id);return findEmployee(id);}

    @GetMapping("/customer-classification-types") @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public List<ClassificationView> classifications(){return jdbc.query("SELECT cct_type_id,cct_type_code,cct_type_name,cct_is_active,cct_validation_regex,cct_requires_photo FROM customer_classification_types ORDER BY cct_type_id",(rs,n)->new ClassificationView(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getBoolean(4),rs.getString(5),rs.getBoolean(6)));}
    @PostMapping("/customer-classification-types") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.CREATED)
    public ClassificationView createClassification(@Valid @RequestBody ClassificationRequest req){jdbc.update("INSERT INTO customer_classification_types(cct_type_code,cct_type_name,cct_is_active,cct_validation_regex,cct_requires_photo) VALUES(?,?,?,?,?)",req.code.trim(),req.name.trim(),req.active==null?true:req.active,req.validationRegex,req.requiresPhoto==null?false:req.requiresPhoto);return findClassification(jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class));}
    @PutMapping("/customer-classification-types/{id}") @PreAuthorize("hasRole('ADMIN')")
    public ClassificationView updateClassification(@PathVariable long id,@Valid @RequestBody ClassificationRequest req){findClassification(id);jdbc.update("UPDATE customer_classification_types SET cct_type_code=?,cct_type_name=?,cct_validation_regex=?,cct_requires_photo=? WHERE cct_type_id=?",req.code.trim(),req.name.trim(),req.validationRegex,req.requiresPhoto==null?false:req.requiresPhoto,id);return findClassification(id);}
    @PatchMapping("/customer-classification-types/{id}/status") @PreAuthorize("hasRole('ADMIN')")
    public ClassificationView classificationStatus(@PathVariable long id,@RequestBody StatusRequest req){findClassification(id);if(req.active==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"active is required");jdbc.update("UPDATE customer_classification_types SET cct_is_active=? WHERE cct_type_id=?",req.active,id);return findClassification(id);}

    private UserView findUser(long id){return jdbc.query("SELECT u.usr_user_id,c.ucr_username,u.usr_status,u.usr_user_type FROM users u JOIN user_credentials c ON c.ucr_user_id=u.usr_user_id WHERE u.usr_user_id=?",rs->{if(!rs.next())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found");return view(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4));},id);}
    private UserView view(long id,String username,String status,String type){List<String> roles=userRolesRaw(id);return new UserView(id,username,status,type,roles);}
    private List<String> userRolesRaw(long id){return jdbc.queryForList("SELECT r.rol_role_code FROM roles r JOIN user_roles ur ON ur.ur_role_id=r.rol_role_id WHERE ur.ur_user_id=? ORDER BY r.rol_role_code",String.class,id);}
    private RoleView findRole(long id){return jdbc.query("SELECT rol_role_id,rol_role_code,rol_role_name,rol_description FROM roles WHERE rol_role_id=?",rs->{if(!rs.next())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Role not found");return new RoleView(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4));},id);}
    private BranchView findBranch(long id){return jdbc.query("SELECT bra_branch_id,bra_branch_code,bra_branch_name,bra_city,bra_is_active FROM branches WHERE bra_branch_id=?",rs->{if(!rs.next())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Branch not found");return branchView(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBoolean(5));},id);}
    private EmployeeView findEmployee(long id){return jdbc.query("SELECT emp_employee_profile_id,emp_user_id,emp_employee_code,emp_full_name,emp_home_branch_id,emp_designation,emp_date_of_joining,emp_date_of_leaving,emp_email FROM employee_profile WHERE emp_employee_profile_id=?",rs->{if(!rs.next())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Employee not found");return employee(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),(Long)rs.getObject(5),rs.getString(6),rs.getDate(7)==null?null:rs.getDate(7).toLocalDate(),rs.getDate(8)==null?null:rs.getDate(8).toLocalDate(),rs.getString(9));},id);}
    private ClassificationView findClassification(long id){return jdbc.query("SELECT cct_type_id,cct_type_code,cct_type_name,cct_is_active,cct_validation_regex,cct_requires_photo FROM customer_classification_types WHERE cct_type_id=?",rs->{if(!rs.next())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Classification type not found");return new ClassificationView(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getBoolean(4),rs.getString(5),rs.getBoolean(6));},id);}
    private BranchView branchView(long id,String code,String name,String city,boolean active){return new BranchView(id,code,name,city,active);}
    private EmployeeView employee(long id,long userId,String code,String name,Long branch,String designation,LocalDate join,LocalDate leave,String email){return new EmployeeView(id,userId,code,name,branch,designation,join,leave,email);}
    private boolean isLastActiveAdmin(long userId){Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM user_roles ur JOIN roles r ON r.rol_role_id=ur.ur_role_id JOIN users u ON u.usr_user_id=ur.ur_user_id WHERE r.rol_role_code='ROLE_ADMIN' AND u.usr_status='ACTIVE'",Integer.class);return count!=null&&count<=1&&!jdbc.queryForList("SELECT ur_user_id FROM user_roles ur JOIN roles r ON r.rol_role_id=ur.ur_role_id WHERE ur.ur_user_id=? AND r.rol_role_code='ROLE_ADMIN'",Long.class,userId).isEmpty();}
    private String normalizeRole(String code){String normalized=code==null?"":code.trim().toUpperCase(java.util.Locale.ROOT);if(!normalized.matches("ROLE_[A-Z0-9_]{2,45}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Role code must use ROLE_ prefix and uppercase letters, numbers, or underscores");return normalized;}

    public record Page<T>(List<T> content,long totalElements,long totalPages,int number,int size){public int getNumberOfElements(){return content.size();}public boolean isEmpty(){return content.isEmpty();}}
    public record UserView(long id,String username,String status,String userType,List<String> roles){}
    public record RoleView(long id,String code,String name,String description){}
    public record BranchView(long id,String code,String name,String city,boolean active){}
    public record EmployeeView(long id,long userId,String employeeCode,String fullName,Long homeBranchId,String designation,LocalDate dateOfJoining,LocalDate dateOfLeaving,String email){}
    public record ClassificationView(long id,String code,String name,boolean active,String validationRegex,boolean requiresPhoto){}
    public record StatusRequest(String status,Boolean active){}
    public record RoleRequest(@NotBlank @Size(max=50) String code,@NotBlank @Size(max=255) String name,@Size(max=500) String description){}
    public record BranchRequest(@NotBlank @Size(max=50) String code,@NotBlank @Size(max=255) String name,String city,Boolean active){}
    public record EmployeeRequest(@NotBlank @Size(max=50) String employeeCode,@NotBlank @Size(max=255) String fullName,@Email String email,Long userId,Long homeBranchId,String designation,LocalDate dateOfJoining,LocalDate dateOfLeaving){}
    public record ClassificationRequest(@NotBlank @Size(max=50) String code,@NotBlank @Size(max=255) String name,Boolean active,@Size(max=500) String validationRegex,Boolean requiresPhoto){}
}
