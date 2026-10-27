"""Downloads a pinned JDK and creates local secrets; does not start containers or alter the host."""
import argparse,hashlib,pathlib,secrets,urllib.request
ROOT=pathlib.Path(__file__).resolve().parents[1]/'infra/practice-judge'
URL='https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_x64_linux_hotspot_17.0.20.1_1.tar.gz'
SHA='3808d1d15e3ec6bd5b84057fb5d84c33d8a1536a258146bcea2e603fc726e08e'
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--prepare',action='store_true');args=parser.parse_args()
    if not args.prepare:
        print('Dry run: Judge0 1.13.1 + Temurin 17.0.20.1, local-only Linux test deployment. Use --prepare to download JDK and generate secrets.');return
    target=ROOT/'jdk17.tar.gz'
    if not target.exists():
        with urllib.request.urlopen(URL,timeout=60) as response,target.open('wb') as output:
            while chunk:=response.read(1024*1024):output.write(chunk)
    if hashlib.file_digest(target.open('rb'),'sha256').hexdigest()!=SHA:raise RuntimeError('JDK checksum mismatch; containers must not be built')
    config=ROOT/'judge0.conf'
    if not config.exists():
        template=(ROOT/'judge0.conf.example').read_text()
        while 'GENERATE_LOCALLY' in template:template=template.replace('GENERATE_LOCALLY',secrets.token_hex(32),1)
        config.write_text(template);config.chmod(0o600)
    print('Prepared verified JDK and local secrets. No deployment performed; follow infra/practice-judge/README.md.')
if __name__=='__main__':main()
