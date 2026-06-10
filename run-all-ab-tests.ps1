$PG_CONTAINER = "appliner-internship-postgres-1"
$BASE_RESULTS = "ab-results"

$scenarios = @(
    @{ name = "ab1-full"; file = "docker-compose.ab1-full.yml"; label = "ALL FEATURES ON" },
    @{ name = "ab2-no-cqrs"; file = "docker-compose.ab2-no-cqrs.yml"; label = "NO CQRS" },
    @{ name = "ab3-no-pool"; file = "docker-compose.ab3-no-pool.yml"; label = "NO CONTEXT POOL" },
    @{ name = "ab4-no-backpressure"; file = "docker-compose.ab4-no-backpressure.yml"; label = "NO BACKPRESSURE" },
    @{ name = "ab5-baseline"; file = "docker-compose.ab5-baseline.yml"; label = "BASELINE (ALL OFF)" }
)

function Reset-Database {
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
}

Write-Host "============================================"
Write-Host " A/B Test Suite - All Scenarios"
Write-Host "============================================"

foreach ($scenario in $scenarios) {
    $resultDir = "$BASE_RESULTS\$($scenario.name)"
    New-Item -ItemType Directory -Path $resultDir -Force | Out-Null

    Write-Host ""
    Write-Host "============================================" -ForegroundColor Cyan
    Write-Host " SCENARIO: $($scenario.label)" -ForegroundColor Cyan
    Write-Host "============================================" -ForegroundColor Cyan

    Write-Host "Starting infrastructure..." -ForegroundColor Yellow
    docker-compose -f $($scenario.file) down 2>$null
    docker volume rm appliner-internship_postgres_data appliner-internship_postgres_replica_data appliner-internship_keycloak_data 2>$null
    docker-compose -f $($scenario.file) up -d --build 2>$null

    Write-Host "Waiting for routing to be ready..." -ForegroundColor Yellow
    $maxWait = 180
    $waited = 0
    while ($waited -lt $maxWait) {
        Start-Sleep -Seconds 5
        $waited += 5
        try {
            $resp = Invoke-WebRequest -Uri "http://localhost:8081/actuator/health" -TimeoutSec 3 -ErrorAction Stop
            if ($resp.StatusCode -eq 200) {
                Write-Host "  Routing ready after ${waited}s.              " -ForegroundColor Green
                break
            }
        } catch {}
        Write-Host "  Waiting... ($waited s)    " -NoNewline
        Write-Host "`r" -NoNewline
    }
    if ($waited -ge $maxWait) { Write-Host "  TIMEOUT after ${maxWait}s" -ForegroundColor Red }

    Reset-Database

    Write-Host ""
    Write-Host "  [READ] Starting findAll test..." -ForegroundColor Green
    k6 run --summary-export="$resultDir\read.json" load-test-findall-only.js 2>&1 | Out-Null

    Reset-Database

    Write-Host "  [WRITE] Starting write test..." -ForegroundColor Green
    k6 run --summary-export="$resultDir\write.json" load-test-write-only.js 2>&1 | Out-Null

    Reset-Database

    Write-Host "  [MIXED] Starting mixed test..." -ForegroundColor Green
    k6 run --summary-export="$resultDir\mixed.json" load-test-mixed.js 2>&1 | Out-Null

    Write-Host "  Results saved to $resultDir\" -ForegroundColor Green

    docker-compose -f $($scenario.file) down 2>$null
    docker volume rm appliner-internship_postgres_data appliner-internship_postgres_replica_data appliner-internship_keycloak_data 2>$null
}

Write-Host ""
Write-Host "============================================"
Write-Host " ALL SCENARIOS COMPLETE"
Write-Host " Results: $BASE_RESULTS\"
Write-Host "============================================"
