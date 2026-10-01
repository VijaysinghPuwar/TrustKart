const {request}=require('/private/tmp/trustkart-audit-20260930/frontend/node_modules/@playwright/test');
const fs=require('node:fs');const path=require('node:path');
const dir=path.resolve(__dirname,'../evidence');const base='http://127.0.0.1:15173/api/v1';
(async()=>{
const c=await request.newContext({storageState:'/private/tmp/trustkart-audit-20260930/audit-browser-state.json'});
const f=JSON.parse(fs.readFileSync(path.join(dir,'fixture.json')));const rows=[];
const endpoints=['/catalog/home','/catalog/categories','/catalog/products?size=24','/catalog/products/apple-iphone-18-pro','/catalog/categories/laptops/facets','/search?q=macbook&size=24','/search/suggest?q=macbook','/cart','/wallet','/checkout/quote','/purchases?size=10','/purchases/'+f.purchaseId,'/collection','/wishlist','/notifications/unread-count','/notifications?size=30','/leaderboards/monthly','/leaderboards/all-time','/leaderboards/me','/me','/me/sessions'];
for(const endpoint of endpoints){
 const samples=[];
 for(let i=0;i<5;i++){
  const start=performance.now();const r=await c.get(base+endpoint);const body=await r.body();const end=performance.now();
  samples.push({ms:+(end-start).toFixed(2),bytes:body.length,status:r.status(),requestId:r.headers()['x-request-id'],encoding:r.headers()['content-encoding']||null,cacheControl:r.headers()['cache-control']});
  if(i===0&&r.ok())fs.writeFileSync(path.join(dir,'payload-'+endpoint.split('?')[0].replaceAll('/','_')+'.json'),body);
 }
 rows.push({endpoint,samples});console.log(endpoint,samples.map(s=>`${s.ms}ms/${s.bytes}B/${s.status}`).join(' '));
}
const page0=await(await c.get(base+'/catalog/products?size=24&page=0')).json();
const beyond=await(await c.get(base+'/catalog/products?size=24&page=9999')).json();
rows.push({check:'out-of-range catalog page totals',page0Total:page0.totalItems,beyond});
const nullOptions=await c.get(base+'/checkout/quote?productId=1&options=null');
rows.push({check:'null options rejected as validation error',status:nullOptions.status(),body:await nullOptions.text()});
fs.writeFileSync(path.join(dir,'api-measurements.json'),JSON.stringify(rows,null,2));await c.dispose();
})().catch(e=>{console.error(e);process.exit(1)});
