"""Validate/dry-run by default. --apply writes additive V6 schema + an atomic, idempotent batch."""
import argparse
import collections
import hashlib
import json
import os
import pathlib
import re
import subprocess

ROOT=pathlib.Path(__file__).resolve().parents[1]

def validate(pack):
    if not __debug__: raise RuntimeError('Run the importer without -O; validation must remain enabled')
    assert re.fullmatch(r'[A-Za-z0-9_-]{1,100}',pack['batchId']), 'Invalid batch ID'
    assert pack.get('reviewStatus')=='APPROVED', 'Only explicitly reviewed packs can be imported'
    companies={c['code']:c for c in pack['companies']}
    assert len(companies)==len(pack['companies']), 'Duplicate company codes'
    for c in companies.values():
        assert re.fullmatch(r'[a-z0-9-]{1,40}',c['code']) and 0<len(c['name'])<=100
        assert c['evidence'].get('checkedAt') and c['evidence'].get('items'), 'Company evidence missing'
        for e in c['evidence']['items']: assert e['url'].startswith('https://') and e['summary'] and e['id']
    canonical=set()
    for q in pack['questions']:
        assert q['canonicalId'] not in canonical, 'Duplicate canonical question'
        canonical.add(q['canonicalId'])
        assert 0<len(q['canonicalId'])<=180 and 0<len(q['title'])<=255 and q['content']
        assert q['type'] in ['KNOWLEDGE','CODING','CULTURE','CHOICE','MULTIPLE_CHOICE','TRUE_FALSE'] and q['difficulty'] in [1,2,3]
        assert q['jobCodes'] and all(re.fullmatch(r'[A-Z]+-[A-Z]+',c) for c in q['jobCodes'])
        assert q['topics'] and all(0<len(t)<=100 for t in q['topics'])
        assert all(0<len(t)<=50 for t in q['tags'])
        assert q['source']['url'].startswith('https://') and q['source']['license'] and q['source']['author']
        assert q['source']['license'] in ['MIT','Apache-2.0','BSD-3-Clause','OfferPilot-original'], 'Source license requires separate review'
        if q['source'].get('repo'): assert re.fullmatch(r'[a-f0-9]{40}',q['source'].get('commit','')), 'Repository source must be pinned'
        if q['type']=='CULTURE':
            assert q['companyCode'] in companies and q['source'].get('evidenceIds'), 'Culture provenance missing'
            evidence=companies[q['companyCode']]['evidence']['items']
            assert set(q['source']['evidenceIds']) <= {e['id'] for e in evidence}
        if q['type']=='CODING':
            assert q['language']=='java' and q['starter'] and q['referenceCode']
            assert len(q['tests'])>=3 and q['examples'], 'Insufficient test coverage'
            for t in q['tests']:
                assert isinstance(t['input'],str) and isinstance(t['expected'],str)
                assert t.get('public') in ['true','false'] and t.get('uuid')
            assert all(t in q['tests'] for t in q['examples'])
        else: assert q['referenceAnswer']
        if q['type'] in ['CHOICE','MULTIPLE_CHOICE','TRUE_FALSE']:
            options=q['options']; keys=[o['key'] for o in options]
            assert 2<=len(options)<=6 and len(set(keys))==len(keys)
            assert all(re.fullmatch(r'[A-F]|true|false',o['key']) and isinstance(o['text'],str) and o['text'].strip() for o in options)
            answers=q['correctAnswer'].split(',')
            assert answers==sorted(set(answers)) and set(answers)<=set(keys), 'Invalid objective answer'
            if q['type']=='MULTIPLE_CHOICE': assert len(answers)>=2
            else: assert len(answers)==1
            if q['type']=='TRUE_FALSE': assert keys==['true','false']
            else: assert keys==list('ABCDEF'[:len(keys)])

def literal(value):
    if value is None:return 'NULL'
    if isinstance(value,int):return str(value)
    if isinstance(value,(dict,list)): value=json.dumps(value,ensure_ascii=False,separators=(',',':'))
    return "CONVERT(X'"+str(value).encode('utf-8').hex()+"' USING utf8mb4) COLLATE utf8mb4_general_ci"

