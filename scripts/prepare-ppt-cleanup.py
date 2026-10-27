from pathlib import Path
from zipfile import ZipFile
import hashlib,json,shutil
root=Path(__file__).resolve().parents[1]
final=root/'designs/animation-navigation-correction'
deck=final/'output/智面幻境_华五ppt.pptx'
assert deck.is_file()
names=['animation-prototypes','function-showcase-differentiated','implementation-validation','innovation-function-showcase','innovation-function-showcase-expanded','interface-demonstration-redesign','interface-focus-redesign','pain-point-analysis','policy-competition','project-background-redesign','project-solution','project-solution-overview','reference-app-analysis','reference-study','resume-workbench-reorder','scoring-dimension-selection','team-closing','visual-v3']
old=[root/'designs'/n for n in names]+[root/'PPT_V3_Workspace']
assets=final/'assets/current-embedded';assets.mkdir(parents=True,exist_ok=True)
z=ZipFile(deck)
hashes={}
for f in z.namelist():
 if f.startswith('ppt/media/') and not f.endswith('/'):
  data=z.read(f);(assets/Path(f).name).write_bytes(data);hashes[hashlib.sha256(data).hexdigest()]=Path(f).name
records=final/'retained-records';records.mkdir(exist_ok=True)
matched=[]
for folder in old:
 if not folder.exists():continue
 for f in folder.rglob('*'):
  if not f.is_file():continue
  rel=f.relative_to(folder)
  if any(p.lower() in ['research','reports'] for p in rel.parts) and f.suffix.lower() in ['.md','.txt','.json','.csv','.pdf']:
   dest=records/folder.name/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(f,dest)
  if f.suffix.lower() in ['.png','.jpg','.jpeg','.svg','.webp']:
   h=hashlib.sha256(f.read_bytes()).hexdigest()
   if h in hashes:matched.append({'original':str(f.relative_to(root)),'retained':hashes[h]})
  if ('device' in rel.parts or '设备' in f.name) and f.suffix.lower() in ['.png','.jpg','.jpeg','.md','.json']:
   dest=final/'assets/device-source'/folder.name/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(f,dest)
# Keep the latest previews, provenance and final PPT; discard previous-generation artifacts here.
keep={deck.resolve(),*[(final/'preview'/f'V25-P{i:02}.png').resolve() for i in [3,4,6,8,12,14]],(final/'preview/P15-静态.png').resolve(),(final/'reports/导航统一检查.json').resolve()}
remove=[]
for f in final.rglob('*'):
 if not f.is_file():continue
 if any(p in ['assets','retained-records'] for p in f.relative_to(final).parts):continue
 if f.resolve() not in keep:remove.append(f.resolve())
manifest={'preserved_deck':str(deck),'deck_sha256':hashlib.sha256(deck.read_bytes()).hexdigest(),'embedded_media':len(hashes),'matched_originals':matched,'delete_directories':[str(p.resolve()) for p in old if p.exists()],'delete_files':[str(p) for p in remove]}
(final/'reports/清理清单.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'preserved':str(deck),'directories':len(manifest['delete_directories']),'files':len(remove),'media':len(hashes)},ensure_ascii=False))
