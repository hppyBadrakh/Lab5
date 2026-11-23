#!/usr/bin/env bash
# Live demo of the order API: 201, 200, 200, 404, 400, 400, 204, 204, 409, plus the OpenAPI endpoints.
# Run from Git Bash or any POSIX shell: bash scripts/demo.sh
set -euo pipefail
cd "$(dirname "$0")/.."

BASE_URL="${BASE_URL:-http://localhost:8080}"
API="$BASE_URL/api/v1/orders"
CUSTOMERS="$BASE_URL/api/v1/customers"
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
    printf '%s\n' "$response" | grep -iE '^(HTTP/|Location:|Content-Type:|[[{])' || true
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
step "3. GET the customer's orders: expect 200 with one summary" "$CUSTOMERS/CUST-1/orders"
step "4. GET unknown id: expect 404 ProblemDetail" "$API/does-not-exist"
step "5. POST invalid body: expect 400 ProblemDetail with invalidFields" -X POST "$API" -H "Content-Type: application/json" -d "$INVALID"
step "6. POST order over the 10,000 limit: expect 400 from the aggregate" -X POST "$API" -H "Content-Type: application/json" -d "$OVER_LIMIT"
step "7. PUT payment: expect 204 No Content" -X PUT "$ORDER_URL/payment"
step "8. PUT cancellation: expect 204 No Content" -X PUT "$ORDER_URL/cancellation"
step "9. PUT cancellation again: expect 409 ProblemDetail" -X PUT "$ORDER_URL/cancellation"
step "10. GET the order: expect 200, status CANCELLED" "$ORDER_URL"

printf '
=== 11. OpenAPI: expect 302 for /swagger-ui.html, 200 for the UI and /v3/api-docs
'
for path in /swagger-ui.html /swagger-ui/index.html /v3/api-docs; do
    printf '%s -> HTTP %s
' "$path" "$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL$path")"
done
if [ -f target/demo-app.log ]; then
    printf '
=== 12. Side-channel log lines (written by side-channel-* threads, not http-nio-*)
'
    grep -F '[Side-Channel' target/demo-app.log || true
fi

printf '\nDemo finished.\n'
