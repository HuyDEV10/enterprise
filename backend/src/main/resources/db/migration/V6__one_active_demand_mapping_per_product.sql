-- Preserve historical mappings; keep the most recently updated active mapping.
WITH ranked AS (
    SELECT id, row_number() OVER (
        PARTITION BY product_id ORDER BY updated_at DESC, created_at DESC, id DESC
    ) AS position
    FROM demand_series_mappings WHERE active = TRUE
)
UPDATE demand_series_mappings m
SET active = FALSE, updated_at = now()
FROM ranked r WHERE m.id = r.id AND r.position > 1;

CREATE UNIQUE INDEX uk_active_demand_mapping_product
    ON demand_series_mappings(product_id) WHERE active = TRUE;
