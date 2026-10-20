#!/bin/sh
# Creates a local certificate authority and a wildcard certificate for the showcases domain.
# Files that already exist are kept, so pointing SHOWCASES_CA_DIR at one directory lets every
# showcase share a single CA that only has to be trusted once. Delete showcases.crt to reissue.
set -eu

dir=${CERTS_DIR:-/certs}
domain=showcases.chipomho.local

if [ -f "$dir/ca.crt" ] && [ -f "$dir/showcases.crt" ] && [ -f "$dir/showcases.key" ]; then
    echo "Using the existing certificates in $dir"
    exit 0
fi

if [ -f "$dir/ca.crt" ] && [ ! -f "$dir/ca.key" ]; then
    echo "$dir/ca.crt exists without ca.key, so no new certificate can be signed with it" >&2
    exit 1
fi

command -v openssl >/dev/null 2>&1 || apk add --no-cache --quiet openssl

umask 077
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

if [ ! -f "$dir/ca.crt" ]; then
    cat > "$work/ca.ext" <<EOF
basicConstraints = critical, CA:TRUE, pathlen:0
keyUsage = critical, keyCertSign, cRLSign
subjectKeyIdentifier = hash
EOF
    openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$work/ca.key"
    openssl req -new -key "$work/ca.key" -subj "/CN=Showcases Local CA" -out "$work/ca.csr"
    openssl x509 -req -in "$work/ca.csr" -key "$work/ca.key" -days 3650 -sha256 \
        -set_serial "0x$(openssl rand -hex 16)" -extfile "$work/ca.ext" -out "$work/ca.crt"
    mv "$work/ca.key" "$dir/ca.key"
    mv "$work/ca.crt" "$dir/ca.crt"
    rm -f "$dir/showcases.crt" "$dir/showcases.key"
    echo "Created a new local CA in $dir/ca.crt"
fi

cat > "$work/leaf.ext" <<EOF
basicConstraints = critical, CA:FALSE
keyUsage = critical, digitalSignature
extendedKeyUsage = serverAuth
subjectAltName = DNS:*.$domain, DNS:$domain
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid
EOF
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$work/showcases.key"
openssl req -new -key "$work/showcases.key" -subj "/CN=*.$domain" -out "$work/leaf.csr"
openssl x509 -req -in "$work/leaf.csr" -CA "$dir/ca.crt" -CAkey "$dir/ca.key" -days 825 -sha256 \
    -set_serial "0x$(openssl rand -hex 16)" -extfile "$work/leaf.ext" -out "$work/leaf.crt"
cat "$work/leaf.crt" "$dir/ca.crt" > "$work/showcases.crt"
mv "$work/showcases.key" "$dir/showcases.key"
mv "$work/showcases.crt" "$dir/showcases.crt"
chmod 644 "$dir/ca.crt" "$dir/showcases.crt"
echo "Issued $dir/showcases.crt for *.$domain"
