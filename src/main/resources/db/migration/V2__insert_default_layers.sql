------------------------------------------------------------
-- CAPAS POR DEFECTO DEL SISTEMA
------------------------------------------------------------

-- Capa para Puntos de Iluminación (rutea a light-point-service)
IF NOT EXISTS (SELECT 1 FROM map_layers WHERE code = 'LIGHT_POINTS')
BEGIN
    INSERT INTO map_layers (
        id, code, name, type, geometry_type, business_target,
        srid, source, is_active, z_index, created_at
    )
    VALUES (
        NEWID(),
        'LIGHT_POINTS',
        'Puntos de Iluminación Pública',
        'VECTOR',
        'POINT',
        'LIGHT_POINT_SERVICE',
        31984, -- SIRGAS 2000 UTM 24S (Espírito Santo)
        'KML_IMPORT',
        1,
        10,
        SYSUTCDATETIME()
    );
    PRINT '✅ Capa LIGHT_POINTS creada';
END
GO

-- Capa para Distritos/Barrios (manejo interno)
IF NOT EXISTS (SELECT 1 FROM map_layers WHERE code = 'DISTRICTS')
BEGIN
    INSERT INTO map_layers (
        id, code, name, type, geometry_type, business_target,
        srid, source, is_active, z_index, created_at
    )
    VALUES (
        NEWID(),
        'DISTRICTS',
        'Límites de Distritos',
        'VECTOR',
        'POLYGON',
        'DISTRICTS',
        31984,
        'SHAPEFILE',
        1,
        5,
        SYSUTCDATETIME()
    );
    PRINT '✅ Capa DISTRICTS creada';
END
GO

-- Capa para Zonas de Riesgo (solo referencia espacial)
IF NOT EXISTS (SELECT 1 FROM map_layers WHERE code = 'RISK_ZONES')
BEGIN
    INSERT INTO map_layers (
        id, code, name, type, geometry_type, business_target,
        srid, source, is_active, z_index, created_at
    )
    VALUES (
        NEWID(),
        'RISK_ZONES',
        'Zonas de Riesgo',
        'VECTOR',
        'POLYGON',
        'NONE',
        31984,
        'SHAPEFILE',
        1,
        8,
        SYSUTCDATETIME()
    );
    PRINT '✅ Capa RISK_ZONES creada';
END
GO