import importlib.util
import unittest
from pathlib import Path

def load(name):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(name+'.py'))
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module
class QualityTests(unittest.TestCase):
    def test_merged_branding_document_remains_available(self):
        root = Path(__file__).resolve().parent.parent
        document = root / 'docs/branding-preparation.md'
        self.assertTrue(document.is_file(), 'Merged branding preparation must not be deleted')
        self.assertTrue(document.read_text().strip())
        self.assertIn('docs/branding-preparation.md', (root / 'README.md').read_text())

    def test_secret_guard_redacts_and_finds_generated_sample(self):
        guard=load('check_secrets')
        token='AKIA'+'0'*16
        self.assertEqual([(1,'aws-key')],guard.findings(token))
        self.assertEqual([],guard.findings('public metadata and placeholder'))
    def test_advisory_query_deduplicates_and_requires_complete_response(self):
        module=load('audit_dependencies');c={'group':'example','name':'demo','version':'1'}
        result=module.audit([c,c],lambda q: {'results':[{'vulns':[{'id':'TEST-1'}]}]})
        self.assertEqual([{'module':'example:demo:1','advisories':['TEST-1']}],result)
        with self.assertRaises(ValueError):module.audit([c],lambda q:{'results':[]})
    def test_paginated_advisory_response_fails_closed(self):
        module=load('audit_dependencies');c={'group':'example','name':'demo','version':'1'}
        with self.assertRaises(ValueError):
            module.audit([c],lambda q:{'results':[{'next_page_token':'more'}]})
if __name__=='__main__':unittest.main()
