"""Real executor health/version smoke test. Never enables the application automatically."""
import base64,json,os,pathlib,urllib.request
def main():
    base=os.environ.get('JUDGE_BASE_URL','').rstrip('/');language_id=int(os.environ.get('JUDGE_LANGUAGE_JAVA','0'))
    if not base or not language_id:raise RuntimeError('Set JUDGE_BASE_URL and the instance-specific JUDGE_LANGUAGE_JAVA first')
    headers={'Content-Type':'application/json','X-Auth-Token':os.environ.get('JUDGE_AUTH_TOKEN','')}
    def request(path,data=None):
        req=urllib.request.Request(base+path,headers=headers,data=None if data is None else json.dumps(data).encode())
        with urllib.request.urlopen(req,timeout=30) as response:return json.load(response)
    language=request('/languages/'+str(language_id))
    if 'OpenJDK 17' not in language.get('name',''):raise RuntimeError('Language metadata is not Java 17')
    source='public class Main { public static void main(String[] args) { System.out.print(System.getProperty("java.version")); } }'
    result=request('/submissions?base64_encoded=true&wait=true',{'source_code':base64.b64encode(source.encode()).decode(),'language_id':language_id,'stdin':'','cpu_time_limit':3,'wall_time_limit':6,'memory_limit':128000,'max_file_size':64,'enable_network':False})
    if result.get('status',{}).get('id')!=3:raise RuntimeError('Java version program did not succeed; inspect the isolated instance before enabling judging')
    actual=base64.b64decode(result.get('stdout','')).decode().strip()
    if not actual.startswith('17.'):raise RuntimeError('Actual Java runtime version mismatch')
    report={'realExecutor':True,'language':language['name'],'languageId':language_id,'runtimeVersion':actual}
    target=pathlib.Path('infra/practice-judge/verification.json');target.write_text(json.dumps(report,indent=2))
    print(json.dumps(report))
if __name__=='__main__':main()
