#!/usr/bin/env bash
set -e # fail on non-zero exit
lsof -i :8080 | grep "java"
ENDPOINT=http://localhost:8080
KEY=$(uuidgen)
curl -f ${ENDPOINT}/register -X POST -H "Content-Type: application/json" -d '{ "apiKey": ${KEY}, "quota": 10, "timeLimit": 100 }'
echo "Test 1 pass"
curl -f ${ENDPOINT}/use -X GET -H "Content-Type: application/json" -H "X-Api-Key: ${KEY}"
echo "Test 2 pass"