def sql_for(pack):
    validate(pack)
    lines=["SET NAMES utf8mb4; SET @lock=GET_LOCK('offerpilot_practice_import',30); SET @guard=IF(@lock=1,'DO 0','IMPORT_LOCK_FAILED'); PREPARE guard FROM @guard; EXECUTE guard; DEALLOCATE PREPARE guard; START TRANSACTION;"]
    for c in pack['companies']:
        vals=','.join(literal(v) for v in [c['code'],c['name'],c['aliases'],c['evidence']])
        lines.append(f"INSERT INTO practice_company(code,name,aliases_json,evidence_json,review_status) VALUES({vals},'APPROVED') ON DUPLICATE KEY UPDATE name=VALUES(name),aliases_json=VALUES(aliases_json),evidence_json=VALUES(evidence_json),review_status='APPROVED';")
    for q in pack['questions']:
        src=dict(q['source']);src['contentHash']=hashlib.sha256(q['content'].encode()).hexdigest()
        if q.get('conceptId'): src['conceptId']=q['conceptId']
        if q['type'] in ['CHOICE','MULTIPLE_CHOICE','TRUE_FALSE']:
            fields=['canonical_id','title','content','subtype','difficulty','job_codes_json','topics_json','tags_json','options_json','correct_answer','reference_answer','source_json','batch_id','review_status']
            values=[q['canonicalId'],q['title'],q['content'],q['type'],q['difficulty'],q['jobCodes'],q['topics'],q['tags'],q['options'],q['correctAnswer'],q['referenceAnswer'],src,pack['batchId'],'APPROVED']
            lines.append('INSERT INTO practice_objective_question('+','.join(fields)+') VALUES('+','.join(map(literal,values))+') ON DUPLICATE KEY UPDATE '+','.join(f'{f}=VALUES({f})' for f in fields[1:])+';')
        elif q['type']=='CODING':
            fields=['canonical_id','title','content','difficulty','job_codes_json','topics_json','tags_json','language','starter_code','examples_json','tests_json','reference_code','source_json','batch_id','review_status']
            values=[q['canonicalId'],q['title'],q['content'],q['difficulty'],q['jobCodes'],q['topics'],q['tags'],q['language'],q['starter'],q['examples'],q['tests'],q['referenceCode'],src,pack['batchId'],'APPROVED']
            lines.append('INSERT INTO practice_coding_question('+','.join(fields)+') VALUES('+','.join(map(literal,values))+') ON DUPLICATE KEY UPDATE '+','.join(f'{f}=VALUES({f})' for f in fields[1:])+';')
        else:
            cid=literal(q['canonicalId'])
            lines.append(f'SET @qid=(SELECT question_id FROM practice_question_meta WHERE canonical_id={cid});')
            content,ref,keys,diff=[literal(v) for v in [q['content'],q['referenceAnswer'],q['keywords'],q['difficulty']]]
            lines.append(f'INSERT INTO skill_question(content,reference_answer,answer_keywords,difficulty) SELECT {content},{ref},{keys},{diff} WHERE @qid IS NULL;')
            lines.append('SET @qid=COALESCE(@qid,LAST_INSERT_ID());')
            lines.append(f'UPDATE skill_question SET content={content},reference_answer={ref},answer_keywords={keys},difficulty={diff} WHERE id=@qid;')
            company='NULL' if q['type']!='CULTURE' else '(SELECT id FROM practice_company WHERE code='+literal(q['companyCode'])+')'
            fields=['title','subtype','job_codes_json','topics_json','tags_json','source_json','batch_id','review_status']
            vals=list(map(literal,[q['title'],q['type'],q['jobCodes'],q['topics'],q['tags'],src,pack['batchId'],'APPROVED']))
            lines.append('INSERT INTO practice_question_meta(question_id,canonical_id,company_id,'+','.join(fields)+') VALUES(@qid,'+cid+','+company+','+','.join(vals)+') ON DUPLICATE KEY UPDATE company_id=VALUES(company_id),'+','.join(f'{f}=VALUES({f})' for f in fields)+';')
            # Culture questions intentionally have no shared interview tags.
            for tag in q['tags'] if q['type']=='KNOWLEDGE' else []:
                t=literal(tag)
                lines.append(f"INSERT INTO skill_tag(name,category) VALUES({t},'专项刷题') ON DUPLICATE KEY UPDATE name=VALUES(name);")
                lines.append(f'INSERT IGNORE INTO skill_question_tag_rel(question_id,tag_id) SELECT @qid,id FROM skill_tag WHERE name={t};')
    lines.append("COMMIT; DO RELEASE_LOCK('offerpilot_practice_import');")
    return '\n'.join(lines)

