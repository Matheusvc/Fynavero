#!/usr/bin/env sh
# Gera um único arquivo executável: backend/target/fynavero.jar (front-end embutido).
set -e
cd "$(dirname "$0")/frontend"
npm install
npm run build
cd ../backend
mvn -q package
echo "Pronto: java -jar backend/target/fynavero.jar  ->  http://localhost:8080"
