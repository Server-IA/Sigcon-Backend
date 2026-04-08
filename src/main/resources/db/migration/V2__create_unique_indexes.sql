-- Parametrización

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email_active
ON users (email)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_username_active
ON users (username)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_roles_active
ON roles (name)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_permissions_active
ON permissions (code)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_parameters_active
ON parameters (name)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_parameters_active
ON user_parameters (user_id, parameter_id)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_modules_active
ON modules (url)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_menu_permissions_active
ON menu_permissions (menu_id, role_id)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_menus_active
ON menus (module_id, path)
WHERE deleted_at IS NULL;

-- Listas contables

CREATE UNIQUE INDEX IF NOT EXISTS uk_puc_code_active
ON cfg_chart_of_accounts (account_code)
WHERE deleted_at IS NULL;

DROP INDEX IF EXISTS uk_puc_name_active;
-- CREATE UNIQUE INDEX IF NOT EXISTS uk_puc_name_active
-- ON cfg_chart_of_accounts (account_name)
-- WHERE deleted_at IS NULL;

-- Centros de costo

CREATE UNIQUE INDEX IF NOT EXISTS uk_cost_center_code_company_active
ON cost_centers (code, company_id)
WHERE deleted_at IS NULL;

-- Reglas de depreciación

CREATE UNIQUE INDEX IF NOT EXISTS uk_depretation_rule_type_accounting_account_effective_date_acti
ON depretation_rules (depretation_type, accounting_account_id, effective_date)
WHERE deleted_at IS NULL;

-- Tipos de moneda
CREATE UNIQUE INDEX IF NOT EXISTS uk_currency_type_iso_code_active
ON cfg_currency_types (iso_code)
WHERE deleted_at IS NULL;

-- Cuentas contables
CREATE UNIQUE INDEX IF NOT EXISTS uk_accounting_account_custom_name_company_active
ON accounting_accounts (custom_name, company_id)
WHERE deleted_at IS NULL;


DROP INDEX IF EXISTS uk_ruler_tax_type_ruler_tax_name_company_active;
DROP INDEX IF EXISTS uk_accounting_account_ruler_tax_id_active;
