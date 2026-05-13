#!/bin/bash
set -e

echo "Configuring PostgreSQL for replication..."

#правило для репликации в pg_hba.conf
cat >> "${PGDATA}/pg_hba.conf" <<EOF

# Replication connections
host    replication     all             0.0.0.0/0               md5
EOF

echo "Replication configuration added to pg_hba.conf"
