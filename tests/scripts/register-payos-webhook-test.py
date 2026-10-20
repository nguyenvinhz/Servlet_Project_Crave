#!/usr/bin/env python3
"""Exercise webhook registration with injected HTTP fixtures and fake keys only."""

import contextlib
import http.client
import importlib.util
import io
import json
from pathlib import Path
import socket
import ssl
import unittest
import unittest.mock
import urllib.error
import urllib.request


SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "register-payos-webhook.py"
SPEC = importlib.util.spec_from_file_location("register_payos_webhook", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

FAKE_ENVIRONMENT = {
    "PAYOS_CLIENT_ID": "fake-client-id",
    "PAYOS_API_KEY": "fake-api-key",
    "PAYOS_PUBLIC_BASE_URL": "http://15.134.39.86:8080/crave/",
}
CALLBACK = "http://15.134.39.86:8080/crave/api/payos/webhook"


class Response(io.BytesIO):
    def __init__(self, body, status=200):
        super().__init__(body)
        self.status = status
        self.read_sizes = []

    def getcode(self):
        return self.status

    def read(self, size=-1):
        self.read_sizes.append(size)
        return super().read(size)


def confirmed():
    return Response(json.dumps({"code": "00", "data": {"webhookUrl": CALLBACK}}).encode())


def network_failures():
    private_message = "fake-client-id fake-api-key private-response https://secret-user:secret-pass@example.com"
    return [
        (TimeoutError(private_message), "timeout"),
        (socket.gaierror(-2, private_message), "DNS lookup failed"),
        (ssl.CertificateError(private_message), "TLS certificate verification failed"),
        (ssl.SSLError(private_message), "TLS connection failed"),
        (ConnectionError(private_message), "connection failed"),
        (http.client.HTTPException(private_message), "HTTP protocol failure"),
        (http.client.IncompleteRead(private_message.encode(), 100), "HTTP protocol failure"),
        (OSError(private_message), "network failure"),
    ]


class FixtureOpener:
    def __init__(self, *results):
        self.results = list(results)
        self.requests = []

    def __call__(self, request, *, timeout):
        self.requests.append((request, timeout))
        result = self.results.pop(0)
        if isinstance(result, BaseException):
            raise result
        return result


class WebhookRegistrationTest(unittest.TestCase):
    def setUp(self):
        network_block = unittest.mock.patch.object(urllib.request.OpenerDirector, "open",
            side_effect=AssertionError("Live network calls are forbidden in webhook tests."))
        network_block.start()
        self.addCleanup(network_block.stop)

    def register(self, opener, environment=None):
        delays = []
        result = MODULE.register_webhook(
            FAKE_ENVIRONMENT if environment is None else environment, opener=opener, sleep=delays.append)
        return result, delays

    def assert_sanitized(self, message):
        for secret in ("fake-client-id", "fake-api-key", "private-response", "secret-user", "secret-pass"):
            self.assertNotIn(secret, message)
        self.assertNotIn("https://", message)
        self.assertNotIn("http://", message)

    def test_precise_request_and_bounded_response(self):
        response = confirmed()
        opener = FixtureOpener(response)
        callback, delays = self.register(opener)
        self.assertEqual(callback, CALLBACK)
        self.assertEqual(delays, [])
        self.assertEqual(len(opener.requests), 1)
        request, timeout = opener.requests[0]
        self.assertEqual(request.full_url, "https://api-merchant.payos.vn/confirm-webhook")
        self.assertEqual(request.get_method(), "POST")
        self.assertEqual(dict((key.lower(), value) for key, value in request.header_items()), {
            "x-client-id": "fake-client-id", "x-api-key": "fake-api-key",
            "content-type": "application/json", "accept": "application/json",
            "user-agent": "CraveWebhookRegistrar/1.0",
        })
        self.assertEqual(json.loads(request.data), {"webhookUrl": CALLBACK})
        self.assertEqual(timeout, 60)
        self.assertEqual(response.read_sizes, [MODULE.MAX_RESPONSE_BYTES + 1])

    def test_retries_429_then_500_only_three_attempts(self):
        opener = FixtureOpener(Response(b"private-response", 429), Response(b"private-response", 500), confirmed())
        callback, delays = self.register(opener)
        self.assertEqual(callback, CALLBACK)
        self.assertEqual(len(opener.requests), 3)
        self.assertEqual(delays, [1, 2])

    def test_http_error_is_retried_without_reading_or_logging_body(self):
        error = urllib.error.HTTPError("https://secret-user:secret-pass@example.com", 503,
            "private-response fake-api-key", {}, io.BytesIO(b"private-response"))
        opener = FixtureOpener(error, confirmed())
        _, delays = self.register(opener)
        self.assertEqual(delays, [1])
        self.assertTrue(error.fp.closed)

    def test_network_failure_is_bounded_and_sanitized(self):
        opener = FixtureOpener(*[urllib.error.URLError("fake-api-key private-response") for _ in range(3)])
        delays = []
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr), self.assertRaises(MODULE.RegistrationError) as error:
            MODULE.register_webhook(FAKE_ENVIRONMENT, opener=opener, sleep=delays.append)
        self.assertEqual(len(opener.requests), 3)
        self.assertEqual(delays, [1, 2])
        self.assertEqual(str(error.exception),
            "Could not contact payOS after 3 attempts. Last failure: network failure.")
        self.assertEqual(len(stderr.getvalue().splitlines()), 3)
        self.assert_sanitized(stderr.getvalue() + str(error.exception))

    def test_transport_categories_are_safe_for_direct_and_wrapped_errors(self):
        for failure, category in network_failures():
            for wrapped in (False, True):
                with self.subTest(category=category, wrapped=wrapped):
                    error = urllib.error.URLError(failure) if wrapped else failure
                    opener = FixtureOpener(error, error, error)
                    delays, stderr = [], io.StringIO()
                    with contextlib.redirect_stderr(stderr), self.assertRaises(MODULE.RegistrationError) as raised:
                        MODULE.register_webhook(FAKE_ENVIRONMENT, opener=opener, sleep=delays.append)
                    self.assertEqual(len(opener.requests), 3)
                    self.assertEqual(delays, [1, 2])
                    self.assertEqual(stderr.getvalue().splitlines(), [
                        "payOS request attempt " + str(attempt) + "/3 failed: " + category
                        + " (request timeout: 60s)." for attempt in (1, 2, 3)
                    ])
                    self.assertEqual(str(raised.exception),
                        "Could not contact payOS after 3 attempts. Last failure: " + category + ".")
                    self.assert_sanitized(stderr.getvalue() + str(raised.exception))

    def test_transport_failure_can_retry_successfully_without_leaking_details(self):
        for failure, category in network_failures():
            for wrapped in (False, True):
                with self.subTest(category=category, wrapped=wrapped):
                    error = urllib.error.URLError(failure) if wrapped else failure
                    opener = FixtureOpener(error, confirmed())
                    stderr = io.StringIO()
                    with contextlib.redirect_stderr(stderr):
                        callback, delays = self.register(opener)
                    self.assertEqual(callback, CALLBACK)
                    self.assertEqual(delays, [1])
                    self.assertEqual(len(opener.requests), 2)
                    self.assertIn("attempt 1/3 failed: " + category, stderr.getvalue())
                    self.assertEqual(len(stderr.getvalue().splitlines()), 1)
                    self.assert_sanitized(stderr.getvalue())

    def test_mixed_http_and_transport_failures_report_the_final_category(self):
        opener = FixtureOpener(Response(b"private-response", 503),
            urllib.error.URLError(socket.gaierror(-2, "fake-api-key private-response")),
            TimeoutError("https://secret-user:secret-pass@example.com"))
        delays, stderr = [], io.StringIO()
        with contextlib.redirect_stderr(stderr), self.assertRaises(MODULE.RegistrationError) as error:
            MODULE.register_webhook(FAKE_ENVIRONMENT, opener=opener, sleep=delays.append)
        self.assertEqual(len(opener.requests), 3)
        self.assertEqual(delays, [1, 2])
        self.assertEqual(stderr.getvalue().splitlines(), [
            "payOS request attempt 2/3 failed: DNS lookup failed (request timeout: 60s).",
            "payOS request attempt 3/3 failed: timeout (request timeout: 60s).",
        ])
        self.assertEqual(str(error.exception),
            "Could not contact payOS after 3 attempts. Last failure: timeout.")
        self.assert_sanitized(stderr.getvalue() + str(error.exception))

    def test_http_retry_failure_is_bounded_and_sanitized(self):
        opener = FixtureOpener(*[Response(b"fake-client-id private-response", 500) for _ in range(3)])
        delays = []
        with self.assertRaises(MODULE.RegistrationError) as error:
            MODULE.register_webhook(FAKE_ENVIRONMENT, opener=opener, sleep=delays.append)
        self.assertEqual(delays, [1, 2])
        self.assertEqual(len(opener.requests), 3)
        self.assertIn("HTTP 500", str(error.exception))
        self.assert_sanitized(str(error.exception))

    def test_nonretryable_http_errors_and_redirects_stop_immediately(self):
        for status in (301, 302, 400, 401, 403, 404, 422):
            with self.subTest(status=status):
                opener = FixtureOpener(Response(b"fake-api-key private-response", status))
                delays = []
                with self.assertRaises(MODULE.RegistrationError) as error:
                    MODULE.register_webhook(FAKE_ENVIRONMENT, opener=opener, sleep=delays.append)
                self.assertEqual(len(opener.requests), 1)
                self.assertEqual(delays, [])
                self.assert_sanitized(str(error.exception))

    def test_provider_redirect_handler_never_follows_target(self):
        handler = MODULE._NoRedirect()
        request = urllib.request.Request(MODULE.PROVIDER_ENDPOINT, headers={"x-api-key": "fake-api-key"})
        self.assertIsNone(handler.redirect_request(request, None, 302, "Found", {}, "https://other.example"))

    def test_invalid_json_schema_business_error_and_mismatched_url_do_not_retry(self):
        bodies = [
            b"private-response fake-api-key", b"\xff", b"null", b"[]",
            b'{"code":0,"data":{}}', b'{"code":"99","desc":"private-response fake-api-key"}',
            b'{"code":"00","data":null}', b'{"code":"00","data":{"webhookUrl":null}}',
            b'{"code":"00","data":{"webhookUrl":"https://secret-user:secret-pass@example.com"}}',
            b'{"code":"99","code":"00","data":{"webhookUrl":"private-response"}}',
            b'{"code":"00","data":{"webhookUrl":"private-response"},"value":NaN}',
            b"x" * (MODULE.MAX_RESPONSE_BYTES + 1),
        ]
        for body in bodies:
            with self.subTest(body_index=bodies.index(body)):
                opener = FixtureOpener(Response(body))
                delays = []
                with self.assertRaises(MODULE.RegistrationError) as error:
                    MODULE.register_webhook(FAKE_ENVIRONMENT, opener=opener, sleep=delays.append)
                self.assertEqual(len(opener.requests), 1)
                self.assertEqual(delays, [])
                self.assert_sanitized(str(error.exception))

    def test_missing_or_unsafe_credentials_fail_before_network(self):
        environments = [
            {}, {**FAKE_ENVIRONMENT, "PAYOS_CLIENT_ID": ""}, {**FAKE_ENVIRONMENT, "PAYOS_API_KEY": ""},
            {**FAKE_ENVIRONMENT, "PAYOS_API_KEY": "fake-api-key\r\nx-test: private-response"},
        ]
        for environment in environments:
            with self.subTest(keys=list(environment)):
                opener = FixtureOpener()
                with self.assertRaises(MODULE.RegistrationError) as error:
                    self.register(opener, environment)
                self.assertEqual(opener.requests, [])
                self.assert_sanitized(str(error.exception))

    def test_invalid_base_urls_fail_before_network_and_are_not_printed(self):
        urls = [
            "", "http://", "//example.com/crave", "ftp://example.com/crave", "http://example.com//",
            "http://example.com/not-crave", "http://example.com/crave?token=fake-api-key", "http://example.com/crave#private-response",
            "http://secret-user:secret-pass@example.com/crave", "http://@example.com/crave", "http://localhost:8080/crave",
            "http://localhost.:8080/crave", "http://app.localhost/crave", "http://127.0.0.1/crave", "http://[::1]/crave",
            "http://10.0.0.1/crave", "http://0.0.0.0/crave", "http://example.com:0/crave", "http://example.com:70000/crave",
            "http://127.1/crave", "http://0177.0.0.1/crave", "http://0x7f.0.0.1/crave", "http://2130706433/crave",
            "http://example.com/crave\n", "http://exa mple.com/crave", "http://example.com/%63rave", "http://example.com/crave?",
        ]
        for url in urls:
            with self.subTest(url_index=urls.index(url)):
                opener = FixtureOpener()
                with self.assertRaises(MODULE.RegistrationError) as error:
                    self.register(opener, {**FAKE_ENVIRONMENT, "PAYOS_PUBLIC_BASE_URL": url})
                self.assertEqual(opener.requests, [])
                self.assert_sanitized(str(error.exception))

    def test_accepts_public_http_and_https_base_urls(self):
        for base in ("http://15.134.39.86:8080/crave", "http://15.134.39.86:8080/crave/", "https://crave.example.com/crave",
                     "https://craveshop.duckdns.org", "https://craveshop.duckdns.org/", "http://15.134.39.86:8080"):
            with self.subTest(base=base):
                self.assertEqual(MODULE.webhook_url(base), base.rstrip("/") + "/api/payos/webhook")

    def test_registers_root_context_callback_without_crave_prefix(self):
        callback = "https://craveshop.duckdns.org/api/payos/webhook"
        for base in ("https://craveshop.duckdns.org", "https://craveshop.duckdns.org/"):
            with self.subTest(base=base):
                opener = FixtureOpener(Response(json.dumps({"code": "00", "data": {"webhookUrl": callback}}).encode()))
                actual, delays = self.register(opener, {**FAKE_ENVIRONMENT, "PAYOS_PUBLIC_BASE_URL": base})
                self.assertEqual(actual, callback)
                self.assertEqual(delays, [])
                self.assertEqual(len(opener.requests), 1)
                self.assertEqual(json.loads(opener.requests[0][0].data), {"webhookUrl": callback})

    def test_cli_failure_and_success_output_are_sanitized(self):
        for error, expected in ((MODULE.RegistrationError("payOS rejected webhook registration."), 1), (None, 0)):
            with self.subTest(result=expected):
                stdout, stderr = io.StringIO(), io.StringIO()
                with unittest.mock.patch.object(MODULE, "register_webhook", side_effect=error), \
                        contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
                    self.assertEqual(MODULE.main(), expected)
                self.assert_sanitized(stdout.getvalue() + stderr.getvalue())
                self.assertEqual(bool(stderr.getvalue()), expected == 1)


if __name__ == "__main__":
    unittest.main(verbosity=2)
