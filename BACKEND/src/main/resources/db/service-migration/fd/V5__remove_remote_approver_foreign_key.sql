-- Staff identities are owned by Identity Service and are not necessarily customers.
-- Keep the approver ID as an audit reference without coupling this service to the user snapshot table.
ALTER TABLE fd_premature_closure
    DROP FOREIGN KEY fd_premature_closure_fpc_approved_by_fkey;
