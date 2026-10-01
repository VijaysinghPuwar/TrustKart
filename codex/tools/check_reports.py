"""Read-only consistency check of all generated audit reports and referenced evidence."""
from pathlib import Path
import json,re,collections,hashlib,subprocess
root=Path(__file__).resolve().parents[1]; repo=root.parent
findings=json.loads((root/'evidence/findings.json').read_text()); ids={f['id'] for f in findings};errors=[];checked=[]
required='README AUDIT_SUMMARY CODEBASE_ARCHITECTURE UI_UX_AUDIT RESPONSIVE_AUDIT FRONTEND_PERFORMANCE IMAGE_ASSET_AUDIT API_NETWORK_AUDIT BACKEND_PERFORMANCE DATABASE_AUDIT REDIS_CACHE_AUDIT ACCESSIBILITY_AUDIT RESOURCE_USAGE_AUDIT SECURITY_OPTIMIZATION_RISKS TEST_RESULTS BUGS_FOUND OPTIMIZATION_FINDINGS CLAUDE_IMPLEMENTATION_PLAN VERIFICATION_CHECKLIST'.split()
for name in required:
 if not (root/(name+'.md')).exists():errors.append('Missing required report '+name)
for doc in sorted(root.glob('*.md')):
 s=doc.read_text(); checked.append({'file':doc.name,'sha256':hashlib.sha256(s.encode()).hexdigest(),'words':len(s.split())})
 found=set(re.findall(r'\b(?:SEC|UX|A11Y|API|FE|DB|BE|QA|IMG)-\d{3}\b',s))
 errors.extend(f'{doc.name}: undefined ID {i}' for i in found-ids)
 for target in re.findall(r'\]\(([^)]+)\)',s):
  if target.startswith(('https:','http:')):continue
  path=target.split('#')[0]
  if path and not (doc.parent/path).exists():errors.append(f'{doc.name}: missing link {target}')
  if '#'+target.split('#')[-1] in target and 'OPTIMIZATION_FINDINGS.md#' in target:
   anchor=target.split('#')[-1].upper()
   if anchor not in ids:errors.append(f'{doc.name}: missing finding anchor {anchor}')
 if doc.stem in ['OPTIMIZATION_FINDINGS','CLAUDE_IMPLEMENTATION_PLAN','VERIFICATION_CHECKLIST']:
  errors.extend(f'{doc.name}: missing finding {i}' for i in ids-found)
 if re.search(r'\b(?:TODO|TBD|FIXME)\b',s):errors.append(f'{doc.name}: unfinished placeholder')
for f in findings:
 for field in ['evidence','repro','current','expected','user','resource','cause','change','safe','risk','ux','implement','test','confidence']:
  if not f.get(field):errors.append(f"{f['id']}: missing {field}")
 for path,line in f['files']:
  p=repo/path
  if not p.exists():errors.append(f"{f['id']}: missing source {path}")
  elif p.is_file() and line and line>len(p.read_text().splitlines()):errors.append(f"{f['id']}: invalid line {path}:{line}")
pri=collections.Counter(x['priority'] for x in findings);security=json.loads((root/'evidence/security-checks.json').read_text()); assertions=[x for x in security if 'pass' in x]
metrics={'routeCases':len({x['name'] for x in json.loads((root/'evidence/route-matrix.json').read_text())}),'routeViewportObservations':len(json.loads((root/'evidence/route-matrix.json').read_text())),'axeScans':len(json.loads((root/'evidence/accessibility.json').read_text())),'performanceLoads':len(json.loads((root/'evidence/performance.json').read_text())),'independentSecurityAssertions':len(assertions),'independentSecurityFailed':sum(not x['pass'] for x in assertions)}
if metrics!={'routeCases':27,'routeViewportObservations':207,'axeScans':54,'performanceLoads':30,'independentSecurityAssertions':16,'independentSecurityFailed':1}:errors.append('Evidence count mismatch')
if pri!={'P1':1,'P2':16,'P3':1}:errors.append('Priority count mismatch')
result={'baseline':subprocess.check_output(['git','rev-parse','HEAD'],cwd=repo,text=True).strip(),'reportsRead':checked,'priorityCounts':{'P0':0,**dict(pri)},'metrics':metrics,'errors':errors}
(root/'evidence/report-qc.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({k:v for k,v in result.items() if k!='reportsRead'},indent=2));raise SystemExit(bool(errors))
