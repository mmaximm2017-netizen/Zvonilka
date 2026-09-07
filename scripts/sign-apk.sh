#!/usr/bin/env bash
set -euo pipefail
# Private key and password file must remain outside this public repository.
: "${ZVONILKA_KEYSTORE:?Set the private PKCS12 path}"
: "${ZVONILKA_PASSWORD_FILE:?Set the private password file path}"
if [ "$#" -ne 3 ]; then echo "Usage: sign-apk.sh apksigner.jar input.apk output.apk" >&2; exit 2; fi
signer="$1"
input_apk="$2"
output_apk="$3"
root_dir="$(cd "$(dirname "$0")/.." && pwd)"
java -jar "$signer" sign --ks "$ZVONILKA_KEYSTORE" --ks-key-alias zvonilka --ks-pass "file:$ZVONILKA_PASSWORD_FILE" --key-pass "file:$ZVONILKA_PASSWORD_FILE" --out "$output_apk" "$input_apk"
verification="$(java -jar "$signer" verify --verbose --print-certs "$output_apk")"
expected="$(tr -d '[:space:]' < "$root_dir/signing-certificate.sha256")"
actual="$(echo "$verification" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | tr -d '[:space:]')"
if [ "$actual" != "$expected" ]; then echo "Signing certificate mismatch" >&2; exit 1; fi
echo "$verification"
