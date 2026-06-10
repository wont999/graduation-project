$PG_CONTAINER = "appliner-internship-postgres-1"
$resultsDir = "results"
New-Item -ItemType Directory -Path $resultsDir -Force | Out-Null

Write-Host "========================================"
Write-Host " A/B Test Suite - Feature Toggle"
Write-Host "========================================"
Write-Host "Results will be saved to $resultsDir/"

function Reset-Database {
    Write-Host "Recreating tables..." -ForegroundColor Yellow
    $seedFile = [System.IO.Path]::GetTempFileName()
    @'
DROP SCHEMA IF EXISTS tenant_appliner CASCADE;
CREATE SCHEMA tenant_appliner;

CREATE TABLE tenant_appliner.products (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    price DECIMAL(10,2),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE tenant_appliner.orders (
    id SERIAL PRIMARY KEY,
    user_id VARCHAR(255),
    total DECIMAL(10,2),
    status VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE tenant_appliner.order_items (
    id SERIAL PRIMARY KEY,
    order_id INTEGER,
    product_id INTEGER,
    quantity INTEGER,
    price DECIMAL(10,2),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

INSERT INTO tenant_appliner.products (name, price)
SELECT 'product-' || i, (random() * 1000 + 1)::numeric(10,2)
FROM generate_series(1, 10000) AS i;

INSERT INTO tenant_appliner.orders (user_id, total, status)
SELECT 'user-' || (i % 100), (random() * 5000 + 10)::numeric(10,2),
       CASE (i % 3) WHEN 0 THEN 'pending' WHEN 1 THEN 'completed' ELSE 'shipped' END
FROM generate_series(1, 10000) AS i;

INSERT INTO tenant_appliner.order_items (order_id, product_id, quantity, price)
SELECT (random() * 9999 + 1)::int, (random() * 9999 + 1)::int,
       (random() * 10 + 1)::int, (random() * 500 + 1)::numeric(10,2)
FROM generate_series(1, 50000) AS i;
'@ | Set-Content -Path $seedFile -Encoding UTF8
    docker cp $seedFile "${PG_CONTAINER}:/tmp/seed.sql" 2>$null
    docker exec -i $PG_CONTAINER psql -U blockly -d blockly -f /tmp/seed.sql 2>$null
    Remove-Item $seedFile -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    Write-Host "Tables recreated: 10000 products, 10000 orders, 50000 order_items" -ForegroundColor Green
}

# Initial seed
Reset-Database

Write-Host ""
Write-Host "===== READ TEST =====" -ForegroundColor Green
k6 run --summary-export="$resultsDir\read.json" load-test-findall-only.js

Reset-Database

Write-Host ""
Write-Host "===== WRITE TEST =====" -ForegroundColor Green
k6 run --summary-export="$resultsDir\write.json" load-test-write-only.js

Reset-Database

Write-Host ""
Write-Host "===== MIXED TEST =====" -ForegroundColor Green
k6 run --summary-export="$resultsDir\mixed.json" load-test-mixed.js

Write-Host ""
Write-Host "========================================"
Write-Host " All tests complete"
Write-Host "========================================"
