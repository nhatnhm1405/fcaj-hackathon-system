#!/usr/bin/env python3
"""Private operator step. Creates two DB identities, never application tables.

Run manually on EC2. Passwords stay in root-only files/processes, never stdout.
This script is not an agent secret resolver and does not use Secrets Manager.
"""
import getpass
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import tempfile

ENDPOINT = 'fcaj-poc-mysql.cxywse4k25mo.ap-southeast-1.rds.amazonaws.com'
CATALOG = 'fcaj_poc'
SOURCE_IP = '10.20.12.59'
CA = '/etc/seal/rds-ca-bundle.pem'
RUNTIME = ('fcaj_runtime', {'SELECT', 'INSERT', 'UPDATE', 'DELETE'})
MIGRATION = ('fcaj_migration', RUNTIME[1] | {'CREATE', 'ALTER', 'INDEX', 'REFERENCES'})


class Stopped(Exception):
    pass


def option_value(value):
    if '\x00' in value:
        raise Stopped('Unsupported credential format.')
    return '"' + value.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n').replace('\r', '\\r') + '"'


def mysql(username, password, sql, directory):
    # The private admin password is never put in argv, env, output or saved state.
    fd, filename = tempfile.mkstemp(prefix='.mysql-', dir=directory)
    try:
        with os.fdopen(fd, 'w') as config:
            config.write('[client]\nuser=' + username + '\npassword=' + option_value(password) + '\n')
        env = dict(os.environ, MYSQL_TEST_LOGIN_FILE='/dev/null')
        env.pop('MYSQL_PWD', None)
        result = subprocess.run([
            'mysql', '--defaults-file=' + filename, '--host=' + ENDPOINT,
            '--port=3306', '--ssl-mode=VERIFY_IDENTITY', '--ssl-ca=' + CA,
            '--connect-timeout=10', '--batch', '--raw', '--skip-column-names',
        ], input=sql, capture_output=True, text=True, env=env)
        if result.returncode:
            # Never echo SQL/server errors: a failed CREATE USER may contain a secret.
            raise Stopped('MySQL operation failed; private files preserved. No passwords or raw SQL displayed.')
        return result.stdout.strip()
    finally:
        Path(filename).unlink(missing_ok=True)


def grant_scope(partial_revokes):
    # MySQL interprets _ as a database wildcard unless partial_revokes is enabled.
    return '`fcaj_poc`.*' if partial_revokes else '`fcaj\\_poc`.*'


def validate_grants(text, expected):
    observed = set()
    for line in text.splitlines():
        match = re.fullmatch(r'GRANT (.+) ON (\S+) TO .+', line)
        if not match or 'WITH GRANT OPTION' in line:
            raise Stopped('Unexpected role or grant option; no automatic revoke attempted.')
        privileges = set(match[1].split(', '))
        scope = match[2].replace('`', '').replace('\\', '')
        if scope == '*.*' and privileges == {'USAGE'}:
            continue
        if scope != CATALOG + '.*' or not privileges <= expected:
            raise Stopped('Unexpected existing DB privileges; no automatic revoke attempted.')
        observed |= privileges
    if observed != expected:
        raise Stopped('Expected DB privileges are incomplete.')


def write_private(path, content):
    if path.is_symlink():
        raise Stopped('Refusing symlink private file.')
    if path.exists():
        if path.read_text() != content or path.stat().st_mode & 0o077:
            raise Stopped('Existing private file differs or has loose permissions; refusing overwrite.')
        return
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as stream:
        stream.write(content)


