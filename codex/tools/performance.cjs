// Independent lab measurements on local production output; not field Core Web Vitals.
const {chromium}=require('/private/tmp/trustkart-audit-20260930/frontend/node_modules/@playwright/test');const fs=require('node:fs');const path=require('node:path');
const root=path.resolve(__dirname,'..');const base='http://127.0.0.1:15173';const rows=[];
const observer=()=>{
 window.auditVitals={lcp:0,lcpElement:null,cls:0,fcp:0,longTasks:[]};
 new PerformanceObserver(l=>{for(const e of l.getEntries()){window.auditVitals.lcp=e.startTime;window.auditVitals.lcpElement={tag:e.element?.tagName,url:e.url,displayWidth:e.element?.getBoundingClientRect().width}}}).observe({type:'largest-contentful-paint',buffered:true});
 new PerformanceObserver(l=>{for(const e of l.getEntries())if(!e.hadRecentInput)window.auditVitals.cls+=e.value}).observe({type:'layout-shift',buffered:true});
 new PerformanceObserver(l=>{for(const e of l.getEntries())if(e.name==='first-contentful-paint')window.auditVitals.fcp=e.startTime}).observe({type:'paint',buffered:true});
 new PerformanceObserver(l=>{for(const e of l.getEntries())window.auditVitals.longTasks.push(e.duration)}).observe({type:'longtask',buffered:true});
};
(async()=>{
const b=await chromium.launch();
for(const [name,url] of [['home','/'],['search','/search?q=laptop'],['product','/p/apple-iphone-18-pro'],['cart','/cart'],['rankings','/rankings']]){
 for(const mobile of [false,true])for(let run=1;run<=3;run++){
  const c=await b.newContext({viewport:mobile?{width:390,height:844}:{width:1440,height:900},deviceScaleFactor:mobile?3:1,isMobile:mobile,hasTouch:mobile,colorScheme:'dark'});
  const p=await c.newPage();const cdp=await c.newCDPSession(p);await cdp.send('Network.enable');await cdp.send('Performance.enable');await cdp.send('Network.setCacheDisabled',{cacheDisabled:true});
  if(mobile){await cdp.send('Emulation.setCPUThrottlingRate',{rate:4});await cdp.send('Network.emulateNetworkConditions',{offline:false,latency:60,downloadThroughput:9*1024*1024/8,uploadThroughput:2*1024*1024/8});}
  const req=new Map();const errors=[];
  cdp.on('Network.responseReceived',e=>req.set(e.requestId,{url:e.response.url.replace(base,''),type:e.type,status:e.response.status,mime:e.response.mimeType,encoding:e.response.headers['Content-Encoding']||null,bytes:0}));
  cdp.on('Network.loadingFinished',e=>{if(req.has(e.requestId))req.get(e.requestId).bytes=e.encodedDataLength});
  p.on('pageerror',e=>errors.push(e.message));await p.addInitScript(observer);
  await p.goto(base+url,{waitUntil:'networkidle'});await p.waitForTimeout(1000);
  const initial=[...req.values()];const vitals=await p.evaluate(()=>({...window.auditVitals,ttfb:performance.getEntriesByType('navigation')[0].responseStart}));
  const dom=await p.evaluate(()=>document.querySelectorAll('*').length);const metrics=Object.fromEntries((await cdp.send('Performance.getMetrics')).metrics.map(m=>[m.name,m.value]));
  await p.evaluate(async()=>{for(let y=0;y<document.body.scrollHeight;y+=600){scrollTo(0,y);await new Promise(r=>setTimeout(r,100));}});await p.waitForLoadState('networkidle');
  const all=[...req.values()];const apis=all.filter(x=>x.url.startsWith('/api/'));const byType={};for(const r of initial)byType[r.type]=(byType[r.type]||0)+r.bytes;
  const row={name,mobile,run,initialRequests:initial.length,initialBytes:initial.reduce((a,r)=>a+r.bytes,0),initialByType:byType,scrolledRequests:all.length,scrolledBytes:all.reduce((a,r)=>a+r.bytes,0),apis,duplicateApis:apis.map(x=>x.url).filter((x,i,a)=>a.indexOf(x)!==i),vitals,dom,jsHeapUsed:metrics.JSHeapUsedSize,scriptDuration:metrics.ScriptDuration,layoutDuration:metrics.LayoutDuration,errors,requests:all};
  rows.push(row);fs.writeFileSync(path.join(root,'evidence/performance.json'),JSON.stringify(rows,null,2));console.log(name,mobile?'mobile':'desktop',run,'LCP',Math.round(vitals.lcp),'CLS',vitals.cls,'bytes',row.initialBytes,'requests',row.initialRequests);await c.close();
 }
}
await b.close();
})().catch(e=>{console.error(e);process.exit(1)});
