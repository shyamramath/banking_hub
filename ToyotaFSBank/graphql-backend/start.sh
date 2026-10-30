#!/usr/bin/env bash
# Use the env variables in .env to start the server, same convention as plaid/quickstart and backend/.
set -e
cd "$(dirname "$0")"
env $(cat .env | grep -v "#" | xargs) ./mvnw spring-boot:run
