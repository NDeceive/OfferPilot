"""Fetch only the audited public repositories, pin commits and retain licenses."""
import concurrent.futures
import hashlib
import json
import pathlib
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1] / '后端/src/main/resources/practice-bank/sources'
EXERCISES = 'two-fer leap raindrops hamming difference-of-squares grains armstrong-numbers isogram pangram reverse-string binary-search gigasecond roman-numerals rna-transcription acronym collatz-conjecture sum-of-multiples allergies darts resistor-color-duo'.split()
REPOS = {'exercism/problem-specifications': 'main', 'Snailclimb/JavaGuide': 'main', 'yangshun/front-end-interview-handbook': 'main', 'lydiahallie/javascript-questions': 'master'}

def read(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'OfferPilot-source-audit'}), timeout=30).read()

def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    manifest = {}
    for repo, branch in REPOS.items():
        commit = json.loads(read(f'https://api.github.com/repos/{repo}/commits/{branch}'))['sha']
        folder = ROOT / repo.split('/')[1]
        folder.mkdir(exist_ok=True)
        license_data = read(f'https://raw.githubusercontent.com/{repo}/{commit}/LICENSE')
        (folder / 'LICENSE').write_bytes(license_data)
        manifest[repo] = {'commit': commit, 'licenseSha256': hashlib.sha256(license_data).hexdigest()}
        if repo == 'exercism/problem-specifications':
            def fetch(slug):
                data = read(f'https://raw.githubusercontent.com/{repo}/{commit}/exercises/{slug}/canonical-data.json')
                (folder / f'{slug}.json').write_bytes(data)
                return slug, hashlib.sha256(data).hexdigest()
            with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
                manifest[repo]['files'] = dict(pool.map(fetch, EXERCISES))
        else:
            (folder / 'README.md').write_bytes(read(f'https://raw.githubusercontent.com/{repo}/{commit}/README.md'))
    (ROOT / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print('Pinned 4 repositories, retained licenses and 20 canonical test files.')

if __name__ == '__main__':
    main()
