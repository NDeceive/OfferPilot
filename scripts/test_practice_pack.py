import copy
import importlib.util
import json
import pathlib
import unittest

ROOT=pathlib.Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('importer',ROOT/'scripts/import-practice-pack.py')
importer=importlib.util.module_from_spec(spec);spec.loader.exec_module(importer)

class PackTest(unittest.TestCase):
    def setUp(self):self.pack=json.loads((ROOT/'后端/src/main/resources/practice-bank/pilot.json').read_text(encoding='utf-8'))
    def test_counts_and_additive_sql(self):
        importer.validate(self.pack)
        counts={k:sum(q['type']==k for q in self.pack['questions']) for k in ['KNOWLEDGE','CODING','CULTURE']}
        self.assertEqual(counts,{'KNOWLEDGE':40,'CODING':20,'CULTURE':18})
        sql=importer.sql_for(self.pack)
        self.assertNotIn('DROP ',sql);self.assertNotIn('DELETE ',sql)
        self.assertIn('START TRANSACTION',sql);self.assertIn('ON DUPLICATE KEY UPDATE',sql)
    def test_duplicates_unreviewed_and_unknown_license_are_rejected(self):
        self.pack['questions'].append(copy.deepcopy(self.pack['questions'][0]))
        with self.assertRaises(AssertionError):importer.validate(self.pack)
        self.pack['questions'].pop();self.pack['reviewStatus']='PENDING'
        with self.assertRaises(AssertionError):importer.validate(self.pack)
        self.pack['reviewStatus']='APPROVED';self.pack['questions'][0]['source']['license']='All rights reserved'
        with self.assertRaises(AssertionError):importer.validate(self.pack)
    def test_missing_evidence_and_broken_examples_are_rejected(self):
        culture=next(q for q in self.pack['questions'] if q['type']=='CULTURE');culture['source']['evidenceIds']=['invented']
        with self.assertRaises(AssertionError):importer.validate(self.pack)
        culture['source']['evidenceIds']=['candid'];coding=next(q for q in self.pack['questions'] if q['type']=='CODING');coding['examples']=[{'input':'invented','expected':'result'}]
        with self.assertRaises(AssertionError):importer.validate(self.pack)
    def test_literal_does_not_interpolate_sql(self):
        literal=importer.literal("'; DROP TABLE practice_session; --")
        self.assertNotIn('DROP',literal);self.assertRegex(literal,r"^CONVERT\(X'[a-f0-9]+' USING utf8mb4\)")

if __name__=='__main__':unittest.main()
