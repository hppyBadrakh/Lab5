#!/usr/bin/env bash
# Live demo of the order API: 201, 200, 404, 400, 400, 200, 200, 409.
# Run from Git Bash or any POSIX shell: bash scripts/demo.sh
set -euo pipefail
cd "$(dirname "$0")/.."

BASE_URL="${BASE_URL:-http://localhost:8080}"
API="$BASE_URL/api/v1/orders"
JAR="target/ecommerce-structures-1.0-SNAPSHOT.jar"
JAVA="java"
[ -n "${JAVA_HOME:-}" ] && JAVA="$JAVA_HOME/bin/java"
app_pid=""
response=""

stop_app() {
    if [ -n "$app_pid" ]; then
        kill "$app_pid" 2>/dev/null || true
    fi
}
trap stop_app EXIT

is_up() {
    curl -s -o /dev/null --max-time 2 "$BASE_URL/v3/api-docs"
}

step() {
    printf '\n=== %s\n' "$1"
    shift
    printf '$ curl %s\n\n' "$*"
    response=$(curl -s -i "$@" | tr -d '\r')
    printf '%s\n' "$response" | grep -iE '^(HTTP/|Location:|Content-Type:|\{)' || true
}

if is_up; then
    echo "Using the application already running at $BASE_URL"
else
    if [ ! -f "$JAR" ]; then
        echo "Building the jar..."
        ./mvnw -B -q -DskipTests package
    fi
    echo "Starting the application..."
    "$JAVA" -jar "$JAR" > target/demo-app.log 2>&1 &
    app_pid=$!
    for _ in $(seq 1 60); do
        is_up && break
        sleep 1
    done
    is_up || { echo "Application did not start; see target/demo-app.log" >&2; exit 1; }
fi

ADDRESS='"shippingAddress":{"street":"Peace Ave 1","city":"Ulaanbaatar","postalCode":"14200","countryCode":"MN"}'
BODY='{"customerId":"CUST-1",'"$ADDRESS"',"items":[{"sku":"ELEC-1001","unitPrice":25.50,"currency":"USD","quantity":2}]}'
OVER_LIMIT='{"customerId":"CUST-1",'"$ADDRESS"',"items":[{"sku":"ELEC-1001","unitPrice":5000.01,"currency":"USD","quantity":2}]}'
INVALID='{"customerId":" ",'"$ADDRESS"',"items":[{"sku":"p1","unitPrice":-1,"currency":"usd","quantity":0}]}'

step "1. POST valid order: expect 201 + Location, status PLACED" -X POST "$API" -H "Content-Type: application/json" -d "$BODY"

ORDER_URL=$(printf '%s\n' "$response" | sed -n 's/^[Ll]ocation: //p')

step "2. GET that order: expect 200" "$ORDER_URL"
step "3. GET unknown id: expect 404 ProblemDetail" "$API/does-not-exist"
step "4. POST invalid body: expect 400 ProblemDetail with invalidFields" -X POST "$API" -H "Content-Type: application/json" -d "$INVALID"
step "5. POST order over the 10,000 limit: expect 400 from the aggregate" -X POST "$API" -H "Content-Type: application/json" -d "$OVER_LIMIT"
step "6. Pay the order: expect 200, status PAID" -X POST "$ORDER_URL/pay"
step "7. Cancel the order: expect 200, status CANCELLED" -X POST "$ORDER_URL/cancel"
step "8. Cancel it again: expect 409 ProblemDetail" -X POST "$ORDER_URL/cancel"

printf '\nDemo finished.\n'
