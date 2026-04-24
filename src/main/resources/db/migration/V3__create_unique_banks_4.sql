-- Neutralizado tras multi-tenant (V9-Z crea los UNIQUE compuestos por company_id).
-- Los UNIQUE globales (code/name/nit/swift/short_name/code_ach) ya no son validos:
-- dos empresas distintas pueden tener el mismo NIT/codigo/nombre de banco.
-- Ver V9-Z__multi_tenant_final_fixes.sql
SELECT 1;
