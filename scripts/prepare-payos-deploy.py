#!/usr/bin/env python3
"""Configure PayOS and apply its additive migration on the trusted EC2 runner."""
import ipaddress
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from urllib.parse import urlsplit


def run(command, **kwargs):
    return subprocess.run(command, check=True, capture_output=True, text=True, **kwargs).stdout


def read_env(file):
    values = {}
    if file.is_file():
        for raw in file.read_text(encoding='utf-8').splitlines():
            if '=' not in raw or raw.strip().startswith('#'):
                continue
            key, value = raw.split('=', 1)
            value = value.strip()
            if value.startswith('"'):
                value = json.loads(value)
            else:
                value = value.strip("'")
            values[key.strip()] = value
    return values


def write_private(file, content):
    if file.is_symlink():
        raise ValueError('PayOS configuration must not be a symlink.')
    with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=file.parent, delete=False) as temp:
        try:
            os.chmod(temp.name, 0o600)
            temp.write(content)
            temp.flush()
            os.fsync(temp.fileno())
        except BaseException:
            os.unlink(temp.name)
            raise
    try:
        os.replace(temp.name, file)
    finally:
        if os.path.exists(temp.name):
            os.unlink(temp.name)


def validate_base(base):
    parsed = urlsplit(base)
    if (parsed.scheme not in ('http', 'https') or not parsed.hostname or parsed.username
            or parsed.password or parsed.query or parsed.fragment or any(ch.isspace() for ch in base)):
        raise ValueError('PAYOS_PUBLIC_BASE_URL must be an absolute HTTP(S) URL including /crave.')
    if parsed.hostname in ('localhost', '127.0.0.1', '::1'):
        raise ValueError('Production PayOS callbacks cannot use localhost.')
    if not parsed.path.rstrip('/').endswith('/crave'):
        raise ValueError('PAYOS_PUBLIC_BASE_URL must include the /crave context.')
    return base.rstrip('/')


def discover_base(config, service):
    # EC2 IMDSv2 gives this instance's public address; never guess a user-supplied Host header.
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    token_request = urllib.request.Request('http://169.254.169.254/latest/api/token', method='PUT',
                                         headers={'X-aws-ec2-metadata-token-ttl-seconds': '60'})
    with opener.open(token_request, timeout=3) as response:
        token = response.read().decode('ascii')
    request = urllib.request.Request('http://169.254.169.254/latest/meta-data/public-ipv4',
                                     headers={'X-aws-ec2-metadata-token': token})
    with opener.open(request, timeout=3) as response:
        address = response.read().decode('ascii')
    if not ipaddress.ip_address(address).is_global:
        raise ValueError('EC2 has no public IPv4 address. Set PAYOS_PUBLIC_BASE_URL.')
    ports = config['services'][service].get('ports', [])
    port = next((str(item['published']) for item in ports if str(item.get('target')) == '8080'
                 and item.get('published') and item.get('host_ip', '') in ('', '0.0.0.0', '::')), None)
    if not port:
        raise ValueError('No public app port found. Set PAYOS_PUBLIC_BASE_URL.')
    return validate_base(f'http://{address}:{port}/crave')


