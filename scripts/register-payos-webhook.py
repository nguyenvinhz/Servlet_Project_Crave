#!/usr/bin/env python3
"""Register the deployed payOS webhook without logging credentials or response bodies."""

import http.client
import ipaddress
import json
import os
import re
import socket
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request


PROVIDER_ENDPOINT = "https://api-merchant.payos.vn/confirm-webhook"
# Confirmation includes a verification callback to the deployed application.
# Match the official payOS Python SDK's 60-second default for that roundtrip.
REQUEST_TIMEOUT = 60
MAX_ATTEMPTS = 3
MAX_RESPONSE_BYTES = 64 * 1024


class RegistrationError(Exception):
    """An error message safe to print in deployment logs."""


class _HttpStatusError(Exception):
    def __init__(self, status):
        self.status = status


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Never forward the merchant credentials to a redirect target.
        return None


def webhook_url(public_base_url):
    if not isinstance(public_base_url, str) or not public_base_url:
        raise RegistrationError("PAYOS_PUBLIC_BASE_URL is required.")
    if any(character.isspace() for character in public_base_url) or "?" in public_base_url or "#" in public_base_url:
        raise RegistrationError("PAYOS_PUBLIC_BASE_URL must not contain whitespace, a query, or a fragment.")
    try:
        parsed = urllib.parse.urlsplit(public_base_url)
        hostname = parsed.hostname
        port = parsed.port
    except ValueError:
        raise RegistrationError("PAYOS_PUBLIC_BASE_URL is invalid.") from None
    if (parsed.scheme not in ("http", "https") or not hostname
            or parsed.username is not None or parsed.password is not None
            or parsed.path not in ("", "/", "/crave", "/crave/") or port == 0):
        raise RegistrationError("PAYOS_PUBLIC_BASE_URL must be a public HTTP(S) URL for root or /crave with no userinfo.")
    hostname = hostname.rstrip(".").lower()
    if hostname == "localhost" or hostname.endswith((".localhost", ".local")):
        raise RegistrationError("PAYOS_PUBLIC_BASE_URL must be publicly reachable.")
    try:
        address = ipaddress.ip_address(hostname)
    except ValueError:
        if (len(hostname) > 253 or "." not in hostname
                or all(re.fullmatch(r"(?:[0-9]+|0x[0-9a-f]+)", label) for label in hostname.split("."))
                or not all(re.fullmatch(r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?", label)
                           for label in hostname.split("."))):
            raise RegistrationError("PAYOS_PUBLIC_BASE_URL has an invalid public host.") from None
    else:
        if not address.is_global or address.is_multicast:
            raise RegistrationError("PAYOS_PUBLIC_BASE_URL must be publicly reachable.")
    return public_base_url.rstrip("/") + "/api/payos/webhook"


def _credential(environment, name):
    value = environment.get(name, "")
    if not isinstance(value, str) or not value:
        raise RegistrationError(name + " is required.")
    if any(ord(character) < 33 or ord(character) > 126 for character in value):
        raise RegistrationError(name + " must be a valid HTTP header value.")
    return value


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON property")
        result[key] = value
    return result


def _reject_constant(value):
    raise ValueError("Non-finite JSON number")


def _confirm_response(response, expected_url):
    if len(response) > MAX_RESPONSE_BYTES:
        raise RegistrationError("payOS response exceeded the allowed size.")
    try:
        result = json.loads(response.decode("utf-8"), object_pairs_hook=_unique_object, parse_constant=_reject_constant)
    except (ValueError, UnicodeError, RecursionError):
        raise RegistrationError("payOS returned an invalid JSON response.") from None
    if not isinstance(result, dict) or not isinstance(result.get("code"), str):
        raise RegistrationError("payOS returned an invalid response schema.")
    if result["code"] != "00":
        raise RegistrationError("payOS rejected webhook registration.")
    data = result.get("data")
    if not isinstance(data, dict) or not isinstance(data.get("webhookUrl"), str):
        raise RegistrationError("payOS returned an invalid response schema.")
    if data["webhookUrl"] != expected_url:
        raise RegistrationError("payOS did not confirm the requested webhook URL.")


def _send(opener, request):
    try:
        with opener(request, timeout=REQUEST_TIMEOUT) as response:
            status = response.getcode()
            if not isinstance(status, int) or isinstance(status, bool) or not 100 <= status <= 599:
                raise RegistrationError("payOS returned an invalid HTTP status.")
            if not 200 <= status < 300:
                raise _HttpStatusError(status)
            return response.read(MAX_RESPONSE_BYTES + 1)
    except urllib.error.HTTPError as error:
        status = error.code
        error.close()
        raise _HttpStatusError(status) from None


def _network_failure_category(error):
    """Describe transport failures using only fixed labels, never exception text."""
    reason = error.reason if isinstance(error, urllib.error.URLError) else error
    if isinstance(reason, TimeoutError):
        return "timeout"
    if isinstance(reason, socket.gaierror):
        return "DNS lookup failed"
    if isinstance(reason, ssl.SSLCertVerificationError):
        return "TLS certificate verification failed"
    if isinstance(reason, ssl.SSLError):
        return "TLS connection failed"
    if isinstance(reason, ConnectionError):
        return "connection failed"
    if isinstance(reason, http.client.HTTPException):
        return "HTTP protocol failure"
    return "network failure"


def register_webhook(environment=None, *, opener=None, sleep=time.sleep):
    """Confirm a callback URL; injected opener/sleep let tests avoid all live calls."""
    environment = os.environ if environment is None else environment
    client_id = _credential(environment, "PAYOS_CLIENT_ID")
    api_key = _credential(environment, "PAYOS_API_KEY")
    callback = webhook_url(environment.get("PAYOS_PUBLIC_BASE_URL", ""))
    if opener is None:
        opener = urllib.request.build_opener(_NoRedirect()).open
    body = json.dumps({"webhookUrl": callback}, separators=(",", ":")).encode("utf-8")
    for attempt in range(1, MAX_ATTEMPTS + 1):
        request = urllib.request.Request(PROVIDER_ENDPOINT, data=body, method="POST", headers={
            "x-client-id": client_id, "x-api-key": api_key,
            "Content-Type": "application/json", "Accept": "application/json",
            # payOS blocks urllib's default User-Agent with Cloudflare error 1010.
            "User-Agent": "CraveWebhookRegistrar/1.0",
        })
        try:
            response = _send(opener, request)
        except _HttpStatusError as error:
            retryable = error.status == 429 or 500 <= error.status <= 599
            if not retryable or attempt == MAX_ATTEMPTS:
                raise RegistrationError("payOS webhook registration failed (HTTP " + str(error.status) + ").") from None
        except (urllib.error.URLError, OSError, http.client.HTTPException) as error:
            category = _network_failure_category(error)
            print("payOS request attempt " + str(attempt) + "/" + str(MAX_ATTEMPTS)
                  + " failed: " + category + " (request timeout: " + str(REQUEST_TIMEOUT) + "s).",
                  file=sys.stderr)
            if attempt == MAX_ATTEMPTS:
                raise RegistrationError("Could not contact payOS after " + str(MAX_ATTEMPTS)
                                        + " attempts. Last failure: " + category + ".") from None
        else:
            _confirm_response(response, callback)
            return callback
        sleep(attempt)
    raise RegistrationError("payOS webhook registration failed.")


def main():
    try:
        register_webhook()
    except RegistrationError as error:
        print(str(error), file=sys.stderr)
        return 1
    print("payOS webhook registered.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
