# Deferred Items

- The full backend suite currently fails in the pre-existing `FdV1ContractBoundaryTest.updateAccountOnlyExposesAutoRenewalAndCannotPatchStatus` assertion because the existing OpenAPI `UpdateAccountRequest` slice still contains `status`. This is outside Wave 1 and was not changed; the plan's focused schema/adapter tests and compile pass.
