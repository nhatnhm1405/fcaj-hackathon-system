#!/usr/bin/env python3
"""Assemble private config on EC2; never print credentials or execute migrations."""
import base64
import json
import os
from pathlib import Path
import secrets
import shlex
import sys

ROOT = Path('/etc/seal')
URL = 'jdbc:mysql://fcaj-poc-mysql.cxywse4k25mo.ap-southeast-1.rds.amazonaws.com:3306/fcaj_poc?sslMode=VERIFY_IDENTITY&serverTimezone=UTC&connectTimeout=5000&socketTimeout=30000'
JVM = '-Djavax.net.ssl.trustStore=/run/secrets/rds-truststore.p12 -Djavax.net.ssl.trustStoreType=PKCS12'


def private_text(path):
    if path.is_symlink() or path.stat().st_uid != 0 or path.stat().st_mode & 0o077:
        raise ValueError('Private file ownership/permissions rejected')
    return path.read_text()


def write_once(path, content):
    if path.is_symlink():
        raise ValueError('Symlink rejected')
    if path.exists():
        if private_text(path) != content:
            raise ValueError('Existing config differs; refusing overwrite')
        return
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as stream:
        stream.write(content)


def identity(path, expected_keys):
    values = {}
    for line in private_text(path).splitlines():
        key, separator, value = line.partition('=')
        if not separator or key not in expected_keys or key in values or not value:
            raise ValueError('Invalid identity file')
        values[key] = value
    if set(values) != set(expected_keys):
        raise ValueError('Incomplete identity file')
    return values


def env_text(values):
    if any('\n' in value or '\r' in value or '\x00' in value for value in values.values()):
        raise ValueError('Multiline environment value rejected')
    return ''.join(key + '=' + value + '\n' for key, value in values.items())


def main():
    if os.geteuid() != 0 or len(sys.argv) != 2:
        raise ValueError('Root and exact Compose template path required')
    os.umask(0o077)
    if ROOT.is_symlink() or ROOT.stat().st_uid != 0 or ROOT.stat().st_mode & 0o077:
        raise ValueError('Private directory ownership/permissions rejected')
    runtime = identity(ROOT / 'db-identities/db-runtime.env', ('SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD'))
    migration = identity(ROOT / 'db-identities/db-migration.env', ('BE1_MIGRATION_USERNAME', 'BE1_MIGRATION_PASSWORD'))
    secret_file = ROOT / 'application-secrets.json'
    if secret_file.exists():
        app_secrets = json.loads(private_text(secret_file))
    else:
        app_secrets = {'jwt': base64.b64encode(secrets.token_bytes(48)).decode('ascii')}
        write_once(secret_file, json.dumps(app_secrets))
    if len(base64.b64decode(app_secrets['jwt'], validate=True)) < 32:
        raise ValueError('Invalid JWT material')
    common = dict(AWS_REGION='ap-southeast-1', APP_STORAGE_BUCKET='fcaj-poc-data-582129522297-ap-southeast-1', APP_STORAGE_PREFIX='poc',
        APP_AI_JOBS_JOBS_QUEUE_URL='https://sqs.ap-southeast-1.amazonaws.com/582129522297/fcaj-poc-ai-jobs',
        APP_AI_JOBS_RESULTS_QUEUE_URL='https://sqs.ap-southeast-1.amazonaws.com/582129522297/fcaj-poc-ai-results')
    runtime.update(SPRING_PROFILES_ACTIVE='aws', SPRING_DATASOURCE_URL=URL, JWT_SECRET=app_secrets['jwt'],
        APP_FRONTEND_URL='https://app.autowashpro.io.vn', APP_CORS_ALLOWED_ORIGINS='https://app.autowashpro.io.vn',
        JWT_COOKIE_SECURE='true', CSRF_COOKIE_DOMAIN='autowashpro.io.vn', SEED_SCENARIO='NONE', BOOTSTRAP_ADMIN_ENABLED='false',
        APP_AI_JOBS_ENABLED='false', APP_AI_JOBS_DELIVERY_ENABLED='false', JAVA_TOOL_OPTIONS=JVM, **common)
    migration.update(BE1_MIGRATION_JDBC_URL=URL, JAVA_TOOL_OPTIONS=JVM)
    settings = dict(DEPLOY_COMPOSE_FILE='/etc/seal/compose.aws.yml', DEPLOY_RUNTIME_ENV_FILE='/etc/seal/runtime.env',
        DEPLOY_MIGRATION_ENV_FILE='/etc/seal/migration.env', DEPLOY_DB_CATALOG='fcaj_poc', DEPLOY_RUNTIME_JDBC_URL=URL,
        DEPLOY_DB_TRUSTSTORE_FILE='/etc/seal/rds-truststore.p12', DEPLOY_SPRING_PROFILES_ACTIVE='aws', DEPLOY_DB_MODE='verify-only')
    compose = Path(sys.argv[1]).read_text()
    if '127.0.0.1:8080:8080' not in compose or 'format: raw' not in compose:
        raise ValueError('Expected backend-only loopback Compose template')
    write_once(ROOT / 'runtime.env', env_text(runtime))
    write_once(ROOT / 'migration.env', env_text(migration))
    write_once(ROOT / 'worker.env', env_text(common))
    write_once(ROOT / 'deploy-settings.sh', ''.join(key + '=' + shlex.quote(value) + '\n' for key, value in settings.items()))
    write_once(ROOT / 'compose.aws.yml', compose)
    print('PRIVATE_RUNTIME_CONFIG_READY: AI delivery and bootstrap admin off; DB verify-only; no secrets displayed.')


if __name__ == '__main__':
    try:
        main()
    except Exception:
        print('CONFIG_PREPARATION_STOPPED: no secret values displayed; existing config preserved.', file=sys.stderr)
        sys.exit(1)
