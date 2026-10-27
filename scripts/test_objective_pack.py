import copy
import importlib.util
import json
import pathlib
import unittest

ROOT=pathlib.Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('importer',ROOT/'scripts/import-practice-pack.py')
importer=importlib.util.module_from_spec(spec);spec.loader.exec_module(importer)

class ObjectivePackTest(unittest.TestCase):
    def setUp(self): self.pack=json.loads((ROOT/'后端/src/main/resources/practice-bank/objective.json').read_text(encoding='utf-8'))
    def test_counts_roles_and_isolated_idempotent_import(self):
        importer.validate(self.pack)
        self.assertEqual(len(self.pack['questions']),241)
        for kind,count in [('CHOICE',101),('TRUE_FALSE',70),('MULTIPLE_CHOICE',70)]:
            items=[q for q in self.pack['questions'] if q['type']==kind]
            self.assertEqual(len(items),count)
            roles={role for q in items for role in q['jobCodes']}
            self.assertEqual(len(roles),32)
            for role in roles: self.assertGreaterEqual(sum(role in q['jobCodes'] for q in items if q['difficulty']==1),10)
        sql=importer.sql_for(self.pack)
        self.assertIn('practice_objective_question',sql)
        self.assertNotIn('INSERT INTO skill_question',sql)
        self.assertNotIn('DELETE ',sql);self.assertNotIn('DROP ',sql)
        self.assertIn('ON DUPLICATE KEY UPDATE',sql)
    def test_invalid_answers_and_options_are_rejected(self):
        for mutate in [lambda q:q.update(correctAnswer='Z'),lambda q:q.update(correctAnswer='A,A'),lambda q:q['options'][1].update(key='A'),lambda q:q.update(correctAnswer='A,B')]:
            pack=copy.deepcopy(self.pack);mutate(pack['questions'][0])
            with self.assertRaises(AssertionError):importer.validate(pack)
        pack=copy.deepcopy(self.pack);q=next(q for q in pack['questions'] if q['type']=='MULTIPLE_CHOICE');q['correctAnswer']='A'
        with self.assertRaises(AssertionError):importer.validate(pack)
    def test_dom_question_retains_code_and_attribution(self):
        q=next(q for q in self.pack['questions'] if q['canonicalId']=='lydia-js-031-choice')
        self.assertIn('<button',q['content']);self.assertNotIn('#### 答案',q['content'])
        self.assertEqual(q['source']['license'],'MIT');self.assertEqual(len(q['source']['commit']),40)

if __name__=='__main__':unittest.main()
