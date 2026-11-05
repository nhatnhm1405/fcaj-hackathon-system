import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('provision_db', Path(__file__).with_name('provision-aws-db-users.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ProvisionTests(unittest.TestCase):
    def test_grant_scope_escapes_underscore_when_wildcards_enabled(self):
        self.assertEqual(module.grant_scope(0), '`fcaj\\_poc`.*')
        self.assertEqual(module.grant_scope(1), '`fcaj_poc`.*')

    def test_reject_privilege_escalation_and_role_membership(self):
        for grants in ['GRANT ALL PRIVILEGES ON *.* TO user',
                       'GRANT CREATE ON `fcaj_poc`.* TO user',
                       'GRANT SELECT ON `other`.* TO user',
                       'GRANT role_name TO user',
                       'GRANT SELECT ON `fcaj_poc`.* TO user WITH GRANT OPTION']:
            with self.subTest(grants=grants), self.assertRaises(module.Stopped):
                module.validate_grants(grants, module.RUNTIME[1])

    def test_accept_exact_runtime_permissions(self):
        module.validate_grants("GRANT USAGE ON *.* TO user REQUIRE SSL\nGRANT DELETE, INSERT, SELECT, UPDATE ON `fcaj\\_poc`.* TO user", module.RUNTIME[1])

    def test_refuse_overwriting_private_file(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'private'
            path.write_text('existing')
            with self.assertRaises(module.Stopped):
                module.write_private(path, 'replacement')
            self.assertEqual(path.read_text(), 'existing')

    def test_options_file_escapes_arbitrary_admin_password(self):
        self.assertEqual(module.option_value('quote"slash\\line\n'), '"quote\\"slash\\\\line\\n"')
        with self.assertRaises(module.Stopped):
            module.option_value('nul\x00')

    def test_mysql_failure_never_exposes_raw_errors(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(module.subprocess, 'run') as run:
                run.return_value.returncode = 1
                run.return_value.stderr = 'PRIVATE_SENTINEL'
                with self.assertRaises(module.Stopped) as failed:
                    module.mysql('fcaj_admin', 'PRIVATE_SENTINEL', 'SELECT 1;', directory)
                self.assertNotIn('PRIVATE_SENTINEL', str(failed.exception))
                args = run.call_args.args[0]
                self.assertNotIn('PRIVATE_SENTINEL', ' '.join(args))
            self.assertEqual(list(Path(directory).iterdir()), [])

    def test_nonempty_or_wrong_target_stops_before_create_or_grant(self):
        expected = dict(version='8.4.11', case=1, partial_revokes=0, charset='utf8mb4', collation='utf8mb4_0900_ai_ci', objects=0)
        for mismatch in [dict(objects=1), dict(version='8.0.40'), dict(case=0), dict(charset=None)]:
            calls = []
            def run(user, password, sql, directory):
                calls.append(sql)
                return json.dumps(expected | mismatch)
            with self.subTest(mismatch=mismatch), tempfile.TemporaryDirectory() as directory:
                with self.assertRaises(module.Stopped):
                    module.provision(Path(directory), 'test-admin', run)
                self.assertEqual(len(calls), 1)
                self.assertEqual(list(Path(directory).iterdir()), [])

    def test_success_creates_only_scoped_identities_without_schema_ddl(self):
        calls, grants = [], {}
        inventory = dict(version='8.4.11', case=1, partial_revokes=0, charset='utf8mb4', collation='utf8mb4_0900_ai_ci', objects=0)
        def run(user, password, sql, directory):
            calls.append(sql)
            if 'JSON_OBJECT' in sql:
                return json.dumps(inventory)
            if 'SELECT User' in sql:
                return ''
            if sql.startswith('SHOW GRANTS'):
                return 'GRANT USAGE ON *.* TO identity REQUIRE SSL' + grants.get(sql.split('FOR ')[1], '')
            if sql.startswith('GRANT '):
                scope, account = sql.split(' TO ')
                grants[account] = '\n' + scope + ' TO identity'
            return '1'
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            module.provision(root, 'test-admin', run)
            self.assertTrue((root / 'db-runtime.env').is_file())
            self.assertTrue((root / 'db-migration.env').is_file())
            self.assertEqual(len([s for s in calls if s.startswith('CREATE USER')]), 2)
            self.assertFalse(any('CREATE TABLE' in s or 'DROP ' in s or 'ALTER USER' in s for s in calls))
            self.assertTrue(all('10.20.12.59' in s for s in calls if s.startswith('CREATE USER')))


if __name__ == '__main__':
    unittest.main()
