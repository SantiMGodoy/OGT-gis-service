------------------------------------------------------------
-- TABLAS BASE DEL GIS SERVICE
------------------------------------------------------------

-- 1. Capas del Mapa (Configuración)
CREATE TABLE map_layers (
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY,
    code NVARCHAR(100) NOT NULL UNIQUE,
    name NVARCHAR(255) NOT NULL,
    type NVARCHAR(30), -- VECTOR, RASTER
    geometry_type NVARCHAR(50), -- POINT, POLYGON, LINESTRING
    business_target NVARCHAR(50), -- NONE, LIGHT_POINT_SERVICE, DISTRICTS
    srid INT, -- 31983, 31984, 31985, 4326
    attribute_mapping NVARCHAR(MAX), -- JSON
    source NVARCHAR(255),
    style NVARCHAR(MAX), -- JSON
    is_active BIT DEFAULT 1,
    z_index INT DEFAULT 0,
    created_at DATETIME2 DEFAULT SYSUTCDATETIME()
);

-- 2. Features Espaciales Genéricos
CREATE TABLE spatial_features (
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY,
    layer_id UNIQUEIDENTIFIER,
    external_id NVARCHAR(200),
    geom GEOMETRY,
    properties NVARCHAR(MAX), -- JSON
    created_at DATETIME2 DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_spatial_features_layer FOREIGN KEY (layer_id) REFERENCES map_layers(id)
);

-- 3. Límites de Distritos
CREATE TABLE district_boundaries (
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY,
    code NVARCHAR(100),
    name NVARCHAR(255),
    geom GEOMETRY,
    area FLOAT,
    metadata NVARCHAR(MAX), -- JSON
    created_at DATETIME2 DEFAULT SYSUTCDATETIME()
);

-- 4. Cuadrículas Estándar
CREATE TABLE standard_grids (
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY,
    scale NVARCHAR(20) NOT NULL, -- '1:5000', '1:10000'
    grid_code NVARCHAR(10) NOT NULL, -- 'A1', 'B2'
    geom GEOMETRY NOT NULL,
    light_points_count INT DEFAULT 0,
    last_updated DATETIME2,
    CONSTRAINT uk_standard_grids UNIQUE (scale, grid_code)
);

-- 5. Jobs de Importación
CREATE TABLE import_jobs (
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY,
    job_type NVARCHAR(50) NOT NULL, -- IMPORT_SHAPEFILE, IMPORT_KML
    status NVARCHAR(30) NOT NULL, -- PENDING, PROCESSING, COMPLETED, FAILED
    parameters NVARCHAR(MAX), -- JSON
    file_url NVARCHAR(1000),
    rows_processed INT,
    error_message NVARCHAR(MAX),
    created_at DATETIME2 DEFAULT SYSUTCDATETIME(),
    started_at DATETIME2,
    completed_at DATETIME2
);

-- 6. Jobs de Exportación
CREATE TABLE export_jobs (
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY,
    job_type NVARCHAR(50) NOT NULL, -- EXPORT_SHP, EXPORT_KML
    status NVARCHAR(30) NOT NULL, -- PENDING, PROCESSING, COMPLETED, FAILED
    parameters NVARCHAR(MAX), -- JSON
    file_url NVARCHAR(1000),
    rows_exported INT,
    error_message NVARCHAR(MAX),
    created_at DATETIME2 DEFAULT SYSUTCDATETIME(),
    started_at DATETIME2,
    completed_at DATETIME2
);

-- ================================================================
-- Índices espaciales (SQL Server - CON BOUNDING BOX)
-- ================================================================

-- Bounding Box para Brasil (aproximado):
-- Espírito Santo y regiones cercanas:
-- Longitud: -42.0 a -39.0 (Este-Oeste)
-- Latitud: -21.5 a -17.5 (Sur-Norte)
-- UTM 24S (EPSG:31984): X: 200000-900000, Y: 7600000-8100000

CREATE SPATIAL INDEX idx_spatial_features_geom
ON spatial_features(geom)
USING GEOMETRY_GRID
WITH (
    BOUNDING_BOX = (200000, 7600000, 900000, 8100000),
    GRIDS = (LEVEL_1 = MEDIUM, LEVEL_2 = MEDIUM, LEVEL_3 = MEDIUM, LEVEL_4 = MEDIUM)
);

CREATE SPATIAL INDEX idx_district_boundaries_geom
ON district_boundaries(geom)
USING GEOMETRY_GRID
WITH (
    BOUNDING_BOX = (200000, 7600000, 900000, 8100000),
    GRIDS = (LEVEL_1 = MEDIUM, LEVEL_2 = MEDIUM, LEVEL_3 = MEDIUM, LEVEL_4 = MEDIUM)
);

CREATE SPATIAL INDEX idx_standard_grids_geom
ON standard_grids(geom)
USING GEOMETRY_GRID
WITH (
    BOUNDING_BOX = (200000, 7600000, 900000, 8100000),
    GRIDS = (LEVEL_1 = MEDIUM, LEVEL_2 = MEDIUM, LEVEL_3 = MEDIUM, LEVEL_4 = MEDIUM)
);

-- Índices comunes
CREATE INDEX idx_map_layers_code ON map_layers(code);
CREATE INDEX idx_spatial_features_layer ON spatial_features(layer_id);
CREATE INDEX idx_import_jobs_status ON import_jobs(status);
CREATE INDEX idx_export_jobs_status ON export_jobs(status);
GO