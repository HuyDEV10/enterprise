CREATE TABLE demand_series_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id),
    warehouse_id UUID REFERENCES warehouses(id),
    source_dataset VARCHAR(50) NOT NULL,
    source_item_id VARCHAR(100) NOT NULL,
    source_location_id VARCHAR(100) NOT NULL,
    category_code VARCHAR(100),
    department_code VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_demand_series_source UNIQUE (source_dataset, source_item_id, source_location_id)
);

CREATE TABLE demand_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mapping_id UUID NOT NULL REFERENCES demand_series_mappings(id) ON DELETE CASCADE,
    demand_date DATE NOT NULL,
    quantity NUMERIC(15, 4) NOT NULL,
    sell_price NUMERIC(15, 4),
    source_day_key VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_demand_history_quantity_non_negative CHECK (quantity >= 0),
    CONSTRAINT ck_demand_history_sell_price_non_negative CHECK (sell_price IS NULL OR sell_price >= 0),
    CONSTRAINT uk_demand_history_mapping_date UNIQUE (mapping_id, demand_date)
);

CREATE INDEX idx_demand_series_product_id ON demand_series_mappings(product_id);
CREATE INDEX idx_demand_series_warehouse_id ON demand_series_mappings(warehouse_id);
CREATE INDEX idx_demand_series_active ON demand_series_mappings(active);
CREATE INDEX idx_demand_history_mapping_id ON demand_history(mapping_id);
CREATE INDEX idx_demand_history_date ON demand_history(demand_date);
