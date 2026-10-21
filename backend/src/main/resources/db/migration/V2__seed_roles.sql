INSERT INTO roles (name, description) VALUES
 ('ADMIN', 'System administrator'),('MANAGER', 'Business manager'),('STAFF', 'Operations staff'),('ANALYST', 'Risk analyst')
ON CONFLICT (name) DO NOTHING;