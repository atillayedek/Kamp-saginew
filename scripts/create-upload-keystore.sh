#!/usr/bin/env bash
# Creates the Play upload keystore on YOUR computer and prints what goes into
# the GitHub repository secrets. The keystore and passwords never enter git.
#
#   bash scripts/create-upload-keystore.sh [output-dir]
#
# Keep the .jks file and both passwords safe (password manager + offline backup).
# With Play App Signing a lost upload key can be reset through Play support,
# but that takes days.
set -euo pipefail

out_dir="${1:-$HOME/kampusagi-keys}"
alias_name="kampusagi-upload"
keystore="$out_dir/kampusagi-upload.jks"

command -v keytool >/dev/null || { echo "keytool bulunamadı: JDK 17+ kur." >&2; exit 1; }
if [ -e "$keystore" ]; then
  echo "$keystore zaten var; üzerine yazılmadı." >&2
  exit 1
fi
mkdir -p "$out_dir"
chmod 700 "$out_dir"

read -r -s -p "Keystore şifresi (en az 8 karakter): " store_password; echo
read -r -s -p "Şifre tekrar: " store_password_again; echo
if [ "$store_password" != "$store_password_again" ] || [ "${#store_password}" -lt 8 ]; then
  echo "Şifreler eşleşmiyor ya da 8 karakterden kısa." >&2
  exit 1
fi
read -r -p "Ad soyad veya şirket adı (sertifikada görünür): " owner_name

# PKCS12 keystores use the store password for the key as well.
keytool -genkeypair -v \
  -keystore "$keystore" -storetype PKCS12 \
  -alias "$alias_name" -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$store_password" -keypass "$store_password" \
  -dname "CN=${owner_name}, O=KampusAgi, C=TR" >/dev/null
chmod 600 "$keystore"

base64_file="$out_dir/keystore.base64.txt"
base64 < "$keystore" | tr -d '\n' > "$base64_file"
chmod 600 "$base64_file"

cat <<INFO

Keystore oluşturuldu: $keystore

GitHub → Settings → Secrets and variables → Actions → New repository secret:
  KAMPUSAGI_KEYSTORE_BASE64  = $base64_file dosyasının içeriği
  KAMPUSAGI_KEYSTORE_PASSWORD = girdiğin şifre
  KAMPUSAGI_KEY_PASSWORD      = aynı şifre
  KAMPUSAGI_KEY_ALIAS         = $alias_name

Secret'ları ekledikten sonra $base64_file dosyasını sil. .jks dosyasını ve şifreyi yedekle.
INFO
