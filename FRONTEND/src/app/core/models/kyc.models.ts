export const KYC_FIELDS = ['dateOfBirth', 'gender', 'relationship', 'fatherOrSpouseName', 'occupation', 'countryOfOrigin', 'proofOfIdType', 'proofOfIdValue', 'idIssueDate'] as const;
export type KycField = typeof KYC_FIELDS[number];
export type KycDraft = Partial<Record<KycField, string>> & { confirmSavedProof?: boolean };
export interface KycProfile {
  kycStatus: 'PENDING' | 'VERIFIED' | 'REJECTED';
  cycleNumber: number;
  cycleStatus: 'OPEN' | 'COMPLETED';
  completionPercent: number;
  completedFields: Record<KycField, boolean>;
  values: Partial<Record<KycField, string | null>>;
  proofNumberMasked: string | null;
  editingAllowed: boolean;
  verifiedAt: string | null;
  reviewDecision?: {decision:'APPROVE'|'REJECT';reason:string|null;decidedAt:string}|null;
  canReapply?: boolean; reapplyAttemptsRemaining?: number;
  fieldErrors: Partial<Record<KycField, string>>;
  accountDetails: { fullName: string; username: string; userId?: number; customerNumber: string; email: string | null; mobile: string | null };
  document: { name: string; size: number; contentType: string } | null;
}
