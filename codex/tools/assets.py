"""Read-only inventory of committed static assets and the complete seeded catalog."""
import collections, gzip, hashlib, importlib.util, json, re, statistics
from pathlib import Path
ROOT=Path('/private/tmp/trustkart-audit-20260930')
OUT=Path(__file__).resolve().parents[1]/'evidence'
spec=importlib.util.spec_from_file_location('validator',ROOT/'scripts/validate_images.py')
v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
public=ROOT/'frontend/public'
assets=[p for p in public.rglob('*') if p.is_file()]
imgs={}
for name in ['demo/images.json','catalog/images.json']:
 imgs.update(json.loads((ROOT/'backend/src/main/resources'/name).read_text()))
products=[]
for p in [ROOT/'backend/src/main/resources/demo/products.json',*sorted((ROOT/'backend/src/main/resources/catalog/products').glob('*.json'))]:
 products.extend(json.loads(p.read_text())['products'])
rows=[]; errors=[];digests=collections.defaultdict(list)
for p in assets:
 row={'path':str(p.relative_to(public)),'bytes':p.stat().st_size,'format':p.suffix}
 if p.suffix=='.webp':row['dimensions']=v.webp_size(p)
 rows.append(row);digests[hashlib.sha256(p.read_bytes()).hexdigest()].append(row['path'])
for p in products:
 img=imgs.get(p['slug'])
 if not img: errors.append({'slug':p['slug'],'issue':'no image metadata'});continue
 for key in ['small','large']:
  f=public/img[key].lstrip('/')
  if not f.exists():errors.append({'slug':p['slug'],'issue':'missing '+key,'path':img[key]});continue
  dim=v.webp_size(f)
  if not dim:errors.append({'slug':p['slug'],'issue':'invalid WebP','path':img[key]})
  if key=='large' and dim!=tuple([img.get('width'),img.get('height')]):errors.append({'slug':p['slug'],'issue':'metadata dimensions mismatch','actual':dim,'recorded':[img.get('width'),img.get('height')]})
 if not img.get('alt'):errors.append({'slug':p['slug'],'issue':'no alt'})
dup=[{'paths':paths,'bytesEach':(public/paths[0]).stat().st_size,'redundantBytes':(len(paths)-1)*(public/paths[0]).stat().st_size} for paths in digests.values() if len(paths)>1]
dup.sort(key=lambda d:d['redundantBytes'],reverse=True)
summary={'products':len(products),'imageMetadataRecords':len(imgs),'assetFiles':len(rows),'assetBytes':sum(r['bytes'] for r in rows),'formats':dict(collections.Counter(r['format'] for r in rows)),'largest':sorted(rows,key=lambda r:r['bytes'],reverse=True)[:20],'validationErrors':errors,'exactDuplicateGroups':len(dup),'exactDuplicateRedundantBytes':sum(d['redundantBytes'] for d in dup)}
for size in ['400','800']:
 a=[r['bytes'] for r in rows if r['path'].endswith('-'+size+'.webp')]
 summary['webp'+size]={'files':len(a),'meanBytes':round(statistics.mean(a)),'medianBytes':statistics.median(a),'maxBytes':max(a)}
chunks=[]
for p in (ROOT/'frontend/dist/assets').glob('*'):
 if p.suffix in ['.js','.css']:chunks.append({'file':p.name,'bytes':p.stat().st_size,'gzipBytes':len(gzip.compress(p.read_bytes()))})
(OUT/'assets.json').write_text(json.dumps(summary,indent=2));(OUT/'asset-duplicates.json').write_text(json.dumps(dup,indent=2));(OUT/'asset-inventory.json').write_text(json.dumps(rows,indent=2));(OUT/'bundle.json').write_text(json.dumps(chunks,indent=2))
print(json.dumps(summary,indent=2))
