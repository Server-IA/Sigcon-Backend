INSERT INTO modules (created_at, deleted_at, description, icon, name, "position", status, updated_at, url)
SELECT *
FROM (
    VALUES
    (now(),NULL::timestamp,'Gestión de parámetros del sistema','bx-cog','Parametrización',1,'ACTIVE',now(),'parametrizacion'),
    (now(),NULL::timestamp,'Gestión de listas contables','ri-list-ordered-2','Listas Contables',2,'ACTIVE',now(),'lists-accounting'),
    (now(),NULL::timestamp,'Gestion de activos del sistema','ri-todo-line','Activos',3,'ACTIVE',now(),'assets'),
    (now(),NULL::timestamp,'Gestion de terceros para el sistema','ri-team-line','Terceros',4,'ACTIVE',now(),'thirds'),
    (now(),NULL::timestamp,'Gestión de bancos y cajas','ri-bank-line','Bancos y Cajas',5,'ACTIVE',now(),'cash-and-banks')
) AS v(created_at, deleted_at, description, icon, name, "position", status, updated_at, url)
WHERE NOT EXISTS (
    SELECT 1 FROM modules m WHERE m.name = v.name
);