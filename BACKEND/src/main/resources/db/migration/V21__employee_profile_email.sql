ALTER TABLE public.employee_profile
    ADD COLUMN IF NOT EXISTS emp_email VARCHAR(255);
