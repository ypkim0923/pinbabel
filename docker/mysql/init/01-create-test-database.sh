#!/usr/bin/env bash
set -euo pipefail

MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --protocol=socket --user=root <<'SQL'
CREATE DATABASE IF NOT EXISTS pinbabel_test
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
GRANT ALL PRIVILEGES ON pinbabel_test.* TO 'pinbabel'@'%';
SQL
