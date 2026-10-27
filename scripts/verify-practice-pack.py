"""Run every converted canonical case through compiled Java reference implementations."""
import concurrent.futures
import hashlib
import importlib.util
import json
import re
import pathlib
import shutil
import subprocess
import tempfile

ROOT=pathlib.Path(__file__).resolve().parents[1]
JAVA_BIN=pathlib.Path(shutil.which('javac')).parent
spec=importlib.util.spec_from_file_location('importer',ROOT/'scripts/import-practice-pack.py');importer=importlib.util.module_from_spec(spec);spec.loader.exec_module(importer)

def normalize(value):
    value=re.sub(r"[ \t]+(?=\n|$)","",value.replace("\r\n","\n"))
    return value[:-1] if value.endswith("\n") else value

def verify(q):
    with tempfile.TemporaryDirectory(prefix='offerpilot-reference-') as folder:
        p=pathlib.Path(folder);(p/'Main.java').write_text(q['referenceCode'],encoding='utf-8')
        subprocess.run([str(JAVA_BIN/'javac'),'--release','17','-encoding','UTF-8','Main.java'],cwd=p,check=True,capture_output=True)
        tests=q.get('verificationTests',q['tests'])
        for case in tests:
            run=subprocess.run([str(JAVA_BIN/'java'),'-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','Main'],cwd=p,input=case['input'].encode('utf-8'),capture_output=True,timeout=5)
            assert run.returncode==0, q['canonicalId']+' / '+run.stderr.decode('utf-8',errors='replace')+run.stdout.decode('utf-8',errors='replace')
            actual=normalize(run.stdout.decode('utf-8'));expected=normalize(case['expected'])
            assert actual==expected,f"{q['canonicalId']} / {case['uuid']}: {actual!r} != {expected!r}"
        # A constant-output implementation must fail at least one real case.
        first=q['examples'][0]['expected']
        wrong=next(t for t in q['tests'] if normalize(t['expected'])!=normalize(first))
        (p/'Main.java').write_text('public class Main {public static void main(String[] args){if(args.length>0)System.out.print("\\n");System.out.println('+json.dumps(first,ensure_ascii=True)+');}}',encoding='utf-8')
        subprocess.run([str(JAVA_BIN/'javac'),'--release','17','-encoding','UTF-8','Main.java'],cwd=p,check=True,capture_output=True)
        bad=subprocess.run([str(JAVA_BIN/'java'),'-Dstdout.encoding=UTF-8','Main'],cwd=p,input=wrong['input'].encode(),capture_output=True,timeout=5,check=True)
        assert normalize(bad.stdout.decode('utf-8'))!=normalize(wrong['expected'])
        malformed=subprocess.run([str(JAVA_BIN/'java'),'-Dstdout.encoding=UTF-8','Main','format'],cwd=p,input=q['examples'][0]['input'].encode(),capture_output=True,timeout=5,check=True)
        assert normalize(malformed.stdout.decode('utf-8'))!=normalize(first)
        print(q['canonicalId'],len(tests),'cases passed; constant solution rejected',flush=True)
        return {'canonicalId':q['canonicalId'],'cases':len(tests),'constantSolutionRejected':True,'formatErrorRejected':True}

def main():
    file=ROOT/'后端/src/main/resources/practice-bank/pilot.json';pack=json.loads(file.read_text(encoding='utf-8'));importer.validate(pack)
    coding=[q for q in pack['questions'] if q['type']=='CODING']
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool: results=list(pool.map(verify,coding))
    report={'packSha256':hashlib.sha256(file.read_bytes()).hexdigest(),'passed':True,'javaRelease':17,'realJudgeExecutor':False,'comparisonMode':'LINE_ENDING','hostRuntime':subprocess.run([str(JAVA_BIN/'java'),'-version'],capture_output=True,text=True,check=True).stderr.splitlines()[0],'results':results}
    (ROOT/'后端/src/main/resources/practice-bank/verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print('All references passed:',sum(r['cases'] for r in results),'cases.')

if __name__=='__main__':main()