def main():
    root_input = Path(os.environ.get('DEPLOY_PATH', '/home/ubuntu/crave'))
    if not root_input.is_absolute():
        raise ValueError('DEPLOY_PATH must be an absolute directory.')
    root = root_input.resolve(strict=True)
    workspace = Path(os.environ.get('GITHUB_WORKSPACE', Path.cwd())).resolve()
    if root == Path('/') or root == workspace or workspace in root.parents:
        raise ValueError('Production directory must be outside the runner checkout.')
    env_file = root / '.env'
    if not env_file.is_file():
        raise ValueError('Existing production .env is required.')
    compose_input = Path(os.environ.get('COMPOSE_FILE', 'compose.yaml'))
    if compose_input.is_absolute():
        raise ValueError('COMPOSE_FILE must be relative to the production directory.')
    compose_file = (root / compose_input).resolve(strict=True)
    if root not in compose_file.parents:
        raise ValueError('Compose must be inside the production directory.')
    for private_file in (root / '.payos.env', root / '.payos-compose.yaml'):
        if private_file.is_symlink() or (private_file.exists() and not private_file.is_file()):
            raise ValueError('PayOS deployment files must be regular files.')
    service = os.environ.get('APP_SERVICE', 'app')
    docker = [shutil.which('docker') or 'docker']
    try:
        run(docker + ['info'])
    except subprocess.CalledProcessError:
        docker = ['sudo', '-n'] + docker
        run(docker + ['info'])
    compose = docker + ['compose', '--project-directory', str(root), '--file', str(compose_file)]
    config = json.loads(run(compose + ['config', '--format', 'json']))
    if service not in config.get('services', {}) or 'mysql' not in config['services']:
        raise ValueError('Application and mysql services are required.')
    current = {**read_env(env_file), **read_env(root / '.payos.env')}
    values = {key: os.environ.get(key) or current.get(key, '')
              for key in ('PAYOS_CLIENT_ID', 'PAYOS_API_KEY', 'PAYOS_CHECKSUM_KEY', 'PAYOS_PUBLIC_BASE_URL')}
    if not all(values[key] for key in ('PAYOS_CLIENT_ID', 'PAYOS_API_KEY', 'PAYOS_CHECKSUM_KEY')):
        raise ValueError('The three PayOS production secrets are required.')
    for key, value in values.items():
        if '\n' in value or '\r' in value or '$' in value:
            raise ValueError(f'Invalid configuration value: {key}.')
    app_env = config['services'][service].get('environment', {})
    base = values['PAYOS_PUBLIC_BASE_URL'] or app_env.get('PAYOS_PUBLIC_BASE_URL', '')
    values['PAYOS_PUBLIC_BASE_URL'] = validate_base(base) if base else discover_base(config, service)
    if '--confirm-webhook' in sys.argv:
        public_url = values['PAYOS_PUBLIC_BASE_URL'] + '/api/payments/payos/webhook'
        request = urllib.request.Request('https://api-merchant.payos.vn/confirm-webhook', method='POST',
            data=json.dumps({'webhookUrl': public_url}).encode(), headers={
                'Content-Type': 'application/json', 'x-client-id': values['PAYOS_CLIENT_ID'],
                'x-api-key': values['PAYOS_API_KEY']})
        with urllib.request.urlopen(request, timeout=30) as response:
            body = json.load(response)
        if body.get('code') != '00':
            raise ValueError('PayOS did not confirm the public webhook. Check the URL and retry.')
        print('PayOS public webhook confirmed.')
        return
    # Only this backward-compatible migration is run; bootstrap schema is never applied here.
    migration = workspace / 'database/migrations/2026-10-12-payos.sql'
    sql = migration.read_text(encoding='utf-8').replace('USE crave;', '')
    db_url = app_env.get('DB_URL', '')
    database = db_url.split('?', 1)[0].rsplit('/', 1)[-1] if db_url.startswith('jdbc:mysql://') else 'crave'
    if not database.replace('_', '').isalnum():
        raise ValueError('Invalid database name.')
    run(compose + ['exec', '-T', 'mysql', 'sh', '-c',
        'export MYSQL_PWD="${MYSQL_ROOT_PASSWORD:?}"; exec mysql --user=root --default-character-set=utf8mb4 --database="$1"',
        'sh', database], input=sql)
    write_private(root / '.payos.env', ''.join(f'{key}={json.dumps(value)}\n' for key, value in values.items()))
    override = {'services': {service: {'environment': {key: '${' + key + '}' for key in values}}}}
    write_private(root / '.payos-compose.yaml', json.dumps(override, indent=2) + '\n')
    print('PayOS migration and private production environment prepared.')
    print('PayOS callback base: ' + values['PAYOS_PUBLIC_BASE_URL'])


if __name__ == '__main__':
    try:
        main()
    except subprocess.CalledProcessError:
        sys.exit('PayOS preparation command failed; credentials were not logged.')
    except Exception as error:
        # Never include provider responses or resolved Compose configuration in logs.
        sys.exit('PayOS preparation failed: ' + (str(error) if isinstance(error, ValueError) else type(error).__name__))