def mysql(sql):
    db=os.environ.get('DB_NAME','zhimian')
    assert re.fullmatch(r'[A-Za-z0-9_]+',db), 'Invalid database name'
    binary=os.environ.get('MYSQL_BIN','C:/Program Files/MySQL/MySQL Server 9.7/bin/mysql.exe')
    env=dict(os.environ);env['MYSQL_PWD']=os.environ.get('DB_PASSWORD','123456')
    result=subprocess.run([binary,'--host='+os.environ.get('DB_HOST','127.0.0.1'),'--port='+os.environ.get('DB_PORT','3306'),
        '--user='+os.environ.get('DB_USER','root'),'--database='+db,'--default-character-set=utf8mb4','--batch','--skip-column-names'],
        input=sql.encode('utf-8'),env=env,capture_output=True)
    if result.returncode: raise RuntimeError(result.stderr.decode('utf-8',errors='replace')[:1800])
    return result.stdout.decode('utf-8')

def main():
    p=argparse.ArgumentParser();p.add_argument('--pack',type=pathlib.Path,default=ROOT/'后端/src/main/resources/practice-bank/pilot.json');p.add_argument('--apply',action='store_true');p.add_argument('--disable-batch');args=p.parse_args()
    if args.disable_batch:
        assert re.fullmatch(r'[A-Za-z0-9_-]{1,100}',args.disable_batch)
        if not args.apply: print('Dry run: batch would be disabled, historical sessions preserved.');return
        b=literal(args.disable_batch)
        mysql(f"START TRANSACTION; UPDATE practice_question_meta SET review_status='DISABLED' WHERE batch_id={b}; UPDATE practice_coding_question SET review_status='DISABLED' WHERE batch_id={b}; UPDATE practice_objective_question SET review_status='DISABLED' WHERE batch_id={b}; COMMIT;")
        print('Batch disabled. Historical sessions preserved.');return
    pack=json.loads(args.pack.read_text(encoding='utf-8'));sql=sql_for(pack)
    counts=collections.Counter(q['type'] for q in pack['questions']);print('Validated:',dict(counts),'companies:',len(pack['companies']))
    if not args.apply: print('Dry run only. Add --apply after reference-code verification and content review.');return
    report=ROOT/'后端/src/main/resources/practice-bank/verification.json'
    if any(q['type']=='CODING' for q in pack['questions']):
        verified=json.loads(report.read_text(encoding='utf-8'))
        assert verified['packSha256']==hashlib.sha256(args.pack.read_bytes()).hexdigest() and verified['passed'], 'Run reference verification for this exact pack first'
    # Validate referenced job codes before starting any writes.
    jobs=set(mysql('SELECT code FROM job_position WHERE status=1;').splitlines())
    assert {c for q in pack['questions'] for c in q['jobCodes']}<=jobs, 'Pack refers to an unknown or disabled job'
    mysql((ROOT/'后端/src/main/resources/db/migration_v6_practice_bank.sql').read_text(encoding='utf-8'))
    mysql((ROOT/'后端/src/main/resources/db/migration_v8_objective_practice.sql').read_text(encoding='utf-8'))
    mysql(sql)
    print('Batch applied atomically. Repeat import is safe; no user history deleted.')

if __name__=='__main__':main()