def provision(directory, admin_password, run=mysql):
    inventory = json.loads(run('fcaj_admin', admin_password, """
SELECT JSON_OBJECT('version',VERSION(),'case',@@lower_case_table_names,
 'partial_revokes',@@GLOBAL.partial_revokes,
 'charset',(SELECT DEFAULT_CHARACTER_SET_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='fcaj_poc'),
 'collation',(SELECT DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='fcaj_poc'),
 'objects',(SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='fcaj_poc')
  +(SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA='fcaj_poc')
  +(SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA='fcaj_poc')
  +(SELECT COUNT(*) FROM information_schema.EVENTS WHERE EVENT_SCHEMA='fcaj_poc'));
""", directory))
    if (not inventory['version'].startswith('8.4.') or inventory['case'] != 1
            or inventory['charset'] != 'utf8mb4' or inventory['collation'] != 'utf8mb4_0900_ai_ci'
            or inventory['objects'] != 0):
        raise Stopped('Target is not the expected empty MySQL 8.4 catalog. No DB identities changed.')
    state_file = directory / 'db-identities.json'
    if state_file.is_symlink():
        raise Stopped('Refusing symlink state.')
    if state_file.exists():
        if state_file.stat().st_mode & 0o077:
            raise Stopped('Private state has loose permissions.')
        state = json.loads(state_file.read_text())
    else:
        state = {name: 'aA9!' + secrets.token_urlsafe(36) for name, _ in (RUNTIME, MIGRATION)}
        write_private(state_file, json.dumps(state))
    for name, _ in (RUNTIME, MIGRATION):
        if not re.fullmatch(r'aA9![A-Za-z0-9_-]{48}', state.get(name, '')):
            raise Stopped('Invalid private state; refusing credential rotation.')
    existing = set(run('fcaj_admin', admin_password,
        "SELECT User FROM mysql.user WHERE Host='10.20.12.59' AND User IN ('fcaj_runtime','fcaj_migration');", directory).splitlines())
    # Check every pre-existing identity before granting anything; never reset its password.
    for name in existing:
        if name not in state:
            raise Stopped('Unexpected existing identity.')
        run(name, state[name], 'SELECT 1;', directory)
    for name, privileges in (RUNTIME, MIGRATION):
        account = "'" + name + "'@'" + SOURCE_IP + "'"
        if name not in existing:
            run('fcaj_admin', admin_password,
                'CREATE USER ' + account + " IDENTIFIED BY '" + state[name] + "' REQUIRE SSL;", directory)
        # Existing grants may only be USAGE or a subset of the planned scope.
        grants = run('fcaj_admin', admin_password, 'SHOW GRANTS FOR ' + account + ';', directory)
        combined = grants + '\nGRANT ' + ', '.join(sorted(privileges)) + ' ON ' + grant_scope(inventory['partial_revokes']) + ' TO ' + account
        validate_grants(combined, privileges)
        run('fcaj_admin', admin_password,
            'GRANT ' + ', '.join(sorted(privileges)) + ' ON ' + grant_scope(inventory['partial_revokes']) + ' TO ' + account + ';', directory)
        validate_grants(run('fcaj_admin', admin_password, 'SHOW GRANTS FOR ' + account + ';', directory), privileges)
        run(name, state[name], 'USE fcaj_poc; SELECT 1;', directory)
    write_private(directory / 'db-runtime.env', 'SPRING_DATASOURCE_USERNAME=fcaj_runtime\nSPRING_DATASOURCE_PASSWORD=' + state['fcaj_runtime'] + '\n')
    write_private(directory / 'db-migration.env', 'BE1_MIGRATION_USERNAME=fcaj_migration\nBE1_MIGRATION_PASSWORD=' + state['fcaj_migration'] + '\n')


def main():
    if not hasattr(os, 'geteuid') or os.geteuid() != 0 or not sys.stdin.isatty():
        raise Stopped('Run with sudo from an interactive private Session Manager terminal.')
    os.umask(0o077)
    directory = Path('/etc/seal/db-identities')
    if directory.is_symlink():
        raise Stopped('Refusing symlink directory.')
    directory.mkdir(mode=0o700, parents=False, exist_ok=True)
    if directory.stat().st_mode & 0o077 or directory.stat().st_uid != 0:
        raise Stopped('Private directory permissions must be 700.')
    password = getpass.getpass('RDS admin password (hidden): ')
    if not password:
        raise Stopped('Empty admin password.')
    provision(directory, password)
    print('DB_IDENTITIES_READY: fcaj_runtime DML-only; fcaj_migration schema privileges; TLS required.')
    print('Private files: /etc/seal/db-identities/db-runtime.env and db-migration.env. Do not display or share contents.')
    print('No application tables or migrations created. No existing passwords reset.')


if __name__ == '__main__':
    try:
        main()
    except Stopped as rejected:
        print('PROVISIONING_STOPPED: ' + str(rejected), file=sys.stderr)
        sys.exit(1)
    except (OSError, ValueError, KeyError, KeyboardInterrupt):
        print('PROVISIONING_STOPPED: private state preserved for retry; no raw errors or passwords displayed.', file=sys.stderr)
        sys.exit(1)
