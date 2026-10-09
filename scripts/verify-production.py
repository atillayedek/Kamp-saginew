#!/usr/bin/env python3
"""Read-only release checks. Never print configuration values or response bodies."""
import json
import os
import re
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen


def https_env(name):
    value = os.environ.get(name, "").rstrip("/")
    parsed = urlparse(value)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username:
        raise ValueError(f"{name} must be a configured HTTPS URL")
    return value


def main():
    backend = https_env("SUPABASE_URL")
    site = https_env("WEBSITE_URL")
    key = os.environ.get("SUPABASE_ANON_KEY", "")
    if not key:
        raise ValueError("SUPABASE_ANON_KEY is required")
    failures = []
    # The endpoint returns 503 before authentication when its server-side
    # RTDN/Play configuration is missing. 404 also blocks an undeployed build.
    request = Request(f"{backend}/functions/v1/play-rtdn", data=b"{}",
                      headers={"apikey": key, "Content-Type": "application/json"})
    try:
        with urlopen(request, timeout=30) as response:
            status = response.status
    except HTTPError as error:
        status = error.code
        error.close()
    if status != 401:
        failures.append("play-rtdn: deployment/server configuration is not ready")
    else:
        print("PASS RTDN endpoint: configured and rejects unsigned requests")
    for path in ("gizlilik", "hesap-silme", "cocuk-guvenligi", "kullanim-kosullari"):
        try:
            with urlopen(f"{site}/{path}", timeout=30) as response:
                text = response.read(2_000_000).decode("utf-8")
                if response.status != 200 or urlparse(response.url).scheme != "https":
                    failures.append(f"{path}: not an accessible HTTPS page")
                elif re.search(r"TASLAK|AVUKAT ONAYI GEREKLİ|Web üzerinden silme henüz yapılandırılmadı", text):
                    failures.append(f"{path}: draft or unconfigured content")
                else:
                    print(f"PASS public page: {path}")
        except (HTTPError, URLError, TimeoutError):
            failures.append(f"{path}: HTTP check failed")
    request = Request(f"{backend}/rest/v1/rpc/list_legal_documents", data=b"{}", headers={
        "apikey": key, "Authorization": f"Bearer {key}", "Content-Type": "application/json",
    })
    with urlopen(request, timeout=30) as response:
        documents = json.load(response)
    required = {
        "aydinlatma_metni", "gizlilik_politikasi", "kullanim_kosullari", "topluluk_kurallari",
        "cocuk_guvenligi", "premium_on_bilgilendirme", "mesafeli_sozlesme",
        "saklama_imha_politikasi", "cerez_sdk_politikasi", "telif_politikasi", "basvuru_formu",
        "acik_riza_pazarlama_eposta", "acik_riza_pazarlama_bildirim",
    }
    actual = {d.get("doc_type") for d in documents}
    if required - actual:
        failures.append("Required active legal documents are missing")
    for doc_type in sorted(required & actual):
        request = Request(f"{backend}/rest/v1/rpc/get_legal_document",
                          data=json.dumps({"p_doc_type": doc_type, "p_version": None}).encode(),
                          headers={"apikey": key, "Authorization": f"Bearer {key}", "Content-Type": "application/json"})
        with urlopen(request, timeout=30) as response:
            rows = json.load(response)
        if not rows or re.search(r"TASLAK|AVUKAT ONAYI GEREKLİ|\[[A-ZÇĞİÖŞÜ_ ]{3,}\]", rows[0].get("content", "")):
            failures.append(f"{doc_type}: missing, draft or placeholder legal text")
        else:
            print(f"PASS active legal text: {doc_type}")
    for failure in failures:
        print(f"FAIL {failure}")
    return 1 if failures else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, HTTPError, URLError, TimeoutError, TypeError, KeyError):
        print("FAIL production verification could not finish; check configuration and connectivity")
        sys.exit(1)
