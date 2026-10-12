#!/usr/bin/env python3
"""Exercise PayOS production preparation without Docker, EC2 or provider traffic."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock


SCRIPT = Path(__file__).resolve().parents[1] / 'prepare-payos-deploy.py'
PROJECT = SCRIPT.parents[1]
SPEC = importlib.util.spec_from_file_location('prepare_payos_deploy', SCRIPT)
PREPARE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PREPARE)


class PayosPreparationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='crave-payos-test-')
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.production = self.directory / 'production'
        self.workspace = self.directory / 'checkout'
        self.production.mkdir()
        (self.workspace / 'database/migrations').mkdir(parents=True)
        self.env_file = self.production / '.env'
        self.env_file.write_text('EXISTING_SETTING=keep-me\nMYSQL_ROOT_PASSWORD=private-database-password\n', encoding='utf-8')
        self.original_env = self.env_file.read_bytes()
        self.compose_file = self.production / 'compose.yaml'
        self.compose_file.write_text('services: {}\n', encoding='utf-8')
        self.migration = self.workspace / 'database/migrations/2026-10-12-payos.sql'
        self.migration.write_bytes((PROJECT / 'database/migrations/2026-10-12-payos.sql').read_bytes())
        self.environment = {
            'DEPLOY_PATH': str(self.production),
            'GITHUB_WORKSPACE': str(self.workspace),
            'PAYOS_CLIENT_ID': 'fixture-client-secret',
            'PAYOS_API_KEY': 'fixture-api-secret',
            'PAYOS_CHECKSUM_KEY': 'fixture-checksum-secret',
            'PAYOS_PUBLIC_BASE_URL': 'https://kitchen.example/crave',
        }
        self.config = {'services': {
            'app': {
                'environment': {'DB_URL': 'jdbc:mysql://mysql:3306/tenant_orders?useUnicode=true',
                                'DB_PASSWORD': 'private-database-password'},
                'ports': [{'target': 8080, 'published': '18080', 'host_ip': '0.0.0.0'}],
            },
            'mysql': {'environment': {'MYSQL_ROOT_PASSWORD': 'private-database-password'}},
        }}
        self.calls = []
        self.stdout = io.StringIO()
        self.stderr = io.StringIO()

    def docker(self, command, **options):
        self.calls.append((command, options))
        self.assertTrue(options['check'])
        self.assertTrue(options['capture_output'])
        self.assertTrue(options['text'])
        if 'config' in command:
            return SimpleNamespace(stdout=json.dumps(self.config))
        return SimpleNamespace(stdout='')

    @contextlib.contextmanager
    def isolated(self, *, subprocess_effect=None):
        with mock.patch.dict(os.environ, self.environment, clear=True), \
                mock.patch.object(sys, 'argv', [str(SCRIPT)]), \
                mock.patch.object(PREPARE.subprocess, 'run', side_effect=subprocess_effect or self.docker), \
                mock.patch.object(PREPARE.shutil, 'which', return_value='docker'), \
                mock.patch.object(PREPARE.urllib.request, 'build_opener') as opener, \
                mock.patch.object(PREPARE.urllib.request, 'urlopen') as provider, \
                contextlib.redirect_stdout(self.stdout), contextlib.redirect_stderr(self.stderr):
            opener.side_effect = AssertionError('Unexpected metadata request')
            provider.side_effect = AssertionError('Unexpected PayOS request')
            yield opener, provider

    def assert_no_secrets_logged(self, extra=''):
        output = self.stdout.getvalue() + self.stderr.getvalue() + str(extra)
        for secret in ('fixture-client-secret', 'fixture-api-secret', 'fixture-checksum-secret',
                       'private-database-password'):
            self.assertNotIn(secret, output)

    def mutation_calls(self):
        return [(command, options) for command, options in self.calls if 'exec' in command]

    def test_prepares_private_files_and_additive_migration_for_configured_database(self):
        with self.isolated():
            PREPARE.main()
        self.assertEqual(self.original_env, self.env_file.read_bytes(), 'Existing production environment changed')
        private_env = self.production / '.payos.env'
        override = self.production / '.payos-compose.yaml'
        values = PREPARE.read_env(private_env)
        for key in ('PAYOS_CLIENT_ID', 'PAYOS_API_KEY', 'PAYOS_CHECKSUM_KEY', 'PAYOS_PUBLIC_BASE_URL'):
            self.assertEqual(self.environment[key], values[key])
        self.assertEqual({'services': {'app': {'environment': {
            key: '${' + key + '}' for key in values
        }}}}, json.loads(override.read_text(encoding='utf-8')))
        self.assertEqual(1, len(self.mutation_calls()))
        command, options = self.mutation_calls()[0]
        self.assertEqual('tenant_orders', command[-1])
        self.assertIn('--database="$1"', command[-3])
        sql = options['input']
        self.assertNotIn('USE crave;', sql)
        self.assertIn('ALTER TABLE payment ADD COLUMN payos_order_code', sql)
        self.assertIn('TABLE_SCHEMA = DATABASE()', sql)
        self.assertNotIn('DROP TABLE', sql.upper())
        self.assertNotIn('CREATE DATABASE', sql.upper())
        self.assertEqual(self.migration.read_text(encoding='utf-8').replace('USE crave;', ''), sql)
        self.assert_no_secrets_logged()
        # Windows only models the write permission bit; POSIX enforces all six bits.
        if os.name != 'nt':
            self.assertEqual(0o600, stat.S_IMODE(private_env.stat().st_mode))
            self.assertEqual(0o600, stat.S_IMODE(override.stat().st_mode))

    def test_reuses_existing_private_configuration_and_environment_overrides_it(self):
        prior = {key: value for key, value in self.environment.items() if key.startswith('PAYOS_')}
        prior['PAYOS_API_KEY'] = 'previous-private-api-key'
        (self.production / '.payos.env').write_text(''.join(
            key + '=' + json.dumps(value) + '\n' for key, value in prior.items()), encoding='utf-8')
        for key in ('PAYOS_CLIENT_ID', 'PAYOS_CHECKSUM_KEY', 'PAYOS_PUBLIC_BASE_URL'):
            self.environment.pop(key)
        with self.isolated():
            PREPARE.main()
        values = PREPARE.read_env(self.production / '.payos.env')
        self.assertEqual(prior['PAYOS_CLIENT_ID'], values['PAYOS_CLIENT_ID'])
        self.assertEqual('fixture-api-secret', values['PAYOS_API_KEY'])
        self.assert_no_secrets_logged()
        self.assertNotIn('previous-private-api-key', self.stdout.getvalue())

    def test_discovers_imdsv2_public_address_and_public_application_port(self):
        self.environment.pop('PAYOS_PUBLIC_BASE_URL')
        metadata = mock.Mock()
        token_response = mock.MagicMock()
        token_response.__enter__.return_value.read.return_value = b'private-imds-token'
        address_response = mock.MagicMock()
        address_response.__enter__.return_value.read.return_value = b'8.8.8.8'
        metadata.open.side_effect = [token_response, address_response]
        # The target 8080 bound publicly wins over another target or loopback port.
        self.config['services']['app']['ports'] = [
            {'target': 8080, 'published': 9090, 'host_ip': '127.0.0.1'},
            {'target': 9000, 'published': 9000, 'host_ip': '0.0.0.0'},
            {'target': 8080, 'published': 18080, 'host_ip': '0.0.0.0'},
        ]
        with self.isolated() as (opener, provider):
            opener.side_effect = None
            opener.return_value = metadata
            PREPARE.main()
            provider.assert_not_called()
        token_call, address_call = metadata.open.call_args_list
        self.assertEqual('PUT', token_call.args[0].method)
        self.assertEqual('http://169.254.169.254/latest/api/token', token_call.args[0].full_url)
        self.assertEqual('60', token_call.args[0].get_header('X-aws-ec2-metadata-token-ttl-seconds'))
        self.assertEqual('private-imds-token', address_call.args[0].get_header('X-aws-ec2-metadata-token'))
        self.assertEqual(3, token_call.kwargs['timeout'])
        self.assertEqual(3, address_call.kwargs['timeout'])
        self.assertEqual('http://8.8.8.8:18080/crave',
                         PREPARE.read_env(self.production / '.payos.env')['PAYOS_PUBLIC_BASE_URL'])
        self.assert_no_secrets_logged()
        self.assertNotIn('private-imds-token', self.stdout.getvalue())

    def test_missing_secrets_fail_before_sql_or_private_files(self):
        for key in ('PAYOS_CLIENT_ID', 'PAYOS_API_KEY', 'PAYOS_CHECKSUM_KEY'):
            with self.subTest(key=key):
                original = self.environment.pop(key)
                self.calls.clear()
                with self.isolated(), self.assertRaisesRegex(ValueError, 'three PayOS production secrets'):
                    PREPARE.main()
                self.assertFalse(self.mutation_calls())
                self.assertFalse((self.production / '.payos.env').exists())
                self.environment[key] = original
        self.assert_no_secrets_logged()

    def test_uses_compose_callback_base_without_metadata_lookup(self):
        self.environment.pop('PAYOS_PUBLIC_BASE_URL')
        self.config['services']['app']['environment']['PAYOS_PUBLIC_BASE_URL'] = 'https://configured.example/crave/'
        with self.isolated() as (opener, _):
            PREPARE.main()
            opener.assert_not_called()
        self.assertEqual('https://configured.example/crave',
                         PREPARE.read_env(self.production / '.payos.env')['PAYOS_PUBLIC_BASE_URL'])

    def test_discovery_rejects_private_address_and_missing_public_port(self):
        self.environment.pop('PAYOS_PUBLIC_BASE_URL')
        for address, ports, message in (
                ('127.0.0.1', [{'target': 8080, 'published': 8080}], 'no public IPv4 address'),
                ('8.8.8.8', [{'target': 8080, 'published': 8080, 'host_ip': '127.0.0.1'}], 'No public app port')):
            with self.subTest(address=address):
                self.config['services']['app']['ports'] = ports
                metadata = mock.Mock()
                responses = [mock.MagicMock(), mock.MagicMock()]
                responses[0].__enter__.return_value.read.return_value = b'private-imds-token'
                responses[1].__enter__.return_value.read.return_value = address.encode()
                metadata.open.side_effect = responses
                with self.isolated() as (opener, _):
                    opener.side_effect = None
                    opener.return_value = metadata
                    with self.assertRaisesRegex(ValueError, message):
                        PREPARE.main()
                self.assertFalse(self.mutation_calls())
                self.assertFalse((self.production / '.payos.env').exists())

    def test_rejects_newlines_and_compose_interpolation_in_secrets(self):
        for value in ('fixture-api-secret\nNEXT=oops', 'fixture-api-secret\r', '${EXFILTRATE}'):
            with self.subTest(value=value):
                self.environment['PAYOS_API_KEY'] = value
                with self.isolated(), self.assertRaisesRegex(ValueError, 'Invalid configuration value: PAYOS_API_KEY'):
                    PREPARE.main()
                self.assertFalse(self.mutation_calls())
        self.assert_no_secrets_logged()

    def test_rejects_checkout_deployment_and_compose_escape_before_docker(self):
        for root in (self.workspace, self.workspace / 'database'):
            with self.subTest(root=root):
                self.environment['DEPLOY_PATH'] = str(root)
                with self.isolated(), self.assertRaisesRegex(ValueError, 'outside the runner checkout'):
                    PREPARE.main()
                self.assertFalse(self.calls)
        self.environment['DEPLOY_PATH'] = str(self.production)
        external = self.directory / 'outside-compose.yaml'
        external.write_text('must remain untouched', encoding='utf-8')
        self.environment['COMPOSE_FILE'] = '../outside-compose.yaml'
        with self.isolated(), self.assertRaisesRegex(ValueError, 'inside the production directory'):
            PREPARE.main()
        self.assertFalse(self.calls)
        self.assertEqual('must remain untouched', external.read_text(encoding='utf-8'))

    def test_rejects_relative_production_and_absolute_compose_paths(self):
        self.environment['DEPLOY_PATH'] = 'relative-production'
        with self.isolated(), self.assertRaisesRegex(ValueError, 'absolute directory'):
            PREPARE.main()
        self.environment['DEPLOY_PATH'] = str(self.production)
        self.environment['COMPOSE_FILE'] = str(self.compose_file)
        with self.isolated(), self.assertRaisesRegex(ValueError, 'must be relative'):
            PREPARE.main()
        self.assertFalse(self.calls)

    def test_rejects_unsafe_private_destinations_before_database_or_other_files(self):
        for name in ('.payos.env', '.payos-compose.yaml'):
            with self.subTest(name=name):
                private = self.production / name
                private.mkdir()
                with self.isolated(), self.assertRaisesRegex(ValueError, 'must be regular files'):
                    PREPARE.main()
                self.assertFalse(self.calls)
                private.rmdir()
        original = Path.is_symlink
        for name in ('.payos.env', '.payos-compose.yaml'):
            with self.subTest(symlink=name):
                expected = (self.production / name).resolve()
                def symlink(path):
                    return path == expected or original(path)
                with self.isolated(), mock.patch.object(Path, 'is_symlink', symlink), \
                        self.assertRaisesRegex(ValueError, 'must be regular files'):
                    PREPARE.main()
                self.assertFalse(self.calls)
        self.assertFalse((self.production / '.payos.env').exists())

    def test_failed_atomic_replace_keeps_original_and_removes_secret_temp_file(self):
        file = self.production / '.payos.env'
        file.write_text('previous-private-value', encoding='utf-8')
        before = set(self.production.iterdir())
        with mock.patch.object(PREPARE.os, 'replace', side_effect=OSError('atomic replace failed')), \
                self.assertRaisesRegex(OSError, 'atomic replace failed'):
            PREPARE.write_private(file, 'fixture-api-secret')
        self.assertEqual('previous-private-value', file.read_text(encoding='utf-8'))
        self.assertEqual(before, set(self.production.iterdir()), 'Secret temp file survived failed replacement')

    def test_invalid_database_name_never_reaches_mysql(self):
        self.config['services']['app']['environment']['DB_URL'] = 'jdbc:mysql://mysql:3306/orders;DROP DATABASE production'
        with self.isolated(), self.assertRaisesRegex(ValueError, 'Invalid database name'):
            PREPARE.main()
        self.assertFalse(self.mutation_calls())
        self.assertFalse((self.production / '.payos.env').exists())

    def test_subprocess_failure_entrypoint_does_not_log_captured_secrets(self):
        error = subprocess.CalledProcessError(1, ['docker', 'compose', 'config'],
                                              output='fixture-api-secret', stderr='private-database-password')
        with self.isolated(subprocess_effect=error), self.assertRaises(SystemExit) as exited:
            exec(compile(SCRIPT.read_text(encoding='utf-8'), str(SCRIPT), 'exec'), {'__name__': '__main__'})
        self.assertEqual('PayOS preparation command failed; credentials were not logged.', exited.exception.code)
        self.assert_no_secrets_logged(exited.exception.code)

    def test_confirm_webhook_uses_credentials_in_headers_and_does_not_migrate(self):
        response = mock.MagicMock()
        response.__enter__.return_value = io.BytesIO(b'{"code":"00","desc":"fixture-api-secret"}')
        with self.isolated() as (_, provider), mock.patch.object(sys, 'argv', [str(SCRIPT), '--confirm-webhook']):
            provider.side_effect = None
            provider.return_value = response
            PREPARE.main()
            request = provider.call_args.args[0]
            self.assertEqual('https://api-merchant.payos.vn/confirm-webhook', request.full_url)
            self.assertEqual('POST', request.method)
            self.assertEqual('fixture-client-secret', request.get_header('X-client-id'))
            self.assertEqual('fixture-api-secret', request.get_header('X-api-key'))
            self.assertEqual({'webhookUrl': 'https://kitchen.example/crave/api/payments/payos/webhook'},
                             json.loads(request.data))
            self.assertEqual(30, provider.call_args.kwargs['timeout'])
        self.assertFalse(self.mutation_calls())
        self.assertFalse((self.production / '.payos.env').exists())
        self.assert_no_secrets_logged()

    def test_provider_rejection_does_not_log_its_response_or_write_files(self):
        response = mock.MagicMock()
        response.__enter__.return_value = io.BytesIO(b'{"code":"99","desc":"fixture-api-secret"}')
        with self.isolated() as (_, provider), mock.patch.object(sys, 'argv', [str(SCRIPT), '--confirm-webhook']):
            provider.side_effect = None
            provider.return_value = response
            with self.assertRaisesRegex(ValueError, 'did not confirm') as error:
                PREPARE.main()
        self.assert_no_secrets_logged(error.exception)
        self.assertFalse(self.mutation_calls())
        self.assertFalse((self.production / '.payos.env').exists())

    def test_write_private_rejects_symlinks_and_sets_private_mode(self):
        file = self.production / '.payos.env'
        with mock.patch.object(PREPARE.os, 'chmod', wraps=os.chmod) as chmod:
            PREPARE.write_private(file, 'fixture-api-secret')
        chmod.assert_called_once()
        self.assertEqual(0o600, chmod.call_args.args[1])
        self.assertEqual('fixture-api-secret', file.read_text(encoding='utf-8'))
        if os.name != 'nt':
            self.assertEqual(0o600, stat.S_IMODE(file.stat().st_mode))
        external = self.directory / 'outside-env'
        external.write_text('must remain untouched', encoding='utf-8')
        file.unlink()
        try:
            file.symlink_to(external)
        except OSError as error:
            self.skipTest('Host does not allow symlinks: ' + type(error).__name__)
        with self.assertRaisesRegex(ValueError, 'must not be a symlink'):
            PREPARE.write_private(file, 'replacement-secret')
        self.assertEqual('must remain untouched', external.read_text(encoding='utf-8'))

    def test_callback_validation_rejects_credentials_local_urls_and_wrong_context(self):
        for value in ('http://localhost:8080/crave', 'https://127.0.0.1/crave',
                      'https://user:fixture-api-secret@kitchen.example/crave',
                      'https://kitchen.example/', 'https://kitchen.example/crave?secret=fixture-api-secret',
                      'https://kitchen.example/crave#fragment', 'https://kitchen.example/crave\n'):
            with self.subTest(value=value), self.assertRaises(ValueError) as error:
                PREPARE.validate_base(value)
            self.assert_no_secrets_logged(error.exception)
        self.assertEqual('https://kitchen.example/crave', PREPARE.validate_base('https://kitchen.example/crave/'))


if __name__ == '__main__':
    unittest.main()
