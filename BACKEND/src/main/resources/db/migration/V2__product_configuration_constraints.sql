ALTER TABLE public.role_type
    ADD COLUMN IF NOT EXISTS rot_is_active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE public.attribute_definitions
    ADD CONSTRAINT uq_attribute_definition_category_key
    UNIQUE (ad_category_id, ad_attribute_key);

ALTER TABLE public.product_role_config
    ADD CONSTRAINT uq_product_role_config_product_role
    UNIQUE (prc_product_id, prc_role_type_id);

ALTER TABLE public.product_attribute_values
    ADD CONSTRAINT uq_product_attribute_value_product_attribute
    UNIQUE (pav_product_id, pav_attribute_def_id);
