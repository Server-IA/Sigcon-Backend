INSERT INTO menus (component, created_at, deleted_at, icon, "label", menu_order, "path", status, updated_at, module_id, parent_id, visible)
SELECT *
FROM (
    VALUES
    ('PERFIL', now(),NULL::timestamp,'ri-user-line','Perfil',1,'perfil','ACTIVE',now(),1,NULL::bigint, TRUE),
    ('MODULOS', now(),NULL::timestamp,'ri-list-settings-fill','Modulos',2,'modules','ACTIVE',now(),1,NULL::bigint, TRUE),
    ('MENUS', now(),NULL::timestamp,'ri-play-list-add-line','Menus',3,'menus','ACTIVE',now(),1,NULL::bigint, TRUE),
    ('PERMISSIONS', now(),NULL::timestamp,'ri-menu-2-fill','Permisos',4,'permisos','ACTIVE',now(),1,NULL::bigint, TRUE),
    ('ROLES', now(),NULL::timestamp,'ri-menu-2-fill','Roles',5,'roles','ACTIVE',now(),1,NULL::bigint, TRUE),
    ('COMPANIES', now(),NULL::timestamp,'ri-building-line','Empresas',6,'companies','ACTIVE',now(),1,NULL::bigint, TRUE),
    ('USERS', now(),NULL::timestamp,'ri-group-linei','Usuarios',6,'users','ACTIVE',now(),1,NULL::bigint, TRUE),
    ('PARAMETROS', now(),NULL::timestamp,'','Parámetros',7,'parameters','ACTIVE',now(),1,NULL::bigint, TRUE),
    ('MENUSPERMISSIONS', now(),NULL::timestamp,'ri-list-settings-line','Permisos Menu',8,'menu-permissions','ACTIVE',now(),1,NULL::bigint, TRUE),
    
    ('PUC', now(),NULL::timestamp,'ri-list-check','Catalogo PUC',1,'puc-catalog','ACTIVE',now(),2,NULL::bigint, TRUE),
    ('DEPRECIATION_RULES', now(),NULL::timestamp,'ri-calculator-line','Reglas de Depreciación',2,'depreciation-rules','ACTIVE',now(),2,NULL::bigint, TRUE),
    ('CURRENCY_TYPES', now(),NULL::timestamp,'ri-money-dollar-circle-line','Tipos de Moneda',5,'currency-types','ACTIVE',now(),2,NULL::bigint, TRUE),
    ('ACT_CALCULO_DEPRECIACION', now(),NULL::timestamp,'ri-arrow-up-down-line','Calculo de depreciacion',2,'calculate-depreciation','ACTIVE',now(),3,NULL::bigint, TRUE),
    ('RULES_TAX', now(),NULL::timestamp,'ri-pencil-ruler-2-line','Reglas tributarias',6,'ruler-tax','ACTIVE',now(),2,NULL::bigint, TRUE),
    ('CENTROS_COSTO', now(),NULL::timestamp,'ri-building-line','Centros de Costo',3,'cost-centers','ACTIVE',now(),2,NULL::bigint, TRUE),
    ('EXCHANGE_RATE', now(),NULL::timestamp,'ri-exchange-dollar-line','Tasas de Cambio',4,'exchange-rates','ACTIVE',now(),2,NULL::bigint, TRUE),
    ('CUENTAS_CONTABLES', now(),NULL::timestamp,'ri-list-settings-fill','Cuentas Contables',6,'accounting-accounts','ACTIVE',now(),2,NULL::bigint, TRUE),
    
    ('ASSETS_REGISTRY', now(),NULL::timestamp,'ri-list-view','Lista de activos',1,'assets','ACTIVE',now(),3,NULL::bigint, TRUE),
    ('CREATE_ASSETS', now(),NULL::timestamp,'ri-list-view','Crear activo',2,'create','ACTIVE',now(),3,18, FALSE),
    ('UPDATE_ASSETS', now(),NULL::timestamp,'ri-list-view','Actualizar activo',3,'update/:id','ACTIVE',now(),3,18, FALSE),
    ('ACT_GENERACION_INFORMES', now(),NULL::timestamp,'ri-file-line','Reporte de activos',3,'report-assets','ACTIVE',now(),3,NULL::bigint, TRUE),
    ('NIIF_CORRECTION', now(),NULL::timestamp,'ri-list-view','Cumplimiento NIIF',4,'compliance-niif','ACTIVE',now(),3,NULL::bigint, TRUE),
    ('NIIF_VERIFICATION', now(),NULL::timestamp,'ri-list-check-3','Verificacion Niff',5,'verification-niff','ACTIVE',now(),3,NULL::bigint, TRUE),

    ('THIRD_PARTY_LIST', now(),NULL::timestamp,'ri-list-unordered','Lista de terceros',1,'list-thirds','ACTIVE',now(),4,NULL::bigint, TRUE),
    ('SEGMENTATION', now(),NULL::timestamp,'ri-profile-line','Segmentacion Terceros',2,'segmentation','ACTIVE',now(),4,NULL::bigint, TRUE),

    ('CASH_LIST', now(),NULL::timestamp,'ri-cash-line','Lista de cajas',1,'cash_list','ACTIVE',now(),5,NULL::bigint, TRUE),
    ('CHEQUERAS', now(),NULL::timestamp,'ri-folder-check-fill','Chequeras',2,'chequeras','ACTIVE',now(),5,NULL::bigint, TRUE),
    ('CHEQUES', now(),NULL::timestamp,'ri-folder-check-fill','Cheques',2,'cheqques','ACTIVE',now(),5,NULL::bigint, TRUE),
    ('CATALOGO_BANCOS', now(),NULL::timestamp,'ri-shield-check-fill','Bancos',3,'banks','ACTIVE',now(),5,NULL::bigint, TRUE),
    ('SUCURSALES_BANCARIAS', now(),NULL::timestamp,'ri-home-office-fill','Sucursales Bancos',4,'branches/:id','ACTIVE',now(),5,27, FALSE),
    ('BANK_ACCOUNTS', now(),NULL::timestamp,'ri-bank-card-line','Cuentas Bancarias',5,'bank-accounts','ACTIVE',now(),5,NULL::bigint, TRUE)

) AS v(component, created_at, deleted_at, icon, "label", menu_order, "path", status, updated_at, module_id, parent_id, visible)
WHERE NOT EXISTS (
    SELECT 1 
    FROM menus m 
    WHERE m.component = v.component 
      AND m.module_id = v.module_id
      AND m.path = v.path
);

UPDATE menus SET visible = TRUE where visible is null;