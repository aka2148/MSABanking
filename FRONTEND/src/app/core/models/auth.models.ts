// ============================================================
// Auth models — field-to-schema mapping (per the "User Module - Renamed
// Field Schema" ER diagram)
//
// user_details (ud_): fullName, dateOfBirth, gender, countryOfOrigin,
//   preferredLanguage, fatherOrSpouseName, occupation.
//   NOTE: gender/countryOfOrigin/preferredLanguage are NOT shown as
//   columns in the diagram's user_details table. user_classification_types
//   exists but has no drawn relationship (no user_id line) to any other
//   table, so there's no confirmed path to store these there either.
//   Kept them on user_details (ud_ prefix, matching every other personal
//   attribute in that table) as the most defensible placement — confirm
//   with the schema owner.
//   ud_designation / ud_reporting_manager_id / ud_date_of_joining /
//   ud_date_of_leaving exist in the schema but are employee-context
//   fields (reporting manager, joining/leaving an organisation) — not
//   collected here since this is customer self-signup, not employee
//   onboarding. Relevant if this form is ever reused for staff creation.
//
// user_identity_proofs (uip_): proofOfIdType, proofOfIdValue, startDate,
//   documentRef (the diagram's real field for the uploaded file's
//   reference — previously we only had UI, no schema field for this).
//   proofValueMasked is backend-computed, not sent from here.
//
// user_credentials (ucr_): username, password (-> password_hash, hashed
//   server-side). credentialType is sent as a fixed 'PASSWORD' value —
//   the schema supports other credential types (future MFA/biometric)
//   but signup only ever creates a password credential.
//
// user_contacts (uco_): mobileNumber and email BOTH go through this table
//   now (contactType='MOBILE' / 'EMAIL') — users no longer has a direct
//   mobile column in this schema version.
//
// user_addresses (ua_): city, postalCode, addressType. NOTE: the diagram
//   doesn't show a street/line field, which seems like a real gap for a
//   usable address — added addressLine following the same ua_ convention.
//   Flag this one too; it's an addition, not something the diagram had.
//
// users (usr_): legalEntityType, homeBranchId. userType is sent as a
//   fixed 'CUSTOMER' value. customerNumber, employeeCode, kycStatus,
//   riskCategory, status are backend-assigned, not signup inputs.
//
// AUDIT: OOO_HOST_TS, LDBID, OOO_USER_ID (post-auth) are backend-derived.
// OOO_WS_ID (channel) and OOO_LOCAL_TS + UUID are the only pieces only
// the frontend can supply at request time — RequestContext carries them.
// Reuse buildContext() wherever money moves — FD creation, transactions.
// ============================================================

export type Channel = 'WEB' | 'MOBILE';

export interface RequestContext {
  channel: Channel;
  localTimestamp: string;
  requestId: string;
}

export type PreferredCurrency = 'INR' | 'USD' | 'JPY' | 'KWD';
export type LegalEntityType = 'INDIVIDUAL' | 'JOINT' | 'CORPORATE';

export interface LoginRequest {
  username: string; // backend's LoginRequest.java expects `username`, not `identifier`
  password: string;
  context: RequestContext;
}

export interface ForgotPasswordRequest {
  identifier: string;
}

export interface VerifyPasswordResetRequest {
  identifier: string;
  code: string;
}

export interface VerifyPasswordResetResponse {
  resetToken: string;
  expiresInSeconds: number;
}

export interface ResetPasswordRequest {
  resetToken: string;
  newPassword: string;
}
// Matches the backend's actual JwtResponse shape — no `success` field exists,
// so a 200 response IS the success signal. Failures come through as HTTP
// error status codes (401/403), caught in the service's error callback, not
// as `success: false` in the body.
export interface LoginResponse {
  success?: boolean;
  mfaRequired?: boolean;
  mfaToken?: string;
  email?: string;
  token?: string;
  role?: string;
  roles?: string[];
  username?: string;
  message?: string;
}

export interface AuthSession {
  authenticated: boolean;
  token: string | null;
  role: string | null;
}

export interface SignupRequest {
  // user_details
  fullName: string;
  preferredLanguage: string;
  // user_credentials
  username: string;
  password: string;
  credentialType: 'PASSWORD';
  // user_contacts
  mobileNumber: string;
  email: string;
  // user_addresses
  addressLine: string;
  state: string;
  city: string;
  postalCode: string;
  // users
  legalEntityType: LegalEntityType;
  homeBranchId: string;
  userType: 'CUSTOMER';
  // not yet in schema — forex placeholder
  preferredCurrency: PreferredCurrency;
  context: RequestContext;
}

export interface SignupResponse {
  success: boolean;
  message?: string;
}

export interface AuthMessageResponse { message: string; }
